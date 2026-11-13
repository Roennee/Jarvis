# Verbindungen und Einrichtungsbedarf

## Google privat · Leseadapter implementiert

1. Google Calendar API in einem eigenen Google-Cloud-Projekt aktivieren.
2. OAuth-Consent für privaten Nutzer konfigurieren; bei externer Test-App das eigene Konto als Testnutzer ergänzen.
3. OAuth-Client **Desktop-App** erstellen und die JSON ausschliesslich lokal unter `private/google-client.json` ablegen.
4. Auf dem Laptop `.venv/bin/python calendar_sync.py google-login` starten und im geöffneten Browser anmelden.
5. In Jarvis „Kalender → Google abrufen“. Es wird nur der primäre Kalender gelesen, inklusive Wiederholungen im Zeitfenster. Kein Zurückschreiben.

Scope: `calendar.readonly`. Eine Test-App kann nach Ablauf ihrer Freigabe erneute Anmeldung benötigen. Der Adapter ist mit simulierten API-Antworten geprüft; persönliche Anmeldung und echte Kalenderdaten sind noch zu testen.

## Outlook geschäftlich · Leseadapter implementiert

1. Eine vom Arbeitgeber zulässige Microsoft-Entra-Public-Client-App registrieren. Delegierte Berechtigung `Calendars.Read`, Device-Code/Public-Client-Flow erlauben. Tenant-Richtlinien können Administratorfreigabe benötigen.
2. Lokal `private/microsoft-app.json` erstellen: `{"client_id":"DEINE-APP-ID","tenant":"DEINE-TENANT-ID"}`.
3. `.venv/bin/python calendar_sync.py microsoft-login` starten. Den lokal angezeigten Anmeldecode ausschliesslich auf der angegebenen Microsoft-Seite eingeben, nicht im Chat teilen.
4. „Kalender → Outlook abrufen“. Es wird nur der Standardkalender gelesen. Wiederholungen werden über CalendarView aufgelöst; kein Zurückschreiben.

Kalenderabrufe übertragen technisch notwendige Konto-/Anfragedaten an Google oder Microsoft. Die Modell- und Notizverarbeitung bleibt lokal. Token-Refresh erfolgt beim Abruf, kein Hintergrund-Polling.

## Kalenderdateien und lokale Planung

ICS-Dateien aus Google oder Outlook lassen sich ohne OAuth importieren. Zeitraum: letzte 30 bis nächste 370 Tage. Wiederimport erkennt gleiche Einträge; entfernte Einträge werden bei ICS nicht gelöscht. Direkte Kontoabrufe ersetzen dagegen nur den zuvor gespeicherten Stand dieses Live-Anbieters und erst nach vollständig erfolgreichem Abruf. Lokal angelegte Termine bleiben getrennt. Zeiten werden als UTC gespeichert und in Europe/Zurich dargestellt. Kein Kalender-Schreibzugriff in Version 0.4.

## Tokenablage

Tokens liegen mit Dateirechten 0600 unter `private/`, nicht zusätzlich verschlüsselt. Benutzerzugang und Datenträgerverschlüsselung schützen sie. `private/` ist von Git und JSON-Export ausgeschlossen. Keine Kennwörter, Client-Dateien oder Tokens im Chat oder öffentlichen Repository teilen.

## Training

Eigene Oberfläche oder CSV mit `day,title,plan,result`. Beispiele unter samples sind Formatbeispiele, keine Trainingsvorgaben. Eine bestehende Trainings-App wird erst nach Auswahl der tatsächlichen Datenquelle angebunden.

## Depot

CSV `symbol,quantity,price,currency` erzeugt eine Momentaufnahme. Datum ist der Importzeitpunkt. Dezimalpunkt, Währung dreistellig. Die Historie bleibt erhalten; keine Livekurse, Währungsumrechnung oder automatische Handelsausführung. Depot und Bees sind getrennt.

## Bees · direkter lokaler Leseadapter

Nach Prüfung von `live_bees.py` und `live_experiment_report.py` im Trading-Repository liest Jarvis ausschliesslich `data/live_bees/state.json` unter `~/trading-experiment` (alternativ Umgebungsvariable `JARVIS_BEES_DIR`). Angezeigt werden nur `capital_eur`, gespeicherte `last_equity`-/`fees_eur`-Werte bekannter Bees und das Vorhandensein von Pending. Unbekannte Beträge bleiben unbekannt. Gebühren werden nicht ein zweites Mal abgezogen; externe AI-Kosten sind nicht enthalten.

Keine Imports des Trading-Codes, Subprozesse, Markt-/Kontenabfragen oder Zugriffe auf Konfigurationsdateien und Schlüssel. Dateizeitpunkt ist kein Beleg für laufenden Watcher oder aktuelle Preise. Feldauswahl mit synthetischem Zustand und Secret-Ausschluss getestet; echte Surface-Datei noch zu prüfen. Zusätzliche JSON-Berichte können manuell importiert werden.

## Pixel-Aufnahme

Browser: Seite offen und entsperrt halten. Android-Prototyp unter `android/`: Quick-Settings-Kachel öffnet die App, bei Bedarf nach Entsperren. Aufnahme startet aus sichtbarer Activity nach Mikrofonfreigabe. Microphone-Foreground-Service mit Stop-Benachrichtigung, lokale Speicherung, begrenzte Wake-Lock-Dauer und anschliessender expliziter Upload über Tailscale. Erfolgreicher APK-Build ist kein Gerätetest. Aufnahme ohne Entsperren wird nicht zugesagt.

## Gerätetests bei Rückkehr

1. Bestehende Notizen und neuer Chat bleiben nach Reload erhalten.
2. Piper-Serverstimme am Pixel nach bewusstem Klick testen.
3. 20 Sekunden Standarddeutsch aufnehmen, speichern und transkribieren.
4. Gleicher Text auf Schweizerdeutsch; Korrekturbeispiele speichern.
5. Mobilfunk, Netzunterbrechung und lokaler Audiodownload prüfen.
6. Kalenderanmeldung und Termine gegen Originalkalender vergleichen.
7. Android-App: Kachel, Mikrofonfreigabe, Sperren, Stoppen und Upload testen.
