"""Public catalog discovery and free-only inference. No personal data in catalog requests."""
import json
import os
import re
import tempfile
import urllib.request
import urllib.error
from datetime import datetime, timezone
from decimal import Decimal, InvalidOperation
from pathlib import Path

OPENROUTER='https://openrouter.ai/api/v1'
HF='https://huggingface.co/api/models?search=GGUF&sort=trendingScore&direction=-1&limit=80&full=true'

def request(url, payload=None, key=None, timeout=25):
    headers={'Content-Type':'application/json','User-Agent':'Jarvis/0.9'}
    if key: headers['Authorization']='Bearer '+key
    req=urllib.request.Request(url,data=None if payload is None else json.dumps(payload).encode(),headers=headers)
    try:
        with urllib.request.urlopen(req,timeout=timeout) as response:
            raw=response.read(8_000_001)
        if len(raw)>8_000_000: raise ValueError('Modellantwort zu gross')
        return json.loads(raw)
    except urllib.error.HTTPError as e:
        labels={401:'API-Schlüssel ungültig',402:'Kein kostenloser Zugang verfügbar',429:'Gratislimit erreicht. Später versuchen oder lokal verwenden'}
        raise ValueError(labels.get(e.code,'Cloudanbieter nicht verfügbar (HTTP '+str(e.code)+')')) from None
    except (OSError,ValueError) as e:
        if isinstance(e,ValueError): raise
        raise ValueError('Modellanbieter nicht erreichbar. Lokal verwenden oder später versuchen.') from None

def atomic_json(path,value):
    path=Path(path);path.parent.mkdir(parents=True,exist_ok=True,mode=0o700)
    fd,name=tempfile.mkstemp(dir=path.parent)
    try:
        with os.fdopen(fd,'w') as stream: json.dump(value,stream,ensure_ascii=False)
        os.replace(name,path)
    finally:
        if os.path.exists(name):os.unlink(name)

def free_models(catalog):
    result=[]
    for model in catalog.get('data',[]):
        if not isinstance(model,dict):continue
        pricing=model.get('pricing') or {};identity=model.get('id','')
        try: free=all(Decimal(str(pricing[k]))==0 for k in ('prompt','completion')) and all(Decimal(str(v))==0 for v in pricing.values())
        except (KeyError,InvalidOperation,TypeError):free=False
        outputs=model.get('architecture',{}).get('output_modalities',['text'])
        if free and identity.endswith(':free') and 'text' in outputs:
            result.append({'id':identity,'name':model.get('name',identity),'context':model.get('context_length',0),'created':model.get('created',0),'url':'https://openrouter.ai/'+identity})
    return sorted(result,key=lambda m:m['created'],reverse=True)

def cloud_catalog():return free_models(request(OPENROUTER+'/models'))

def scan(previous=None):
    report={'checked_at':datetime.now(timezone.utc).isoformat(),'cloud':[],'local':[],'errors':[],
        'note':'Kandidaten aus aktuellen Katalogen; keine Rangliste der Qualität. Vor Wechsel Deutsch, Aufgaben und Latenz testen. Surface: 8 GB RAM, CPU; 0.5–3B quantisiert bevorzugen.'}
    try:report['cloud']=cloud_catalog()
    except Exception:report['errors'].append('Cloudkatalog nicht erreichbar; letzter erfolgreicher Stand bleibt sichtbar.');report['cloud']=(previous or {}).get('cloud',[])
    try:
        for m in request(HF):
            identity=m.get('id','')
            if m.get('gated') or m.get('private'):continue
            match=re.search(r'(?i)(?:^|[-_])((?:0\.[5-9]|[123](?:\.\d+)?))b(?:[-_]|$)',identity)
            if not match or float(match[1])>3:continue
            report['local'].append({'id':identity,'name':identity,'url':'https://huggingface.co/'+identity,'downloads':m.get('downloads',0),'last_modified':m.get('lastModified',''),'note':'GGUF-Kandidat, RAM/CPU und Ollama-Kompatibilität vor Installation prüfen.'})
        report['local']=report['local'][:15]
    except Exception:report['errors'].append('Lokaler Modellkatalog nicht erreichbar.');report['local']=(previous or {}).get('local',[])
    return report

def cloud_generate(payload,key,model):
    # Recheck live prices before every inference; no paid/model fallback.
    if model!='openrouter/free' and model not in {m['id'] for m in cloud_catalog()}:
        raise ValueError('Cloudmodell ist aktuell nicht als kostenlose Variante bestätigt. Katalog aktualisieren.')
    body={'model':model,'messages':payload['messages'],'stream':False,'max_tokens':payload.get('options',{}).get('num_predict',500),
        'temperature':payload.get('options',{}).get('temperature',0.3),
        'provider':{'allow_fallbacks':False,'max_price':{'prompt':0,'completion':0}},'reasoning':{'enabled':False}}
    if payload.get('format')=='json':body['response_format']={'type':'json_object'}
    result=request(OPENROUTER+'/chat/completions',body,key,90)
    choices=result.get('choices') or []
    if not choices or not choices[0].get('message',{}).get('content'):raise ValueError('Gratis-Modell lieferte keine Antwort. Anderes Gratis-Modell oder lokal wählen.')
    choice=choices[0]
    return {'message':{'content':choice['message']['content']},'done_reason':choice.get('finish_reason'),'model':result.get('model',model)}
