#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
root="$(pwd)"
if [[ "$root" == *'"'* || "$root" == *'%'* || "$root" == *$'\n'* ]]; then
  echo 'Pfad für den Dienst nicht unterstützt. Repository unter ~/Jarvis verwenden.' >&2; exit 1
fi
mkdir -p "$HOME/.config/systemd/user"
cat > "$HOME/.config/systemd/user/jarvis.service" <<EOF
[Unit]
Description=Jarvis local personal assistant
After=network-online.target

[Service]
Type=simple
WorkingDirectory="$root"
ExecStart=/bin/bash "$root/start.sh"
Restart=on-failure
RestartSec=5
UMask=0077

[Install]
WantedBy=default.target
EOF
cat > "$HOME/.config/systemd/user/jarvis-model-update.service" <<EOF
[Unit]
Description=Jarvis selected local model update with rollback

[Service]
Type=oneshot
WorkingDirectory="$root"
ExecStart=/bin/bash "$root/model-update.sh"
UMask=0077
EOF
cat > "$HOME/.config/systemd/user/jarvis-model-update.timer" <<'EOF'
[Unit]
Description=Weekly update of the selected Jarvis model

[Timer]
OnCalendar=Sun *-*-* 04:00:00
Persistent=true
RandomizedDelaySec=15m

[Install]
WantedBy=timers.target
EOF
systemctl --user daemon-reload
systemctl --user enable jarvis.service
echo 'Vor dem Start den bisherigen manuellen Server mit Strg+C stoppen.'
echo 'Dann: systemctl --user start jarvis'
echo 'Dieser Dienst startet bei der Benutzeranmeldung. Für Betrieb nach Abmeldung optional: sudo loginctl enable-linger "$USER"'
echo 'Tailscale Serve bleibt separat eingerichtet; System-Suspend beendet weiterhin die Erreichbarkeit.'

echo 'Optional für die gewünschten wöchentlichen Modellupdates:'
echo 'systemctl --user enable --now jarvis-model-update.timer'
echo 'Timer läuft sonntags 04:00 Systemzeit bzw. beim nächsten Start nach. Nur gewählter Modelltag; keine Auswahl neuer Modellfamilien.'
