"""옷 레이어 안의 맨살 픽셀(실기기 3차 DV3-8, [임시:D-성-172]).

피부는 맨몸 바탕에서만 오고 러닝량 톤이 걸린다. 옷 레이어에 승인 텍스처의 피부(소매 밑 겨드랑이, 소매 끝 손목, 목둘레 안, 발목)가
남으면 톤이 걸리지 않아 러닝량이 높을 때 짙은 팔 옆에 밝은 분홍 피부로 보인다.

맨살 픽셀: 알파 [ALPHA_MIN] 이상이고 밝은 피부 색조(분홍빛 살구색: R ≥ 170, G ≥ 130, 30 ≤ R−G ≤ 80, G−B ≥ 4,
2(G−B) ≤ (R−G)+4)인 픽셀. 크림·베이지 천(G−B가 큼)과 코랄 천(R−G가 큼), 러닝량 100 톤이 걸린 피부(G ≤ 127)는 빠진다. 맨살 덩어리(8방향으로 이은 맨살 픽셀)가 옷의 빈 곳(알파 [ALPHA_MIN] 미만)이나
그림 가장자리에 닿으면 「드러난 맨살」이다. 천 안에 갇힌 피부색 무늬(끈, 패치, 끈 구멍, 하이라이트)는 빈 곳에 닿지 않아 빠진다.
앱 시험(OutfitAssetSpecTest)이 같은 규칙으로 센다.
"""

from __future__ import annotations

from collections import deque

import numpy as np
from PIL import Image

ALPHA_MIN = 32
# 외곽선 안티에일리어싱 부스러기(이보다 작은 덩어리)는 세지 않는다
MIN_PIECE = 4
# 지울 때는 경계를 이만큼 넓힌다(검사·앱 시험은 넓히지 않는다)
STRIP_MARGIN = 2


def skin_mask(rgba: np.ndarray, margin: int = 0) -> np.ndarray:
    """맨살 픽셀. [margin]만큼 경계를 넓힌다(지울 때 쓴다: 앱 디코더 반올림 차이로 경계 픽셀이 남지 않게)."""
    r, g, b, a = (rgba[..., i].astype(int) for i in range(4))
    rg = r - g
    gb = g - b
    return (
        (a >= ALPHA_MIN)
        & (r >= 170 - margin)
        & (g >= 130 - margin)
        & (rg >= 30 - margin)
        & (rg <= 80 + margin)
        & (gb >= 4 - margin)
        & (2 * gb <= rg + 4 + margin)
    )


def exposed_skin(rgba: np.ndarray, minimum: int = 1, margin: int = 0) -> np.ndarray:
    """빈 곳이나 가장자리에 닿은 맨살 덩어리([minimum]픽셀 이상)의 마스크."""
    skin = skin_mask(rgba, margin)
    empty = rgba[..., 3].astype(int) < ALPHA_MIN
    height, width = skin.shape
    seen = np.zeros_like(skin)
    out = np.zeros_like(skin)
    for y0, x0 in zip(*np.nonzero(skin), strict=True):
        if seen[y0, x0]:
            continue
        piece = [(y0, x0)]
        seen[y0, x0] = True
        queue = deque(piece)
        touches = False
        while queue:
            y, x = queue.popleft()
            if y in (0, height - 1) or x in (0, width - 1):
                touches = True
            for dy in (-1, 0, 1):
                for dx in (-1, 0, 1):
                    ny, nx = y + dy, x + dx
                    if not (0 <= ny < height and 0 <= nx < width):
                        continue
                    if abs(dy) + abs(dx) == 1 and empty[ny, nx]:
                        touches = True
                    if skin[ny, nx] and not seen[ny, nx]:
                        seen[ny, nx] = True
                        piece.append((ny, nx))
                        queue.append((ny, nx))
        if touches and len(piece) >= minimum:
            ys, xs = zip(*piece, strict=True)
            out[list(ys), list(xs)] = True
    return out


def count_exposed(image: Image.Image) -> int:
    """시험·검사용: 외곽선 부스러기를 뺀 드러난 맨살 픽셀 수."""
    return int(exposed_skin(np.array(image.convert("RGBA")), MIN_PIECE).sum())


def strip_skin(image: Image.Image) -> tuple[Image.Image, int]:
    """드러난 맨살을 투명하게 지운다(맨몸 바탕의 톤 걸린 피부가 보이게). 지운 픽셀 수도 돌려준다."""
    array = np.array(image.convert("RGBA"))
    total = 0
    # 지우면 새 가장자리가 생겨 안쪽 맨살이 드러날 수 있어 더 없을 때까지 되풀이한다
    while True:
        mask = exposed_skin(array, margin=STRIP_MARGIN)
        count = int(mask.sum())
        if count == 0:
            break
        array[mask] = 0
        total += count
    return Image.fromarray(array, "RGBA"), total
