"""공용 픽스처와 가짜 구현.

실제 LLM·얼굴 변환 API를 부르지 않는다. 라우터 시험은 13.3·14장대로
`app.dependency_overrides`로 get_comment_service·get_face_service·get_runbody_service를 덮어
가짜 LlmClient·FaceTransformer를 가진 서비스와 메모리 저장소를 쓰는 RUN바디 서비스를 끼운다.
"""

from __future__ import annotations

import asyncio
from collections.abc import AsyncIterator, Callable, Mapping
from dataclasses import dataclass, field
from datetime import UTC, datetime

import httpx
import pytest
from fastapi import FastAPI

from runkk_ai.comment import CommentService, LlmClient, LlmReply, get_comment_service
from runkk_ai.core import Settings
from runkk_ai.face import (
    FacePart,
    FaceTransformer,
    FaceTransformService,
    SdFacePartImages,
    get_face_service,
)
from runkk_ai.main import create_app
from runkk_ai.runbody import (
    InMemoryResultRepository,
    MonthlyService,
    RunbodyService,
    get_monthly_service,
    get_runbody_service,
)

TOKEN = "test-internal-token-0123456789"
AUTH = {"X-Internal-Token": TOKEN}

# 가장 작은 정상 PNG(1x1)와 JPEG 머리. 형식 검사는 앞 바이트만 본다.
PNG_1X1 = bytes.fromhex(
    "89504e470d0a1a0a0000000d4948445200000001000000010806000000"
    "1f15c4890000000d49444154789c6360000002000154a24f5d0000000049454e44ae426082"
)
# SOI + APP0(JFIF) + SOF0(1x1, 3성분). 형식 검사는 앞 바이트와 머리의 가로·세로만 본다(L28)
JPEG_HEAD = (
    b"\xff\xd8"
    + b"\xff\xe0\x00\x10JFIF\x00\x01\x01\x00\x00\x01\x00\x01\x00\x00"
    + b"\xff\xc0\x00\x11\x08\x00\x01\x00\x01\x03\x01\x11\x00\x02\x11\x01\x03\x11\x01"
)
JPEG_HEAD += b"\x00" * (64 - len(JPEG_HEAD))
PNG_IMAGE = PNG_1X1

VALID_SUMMARY: dict[str, object] = {
    "averageScore": 42.5,
    "distanceChangeMeters": 1200,
    "paceChangeSeconds": -8,
    "frequentDay": "SATURDAY",
    "frequentHourBand": 2,
    "runCount": 10,
}


def make_settings(**overrides: object) -> Settings:
    values: dict[str, object] = {"internal_token": TOKEN}
    values.update(overrides)
    return Settings(_env_file=None, **values)  # type: ignore[arg-type]


@dataclass
class FakeLlmClient(LlmClient):
    """정해 둔 응답을 돌려주거나 예외를 던지는 가짜 LLM."""

    text: str = "꾸준히 늘고 있어요. 이번 주도 토요일 아침에 달려 봐요!"
    stop_reason: str | None = "end_turn"
    error: BaseException | None = None
    delay_s: float = 0.0
    gate: asyncio.Event | None = None
    prompts: list[str] = field(default_factory=list)
    active: int = 0
    max_active: int = 0
    closed: bool = False

    def __post_init__(self) -> None:
        LlmClient.__init__(self, "fake-model")

    async def complete(self, prompt: str) -> LlmReply:
        self.prompts.append(prompt)
        self.active += 1
        self.max_active = max(self.max_active, self.active)
        try:
            if self.gate is not None:
                await self.gate.wait()
            if self.delay_s:
                await asyncio.sleep(self.delay_s)
            if self.error is not None:
                raise self.error
            return LlmReply(text=self.text, stop_reason=self.stop_reason, input_tokens=111, output_tokens=22)
        finally:
            self.active -= 1

    async def aclose(self) -> None:
        self.closed = True


def all_parts(png: bytes = PNG_1X1) -> dict[FacePart, bytes]:
    return dict.fromkeys(FacePart, png)


@dataclass
class FakeFaceTransformer(FaceTransformer):
    """정해 둔 부위 이미지를 돌려주거나 예외를 던지는 가짜 변환기."""

    parts: Mapping[FacePart, bytes] = field(default_factory=all_parts)
    error: BaseException | None = None
    delay_s: float = 0.0
    received: list[int] = field(default_factory=list)

    async def convert(self, image: bytes) -> SdFacePartImages:
        self.received.append(len(image))
        if self.delay_s:
            await asyncio.sleep(self.delay_s)
        if self.error is not None:
            raise self.error
        return SdFacePartImages(images=dict(self.parts))


@pytest.fixture
def fake_llm() -> FakeLlmClient:
    return FakeLlmClient()


@pytest.fixture
def fake_face() -> FakeFaceTransformer:
    return FakeFaceTransformer()


@pytest.fixture
def comment_service(fake_llm: FakeLlmClient) -> CommentService:
    return CommentService(llm=fake_llm, daily_cap=240)


@pytest.fixture
def face_service(fake_face: FakeFaceTransformer) -> FaceTransformService:
    return FaceTransformService(transformer=fake_face, timeout_s=1.0, max_image_bytes=1024 * 1024)


# 계약 표본의 updatedAt을 고정하려고 서비스 시계를 멈춘다
FIXED_NOW = datetime(2026, 10, 19, 10, 30, tzinfo=UTC)


@pytest.fixture
def runbody_repository() -> InMemoryResultRepository:
    """1회 분석과 월간 조회가 같은 저장소를 본다."""
    return InMemoryResultRepository()


@pytest.fixture
def monthly_service(runbody_repository: InMemoryResultRepository) -> MonthlyService:
    return MonthlyService(runbody_repository, clock=lambda: FIXED_NOW)


@pytest.fixture
async def runbody_service(runbody_repository: InMemoryResultRepository) -> AsyncIterator[RunbodyService]:
    service = RunbodyService(runbody_repository, clock=lambda: FIXED_NOW)
    yield service
    await service.aclose()


@pytest.fixture
def app(
    comment_service: CommentService,
    face_service: FaceTransformService,
    runbody_service: RunbodyService,
    monthly_service: MonthlyService,
) -> FastAPI:
    application = create_app(make_settings())
    application.dependency_overrides[get_comment_service] = lambda: comment_service
    application.dependency_overrides[get_face_service] = lambda: face_service
    application.dependency_overrides[get_runbody_service] = lambda: runbody_service
    application.dependency_overrides[get_monthly_service] = lambda: monthly_service
    return application


ClientFactory = Callable[..., httpx.AsyncClient]


@pytest.fixture
async def client(app: FastAPI) -> AsyncIterator[httpx.AsyncClient]:
    transport = httpx.ASGITransport(app=app)
    async with httpx.AsyncClient(transport=transport, base_url="http://ai", headers=AUTH) as c:
        yield c


@pytest.fixture
async def anon_client(app: FastAPI) -> AsyncIterator[httpx.AsyncClient]:
    """X-Internal-Token 없이 부르는 클라이언트."""
    transport = httpx.ASGITransport(app=app)
    async with httpx.AsyncClient(transport=transport, base_url="http://ai") as c:
        yield c
