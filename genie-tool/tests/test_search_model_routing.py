import os
import unittest
from unittest.mock import patch

from genie_tool.model.protocal import DeepSearchRequest
from genie_tool.util.llm_util import resolve_llm_model


class SearchModelRoutingTest(unittest.TestCase):
    def test_request_model_wins_and_gets_openai_provider(self) -> None:
        with patch.dict(os.environ, {"QUERY_DECOMPOSE_MODEL": "stale-model", "DEFAULT_MODEL": "fallback"}):
            self.assertEqual(
                "openai/qwen3.7-plus",
                resolve_llm_model("qwen3.7-plus", "QUERY_DECOMPOSE_MODEL"),
            )

    def test_existing_provider_prefix_is_preserved(self) -> None:
        self.assertEqual(
            "openai/glm-5.2",
            resolve_llm_model("openai/glm-5.2", "SEARCH_ANSWER_MODEL"),
        )

    def test_default_model_replaces_removed_deepseek_fallback(self) -> None:
        with patch.dict(os.environ, {"DEFAULT_MODEL": "qwen3.7-plus"}, clear=True):
            self.assertEqual(
                "openai/qwen3.7-plus",
                resolve_llm_model(None, "SEARCH_REASONING_MODEL"),
            )

    def test_deep_search_request_accepts_conversation_model(self) -> None:
        request = DeepSearchRequest(request_id="req-1", query="news", model="glm-5.2")
        self.assertEqual("glm-5.2", request.model)


if __name__ == "__main__":
    unittest.main()
