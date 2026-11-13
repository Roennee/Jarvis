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
