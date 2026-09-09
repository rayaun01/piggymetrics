#!/usr/bin/env python3

import datetime
import json
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import parse_qs, urlparse


class RatesHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        parsed = urlparse(self.path)
        query = parse_qs(parsed.query)
        if parsed.path != "/latest" or query.get("base") != ["USD"]:
            self.send_error(404)
            return

        body = json.dumps({
            "base": "USD",
            "date": datetime.date.today().isoformat(),
            "rates": {
                "USD": 1,
                "EUR": 0.92,
                "RUB": 92.5,
                "JPY": 147.85,
            },
        }).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format, *args):
        return


if __name__ == "__main__":
    HTTPServer(("0.0.0.0", 18080), RatesHandler).serve_forever()
