#!/usr/bin/env python3
"""Explicit opt-in LoRA training on reviewed own-voice clips; requires a CUDA GPU.
No background training, uploads, or automatic activation of untested weights.
"""
import argparse,json,os,random,re,sqlite3
from pathlib import Path
from speech_profile import SWISS_REPO,SWISS_REVISION
ROOT=Path(__file__).resolve().parent
p=argparse.ArgumentParser();p.add_argument('--data-dir',default=os.environ.get('JARVIS_DATA_DIR',str(ROOT)));p.add_argument('--export-only',action='store_true');args=p.parse_args()
data=Path(args.data_dir).resolve();out=data/'private'/'speech-training';out.mkdir(parents=True,exist_ok=True);os.chmod(out,0o700)
with sqlite3.connect(data/'jarvis.sqlite3') as c:
    samples=c.execute("SELECT recording_id,corrected FROM speech_samples WHERE own_voice=1 AND corrected!='' ORDER BY recording_id").fetchall()
from faster_whisper.audio import decode_audio
clips=[]
for rid,corrected in samples:
    audio=data/'recordings'/rid
    if not re.fullmatch('[0-9a-f]{32}',rid) or not audio.is_file():continue
    waveform=decode_audio(str(audio),sampling_rate=16000)
    if not 1<=len(waveform)/16000<=30:continue
    clips.append({'id':rid,'audio':str(audio),'text':corrected})
# Group identical corrected sentences to avoid a duplicated sentence leaking into evaluation.
groups={}
for clip in clips:groups.setdefault(re.sub(r'\W+',' ',clip['text'].lower().replace('ß','ss')).strip(),[]).append(clip)
split_file=out/'heldout-groups.json'
if split_file.exists(): heldout=set(json.loads(split_file.read_text()))
else:
    keys=sorted(groups);random.Random(42).shuffle(keys);heldout=set(keys[:max(3,len(keys)//5)])
    if len(groups)>=10:split_file.write_text(json.dumps(sorted(heldout),ensure_ascii=False));split_file.chmod(0o600)
validation=[clip for k,values in groups.items() if k in heldout for clip in values]
train=[clip for k,values in groups.items() if k not in heldout for clip in values]
for name,rows in [('train',train),('validation',validation)]:
    path=out/(name+'.jsonl');path.write_text(''.join(json.dumps(r,ensure_ascii=False)+'\n' for r in rows));path.chmod(0o600)
print(f'Nur bestätigte eigene Stimme: {len(train)} Trainingsclips, {len(validation)} unabhängige Prüfclips. Ablage: {out}')
if args.export_only:raise SystemExit(0)
if len(clips)<30 or len(groups)<10:raise SystemExit('Noch zu wenig geprüfte eigene Sprache. Erst mindestens 30 kurze Clips mit mindestens 10 unterschiedlichen Sätzen sammeln.')
import torch
if not torch.cuda.is_available():raise SystemExit('Echtes Training benötigt hier eine CUDA-GPU. Auf dem Surface bitte nur --export-only verwenden; keine Modellgewichte geändert.')
from transformers import WhisperProcessor,WhisperTokenizerFast,WhisperForConditionalGeneration,Seq2SeqTrainer,Seq2SeqTrainingArguments
from peft import LoraConfig,get_peft_model
processor=WhisperProcessor.from_pretrained(SWISS_REPO,revision=SWISS_REVISION,language='german',task='transcribe')
processor.tokenizer=WhisperTokenizerFast.from_pretrained(SWISS_REPO,revision=SWISS_REVISION,language='german',task='transcribe')
base=WhisperForConditionalGeneration.from_pretrained(SWISS_REPO,revision=SWISS_REVISION,torch_dtype=torch.float16)
base.config.use_cache=False
model=get_peft_model(base,LoraConfig(r=8,lora_alpha=16,lora_dropout=.05,target_modules=['q_proj','v_proj'],bias='none'))
class Clips(torch.utils.data.Dataset):
    def __init__(self,rows):self.rows=rows
    def __len__(self):return len(self.rows)
    def __getitem__(self,i):
        row=self.rows[i];wave=decode_audio(row['audio'],sampling_rate=16000)
        return {'input_features':processor.feature_extractor(wave,sampling_rate=16000).input_features[0], 'labels':processor.tokenizer(row['text']).input_ids}
def collate(batch):
    features=processor.feature_extractor.pad([{'input_features':r['input_features']} for r in batch],return_tensors='pt')
    labels=processor.tokenizer.pad([{'input_ids':r['labels']} for r in batch],return_tensors='pt')
    values=labels['input_ids'].masked_fill(labels.attention_mask.ne(1),-100)
    if (values[:,0]==model.config.decoder_start_token_id).all():values=values[:,1:]
    features['labels']=values;return features
training=Seq2SeqTrainingArguments(output_dir=str(out/'checkpoints'),per_device_train_batch_size=1,gradient_accumulation_steps=8,learning_rate=1e-5,num_train_epochs=3,fp16=True,save_strategy='epoch',save_total_limit=1,logging_steps=10,report_to=[],remove_unused_columns=False,label_names=['labels'])
trainer=Seq2SeqTrainer(model=model,args=training,train_dataset=Clips(train),eval_dataset=Clips(validation),data_collator=collate)
trainer.train();model.save_pretrained(out/'adapter');merged=model.merge_and_unload();merged.save_pretrained(out/'merged');processor.save_pretrained(out/'merged')
del trainer,model,base,merged;torch.cuda.empty_cache()
from ctranslate2.converters import TransformersConverter
candidate=out/'candidate-ct2';TransformersConverter(str(out/'merged'),copy_files=['tokenizer.json','preprocessor_config.json']).convert(str(candidate),quantization='int8',force=True)
# An export is a candidate, not a verified improvement; never silently select it.
print(f'Trainingskandidat erstellt: {candidate}. Noch NICHT aktiviert. Gegen die unverwendeten Prüfclips vergleichen; erst danach als persönliches Profil übernehmen.')
