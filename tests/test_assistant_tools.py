import json
import math
import random
import shutil
import struct
import tempfile
import unittest
import wave
from pathlib import Path
from unittest.mock import patch
import model_hub
from audio_tools import byte_range,clean_audio
from calendar_export import event_ics

class AudioTests(unittest.TestCase):
    def test_seek_ranges(self):
        self.assertIsNone(byte_range(None,100))
        for header,span in [('bytes=0-9',(0,9)),('bytes=25-',(25,99)),('bytes=-12',(88,99)),('bytes=90-200',(90,99))]:self.assertEqual(byte_range(header,100),span)
        for header in ['bytes=100-','bytes=8-4','bytes=-0','bytes=0-1,4-5','bogus','bytes=-']:
            with self.assertRaises(ValueError):byte_range(header,100)

    @unittest.skipUnless(shutil.which('ffmpeg'),'ffmpeg not installed')
    def test_clean_speech_with_quiet_gaps_is_not_spectrally_altered(self):
        rate=16000
        frames=[0]*rate+[int(6000*math.sin(2*math.pi*440*i/rate)) for i in range(rate)]+[0]*rate
        raw=struct.pack('<'+'h'*len(frames),*frames)
        with tempfile.TemporaryDirectory() as folder:
            source=Path(folder)/'source.wav';target=Path(folder)/'clean.wav'
            with wave.open(str(source),'wb') as w:w.setparams((1,2,rate,0,'NONE',''));w.writeframes(raw)
            clean_audio(source,target)
            with wave.open(str(target),'rb') as w:self.assertEqual(w.readframes(w.getnframes()),raw)

    @unittest.skipUnless(shutil.which('ffmpeg'),'ffmpeg not installed')
    def test_short_word_gaps_protect_clean_utterance(self):
        rate=16000
        # Fifty-millisecond gaps: a 100-ms noise window incorrectly mixed these
        # with voiced samples and classified clean speech as background noise.
        frames=([int(6000*math.sin(2*math.pi*440*i/rate)) for i in range(5600)]+[0]*800)*8
        raw=struct.pack('<'+'h'*len(frames),*frames)
        with tempfile.TemporaryDirectory() as folder:
            source=Path(folder)/'source.wav';target=Path(folder)/'clean.wav'
            with wave.open(str(source),'wb') as w:w.setparams((1,2,rate,0,'NONE',''));w.writeframes(raw)
            clean_audio(source,target)
            with wave.open(str(target),'rb') as w:self.assertEqual(w.readframes(w.getnframes()),raw)

    @unittest.skipUnless(shutil.which('ffmpeg'),'ffmpeg not installed')
    def test_denoise_reduces_stationary_noise_without_trimming_timeline(self):
        rng=random.Random(1);rate=16000
        samples=[int(2200*rng.uniform(-1,1)+(5000*math.sin(2*math.pi*220*i/rate) if rate<=i<2*rate else 0)) for i in range(3*rate)]
        with tempfile.TemporaryDirectory() as folder:
            source=Path(folder)/'source.wav';target=Path(folder)/'clean.wav'
            with wave.open(str(source),'wb') as w:w.setparams((1,2,rate,0,'NONE',''));w.writeframes(struct.pack('<'+'h'*len(samples),*samples))
            original=source.read_bytes();clean_audio(source,target)
            with wave.open(str(target),'rb') as w:
                self.assertEqual(w.getnframes(),len(samples));self.assertEqual(w.getframerate(),rate)
                output=struct.unpack('<'+'h'*w.getnframes(),w.readframes(w.getnframes()))
            def rms(values):return math.sqrt(sum(x*x for x in values)/len(values))
            self.assertLess(rms(output[rate//2:rate]),rms(samples[rate//2:rate])*.85)
            self.assertGreater(rms(output[rate:2*rate]),rms(output[rate//2:rate])*2)
            self.assertEqual(source.read_bytes(),original)

class CloudTests(unittest.TestCase):
    def catalog(self):return {'data':[{'id':'a:free','pricing':{'prompt':'0','completion':'0'},'created':2},{'id':'paid','pricing':{'prompt':'1','completion':'0'}},{'id':'hidden:free','pricing':{'prompt':'0','completion':'0','request':'0.1'}},{'id':'missing:free'}]}
    def test_only_verified_zero_cost_variants(self):self.assertEqual([m['id'] for m in model_hub.free_models(self.catalog())],['a:free'])
    def test_price_change_blocks_inference(self):
        with patch.object(model_hub,'request',return_value={'data':[]}) as req:
            with self.assertRaises(ValueError):model_hub.cloud_generate({'messages':[]},'secret','a:free')
            self.assertEqual(req.call_count,1)
    def test_no_paid_fallback_and_json_forwarded(self):
        with patch.object(model_hub,'request',side_effect=[self.catalog(),{'choices':[{'message':{'content':'{}'},'finish_reason':'stop'}],'model':'a:free'}]) as req:
            result=model_hub.cloud_generate({'messages':[{'role':'user','content':'Test'}],'format':'json','options':{'num_predict':80}},'secret','a:free')
            body=req.call_args.args[1]
            self.assertFalse(body['provider']['allow_fallbacks']);self.assertEqual(body['provider']['max_price'],{'prompt':0,'completion':0})
            self.assertEqual(body['response_format'],{'type':'json_object'});self.assertEqual(result['message']['content'],'{}')
    def test_catalog_failure_preserves_last_successful_candidates(self):
        with patch.object(model_hub,'request',side_effect=ValueError('offline')):
            result=model_hub.scan({'cloud':[{'id':'previous'}],'local':[{'id':'old'}]})
        self.assertEqual(result['cloud'][0]['id'],'previous');self.assertEqual(len(result['errors']),2)

class CalendarTests(unittest.TestCase):
    def test_ics_escape_timezone_and_utf8_folding(self):
        content=event_ics({'id':'abc','title':'ä'*100+'\nBEGIN:VEVENT','details':'a,b;c\\d','start':'2026-11-20T14:00:00+01:00','end':'2026-11-20T15:00:00+01:00'})
        text=content.decode().replace('\r\n ','');self.assertIn('DTSTART:20261120T130000Z',text);self.assertIn('\\nBEGIN:VEVENT',text);self.assertIn('a\\,b\\;c\\\\d',text)
        self.assertTrue(all(len(line)<=75 for line in content.split(b'\r\n')))

if __name__=='__main__':unittest.main()
