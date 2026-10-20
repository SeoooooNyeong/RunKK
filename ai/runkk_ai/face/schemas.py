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


class FacePart(StrEnum):
    EYES = "EYES"
    NOSE = "NOSE"
    MOUTH = "MOUTH"
    EARS = "EARS"
    HAIR = "HAIR"


class FaceOut(BaseModel):
    """부위별 base64 PNG. 저장 키는 ai 서버가 만들지 않는다(api FacePartImageStore가 만든다)."""

    parts: dict[FacePart, str]
