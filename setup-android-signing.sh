#!/usr/bin/env bash
# One-time local setup. Never prints key material or passwords.
set -euo pipefail
umask 077
for dependency in openssl gh base64; do
  command -v "$dependency" >/dev/null || { echo "Fehlt: $dependency. Bitte installieren und erneut starten." >&2; exit 1; }
done
gh auth status >/dev/null 2>&1 || { echo 'Bitte zuerst gh auth login ausführen.' >&2; exit 1; }
repo='Roennee/Jarvis'
key_dir="${XDG_DATA_HOME:-$HOME/.local/share}/jarvis/android-signing"
mkdir -p "$key_dir"
key_file="$key_dir/jarvis.p12"
password_file="$key_dir/password"
if [[ -f "$key_file" && ! -f "$password_file" ]] || [[ ! -f "$key_file" && -f "$password_file" ]]; then
  echo 'Unvollständige Schlüsselsicherung. Nicht neu erzeugen: zuerst vorhandene Sicherung prüfen.' >&2
  exit 1
fi
staging=$(mktemp -d)
trap 'rm -rf "$staging"' EXIT
if [[ ! -f "$key_file" ]]; then
  openssl rand -hex 32 | tr -d '\n' > "$staging/password"
  openssl req -x509 -newkey rsa:3072 -sha256 -days 10000 -nodes \
    -keyout "$staging/key.pem" -out "$staging/cert.pem" -subj '/CN=Jarvis Android/' 2>/dev/null
  openssl pkcs12 -export -name jarvis -inkey "$staging/key.pem" -in "$staging/cert.pem" \
    -out "$staging/jarvis.p12" -passout "file:$staging/password"
  mv "$staging/jarvis.p12" "$key_file"
  mv "$staging/password" "$password_file"
fi
chmod 600 "$key_file" "$password_file"
base64 < "$key_file" > "$staging/keystore.b64"
gh secret set JARVIS_ANDROID_KEYSTORE --repo "$repo" < "$staging/keystore.b64"
gh secret set JARVIS_ANDROID_KEYSTORE_PASSWORD --repo "$repo" < "$password_file"
echo 'Privater Android-Schlüssel ist eingerichtet. Keine Schlüssel ins Repository oder in Chats kopieren.'
echo "Lokale Sicherung: $key_dir (beide Dateien gemeinsam sichern)."
gh workflow run jarvis.yml --repo "$repo" --ref feature/jarvis-0.4
echo 'Build gestartet. Download erscheint nach erfolgreichen Prüfungen unter GitHub Actions.'
