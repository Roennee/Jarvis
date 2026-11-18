"""Real desktop/Pixel layout checks on synthetic data only. Requires Playwright."""
import importlib.util
import os
from pathlib import Path
import sys
import tempfile
import threading
from datetime import datetime, timedelta
from http.server import ThreadingHTTPServer
from playwright.sync_api import sync_playwright, expect

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
with tempfile.TemporaryDirectory() as data:
    os.environ['JARVIS_DATA_DIR']=data
    spec=importlib.util.spec_from_file_location('jarvis_ui_test',ROOT/'server.py')
    app=importlib.util.module_from_spec(spec);spec.loader.exec_module(app);app.init()
    app.ollama=lambda *a,**k:{'models':[{'name':'qwen3:1.7b'}]}
    tomorrow=datetime.now(app.ZURICH).date()+timedelta(days=1)
    with app.connect() as c:
        for i,title in enumerate(['Projektbesprechung','Training · Kraft & Technik','Abendessen']):
            start=str(tomorrow)+'T'+['14:00','18:00','20:00'][i]+'+01:00';end=str(tomorrow)+'T'+['15:00','19:30','21:00'][i]+'+01:00'
            c.execute('INSERT INTO events VALUES(?,?,?,?,?,?)',(str(i),title,start,end,'Arbeit' if i==0 else 'Privat','<html><body><p>Besprechung im Raum 2</p></body></html>'))
    http=ThreadingHTTPServer(('127.0.0.1',0),app.Handler);url='http://127.0.0.1:'+str(http.server_address[1]);app.HOSTS={url.split('//')[1]};app.ORIGINS={url}
    thread=threading.Thread(target=http.serve_forever,daemon=True);thread.start()
    try:
        with sync_playwright() as p:
            browser=p.chromium.launch(headless=True)
            page=browser.new_page(viewport={'width':1440,'height':1000});errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
            page.goto(url+'/#calendar');page.wait_for_selector('.agenda-event')
            assert page.locator('.agenda-event').count()==3
            assert page.locator('#agendaCreate').get_attribute('open') is None
            Path('ui-checks').mkdir(exist_ok=True);page.screenshot(path='ui-checks/calendar-desktop.png',full_page=True)
            page.locator('.agenda-event summary').first.click();assert '<html' not in page.locator('.agenda-detail').first.inner_text()
            page.locator('#agendaSearch').fill('Training');assert page.locator('.agenda-event').count()==1
            page.locator('#agendaSearch').fill('');page.locator('#agendaSource').select_option('Arbeit');assert page.locator('.agenda-event').count()==1
            page.set_viewport_size({'width':412,'height':915});page.locator('#agendaSource').select_option('');page.screenshot(path='ui-checks/calendar-pixel.png',full_page=True)
            assert page.evaluate('document.documentElement.scrollWidth<=innerWidth')
            page.locator('#agendaNew').click();assert page.locator('#agendaCreate').get_attribute('open') is not None
            # A failed account fetch must preserve the imported agenda.
            page.route('**/api/calendar/sync',lambda route:route.fulfill(status=409,content_type='application/json',body='{"error":"Kontoabruf nicht verfügbar"}'))
            page.locator('.agenda-settings').nth(1).locator('summary').first.click();page.locator('#syncOutlook').click()
            expect(page.locator('#agendaStatus')).to_contain_text('bleiben sichtbar')
            assert page.locator('.agenda-event').count()==3
            assert not errors,errors
            browser.close();print('Desktop/Pixel: filters, safe details, failed sync retention, form, no overflow or JS errors passed.')
    finally:http.shutdown();http.server_close()
