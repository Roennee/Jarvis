"""Speech model selection and locally curated personal vocabulary."""
from pathlib import Path
PROFILES={'base':'whisper-base','swiss':'whisper-swiss/ct2','personal':'whisper-personal'}
SWISS_REPO='gcoli/whisper-large-v3-turbo-swiss-german-mit'
SWISS_REVISION='540fb2e7a9135048963c5de9822579c96f8962e5'
def model_path(root,profile):
    if profile not in PROFILES: raise ValueError('Unbekanntes Sprachprofil')
    return Path(root)/'models'/PROFILES[profile]
def ready(root,profile):
    return all((model_path(root,profile)/name).is_file() for name in ('model.bin','config.json','tokenizer.json'))
def prompt(glossary,words):
    vocabulary=', '.join(dict.fromkeys([w.strip() for w in words if isinstance(w,str) and w.strip()]))[:1000]
    return ('Schweizerdeutsche Sprache, hochdeutsche Verschriftlichung. '+glossary+' '+vocabulary).strip()[:2000]

def word_errors(reference,hypothesis):
    import re
    def tokens(value):return re.sub(r'[^\w\s]',' ',value.lower().replace('ß','ss')).split()
    ref,hyp=tokens(reference),tokens(hypothesis);previous=list(range(len(hyp)+1))
    for i,a in enumerate(ref,1):
        row=[i]
        for j,b in enumerate(hyp,1):row.append(min(row[-1]+1,previous[j]+1,previous[j-1]+(a!=b)))
        previous=row
    return previous[-1],len(ref)
