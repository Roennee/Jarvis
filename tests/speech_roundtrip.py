import subprocess,os,tempfile,time,json,urllib.request,base64,wave,io,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory() as t:
 env={**os.environ,'JARVIS_DATA_DIR':t,'JARVIS_PORT':'18765','JARVIS_ORIGIN':''}
 p=subprocess.Popen([sys.executable,str(ROOT/'server.py')],env=env,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
 base='http://127.0.0.1:18765'
 try:
  for _ in range(40):
   try:
    with urllib.request.urlopen(base+'/api/status') as r:s=json.load(r)
    break
   except OSError:time.sleep(.1)
  assert s['voice_engine']=='piper',s
  assert s['transcription'],s
  def post(path,body,raw=False):
   req=urllib.request.Request(base+path,data=json.dumps(body).encode(),headers={'Content-Type':'application/json','Origin':base,'X-Jarvis-Token':s['csrf']})
   with urllib.request.urlopen(req,timeout=100) as r:return r.read() if raw else json.load(r)
  audio=post('/api/voice',{'text':'Morgen gehen wir zum Training. Danach kaufen wir Milch.'},True)
  assert audio[:4]==b'RIFF'
  with wave.open(io.BytesIO(audio)) as f:assert f.getnframes()>16000
  rec=post('/api/recordings',{'mime':'audio/wav','audio':base64.b64encode(audio).decode()})
  post('/api/transcribe',{'id':rec['id']})
  for _ in range(90):
   with urllib.request.urlopen(base+'/api/recordings') as r:records=json.load(r)
   item=next(x for x in records if x['id']==rec['id'])
   if item['status'] in ('done','error'):break
   time.sleep(.5)
  assert item['status']=='done',item
  print('Transkript:',item['transcript'])
  assert 'training' in item['transcript'].lower(),item
  post('/api/corrections',{'id':rec['id'],'text':item['transcript']})
  with urllib.request.urlopen(base+'/api/notes') as r:notes=json.load(r)
  assert any(n['body']==item['transcript'] for n in notes)
  print('PASS: lokale Piper-WAV → Audio-Upload → Offline-Whisper-Hintergrundauftrag → geprüfte Notiz.')
 finally:p.terminate();p.wait()
