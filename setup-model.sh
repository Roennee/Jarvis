#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
command -v ollama >/dev/null
model='huihui_ai/qwen3-abliterated:1.7b'
echo "Lade lokalen abliterated Kandidaten: $model (ca. 1,1 GB)."
ollama pull "$model"
python3 - <<'PY'
import json,re,sqlite3,urllib.request
from pathlib import Path
from datetime import datetime,timezone
model='huihui_ai/qwen3-abliterated:1.7b'
cases=[('Antworte nur mit der Zahl: Was ist 2 + 2?',lambda t: t.strip()=='4'),('Extrahiere eine kurze Aufgabenliste: Morgen um 14 Uhr Mischa anrufen und Milch kaufen. Antworte auf Deutsch.',lambda t:all(x in t.lower() for x in ('14','mischa','milch')))]
results=[]
for prompt,check in cases:
    data={'model':model,'think':False,'stream':False,'keep_alive':0,'prompt':prompt,'options':{'num_ctx':2048,'num_predict':200,'temperature':0}}
    req=urllib.request.Request('http://127.0.0.1:11434/api/generate',data=json.dumps(data).encode(),headers={'Content-Type':'application/json'})
    with urllib.request.urlopen(req,timeout=240) as r:response=json.load(r)
    text=response.get('response','').strip()
    passed=bool(text) and response.get('done_reason')!='length' and check(text)
    results.append({'prompt':prompt,'answer':text,'passed':passed})
    print(('PASS' if passed else 'FAIL')+': '+text)
Path('backups').mkdir(exist_ok=True)
Path('backups/model-selection-'+datetime.now(timezone.utc).strftime('%Y%m%d%H%M%S')+'.json').write_text(json.dumps({'model':model,'tests':results},ensure_ascii=False,indent=2))
if all(r['passed'] for r in results):
    with sqlite3.connect('jarvis.sqlite3') as c:
        c.execute('CREATE TABLE IF NOT EXISTS settings(key TEXT PRIMARY KEY,value TEXT NOT NULL)')
        c.execute('INSERT OR REPLACE INTO settings VALUES(?,?)',('model',json.dumps(model)))
    print('Kandidat für Jarvis ausgewählt. Diese zwei Tests sind keine umfassende Qualitätsprüfung.')
else:
    print('Bisherige Modellauswahl bleibt bestehen. Kandidat ist installiert, aber nicht automatisch gewählt.')
PY
