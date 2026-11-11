"""달리기(옆모습) 옷 레이어 마무리(판정 round3 달리기 묶음).

- [drop_loose_pieces]: 옷 본체와 떨어진 작은 덩어리를 지운다. singlet 달리기 팔 레이어에 맨몸 팔 외곽선 조각이
  세로 선으로 딸려 와 어깨 뒤에 짙은 곡선으로 떠 보였고, hoodie 달리기 몸통 위 끝에도 떨어진 획이 목 곡선으로 보였다.
- [outline_back]: hoodie 달리기 몸통 등 쪽 가장자리 [EDGE_PX]px 가운데 아직 짙지 않은 곳을 짙은 갈색으로 칠한다. 등판
  가장자리가 옅어 외곽선이 없어 보였다(달리기 몸통 메시는 옷 가장자리까지 보인다: 픽셀 좌표 부호 렌더 실측).
- [clean_cord]: jogger 달리기 다리의 허리끈 끝(텍스처 왼쪽 위)이 엉덩이 뒤에 짙은 점으로 떠 보여, 그 칸의 끈 픽셀을 이웃 천 색으로 바꾼다.
"""

from __future__ import annotations

import numpy as np
from PIL import Image, ImageFilter

SOLID = 128
PIECE = 1000
BACK_KEYS = ("top_winter_hoodie",)
EDGE_PX = 4
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


def finish_run_layer(layer: Image.Image, key: str, part: str, slot: str, leg: Image.Image | None = None) -> Image.Image:
    """달리기 상의·하의 레이어 마무리(판정 round3): 상의 몸통·팔은 떨어진 조각을 지우고 몸통은 등 외곽선, 하의 다리는 허리끈 끝을 정리한
    뒤 맨몸 다리 외곽선을 다시 입힌다([leg] = 맨몸 달리기 다리, outfit_pipeline.hip.finish_run_leg)."""
    if part == "TOP" and slot in ("run_body", "run_arm"):
        layer, _ = drop_loose_pieces(layer)
        if slot == "run_body":
            layer, _ = outline_back(layer, key)
    elif part == "BOTTOM" and slot == "run_leg" and key in CORD_BOX:
        from outfit_pipeline.hip import finish_run_leg

        layer = finish_run_leg(clean_cord(layer, key), leg)
    return layer
