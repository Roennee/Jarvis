#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
if [ -n "$(git status --porcelain --untracked-files=no)" ]; then
  echo 'Lokale Codeänderungen vorhanden. Update abgebrochen, nichts überschrieben.' >&2; exit 1
fi
mkdir -p backups
python3 - <<'PY'
import sqlite3
from pathlib import Path
from datetime import datetime,timezone
p=Path('jarvis.sqlite3')
if p.exists():
    target=Path('backups')/('before-update-'+datetime.now(timezone.utc).strftime('%Y%m%d-%H%M%S')+'.sqlite3')
    with sqlite3.connect(p) as src,sqlite3.connect(target) as dst: src.backup(dst)
    print('Datenbank gesichert:',target)
PY
git pull --ff-only
if systemctl --user is-active --quiet jarvis.service; then
  systemctl --user restart jarvis.service
  echo 'Jarvis-Dienst neu gestartet.'
else
  echo 'Code aktualisiert. Manuellen Server stoppen und bash start.sh ausführen.'
fi
