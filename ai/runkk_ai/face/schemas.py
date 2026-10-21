"""얼굴 변환 응답 형식(6.1.16, D-59). 부위 이름은 api avatar.FacePart와 같다(6.1.11)."""

from __future__ import annotations

from enum import StrEnum
from typing import Literal, get_args

from pydantic import BaseModel

# api avatar.FailureReason과 같은 이름(6.1.11 FaceTransformGateway)
FaceFailureReason = Literal["NO_FACE", "MULTIPLE_FACES", "UNSUPPORTED_IMAGE", "TOO_LARGE", "UPSTREAM_FAILED", "TIMEOUT"]
FACE_FAILURE_REASONS: frozenset[str] = frozenset(get_args(FaceFailureReason))

# 이미지 형식 시그니처. 라우터(입력 검사)와 서비스(결과 검사)가 같은 값을 쓴다
JPEG_SIGNATURE = b"\xff\xd8\xff"
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"

# 이미지 머리 정보에서 가로·세로만 읽는다(디코드하지 않는다). 압축 폭탄처럼 1MB 안에 큰 픽셀 수를 담은 입력을
# 외부 변환 API로 넘기지 않으려는 것이다(코드 리뷰 2차 L28)
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


class FacePart(StrEnum):
    EYES = "EYES"
    NOSE = "NOSE"
    MOUTH = "MOUTH"
    EARS = "EARS"
    HAIR = "HAIR"


class FaceOut(BaseModel):
    """부위별 base64 PNG. 저장 키는 ai 서버가 만들지 않는다(api FacePartImageStore가 만든다)."""

    parts: dict[FacePart, str]
