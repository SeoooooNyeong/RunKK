"""옷 레이어의 구멍 메우기(검수 3차, [임시:D-성-185] 뒤).

그림 도구 추출과 맨살 지우기(`skin.py`)가 옷 안에 반투명·투명 구멍을 남겼다: 폴로 플라켓의 단추 자리(살구색 단추가 맨살로 지워짐),
끈 고리 안, 주머니 옆 틈, 밑단 옆 V 트임. 예전엔 맨몸 바탕의 회색 속옷이 비쳐 옷처럼 보였지만, 맨몸이 피부가 되고 자르기가 생기자
갈색 점·배경으로 보였다.

구멍 = 옷 실루엣 안의 알파 128 미만 픽셀과 그 둘레 2px 안의 알파 200 미만 테두리(구멍과 떨어진 외곽선 안티앨리어싱은 그대로 둔다,
텍스처 가장자리 너머는 바깥):
- 둘러싸인 구멍: 굵은 천(7x7 열기) 안에서 알파 128 이상 옷으로 둘러싸인 빈 곳(4px 이하 틈으로만 바깥과 이어진 곳 포함, 바깥과 1px 넘게
  떨어진 곳).
- 좁은 트임: 7x7 닫기로 메워지는 바깥과 이어진 빈 곳 가운데 왼쪽·오른쪽 8px 안에 모두 굵은 천(7x7 열기)이 있는 곳(밑단 옆 V 트임).
  늘어진 끈 사이(가는 끈만 옆에 있다)와 목처럼 넓게 트인 곳은 남는다.

메우는 색: 밝은 천(플라켓) 위 둥근 구멍은 단추로 그린다(테두리 짙게, 길쭉한 구멍은 천 색만). 바로 옆(끈)과 조금 떨어진 곳(천)의 색이 크게 다르면
(끈 고리 안) 떨어진 곳의 천 색, 그 밖은 이웃 천 색을 안쪽으로 번져 채운다. 반투명 픽셀은 원래 색 위에 섞어 불투명하게 한다.
"""

from __future__ import annotations

import numpy as np
from PIL import Image, ImageFilter

SOLID = 128
SEE_THROUGH = 200  # 구멍 둘레에서 함께 메우는 반투명 테두리의 알파 상한
RIM = 2  # 구멍 둘레 반투명 테두리 폭
FILL_ROUNDS = 12  # 메우기 반복 상한
PAD = 8  # 가장자리 너머 바깥으로 덧대는 폭(7x7 닫기보다 넓게)
EDGE = 3  # 텍스처 가장자리에서 이 폭 안은 구멍으로 보지 않는다
CLOSE = 7
SEAL = 5
THICK = 7
REACH = 8
SURROUND = 48  # 둘러싸인 구멍: 네 방향 가운데 셋 이상에서 이 거리 안에 굵은 천
BUTTON_RADIUS = 5.0
BUTTON_CORE = 3  # 단추 자리로 보는 두께: 3x3로 이만큼 열어도 남는 곳
ROUND_ASPECT = 1.5  # 단추 자리의 긴 변 / 짧은 변 상한
ROUND_FILL = 0.6  # 단추 자리 넓이 / 바깥 상자 넓이 하한(원은 0.79)
LIGHT = 190  # 단추 바탕(플라켓)으로 보는 밝기
RING_DIFF = 60  # 바로 옆과 조금 떨어진 곳의 밝기 차가 이보다 크면 끈 고리 안


def _rgba(image: Image.Image | np.ndarray) -> np.ndarray:
    return np.array(image.convert("RGBA")) if isinstance(image, Image.Image) else image


def _filter(mask: np.ndarray, *filters: ImageFilter.Filter) -> np.ndarray:
    image = Image.fromarray((mask * 255).astype(np.uint8))
    for f in filters:
        image = image.filter(f)
    return np.array(image) > 0


def _lum(rgb: np.ndarray) -> np.ndarray:
    return rgb[..., :3].astype(float).mean(axis=-1)


def _reach(mask: np.ndarray, dy: int, dx: int, reach: int = REACH) -> np.ndarray:
    """픽셀마다 (dy, dx) 방향 1~[reach]px 안에 [mask]가 있는지."""
    out = np.zeros_like(mask)
    height, width = mask.shape
    for d in range(1, reach + 1):
        sy, sx = dy * d, dx * d
        src = mask[max(0, sy) : height + min(0, sy), max(0, sx) : width + min(0, sx)]
        out[max(0, -sy) : height + min(0, -sy), max(0, -sx) : width + min(0, -sx)] |= src
    return out


def hole_mask(image: Image.Image | np.ndarray) -> np.ndarray:
    """옷 실루엣 안의 구멍(둘러싸인 구멍 + 굵은 천 사이 좁은 트임)."""
    from outfit_pipeline.build import _fill_holes

    # 텍스처 가장자리 너머도 바깥이다(가장자리에서 잘린 양말 목·허리를 둘러싸인 것으로 보지 않게 투명하게 덧대고 계산한다)
    array = np.pad(_rgba(image), ((PAD, PAD), (PAD, PAD), (0, 0)))
    alpha = array[..., 3]
    solid = alpha >= SOLID
    thick = _filter(solid, ImageFilter.MinFilter(THICK), ImageFilter.MaxFilter(THICK))
    # 4px 이하 틈으로만 바깥과 이어진 구멍(플라켓을 따라 목 트임과 이어진 단추 자리)도 둘러싸인 것으로 본다
    sealed = _filter(solid, ImageFilter.MaxFilter(SEAL), ImageFilter.MinFilter(SEAL)) | solid
    filled = np.array(_fill_holes(Image.fromarray((sealed * 255).astype(np.uint8)))) > 0
    # 굵은 천이 둘러싼 곳만: 가는 선(다리 외곽선 같은)으로 둘러싸인 빈 곳은 옷 안이 아니다
    body = np.array(_fill_holes(Image.fromarray((_filter(thick, ImageFilter.MaxFilter(3)) * 255).astype(np.uint8)))) > 0
    near_outside = _filter(~filled, ImageFilter.MaxFilter(3))
    # 텍스처 가장자리 3px 안은 메시가 잘리는 곳이라 구멍으로 보지 않는다(양말 목 위 끝의 반투명 홈)
    inner = np.zeros_like(near_outside)
    inner[PAD + EDGE : -(PAD + EDGE), PAD + EDGE : -(PAD + EDGE)] = True
    enclosed = filled & body & ~solid & ~near_outside
    # 굵은 천이 네 방향 가운데 셋 이상에서 [SURROUND]px 안에 있어야 한다(밑단 아래 늘어진 끈 사이 틈은 위에만 천이 있어, 메운 줄 바로 아래가
    # 다시 둘러싸인 것으로 보여 끈 사이를 한 줄씩 끝까지 메웠다)
    sides = sum(_reach(thick, dy, dx, SURROUND).astype(int) for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1)))
    enclosed &= sides >= 3
    closed = _filter(solid, ImageFilter.MaxFilter(CLOSE), ImageFilter.MinFilter(CLOSE))
    slit = closed & ~filled & ~solid
    if slit.any():
        slit &= _reach(thick, 0, -1) & _reach(thick, 0, 1)
    seeds = (enclosed | slit) & inner
    # 구멍 둘레 2px 안의 반투명(알파 128~199) 테두리도 구멍과 함께 메운다. 구멍과 떨어진 외곽선 안티앨리어싱은 그대로 둔다
    rim = _filter(seeds, ImageFilter.MaxFilter(2 * RIM + 1)) & solid & (alpha < SEE_THROUGH)
    return (seeds | rim)[PAD:-PAD, PAD:-PAD]


def _component_fill(array: np.ndarray, piece: np.ndarray) -> None:
    """구멍 한 덩어리([piece], 불리언)를 메운다. [array](float RGBA)를 바꾼다."""
    solid = (array[..., 3] >= SOLID) & ~piece
    near = _filter(piece, ImageFilter.MaxFilter(3)) & solid
    far = _filter(piece, ImageFilter.MaxFilter(17)) & ~_filter(piece, ImageFilter.MaxFilter(7)) & solid
    if not near.any():
        return
    c_near = np.median(array[near][:, :3], axis=0)
    c_far = np.median(array[far][:, :3], axis=0) if far.any() else c_near
    original = array[piece].copy()
    cover = original[:, 3:4] / 255.0
    light = solid & (_lum(array) >= LIGHT)
    broad_light = _filter(light, ImageFilter.MinFilter(THICK), ImageFilter.MaxFilter(THICK))
    if _lum(c_near) >= LIGHT and (near & broad_light).any():
        # 넓은 밝은 천(플라켓·흰 단, 7x7 열기로 남는 폭) 안의 구멍: 그 천 색으로 메우고, 작고 둥근 구멍(단추 자리)에는 둥근 단추를 그린다(테두리 짙게)
        array[piece, :3] = c_near
        from outfit_pipeline.build import _components, _fill_holes

        # 단추 자리: 구멍(남은 단추 조각을 감싼 고리 모양이면 안을 채운 것)을 3x3로 3번 열어(깎고 다시 키워) 남는 덩어리 가운데 둥근 것
        # (긴 변 ≤ 짧은 변 × 1.5, 넓이 ≥ 바깥 상자의 0.6)마다 둥근 단추를 그린다. 플라켓 가장자리를 따라 이어진 1~2px 틈은 열면 사라지고,
        # 흰 티 밑단 위 길쭉한 구멍은 둥글지 않아 단추가 되지 않는다(이웃 천 색으로만 메운다)
        whole = np.array(_fill_holes(Image.fromarray((piece * 255).astype(np.uint8)))) > 0
        opened = _filter(
            whole, *([ImageFilter.MinFilter(3)] * BUTTON_CORE), *([ImageFilter.MaxFilter(3)] * BUTTON_CORE)
        )
        yy, xx = np.mgrid[0 : array.shape[0], 0 : array.shape[1]]
        w = array.shape[1]
        for blob in _components(opened):
            cy, cx = np.divmod(np.array(blob), w)
            height, width = cy.max() - cy.min() + 1, cx.max() - cx.min() + 1
            if max(height, width) > ROUND_ASPECT * min(height, width) or len(blob) < ROUND_FILL * height * width:
                continue
            distance = np.hypot(yy - cy.mean(), xx - cx.mean())
            disc = distance <= BUTTON_RADIUS
            array[disc, :3] = c_near * 0.88
            array[disc & (distance > BUTTON_RADIUS - 1.2), :3] = c_near * 0.62
            array[disc, 3] = 255
    elif abs(_lum(c_near) - _lum(c_far)) > RING_DIFF:
        array[piece, :3] = original[:, :3] * cover + c_far * (1 - cover)
    else:
        todo = piece.copy()
        colors = np.zeros_like(array[..., :3])
        known = solid.copy()
        colors[known] = array[known][:, :3]
        while todo.any():
            grown = []
            for y, x in zip(*np.nonzero(todo), strict=True):
                around = [
                    colors[ny, nx]
                    for ny, nx in ((y - 1, x), (y + 1, x), (y, x - 1), (y, x + 1))
                    if 0 <= ny < known.shape[0] and 0 <= nx < known.shape[1] and known[ny, nx]
                ]
                if around:
                    grown.append((y, x, np.mean(around, axis=0)))
            if not grown:
                break
            for y, x, color in grown:
                colors[y, x] = color
                known[y, x] = True
                todo[y, x] = False
        array[piece, :3] = original[:, :3] * cover + colors[piece] * (1 - cover)
    array[piece, 3] = 255


def fill_garment_holes(image: Image.Image) -> tuple[Image.Image, int]:
    """옷 레이어의 [hole_mask]를 메운다(메우면 새 트임이 닫힐 수 있어 더 없을 때까지, 최대 [FILL_ROUNDS]번: 넓게 비친 깃은 한 번에 몇 px씩 둘러싸인다). (레이어, 메운 픽셀 수)."""
    from outfit_pipeline.build import _components

    array = _rgba(image).astype(np.float64)
    total = 0
    for _ in range(FILL_ROUNDS):
        mask = hole_mask(array.round().clip(0, 255).astype(np.uint8))
        if not mask.any():
            break
        width = mask.shape[1]
        for flat in _components(mask):
            ys, xs = np.divmod(np.array(flat), width)
            y0, y1 = max(0, ys.min() - 12), min(mask.shape[0], ys.max() + 13)
            x0, x1 = max(0, xs.min() - 12), min(width, xs.max() + 13)
            piece = np.zeros((y1 - y0, x1 - x0), bool)
            piece[ys - y0, xs - x0] = True
            window = array[y0:y1, x0:x1]
            _component_fill(window, piece)
        total += int(mask.sum())
    return Image.fromarray(array.round().clip(0, 255).astype(np.uint8), "RGBA"), total
