#!/usr/bin/env python3
import json, sqlite3, urllib.request, urllib.error, os
from urllib.parse import urlsplit
from pathlib import Path
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
ROOT=Path(__file__).resolve().parent
DB=ROOT/'jarvis.sqlite3'
REMOTE_ORIGIN=os.environ.get('JARVIS_ORIGIN','').rstrip('/')
if REMOTE_ORIGIN and (urlsplit(REMOTE_ORIGIN).scheme != 'https' or not urlsplit(REMOTE_ORIGIN).hostname.endswith('.ts.net') or urlsplit(REMOTE_ORIGIN).path):
 raise SystemExit('JARVIS_ORIGIN muss eine HTTPS-Tailscale-Adresse ohne Pfad sein.')
ALLOWED_ORIGINS={'http://127.0.0.1:8765','http://localhost:8765'}
if REMOTE_ORIGIN: ALLOWED_ORIGINS.add(REMOTE_ORIGIN)
def db():
 c=sqlite3.connect(DB); c.execute('CREATE TABLE IF NOT EXISTS notes(id INTEGER PRIMARY KEY, body TEXT NOT NULL, created TEXT DEFAULT CURRENT_TIMESTAMP)'); return c
class Handler(BaseHTTPRequestHandler):
 def reply(self,obj,status=200):
  data=json.dumps(obj,ensure_ascii=False).encode(); self.send_response(status); self.send_header('Content-Type','application/json'); self.send_header('Content-Length',str(len(data))); self.end_headers(); self.wfile.write(data)
 def do_GET(self):
  if self.path=='/':
   data=(ROOT/'index.html').read_bytes(); self.send_response(200); self.send_header('Content-Type','text/html; charset=utf-8'); self.end_headers(); self.wfile.write(data)
  elif self.path=='/api/notes':
   with db() as c: rows=c.execute('SELECT id,body,created FROM notes ORDER BY id DESC').fetchall()
   self.reply([dict(id=r[0],body=r[1],created=r[2]) for r in rows])
  elif self.path=='/api/status':
   try:
    with urllib.request.urlopen('http://127.0.0.1:11434/api/tags',timeout=3) as r: models=json.load(r).get('models',[])
    self.reply({'online':True,'models':[m['name'] for m in models]})
   except Exception: self.reply({'online':False,'models':[]})
  else: self.reply({'error':'Nicht gefunden'},404)
 def do_POST(self):
  # Browser requests must originate from this local application.
  if self.headers.get('Origin') not in ALLOWED_ORIGINS:
   return self.reply({'error':'Origin abgelehnt'},403)
  if self.headers.get('Content-Type','').split(';')[0]!='application/json': return self.reply({'error':'JSON erforderlich'},415)
  try:
   n=int(self.headers.get('Content-Length','0'))
   if n<1 or n>64000: return self.reply({'error':'Anfrage zu gross oder leer'},400)
   body=json.loads(self.rfile.read(n))
   if self.path=='/api/notes':
    note=body.get('body','').strip()
    if not note: return self.reply({'error':'Notiz ist leer'},400)
    with db() as c: c.execute('INSERT INTO notes(body) VALUES(?)',(note,))
    return self.reply({'ok':True})
   if self.path=='/api/chat':
    messages=body.get('messages',[])[-12:]
    if not messages or any(m.get('role') not in ('user','assistant') or not isinstance(m.get('content'),str) for m in messages): return self.reply({'error':'Ungültige Nachrichten'},400)
    payload={'model':'qwen3:1.7b','think':False,'stream':False,'keep_alive':0,'messages':[{'role':'system','content':'Du bist Jarvis, Raffaels persönlicher Assistent. Antworte knapp auf Deutsch. Du kannst hier nur chatten. Du hast keinen Kalender-, Handels- oder Dateizugriff. Behaupte niemals, Aktionen ausgeführt zu haben. Notizen werden nur durch den separaten Speichern-Knopf gespeichert.'}]+messages,'options':{'num_ctx':2048,'num_predict':400}}
    req=urllib.request.Request('http://127.0.0.1:11434/api/chat',data=json.dumps(payload).encode(),headers={'Content-Type':'application/json'})
    with urllib.request.urlopen(req,timeout=180) as r: result=json.load(r)
    return self.reply({'text':result.get('message',{}).get('content',''),'truncated':result.get('done_reason')=='length'})
   self.reply({'error':'Nicht gefunden'},404)
  except (ValueError,TypeError): self.reply({'error':'Ungültige Eingabe'},400)
  except Exception: self.reply({'error':'Ollama nicht erreichbar oder Anfrage fehlgeschlagen. Prüfe den Ollama-Dienst.'},502)
if __name__=='__main__':
 print('Jarvis öffnen: http://127.0.0.1:8765 — Beenden: Strg+C',flush=True)
 ThreadingHTTPServer(('127.0.0.1',8765),Handler).serve_forever()
