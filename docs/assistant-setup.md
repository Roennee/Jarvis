# Jarvis 0.9 / Android 0.5 einrichten und prüfen

Dies ist dieselbe Jarvis-App: Paket `ch.jarvis.companion`, bestehende Datenbank,
Tailscale-Adresse und bestehender Signaturschlüssel. Android-VersionCode 9.

## Aktualisieren

Manuellen Server mit Ctrl+C beenden. Im bisherigen Jarvis-Ordner:

```bash
git pull --ff-only
sudo apt-get update
sudo apt-get install -y ffmpeg
bash start.sh
```

Keine neuen Python-Pakete für die neuen Basisfunktionen nötig. Die vorhandene
Sprachinstallation bleibt erhalten. Vor Schema-11-Migration wird eine Sicherung
angelegt. Falls die Sprachinstallation noch fehlt: `bash setup.sh`.
Die neue APK aus dem Artefakt `jarvis-android` des erfolgreichen Actions-Laufs
installieren. Keinen neuen Schlüssel erzeugen und die App nicht deinstallieren.

## Sprechen und Aufnahme

Auf dem Pixel unter **Sprechen**: „Jetzt sprechen“ startet den nativen
AudioRecord-Weg. „Hey Jarvis“ benötigt das einmalig installierte Vosk-Wakemodell.
„Still zuhören“ sammelt ausschliesslich Vorschläge ohne Sprachausgabe.
Der Aufnahmebutton berücksichtigt den gewählten Modus: Gespräch/Stillmodus nutzen
den nativen Zuhördienst; Transkript/Lernaufnahme nutzen native AAC-Monoaufnahme
mit anschliessendem Upload. Lernaufnahmen enden nach 30 Sekunden, ausdrückliche
Transkriptaufnahmen nach zwei Minuten. Misslungene Uploads bleiben auf dem Pixel
und können unter „Sprache einrichten“ nachgesendet werden.

Mikrofon und Benachrichtigungen erlauben. Bei parallelem Zuhören zuerst stoppen,
bevor eine Lern-/Transkriptaufnahme beginnt. Berechtigungen werden nur nach einer
bewussten Aktion angefragt. Die Aufnahme erhält einen sichtbaren Systemhinweis.

Auf dem Laptop das richtige Mikrofon auswählen. Nach der ersten Freigabe sind
auch die Gerätenamen sichtbar. Bei starkem Rauschen USB-/Headset-Mikrofon mit dem
internen Mikrofon vergleichen. Jarvis spielt das Mikrofon nicht live ab.

Der neue FFmpeg-Filter reduziert lokal Rauschen (80-Hz-Hochpass, Tiefpass,
spektrale Unterdrückung). Keine automatische Lautheitsanhebung. Vor der ASR wird
nur eine temporäre Kopie verarbeitet. In der Browser-Vorschau kann das Original
verglichen werden. Bereits gefilterte Browseraufnahmen werden bei ASR nicht
nochmals gefiltert. Lernoriginale bleiben verfügbar. Der Filter ist abschaltbar,
falls leise Wörter beeinträchtigt werden. Maximal zehn Minuten pro Filterlauf;
längere Dateien werden nicht still abgeschnitten. Für die Erkennung bleibt bei
Filterfehler das Original verwendbar.

Audioantworten unterstützen HTTP-Range, 206, Suffix-Ranges, 416 und HEAD.
Gefilterte WAV-Vorschauen haben eine definierte Dauer; importierte unvollständige
WebM-Dateien können erst nach WAV-Umwandlung zuverlässig spulbar sein.

## Sperrbildschirm

Unter **Sprechen → Sperrbildschirm-Zuhören einrichten** den Zugriff erlauben und
Androids „Kachel hinzufügen“ bestätigen. Mikrofon/Benachrichtigungen vorher oder
im Einrichtungsdialog erlauben. Auf dem gesperrten Pixel von oben herunterziehen
und **Jarvis still zuhören** antippen. Die Kachel öffnet eine reduzierte
Sperrbildschirm-Steuerung und startet dort eine 30-Minuten-Sitzung.

Dies ist eine Android-Schnelleinstellung, kein frei platzierbarer Ersatz für die
von Pixel vorgegebenen zwei Sperrbildschirm-Verknüpfungen. Android bestimmt die
verfügbaren Positionen und kann einen Start bei restriktiven Einstellungen
verhindern. Der reduzierte Bildschirm zeigt keine privaten Vorschläge/Kalender.
Stoppen über Kachel, Steuerbildschirm, App oder Benachrichtigung. Kein Autostart
nach Neustart. Zugriff lässt sich im selben Einrichtungsdialog widerrufen.

## Kalender

Auf dem Pixel **Kalender → Pixel-Kalender verbinden / aktualisieren** öffnen,
Lesezugriff erlauben und die gewünschten Kalender auswählen. Die Auswahl ist
anfangs leer. Nur ausgewählte Kalender werden an den privaten Jarvis-Laptop
übertragen (30 Tage zurück bis 370 Tage voraus, maximal 3000 Termine).
Wiederholungen werden durch Android aufgelöst. Ein erneuter Abruf ersetzt die
vorherige Pixel-Momentaufnahme; lokale Jarvis-Termine bleiben erhalten.

Google-Kalender sind gewöhnlich bereits im Android-Kalenderspeicher vorhanden.
Outlook muss über seine Android-Kalendersynchronisierung verbunden sein.
Arbeitsprofil-/Organisationsrichtlinien können dies verhindern. Jarvis umgeht
solche Richtlinien nicht. Es handelt sich um eine Momentaufnahme, keine
unbemerkte Hintergrundsynchronisierung; nach Änderungen erneut abrufen.

Bestätigte Jarvis-Termine: **In Kalender-App speichern** öffnet die Android-
Terminmaske. Dort Kalender wählen und speichern. Keine Einladungen/Teilnehmer
werden automatisch versandt. Im Laptop-Browser wird eine einzelne ICS-Datei
exportiert, die Google/Outlook importieren können.

Fragen wie „Welche Termine habe ich morgen?“ beantwortet Jarvis im Chat und
Gespräch direkt aus seiner Agenda. Keine Netz-/Modellabfrage nötig. Fehlende
Termine können auf einen veralteten oder fehlenden Abruf zurückgehen.

### Direkte Google-Anbindung am Laptop

1. In Google Cloud Calendar API aktivieren, OAuth-Desktop-App erstellen und das
   eigene Konto als Testnutzer zulassen, falls die App im Testmodus ist.
2. Clientdatei lokal nach `private/google-client.json` kopieren. Nicht ins Git
   oder einen Chat kopieren.
3. `.venv/bin/python calendar_sync.py google-login` ausführen.
4. In Jarvis „Google abrufen“. Nur Lesezugriff auf den primären Kalender.

### Direkte Microsoft-Anbindung am Laptop

1. Eigene Entra-App mit geeignetem Kontotyp und Public-Client/Device-Flow
   registrieren; delegierten Zugriff `Calendars.Read` erlauben.
2. `private/microsoft-app.json` lokal erstellen mit `client_id` und `tenant`
   (`common`, `consumers`, `organizations` oder konkrete Tenant-ID je nach App).
3. `.venv/bin/python calendar_sync.py microsoft-login` ausführen und die
   Geräteanmeldung im Browser abschliessen.
4. In Jarvis „Outlook abrufen“. Organisationsfreigaben können nötig sein.

Diese direkten Adapter waren bereits vorhanden und bleiben lesend. Ein Client-
ID/OAuth-Konto kann nicht von Jarvis stellvertretend erfunden werden.

## Gratis-Cloud

Unter Einstellungen einen eigenen OpenRouter-API-Schlüssel eingeben und
**Gratis-Cloud** wählen. Schlüssel liegt nur in `private/cloud-key.json` mit
restriktiven Dateirechten; nicht in SQLite, Export, Git oder Browser-Speicher.
Ein leeres Passwortfeld belässt den vorhandenen Schlüssel. Entfernen schaltet
auf lokal zurück.

Auswahl: `openrouter/free` (wechselnde Modelle, keine Bestenauswahl) oder eine im
aktuellen Katalog bestätigte `:free`-Variante. Vor jeder Anfrage an eine konkrete
Variante werden Nullpreise erneut geprüft. Kostenobergrenze 0 für Ein-/Ausgabe,
kein kostenpflichtiger Modell-/Provider-Fallback. Limits/fehlende Kapazität
erscheinen als Fehler; kein stiller Wechsel zu einer bezahlten API.

Chatinhalt wird im Cloudmodus an OpenRouter und den ausgewählten Anbieter
übertragen. Erkanntes Gespräch wird erst mit zusätzlicher Sprachfreigabe in den
Einstellungen übertragen. Die Audio-Spracherkennung bleibt lokal. Datenregeln
und Verfügbarkeit des Anbieters prüfen. DeepSeeks direkte API ist nicht gratis;
DeepSeek ist hier nur wählbar, wenn eine echte Gratis-Variante im Katalog steht.

**Aktives Modell mit Beispielaufgaben testen** sendet nur einen erfundenen
Beispielsatz an das gewählte Modell und protokolliert JSON-Aufgabenerkennung und
Antwortzeit. Ein Test beweist keine allgemeine Qualität. Ohne eigenen Schlüssel
konnte die reale Cloudantwort bei der Entwicklung nicht geprüft werden.

## Wöchentliche Modellsuche

Nach dem Serverstart und dann nach jeweils sieben Tagen ruft Jarvis öffentliche
Katalogdaten von OpenRouter und Hugging Face ab. Ein schlafender/ausgeschalteter
Laptop prüft beim nächsten Start nach. Keine persönlichen Inhalte werden für
die Katalogsuche gesendet. Letzte Kandidaten bleiben bei Netzfehler sichtbar,
mit Fehlerhinweis und Prüfzeitpunkt. Lokale GGUF-Kandidaten werden auf kleine
0.5–3B-Modelle begrenzt; RAM-Bedarf und Ollama-Kompatibilität sind vor Installation
zu prüfen. Trending/Neuheit ist kein Qualitätsbeweis. Kein automatischer Download
oder Modellwechsel. Abschalten unter Einstellungen.

Zusätzlich ist eine separate wöchentliche Recherche hier in ChatGPT eingerichtet.
Sie liefert eine quellengeprüfte Empfehlung, ändert aber deine App nicht selbst.

## Abnahme auf deinen Geräten

- Pixel: Gespräch, Transkript und eigene Lernaufnahme je einmal starten/stoppen;
  danach zweite Sitzung; verweigerte Berechtigung muss verständlich enden.
- Laptop: 5 Sekunden Ruhe, normal sprechen, Original/Filter vergleichen.
  Leise Wörter müssen verständlich bleiben. Richtiges Mikrofon auswählen.
- Audio: bei pausierter und laufender WAV-/AAC-Wiedergabe vor/zurück spulen.
- Kalender: zwei ausgewählte Kalender, wiederkehrender und ganztägiger Termin,
  erneuter Import ohne Duplikate; bestätigten Jarvis-Termin extern speichern.
- Gesperrter Pixel: Kachel starten/stoppen, keine Sprachausgabe/privaten Inhalte.
- Cloud: eigenes Konto verbinden, künstlichen Kurztest ausführen, Schlüssel
  entfernen; dann lokales Gespräch prüfen.

Automatische Tests decken Datenintegrität, Preisprüfung, Cloudfreigabe,
Range-Wiedergabe und synthetische Filterwirkung ab. Hardware-Mikrofone,
Android-Berechtigungsdialoge, echter Sperrbildschirm, Konten und Sprachqualität
müssen auf dem Pixel/Surface geprüft werden. Solange dein Laptop als Sprachserver
dient, muss er eingeschaltet, erreichbar und Jarvis gestartet sein.
