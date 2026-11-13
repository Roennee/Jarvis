# Jarvis · lokaler persönlicher Assistent

Version **0.4.0**, für Linux Mint, Python 3.12, 8 GB RAM und Pixel mit Tailscale.

## Start

```bash
git clone https://github.com/Roennee/Jarvis.git ~/Jarvis
cd ~/Jarvis
bash start.sh
```

Lokal: http://127.0.0.1:8765. Handy: die vom Startskript angezeigte Tailscale-HTTPS-Adresse. Die bestehende Freigabe `sudo tailscale serve --bg http://127.0.0.1:8765` bleibt bestehen. Kein Funnel, keine Portfreigabe. Zugriff wird durch die Tailscale-Netzwerkregeln geregelt; keine zusätzliche App-Anmeldung. Nur eigene/berechtigte Geräte ins Netz aufnehmen.

## Von 0.2 / 0.3 aktualisieren

Manuellen Server mit Strg+C stoppen, dann:

```bash
cd ~/Jarvis
git pull --ff-only
bash setup.sh
bash start.sh
```

`setup.sh` installiert lokale Sprachausgabe (eSpeak NG), lokale Transkription (faster-whisper mit Base-Modell) und ICS-Import. Dafür werden beim ersten Lauf Pakete und Modellgewichte heruntergeladen. Danach transkribiert der Worker mit deaktiviertem Netzwerkdownload. Anschliessend lädt `setup-model.sh` den abliterated Kandidaten `huihui_ai/qwen3-abliterated:1.7b` (ca. 1,1 GB) und wählt ihn nur, wenn zwei einfache Antworttests bestehen. Bei fehlgeschlagenen Tests bleibt die alte Auswahl bestehen. Ohne Setup funktionieren Chat, Notizen, Aufgaben, Trainings-/Depotimport und Kalenderplanung weiterhin. Eine fehlende Erweiterung wird im Dashboard angezeigt.

Auf Laptop mit Strg+Umschalt+R neu laden. Auf Pixel gegebenenfalls Tab schliessen und erneut öffnen. Die Datenbank wird erweitert, bestehende Notizen bleiben erhalten.

## Was funktioniert

- Responsive UI, Übersicht, Status und Handy-Navigation.
- Chats und Notizen dauerhaft in SQLite; Notizensuche, optional letzte drei Notizen als Modellkontext. Das Modell führt keine Werkzeuge selbst aus.
- Aufgaben mit Fälligkeit und Erledigt-Status.
- Mikrofonaufnahme im Browser bis zwei Minuten, Vorschau, lokaler Download und Upload. Audio bleibt nach Upload auf dem Laptop.
- Lokale Transkription als Hintergrundauftrag. Danach Text prüfen, korrigieren und als Notiz speichern. Original/Korrektur werden aufbewahrt; kein automatisches Sprachtraining.
- Geräte-TTS nur für Stimmen mit `localService=true`; eSpeak als zuverlässigerer Serverfallback mit Player. Keine Fish-Audio-Anbindung. Fallback klingt synthetisch, keine zugesagte natürliche Wunschstimme. Im synthetischen Sprachtest wurden einzelne Wörter falsch erkannt; Transkripte deshalb immer prüfen, besonders Zeiten, Namen und Schweizerdeutsch.
- Lokale Termine und Import von Google-/Outlook-ICS-Dateien; wiederkehrende Einträge im Fenster letzte 30 bis nächste 370 Tage. Alle Zeiten werden in Europe/Zurich angezeigt. Wiederimport dedupliziert erkannte Termine, löscht aber keine entfernten Termine. Google/Outlook zusätzlich als Leseadapter nach Kontoeinrichtung (manueller Abruf, keine Schreibaktionen).
- Training manuell und CSV-Import. Depot-Snapshots als CSV, Historie und Summen je Währung. Keine Livekurse, keine Währungsumrechnung.
- Bees-JSON-Berichte als unveränderte Momentaufnahmen. Keine direkte Prozess-/Kraken-Anbindung, keine Trades.
- SQLite-Onlinebackup und Datenexport als JSON. Die Audio-Dateien sind im JSON-Export nicht enthalten.
- Konfiguration des installierten Ollama-Modells. CPU-intensive KI-Aufgaben werden serialisiert.

## Update und Betrieb

```bash
bash update.sh
```

Sichert die Datenbank und holt Code mit Fast-Forward. Bei geändertem Code bricht es ab. Ein laufender systemd-Benutzerdienst wird neu gestartet; manuelle Server danach selbst neu starten. Neue Abhängigkeiten bei Bedarf mit `bash setup.sh` installieren.

```bash
bash install-service.sh
# Nach Stop des manuellen Servers:
systemctl --user start jarvis
systemctl --user status jarvis
journalctl --user -u jarvis -n 50
```

Der Dienst startet bei Anmeldung. Für Start beim Systemboot ohne Benutzeranmeldung optional `sudo loginctl enable-linger "$USER"` einrichten. Gerät muss wach bleiben; Suspend kann diese App nicht verhindern.

Für Modellupdates getrennt vom Code:

```bash
# Server vorher stoppen, auch manuelle Instanz.
bash model-update.sh
```

Sichert den bisherigen Modelltag lokal, lädt den gewählten Tag neu und prüft eine Antwort. Bei technischem Fehler wird der alte Tag wiederhergestellt. Die Qualität ist zusätzlich anhand der gedruckten Testantwort zu prüfen. Der optionale lokale Wochentimer wird durch `install-service.sh` vorbereitet. Aktivierung: `systemctl --user enable --now jarvis-model-update.timer`. Er aktualisiert sonntags um 04:00 Systemzeit (oder beim nächsten Start) nur den bereits gewählten Modelltag, mit technischem Test und Rückfallkopie. Er stoppt/startet dafür den Jarvis-Benutzerdienst kurzzeitig. Neue Modellfamilien und abliterated Varianten werden noch nicht automatisch ausgewählt. Technischer Test ist kein Nachweis besserer Antwortqualität. UI „Modellstand protokollieren“ erfasst nur installierte Stände, kein Online-Updatecheck.

## Daten und Privatsphäre

`jarvis.sqlite3` (inklusive Chatverläufen, persönlichen Einstellungen), `recordings/`, `backups/` und `models/` bleiben lokal und sind von Git ausgeschlossen. Datenbank und Audio regelmässig ausserhalb dieses Rechners sichern. Keine Tokens oder persönlichen Exporte ins öffentliche Repository legen. Aufnahme nur bewusst starten. Auf dem Pixel kann die Aufnahme beim Sperren der Seite unterbrochen werden; nicht als Hintergrundrekorder benutzen.

## Noch nicht fertig / benötigt Einrichtung

- **Native Pixel-Aufnahme aus dem Sperrbildschirm:** Android-MVP-Quellcode unter `android/` mit Kachel, Foreground-Service und lokalem Upload. Noch kein erfolgreicher APK-Build oder Gerätetest. Kachel fordert bei Bedarf Entsperren an. Webaufnahme ersetzt die App nicht.
- **Google privat / Outlook geschäftlich:** Leseadapter sind implementiert, brauchen aber OAuth-App-Konfiguration und Kontofreigaben. Noch kein Test gegen persönliche Konten. ICS-Import funktioniert ohne Kontoanmeldung. Siehe `docs/integrations.md`.
- **Schweizerdeutsch:** Base-Modell und persönliches Glossar sind ein Startpunkt, keine garantierte Dialekterkennung. Korrekturbeispiele können später als Datengrundlage dienen.
- **Natürlich klingende eigene Stimme:** eSpeak ist der lokale funktionale Fallback; eine bessere Offline-Stimme muss hardwarebezogen getestet werden.
- **Abliterated-Modell:** Kleine huihui-Qwen3-Variante als Installationskandidat vorbereitet; auf dem Nutzer-Laptop noch nicht geprüft. Zwei einfache Setup-Tests ersetzen keine umfassende Qualitätsprüfung. Der optionale Update-Timer aktualisiert nur diesen gewählten Tag und nimmt keine neue Modellwahl vor.
- **Bees live:** muss an das aktuelle, geprüfte Berichtsschema und die tatsächlichen lokalen Dateien angeschlossen werden.

## Tests

```bash
python3 -m unittest discover -s tests -v
```

Integrationstests verwenden eine temporäre Datenbank und eine simulierte Ollama-Antwort. Sie handeln nicht und rufen keine privaten Dienste auf. Reale Audio-/Dialektqualität und Pixel-Sperrbildschirmverhalten müssen am Gerät getestet werden.

Modellquelle: https://ollama.com/huihui_ai/qwen3-abliterated:1.7b — Community-Variante laut Modellkarte per Abliteration abgeleitet, kein Versprechen über Antwortqualität.
