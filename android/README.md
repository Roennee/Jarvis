# Jarvis Recorder · Android MVP (Quellcode)

Eigenständige Android-App mit sichtbarer Mikrofonaufnahme als Foreground-Service, Stop-Benachrichtigung, maximal 2 Minuten, lokaler Dateiablage und bewusst ausgelöstem Upload zur Tailscale-HTTPS-Adresse. Quick-Settings-Kachel öffnet die App und fordert bei gesperrtem Gerät Entsperren an; keine Umgehung der Gerätesperre. Nach gestarteter Aufnahme darf der Bildschirm ausgeschaltet werden. Am Pixel noch nicht verifiziert.

## Build

JDK 17, Android SDK 35, Gradle 8.11.1:

```bash
cd android
gradle :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Erstinstallation einer Debug-App muss am Pixel bewusst zugelassen werden. Kein Play-Store-Release, keine Produktionssignierung. APK wurde in GitHub Actions kompiliert; Aufnahme, Sperrbildschirm und Upload sind am Pixel noch zu prüfen.

## Einrichtung

1. Tailscale am Pixel verbinden.
2. App öffnen und die eigene vollständige Jarvis-Adresse speichern, zum Beispiel `https://dein-laptop.dein-netz.ts.net`.
3. Mikrofon-/Benachrichtigungsfreigabe erteilen.
4. Quick Settings bearbeiten und „Jarvis aufnehmen“ ergänzen.
5. Kachel antippen, bei Bedarf entsperren. Aufnahme stoppen, „An Laptop senden“ wählen.
6. Text in der Jarvis-Weboberfläche unter Aufnehmen prüfen.

Uploads erfolgen nur nach Klick. Keine automatischen Uploads nach Sperren oder bei Neustart. Fehlgeschlagene Uploads behalten die Originaldatei für erneuten Versuch. Erfolgreiche Uploads bekommen eine lokale Markierung. Dateien werden nicht automatisch gelöscht. Keine Cloud-Transkription. Nach einem App-Prozessabbruch können `.pending`-Fragmente liegenbleiben und müssen später geprüft werden. Noch keine robuste Wiederherstellung einer während des Abbruchs laufenden Aufnahme; MVP.

## Testbedarf

Build, Mikrofonrechte auf Pixel, Start per Kachel, Lock/Unlock, Stop-Benachrichtigung, Netzwerkverlust, Doppelklicks, Uploadgrössen, Verbindung zum aktualisierten Server. Ein App-Update benötigt dieselbe Signatur; Debug-Key nicht bei jedem Build wechseln, falls lokale Aufnahmen erhalten bleiben sollen.

APK-Builds: https://github.com/Roennee/Jarvis/actions/workflows/jarvis.yml — Artefakt `jarvis-recorder-debug`. Kein Gerätetest und keine Produktionssignierung.


## v0.2: Hey Jarvis und stiller Zuhörmodus (Prototyp)
Server v0.6.0 erforderlich. APK über GitHub Actions bauen; bestehende App durch neue APK ersetzen. Bei unterschiedlicher Debug-Signatur kann Android eine Neuinstallation verlangen: lokal gespeicherte Aufnahmen vorher zum Laptop senden.

1. In der entsperrten App die HTTPS-Tailscale-Adresse speichern. Mikrofon und Benachrichtigungen erlauben.
2. Für Wakeword den optionalen Modellbutton verwenden. Er lädt `vosk-model-small-en-us-0.15` direkt über HTTPS vom Vosk-Anbieter (ca. 40 MB komprimiert). Danach funktioniert die englische Erkennung lokal. Vosk Android 0.3.75, Apache-2.0; Modell siehe Anbieter-Lizenz.
3. Sitzungslänge wählen: 30 oder 60 Minuten oder bis zu 8 Stunden. Hey Jarvis aktivieren. Mit englischem „Hey Jarvis“ starten, kurz pausieren, dann Wunsch auf Deutsch sagen. Nach Sprechpause wird der Wunsch übertragen. Die Erkennung ist ein Vosk-Sprachmodell mit begrenzter Grammatik, kein energiesparender DSP-Wakeword-Detektor; Fehlaktivierungen sind möglich.
4. Alternativ Still zuhören aktivieren: sprachaktive Abschnitte (bis 45 Sekunden) werden übertragen. Keine Sprachausgabe. Das ist eine einfache Lautstärkeschwelle, keine robuste Sprechertrennung. Hintergrundgeräusche können Abschnitte auslösen. Der Modus erfasst auch Stimmen in seiner Umgebung; nur in passenden Situationen aktivieren und Beteiligte informieren.
5. Schalter für die Sperrbildschirmkachel einmal aktivieren. In Android Schnellzugriffe bearbeiten und „Jarvis still zuhören“ hinzufügen. Danach herunterwischen und Kachel tippen. Sie öffnet eine reduzierte Oberfläche oberhalb des Sperrbildschirms, startet 30 Minuten stilles Zuhören und zeigt keine privaten Inhalte. Je nach Pixel-/Android-Einstellung ist Entsperren trotzdem nötig. Die App ersetzt keine frei konfigurierbare System-Wischgeste. Erneutes Tippen oder die Benachrichtigung stoppt den Modus.

Mikrofonzugriff ist sichtbar und endet bei Stoppen, Sitzungsende oder Android-Prozessende. Kein Boot-Autostart und keine Garantie, dass Android einen ganztägigen Dienst laufen lässt. CPU-Wake-Lock wird nur während der gewählten Sitzung gehalten. Akkuverbrauch und Pixel-Sperrbildschirmverhalten müssen am Gerät gemessen werden.

Im Wake-Modus wird Warteton-Audio nicht gespeichert/hochgeladen; erst nach erkanntem Ruf wird eine WAV-Datei erzeugt. Im Stillmodus werden sprachaktive Abschnitte lokal gespeichert und an den privaten Laptop gesendet. Laptop, Ollama, Server und Tailscale müssen erreichbar sein. Uploadwarteschlange ist begrenzt, Audioablage auf 100 MB begrenzt. Bei Ausfällen bleiben WAV-Dateien unter app-internem `voice-pending` erhalten; über „Ausstehende Sprachdateien erneut senden“ lassen sie sich nach Stoppen nachsenden. Verarbeitete Audiodateien können mit einem eigenen Button gelöscht werden. Es erfolgt kein automatisches Löschen auf dem Laptop.

Automatische Termine werden ausschliesslich lokal in Jarvis erstellt. Vorerst versteht die Datumserkennung heute/morgen/übermorgen oder YYYY-MM-DD mit expliziter Uhrzeit. Ohne Dauer gelten sichtbar 30 Minuten. Unklare oder nicht unterstützte Datumsangaben werden als Notiz zur Klärung abgelegt. Keine Google-/Outlook-Schreibzugriffe, Einladungen oder Handelsaktionen. Ein kleines LLM kann trotz Quellprüfung falsch entscheiden; bitte Einträge prüfen. Sprachfragen haben zunächst keinen Kalender-/Notizkontext und keine dauerhafte Dialoghistorie.
