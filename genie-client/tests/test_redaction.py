import pathlib
import unittest

class RedactionTest(unittest.TestCase):
    def test_client_sources_do_not_log_raw_transport_values(self):
        root = pathlib.Path(__file__).parents[1]
        text = '\n'.join((root / p).read_text(encoding='utf-8') for p in ('server.py','app/client.py','app/header.py'))
        self.assertNotIn('request headers:', text)
        self.assertNotIn('cookies={self.cookies}', text)
        self.assertNotIn('with arguments:', text)

if __name__ == '__main__': unittest.main()
