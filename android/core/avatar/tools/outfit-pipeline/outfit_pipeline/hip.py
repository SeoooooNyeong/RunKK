"""달리기 엉덩이 잇기 메시(판정 round1 C).

승인 달리기 riv(avatar_run_approved, avatar_male_run)의 Image 「Cloth textured hip connection」은 타원 메시(윤곽 꼭짓점 16개)로 하의
달리기 다리 텍스처(run_leg, 256x768)의 가운데 조각(u 0.344~0.656, v 약 0.209~0.271 = x 88~168, y 160~208)을 몸통과 다리 사이에
붙인다. 그 조각은 천 가운데라 외곽선이 없고, 같은 자리를 앞쪽 다리 메시도 허벅지에 써서 거기에 외곽선을 그리면 허벅지에도 선이 생긴다.

그래서 메시 v만 다리 메시가 화면에 쓰지 않는 칸(텍스처 위쪽 y 0~95, 몸통·엉덩이에 가려 보이지 않는다. 판정 렌더 4프레임 × 남녀로
16x16 칸마다 실측)으로 옮기고([V_SHIFT]), 하의마다 그 칸에 원래 조각을 옮겨 그린 뒤 타원 둘레에 짙은 외곽선을 그린다([paint_hip]).
riv 파일은 승인본(SHA 시험) 그대로 두고, 앱은 읽은 바이트에서 그 꼭짓점 v만 바꾼다(RivHipConnection, [vertex_v_offsets]로 만든 표).
"""

from __future__ import annotations

import math
import struct

import numpy as np
from PIL import Image

from outfit_pipeline.riv import NAME, PARENT_ID, VERTEX_V, _varuint
from outfit_pipeline.riv_schema import PROPERTY_FORMAT

IMAGE_NAME = "Cloth textured hip connection"
SOURCE = (88, 160, 168, 208)  # 원래 메시가 쓰던 텍스처 칸(x0, y0, x1, y1)
TARGET_TOP = 24  # 옮길 칸 위 끝(텍스처 y). 아래 끝은 TARGET_TOP + 48
HEIGHT = 768
V_SHIFT = (SOURCE[1] - TARGET_TOP) / HEIGHT  # 꼭짓점 v에서 뺄 값(160 → 24)
_TOC_FORMAT = ("u", "s", "f", "c")


def hip_box() -> tuple[int, int, int, int]:
    """엉덩이 잇기 메시가 쓰는 칸(x0, y0, x1, y1, 끝 제외)."""
    return (SOURCE[0], TARGET_TOP, SOURCE[2], TARGET_TOP + SOURCE[3] - SOURCE[1])


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
    """달리기 다리 레이어의 옮긴 칸(y [TARGET_TOP]~+48)에 원래 조각(SOURCE)을 그대로 옮긴다. 원래 조각이 비어 있으면(하의가 없는
    곳) 그대로 둔다. 타원 둘레에 외곽선은 그리지 않는다: 엉덩이 뒷면 실루엣은 다리 메시 윗부분(텍스처 y 약 100~280 왼쪽 가장자리)이
    만들고 이 메시는 그 안쪽에 겹쳐, 타원 외곽선이 옷 안쪽에 짙은 호로 떠 보였다(판정 round3). 뒷면 모양은 [round_hip_corner]가 다룬다."""
    array = np.array(layer.convert("RGBA"))
    x0, y0, x1, y1 = SOURCE
    patch = array[y0:y1, x0:x1].copy()
    if not (patch[..., 3] > 0).any():
        return layer
    array[TARGET_TOP : TARGET_TOP + (y1 - y0), x0:x1] = patch
    return Image.fromarray(array, "RGBA")


# 엉덩이 뒷면 모서리(판정 round3 C): 다리 메시는 맨몸 다리 실루엣 모양이라 화면 뒷면 실루엣은 텍스처의 맨몸 다리 왼쪽 가장자리를 따른다
# (실측: 텍스처 행마다 화면 뒷면 경계의 x가 맨몸 다리 왼쪽 끝과 같다). 맨몸·하의 다리 텍스처 윗부분은 y 약 170~255에서 x 1~3으로
# 곧게 내려오다 y 264에서 x 11로 10px 꺾여, 엉덩이가 곧은 뒷면과 각진 아랫모서리의 블록으로 보였다. 그 사이를 둥근 곡선으로 깎는다.
CORNER_ROWS = (170, 266)  # 깎는 행(위 끝은 곧은 뒷면 가운데, 아래 끝은 허벅지가 x 11에서 시작하는 행)
CORNER_X = (2, 12)  # 곡선이 지나는 x(위 끝 → 아래 끝, 아래 끝은 허벅지 가장자리)
CORNER_OUTLINE = (70, 45, 40)  # 맨몸 다리 외곽선 색
CORNER_OUTLINE_PX = 5


def corner_edge(y: int) -> float:
    """[CORNER_ROWS] 안 행 [y]의 새 뒷면 x: 위에서는 곧은 뒷면에 붙고 아래로 갈수록 빨리 안으로 들어가는 둥근 곡선(사분 타원)."""
    top, bottom = CORNER_ROWS
    t = (y - top) / (bottom - top)
    return CORNER_X[0] + (CORNER_X[1] - CORNER_X[0]) * (1 - math.sqrt(max(0.0, 1 - t * t)))


def round_hip_corner(layer: Image.Image) -> Image.Image:
    """달리기 다리 레이어(맨몸·하의)의 엉덩이 뒷면 모서리를 둥글게 깎고, 깎은 행의 새 가장자리 [CORNER_OUTLINE_PX]px을 짙은 외곽선으로
    칠한다(그 행에 불투명 픽셀이 있을 때만). 깎은 곳은 투명해 그 뒤 배경이 보인다."""
    array = np.array(layer.convert("RGBA"))
    top, bottom = CORNER_ROWS
    for y in range(top, bottom):
        edge = corner_edge(y)
        solid = np.nonzero(array[y, :, 3] >= 128)[0]
        if len(solid) == 0 or solid.min() > 64:
            continue
        cut = math.floor(edge)
        if solid.min() >= cut + CORNER_OUTLINE_PX:
            continue  # 이미 곡선보다 안쪽(외곽선이 따로 있다)
        array[y, :cut] = 0
        # 경계 픽셀은 소수 부분만큼 반투명(곡선이 계단으로 보이지 않게). 다시 돌려도 같도록 원래 알파가 아니라 255에서 정한다
        frac = edge - cut
        if array[y, cut, 3] > 0:
            array[y, cut, 3] = min(int(array[y, cut, 3]), round(255 * (1 - frac)))
        band = slice(cut, cut + CORNER_OUTLINE_PX)
        opaque = array[y, band, 3] > 0
        array[y, band][opaque, :3] = CORNER_OUTLINE
    return Image.fromarray(array, "RGBA")


def retarget_rml(text: str) -> str:
    """Rive 원본(scene.rml)에서 엉덩이 잇기 메시 꼭짓점 v를 [V_SHIFT]만큼 옮긴다(판정 렌더가 앱과 같은 메시를 쓰게)."""
    import re

    start = text.index(f'<Image name="{IMAGE_NAME}"')
    end = text.index("</Mesh>", start)

    def shift(match: re.Match[str]) -> str:
        return f'v="{float(match.group(1)) - V_SHIFT!r}"'

    return text[:start] + re.sub(r'v="([0-9.eE+-]+)"', shift, text[start:end]) + text[end:]


LEG_EDGE_PX = 7  # 맨몸 다리 외곽선으로 보는 가장자리 폭
LEG_DARK = 110


def outline_leg_edge(layer: Image.Image, body: Image.Image) -> Image.Image:
    """하의 달리기 다리(판정 round3: wide·jogger 외곽선 없음, leggings 무릎 비침, cargo 주머니 가장자리 톱니): 다리 메시는 맨몸 다리 실루엣 모양이라 하의가 그보다
    넓은 곳은 화면에 나오지 않고, 실루엣 가장자리에는 하의 천이 나온다(하의 자체 외곽선은 메시 밖이라 안 보였다). 하의가 있는 행(밑단 위)에서
    맨몸 다리 외곽선 픽셀(실루엣 가장자리 [LEG_EDGE_PX]px 안의 짙은 픽셀) 위의 하의 픽셀을 그 외곽선 색으로 바꿔 하의 외곽선으로 쓴다."""
    array = np.array(layer.convert("RGBA"))
    skin = np.array(body.convert("RGBA"))
    garment = array[..., 3] >= 128
    rows = np.nonzero(garment.sum(axis=1) >= 40)[0]
    if len(rows) == 0:
        return layer
    solid = skin[..., 3] >= 128
    from PIL import ImageFilter

    outside = Image.fromarray(((~solid) * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(2 * LEG_EDGE_PX + 1))
    edge = solid & (np.array(outside) > 0) & (skin[..., :3].astype(int).mean(axis=-1) < LEG_DARK)
    edge[: rows.min()] = False
    edge[rows.max() - 6 :] = False
    target = edge & garment
    array[target, :3] = skin[target, :3]
    return Image.fromarray(array, "RGBA")


LEG_TOP_ROWS = (90, 280)  # 다리 윗부분(엉덩이) 행


def cover_leg_top(layer: Image.Image, body: Image.Image) -> Image.Image:
    """다리 윗부분(엉덩이, [LEG_TOP_ROWS])에서 하의가 맨몸보다 좁아 맨몸(회색 속옷 칸·피부)이 하의 뒤로 비치는 곳을 가까운 하의 색으로
    덮는다(판정 round4 leggings: 엉덩이 뒤에 회색 반투명 덧조각). 맨몸 실루엣 가장자리 [LEG_EDGE_PX]px(외곽선 자리)는 덮지 않아
    [outline_leg_edge]가 외곽선을 입힌다. 하의가 그 행에 없으면(맨다리) 그대로 둔다."""
    array = np.array(layer.convert("RGBA")).astype(np.float64)
    skin = np.array(body.convert("RGBA"))
    garment = array[..., 3] >= 128
    solid = skin[..., 3] >= 128
    from PIL import ImageFilter

    outside = Image.fromarray(((~solid) * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(2 * LEG_EDGE_PX + 1))
    inner = solid & ~(np.array(outside) > 0)
    y0, y1 = LEG_TOP_ROWS
    todo = np.zeros(garment.shape, bool)
    rows = [y for y in range(y0, y1) if garment[y].sum() >= 40]
    for y in rows:
        todo[y] = inner[y] & ~garment[y]
    known = garment.copy()
    while todo.any():
        grown = []
        for y, x in zip(*np.nonzero(todo), strict=True):
            around = [
                array[ny, nx, :3]
                for ny, nx in ((y - 1, x), (y + 1, x), (y, x - 1), (y, x + 1))
                if 0 <= ny < known.shape[0] and 0 <= nx < known.shape[1] and known[ny, nx]
            ]
            if around:
                grown.append((y, x, np.mean(around, axis=0)))
        if not grown:
            break
        for y, x, color in grown:
            array[y, x, :3] = color
            array[y, x, 3] = 255
            known[y, x] = True
            todo[y, x] = False
    return Image.fromarray(array.round().clip(0, 255).astype(np.uint8), "RGBA")


def finish_run_leg(layer: Image.Image, body: Image.Image | None) -> Image.Image:
    """달리기 다리 하의 레이어 마무리(판정 round1~3 C): 엉덩이 잇기 칸(등 아래 모서리 깎음), 엉덩이 뒷면 둥근 모서리, 맨몸 다리
    외곽선."""
    from outfit_pipeline.body import close_seams

    layer = round_hip_corner(carve_hip(paint_hip(layer)))
    if body is not None:
        layer = outline_leg_edge(cover_leg_top(layer, body), body)
    # 깎은 뒷면 끝 픽셀이 세로 틈(검사 14번)이 되지 않게 닫는다
    return close_seams(layer)[0]


CARVE = 0.42  # 엉덩이 잇기 타원의 등 아래 사분면에서 (x/rx)²+(y/ry)²가 이보다 크면 깎는다(화면 블록의 등 아래 모서리)
CARVE_TOP = -0.2  # 이 높이(-1 위 끝 ~ 1 아래 끝)부터 등 쪽 깎기가 시작된다
CARVE_FULL = 0.5  # 이 높이 아래에서는 [CARVE_BACK]까지 깎는다
CARVE_BACK = -0.35  # 타원 가로 위치(-1 등 끝 ~ 1 앞 끝)가 이보다 등 쪽이면 깎는다(화면 블록의 곧은 뒷면·뒤 허리 모서리)


def carve_hip(layer: Image.Image) -> Image.Image:
    """엉덩이 잇기 칸(TARGET)의 등 쪽([CARVE_BACK]보다 등 쪽 전체와 등 아래 사분면 바깥)을 투명하게 깎는다(판정 round3·4 C). 메시가
    타원이어도 뼈 변형으로 화면에서는 각진 블록이라, 그 등 쪽이 다리 윗부분보다 튀어나와 곧은 뒷면·각진 모서리·밝은 슬래브로 보였다.
    깎은 자리에는 그 뒤 다리 메시 윗부분(맨몸 다리 외곽선을 입은 하의 엉덩이)이 보여 뒷면 실루엣이 다리로 이어진다. 깎은 경계와 둘레에는
    외곽선을 그리지 않는다(옷 안쪽 호·짙은 쐐기로 떠 보였다)."""
    array = np.array(layer.convert("RGBA"))
    x0, y0, x1, y1 = SOURCE
    height = y1 - y0
    region = array[TARGET_TOP : TARGET_TOP + height, x0:x1]
    if not (region[..., 3] > 0).any():
        return layer
    yy, xx = np.mgrid[0:height, 0 : x1 - x0]
    nx = (xx - (x1 - x0 - 1) / 2) / ((x1 - x0) / 2)
    ny = (yy - (height - 1) / 2) / (height / 2)
    # 깎는 경계는 위로 갈수록 등 끝(-1)으로 부드럽게 물러나는 곡선이다: 맨 위까지 깎으면 상의 밑단과 다리 사이에 배경 삼각형이
    # 뚫렸고, 곧은 세로선으로 깎다 위에서 멈추면 「ㄱ」자 홈이 생겼다(판정 round4)
    t = np.clip((ny - CARVE_TOP) / (CARVE_FULL - CARVE_TOP), 0, 1)
    boundary = -1 + (CARVE_BACK + 1) * (1 - (1 - t) ** 2)
    region[(nx < boundary) | ((nx < 0) & (ny > 0) & (nx * nx + ny * ny > CARVE))] = 0
    return Image.fromarray(array, "RGBA")
