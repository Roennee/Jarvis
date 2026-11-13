#!/usr/bin/env python3
"""Short-lived CPU worker. Uses only previously downloaded local model files."""
import json, os, sys
os.environ.setdefault('UCX_VFS_ENABLE','n')
from faster_whisper import WhisperModel
model = WhisperModel(sys.argv[2], device='cpu', compute_type='int8', cpu_threads=2, num_workers=1, local_files_only=True)
segments, info = model.transcribe(sys.argv[1], language='de', beam_size=3, vad_filter=True, initial_prompt=os.environ.get('JARVIS_GLOSSARY','')[:2000] or None)
print(json.dumps({'text':' '.join(segment.text.strip() for segment in segments).strip()},ensure_ascii=False))
