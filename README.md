# Jarvis UI 0.2
Python 3.12 und Ollama mit qwen3:1.7b benötigt. Keine Zusatzpakete.
Lokal: python3 server.py
Mit Tailscale: bash start.sh
In anderem Terminal: sudo tailscale serve --bg http://127.0.0.1:8765
Die HTTPS-Freigabe bei Aufforderung über den von Tailscale ausgegebenen Link aktivieren. Nur Serve verwenden, kein Funnel. Zugriff wird über das private Tailscale-Netz geregelt; alle laut dessen Zugriffsregeln berechtigten Teilnehmer können die App nutzen. Keine zusätzliche App-Anmeldung.
Zum Deaktivieren: sudo tailscale serve reset
Notizen liegen in jarvis.sqlite3. Beim Update nur Programmdateien ersetzen, Datenbank behalten. Chat bleibt nur im offenen Tab. Keine Sprachaufnahme, Kalenderanbindung, Trades oder automatischen Modellupdates. Standard-Testmodell, keine verifizierte Abliteration.

## Installation über GitHub

```bash
git clone https://github.com/Roennee/Jarvis.git ~/Jarvis
cd ~/Jarvis
bash start.sh
```

Voraussetzung: Tailscale ist angemeldet. Ohne Tailscale lokal mit `python3 server.py` starten.

## Updates

Server mit Strg+C stoppen. Im Repository:

```bash
git pull --ff-only
bash start.sh
```

SQLite-Datenbank und private Konfigurationen sind nicht Teil von Git. Bei Umstieg aus dem ZIP die Datei `jarvis.sqlite3` bei gestopptem alten Server in den neuen Repository-Ordner kopieren. Bestehende Datenbank nicht überschreiben. Vor Updates eine Sicherung der Datenbank ausserhalb des Repositorys anlegen.

## Stand und nächste Schritte

0.2: lokale UI, Ollama-Chat, Notizen in SQLite, konfigurierter privater Tailscale-HTTPS-Zugriff.
Offen: Audioaufnahme und lokale Transkription, Pixel-Sperrbildschirmzugriff, persistente Chats, Google/Outlook OAuth, Trainings- und Depotdaten, Bees-Anbindung sowie geprüfte Modellupdates und Auswahl eines abliterated Modells.

Modellupdates sind getrennt von Programmupdates; `git pull` lädt keine neuen Modellgewichte. Keine Cloud-KI wird automatisch hinzugefügt. Repository enthält keine persönlichen Kalender- oder Finanzdaten und keine Zugangsdaten.
