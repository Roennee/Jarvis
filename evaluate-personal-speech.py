#!/usr/bin/env python3
"""Evaluate reviewed held-out clips; activate only after a conservative improvement check."""
import argparse,json,os,sqlite3,shutil
from pathlib import Path
from datetime import datetime
from speech_profile import model_path,word_errors,ready
ROOT=Path(__file__).resolve().parent
p=argparse.ArgumentParser();p.add_argument('--data-dir',default=os.environ.get('JARVIS_DATA_DIR',str(ROOT)));p.add_argument('--activate',action='store_true');args=p.parse_args()
data=Path(args.data_dir);folder=data/'private'/'speech-training';candidate=folder/'candidate-ct2';baseline=model_path(ROOT,'personal' if ready(ROOT,'personal') else 'swiss')
if not baseline.is_dir() or not candidate.is_dir():raise SystemExit('Schweizer Basismodell und persönlicher Trainingskandidat müssen eingerichtet sein.')
validation=[json.loads(line) for line in (folder/'validation.jsonl').read_text().splitlines() if line.strip()]
if len(validation)<3:raise SystemExit('Mindestens drei unabhängige korrigierte Prüfclips nötig.')
from faster_whisper import WhisperModel
from faster_whisper.audio import decode_audio
results=[]
# Release each model before loading the other, keeping RAM use bounded.
for path in [baseline,candidate]:
    model=WhisperModel(str(path),device='cpu',compute_type='int8',cpu_threads=2,local_files_only=True)
    rows=[]
    for clip in validation:
        segments,_=model.transcribe(clip['audio'],language='de',beam_size=3,vad_filter=True,condition_on_previous_text=False)
        text=' '.join(s.text.strip() for s in segments);errors,words=word_errors(clip['text'],text)
        rows.append({'id':clip['id'],'errors':errors,'words':words,'text':text})
    results.append(rows);del model
base_errors=sum(r['errors'] for r in results[0]);new_errors=sum(r['errors'] for r in results[1]);words=sum(r['words'] for r in results[0])
passed=new_errors<base_errors and all(new['errors']<=base['errors'] for base,new in zip(*results))
report={'baseline_wer':base_errors/max(1,words),'candidate_wer':new_errors/max(1,words),'passed':passed,'clips':len(validation),'baseline':results[0],'candidate':results[1]}
path=folder/'evaluation.json';path.write_text(json.dumps(report,ensure_ascii=False,indent=2));path.chmod(0o600)
print(json.dumps({k:v for k,v in report.items() if k not in ('baseline','candidate')},ensure_ascii=False))
if args.activate:
    if not passed:raise SystemExit('Prüfung nicht bestanden. Bisheriges Modell bleibt aktiv.')
    target=model_path(ROOT,'personal')
    if target.exists():target.rename(target.with_name('whisper-personal-backup-'+datetime.now().strftime('%Y%m%d%H%M%S')))
    shutil.copytree(candidate,target)
    with sqlite3.connect(data/'jarvis.sqlite3') as c:c.execute('INSERT OR REPLACE INTO settings VALUES(?,?)',('speech_profile',json.dumps('personal')))
    print('Persönliches Profil aktiviert. Verbesserung gilt nur für diese kleine Prüfung, nicht als Garantie für alle Gespräche.')
