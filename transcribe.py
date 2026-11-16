#!/usr/bin/env python3
"""Short-lived CPU worker. Uses only previously downloaded local model files."""
import json, os, sys, tempfile
from pathlib import Path
from audio_tools import clean_audio
os.environ.setdefault('UCX_VFS_ENABLE','n')
from faster_whisper import WhisperModel
model = WhisperModel(sys.argv[2], device='cpu', compute_type='int8', cpu_threads=2, num_workers=1, local_files_only=True)
temporary=tempfile.TemporaryDirectory(prefix='jarvis-stt-')
source=sys.argv[1]
if os.environ.get('JARVIS_DENOISE')=='1':
    try:
        cleaned=Path(temporary.name)/'speech.wav';clean_audio(source,cleaned);source=str(cleaned)
    except ValueError as error:print(str(error),file=sys.stderr)
segments, info = model.transcribe(source, language='de', beam_size=3, vad_filter=True, condition_on_previous_text=False, initial_prompt=os.environ.get('JARVIS_GLOSSARY','')[:2000] or None)
print(json.dumps({'text':' '.join(segment.text.strip() for segment in segments).strip(),'duration':info.duration},ensure_ascii=False))

temporary.cleanup()
