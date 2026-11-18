# Lokale Sprachkomponenten

- Piper: https://github.com/OHF-Voice/piper1-gpl — GPL-3.0. Wird als separat installiertes Programm aufgerufen; Bibliotheken und Modellgewichte werden nicht ins Repository kopiert.
- Thorsten-Stimme: https://huggingface.co/rhasspy/piper-voices/tree/main/de/de_DE/thorsten/medium — Modellkarte nennt CC0 für den Sprachdatensatz und mindestens Piper 1.5. Keine Imitation von Richard David Precht.
- faster-whisper: https://github.com/SYSTRAN/faster-whisper — lokale CPU-Transkription.
- Whisper-Base-Gewichte: https://huggingface.co/Systran/faster-whisper-base — werden einmalig für lokale Nutzung heruntergeladen.
- eSpeak NG: https://github.com/espeak-ng/espeak-ng — einfacher lokaler Fallback.

Die erste Einrichtung braucht Internet für Pakete und Gewichte. Die eigentliche Synthese und Transkription erfolgen auf dem Laptop; Kalenderabrufe gehen ausschliesslich nach eigener OAuth-Anmeldung an den jeweiligen Kalenderanbieter. Die Anwendung enthält keine Fish-Audio-API.
