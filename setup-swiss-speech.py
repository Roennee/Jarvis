#!/usr/bin/env python3
"""Download an explicitly selected Swiss German ASR candidate. No user audio is uploaded."""
import argparse,json,sqlite3,os
from pathlib import Path
from speech_profile import SWISS_REPO,SWISS_REVISION,ready
ROOT=Path(__file__).resolve().parent
parser=argparse.ArgumentParser();parser.add_argument('--activate',action='store_true');args=parser.parse_args()
from huggingface_hub import snapshot_download
print('Lade Schweizerdeutsch-Modell. Grosser Download; CPU-Geschwindigkeit auf dem Surface ist noch ungeprüft.')
snapshot_download(SWISS_REPO,revision=SWISS_REVISION,allow_patterns=['ct2/*','README.md','LICENSE*'],local_dir=str(ROOT/'models'/'whisper-swiss'))
if not ready(ROOT,'swiss'):raise SystemExit('Modell unvollständig; bisheriges Profil bleibt erhalten.')
if args.activate:
    db=Path(os.environ.get('JARVIS_DATA_DIR',str(ROOT)))/'jarvis.sqlite3'
    if not db.is_file():raise SystemExit('Jarvis einmal starten, dann erneut aktivieren.')
    with sqlite3.connect(db) as c:c.execute('INSERT OR REPLACE INTO settings VALUES(?,?)',('speech_profile',json.dumps('swiss')))
print('Modell bereit.'+(' Schweizerdeutsch-Profil aktiviert.' if args.activate else ' Unter Einstellungen aktivierbar.'))
