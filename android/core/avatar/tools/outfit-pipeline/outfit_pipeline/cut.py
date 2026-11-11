"""상의 자르기([임시:D-성-156])의 파이썬 판. 앱 `TopBodyCut`(core:avatar)과 같은 규칙이다(검수 렌더가 앱과 같은 그림을 그리게).

자르기 칸 안의 상의 밖(옷으로 둘러싸인 빈 곳은 옷으로 친다) 맨몸 픽셀이 상의와 배경 사이에 낀 얇은 띠(가장 가까운 상의·배경까지 거리 합
≤ RIM_PX + 2, 두 방향의 코사인 < OPPOSITE)이면 지운다.
"""

from __future__ import annotations

import numpy as np
from PIL import Image

RIM_PX = 30
OPPOSITE = -0.5
CRUMB_PX = 80
ARM_COVERED = 0.6  # 팔 띠는 상의가 그 띠 맨몸을 이만큼 넘게 덮을 때(긴소매)만 자른다
TORSO = 255
FULL_CUT = 240
_FAR = 10**6


def _nearest(source: np.ndarray, steps: int) -> tuple[np.ndarray, np.ndarray]:
    """픽셀마다 [source]의 가장 가까운 픽셀 좌표(8방향 전파 [steps]번, 근사). 없으면 (-1, -1)."""
    height, width = source.shape
    yy, xx = np.mgrid[0:height, 0:width]
    oy = np.where(source, yy, -1)
    ox = np.where(source, xx, -1)
    for _ in range(steps):
        best = np.where(oy >= 0, (oy - yy) ** 2 + (ox - xx) ** 2, _FAR)
        for dy in (-1, 0, 1):
            for dx in (-1, 0, 1):
                if dx == 0 and dy == 0:
                    continue
                sy = np.full_like(oy, -1)
                sx = np.full_like(ox, -1)
                ys = slice(max(dy, 0), height + min(dy, 0))
                yd = slice(max(-dy, 0), height + min(-dy, 0))
                xs = slice(max(dx, 0), width + min(dx, 0))
                xd = slice(max(-dx, 0), width + min(-dx, 0))
                sy[yd, xd] = oy[ys, xs]
                sx[yd, xd] = ox[ys, xs]
                cand = np.where(sy >= 0, (sy - yy) ** 2 + (sx - xx) ** 2, _FAR)
                better = cand < best
                oy = np.where(better, sy, oy)
                ox = np.where(better, sx, ox)
                best = np.minimum(best, cand)
    return oy, ox


def apply(body: Image.Image, top: Image.Image, area: Image.Image) -> Image.Image:
    from outfit_pipeline.build import _fill_holes

    b = np.array(body.convert("RGBA"))
    top_alpha = np.array(top.convert("RGBA"))[..., 3] > 0
    inside = np.array(_fill_holes(Image.fromarray((top_alpha * 255).astype(np.uint8)))) > 0
    labels = np.array(area.convert("RGBA"))[..., 3]
    skin = b[..., 3] > 0
    area_on = labels >= 128
    for value in np.unique(labels):
        if value < 128 or value in (TORSO, FULL_CUT):
            continue
        band = (labels == value) & skin
        if band.sum() and (band & top_alpha).sum() / band.sum() <= ARM_COVERED:
            area_on &= labels != value
    background = ~skin & ~top_alpha
    steps = RIM_PX + 3
    ty, tx = _nearest(top_alpha, steps)
    by, bx = _nearest(background, steps)
    yy, xx = np.mgrid[0 : b.shape[0], 0 : b.shape[1]]
    candidate = skin & area_on & ~inside & (ty >= 0)
    vty, vtx = (ty - yy).astype(float), (tx - xx).astype(float)
    vby = np.where(by >= 0, by - yy, 0).astype(float)
    vbx = np.where(by >= 0, bx - xx, 10**6).astype(float)
    height, width = skin.shape
    # 배경: 가장 가까운 빈 픽셀, 또는 텍스처 가장자리 너머
    for ex, ey in (
        (-(xx + 1.0), 0 * yy),
        (width - xx + 0.0, 0 * yy),
        (0 * xx, -(yy + 1.0)),
        (0 * xx, height - yy + 0.0),
    ):
        closer = np.hypot(ex, ey) < np.hypot(vbx, vby)
        vbx = np.where(closer, ex, vbx)
        vby = np.where(closer, ey, vby)
    to_top = np.hypot(vty, vtx)
    to_bg = np.hypot(vby, vbx)
    with np.errstate(invalid="ignore", divide="ignore"):
        cosine = (vty * vby + vtx * vbx) / (to_top * to_bg)
    rim = candidate & (to_top + to_bg <= RIM_PX + 2) & (cosine < OPPOSITE)
    rim |= skin & (labels == FULL_CUT) & ~inside & ~top_alpha  # 달리기 몸통: 상의 밖은 모두
    original = b.copy()
    b[rim] = 0
    b = close_slits(original, b)
    # 남은 부스러기: 상의에서 RIM_PX 안에만 있는 작은 덩어리(CRUMB_PX 이하)
    from outfit_pipeline.build import _components

    exposed = (b[..., 3] > 0) & area_on & ~inside & ~top_alpha
    near = (ty >= 0) & (to_top <= RIM_PX)
    for piece in _components(exposed):
        if len(piece) <= CRUMB_PX and near.reshape(-1)[piece].all():
            b.reshape(-1, 4)[piece] = 0
    return Image.fromarray(b, "RGBA")


def _sandwiched(mask: np.ndarray) -> np.ndarray:
    """위·아래 또는 왼쪽·오른쪽이 모두 [mask]인 픽셀."""
    up = np.zeros_like(mask)
    down = np.zeros_like(mask)
    left = np.zeros_like(mask)
    right = np.zeros_like(mask)
    up[1:-1] = mask[:-2]
    down[1:-1] = mask[2:]
    left[:, 1:-1] = mask[:, :-2]
    right[:, 1:-1] = mask[:, 2:]
    return (up & down) | (left & right)


def close_slits(before: np.ndarray, after: np.ndarray) -> np.ndarray:
    """띠 판정이 한 줄 걸러 지워 남긴 빗살을 고른다(앱 TopBodyCut.closeSlits): 먼저 지운 줄 사이에 낀 1px 맨몸 줄을 지우고(넓은 띠는 고르게
    비운다), 그다음 남은 맨몸 사이에 낀 1px 틈 가운데 8방향 이웃에 지운 픽셀이 2개 이하인 것(맨몸 안의 줄 틈)을 되살린다."""
    out = after.copy()
    removed = (before[..., 3] > 0) & (out[..., 3] == 0)
    out[(out[..., 3] > 0) & _sandwiched(removed)] = 0
    kept = out[..., 3] > 0
    removed = (before[..., 3] > 0) & ~kept
    around = np.zeros(kept.shape, int)
    padded = np.pad(removed, 1)
    height, width = kept.shape
    for dy in (-1, 0, 1):
        for dx in (-1, 0, 1):
            if dy or dx:
                around += padded[1 + dy : 1 + dy + height, 1 + dx : 1 + dx + width]
    restore = removed & _sandwiched(kept) & (around <= 2)
    out[restore] = before[restore]
    return out
