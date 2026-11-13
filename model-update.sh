#!/bin/bash
# Run explicitly on the laptop. Downloads model weights; no silent scheduling.
set -euo pipefail
cd "$(dirname "$0")"
command -v ollama >/dev/null
mkdir -p backups
exec 9>backups/model-update.lock
flock -n 9 || { echo 'Modellupdate läuft bereits.' >&2; exit 1; }
restart_service=0
if systemctl --user is-active --quiet jarvis.service; then
  restart_service=1
  systemctl --user stop jarvis.service
fi
restore_service() {
  if [ "$restart_service" = 1 ]; then systemctl --user start jarvis.service; fi
}
trap restore_service EXIT
if python3 - <<'CHECK'
import urllib.request
try:
    urllib.request.urlopen('http://127.0.0.1:8765/api/status',timeout=2)
except Exception:
    raise SystemExit(1)
CHECK
then
  echo 'Manuelle Jarvis-Instanz läuft. Zuerst stoppen, dann erneut versuchen.' >&2; exit 1
fi
python3 - <<'PY'
import json,sqlite3,subprocess,urllib.request,datetime
from pathlib import Path
model='qwen3:1.7b'
if Path('jarvis.sqlite3').exists():
    with sqlite3.connect('jarvis.sqlite3') as c:
        try:
            r=c.execute("SELECT value FROM settings WHERE key='model'").fetchone()
            if r:model=json.loads(r[0])
        except sqlite3.OperationalError:pass
if not isinstance(model,str) or model.startswith('-'):raise SystemExit('Ungültiges Modell')
backup='jarvis-rollback:'+datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%d%H%M%S')
subprocess.run(['ollama','cp',model,backup],check=True)
print('Vorheriges Modell gesichert als',backup)
try:
    subprocess.run(['ollama','pull',model],check=True)
    payload={'model':model,'think':False,'stream':False,'keep_alive':0,'prompt':'Schreibe auf Deutsch eine kurze Aufgabenliste aus dieser Notiz: Morgen Milch kaufen und um 14 Uhr Mischa anrufen.','options':{'num_ctx':2048,'num_predict':150,'temperature':0}}
    req=urllib.request.Request('http://127.0.0.1:11434/api/generate',data=json.dumps(payload).encode(),headers={'Content-Type':'application/json'})
    with urllib.request.urlopen(req,timeout=240) as response:r=json.load(response)
    answer=r.get('response','').strip()
    if not answer or r.get('done_reason')=='length':raise RuntimeError('Technischer Antworttest fehlgeschlagen')
    print('\nTestantwort (inhaltlich selbst prüfen):\n'+answer)
    Path('backups').mkdir(exist_ok=True)
    Path('backups/model-update-'+datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%d%H%M%S')+'.json').write_text(json.dumps({'model':model,'rollback':backup,'response':answer,'time':datetime.datetime.now(datetime.timezone.utc).isoformat()},ensure_ascii=False,indent=2))
    print('Rollback bei Qualitätsproblemen: ollama cp '+backup+' '+model)
except BaseException:
    subprocess.run(['ollama','cp',backup,model],check=True)
    print('Vorheriges Modell wiederhergestellt.')
    raise
PY
