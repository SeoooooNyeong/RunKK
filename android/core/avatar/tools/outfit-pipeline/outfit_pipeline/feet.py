"""서기 신발이 맨몸 발 실루엣을 덮게 하기(판정 round2~3: lime_runner 신발보다 큰 외곽선과 살구색 테두리, ankle_boots 바깥 밝은 띠,
basic_runner 옆 띠).

서기 맨몸 아틀라스는 신발 밑에 맨발(종 모양 실루엣과 짙은 외곽선)이 있다. 생성 그림의 신발이 그 실루엣보다 좁으면 신발 옆으로 맨발
피부(러닝량 톤이 걸려 살구색)와 맨발 외곽선이 신발보다 큰 테두리로 보였다. 신발 칸(spec 「신발 기준점」 두 발 칸)에서 신발이 시작하는
행부터 아래로, 그 행 바로 위 다리 폭 밖으로 나온 맨발 픽셀을 신발로 덮는다: 바깥 가장자리 2px의 맨발 외곽선·반투명 픽셀은 맨몸
그대로(신발 외곽선이 된다), 나머지는 이웃 신발 천 색(holes.line_collar의 매끄러운 보간). 신발이 실루엣을 이미 덮으면 바뀌지 않는다.
"""

from __future__ import annotations

import numpy as np
from PIL import Image, ImageFilter

SOLID = 128
FEET = ((96, 627, 257, 768), (260, 627, 421, 768))  # 정면 발 칸(OUTFIT_SPEC 3절 「신발 기준점」)
START_SHARE = 0.6  # 신발 가로 폭이 그 행 맨발 폭의 이 비율 이상인 첫 행부터 덮는다(그 위는 발목·다리)
EDGE = 2  # 맨발 외곽선을 남기는 바깥 가장자리 폭
INK = 120  # 이보다 어두운 맨몸 픽셀은 외곽선
MIN_PIECE = 6  # 이보다 작은 덮을 조각(외곽선 안티앨리어싱 한두 점)은 그대로 둔다


def _rgba(image: Image.Image | np.ndarray) -> np.ndarray:
    return np.array(image.convert("RGBA")) if isinstance(image, Image.Image) else image


def _grow(mask: np.ndarray, size: int) -> np.ndarray:
    return np.array(Image.fromarray((mask * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(size))) > 0


def exposed_feet(layer: Image.Image | np.ndarray, body: Image.Image | np.ndarray) -> np.ndarray:
    """신발 레이어가 덮지 않아 신발 옆으로 보이는 맨발 픽셀(덮을 곳)."""
    shoe = _rgba(layer)[..., 3] >= SOLID
    skin = (
        _rgba(body)[..., 3] >= SOLID
    )  # 반투명 안티앨리어싱은 맨몸 그대로 둔다(신발 레이어 밑에서 가장자리를 부드럽게 한다)
    out = np.zeros(shoe.shape, bool)
    for x0, y0, x1, y1 in FEET:
        start = None
        for y in range(y0, y1):
            row_skin = np.nonzero(skin[y, x0:x1])[0]
            row_shoe = np.nonzero(shoe[y, x0:x1])[0]
            if len(row_skin) and len(row_shoe) and np.ptp(row_shoe) + 1 >= START_SHARE * (np.ptp(row_skin) + 1):
                start = y
                break
        if start is None:
            continue
        above = np.nonzero(skin[start - 1, x0:x1])[0] + x0
        leg = np.zeros(shoe.shape, bool)
        if len(above):
            leg[:, above.min() : above.max() + 1] = True
        box = np.zeros(shoe.shape, bool)
        box[start:y1, x0:x1] = True
        out |= box & skin & ~shoe & ~leg
    # 신발 바깥으로 떨어진 한두 점(외곽선 안티앨리어싱)은 덮지 않는다
    from outfit_pipeline.build import _components

    width = out.shape[1]
    for piece in _components(out):
        if len(piece) < MIN_PIECE:
            ys, xs = np.divmod(np.array(piece), width)
            out[ys, xs] = False
    return out


def cover_feet(layer: Image.Image, body: Image.Image) -> tuple[Image.Image, int]:
    """서기 신발 레이어가 맨발 실루엣을 덮게 한다([exposed_feet]). (레이어, 덮은 픽셀 수)."""
    from outfit_pipeline.holes import line_collar

    shoe = _rgba(layer)
    skin = _rgba(body)
    region = exposed_feet(shoe, skin)
    if not region.any():
        return layer, 0
    solid = shoe[..., 3] >= SOLID
    background = ~solid & ~region & (skin[..., 3] < SOLID)
    edge = _grow(background, 2 * EDGE + 1)
    keep_body = region & (skin[..., :3].astype(int).mean(axis=-1) < INK) & edge
    out, _ = line_collar(Image.fromarray(shoe, "RGBA"), region=region & ~keep_body)
    array = np.array(out)
    array[keep_body] = skin[keep_body]
    return Image.fromarray(array, "RGBA"), int(region.sum())


# 신발 옆 회색 띠(판정 round2~3: basic_runner 옆 띠). 생성 그림의 부드러운 그림자가 신발 외곽선 밖에 반투명 회색 띠(2~5px)로 남아 신발 옆에
# 흐린 띠로 보였다. 행마다 신발 바깥 끝에서 안쪽으로 [HALO_MAX]px 안의 첫 짙은 외곽선 픽셀보다 바깥에 있는, 외곽선보다 [HALO_LIGHT] 넘게 밝은
# 픽셀을 지운다(반투명 1px을 남기면 부스러기 검사 10번의 가는 털이 됐다).
HALO_MAX = (
    10  # 뒤꿈치 옆 회색 덧판은 외곽선 밖으로 8px까지 나왔다(6px로 자르면 위쪽만 남아 네모 탭이 됐다, 판정 round4)
)
HALO_FAINT = 64
HALO_MIN = 2  # 띠로 보는 덜 투명한 밝은 픽셀 최소 수
HALO_BRIGHT = 200  # 이보다 밝은 픽셀(흰 밑창)은 띠가 아니다
HALO_DARK = 90  # 이보다 어두운 불투명 픽셀은 외곽선
HALO_LIGHT = 40
# 신발별 밝기 차 하한: basic_runner 뒤꿈치 옆 회색 덧판(밝기 95~117)은 외곽선(45~60)과 차가 40에 못 미치는 행이 있어 위쪽만 남아 귀 모양 탭이 됐다
# (판정 round4). 다른 신발에 30을 쓰면 신발 그림(lime_runner 옆 천)까지 지운다
HALO_LIGHT_BY_KEY = {"shoes_basic_runner": 30}


def drop_halo(layer: Image.Image, _body: Image.Image | None = None, light: int = HALO_LIGHT) -> tuple[Image.Image, int]:
    """서기 신발 레이어의 두 발 칸에서 신발 외곽선 밖 회색 띠를 지운다. (레이어, 바꾼 픽셀 수)."""
    array = _rgba(layer).copy()
    alpha = array[..., 3]
    lum = array[..., :3].astype(int).mean(axis=-1)
    changed = 0
    for x0, y0, x1, y1 in FEET:
        for y in range(y0, y1):
            for xs in (list(range(x0, x1)), list(range(x1 - 1, x0 - 1, -1))):
                start = next((i for i, x in enumerate(xs) if alpha[y, x] > 0), None)
                if start is None:
                    continue
                window = xs[start : start + HALO_MAX]
                ink = next((i for i, x in enumerate(window) if alpha[y, x] >= SOLID and lum[y, x] < HALO_DARK), None)
                if not ink:
                    continue
                # 거의 투명한 안티앨리어싱(알파 [HALO_FAINT] 미만)은 색과 상관없이 띠에 넣는다
                halo = [
                    x
                    for x in window[:ink]
                    if alpha[y, x] < HALO_FAINT or lum[y, window[ink]] + light <= lum[y, x] < HALO_BRIGHT
                ]
                if sum(alpha[y, x] >= HALO_FAINT for x in halo) < HALO_MIN:
                    continue  # 반투명 안티앨리어싱만 있는 외곽선 가장자리는 그대로
                if len(halo) != ink:
                    continue  # 외곽선 밖이 모두 밝은 띠일 때만(외곽선 바깥에 짙은 픽셀이 섞이면 신발 그림이다)
                for x in halo:
                    array[y, x] = 0
                changed += len(halo)
    return Image.fromarray(array, "RGBA"), changed
