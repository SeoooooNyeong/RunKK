"""아바타(Node) 중계 응답 형식. 안선영 님 RunKK Node API 명세 v1.2의 HeadJob·SavedHead·BodyPack을 그대로 따른다.

이미지 머리 검사(image_dimensions)는 셀카 접수 전 입력 검사용이다(L28).
"""

from __future__ import annotations

from typing import Any, Literal, get_args

from pydantic import BaseModel, ConfigDict, Field, model_validator

# {"reason"} 오류 본문의 값. api avatar 쪽 FailureReason과 이름을 맞춘다(6.1.11)
FaceFailureReason = Literal[
    "UNSUPPORTED_IMAGE",
    "TOO_LARGE",
    "UPSTREAM_FAILED",
    "TIMEOUT",
    "JOB_NOT_FOUND",
    "JOB_NOT_READY",
    "HEAD_NOT_FOUND",
    "BODY_NOT_FOUND",
]
FACE_FAILURE_REASONS: frozenset[str] = frozenset(get_args(FaceFailureReason))

JobStatus = Literal["QUEUED", "RUNNING", "SUCCEEDED", "FAILED"]

# 이미지 형식 시그니처. 라우터(입력 검사)가 쓴다
JPEG_SIGNATURE = b"\xff\xd8\xff"
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"

# 이미지 머리 정보에서 가로·세로만 읽는다(디코드하지 않는다). 압축 폭탄처럼 1MB 안에 큰 픽셀 수를 담은 입력을
# 아바타 서버로 넘기지 않으려는 것이다(코드 리뷰 2차 L28)
DEFAULT_MAX_IMAGE_PIXELS = 4096 * 4096
_PNG_IHDR = b"IHDR"
_PNG_HEADER_END = 24
# JPEG 프레임 시작(SOF0~SOF15). DHT(C4)·JPG(C8)·DAC(CC)는 이름만 비슷한 다른 표지다
_JPEG_SOF_MARKERS = frozenset(range(0xC0, 0xD0)) - {0xC4, 0xC8, 0xCC}
# 길이 칸이 없는 표지(TEM, RST0~7)와 프레임 전에 나오면 크기를 알 수 없는 표지(SOS, EOI)
_JPEG_STANDALONE_MARKERS = frozenset({0x01, *range(0xD0, 0xD8)})
_JPEG_END_MARKERS = frozenset({0xD9, 0xDA})
_JPEG_FILL = 0xFF


def image_dimensions(image: bytes) -> tuple[int, int] | None:
    """JPEG·PNG 머리 정보의 (가로, 세로). 머리가 깨졌거나 크기가 0이면 None."""
    if image.startswith(PNG_SIGNATURE):
        return _png_dimensions(image)
    if image.startswith(JPEG_SIGNATURE):
        return _jpeg_dimensions(image)
    return None


def _png_dimensions(image: bytes) -> tuple[int, int] | None:
    if len(image) < _PNG_HEADER_END or image[12:16] != _PNG_IHDR:
        return None
    return _positive(int.from_bytes(image[16:20], "big"), int.from_bytes(image[20:24], "big"))


def _jpeg_dimensions(image: bytes) -> tuple[int, int] | None:
    i = 2
    while i + 4 <= len(image) and image[i] == _JPEG_FILL:
        marker = image[i + 1]
        if marker in _JPEG_SOF_MARKERS:
            return _jpeg_frame_dimensions(image, i)
        if marker == _JPEG_FILL:
            i += 1
        elif marker in _JPEG_STANDALONE_MARKERS:
            i += 2
        elif marker in _JPEG_END_MARKERS:
            return None
        else:
            i += 2 + int.from_bytes(image[i + 2 : i + 4], "big")
    return None


def _jpeg_frame_dimensions(image: bytes, at: int) -> tuple[int, int] | None:
    if at + 9 > len(image):
        return None
    height = int.from_bytes(image[at + 5 : at + 7], "big")
    width = int.from_bytes(image[at + 7 : at + 9], "big")
    return _positive(width, height)


def _positive(width: int, height: int) -> tuple[int, int] | None:
    return (width, height) if width > 0 and height > 0 else None


class JobErrorOut(BaseModel):
    """Node 작업 실패 사유. code는 Node 명세 8장(SELFIE_QUALITY_REJECTED, NO_FACE_DETECTED, UPSTREAM_TIMEOUT …)."""

    code: str
    message: str


class HeadJobOut(BaseModel):
    """머리 생성 작업(HeadJob). Node의 userId·statusPath·resultPath 등 나머지 필드는 api가 쓰지 않아 뺀다."""

    jobId: str
    status: JobStatus
    headId: str | None = None
    error: JobErrorOut | None = None


class HeadOut(BaseModel):
    """완성된 머리(SavedHead). 레이어 3장(face·eyes·hair)은 1024×1024 PNG Data URL이다.

    traits·alignment·mobile 등 나머지는 Node가 주는 그대로 넘긴다(extra=allow). Node가 필드를 더해도 ai를 고치지 않는다.
    Node의 id는 headId로 읽고, 과금 지표 metrics는 응답에서 뺀다(로그·정산용이지 앱이 쓸 값이 아니다).
    """

    model_config = ConfigDict(extra="allow")

    headId: str
    faceImage: str = Field(pattern=r"^data:image/png;base64,")
    eyesImage: str = Field(pattern=r"^data:image/png;base64,")
    hairImage: str = Field(pattern=r"^data:image/png;base64,")

    @model_validator(mode="before")
    @classmethod
    def _normalize(cls, data: Any) -> Any:
        if not isinstance(data, dict):
            return data
        values = dict(data)
        values.pop("metrics", None)
        if "headId" not in values and "id" in values:
            values["headId"] = values["id"]
        values.pop("id", None)
        return values


class BodyOut(BaseModel):
    """기본 몸 프리셋(BodyPack). pack 안은 Node 명세 4.9·4.8(mobile 합성 계약)대로 그대로 넘긴다."""

    pack: dict[str, Any]
