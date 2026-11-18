"""One explicit event export, no invitations or attendee data."""
from datetime import datetime, timezone

def event_ics(event):
    def escape(value):return str(value).replace('\\','\\\\').replace('\r','').replace('\n','\\n').replace(';','\\;').replace(',','\\,')
    def stamp(value):return datetime.fromisoformat(value.replace('Z','+00:00')).astimezone(timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    lines=['BEGIN:VCALENDAR','VERSION:2.0','PRODID:-//Jarvis//Assistant//DE','BEGIN:VEVENT',
        'UID:'+escape(event['id'])+'@jarvis.local','DTSTAMP:'+datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ'),
        'DTSTART:'+stamp(event['start']),'DTEND:'+stamp(event['end']),'SUMMARY:'+escape(event['title']),
        'DESCRIPTION:'+escape(event.get('details','')),'END:VEVENT','END:VCALENDAR']
    folded=[]
    for line in lines:
        segment=''
        for char in line:
            if len((segment+char).encode())>74:folded.append(segment);segment=' '
            segment+=char
        folded.append(segment)
    return ('\r\n'.join(folded)+'\r\n').encode()
