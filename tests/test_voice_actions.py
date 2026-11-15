import unittest
import json
from datetime import datetime
from zoneinfo import ZoneInfo
from voice_actions import calendar_slot,decide

REF=datetime(2026,11,15,9,0,tzinfo=ZoneInfo('Europe/Zurich'))
class VoiceActionsTests(unittest.TestCase):
    def model(self,**obj):
        return lambda *a,**k:{'message':{'content':json.dumps(obj)}}
    def test_explicit_calendar(self):
        slot=calendar_slot('Trage morgen um 14:30 Training für 60 Minuten ein',REF)
        self.assertEqual(slot[:2],('2026-11-16T14:30:00+01:00','2026-11-16T15:30:00+01:00'))
        self.assertFalse(slot[2])
    def test_ambiguous_calendar_is_note(self):
        text='Trage morgen Training ein'
        result=decide(text,'assistant',self.model(action='calendar',evidence=text,title='Training'),'model',REF)
        self.assertEqual(result['action'],'note');self.assertTrue(result['needs_review'])
    def test_silent_never_answers(self):
        result=decide('Wie geht es dir?','silent',self.model(action='answer',reply='Gut'),'model',REF)
        self.assertEqual(result['action'],'none');self.assertEqual(result['reply'],'')
    def test_requires_verbatim_evidence(self):
        with self.assertRaises(ValueError):decide('Hallo','silent',self.model(action='calendar',evidence='Morgen 14 Uhr'),'model',REF)
    def test_reject_past_unknown_and_dst(self):
        self.assertIsNone(calendar_slot('um 14 Uhr',REF))
        self.assertIsNone(calendar_slot('heute um 8 Uhr',REF))
        self.assertIsNone(calendar_slot('2027-03-28 um 2:30 Uhr',REF))
        self.assertIsNone(calendar_slot('2027-10-31 um 2:30 Uhr',REF))
    def test_default_duration(self):
        slot=calendar_slot('morgen um 14 Uhr',REF);self.assertTrue(slot[2]);self.assertIn('14:30',slot[1])

    def test_tasks_and_explicit_transcript(self):
        source='Ich muss Mischa anrufen.'
        task={'body':'Mischa anrufen','evidence':'Mischa anrufen'}
        d=decide(source,'silent',self.model(action='task',evidence=source,category='Arbeit',tasks=[task]),'model',REF)
        self.assertEqual(d['tasks'][0]['body'],'Mischa anrufen');self.assertEqual(d['category'],'Arbeit')
        d=decide('Erstelle ein Transkript davon','assistant',None,'model',REF)
        self.assertTrue(d['keep_transcript'])
