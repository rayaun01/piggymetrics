import json
import os
import re
import secrets
from pathlib import Path
from urllib.parse import urlparse

from flask import Flask, redirect, render_template, request, session, url_for
from markupsafe import Markup, escape


BASE_DIR = Path(__file__).resolve().parent
CONTENT_PATH = BASE_DIR / "content.json"

app = Flask(__name__)
app.secret_key = os.environ.get("REPORT_SECRET_KEY") or secrets.token_hex(32)
app.config.update(
    TEMPLATES_AUTO_RELOAD=True,
    SESSION_COOKIE_HTTPONLY=True,
    SESSION_COOKIE_SAMESITE="Lax",
)


def _safe_href(value):
    href = str(value or "").strip()
    scheme = urlparse(href).scheme.lower()
    if scheme and scheme not in {"http", "https", "mailto"}:
        return "#"
    return href


def inline_format(value):
    """Render the deliberately small inline syntax supported by the report schema."""
    text = str(escape(str(value or "")))
    tokens = []

    def remember(kind, value):
        tokens.append((kind, value))
        return f"\x00{len(tokens) - 1}\x00"

    text = re.sub(
        r"`([^`]+)`",
        lambda match: remember("code", match.group(1)),
        text,
    )
    text = re.sub(
        r"\[([^\]]+)\]\(([^)\s]+)\)",
        lambda match: remember(
            "link",
            f'<a href="{escape(_safe_href(match.group(2)))}" target="_blank" rel="noreferrer">'
            f"{match.group(1)}</a>"
        ),
        text,
    )
    text = re.sub(
        r"\*\*(.+?)\*\*",
        lambda match: remember("bold", match.group(1)),
        text,
    )

    token_ref = re.compile(r"\x00(\d+)\x00")
    rendered = {}

    def render_token(index):
        if index in rendered:
            return rendered[index]
        kind, value = tokens[index]
        content = token_ref.sub(
            lambda match: render_token(int(match.group(1))),
            value,
        )
        if kind == "code":
            rendered[index] = f"<code>{content}</code>"
        elif kind == "bold":
            rendered[index] = f"<strong>{content}</strong>"
        else:
            rendered[index] = content
        return rendered[index]

    text = token_ref.sub(
        lambda match: render_token(int(match.group(1))),
        text,
    )
    return Markup(text)


app.jinja_env.filters["inline"] = inline_format
app.jinja_env.filters["safe_href"] = _safe_href


def load_content():
    with CONTENT_PATH.open(encoding="utf-8") as content_file:
        return json.load(content_file)


@app.route("/")
def report():
    if not session.get("auth"):
        return redirect(url_for("login"))
    return render_template("report.html", content=load_content())


@app.route("/login", methods=["GET", "POST"])
def login():
    error = None
    if request.method == "POST":
        expected_user = os.environ.get("REPORT_USER", "akira")
        expected_password = os.environ.get("REPORT_PASSWORD", "akiraPassword")
        username = request.form.get("username", "")
        password = request.form.get("password", "")
        if secrets.compare_digest(username, expected_user) and secrets.compare_digest(
            password, expected_password
        ):
            session["auth"] = True
            return redirect(url_for("report"))
        error = "The username or password did not match."
    status = 401 if error else 200
    return render_template("login.html", error=error), status


@app.route("/logout")
def logout():
    session.clear()
    return redirect(url_for("login"))


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=int(os.environ.get("PORT", "5100")), debug=False)
