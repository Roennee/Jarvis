#!/bin/sh
set -eu
cd "$(dirname "$0")"
if [ -z "${JARVIS_ORIGIN:-}" ] && command -v tailscale >/dev/null 2>&1; then
  origin="$(tailscale status --json 2>/dev/null | python3 -c 'import json,sys; d=json.load(sys.stdin); n=d.get("Self",{}).get("DNSName","").rstrip("."); print("https://"+n if n else "")' 2>/dev/null || true)"
  if [ -n "$origin" ]; then JARVIS_ORIGIN="$origin"; export JARVIS_ORIGIN; fi
fi
if [ -x .venv/bin/python ]; then exec .venv/bin/python server.py; fi
exec python3 server.py
