"""달리기 엉덩이 잇기 메시(판정 round1 C).

승인 달리기 riv(avatar_run_approved, avatar_male_run)의 Image 「Cloth textured hip connection」은 타원 메시(윤곽 꼭짓점 16개)로 하의
달리기 다리 텍스처(run_leg, 256x768)의 가운데 조각(u 0.344~0.656, v 약 0.209~0.271 = x 88~168, y 160~208)을 몸통과 다리 사이에
붙인다. 그 조각은 천 가운데라 외곽선이 없고, 같은 자리를 앞쪽 다리 메시도 허벅지에 써서 거기에 외곽선을 그리면 허벅지에도 선이 생긴다.

그래서 메시 v만 다리 메시가 화면에 쓰지 않는 칸(텍스처 위쪽 y 0~95, 몸통·엉덩이에 가려 보이지 않는다. 판정 렌더 4프레임 × 남녀로
16x16 칸마다 실측)으로 옮기고([V_SHIFT]), 하의마다 그 칸에 원래 조각을 옮겨 그린 뒤 타원 둘레에 짙은 외곽선을 그린다([paint_hip]).
riv 파일은 승인본(SHA 시험) 그대로 두고, 앱은 읽은 바이트에서 그 꼭짓점 v만 바꾼다(RivHipConnection, [vertex_v_offsets]로 만든 표).
"""

from __future__ import annotations

import struct

import numpy as np
from PIL import Image, ImageDraw

from outfit_pipeline.riv import NAME, PARENT_ID, VERTEX_V, _varuint
from outfit_pipeline.riv_schema import PROPERTY_FORMAT

IMAGE_NAME = "Cloth textured hip connection"
SOURCE = (88, 160, 168, 208)  # 원래 메시가 쓰던 텍스처 칸(x0, y0, x1, y1)
TARGET_TOP = 24  # 옮길 칸 위 끝(텍스처 y). 아래 끝은 TARGET_TOP + 48
HEIGHT = 768
V_SHIFT = (SOURCE[1] - TARGET_TOP) / HEIGHT  # 꼭짓점 v에서 뺄 값(160 → 24)
OUTLINE = (70, 48, 40)
OUTLINE_PX = 3
ARC = (100, 235)  # 외곽선을 그리는 타원 둘레 각(도, x 오른쪽 0, y 아래로 커짐): 위 → 등 쪽 → 엉덩이 아래 뒤
_TOC_FORMAT = ("u", "s", "f", "c")


def vertex_v_offsets(data: bytes) -> list[tuple[int, float]]:
    """[data](riv)에서 엉덩이 잇기 메시 꼭짓점마다 v 값(float32)이 있는 바이트 위치와 그 값."""
    if data[:4] != b"RIVE":
        raise ValueError("not a riv file")
    at = 4
    for _ in range(3):
        _, at = _varuint(data, at)
    toc: list[int] = []
    while True:
        key, at = _varuint(data, at)
        if key == 0:
            break
        toc.append(key)
    toc_format: dict[int, str] = {}
    bits, packed = 8, 0
    for key in toc:
        if bits == 8:
            packed = struct.unpack_from("<I", data, at)[0]
            at += 4
            bits = 0
        toc_format[key] = _TOC_FORMAT[(packed >> bits) & 3]
        bits += 2
    objects: list[tuple[int, dict[int, tuple[object, int]]]] = []
    while at < len(data):
        type_key, at = _varuint(data, at)
        props: dict[int, tuple[object, int]] = {}
        while True:
            key, at = _varuint(data, at)
            if key == 0:
                break
            kind = toc_format.get(key) or PROPERTY_FORMAT.get(key)
            start = at
            value: object
            if kind == "u":
                value, at = _varuint(data, at)
            elif kind == "s":
                length, at = _varuint(data, at)
                value = data[at : at + length]
                at += length
            elif kind == "f":
                value = struct.unpack_from("<f", data, at)[0]
                at += 4
            else:
                value = struct.unpack_from("<I", data, at)[0]
                at += 4
            props[key] = (value, start)
        objects.append((type_key, props))
    from outfit_pipeline.riv_schema import TYPE_NAME

    names = [TYPE_NAME.get(t, str(t)) for t, _ in objects]
    start = names.index("Artboard")
    end = next(
        (i for i in range(start + 1, len(names)) if names[i] in ("LinearAnimation", "StateMachine", "Artboard")),
        len(names),
    )
    board = objects[start:end]
    kinds = names[start:end]
    image = next(
        i
        for i, (_, props) in enumerate(board)
        if kinds[i] == "Image" and props.get(NAME, (b"", 0))[0] == IMAGE_NAME.encode()
    )
    mesh = next(
        i for i, (_, props) in enumerate(board) if kinds[i] == "Mesh" and props.get(PARENT_ID, (None, 0))[0] == image
    )
    return [
        (int(props[VERTEX_V][1]), float(props[VERTEX_V][0]))  # type: ignore[arg-type]
        for i, (_, props) in enumerate(board)
        if kinds[i] in ("MeshVertex", "ContourMeshVertex")
        and props.get(PARENT_ID, (None, 0))[0] == mesh
        and VERTEX_V in props
    ]


def retarget(data: bytes) -> bytes:
    """엉덩이 잇기 메시 꼭짓점 v를 [V_SHIFT]만큼 위로 옮긴 riv 사본."""
    out = bytearray(data)
    for offset, value in vertex_v_offsets(data):
        struct.pack_into("<f", out, offset, value - V_SHIFT)
    return bytes(out)


def paint_hip(layer: Image.Image) -> Image.Image:
    """달리기 다리 레이어의 옮긴 칸(y [TARGET_TOP]~+48)에 원래 조각(SOURCE)을 그대로 옮기고, 메시 타원(u 0.344~0.656, 칸 높이) 둘레
    [OUTLINE_PX]px 가운데 등 쪽([ARC])을 짙은 외곽선으로 칠한다. 원래 조각이 비어 있으면(하의가 없는 곳) 그대로 둔다."""
    array = np.array(layer.convert("RGBA"))
    x0, y0, x1, y1 = SOURCE
    patch = array[y0:y1, x0:x1].copy()
    if not (patch[..., 3] > 0).any():
        return layer
    height = y1 - y0
    array[TARGET_TOP : TARGET_TOP + height, x0:x1] = patch
    ring = Image.new("L", (x1 - x0, height), 0)
    draw = ImageDraw.Draw(ring)
    draw.ellipse((0, 0, x1 - x0 - 1, height - 1), fill=255)
    inner = Image.new("L", ring.size, 0)
    ImageDraw.Draw(inner).ellipse((OUTLINE_PX, OUTLINE_PX, x1 - x0 - 1 - OUTLINE_PX, height - 1 - OUTLINE_PX), fill=255)
    band = (np.array(ring) > 0) & ~(np.array(inner) > 0)
    # 외곽선은 뒤쪽(텍스처 왼쪽, 화면에서 등 쪽) 둘레만: 위에서 등을 돌아 엉덩이 아래 뒤까지([ARC] 도). 앞·아래 가운데는 허벅지로
    # 이어지는 곳이라 선을 그리면 엉덩이에 타원 무늬로 보였다
    yy, xx = np.mgrid[0:height, 0 : x1 - x0]
    angle = np.degrees(np.arctan2(yy - (height - 1) / 2, xx - (x1 - x0 - 1) / 2)) % 360
    band &= (angle >= ARC[0]) & (angle <= ARC[1])
    target = array[TARGET_TOP : TARGET_TOP + height, x0:x1]
    target[band, :3] = OUTLINE
    target[band, 3] = 255
    return Image.fromarray(array, "RGBA")


def retarget_rml(text: str) -> str:
    """Rive 원본(scene.rml)에서 엉덩이 잇기 메시 꼭짓점 v를 [V_SHIFT]만큼 옮긴다(판정 렌더가 앱과 같은 메시를 쓰게)."""
    import re

    start = text.index(f'<Image name="{IMAGE_NAME}"')
    end = text.index("</Mesh>", start)

    def shift(match: re.Match[str]) -> str:
        return f'v="{float(match.group(1)) - V_SHIFT!r}"'

    return text[:start] + re.sub(r'v="([0-9.eE+-]+)"', shift, text[start:end]) + text[end:]
