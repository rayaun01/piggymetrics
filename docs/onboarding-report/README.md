# PiggyMetrics onboarding report

This is a small password-gated Flask report for engineers onboarding to the PiggyMetrics Devin demo. The report content lives in `content.json` and is rendered as bilingual English/Japanese rows.

## Run locally

```sh
python3 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
python app.py
```

Then open <http://127.0.0.1:5100/>. The development defaults are defined in `app.py`.

Credentials can be overridden with `REPORT_USER` and `REPORT_PASSWORD`. Set `REPORT_SECRET_KEY` to use a stable Flask session key; otherwise a secure key is generated when the process starts. `PORT` can override the default port.

Mermaid is vendored under `static/vendor/`, and the SoftBank mark is stored under `static/img/`; the app does not require a CDN at runtime.
