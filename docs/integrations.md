# Verbindungen: Status und Einrichtungsbedarf

## Google privat

Aktuell: ICS-Import in der Kalenderansicht. Einmalige Imports erzeugen keine dauerhafte Verbindung. Vollständige Umsetzung benötigt eine Google-Cloud-OAuth-App (Desktop- oder Web-Client passend zum späteren Flow), aktivierte Calendar API, explizite Nutzeranmeldung, verschlüsselte oder OS-geschützte Tokenablage und getrennte Lese-/Schreibfreigaben. Private OAuth-Dateien gehören nicht in Git.

## Outlook geschäftlich

Aktuell: ICS-Import. Direkter Microsoft-Graph-Zugriff benötigt eine Entra-App-Konfiguration und geeignete delegierte Kalenderberechtigungen. Tenant-Richtlinien können die Freigabe durch einen Administrator erfordern. Zugang zu diesem Chat oder Tailscale verleiht keinen Kalenderzugriff. Keine Geschäftskennwörter in Jarvis oder Git hinterlegen.

## Training

Die erste Anbindung erfolgt über die eigene Oberfläche oder CSV mit den Spalten `day,title,plan,result`. Bei Auswahl einer bestehenden Trainings-App wird ein passender Adapter ergänzt. Beispiel unter samples ist ausdrücklich kein empfohlener Trainingsplan.

## Depot

CSV `symbol,quantity,price,currency` importiert eine Momentaufnahme. Datum wird beim Import erfasst. Dezimalzahlen mit Punkt, Währung dreistellig. Snapshot-Historie bleibt vollständig. Keine Vermischung mit Bees und keine automatische Handelsausführung.

## Bees

Version 0.4 bewahrt manuell importierte JSON-Berichte exakt auf. Vor direkter Anbindung müssen die tatsächlichen Dateien, Schema, Fehlersignale, UTC-Zeitstempel und Berechnungsdefinitionen aus dem Trading-Repository geprüft werden. Keine geratenen Gewinnberechnungen. Kein Zugriff auf API-Secrets.

## Pixel-Sperrbildschirm

Aktuell Browseraufnahme: Bildschirm offen lassen. Zielarchitektur der nativen App: Quick-Settings-Kachel öffnet eine sichtbare Activity; explizite Mikrofonfreigabe; Aufnahme als Microphone-Foreground-Service mit sichtbarer Stop-Aktion; verschlüsselter Upload über Tailscale; lokale Warteschlange mit Uploadstatus und Wiederholungslogik. Aufnahme ohne Entsperren hängt von Android-Sicherheitsregeln ab und wird nicht zugesagt. Noch keine App gebaut oder am Pixel geprüft.

## Reihenfolge der Gerätetests

1. Bestehende Notizen und neuer Chat bleiben nach Seitenreload erhalten.
2. Lokale Serverstimme spielt am Pixel nach bewusstem Klick.
3. 20 Sekunden Standarddeutsch aufnehmen, speichern und transkribieren.
4. Gleicher Text auf Schweizerdeutsch; Korrekturbeispiele speichern.
5. Mobilfunk mit Tailscale, Netzunterbrechung, Download als Rückfalloption.
6. Kalenderimporte und Wiederholungen gegen den Originalkalender prüfen.
7. Erst danach Kontoverbindungen und native App ausbauen.

## Implementierter Leseadapter (0.4)

Die Adapter sind im Repository vorhanden; sie wurden mit simulierten API-Antworten geprüft, nicht mit persönlichen Konten. Einrichtung:

### Google

1. In Google Cloud Calendar API aktivieren, OAuth-Consent für privaten Nutzer konfigurieren (gegebenenfalls externe Test-App mit eigenem Konto als Testnutzer).
2. OAuth-Client **Desktop-App** erstellen. JSON ausschliesslich lokal unter `private/google-client.json` ablegen.
3. Auf dem Laptop `.venv/bin/python calendar_sync.py google-login` starten und im geöffneten Browser anmelden.
4. In Jarvis „Kalender → Google abrufen“ wählen. Es wird nur der primäre Kalender gelesen. Read-only-Scope; kein Zurückschreiben.
5. Eine Test-App kann erneute Anmeldung nach Ablauf der Tokenfreigabe benötigen.

### Outlook

1. In Microsoft Entra eine vom Arbeitgeber zulässige Public-Client-App registrieren. Delegierte Berechtigung `Calendars.Read`, Device-Code/Public-Client-Flow erlauben; gegebenenfalls Administrator erforderlich.
2. Lokal `private/microsoft-app.json` erstellen: `{"client_id":"DEINE-APP-ID","tenant":"DEINE-TENANT-ID"}`.
3. `.venv/bin/python calendar_sync.py microsoft-login` starten. Nur den dabei lokal angezeigten Microsoft-Anmeldecode auf der angegebenen Microsoft-Seite eingeben; nicht im Chat teilen.
4. „Kalender → Outlook abrufen“. Es wird nur der Standardkalender gelesen, inklusive Wiederholungsinstanzen. Kein Zurückschreiben.

Die Tokens liegen mit Dateirechten 0600 unter `private/`; sie sind nicht zusätzlich verschlüsselt. Der Benutzerzugang und die Datenträgerverschlüsselung schützen sie. `private/` ist von Git und JSON-Export ausgeschlossen. OAuth-Konfiguration und Anmeldung sind noch vom Nutzer vorzunehmen. Token-Refresh erfolgt bei einem Abruf, kein Hintergrund-Polling. Externe Abrufe übertragen technisch notwendige Anfrage-/Kontodaten an Google/Microsoft; Modell und Notizverarbeitung bleiben lokal.
