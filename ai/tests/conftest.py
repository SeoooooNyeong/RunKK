"""공용 픽스처와 가짜 구현.

실제 LLM·얼굴 변환 API를 부르지 않는다. 라우터 시험은 13.3·14장대로
`app.dependency_overrides`로 get_comment_service·get_face_service·get_runbody_service를 덮어
가짜 LlmClient·AvatarClient를 가진 서비스와 메모리 저장소를 쓰는 RUN바디 서비스를 끼운다.
"""

from __future__ import annotations

import asyncio
from collections.abc import AsyncIterator, Callable
from dataclasses import dataclass, field
from datetime import UTC, datetime
from typing import Any

import httpx
import pytest
from fastapi import FastAPI

from runkk_ai.comment import CommentService, LlmClient, LlmReply, get_comment_service
from runkk_ai.core import Settings
from runkk_ai.face import (
    AvatarClient,
    AvatarService,
    BodyOut,
    HeadJobOut,
    HeadOut,
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


# 아바타(Node) 계약 표본. tests/contract/faces-*.json과 글자까지 같아야 한다
DATA_URL_PNG = "data:image/png;base64," + __import__("base64").b64encode(PNG_1X1).decode("ascii")
JOB_ID = "6f1c2a3e-9b4d-4c8e-8a1f-2d3e4f5a6b7c"
HEAD_ID = "0b2c4d6e-8f1a-4b3c-9d5e-7f8a9b0c1d2e"
DEMO_USER = "42"
HEAD_SAMPLE: dict[str, Any] = {
    "headId": HEAD_ID,
    "userId": DEMO_USER,
    "faceImage": DATA_URL_PNG,
    "eyesImage": DATA_URL_PNG,
    "hairImage": DATA_URL_PNG,
    "traits": {
        "hair": "검은색 가르마 머리",
        "eyes": "둥근 갈색 눈",
        "eyebrows": "완만한 눈썹",
        "nose": "작은 코",
        "mouth": "작은 미소",
        "face": "둥근 얼굴",
        "skin_tone": "밝고 따뜻한 색감",
        "accessories": "없음",
        "hair_length": "short",
    },
    "hairLength": "short",
    "alignment": {"pivotX": 0.5, "pivotY": 0.9, "skin": 0, "estimated": False},
    "mobile": {
        "canvas": [1024, 1024],
        "anchor": [0.5, 0.88],
        "renderFaceImage": DATA_URL_PNG,
        "displayHeight": 493.3,
        "neckRemoved": True,
        "skinColor": [245, 207, 188],
    },
    "createdAt": "2026-10-29T01:53:02.761Z",
}
BODY_SAMPLE: dict[str, Any] = {
    "pack": {
        "format": "runkk-body-pack",
        "version": 2,
        "rigVersion": "runkk-front-v1",
        "renderer": "seamless-mesh-v1",
        "id": "daily",
        "name": "기본 러닝복",
        "image": DATA_URL_PNG,
        "neck": [0.5, 0.083],
        "skinZones": [{"rect": [0.4, 0.0, 0.2, 0.1]}],
        "preserveArt": True,
        "mobile": {
            "canvas": [633, 1110],
            "neck": [0.5, 0.083],
            "neckWidth": 120,
            "neckHeight": 60,
            "neckTop": 30,
            "neckDetected": True,
        },
    }
}


@dataclass
class FakeAvatarClient(AvatarClient):
    """정해 둔 작업·머리·몸을 돌려주거나 예외를 던지는 가짜 아바타 서버 클라이언트."""

    job_status: str = "QUEUED"
    job_error: dict[str, Any] | None = None
    head: dict[str, Any] = field(default_factory=lambda: dict(HEAD_SAMPLE))
    body_pack: dict[str, Any] = field(default_factory=lambda: dict(BODY_SAMPLE))
    error: BaseException | None = None
    delay_s: float = 0.0
    submitted: list[tuple[str, str, int]] = field(default_factory=list)  # (userId, clientRequestId, 바이트 수)
    calls: list[str] = field(default_factory=list)
    closed: bool = False

    async def _gate(self, name: str) -> None:
        self.calls.append(name)
        if self.delay_s:
            await asyncio.sleep(self.delay_s)
        if self.error is not None:
            raise self.error

    async def submit_head(self, user_id: str, client_request_id: str, image: bytes) -> HeadJobOut:
        self.submitted.append((user_id, client_request_id, len(image)))
        await self._gate("submit")
        return HeadJobOut(jobId=JOB_ID, status=self.job_status)  # type: ignore[arg-type]

    async def job(self, user_id: str, job_id: str) -> HeadJobOut:
        await self._gate("job")
        head_id = HEAD_ID if self.job_status == "SUCCEEDED" else None
        return HeadJobOut(jobId=job_id, status=self.job_status, headId=head_id, error=self.job_error)  # type: ignore[arg-type]

    async def job_result(self, user_id: str, job_id: str) -> HeadOut:
        await self._gate("result")
        return HeadOut.model_validate(self.head)

    async def latest_head(self, user_id: str) -> HeadOut:
        await self._gate("latest")
        return HeadOut.model_validate(self.head)

    async def body(self, body_id: str) -> BodyOut:
        await self._gate("body")
        return BodyOut.model_validate(self.body_pack)

    async def aclose(self) -> None:
        self.closed = True


@pytest.fixture
def fake_llm() -> FakeLlmClient:
    return FakeLlmClient()


@pytest.fixture
def fake_face() -> FakeAvatarClient:
    return FakeAvatarClient()


@pytest.fixture
def comment_service(fake_llm: FakeLlmClient) -> CommentService:
    return CommentService(llm=fake_llm, daily_cap=240)


@pytest.fixture
def face_service(fake_face: FakeAvatarClient) -> AvatarService:
    return AvatarService(client=fake_face, timeout_s=1.0, max_image_bytes=1024 * 1024)


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
    face_service: AvatarService,
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
