#!/usr/bin/env python3
"""Read-only Google/Microsoft calendar adapters. No external writes."""
import argparse
import hashlib
import json
import os
from datetime import datetime, timedelta, timezone
from pathlib import Path
import urllib.parse
import urllib.request
from zoneinfo import ZoneInfo

ROOT=Path(__file__).resolve().parent
PRIVATE=Path(os.environ.get('JARVIS_DATA_DIR',str(ROOT)))/'private'
GOOGLE_SCOPES=['https://www.googleapis.com/auth/calendar.readonly']
MS_SCOPES=['Calendars.Read']


def save(path,content):
    PRIVATE.mkdir(parents=True,exist_ok=True,mode=0o700)
    temp=path.with_suffix(path.suffix+'.tmp')
    fd=os.open(temp,os.O_WRONLY|os.O_CREAT|os.O_TRUNC,0o600)
    with os.fdopen(fd,'w') as f:f.write(content)
    os.replace(temp,path)
    os.chmod(path,0o600)


def google_credentials(login=False):
    from google.oauth2.credentials import Credentials
    from google.auth.transport.requests import Request
    path=PRIVATE/'google-token.json'
    creds=Credentials.from_authorized_user_file(str(path),GOOGLE_SCOPES) if path.exists() else None
    if creds and creds.expired and creds.refresh_token:creds.refresh(Request())
    if not creds or not creds.valid:
        if not login:raise ValueError('Google-Anmeldung fehlt oder ist abgelaufen. calendar_sync.py google-login auf dem Laptop ausführen.')
        from google_auth_oauthlib.flow import InstalledAppFlow
        client=PRIVATE/'google-client.json'
        if not client.exists():raise ValueError('private/google-client.json fehlt (Google Desktop-OAuth-Client).')
        creds=InstalledAppFlow.from_client_secrets_file(str(client),GOOGLE_SCOPES).run_local_server(port=0)
    save(path,creds.to_json())
    return creds


def microsoft_token(login=False):
    import msal
    appfile=PRIVATE/'microsoft-app.json'
    if not appfile.exists():raise ValueError('private/microsoft-app.json fehlt (client_id und tenant).')
    cfg=json.loads(appfile.read_text())
    client_id=cfg.get('client_id','');tenant=cfg.get('tenant','organizations')
    if not client_id or not all(c.isalnum() or c in '-.' for c in tenant):raise ValueError('Microsoft-App-Konfiguration ungültig.')
    path=PRIVATE/'microsoft-cache.json';cache=msal.SerializableTokenCache()
    if path.exists():cache.deserialize(path.read_text())
    app=msal.PublicClientApplication(client_id,authority='https://login.microsoftonline.com/'+tenant,token_cache=cache)
    accounts=app.get_accounts()
    if len(accounts)>1:raise ValueError('Mehrere Konten im Cache. Für eindeutige Anmeldung separaten Cache verwenden.')
    result=app.acquire_token_silent(MS_SCOPES,account=accounts[0]) if accounts else None
    if not result or 'access_token' not in result:
        if not login:raise ValueError('Outlook-Anmeldung fehlt oder ist abgelaufen. calendar_sync.py microsoft-login ausführen.')
        flow=app.initiate_device_flow(scopes=MS_SCOPES)
        if 'user_code' not in flow:raise ValueError('Microsoft-Geräteanmeldung nicht verfügbar. Tenant-Richtlinie prüfen.')
        print(flow['message'],flush=True)
        result=app.acquire_token_by_device_flow(flow)
    if cache.has_state_changed:save(path,cache.serialize())
    if not result or 'access_token' not in result:raise ValueError('Outlook-Anmeldung fehlgeschlagen oder nicht freigegeben.')
    return result['access_token']


def timestamp(value,default_zone='Europe/Zurich'):
    dt=datetime.fromisoformat(value.replace('Z','+00:00'))
    if not dt.tzinfo:dt=dt.replace(tzinfo=ZoneInfo(default_zone))
    return dt.astimezone(timezone.utc).isoformat()


def fetch_events(provider):
    start=datetime.now(timezone.utc)-timedelta(days=30);end=start+timedelta(days=400)
    items=[]
    if provider=='google':
        from googleapiclient.discovery import build
        service=build('calendar','v3',credentials=google_credentials(),cache_discovery=False)
        token=None
        while True:
            response=service.events().list(calendarId='primary',timeMin=start.isoformat(),timeMax=end.isoformat(),singleEvents=True,maxResults=1000,pageToken=token).execute()
            for event in response.get('items',[]):
                if event.get('status')=='cancelled':continue
                a=event['start'];z=event['end']
                items.append((event['id'],event.get('summary','Termin'),timestamp(a.get('dateTime',a.get('date'))),timestamp(z.get('dateTime',z.get('date'))),event.get('description','')[:10000]))
            token=response.get('nextPageToken')
            if not token:break
            if len(items)>10000:raise ValueError('Zu viele Google-Termine; Abfrage abgebrochen.')
    elif provider=='microsoft':
        token=microsoft_token()
        query=urllib.parse.urlencode({'startDateTime':start.isoformat(),'endDateTime':end.isoformat(),'$top':'1000'})
        url='https://graph.microsoft.com/v1.0/me/calendarView?'+query
        while url:
            if urllib.parse.urlsplit(url).scheme!='https' or urllib.parse.urlsplit(url).netloc!='graph.microsoft.com':raise ValueError('Unerwartete Microsoft-Folgeseite')
            request=urllib.request.Request(url,headers={'Authorization':'Bearer '+token,'Prefer':'outlook.timezone="UTC"'})
            with urllib.request.urlopen(request,timeout=30) as response:result=json.load(response)
            for event in result.get('value',[]):
                if event.get('isCancelled'):continue
                items.append((event['id'],event.get('subject','Termin'),timestamp(event['start']['dateTime'],'UTC'),timestamp(event['end']['dateTime'],'UTC'),event.get('bodyPreview','')[:10000]))
            url=result.get('@odata.nextLink')
            if len(items)>10000:raise ValueError('Zu viele Outlook-Termine; Abfrage abgebrochen.')
    else:raise ValueError('Unbekannter Kalenderanbieter')
    source='Google live' if provider=='google' else 'Outlook live'
    entries=[(hashlib.sha256((source+identity).encode()).hexdigest(),title[:500],begin,finish,source,description) for identity,title,begin,finish,description in items]
    return source,entries


if __name__=='__main__':
    parser=argparse.ArgumentParser(description='Einmalige lokale Kalenderanmeldung, nur Lesezugriff.')
    parser.add_argument('command',choices=['google-login','microsoft-login'])
    args=parser.parse_args()
    try:
        if args.command=='google-login':google_credentials(login=True)
        else:microsoft_token(login=True)
        print('Anmeldung gespeichert. Kalender jetzt in Jarvis synchronisieren.')
    except Exception as e:
        print('Einrichtung nicht abgeschlossen:',str(e).split('\n')[0]);raise SystemExit(1)
