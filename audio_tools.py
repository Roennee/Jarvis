"""Bounded local audio processing; original files remain available for comparison."""
import re
import shutil
import subprocess
import tempfile
import wave
import array
import math
import sys
from pathlib import Path

def byte_range(header,size):
    if header is None:return None
    match=re.fullmatch(r'bytes=(\d*)-(\d*)',header.strip())
    if not match or not any(match.groups()) or size<=0:raise ValueError('Range')
    left,right=match.groups()
    if not left:
        if int(right)==0:raise ValueError('Range')
        start,end=max(0,size-int(right)),size-1
    else:start,end=int(left),min(size-1,int(right)) if right else size-1
    if start>=size or end<start:raise ValueError('Range')
    return start,end

def clean_audio(source,target):
    binary=shutil.which('ffmpeg')
    if not binary:raise ValueError('Audiofilter fehlt. Auf dem Laptop: sudo apt install ffmpeg')
    target=Path(target)
    fd=tempfile.NamedTemporaryFile(dir=target.parent,suffix='.wav',delete=False);temporary=Path(fd.name);fd.close()
    raw=temporary.with_suffix('.raw.wav')
    try:
        result=subprocess.run([binary,'-nostdin','-v','error','-y','-protocol_whitelist','file,pipe','-i',str(source),'-t','601','-vn','-ac','1','-ar','16000',
            '-c:a','pcm_s16le',str(raw)],capture_output=True,timeout=90)
        if result.returncode or raw.stat().st_size<=44:raise ValueError('Audio konnte nicht gelesen werden. Original bleibt erhalten.')
        levels=[]
        with wave.open(str(raw),'rb') as stream:
            if stream.getnframes()/stream.getframerate()>600:raise ValueError('Rauschfilter unterstützt höchstens 10 Minuten. Original verwenden.')
            while True:
                chunk=stream.readframes(1600)
                if not chunk:break
                values=array.array('h',chunk)
                if sys.byteorder!='little':values.byteswap()
                levels.append(math.sqrt(sum(v*v for v in values)/len(values))/32768)
        # Quiet gaps anchor the estimate. Do not apply spectral subtraction to a
        # clean signal: it can remove quiet consonants and degrade recognition.
        if not levels:raise ValueError('Keine Audiosamples gefunden. Original bleibt erhalten.')
        levels.sort();floor=20*math.log10(max(levels[len(levels)//5],1e-9))
        if floor < -55:
            raw.replace(target);return
        noise_floor=max(-50,min(-25,floor+3))
        result=subprocess.run([binary,'-nostdin','-v','error','-y','-i',str(raw),'-af',
            f'highpass=f=80,lowpass=f=7600,afftdn=nr=16:nf={noise_floor:.1f}',
            '-c:a','pcm_s16le',str(temporary)],capture_output=True,timeout=90)
        if result.returncode or temporary.stat().st_size<=44:raise ValueError('Audio konnte nicht gefiltert werden. Original bleibt erhalten.')
        temporary.replace(target)
    finally:
        temporary.unlink(missing_ok=True);raw.unlink(missing_ok=True)
