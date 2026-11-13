#!/bin/sh
set -eu
cd "$(dirname "$0")"
JARVIS_ORIGIN="$(tailscale status --json | python3 -c 'import json,sys; print("https://"+json.load(sys.stdin)["Self"]["DNSName"].rstrip("."))')"
export JARVIS_ORIGIN
printf 'Jarvis am Handy: %s\n' "$JARVIS_ORIGIN"
exec python3 server.py
