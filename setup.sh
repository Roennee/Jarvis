#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
echo 'Installiere lokale Sprache und Kalenderimport. Der erste Lauf lädt Pakete und das Sprachmodell herunter.'
sudo apt-get update
sudo apt-get install -y python3-venv espeak-ng
python3 -m venv .venv
.venv/bin/python -m pip install -r requirements.txt
mkdir -p models
.venv/bin/python - <<'PY'
from pathlib import Path
path=Path('models/whisper-base')
if not all((path/name).exists() for name in ('model.bin','config.json','tokenizer.json')):
    from huggingface_hub import snapshot_download
    snapshot_download('Systran/faster-whisper-base', local_dir=str(path))
print('Sprachmodell lokal vorhanden. Schweizerdeutsch zunächst mit echten Aufnahmen testen.')
PY
echo 'Fertig. Server stoppen und mit bash start.sh neu starten.'
