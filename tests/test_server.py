import base64
import importlib.util
import json
import os
from pathlib import Path
import sqlite3
import tempfile
import threading
import unittest
import urllib.request
import urllib.error
from http.server import ThreadingHTTPServer


class WorkspaceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp=tempfile.TemporaryDirectory()
        cls.old=os.environ.get('JARVIS_DATA_DIR')
        os.environ['JARVIS_DATA_DIR']=cls.tmp.name
        # Simulate a v0.1 database, with existing personal notes.
        with sqlite3.connect(Path(cls.tmp.name)/'jarvis.sqlite3') as c:
            c.execute('CREATE TABLE notes(id INTEGER PRIMARY KEY,body TEXT NOT NULL,created TEXT DEFAULT CURRENT_TIMESTAMP)')
            c.execute('INSERT INTO notes(body) VALUES(?)',('Bestehende Notiz bleibt erhalten',))
        spec=importlib.util.spec_from_file_location('jarvis_test',Path(__file__).resolve().parents[1]/'server.py')
        cls.app=importlib.util.module_from_spec(spec);spec.loader.exec_module(cls.app);cls.app.init()
        cls.requests=[]
        def fake(path,payload=None,timeout=8):
            if path=='/api/tags':return {'models':[{'name':'qwen3:1.7b'}]}
            cls.requests.append(payload)
            return {'message':{'content':'Milch kaufen und Mischa um 14 Uhr anrufen.'},'done_reason':'stop'}
        cls.app.ollama=fake
        cls.http=ThreadingHTTPServer(('127.0.0.1',0),cls.app.Handler)
        cls.port=cls.http.server_address[1];cls.url=f'http://127.0.0.1:{cls.port}'
        cls.app.ORIGINS={cls.url};cls.app.HOSTS={f'127.0.0.1:{cls.port}'}
        cls.thread=threading.Thread(target=cls.http.serve_forever,daemon=True);cls.thread.start()

    @classmethod
    def tearDownClass(cls):
        cls.http.shutdown();cls.http.server_close();cls.tmp.cleanup()
        if cls.old is None:os.environ.pop('JARVIS_DATA_DIR',None)
        else:os.environ['JARVIS_DATA_DIR']=cls.old

    def req(self,path,body=None,headers=None):
        h={'Origin':self.url,'X-Jarvis-Token':self.app.CSRF,'Content-Type':'application/json'}
        h.update(headers or {})
        data=None if body is None else json.dumps(body).encode()
        request=urllib.request.Request(self.url+path,data=data,headers=h)
        try:
            with urllib.request.urlopen(request,timeout=5) as r:return r.status,r.read(),r.headers
        except urllib.error.HTTPError as e:return e.code,e.read(),e.headers

    def data(self,path,body=None):
        status,data,_=self.req(path,body);self.assertEqual(status,200,data);return json.loads(data)

    def test_01_migration(self):
        self.assertTrue(any('Bestehende' in n['body'] for n in self.data('/api/notes')))
        self.assertTrue(list((Path(self.tmp.name)/'backups').glob('*.sqlite3')))
        with self.app.connect() as c:self.assertEqual(c.execute('PRAGMA user_version').fetchone()[0],4)

    def test_assets(self):
        for path in ['/','/app.js','/style.css','/icon.svg','/manifest.webmanifest']:
            status,data,headers=self.req(path);self.assertEqual(status,200);self.assertTrue(data);self.assertIn("frame-ancestors 'none'",headers['Content-Security-Policy'])

    def test_note_unicode_search(self):
        self.data('/api/notes',{'body':'Prüfnotiz Zürich <script>alert(1)</script>'})
        self.assertTrue(any('Zürich' in n['body'] for n in self.data('/api/notes?q=Pr%C3%BCfnotiz')))

    def test_csrf_and_origin(self):
        for headers in [{'Origin':'https://evil.example'},{'X-Jarvis-Token':'wrong'},{'Host':'evil.example'}]:
            status,_,_=self.req('/api/notes',{'body':'must not save'},headers);self.assertEqual(status,403)
        self.assertFalse(any(n['body']=='must not save' for n in self.data('/api/notes')))

    def test_tasks(self):
        self.data('/api/tasks',{'body':'Task test','due':'2026-11-14'})
        t=next(t for t in self.data('/api/tasks') if t['body']=='Task test')
        self.data('/api/tasks/toggle',{'id':t['id'],'done':True})
        self.assertEqual(next(x for x in self.data('/api/tasks') if x['id']==t['id'])['done'],1)
        self.assertEqual(self.req('/api/tasks',{'body':'invalid','due':'not a date'})[0],400)

    def test_persistent_chat(self):
        chat=self.data('/api/chats',{'title':'Test conversation'})
        response=self.data('/api/chat',{'chat_id':chat['id'],'text':'Milch und Mischa'})
        self.assertIn('Milch',response['text'])
        messages=self.data('/api/messages?chat='+chat['id'])
        self.assertEqual([m['role'] for m in messages],['user','assistant'])
        self.assertEqual(messages[0]['content'],'Milch und Mischa')

    def test_chat_busy_does_not_save(self):
        chat=self.data('/api/chats',{'title':'Busy test'})
        self.app.MODEL_LOCK.acquire()
        try:self.assertEqual(self.req('/api/chat',{'chat_id':chat['id'],'text':'not saved'})[0],409)
        finally:self.app.MODEL_LOCK.release()
        self.assertEqual(self.data('/api/messages?chat='+chat['id']),[])

    def test_calendar_time(self):
        self.data('/api/events',{'title':'Zurich appointment','start':'2026-11-14T14:00','end':'2026-11-14T15:00'})
        event=next(e for e in self.data('/api/events') if e['title']=='Zurich appointment')
        self.assertTrue(event['start'].startswith('2026-11-14T13:00'))
        self.assertEqual(self.req('/api/events',{'title':'Bad','start':'2026-11-14T15:00','end':'2026-11-14T14:00'})[0],400)

    def test_training_import_atomic(self):
        before=len(self.data('/api/training'))
        self.assertEqual(self.req('/api/training/import',{'content':'day,title,plan,result\n2026-11-14,Good,plan,\ninvalid,Bad,plan,\n'})[0],400)
        self.assertEqual(len(self.data('/api/training')),before)
        self.assertEqual(self.data('/api/training/import',{'content':'day,title,plan,result\n2026-11-14,Squats,5x5,done\n'})['count'],1)

    def test_holdings_history_and_validation(self):
        for _ in range(2):self.data('/api/holdings/import',{'content':'symbol,quantity,price,currency\nDEMO,2,10,CHF\n'})
        self.assertGreaterEqual(len(self.data('/api/holdings')),2)
        for bad in ['nan','inf','-1']:
            self.assertEqual(self.req('/api/holdings/import',{'content':f'symbol,quantity,price,currency\nDEMO,2,{bad},CHF\n'})[0],400)

    def test_recordings_and_correction(self):
        raw=b'not-real-audio-test'
        rec=self.data('/api/recordings',{'mime':'audio/webm;codecs=opus','audio':base64.b64encode(raw).decode()})
        status,data,_=self.req('/api/audio/'+rec['id']);self.assertEqual(status,200);self.assertEqual(data,raw)
        self.data('/api/corrections',{'id':rec['id'],'text':'Korrigierter Text'})
        self.assertTrue(any(n['body']=='Korrigierter Text' for n in self.data('/api/notes')))
        self.assertEqual(self.req('/api/audio/../../server.py')[0],404)

    def test_settings(self):
        self.data('/api/settings',{'model':'qwen3:1.7b','glossary':'Mischa, Frauenfeld','context':True})
        self.assertTrue(self.data('/api/status')['settings']['context'])
        self.assertEqual(self.req('/api/settings',{'model':'not installed','context':False,'glossary':''})[0],400)

    def test_bees_roundtrip(self):
        original={'adaptive':{'equity':'unknown schema'},'paper':True}
        self.data('/api/bees/import',{'content':json.dumps(original)})
        self.assertEqual(json.loads(self.data('/api/bees')[0]['body']),original)

    def test_backup_and_export(self):
        backup=self.data('/api/backup',{})
        with sqlite3.connect(Path(self.tmp.name)/'backups'/backup['filename']) as c:self.assertGreater(c.execute('SELECT COUNT(*) FROM notes').fetchone()[0],0)
        exported=self.data('/api/export');self.assertIn('messages',exported['data']);self.assertEqual(exported['version'],'0.4.0')

    def test_local_bees_allowlist(self):
        previous=self.app.BEES_ROOT
        try:
            self.app.BEES_ROOT=Path(self.tmp.name)/'trading'
            target=self.app.BEES_ROOT/'data'/'live_bees'/'state.json';target.parent.mkdir(parents=True)
            target.write_text(json.dumps({'capital_eur':'1000','pending':None,'api_key':'TOPSECRET','bees':{'trend':{'last_equity':'330.20','fees_eur':'1.23','secret':'TOPSECRET'}}}))
            d=self.data('/api/bees/local')
            self.assertTrue(d['available']);self.assertEqual(d['agents'][0]['saved_equity_eur'],'330.20')
            self.assertNotIn('TOPSECRET',json.dumps(d))
        finally:self.app.BEES_ROOT=previous

    def test_optional_ics(self):
        if not self.data('/api/status')['calendar_import']:self.skipTest('ICS dependencies not installed')
        ics='BEGIN:VCALENDAR\r\nVERSION:2.0\r\nBEGIN:VEVENT\r\nUID:test-event\r\nDTSTART;TZID=Europe/Zurich:20261114T140000\r\nDTEND;TZID=Europe/Zurich:20261114T150000\r\nRRULE:FREQ=DAILY;COUNT=3\r\nSUMMARY:Recurring test\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n'
        a=self.data('/api/calendar/import',{'source':'Google-Import','content':ics});self.assertEqual(a['count'],3)
        before=len(self.data('/api/events'));self.data('/api/calendar/import',{'source':'Google-Import','content':ics});self.assertEqual(len(self.data('/api/events')),before)

if __name__=='__main__':unittest.main()
