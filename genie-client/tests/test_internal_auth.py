import os
import unittest
from app.security import require_internal_mcp_token

class InternalAuthTest(unittest.TestCase):
    def test_constant_time_token_accepts_matching_value(self):
        os.environ['GENIE_INTERNAL_MCP_TOKEN'] = 'test-token'
        self.assertIsNone(require_internal_mcp_token('test-token'))
    def test_missing_or_wrong_token_is_fixed_401(self):
        os.environ['GENIE_INTERNAL_MCP_TOKEN'] = 'test-token'
        for value in (None, 'wrong'):
            with self.assertRaises(Exception) as ctx:
                require_internal_mcp_token(value)
            self.assertEqual(getattr(ctx.exception, 'status_code', None), 401)

if __name__ == '__main__': unittest.main()
