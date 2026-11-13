"""신발 잔상 지우기(판정 round1 D).

그림 생성 결과를 기본 신발 실루엣(템플릿)에 맞춰 뽑을 때, 새 신발이 템플릿보다 좁은 곳에 생성 그림의 그림자·배경이 불투명하게 남았다
(high_top의 검은 실루엣, lime_runner의 적갈색 테두리). 신발 자체 외곽선 바깥의 이 띠를 지운다.

띠 찾기: 행마다 바깥(투명)에서 안으로 들어가며 이웃 픽셀 색 차가 [JUMP] 이하로 부드럽게 이어지는 픽셀을 모으다가 처음 큰 색 변화(신발
외곽선)를 만나면 멈춘다. 그 길이가 [MIN_RUN]~[MAX_RUN]px이고 밝은 픽셀(양말·흰 밑창)이 없으면 잔상 띠다. 기본 신발처럼 외곽선이
실루엣 가장자리에 바로 있으면 띠 길이가 짧아 남는다. 지운 뒤 남는 실루엣은 5x5 닫기와 7x7 열기로 다듬는다(행마다 멈춘 자리가 달라
생기는 빗살을 없앤다). 다듬기로 지우는 곳은 띠 둘레 3px 안뿐이다.

모든 신발에 쓰면 외곽선이 부드러운 신발(밑창 무늬, 짙은 갑피)의 외곽선까지 지워, 잔상이 확인된 신발([spec.SHOE_GHOST_KEYS])에만 쓴다.
"""

from __future__ import annotations

import numpy as np
from PIL import Image, ImageFilter

JUMP = 35  # 잔상 띠 안 이웃 픽셀 색 차(채널 최댓값) 상한. 이보다 크면 신발 외곽선이 시작된 것으로 본다
MIN_RUN = 6
MAX_RUN = 22
BRIGHT = 190  # 이 밝기 이상 픽셀이 있는 띠는 잔상이 아니다(양말·흰 밑창)


def _scan(rgb: np.ndarray, ok: np.ndarray) -> np.ndarray:
    """1차원 열에서 바깥(투명)에서 들어와 처음 큰 색 변화 전까지의 잔상 띠."""
    out = np.zeros(len(ok), bool)
    n = len(ok)
    for i in range(n):
        if not ok[i] or (i > 0 and ok[i - 1]):
            continue
        j = i + 1
        while j < n and ok[j] and j - i <= MAX_RUN and np.abs(rgb[j] - rgb[j - 1]).max() <= JUMP:
            j += 1
        if MIN_RUN <= j - i <= MAX_RUN and j < n and ok[j] and rgb[i:j].mean(axis=1).max() < BRIGHT:
            out[i:j] = True
    return out


def ghost_mask(image: Image.Image | np.ndarray) -> np.ndarray:
    """신발 레이어의 잔상 픽셀(지울 곳)."""
    array = np.array(image.convert("RGBA")) if isinstance(image, Image.Image) else image
    solid = array[..., 3] > 0
    rgb = array[..., :3].astype(int)
    band = np.zeros_like(solid)
    for y in range(solid.shape[0]):
        if solid[y].any():
            band[y] |= _scan(rgb[y], solid[y])
            band[y] |= _scan(rgb[y][::-1], solid[y][::-1])[::-1]
    band = _morph(band, ImageFilter.MaxFilter(3), ImageFilter.MinFilter(3)) & solid
    keep = _morph(solid & ~band, ImageFilter.MaxFilter(5), ImageFilter.MinFilter(5))
    keep = _morph(keep, ImageFilter.MinFilter(7), ImageFilter.MaxFilter(7))
    # 다듬기로 지워지는 곳은 띠 둘레뿐이다(가는 끈·양말 끝은 띠와 멀면 남는다)
    return solid & ~keep & _morph(band, ImageFilter.MaxFilter(7))


def _morph(mask: np.ndarray, *filters: ImageFilter.Filter) -> np.ndarray:
    image = Image.fromarray((mask * 255).astype(np.uint8))
    for f in filters:
        image = image.filter(f)
    return np.array(image) > 0


def drop_ghost(image: Image.Image) -> tuple[Image.Image, int]:
    """잔상 띠를 지운 레이어와 지운 픽셀 수."""
    array = np.array(image.convert("RGBA"))
    mask = ghost_mask(array)
    array[mask] = 0
    return Image.fromarray(array, "RGBA"), int(mask.sum())


PIECE = 200  # 신발 레이어에서 따로 떨어진 덩어리를 지우는 크기 상한(신발 한 짝은 1만 5천 픽셀 남짓)


def drop_pieces(image: Image.Image) -> tuple[Image.Image, int]:
    """신발 레이어에서 신발과 떨어진 작은 덩어리(8방향, [PIECE]px 미만)를 지운다(판정 round1 D: ankle_boots 발목 위 짧은 획)."""
    from outfit_pipeline.build import _components

    array = np.array(image.convert("RGBA"))
    width = array.shape[1]
    removed = 0
    for flat in _components(array[..., 3] > 0):
        if len(flat) < PIECE:
            ys, xs = np.divmod(np.array(flat), width)
            array[ys, xs] = 0
            removed += len(flat)
    return Image.fromarray(array, "RGBA"), removed
