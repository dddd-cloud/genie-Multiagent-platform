# -*- coding: utf-8 -*-
# =====================
# 
# 
# Author: liumin.423
# Date:   2025/7/8
# =====================
import json
import os
from typing import List, Any, Optional

from litellm import acompletion

from genie_tool.util.log_util import timer, AsyncTimer
from genie_tool.util.sensitive_detection import SensitiveWordsReplace


def resolve_llm_model(request_model: str | None = None, env_name: str | None = None) -> str:
    """Resolve one request's model and make bare names explicit for LiteLLM.

    This tool service talks to an OpenAI-compatible endpoint. LiteLLM cannot infer a
    provider from bare names such as ``qwen3.7-plus`` or ``deepseek-v4-flash``.
    A model carried by the current chat takes precedence over tool-specific env vars.
    """
    selected = (request_model or "").strip()
    if not selected and env_name:
        selected = os.getenv(env_name, "").strip()
    if not selected:
        selected = os.getenv("DEFAULT_MODEL", "").strip()
    if not selected:
        selected = "deepseek-v4-flash"
    return selected if "/" in selected else f"openai/{selected}"


@timer(key="enter")
async def ask_llm(
        messages: str | List[Any],
        model: str,
        temperature: float = None,
        top_p: float = None,
        stream: bool = False,

        # 自定义字段
        only_content: bool = False,     # 只返回内容

        extra_headers: Optional[dict] = None,
        **kwargs,
):
    if isinstance(messages, str):
        messages = [{"role": "user", "content": messages}]
    if os.getenv("SENSITIVE_WORD_REPLACE", "false") == "true":
        for message in messages:
            if isinstance(message.get("content"), str):
                message["content"] = SensitiveWordsReplace.replace(message["content"])
            else:
                message["content"] = json.loads(
                    SensitiveWordsReplace.replace(json.dumps(message["content"], ensure_ascii=False)))
    response = await acompletion(
        messages=messages,
        model=model,
        temperature=temperature,
        top_p=top_p,
        stream=stream,
        extra_headers=extra_headers,
        **kwargs
    )
    async with AsyncTimer(key=f"exec ask_llm"):
        if stream:
            async for chunk in response:
                if only_content:
                    if chunk.choices and chunk.choices[0] and chunk.choices[0].delta and chunk.choices[0].delta.content:
                        yield chunk.choices[0].delta.content
                else:
                    yield chunk
        else:
            yield response.choices[0].message.content if only_content else response


if __name__ == "__main__":
    pass
