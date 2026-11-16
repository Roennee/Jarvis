import unittest,tempfile
from pathlib import Path
from speech_profile import model_path,ready,prompt,word_errors
class SpeechProfileTests(unittest.TestCase):
    def test_no_uninstalled_or_arbitrary_path(self):
        with tempfile.TemporaryDirectory() as root:
            self.assertFalse(ready(root,'swiss'))
            path=model_path(root,'swiss');path.mkdir(parents=True)
            for name in ('model.bin','config.json','tokenizer.json'):(path/name).write_bytes(b'x')
            self.assertTrue(ready(root,'swiss'))
            with self.assertRaises(ValueError):model_path(root,'../../private')
    def test_only_curated_words_and_bounded_prompt(self):
        text=prompt('Sorter',['Mischa','Mischa','Beumer'])
        self.assertEqual(text.count('Mischa'),1);self.assertIn('Beumer',text)
        self.assertLessEqual(len(prompt('x'*4000,[])),2000)
    def test_word_error_metric(self):
        self.assertEqual(word_errors('Mischa anrufen','Mischa anrufen'),(0,2))
        self.assertEqual(word_errors('morgen um 14 Uhr','morgen um 15 Uhr'),(1,4))
