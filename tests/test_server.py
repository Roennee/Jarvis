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

    def test_agenda_widget_projection_preserves_raw_import_and_metadata(self):
        from datetime import datetime,timedelta
        day=datetime.now(self.app.ZURICH).date()+timedelta(days=10)
        event={'id':'agenda-projection-test','title':'Projekttermin','start':str(day)+'T00:00:00+01:00','end':str(day+timedelta(days=1))+'T00:00:00+01:00','all_day':True,'calendar':'Firmenkalender','details':'<html><body><p>Raum 2 &amp; Planung</p></body></html>'}
        self.data('/api/calendar/device',{'events':[event]})
        raw=next(e for e in self.data('/api/events') if e['title']=='Projekttermin')
        self.assertIn('<html>',raw['details'])
        projected=next(e for e in self.data('/api/agenda') if e['id']==raw['id'])
        self.assertEqual(projected['details'],'Raum 2 & Planung');self.assertTrue(projected['all_day']);self.assertEqual(projected['calendar'],'Firmenkalender')
        snapshot=self.data('/api/widget');self.assertLessEqual(len(snapshot['events']),3);self.assertLessEqual(len(snapshot['tasks']),3)
        self.assertTrue(all('details' not in e for e in snapshot['events']))
        self.assertEqual(self.req('/agenda.js')[0],200)
        invalid=dict(event,all_day='yes')
        self.assertEqual(self.req('/api/calendar/device',{'events':[invalid]})[0],400)
        self.assertEqual(raw,next(e for e in self.data('/api/events') if e['id']==raw['id']))

    def test_calendar_question_uses_connected_agenda(self):
        from datetime import datetime,timedelta
        date=datetime.now(self.app.ZURICH).date()+timedelta(days=1)
        self.data('/api/events',{'title':'Agenda Prüfung','start':str(date)+'T14:00','end':str(date)+'T15:00'})
        answer=self.app.agenda_answer('Welche Termine habe ich morgen?')
        self.assertIn('Agenda Prüfung',answer);self.assertIn('14:00',answer)
        self.assertIsNone(self.app.agenda_answer('Erstelle morgen um 14 Uhr einen Termin'))

    @unittest.skipUnless(__import__('shutil').which('ffmpeg'),'ffmpeg missing')
    def test_saved_web_audio_can_be_prepared_without_replacing_original(self):
        import io,wave
        buffer=io.BytesIO()
        with wave.open(buffer,'wb') as out:
            out.setparams((1,2,16000,0,'NONE',''));out.writeframes(b'\x01\x00'*16000)
        original=buffer.getvalue()
        rid=self.data('/api/recordings',{'mime':'audio/wav','audio':base64.b64encode(original).decode()})['id']
        prepared=self.data('/api/recordings/prepare',{'id':rid})
        self.assertEqual(prepared,self.data('/api/recordings/prepare',{'id':rid}))
        status,data,headers=self.req(prepared['url'],headers={'Range':'bytes=0-11'})
        self.assertEqual(status,206);self.assertEqual(data[:4],b'RIFF')
        self.assertEqual(self.req('/api/audio/'+rid)[1],original)

    def test_audio_seek_http_and_head(self):
        payload=bytes(range(100))
        rid=self.data('/api/recordings',{'mime':'audio/wav','audio':base64.b64encode(payload).decode()})['id']
        status,data,headers=self.req('/api/audio/'+rid,headers={'Range':'bytes=20-39'})
        self.assertEqual(status,206);self.assertEqual(data,payload[20:40]);self.assertEqual(headers['Content-Range'],'bytes 20-39/100')
        status,data,headers=self.req('/api/audio/'+rid,headers={'Range':'bytes=1000-'})
        self.assertEqual(status,416);self.assertEqual(data,b'');self.assertEqual(headers['Content-Range'],'bytes */100')
        request=urllib.request.Request(self.url+'/api/audio/'+rid,method='HEAD')
        with urllib.request.urlopen(request,timeout=5) as response:
            self.assertEqual(response.read(),b'');self.assertEqual(response.headers['Accept-Ranges'],'bytes')

    def test_device_calendar_snapshot_does_not_remove_local_and_validates_before_replace(self):
        self.data('/api/events',{'title':'Unberührt','start':'2026-12-01T14:00','end':'2026-12-01T15:00'})
        event={'id':'device-1','title':'Pixel Test','start':'2026-12-02T14:00+01:00','end':'2026-12-02T15:00+01:00','details':''}
        for _ in range(2):self.data('/api/calendar/device',{'events':[event]})
        events=self.data('/api/events');self.assertEqual(sum(e['title']=='Pixel Test' for e in events),1)
        self.assertTrue(any(e['title']=='Unberührt' for e in events))
        self.assertEqual(self.req('/api/calendar/device',{'events':[{**event,'end':event['start']}]})[0],400)
        self.assertTrue(any(e['title']=='Pixel Test' for e in self.data('/api/events')))
        self.data('/api/calendar/device',{'events':[]})
        self.assertFalse(any(e['source']=='Pixel Kalender' for e in self.data('/api/events')))

    def test_cloud_secret_not_exported_and_voice_requires_separate_opt_in(self):
        from unittest.mock import patch
        secret='test-secret-value-123456'
        self.data('/api/cloud/settings',{'ai_mode':'cloud','cloud_model':'openrouter/free','key':secret,'cloud_voice':False,'weekly_models':False,'denoise':True})
        try:
            self.assertNotIn(secret,json.dumps(self.data('/api/status')))
            self.assertNotIn(secret,json.dumps(self.data('/api/export')))
            with patch.object(self.app.model_hub,'cloud_generate',return_value={'message':{'content':'Cloud'}}) as cloud:
                local=self.app.generate('/api/chat',{'messages':[]},voice=True)
                self.assertNotEqual(local['message']['content'],'Cloud');cloud.assert_not_called()
                self.assertEqual(self.app.generate('/api/chat',{'messages':[]})['message']['content'],'Cloud')
        finally:self.data('/api/cloud/settings',{'ai_mode':'local','cloud_model':'openrouter/free','key':'','remove_key':True,'cloud_voice':False,'weekly_models':True,'denoise':True})
        self.assertFalse(self.app.cloud_key())

    def test_protocol_review_archive_and_reanalysis(self):
        from unittest.mock import patch
        task={'body':'Mischa anrufen','owner':'Raffa','due':'morgen','evidence':'Mischa anrufen'}
        analysis={'title':'Abstimmung','category':'Arbeit','summary':'Anruf vereinbart','key_points':[], 'decisions':[], 'open_questions':[], 'tasks':[task]}
        with patch.object(self.app,'ollama',return_value={'message':{'content':json.dumps(analysis)}}):
            nid=self.data('/api/protocols',{'text':'Mischa anrufen'})['id']
        self.data('/api/notes/update',{'id':nid,'title':'Projekt','category':'Privat','pinned':True})
        note=next(n for n in self.data('/api/notes?q=Projekt') if n['id']==nid)
        self.assertEqual(note['category'],'Privat');self.assertTrue(note['pinned'])
        payload={'note_id':nid,'position':0,'body':'Mischa wegen Sorter anrufen','owner':'Raffa','due':'2026-11-20'}
        self.data('/api/protocols/task',payload)
        self.data('/api/protocols/task',payload)
        with self.app.connect() as c:
            self.assertEqual(c.execute('SELECT count(*) FROM note_tasks WHERE note_id=?',(nid,)).fetchone()[0],1)
            tid=c.execute('SELECT task_id FROM note_tasks WHERE note_id=?',(nid,)).fetchone()[0]
            self.assertEqual(c.execute('SELECT due FROM tasks WHERE id=?',(tid,)).fetchone()[0],'2026-11-20')
            changed={**analysis,'tasks':[{'body':'Andere Aufgabe','owner':'','due':'','evidence':'Andere Aufgabe'},task]}
            c.execute('UPDATE notes SET analysis=? WHERE id=?',(json.dumps(changed),nid))
        self.data('/api/protocols/task',{**payload,'position':1})
        with self.app.connect() as c:
            self.assertEqual(c.execute('SELECT count(*) FROM note_tasks WHERE note_id=?',(nid,)).fetchone()[0],1)
        self.data('/api/notes/update',{'id':nid,'archived':True})
        self.assertFalse(any(n['id']==nid for n in self.data('/api/notes')))
        self.assertTrue(any(n['id']==nid for n in self.data('/api/notes?archived=1')))
        self.data('/api/notes/update',{'id':nid,'archived':False})
        self.assertEqual(self.req('/api/notes/update',{'id':nid,'title':'X','category':'Invalid'})[0],400)
        self.assertEqual(self.req('/api/protocols/task',{**payload,'due':'not-a-date','position':0})[0],400)

    def test_voice_action_execution_is_idempotent(self):
        from unittest.mock import patch
        payload={'mime':'audio/wav','audio':base64.b64encode(b'RIFFtest').decode(),'mode':'silent','client_key':'c'*64,'captured_at':'2026-11-14T20:00:00+00:00'}
        rid=self.data('/api/recordings',payload)['id']
        self.assertEqual(rid,self.data('/api/recordings',payload)['id'])
        with patch.object(self.app,'decide_voice',return_value={'action':'calendar','title':'Training','start':'2026-11-16T14:00:00+01:00','end':'2026-11-16T14:30:00+01:00','default_duration':True,'reply':'ignored'}):
            first=self.app.apply_voice_action(rid,'Trage morgen um 14 Uhr Training ein','silent')
            second=self.app.apply_voice_action(rid,'Trage morgen um 14 Uhr Training ein','silent')
        self.assertEqual(first['proposal_id'],second['proposal_id']);self.assertEqual(first['reply'],'')
        self.assertEqual(first['state'],'pending')
        first=self.data('/api/voice/review',{'id':first['proposal_id'],'accept':True})
        self.assertEqual(self.data('/api/voice/result?id='+rid)['result']['event_id'],first['event_id'])
        self.assertEqual(len([e for e in self.data('/api/events') if e['id']==first['event_id']]),1)
        self.assertEqual(self.req('/api/recordings',{'mime':'audio/wav','audio':'eA==','mode':'untrusted'})[0],400)

    def test_both_modes_require_review(self):
        from unittest.mock import patch
        task={'body':'Mischa anrufen','owner':'','due':'morgen','evidence':'Mischa anrufen'}
        decision={'action':'note','title':'Sorter','summary':'Mischa kontaktieren','category':'Arbeit','tasks':[task],'reply':''}
        def recording(mode,session):
            return self.data('/api/recordings',{'mime':'audio/wav','audio':'eA==','mode':mode,'session_id':session})['id']
        before=len(self.data('/api/tasks'))
        rid=recording('assistant','dialogue-test')
        with patch.object(self.app,'decide_voice',return_value=decision):
            result=self.app.apply_voice_action(rid,'Mischa anrufen','assistant')
        self.assertEqual(result['state'],'pending');self.assertEqual(len(self.data('/api/tasks')),before)
        proposals=self.data('/api/voice/proposals');self.assertTrue(any(p['id']==result['proposal_id'] for p in proposals))
        accepted=self.data('/api/voice/review',{'id':result['proposal_id'],'accept':True,'category':'Arbeit','title':'Sorter','summary':'Mischa kontaktieren'})
        self.assertEqual(accepted['state'],'accepted');self.assertEqual(len(self.data('/api/tasks')),before+1)
        self.data('/api/voice/review',{'id':result['proposal_id'],'accept':True})
        self.assertEqual(len(self.data('/api/tasks')),before+1)
        self.assertTrue(any(g['category']=='Arbeit' for g in self.data('/api/notes/overview')))
        # Another session cannot confirm this session's draft.
        rid2=recording('assistant','dialogue-test')
        with patch.object(self.app,'decide_voice',return_value={**decision,'title':'Weitere Notiz'}):
            second=self.app.apply_voice_action(rid2,'Mischa anrufen','assistant')
        rid3=recording('assistant','dialogue-test')
        confirmed=self.app.apply_voice_action(rid3,'Ja bitte','assistant')
        self.assertEqual(confirmed['reply'],'Gespeichert.')
        self.assertFalse(any(p['id']==second['proposal_id'] for p in self.data('/api/voice/proposals')))
        silent_rid=recording('silent','silent-test')
        with patch.object(self.app,'decide_voice',return_value={**decision,'tasks':[dict(task,body='Layout prüfen')]}):
            silent=self.app.apply_voice_action(silent_rid,'Mischa anrufen','silent')
        self.assertEqual(silent['state'],'pending');self.assertEqual(silent['reply'],'');self.assertNotIn('task_ids',silent)

    def test_rejection_and_session_isolation(self):
        from unittest.mock import patch
        payload={'action':'task','title':'Aufgabe','category':'Arbeit','tasks':[{'body':'Isolierte Aufgabe','owner':'','due':'','evidence':'Isolierte Aufgabe'}],'reply':''}
        def rec(session):return self.data('/api/recordings',{'mime':'audio/wav','audio':'eA==','mode':'assistant','session_id':session})['id']
        with patch.object(self.app,'decide_voice',return_value=payload):
            proposed=self.app.apply_voice_action(rec('session-a'),'Isolierte Aufgabe','assistant')
        count=len(self.data('/api/tasks'))
        with patch.object(self.app,'decide_voice',return_value={'action':'answer','tasks':[],'reply':'Was meinst du?'}):
            other=self.app.apply_voice_action(rec('session-b'),'Ja','assistant')
        self.assertEqual(other['reply'],'Was meinst du?')
        self.data('/api/voice/review',{'id':proposed['proposal_id'],'accept':False})
        self.assertEqual(len(self.data('/api/tasks')),count)
        again=self.data('/api/voice/review',{'id':proposed['proposal_id'],'accept':True})
        self.assertEqual(again['state'],'dismissed');self.assertEqual(len(self.data('/api/tasks')),count)

    def test_personal_speech_corrections_require_own_voice(self):
        rid=self.data('/api/recordings',{'mime':'audio/wav','audio':'eA==','mode':'training','own_voice':True})['id']
        with self.app.connect() as c:c.execute('INSERT INTO speech_samples(recording_id,original,duration) VALUES(?,?,?)',(rid,'Blumer anrufen',5))
        payload={'id':rid,'corrected':'Beumer anrufen','words':'Beumer, Mischa','own_voice':False}
        self.assertEqual(self.req('/api/speech/learn',payload)[0],400)
        self.data('/api/speech/learn',{**payload,'own_voice':True})
        sample=next(s for s in self.data('/api/speech/samples') if s['recording_id']==rid)
        self.assertEqual(sample['corrected'],'Beumer anrufen');self.assertEqual(sample['own_voice'],1)
        self.assertEqual(self.req('/api/recordings',{'mime':'audio/wav','audio':'eA==','mode':'training'})[0],400)
        self.assertEqual(self.req('/api/speech/profile',{'profile':'../../outside'})[0],400)
        self.data('/api/speech/delete',{'id':rid})
        self.assertFalse(any(s['recording_id']==rid for s in self.data('/api/speech/samples')))

    def test_01_migration(self):
        self.assertTrue(any('Bestehende' in n['body'] for n in self.data('/api/notes')))
        self.assertTrue(list((Path(self.tmp.name)/'backups').glob('*.sqlite3')))
        with self.app.connect() as c:self.assertEqual(c.execute('PRAGMA user_version').fetchone()[0],12)

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
        exported=self.data('/api/export');self.assertIn('messages',exported['data']);self.assertEqual(exported['version'],self.app.VERSION)

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


class ProtocolTests(unittest.TestCase):
    def test_analysis_evidence_and_idempotent_storage(self):
        from unittest.mock import patch
        with tempfile.TemporaryDirectory() as tmp:
            with patch.dict(os.environ, {'JARVIS_DATA_DIR':tmp}):
                spec=importlib.util.spec_from_file_location('protocol_test',Path(__file__).resolve().parents[1]/'server.py')
                app=importlib.util.module_from_spec(spec);spec.loader.exec_module(app);app.init()
                transcript='Mischa anrufen. Termin offen.'
                output={'title':'Sorter','summary':'Anruf geplant','category':'Arbeit','tasks':[
                    {'body':'Anrufen','owner':'','due':'','evidence':'Mischa anrufen'},
                    {'body':'Erfundene Aufgabe','evidence':'nicht im Text'}]}
                with patch.object(app,'ollama',return_value={'message':{'content':json.dumps(output)}}):
                    analysis=app.summarize_transcript(transcript)
                self.assertEqual(len(analysis['tasks']),1)
                a=app.save_protocol(transcript,analysis,'a'*32)
                b=app.save_protocol(transcript,analysis,'a'*32)
                self.assertEqual(a,b)
                self.assertEqual(len(app.rows('SELECT * FROM notes')),1)
                self.assertEqual(app.rows('SELECT * FROM tasks'),[])
                with patch.object(app,'ollama',return_value={'message':{'content':'{}'}}):
                    with self.assertRaises(ValueError):app.summarize_transcript(transcript)


class VoiceWorkerTests(unittest.TestCase):
    def test_normal_voice_is_discarded_after_processing(self):
        from unittest.mock import patch
        from types import SimpleNamespace
        with tempfile.TemporaryDirectory() as tmp,patch.dict(os.environ,{'JARVIS_DATA_DIR':tmp}):
            spec=importlib.util.spec_from_file_location('voice_worker_test',Path(__file__).resolve().parents[1]/'server.py')
            app=importlib.util.module_from_spec(spec);spec.loader.exec_module(app);app.init()
            rid='d'*32
            directory=Path(tmp)/'recordings';directory.mkdir();(directory/rid).write_bytes(b'fakeaudio')
            with app.connect() as c:c.execute("INSERT INTO recordings(id,status,mime,mode,session_id) VALUES(?, 'queued','audio/wav','silent','worker')",(rid,))
            obj={'action':'task','title':'Anruf','summary':'Anruf nötig','category':'Arbeit','evidence':'Mischa anrufen','tasks':[{'body':'Mischa anrufen','owner':'','due':'','evidence':'Mischa anrufen'}]}
            def fake(*a,**kw):return {'message':{'content':json.dumps(obj)}}
            with patch.object(app,'ollama',side_effect=fake),patch.object(app.subprocess,'run',return_value=SimpleNamespace(returncode=0,stdout=json.dumps({'text':'Mischa anrufen'}))):
                threading.Thread(target=app.recording_worker,daemon=True).start();app.JOBS.put(rid)
                import time
                deadline=time.monotonic()+5
                while app.JOBS.unfinished_tasks and time.monotonic()<deadline:time.sleep(.01)
                self.assertEqual(app.JOBS.unfinished_tasks,0)
            row=app.rows('SELECT * FROM recordings WHERE id=?',(rid,))[0]
            self.assertEqual(row['status'],'done');self.assertIn('reasoning_s',json.loads(row['timings']));self.assertEqual(row['transcript'],'');self.assertFalse((directory/rid).exists())
            self.assertEqual(len(app.rows('SELECT * FROM tasks')),0)
            self.assertEqual(len(app.rows("SELECT * FROM voice_proposals WHERE state='pending'")),1)
