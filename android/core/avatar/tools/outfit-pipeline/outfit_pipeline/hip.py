"""달리기 엉덩이 잇기 메시(판정 round1~5 C).

승인 달리기 riv(avatar_run_approved, avatar_male_run)의 Image 「Cloth textured hip connection」은 타원 메시(윤곽 꼭짓점 16개)로 하의
달리기 다리 텍스처(run_leg, 256x768)의 가운데 조각(u 0.344~0.656, v 약 0.209~0.271 = x 88~168, y 160~208)을 몸통과 다리 사이에
붙인다. 그 조각은 천 가운데라 외곽선이 없고, 같은 자리를 앞쪽 다리 메시도 허벅지에 써서 거기를 고치면 허벅지도 바뀐다.

그래서 메시 v만 다리 메시가 화면에 쓰지 않는 칸(텍스처 위쪽 x 88~167, y 0~95: 판정 렌더 4프레임 × 남녀로 16x16 칸마다 실측)으로 옮기고,
남녀 리그가 이 메시를 다르게 휘므로 칸을 나눈다(여자 y 0~47, 남자 y 48~95, [TARGET_TOPS]). 하의마다 두 칸에 원래 조각을 옮기고([paint_hip])
등 쪽을 깎는다([carve_hip]). riv 파일은 승인본(SHA 시험) 그대로 두고, 앱은 읽은 바이트에서 그 꼭짓점 v만 바꾼다(RivHipConnection,
[vertex_v_offsets]로 만든 표). 판정 렌더는 Rive 원본의 같은 꼭짓점을 옮긴다([retarget_rml]).
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
# 옮길 칸 위 끝(텍스처 y, 아래 끝은 + 48). 남녀 리그가 이 메시를 다르게 휘어 화면에서 맡는 몫이 달라(여자는 다리 윗부분 안쪽에 겹치고
# 남자는 뒤 실루엣을 만든다) 남녀 칸을 나눈다(판정 round5)
TARGET_TOPS = {"female": 0, "male": 48}
RIV_GENDER = {"avatar_run_approved.riv": "female", "avatar_male_run.riv": "male"}
HEIGHT = 768
V_SHIFTS = {g: (SOURCE[1] - top) / HEIGHT for g, top in TARGET_TOPS.items()}  # 꼭짓점 v에서 뺄 값
_TOC_FORMAT = ("u", "s", "f", "c")


def hip_box() -> tuple[int, int, int, int]:
    """엉덩이 잇기 메시가 쓰는 칸(남녀 둘을 덮는 상자, x0, y0, x1, y1, 끝 제외)."""
    height = SOURCE[3] - SOURCE[1]
    return (SOURCE[0], min(TARGET_TOPS.values()), SOURCE[2], max(TARGET_TOPS.values()) + height)


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


def retarget(data: bytes, gender: str) -> bytes:
    """엉덩이 잇기 메시 꼭짓점 v를 그 성별 칸으로([V_SHIFTS]) 옮긴 riv 사본."""
    out = bytearray(data)
    for offset, value in vertex_v_offsets(data):
        struct.pack_into("<f", out, offset, value - V_SHIFTS[gender])
    return bytes(out)


def paint_hip(layer: Image.Image) -> Image.Image:
    """달리기 다리 레이어의 남녀 칸([TARGET_TOPS], 각 48행)에 원래 조각(SOURCE)을 그대로 옮긴다. 원래 조각이 비어 있으면(하의가 없는
    곳) 그대로 둔다. 타원 둘레에 외곽선은 그리지 않는다: 엉덩이 뒷면 실루엣은 다리 메시 윗부분(텍스처 y 약 100~280 왼쪽 가장자리)이
    만들고 이 메시는 그 안쪽에 겹쳐, 타원 외곽선이 옷 안쪽에 짙은 호로 떠 보였다(판정 round3). 뒷면 모양은 [round_hip_corner]가 다룬다."""
    array = np.array(layer.convert("RGBA"))
    x0, y0, x1, y1 = SOURCE
    patch = array[y0:y1, x0:x1].copy()
    if not (patch[..., 3] > 0).any():
        return layer
    for top in TARGET_TOPS.values():
        array[top : top + (y1 - y0), x0:x1] = patch
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


def retarget_rml(text: str, gender: str) -> str:
    """Rive 원본(scene.rml)에서 엉덩이 잇기 메시 꼭짓점 v를 그 성별 칸으로([V_SHIFTS]) 옮긴다(판정 렌더가 앱과 같은 메시를 쓰게)."""
    import re

    start = text.index(f'<Image name="{IMAGE_NAME}"')
    end = text.index("</Mesh>", start)

    def shift(match: re.Match[str]) -> str:
        return f'v="{float(match.group(1)) - V_SHIFTS[gender]!r}"'

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


# 남자 칸 등 뒤 모양(각도는 타원 중심에서 잰 도: 90 = 아래, 180 = 등, 270 = 위). 등 아래 사분면은 90도에서 반지름 비율 [MALE_CARVE]까지
# 깎고 180도로 갈수록 메시 끝(1.0)까지 부드럽게 넓히며, 등 가운데부터 위는 메시 끝까지 남긴다. 판정 round6~7: 사분면을 곧게 깎고
# 외곽선을 반지름 띠로만 칠해 180도에서 바깥 끝이 0.9 → 1.02로 뛰어 단차가 났고, 띠가 250도에서 멈춰 위 모서리 천이 외곽선 밖에 남았다.
# 90도 근처를 이보다 더 깎으면 여자 리그 앞 다리 메시가 쌍선형으로 섞어 읽는 칸 아래 끝 몇 텍셀이 바뀌어 여자 달리기가 달라졌다.
MALE_CARVE = 0.9
# 남자 칸 등 외곽선: 남긴 모양 가장자리에서 안쪽 [MALE_OUTLINE_PX] 텍셀을 이 둘레 각 사이에 칠한다. 양 끝 [MALE_ARC_FADE]도는 두께를
# 0까지 줄인다(아래 끝은 다리 메시 안으로 들어가는 곳이라 끝이 뚝 끊기면 옷 안쪽 호로 보였다, 위 끝은 상의 밑단 아래).
MALE_ARC = (130, 290)
MALE_ARC_FADE = 25
MALE_OUTLINE_PX = 3


def _ellipse_axes(height: int, width: int) -> tuple[np.ndarray, np.ndarray]:
    yy, xx = np.mgrid[0:height, 0:width]
    return (xx - (width - 1) / 2) / (width / 2), (yy - (height - 1) / 2) / (height / 2)


def _male_back(height: int, width: int, nx: np.ndarray, ny: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    """남자 칸에서 깎을 곳과 등 외곽선 띠([MALE_CARVE], [MALE_ARC] 설명)."""
    radius = np.sqrt(nx * nx + ny * ny)
    angle = np.degrees(np.arctan2(ny, nx)) % 360
    s = np.clip((angle - 90) / 90, 0, 1)
    limit = np.where(angle <= 180, MALE_CARVE + (1 - MALE_CARVE) * s * s * (3 - 2 * s), 1.0)
    cut = (nx < 0) & (radius > limit)
    # 각도 방향으로 텍셀 한 칸이 반지름 비율로 얼마인지(가로 반지름 width/2, 세로 height/2)
    ray = np.hypot(width / 2 * np.cos(np.radians(angle)), height / 2 * np.sin(np.radians(angle)))
    lo, hi = MALE_ARC
    fade = np.clip((angle - lo) / MALE_ARC_FADE, 0, 1) * np.clip((hi - angle) / MALE_ARC_FADE, 0, 1)
    band = ~cut & (fade > 0) & (radius > limit - MALE_OUTLINE_PX / ray * fade)
    return cut, band


def carve_hip(layer: Image.Image) -> Image.Image:
    """엉덩이 잇기 칸(남녀 각각)의 등 쪽(높이 -0.2부터 0.5까지 등 끝에서 가로 -0.35로 부드럽게 들어오는 곡선보다 등 쪽)과 등 아래 사분면
    바깥을 투명하게 깎는다(판정 round3~5 C). 메시가 타원이어도 뼈 변형으로 화면에서는 각진 블록이라 텍스처 알파로 깎는다. 둘레 외곽선은
    그리지 않는다: 픽셀 좌표 부호 렌더로 보니 남녀 모두 뒤 실루엣의 대부분은 다리 메시 윗부분(텍스처 왼쪽 위 x 7~57, y 54~246)이 만들고
    이 메시의 등 쪽은 그 안쪽에 겹쳐, 둘레 외곽선이 옷 안쪽 호로 떠 보였다(round3). 남녀 칸은 나뉘어 있어(TARGET_TOPS) 성별마다 다르게
    깎는다: 남자 리그에서는 이 메시가 등 뒤 실루엣을 만들어 등 둘레에 외곽선을 입힌다([_male_back])."""
    array = np.array(layer.convert("RGBA"))
    x0, y0, x1, y1 = SOURCE
    height, width = y1 - y0, x1 - x0
    nx, ny = _ellipse_axes(height, width)
    radius = nx * nx + ny * ny
    t = np.clip((ny - CARVE_TOP) / (CARVE_FULL - CARVE_TOP), 0, 1)
    boundary = -1 + (CARVE_BACK + 1) * (1 - (1 - t) ** 2)
    quadrant = (nx < 0) & (ny > 0) & (radius > CARVE)
    # 남자 리그는 이 메시 등 아래 사분면 뒤에 다리가 덜 와서, 여자와 같이 깎으면 배경 삼각형이 비쳤다: 등 아래만 조금 깎고 외곽선을 입힌다
    male_cut, male_band = _male_back(height, width, nx, ny)
    cuts = {"female": (nx < boundary) | quadrant, "male": male_cut}
    for gender, top in TARGET_TOPS.items():
        region = array[top : top + height, x0:x1]
        if not (region[..., 3] > 0).any():
            continue
        region[cuts[gender]] = 0
        if gender == "male":
            band = male_band & (region[..., 3] > 0)
            region[band, :3] = CORNER_OUTLINE
            region[band, 3] = 255
    return Image.fromarray(array, "RGBA")
