import importlib.util
import json
from pathlib import Path
import sys
import types
import unittest
from unittest.mock import patch
spec=importlib.util.spec_from_file_location('calendar_adapter_test',Path(__file__).resolve().parents[1]/'calendar_sync.py')
adapter=importlib.util.module_from_spec(spec);spec.loader.exec_module(adapter)

class CalendarAdapterTests(unittest.TestCase):
    def test_timezone(self):
        self.assertEqual(adapter.timestamp('2026-11-14T14:00'),'2026-11-14T13:00:00+00:00')
        self.assertEqual(adapter.timestamp('2026-11-14T14:00','UTC'),'2026-11-14T14:00:00+00:00')

    def test_google_read_only_pages(self):
        calls=[]
        class API:
            def events(self):return self
            def list(self,**kw):calls.append(kw);return self
            def execute(self):
                if len(calls)==1:return {'items':[{'id':'one','summary':'Test','start':{'dateTime':'2026-11-14T14:00:00+01:00'},'end':{'dateTime':'2026-11-14T15:00:00+01:00'}}],'nextPageToken':'page2'}
                return {'items':[{'id':'cancelled','status':'cancelled'}]}
        discovery=types.ModuleType('googleapiclient.discovery');discovery.build=lambda *a,**kw:API()
        package=types.ModuleType('googleapiclient');package.discovery=discovery
        with patch.dict(sys.modules,{'googleapiclient':package,'googleapiclient.discovery':discovery}),patch.object(adapter,'google_credentials',return_value=object()):
            source,events=adapter.fetch_events('google')
        self.assertEqual(source,'Google live');self.assertEqual(len(events),1)
        self.assertEqual(calls[1]['pageToken'],'page2');self.assertTrue(events[0][2].startswith('2026-11-14T13:00'))

    def test_microsoft_read_only_and_safe_pagination(self):
        class Response:
            def __enter__(self):return self
            def __exit__(self,*a):pass
            def read(self):return json.dumps({'value':[{'id':'event','subject':'Business','start':{'dateTime':'2026-11-14T13:00:00'},'end':{'dateTime':'2026-11-14T14:00:00'}}]}).encode()
        with patch.object(adapter,'microsoft_token',return_value='test-token'),patch.object(adapter.urllib.request,'urlopen',return_value=Response()) as request:
            source,events=adapter.fetch_events('microsoft')
            self.assertEqual(request.call_count,1)
        self.assertEqual(source,'Outlook live');self.assertEqual(len(events),1);self.assertTrue(events[0][2].endswith('+00:00'))
        class BadResponse(Response):
            def read(self):return json.dumps({'value':[],'@odata.nextLink':'https://evil.example/steal-token'}).encode()
        with patch.object(adapter,'microsoft_token',return_value='test-token'),patch.object(adapter.urllib.request,'urlopen',return_value=BadResponse()) as request:
            with self.assertRaises(ValueError):adapter.fetch_events('microsoft')
            self.assertEqual(request.call_count,1)
