"""Presentation-only agenda projections. Raw calendar imports remain unchanged."""
from datetime import datetime, timezone
from html.parser import HTMLParser
import re
from zoneinfo import ZoneInfo

ZONE = ZoneInfo('Europe/Zurich')


class PlainText(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.parts = []; self.hidden = 0

    def handle_starttag(self, tag, attrs):
        if tag in ('script', 'style', 'head'): self.hidden += 1
        if not self.hidden and tag in ('br', 'p', 'div', 'li', 'tr'): self.parts.append('\n')

    def handle_endtag(self, tag):
        if tag in ('script', 'style', 'head'): self.hidden = max(0, self.hidden - 1)
        if not self.hidden and tag in ('p', 'div', 'li', 'tr'): self.parts.append('\n')

    def handle_data(self, data):
        if not self.hidden: self.parts.append(data)


def plain(value):
    text = str(value or '')
    if re.search(r'</?(?:html|body|div|p|br|span|table|script|style|head|a|b|strong)\b', text, re.I):
        parser = PlainText(); parser.feed(text); text = ''.join(parser.parts)
    return '\n'.join(line for line in (re.sub(r'[\t \xa0]+', ' ', s).strip() for s in text.splitlines()) if line)[:10000]


def instant(value):
    stamp = datetime.fromisoformat(value.replace('Z', '+00:00'))
    return stamp.replace(tzinfo=ZONE) if stamp.tzinfo is None else stamp


def events(raw, metadata=None, now=None):
    now = now or datetime.now(timezone.utc)
    metadata = metadata or {}; result = []
    for row in raw:
        try: start, end = instant(row['start']), instant(row['end'])
        except (ValueError, TypeError, KeyError): continue
        if end <= now or end <= start: continue
        a, z = start.astimezone(ZONE), end.astimezone(ZONE)
        meta = metadata.get(row['id'], {})
        # Older imports have no all-day flag; midnight-to-midnight is the legacy fallback.
        all_day = bool(meta.get('all_day', a.date() < z.date() and a.time().isoformat() == z.time().isoformat() == '00:00:00'))
        result.append(dict(row, title=plain(row['title']), details=plain(row.get('details')),
                           all_day=all_day, calendar=meta.get('calendar_name') or row['source']))
    return sorted(result, key=lambda r: (instant(r['start']), r['id']))


def widget(raw_events, raw_tasks, metadata=None, now=None):
    now = now or datetime.now(timezone.utc)
    upcoming = events(raw_events, metadata, now)
    tasks = sorted((t for t in raw_tasks if not t['done']),
                   key=lambda t: (not bool(t['due']), t['due'], -t['id']))[:3]
    return {'generated_at': now.isoformat(), 'timezone': 'Europe/Zurich',
            'events': [{k: e[k] for k in ('id', 'title', 'start', 'end', 'all_day', 'calendar')} for e in upcoming[:3]],
            'tasks': [{k: t[k] for k in ('id', 'body', 'due')} for t in tasks]}
