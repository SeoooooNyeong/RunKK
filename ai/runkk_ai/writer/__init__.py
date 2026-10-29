"""runkk-writer: 분석 결과를 코칭 문장으로 바꾸는 자체 모델 쪽 코드(S15P31A207-191·194·197).

brief(입력 요약)·prompt(지시문)·checks(출력 검사)는 학습 데이터와 실제 추론이 함께 쓴다.
dataset은 학습 데이터를 만드는 오프라인 도구다(서버가 import하지 않는다).
client(llama.cpp 서버 호출)·service(검사·재시도·템플릿 폴백)는 main이 분석 서비스에 끼운다(S15P31A207-196).
"""

from runkk_ai.writer.client import CompletionClient, LlamaCppClient
from runkk_ai.writer.registry import (
    InMemoryModelRegistry,
    ModelNotFound,
    ModelRegistry,
    NoPreviousModel,
    WriterModel,
)
from runkk_ai.writer.router import ModelsRouter, get_model_registry
from runkk_ai.writer.service import WriterService

__all__ = [
    "CompletionClient",
    "InMemoryModelRegistry",
    "LlamaCppClient",
    "ModelNotFound",
    "ModelRegistry",
    "ModelsRouter",
    "NoPreviousModel",
    "WriterModel",
    "WriterService",
    "get_model_registry",
]
