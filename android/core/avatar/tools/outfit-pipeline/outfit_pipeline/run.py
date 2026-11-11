"""달리기(옆모습) 옷 레이어 마무리(판정 round3 달리기 묶음).

- [drop_loose_pieces]: 옷 본체와 떨어진 작은 덩어리를 지운다. singlet 달리기 팔 레이어에 맨몸 팔 외곽선 조각이
  세로 선으로 딸려 와 어깨 뒤에 짙은 곡선으로 떠 보였고, hoodie 달리기 몸통 위 끝에도 떨어진 획이 목 곡선으로 보였다.
- [outline_back]: hoodie 달리기 몸통 등 쪽 가장자리 [EDGE_PX]px 가운데 아직 짙지 않은 곳을 짙은 갈색으로 칠한다. 등판
  가장자리가 옅어 외곽선이 없어 보였다(달리기 몸통 메시는 옷 가장자리까지 보인다: 픽셀 좌표 부호 렌더 실측).
- [clean_cord]: jogger 달리기 다리의 허리끈 끝(텍스처 왼쪽 위)이 엉덩이 뒤에 짙은 점으로 떠 보여, 그 칸의 끈 픽셀을 이웃 천 색으로 바꾼다.
"""

from __future__ import annotations

from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

SOLID = 128
PIECE = 1000
BACK_KEYS = ("top_winter_hoodie",)
EDGE_PX = 4
SHEEN_PX = 14  # 소매 가장자리 밝은 줄을 누르는 띠 폭
EDGE_INK = 75  # 이보다 어두운 가장자리는 이미 외곽선
OUTLINE = (66, 46, 40)
CORD_BOX = {"bottom_jogger_pants": (0, 95, 30, 155)}  # 허리끈 끝(갈색·금속) 칸(x0, y0, x1, y1)


def _morph(mask: np.ndarray, *filters: ImageFilter.Filter) -> np.ndarray:
    image = Image.fromarray((mask * 255).astype(np.uint8))
    for f in filters:
        image = image.filter(f)
    return np.array(image) > 0


def drop_loose_pieces(layer: Image.Image) -> tuple[Image.Image, int]:
    """옷 본체와 떨어진 작은 덩어리(8방향, [PIECE]px 미만)를 지운다. 달리기 상의 레이어는 본체 하나(5만 픽셀 이상)이고, 그보다 작은
    덩어리는 딸려 온 맨몸 외곽선 조각·획이다(singlet 달리기 팔은 소매가 없어 조각뿐이다). (레이어, 지운 픽셀 수)."""
    from outfit_pipeline.build import _components

    array = np.array(layer.convert("RGBA"))
    width = array.shape[1]
    removed = 0
    for flat in _components(array[..., 3] > 0):
        if len(flat) < PIECE:
            ys, xs = np.divmod(np.array(flat), width)
            array[ys, xs] = 0
            removed += len(flat)
    return Image.fromarray(array, "RGBA"), removed


def outline_back(layer: Image.Image, key: str) -> tuple[Image.Image, int]:
    """[BACK_KEYS] 상의의 달리기 몸통: 행마다 왼쪽(등) 첫 불투명 픽셀부터 [EDGE_PX]px 가운데 밝기 [EDGE_INK] 이상을 짙은 갈색으로 칠한다
    (hoodie 등판 가장자리가 옅어 외곽선이 없어 보였다. 다른 상의는 등 외곽선이 이미 짙다)."""
    if key not in BACK_KEYS:
        return layer, 0
    array = np.array(layer.convert("RGBA")).astype(np.float64)
    solid = array[..., 3] >= SOLID
    changed = 0
    for y in range(array.shape[0]):
        xs = np.nonzero(solid[y])[0]
        if len(xs) == 0:
            continue
        band = slice(xs.min(), xs.min() + EDGE_PX)
        light = solid[y, band] & (array[y, band, :3].mean(axis=-1) >= EDGE_INK)
        row = array[y, band]
        row[light, :3] = row[light, :3] * 0.2 + np.array(OUTLINE) * 0.8
        changed += int(light.sum())
    return Image.fromarray(array.round().clip(0, 255).astype(np.uint8), "RGBA"), changed


def clean_cord(layer: Image.Image, key: str) -> Image.Image:
    """[CORD_BOX]의 칸에서 천(무채색 회색)이 아닌 픽셀(허리끈·끈 끝 금속)을 이웃 천 색으로 바꾼다. 칸이 없는 하의는 그대로."""
    box = CORD_BOX.get(key)
    if box is None:
        return layer
    array = np.array(layer.convert("RGBA")).astype(np.float64)
    x0, y0, x1, y1 = box
    rgb = array[..., :3]
    spread = rgb.max(axis=-1) - rgb.min(axis=-1)
    lum = rgb.mean(axis=-1)
    fabric = (array[..., 3] >= SOLID) & (spread < 14) & (lum > 40) & (lum < 130)
    ink = lum < 65  # 외곽선(맨몸 다리 외곽선 색)은 끈이 아니다
    target = np.zeros(fabric.shape, bool)
    target[y0:y1, x0:x1] = (array[y0:y1, x0:x1, 3] >= SOLID) & ~fabric[y0:y1, x0:x1] & ~ink[y0:y1, x0:x1]
    known = fabric | ~(array[..., 3] >= SOLID)
    known &= ~target
    todo = target.copy()
    while todo.any():
        grown = []
        for y, x in zip(*np.nonzero(todo), strict=True):
            around = [
                array[ny, nx, :3]
                for ny, nx in ((y - 1, x), (y + 1, x), (y, x - 1), (y, x + 1))
                if 0 <= ny < known.shape[0] and 0 <= nx < known.shape[1] and known[ny, nx] and fabric[ny, nx]
            ]
            if around:
                grown.append((y, x, np.mean(around, axis=0)))
        if not grown:
            break
        for y, x, color in grown:
            array[y, x, :3] = color
            known[y, x] = True
            fabric[y, x] = True
            todo[y, x] = False
    return Image.fromarray(array.round().clip(0, 255).astype(np.uint8), "RGBA")


def outline_all_edges(layer: Image.Image) -> tuple[Image.Image, int]:
    """배경(텍스처 밖 포함)과 [EDGE_PX]px 안의 옷 픽셀 가운데 밝기 [EDGE_INK] 이상을 짙은 갈색으로 칠한다(hoodie 달리기 소매: 화면에서
    등 쪽 실루엣은 소매가 만들고, 그 가장자리가 밝은 줄(소매 얼룩)이라 외곽선이 없어 보였다)."""
    array = np.array(layer.convert("RGBA")).astype(np.float64)
    solid = array[..., 3] >= SOLID
    background = np.pad(~solid, EDGE_PX, constant_values=True)
    near = _morph(background, ImageFilter.MaxFilter(2 * EDGE_PX + 1))[EDGE_PX:-EDGE_PX, EDGE_PX:-EDGE_PX]
    edge = solid & near & (array[..., :3].mean(axis=-1) >= EDGE_INK)
    array[edge, :3] = array[edge, :3] * 0.2 + np.array(OUTLINE) * 0.8
    # 외곽선 안쪽 띠(가장자리에서 [EDGE_PX]~[SHEEN_PX]px)의 밝은 줄(소매 얼룩)을 천 가운데값 쪽으로 누른다
    wide = _morph(background, ImageFilter.MaxFilter(2 * SHEEN_PX + 1))[EDGE_PX:-EDGE_PX, EDGE_PX:-EDGE_PX]
    cloth = solid & ~near
    median = np.median(array[cloth][:, :3], axis=0) if cloth.any() else None
    sheen = cloth & wide & (array[..., :3].mean(axis=-1) > (median.mean() + 12 if median is not None else 999))
    if median is not None:
        array[sheen, :3] = array[sheen, :3] * 0.4 + median * 0.6
    return Image.fromarray(array.round().clip(0, 255).astype(np.uint8), "RGBA"), int(edge.sum())


TOP_STROKE_ROWS = 45  # 달리기 몸통 위 끝(후드 꼭대기)에서 가는 획을 지우는 행


def trim_top_strokes(layer: Image.Image) -> tuple[Image.Image, int]:
    """달리기 몸통 위 [TOP_STROKE_ROWS]행에서 5x5로 열면 사라지는 가는 부분(후드 꼭대기 들쭉날쭉한 끝, 「ㅅ」자 획)을 지운다
    (판정 round4 hoodie run 목)."""
    array = np.array(layer.convert("RGBA"))
    solid = array[..., 3] >= SOLID
    thick = _morph(solid, ImageFilter.MinFilter(5), ImageFilter.MaxFilter(5), ImageFilter.MaxFilter(3))
    thin = (array[..., 3] > 0) & ~thick
    thin[TOP_STROKE_ROWS:] = False
    array[thin] = 0
    return Image.fromarray(array, "RGBA"), int(thin.sum())


FIXES = Path(__file__).resolve().parent.parent / "templates" / "fixes"


def apply_fix_mask(layer: Image.Image, key: str, file_stem: str) -> Image.Image:
    """손으로 정한 지울 칸(`templates/fixes/<key>__<file_stem>.png`, 흰색 = 지움)을 투명하게 한다. 자동 판별로 외곽선과 가르기 어려운
    생성 잔상만 둔다(판정 round4: high_top 달리기 신발 끈 옆 검은 계단 그림자). 파일이 없으면 그대로."""
    path = FIXES / f"{key}__{file_stem}.png"
    if not path.is_file():
        return layer
    mask = np.array(Image.open(path).convert("L")) > 0
    array = np.array(layer.convert("RGBA"))
    array[mask] = 0
    return Image.fromarray(array, "RGBA")


def finish_run_layer(layer: Image.Image, key: str, part: str, slot: str, leg: Image.Image | None = None) -> Image.Image:
    """달리기 상의·하의 레이어 마무리(판정 round3): 상의 몸통·팔은 떨어진 조각을 지우고 몸통은 등 외곽선, 하의 다리는 허리끈 끝을 정리한
    뒤 맨몸 다리 외곽선을 다시 입힌다([leg] = 맨몸 달리기 다리, outfit_pipeline.hip.finish_run_leg)."""
    if part == "TOP" and slot in ("run_body", "run_arm"):
        layer, _ = drop_loose_pieces(layer)
        if slot == "run_body":
            layer, _ = outline_back(layer, key)
            if key in BACK_KEYS:
                layer, _ = drop_loose_pieces(trim_top_strokes(layer)[0])
        elif key in BACK_KEYS:
            layer, _ = outline_all_edges(layer)
    elif part == "SHOES" and slot == "run_shoe":
        from outfit_pipeline.body import drop_strays
        from outfit_pipeline.shoes import drop_pieces

        layer = drop_pieces(drop_strays(apply_fix_mask(layer, key, "female_run_shoe"), thin=True)[0])[0]
    elif part == "BOTTOM" and slot == "run_leg" and key in CORD_BOX:
        from outfit_pipeline.hip import finish_run_leg

        layer = finish_run_leg(clean_cord(layer, key), leg)
    return layer
