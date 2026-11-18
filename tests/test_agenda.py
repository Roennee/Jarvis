"""Agenda projections do not change raw imports, and widget payloads stay bounded."""
import copy
from datetime import datetime, timezone
import unittest
import agenda

NOW = datetime(2026,11,18,12,tzinfo=timezone.utc)

def event(id,start='2026-11-18T14:00:00+01:00',end='2026-11-18T15:00:00+01:00',**kw):
    return dict(id=id,title='Termin '+id,start=start,end=end,source='Pixel Kalender',details='',**kw)

class AgendaTests(unittest.TestCase):
    def test_outlook_html_is_plain_and_raw_data_stays_untouched(self):
        row=event('a');row['details']='<html><head><style>p{color:red}</style></head><body><p>Besprechung &amp; Planung</p><script>alert(1)</script><div>Raum 2<br>Bitte kommen</div></body></html>'
        original=copy.deepcopy(row);view=agenda.events([row],now=NOW)[0]
        self.assertEqual(view['details'],'Besprechung & Planung\nRaum 2\nBitte kommen')
        self.assertEqual(row,original)

    def test_current_multiday_exclusive_end_and_explicit_timed_midnight(self):
        rows=[event('ongoing','2026-11-16T00:00:00+01:00','2026-11-19T00:00:00+01:00'),event('ended','2026-11-18T11:00:00Z','2026-11-18T12:00:00Z'),event('timed','2026-11-19T00:00:00+01:00','2026-11-20T00:00:00+01:00')]
        result=agenda.events(rows,{'timed':{'all_day':0,'calendar_name':'Arbeit'}},NOW)
        self.assertEqual([e['id'] for e in result],['ongoing','timed']);self.assertTrue(result[0]['all_day']);self.assertFalse(result[1]['all_day']);self.assertEqual(result[1]['calendar'],'Arbeit')

    def test_widget_three_upcoming_three_open_due_first_no_details(self):
        rows=[event(str(i),f'2026-11-{i+18}T14:00:00+01:00',f'2026-11-{i+18}T15:00:00+01:00') for i in range(5)]
        tasks=[dict(id=i,body='Aufgabe '+str(i),done=done,due=due) for i,done,due in [(1,0,''),(2,0,'2026-11-19'),(3,0,'2026-11-15'),(4,1,'2026-11-01'),(5,0,'')]]
        data=agenda.widget(rows[::-1],tasks,now=NOW)
        self.assertEqual([e['id'] for e in data['events']],['0','1','2']);self.assertEqual([t['id'] for t in data['tasks']],[3,2,5]);self.assertNotIn('details',data['events'][0]);self.assertEqual(data['timezone'],'Europe/Zurich')
        self.assertEqual(agenda.widget([],[],now=NOW)['events'],[])
