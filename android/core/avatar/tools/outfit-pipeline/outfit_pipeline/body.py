"""맨몸 바탕·옷 레이어 정리(D-816 민소매 팔 선, D-817 허리 띠·반바지 가장자리).

riv 메시는 체형과 숨쉬기 동작에서 팔과 몸통 사이, 엉덩이 옆을 늘린다. 그래서 텍스처의 작은 흠이 화면에서 선으로 커진다.

- 부스러기(`drop_strays`): 외곽선에 붙지 않은 고립 점과 거의 투명한 먼지(`dust_mask`). 팔꿈치 꼬리(소매 모서리
  외곽선 조각, 그림 도구 외곽선 실오라기)과 떨어진 점·거의 투명한 먼지다. 가장자리 안티에일리어싱 1px은 남는다.
- 소매 밑단 줄(`smooth_sleeve_line`): 서기 맨몸 팔은 소매 높이 위가 그림 도구 피부, 아래가 승인 피부라 그 사이에 소매 밑단 선과
  소매 그림자 띠가 남았다. 민소매에서 팔꿈치 위 가로 줄로 보였다. 팔 안쪽 픽셀을 위아래 피부 사이 보간으로 바꾼다.
- 하의 허리 조각(`trim_waist`): 하의 서기 레이어의 허리 겹침 띠에 하의 본체와 떨어진 조각이 남아 있었다.
- 엉덩이 속옷(`bare_hips`): 서기 맨몸의 허리·엉덩이 칸(`spec.STAND_HIPS`)에는 승인 기본 반바지(짙은 허리 밴드, 회색 바지, 밑단 선)가
  남아 있었다. 다른 하의 밖으로 비치고(밑단과 허리 사이 짙은 띠, 하의 가장자리 들쭉날쭉) 피부톤이 걸려 더 짙어졌다. 안쪽 무채색 픽셀은
  그 열의 다리 피부색(위로 이음)으로, 실루엣 가장자리 무채색은 몸 외곽선 갈색으로 바꾸고, 허리 밴드는 기본 하의 레이어로 옮긴다.
"""

from __future__ import annotations

from itertools import pairwise

import numpy as np
from PIL import Image, ImageFilter

from outfit_pipeline import spec

SOLID = 128
NEUTRAL = 25  # 채널 최대−최소가 이보다 작으면 무채색(속옷 회색·짙은 허리 밴드·그 외곽선). 피부는 40 이상
BODY_OUTLINE = (58, 30, 16)  # 승인 텍스처 다리 외곽선
WISP_DARK = 140  # 옷 레이어의 갈색 실오라기 밝기 상한(크림·베이지 천은 더 밝다)
REMOVE_MARGIN = 2  # 지울 때 문턱을 넓히는 폭(앱 디코더 반올림 차이로 검사 문턱에 걸린 픽셀이 남지 않게)
EDGE_PX = 5  # 실루엣 가장자리(빈 곳까지 이 거리 안): 맨몸 외곽선 두께(4~6px)를 다 덮는다. 이 안은 고치지 않는다
OUTLINE_DARK = 120  # 맨몸 외곽선으로 보는 밝기 상한
DUST_PIECE = 40  # 8방향으로 이은 알파 덩어리가 이보다 작으면(또는 알파 128 이상 픽셀이 없으면) 고립 점
ELBOW_WINDOW = (140, 180)  # 서기 팔꿈치 높이(승인 반팔 소매 모서리 외곽선 꼬리가 남은 행)
EMPTY = 16  # 이보다 옅으면 빈 곳(속바지 밑단 선 안쪽의 반투명 픽셀은 빈 곳이 아니다)
LEG_REACH = 40  # 허리·엉덩이 칸 아래에서 다리 피부를 찾는 행 수
PINHOLE = 8  # 몸 안 투명 구멍을 채우는 최대 크기
LEG_SMOOTH = 21  # 허리·엉덩이 피부색을 가로로 고르는 폭
DARK = 120  # 허리·엉덩이 칸 안쪽에서 이보다 어두우면 옛 반바지 선
SEAM_FROM = 100  # 승인 소매를 찾는 행(어깨 아래)
SEAM_LIMIT = 230  # 승인 소매를 찾는 행(그 아래는 몸판)
SEAM_SLEEVE_MAX = 200  # 소매 밑단으로 보는 행 상한(그보다 아래면 몸판까지 이어진 열)
SEAM_BORROW = 40  # 밑단을 못 찾은 열이 빌려 올 소매 열의 거리
SEAM_ABOVE = 4  # 소매 밑단 행 위로 이만큼(보간 위 끝)
SEAM_BELOW = 30  # 소매 밑단 행 아래로 이만큼(아래팔 피부 기준 색)
SEAM_MIN_DEPTH = 14  # 맨몸 소매 줄은 승인 밑단보다 최대 8행 아래에 있다(맨몸 만들 때 피부가 시작하는 행부터 옮겼다)
SEAM_BLEND = 8  # 끝 색을 고르는 양옆 열 수
SEAM_MATCH = 18  # 아래팔 피부와 이만큼(채널 차 합) 안이면 그림자 띠가 끝난 것


def _rgba(image: Image.Image | np.ndarray) -> np.ndarray:
    return np.array(image.convert("RGBA")) if isinstance(image, Image.Image) else image


def _thick(solid: np.ndarray, size: int) -> np.ndarray:
    """[solid]를 size x size로 열고(깎았다 넓힘) 1px 더 넓힌 굵은 실루엣."""
    image = Image.fromarray((solid * 255).astype(np.uint8))
    opened = image.filter(ImageFilter.MinFilter(size)).filter(ImageFilter.MaxFilter(size))
    return np.array(opened.filter(ImageFilter.MaxFilter(3))) > 0


def _thin_mask(rgba: np.ndarray) -> np.ndarray:
    """굵은 실루엣(3x3로 열고 1px 넓힌 곳) 밖의 알파가 있는 픽셀(실루엣에서 뻗은 1~2px 털 포함)."""
    return (rgba[..., 3] > 0) & ~_thick(rgba[..., 3] >= SOLID, 3)


def stray_mask(rgba: np.ndarray, garment: bool = False, thin: bool = False, margin: int = 0) -> np.ndarray:
    """부스러기: 고립 점([dust_mask]: 외곽선에 붙지 않은 작은 덩어리, 거의 투명한 먼지). 맨몸은 외곽선에 붙은 픽셀을 건드리지 않는다(검수: 3x3로
    깎으면 외곽선이 끊겨 실기기에서 팔이 울퉁불퉁했다). 옷 레이어([thin])는 3x3 굵은 실루엣 밖 1~2px 털도 센다. [garment]면 옷 레이어에 남은
    갈색 맨몸 외곽선 실오라기(5x5 굵은 실루엣 밖 불투명 픽셀, R > G + 10, R > B + 20, 밝기 [WISP_DARK] 미만)도 센다. 지울 때는 [margin]만큼
    경계를 넓혀 앱 디코더 반올림 차이로 문턱에 걸린 픽셀이 남지 않게 한다."""
    solid = rgba[..., 3] >= SOLID
    mask = dust_mask(rgba)
    if thin:
        mask |= _thin_mask(rgba)
    if garment:
        rgb = rgba[..., :3].astype(int)
        brown = (
            (rgb[..., 0] > rgb[..., 1] + 10 - margin)
            & (rgb[..., 0] > rgb[..., 2] + 20 - margin)
            & (rgb.mean(axis=2) < WISP_DARK + margin)
        )
        mask |= solid & brown & ~_thick(solid, 5)
    return mask


def wisp_layer(asset_key: str, name: str) -> bool:
    """갈색 실오라기 규칙을 보는 옷 레이어: 맨팔이 옷 가장자리에 닿는 반팔·민소매 상의(`maleTopSurface` 1, 기본 의상 base_top 제외)의
    서기·달리기 몸통. 긴소매는 팔을 덮고, 갈색 옷(후드)은 제 외곽선이 갈색이라 뺀다. 팔 레이어는 맨팔과 맞닿은 갈색 외곽선이 옷의 일부다."""
    slot = name.removesuffix(".webp").split("_", 1)[1].removesuffix("_b1").removesuffix("_b2")
    try:
        short_top = spec.part_of_key(asset_key) == "TOP" and spec.default_male_top_surface(asset_key) == 1
    except KeyError:  # 묶음 폴더가 아닌 곳(시험용 임시 파일)
        return False
    return short_top and not asset_key.startswith("base_") and slot in ("stand", "run_body")


def count_strays(image: Image.Image, garment: bool = False, thin: bool = False) -> int:
    return int(stray_mask(_rgba(image), garment, thin).sum())


def drop_strays(image: Image.Image, garment: bool = False, thin: bool = False) -> tuple[Image.Image, int]:
    """부스러기를 투명하게 지운다(지우면 새 털이 드러날 수 있어 더 없을 때까지). 지운 픽셀 수도 돌려준다."""
    array = _rgba(image).copy()
    total = 0
    while (mask := stray_mask(array, garment, thin, margin=REMOVE_MARGIN)).any():
        array[mask] = 0
        total += int(mask.sum())
    return Image.fromarray(array, "RGBA"), total


def _near_edge(alpha: np.ndarray, threshold: int, radius: int) -> np.ndarray:
    """[threshold] 미만 픽셀에서 [radius](체비쇼프) 안."""
    thin = Image.fromarray(((alpha < threshold) * 255).astype(np.uint8))
    return np.array(thin.filter(ImageFilter.MaxFilter(2 * radius + 1))) > 0


def _neutral(rgba: np.ndarray) -> np.ndarray:
    rgb = rgba[..., :3].astype(int)
    return (rgb.max(axis=2) - rgb.min(axis=2)) < NEUTRAL


def _zone(shape: tuple[int, ...], box: tuple[int, int, int, int]) -> np.ndarray:
    x0, y0, x1, y1 = box
    zone = np.zeros(shape[:2], bool)
    zone[y0:y1, x0:x1] = True
    return zone


def _near_outside(alpha: np.ndarray, radius: int) -> np.ndarray:
    """그림 밖과 이어진 빈 곳(알파 [EMPTY] 미만)에서 [radius] 안. 몸 안에 갇힌 반투명 구멍은 밖이 아니다."""
    from outfit_pipeline.build import _fill_holes

    filled = np.array(_fill_holes(Image.fromarray(((alpha >= EMPTY) * 255).astype(np.uint8)))) > 0
    outside = Image.fromarray((~filled * 255).astype(np.uint8))
    return np.array(outside.filter(ImageFilter.MaxFilter(2 * radius + 1))) > 0


def underwear_mask(rgba: np.ndarray | Image.Image, margin: int = 0) -> np.ndarray:
    """서기 맨몸의 허리·엉덩이 칸 안 옷 픽셀: 실루엣 가장자리([EDGE_PX]) 안쪽의 무채색(속옷 회색·짙은 허리 밴드)과 짙은 선(옛 반바지 밑단·주름
    선, 밝기 [DARK] 미만). 가장자리의 몸 외곽선은 빠진다."""
    array = _rgba(rgba)
    rgb = array[..., :3].astype(int)
    dark = rgb.mean(axis=2) < DARK + margin
    inner = ~_near_outside(array[..., 3], EDGE_PX)
    neutral = (rgb.max(axis=2) - rgb.min(axis=2)) < NEUTRAL + margin
    return (array[..., 3] >= SOLID) & (neutral | dark) & inner & _zone(array.shape, spec.STAND_HIPS)


def _leg_colors(array: np.ndarray) -> np.ndarray:
    """허리·엉덩이 칸 열마다 칠할 피부색: 그 열에서 칸 바로 아래 다리 피부(밝은 살구색, 가장자리 밖)의 가운데값. 다리가 없는 열(가랑이 틈,
    엉덩이 바깥)은 양옆 열 사이를 가로로 보간하고, 가로로 [LEG_SMOOTH]px 고른다. (열 수, 3)."""
    x0, _, x1, y1 = spec.STAND_HIPS
    rgb = array[..., :3].astype(int)
    skin = (
        (array[..., 3] >= 250)
        & (rgb[..., 0] >= 170)
        & (rgb[..., 0] - rgb[..., 2] >= 30)
        & ~_near_edge(array[..., 3], EMPTY, 3)
    )
    xs, colors = [], []
    for x in range(x0, x1):
        rows = np.flatnonzero(skin[y1 : y1 + LEG_REACH, x])
        if len(rows):
            xs.append(x)
            colors.append(np.median(rgb[y1 + rows, x], axis=0))
    if not xs:
        raise ValueError("no leg skin under the hips")
    known = np.array(colors, float)
    columns = np.arange(x0, x1)
    line = np.stack([np.interp(columns, xs, known[:, c]) for c in range(3)], axis=1)
    kernel = np.ones(LEG_SMOOTH) / LEG_SMOOTH  # 다리 그늘이 세로 줄무늬로 올라오지 않게 가로로 고르게
    padded = np.pad(line, ((LEG_SMOOTH // 2, LEG_SMOOTH // 2), (0, 0)), mode="edge")
    smooth = np.stack([np.convolve(padded[:, c], kernel, mode="valid") for c in range(3)], axis=1)
    return smooth.round().clip(0, 255).astype(np.uint8)


HIP_HOLE_X = (110, 403)  # 엉덩이 구멍으로 메우는 x 범위(팔과 엉덩이 사이 틈은 빼고)


def bare_hips(image: Image.Image) -> tuple[Image.Image, Image.Image]:
    """서기 맨몸의 허리·엉덩이 속옷을 맨몸으로 바꾼다. (맨몸, 기본 하의로 옮길 허리 밴드 레이어)."""
    array = _rgba(image).copy()
    zone = _zone(array.shape, spec.STAND_HIPS)
    near = _near_outside(array[..., 3], EDGE_PX)
    under = underwear_mask(array, REMOVE_MARGIN)
    band = np.zeros_like(array)
    waist = (array[..., 3] >= SOLID) & _neutral(array) & zone & _zone(array.shape, spec.STAND_WAIST)
    band[waist] = array[waist]
    # 몸 안에 갇힌 반투명 구멍(옛 밑단 선 사이)도 피부로 채운다
    holes = zone & (array[..., 3] < SOLID) & ~_near_outside(array[..., 3], 0)
    # 팔과 엉덩이 사이 틈(x 110 밖)은 구멍이 아니다: 메우면 팔 가장자리가 승인 그림과 달라지고 팔과 몸 사이가 피부로 이어졌다
    holes[:, : HIP_HOLE_X[0]] = False
    holes[:, HIP_HOLE_X[1] :] = False
    inner = under | holes
    colors = _leg_colors(array)
    x0 = spec.STAND_HIPS[0]
    ys, xs = np.nonzero(inner)
    array[ys, xs, :3] = colors[xs - x0]
    array[ys, xs, 3] = 255
    # 가장자리(외곽선)는 모양 그대로 둔다(외곽선을 다시 그리면 실기기에서 들쭉날쭉했다). 옛 반바지 외곽선의 무채색만 색을 바꾼다:
    # 짙은 픽셀은 같은 밝기의 몸 외곽선 갈색으로, 밝은 회색 안티에일리어싱은 다리 피부색으로(하의 옆에 회색 테두리로 비쳤다)
    rim = (
        zone
        & near
        & (array[..., 3] > 0)
        & ((array[..., :3].astype(int).max(axis=2) - array[..., :3].astype(int).min(axis=2)) < NEUTRAL + REMOVE_MARGIN)
    )
    lum = array[..., :3].astype(float).mean(axis=2)
    dark_rim = rim & (lum < DARK)
    tint = np.array(BODY_OUTLINE, float) / np.mean(BODY_OUTLINE)
    array[dark_rim, :3] = (lum[dark_rim, None] * tint).clip(0, 255).astype(np.uint8)
    light_rim = rim & ~dark_rim
    ys2, xs2 = np.nonzero(light_rim)
    array[ys2, xs2, :3] = colors[xs2 - x0]
    return Image.fromarray(array, "RGBA"), Image.fromarray(band, "RGBA")


def sleeve_seams(legacy: Image.Image) -> dict[int, int]:
    """승인 기본 텍스처(흰 반팔)의 열마다 소매 밑단 행(소매 흰 픽셀의 가장 아래 행, y 230 위). 서기 팔 열만."""
    ref = _rgba(legacy).astype(int)
    rgb = ref[..., :3]
    tee = (ref[..., 3] > 0) & (rgb.min(axis=2) > 215) & ((rgb.max(axis=2) - rgb.min(axis=2)) < 30)
    columns = [*range(spec.STAND_LEFT_ARM[0], spec.STAND_LEFT_ARM[2]), *range(spec.STAND_RIGHT_ARM[0], ref.shape[1])]
    seams = {}
    for x in columns:
        # 흰 소매가 끝나고 아래로 5행 넘게 피부(흰 티 아님, 불투명, 붉은 기)가 이어지는 마지막 행
        skin = (ref[:, x, 3] > 0) & ~tee[:, x] & (rgb[:, x, 0] - rgb[:, x, 2] > 20)
        ends = [r for r in range(SEAM_FROM, SEAM_SLEEVE_MAX) if tee[r, x] and skin[r + 2 : r + 8].all()]
        if ends:
            seams[x] = ends[-1]
    # 겨드랑이 쪽 열은 소매가 몸판 흰 티와 이어져 밑단을 찾지 못한다: 가장 가까운 소매 열의 밑단 행을 쓴다
    for x in columns:
        if x not in seams and seams:
            nearest = min(seams, key=lambda c: abs(c - x))
            if abs(nearest - x) <= SEAM_BORROW:
                seams[x] = seams[nearest]
    return seams


def smooth_sleeve_line(image: Image.Image, legacy: Image.Image) -> Image.Image:
    """서기 맨몸 팔의 소매 밑단 줄과 그림자 띠를 지운다: 열마다 승인 소매 밑단 행([sleeve_seams]) 위 [SEAM_ABOVE]행 ~ 아래 [SEAM_BELOW]행의
    밝은 팔 피부를 위·아래 끝 피부 사이 직선 보간으로 바꾼다. 외곽선(빈 곳에서 [EDGE_PX] 안, 짙은 픽셀)은 건드리지 않는다."""
    array = _rgba(image).copy()
    alpha = array[..., 3]
    skin = (alpha >= 250) & ~_near_edge(alpha, EMPTY, EDGE_PX) & (array[..., :3].astype(int).mean(axis=2) >= 120)
    out = array.astype(np.float64)
    spans = {}
    for x, seam in sleeve_seams(legacy).items():
        top, far = seam - SEAM_ABOVE, seam + SEAM_BELOW
        if top < 0 or far >= array.shape[0] or not skin[top, x] or not skin[far, x]:
            continue
        # 아래 끝: 소매 그림자 띠가 끝나 아래팔 피부(밑단에서 SEAM_BELOW 아래 색)와 거의 같아지는 첫 행
        target = out[far, x, :3]
        bottom = next(
            (y for y in range(seam + 4, far) if skin[y, x] and np.abs(out[y, x, :3] - target).sum() <= SEAM_MATCH),
            far,
        )
        spans[x] = (seam, top, bottom - seam)
    for x, (seam, top, _) in spans.items():
        # 그림자 띠가 끝나는 깊이는 양옆 열의 가운데값으로 고른다(열마다 다르면 세로 줄무늬·모서리가 생겼다). 끝 색은 그 행의 실제 피부
        depth = int(np.median([spans[n][2] for n in range(x - SEAM_BLEND, x + SEAM_BLEND + 1) if n in spans]))
        bottom = min(seam + max(depth, SEAM_MIN_DEPTH), seam + SEAM_BELOW)
        if not skin[bottom, x]:
            continue
        rows = np.arange(top + 1, bottom)
        rows = rows[skin[rows, x]]
        t = ((rows - top) / (bottom - top))[:, None]
        out[rows, x, :3] = out[top, x, :3] * (1 - t) + out[bottom, x, :3] * t
    return Image.fromarray(out.round().clip(0, 255).astype(np.uint8), "RGBA")


def fill_pinholes(image: Image.Image) -> Image.Image:
    """몸 안에 갇힌 작은 투명 구멍([PINHOLE]픽셀 이하, 팔 안쪽 윤곽 옆 1px 틈 등)을 이웃 불투명 색으로 채운다. 옷 레이어의 부스러기를
    지우면 그 구멍이 드러났다."""
    from outfit_pipeline.build import _components

    array = _rgba(image).copy()
    empty = array[..., 3] < SOLID
    width = array.shape[1]
    for piece in _components(empty):
        ys, xs = np.divmod(np.array(piece), width)
        if (
            len(piece) > PINHOLE
            or ys.min() == 0
            or xs.min() == 0
            or ys.max() == array.shape[0] - 1
            or xs.max() == width - 1
        ):
            continue
        ring = np.zeros_like(empty)
        for y, x in zip(ys, xs, strict=True):
            ring[max(0, y - 1) : y + 2, max(0, x - 1) : x + 2] = True
        ring &= ~empty
        array[ys, xs] = np.median(array[ring], axis=0).astype(np.uint8)
    return Image.fromarray(array, "RGBA")


WAIST_OPEN = 3  # 하의 허리 겹침 띠에서 덩어리를 볼 때 먼저 깎아 내는 가는 털 폭(부스러기 규칙과 같은 3x3)
WAIST_ROOT = 330  # 하의 본체: 이 행 아래까지 이어진 덩어리
WAIST_BRIDGE = 10  # 허리 가운데 트인 틈으로 보고 메우는 최대 폭
WAIST_BRIDGE_ROWS = 345


def waist_tufts_mask(rgba: np.ndarray | Image.Image) -> np.ndarray:
    """하의 서기 레이어의 허리 겹침 띠(`spec.STAND_WAIST` 행)에서 하의 본체(y [WAIST_ROOT] 아래로 이어진 덩어리)와 떨어진 조각(그림 도구
    추출 찌꺼기). 가는 털로만 이어진 조각도 떨어진 것으로 본다. 상의 밑단이 짧으면 맨몸 위로 검은 얼룩·들쭉날쭉한 허리선으로 보였다(D-817)."""
    from outfit_pipeline.build import _components

    array = _rgba(rgba)
    _, y0, _, y1 = spec.STAND_WAIST
    band = np.zeros(array.shape[:2], bool)
    band[y0:y1] = True
    solid = array[..., 3] >= SOLID
    image = Image.fromarray((solid * 255).astype(np.uint8))
    opened = np.array(image.filter(ImageFilter.MinFilter(WAIST_OPEN)).filter(ImageFilter.MaxFilter(WAIST_OPEN))) > 0
    kept = solid & (opened | ~band)
    rooted = np.zeros_like(kept)
    width = kept.shape[1]
    for piece in _components(kept):
        if max(piece) // width >= WAIST_ROOT:
            rooted.reshape(-1)[piece] = True
    near = np.array(Image.fromarray((rooted * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(3))) > 0
    return band & (array[..., 3] > 0) & ~near


def trim_waist(image: Image.Image) -> tuple[Image.Image, int]:
    """[waist_tufts_mask]를 투명하게 지운다. 지운 픽셀 수도 돌려준다."""
    array = _rgba(image).copy()
    mask = waist_tufts_mask(array)
    array[mask] = 0
    return Image.fromarray(array, "RGBA"), int(mask.sum())


def _body_inside(body: Image.Image) -> np.ndarray:
    solid = _rgba(body)[..., 3] >= SOLID
    return solid & _thick(solid, 3)


WAIST_STEP = 3  # 허리 옆 가장자리가 한 행에 이보다 많이 바깥으로 뛰면 계단(홈)으로 본다
WAIST_SIDE_ROWS = (296, 345)  # 허리 옆 계단을 찾는 행 범위
WAIST_EDGE_AA = 2  # 이어 붙일 때 가장자리 밖으로 함께 옮기는 안티앨리어싱 폭


def square_waist_sides(image: Image.Image) -> tuple[Image.Image, int]:
    """하의 서기 허리 옆 계단을 없앤다(판정 round1 A): 허리를 맨몸 실루엣까지만 늘리면 하의 원래 허리 높이에서 옆 가장자리가 3~8px 바깥으로
    뛰어 밑단 옆에 홈과 짙은 획으로 보였다. 가장자리가 [WAIST_STEP]px 넘게 바깥으로 뛰는 행을 찾아 그 행의 옆 가장자리(외곽선·안티앨리어싱
    포함, 바깥 2px까지)를 위쪽 행(y 296부터)에 그대로 이어 붙인다. (레이어, 더한 픽셀 수)."""
    array = np.array(image.convert("RGBA"))
    solid = array[..., 3] >= 128
    y0, y1 = WAIST_SIDE_ROWS
    added = 0
    for sign in (1, -1):  # 1 = 왼쪽, -1 = 오른쪽

        def edge(y: int, sign: int = sign) -> int | None:
            xs = np.nonzero(solid[y])[0]
            if len(xs) == 0:
                return None
            return int(xs.min()) if sign == 1 else int(xs.max())

        step_row = None
        for y in range(y0 + 1, y1):
            a, b = edge(y - 1), edge(y)
            if a is not None and b is not None and (a - b) * sign > WAIST_STEP:
                step_row = y
                break
        if step_row is None:
            continue
        source = array[step_row]
        outer = edge(step_row)
        assert outer is not None
        start = outer - WAIST_EDGE_AA * sign  # 바깥 안티앨리어싱은 2px까지만(더 옅은 끝은 팔 가장자리에 겹친다)
        for y in range(y0, step_row):
            inner = edge(y)
            if inner is None or (inner - outer) * sign <= 0:
                continue
            xs = range(start, inner) if sign == 1 else range(inner + 1, start + 1)
            for x in xs:
                if source[x, 3] > array[y, x, 3]:
                    array[y, x] = source[x]
                    added += 1
    return Image.fromarray(array, "RGBA"), added


def extend_waist(image: Image.Image, body: Image.Image) -> tuple[Image.Image, int]:
    """하의 서기 레이어의 허리를 허리 겹침 띠 위(y 296)까지 늘린다(상의 밑단 밑으로 들어가게, D-790·D-817). 열마다 하의가
    5행 이어지기 시작하는 행 아래 4~8행의 색(양옆 10열 가운데값)으로, 맨몸 실루엣 안만 채운다. 하의가 그 띠에서 시작하지 않는 열은 그대로.
    밑단이 높은 상의와 짧은 허리 사이로 맨몸(예전엔 맨몸의 짙은 허리 밴드)이 띠로 보이지 않게 한다. (레이어, 채운 픽셀 수)."""
    array = _rgba(image).astype(np.float64)
    # 맨몸 실루엣 안: 굵은 실루엣(3x3 열기)만(외곽선 밖 낱 픽셀까지 늘리면 떨어진 점이 생겼다)
    inside = _body_inside(body)
    starts = waist_starts(_rgba(image))
    y0 = spec.STAND_WAIST[1]
    reference = {x: np.median(array[r + 4 : r + 9, x, :3], axis=0) for x, r in starts.items()}
    added = 0
    for x, r in starts.items():
        near = [reference[n] for n in range(x - 10, x + 11) if n in reference]
        color = np.median(np.array(near), axis=0)
        for y in range(y0, r):
            if not inside[y, x] or array[y, x, 3] >= 255:
                continue
            cover = array[y, x, 3] / 255
            array[y, x, :3] = array[y, x, :3] * cover + color * (1 - cover)
            array[y, x, 3] = 255
            added += 1
    return Image.fromarray(array.round().clip(0, 255).astype(np.uint8), "RGBA"), added


def waist_starts(rgba: np.ndarray) -> dict[int, int]:
    """허리 겹침 띠의 열마다 하의가 시작하는 행: 하의가 5행 이어지는 곳이 y 296~329에 있는 열에서 처음 그런 곳부터 위로 이어진 천의 첫
    행. 그런 열 사이 [WAIST_BRIDGE]px 안의 틈(허리 가운데 끈 사이로 트인 곳)도 넣는다: 그 열은 처음 불투명한 행(y 345
    까지, 없으면 345). 하의가 없는 열은 빠진다."""
    x0, y0, x1, _ = spec.STAND_WAIST
    solid = rgba[..., 3] >= SOLID
    starts = {}
    for x in range(x0, x1):
        run = next((r for r in range(y0, WAIST_ROOT) if solid[r : r + 5, x].all()), None)
        if run is not None:
            # 그 5행에서 위로 이어진 천의 첫 행(떨어진 점에서 시작하지 않는다)
            while run > y0 and solid[run - 1, x]:
                run -= 1
            starts[x] = run
    columns = sorted(starts)
    for left, right in pairwise(columns):
        if 1 < right - left <= WAIST_BRIDGE:
            for x in range(left + 1, right):
                rows = np.flatnonzero(solid[y0:WAIST_BRIDGE_ROWS, x])
                starts[x] = y0 + int(rows[0]) if len(rows) else WAIST_BRIDGE_ROWS
    return starts


def waist_gap(image: Image.Image, body: Image.Image) -> int:
    """하의가 띠에서 시작하는 열에서 y 296과 그 시작 행 사이, 맨몸 실루엣 안인데 하의가 덮지 않은 픽셀 수(검사 13번)."""
    array = _rgba(image)
    # 맨몸 실루엣 안: 굵은 실루엣(3x3 열기)만(외곽선 밖 낱 픽셀까지 늘리면 떨어진 점이 생겼다)
    inside = _body_inside(body)
    y0 = spec.STAND_WAIST[1]
    return sum(int((inside[y0:r, x] & (array[y0:r, x, 3] < SOLID)).sum()) for x, r in waist_starts(array).items())


SEAM = 4  # 하의 레이어에서 위·아래가 모두 천인 이 높이 이하 세로 틈은 그림 도구 추출 틈(밑단 안감 사이)으로 보고 메운다


def seam_mask(rgba: np.ndarray | Image.Image) -> np.ndarray:
    """하의 레이어의 세로 [SEAM]px 이하 틈: 열마다 불투명 천 사이에 낀 반투명·투명 픽셀 줄. 예전엔 맨몸 바탕의 회색 속바지가 메워
    보이지 않다가, 맨몸이 피부가 되자 밑단에 살색 점선으로 보였다(D-817)."""
    array = _rgba(rgba)
    solid = array[..., 3] >= SOLID
    height = solid.shape[0]
    mask = np.zeros_like(solid)
    for gap in range(1, SEAM + 1):
        for start in range(1, height - gap):
            run = ~solid[start : start + gap].any(axis=0)
            mask[start : start + gap] |= solid[start - 1] & solid[start + gap] & run
    # 메워도 부스러기(가장자리 1~2px 홈)로 남을 곳은 틈으로 치지 않는다
    return mask & _thick(solid | mask, 3)


def close_seams(image: Image.Image) -> tuple[Image.Image, int]:
    """[seam_mask]를 위·아래 천 색의 세로 보간으로 메운다. (레이어, 메운 픽셀 수)."""
    array = _rgba(image).astype(np.float64)
    mask = seam_mask(_rgba(image))
    solid = array[..., 3] >= SOLID
    ys, xs = np.nonzero(mask)
    for y, x in zip(ys, xs, strict=True):
        above = y - 1
        while not solid[above, x]:
            above -= 1
        below = y + 1
        while not solid[below, x]:
            below += 1
        t = (y - above) / (below - above)
        array[y, x] = array[above, x] * (1 - t) + array[below, x] * t
        array[y, x, 3] = 255
    return Image.fromarray(array.round().clip(0, 255).astype(np.uint8), "RGBA"), len(ys)


GAP_ZONES = ((140, 150, 166, 300), (346, 150, 372, 300))  # 서기 팔과 몸통 사이 틈(겨드랑이~허리 옆)
GAP_PIECE = 200  # 틈 안에 따로 떨어진 불투명 덩어리(4방향, 알파 250 이상)가 이보다 작으면 조각


def drop_gap_fragments(image: Image.Image) -> Image.Image:
    """서기 맨몸의 팔과 몸통 사이 틈([GAP_ZONES]) 안에 따로 떨어진 작은 불투명 덩어리(옛 반팔·몸통 가장자리의 짙은 선 조각, 팔 외곽선과
    반투명 1px로만 닿는다)를 지운다. 팔·몸통 본체는 큰 덩어리라 남는다(D-816)."""
    from outfit_pipeline.build import _components

    array = _rgba(image).copy()
    zone = np.zeros(array.shape[:2], bool)
    for box in GAP_ZONES:
        zone |= _zone(array.shape, box)
    flat_zone = zone.reshape(-1)
    flat = array.reshape(-1, 4)
    for piece in _components(array[..., 3] >= 250):
        if len(piece) < GAP_PIECE and flat_zone[piece].all():
            flat[piece] = 0
    return Image.fromarray(array, "RGBA")


SIDE_CLOSE = 7  # 하의 엉덩이 옆 칸·허리에서 이 폭으로 닫아 메우는 트임(밑단 옆 안감 사이, 허리 옆 구멍)
SIDE_WAIST = (100, 296, 412, 330)  # 허리(겹침 띠와 허리 밴드 아래 몇 행)


def side_gap_mask(image: Image.Image, body: Image.Image) -> np.ndarray:
    """하의 서기 레이어의 엉덩이 옆 칸(`spec.STAND_HIP_CUTS`)과 허리([SIDE_WAIST])에서 [SIDE_CLOSE]px로 닫으면 메워지는 맨몸 실루엣 안
    빈 곳(밑단 옆 트임, 허리 옆 구멍). 그 밑 맨몸이 배경(자르기)이나 살색 조각으로 비쳤다(D-817)."""
    array = _rgba(image)
    solid = array[..., 3] >= SOLID
    mask = Image.fromarray((solid * 255).astype(np.uint8))
    closed = np.array(mask.filter(ImageFilter.MaxFilter(SIDE_CLOSE)).filter(ImageFilter.MinFilter(SIDE_CLOSE))) > 0
    zone = np.zeros(solid.shape, bool)
    for box in (*spec.STAND_HIP_CUTS, SIDE_WAIST):
        zone |= _zone(array.shape, box)
    return zone & closed & ~solid & (_rgba(body)[..., 3] >= SOLID)


def close_side_gaps(image: Image.Image, body: Image.Image) -> tuple[Image.Image, int]:
    """[side_gap_mask]를 이웃 천 색으로 바깥에서 안으로 메운다. (레이어, 메운 픽셀 수)."""
    array = _rgba(image).astype(np.float64)
    todo = side_gap_mask(image, body)
    total = int(todo.sum())
    height, width = todo.shape
    while todo.any():
        solid = array[..., 3] >= SOLID
        done = []
        for y, x in zip(*np.nonzero(todo), strict=True):
            near = [
                array[ny, nx]
                for ny, nx in ((y - 1, x), (y + 1, x), (y, x - 1), (y, x + 1))
                if 0 <= ny < height and 0 <= nx < width and solid[ny, nx]
            ]
            if near:
                done.append((y, x, np.mean(near, axis=0)))
        if not done:
            break
        for y, x, color in done:
            array[y, x] = color
            array[y, x, 3] = 255
            todo[y, x] = False
    return Image.fromarray(array.round().clip(0, 255).astype(np.uint8), "RGBA"), total


def _components8(mask: np.ndarray) -> list[np.ndarray]:
    """8방향으로 이은 덩어리(평탄화한 픽셀 번호 배열)."""
    from collections import deque

    height, width = mask.shape
    flat = mask.reshape(-1)
    seen = np.zeros(flat.size, bool)
    pieces = []
    for start in np.flatnonzero(flat):
        if seen[start]:
            continue
        seen[start] = True
        piece = [int(start)]
        queue = deque(piece)
        while queue:
            i = queue.popleft()
            y, x = divmod(i, width)
            for dy in (-1, 0, 1):
                for dx in (-1, 0, 1):
                    ny, nx = y + dy, x + dx
                    if 0 <= ny < height and 0 <= nx < width:
                        n = ny * width + nx
                        if flat[n] and not seen[n]:
                            seen[n] = True
                            piece.append(n)
                            queue.append(n)
        pieces.append(np.array(piece))
    return pieces


def dust_mask(rgba: np.ndarray | Image.Image) -> np.ndarray:
    """맨몸 바탕의 고립 점: 알파가 있는 픽셀을 8방향으로 이은 덩어리 가운데 알파 128 이상이 하나도 없거나(거의 투명한 먼지·떨어진 유령
    외곽선) [DUST_PIECE]픽셀보다 작은 것. 외곽선에 붙은 안티에일리어싱은 몸 덩어리에 이어져 있어 남는다(D-816 검수)."""
    array = _rgba(rgba)
    alpha = array[..., 3]
    out = np.zeros(alpha.shape, bool)
    flat_out = out.reshape(-1)
    flat_alpha = alpha.reshape(-1)
    for piece in _components8(alpha > 0):
        if len(piece) < DUST_PIECE or flat_alpha[piece].max() < SOLID:
            flat_out[piece] = True
    return out


WISP_EMPTY = 24  # 획 바깥(투명)으로 보는 알파 상한
WISP_INK = 40  # 획·틈을 가르는 알파
WISP_MAX = 3  # 획·틈의 최대 폭(px)
WISP_SOLID = 3  # 틈 너머 실루엣으로 보는 알파 128 이상 연속 폭


def parallel_wisps(rgba: np.ndarray) -> np.ndarray:
    """외곽선 바깥에 나란히 떠 있는 가는 획(판정 round1 E: 손 외곽에 털처럼 삐친 선). 가로·세로 줄마다 바깥에서 안으로
    투명(알파 < [WISP_EMPTY]) → 획(알파 ≥ [WISP_INK], 1~[WISP_MAX]px) → 틈(알파 < [WISP_INK], 1~[WISP_MAX]px) → 실루엣(그다음 [WISP_SOLID]+1px 가운데
    [WISP_SOLID]px 이상이 알파 128 이상)인 곳의 획. 외곽선과 그 안티앨리어싱은 실루엣 쪽이라 남는다."""
    alpha = rgba[..., 3].astype(int)
    mask = np.zeros(alpha.shape, bool)

    def scan(line: np.ndarray) -> np.ndarray:
        out = np.zeros(len(line), bool)
        n = len(line)
        for i in range(1, n):
            if line[i - 1] >= WISP_EMPTY or line[i] < WISP_INK:
                continue
            j = i
            while j < n and line[j] >= WISP_INK and j - i < WISP_MAX + 1:
                j += 1
            if j - i > WISP_MAX or j >= n:
                continue
            k = j
            while k < n and line[k] < WISP_INK and k - j < WISP_MAX + 1:
                k += 1
            ahead = line[k : k + WISP_SOLID + 1]  # 외곽선 안티앨리어싱 1px을 건너 실루엣
            if 1 <= k - j <= WISP_MAX and len(ahead) > WISP_SOLID and int((ahead >= 128).sum()) >= WISP_SOLID:
                out[i:j] = True
        return out

    for y in range(alpha.shape[0]):
        if alpha[y].any():
            mask[y] |= scan(alpha[y]) | scan(alpha[y][::-1])[::-1]
    for x in range(alpha.shape[1]):
        if alpha[:, x].any():
            mask[:, x] |= scan(alpha[:, x]) | scan(alpha[:, x][::-1])[::-1]
    return mask


def wisp_strokes(rgba: np.ndarray) -> np.ndarray:
    """지울 손 외곽 털 전체: [parallel_wisps]를 지우면 그 뒤에 있던 획이 새로 드러날 수 있어 더 없을 때까지(최대 3번) 지우고, 그 뒤
    고립 점이 된 조각([dust_mask])까지 더한 것."""
    array = rgba.copy()
    removed = np.zeros(array.shape[:2], bool)
    for _ in range(3):
        mask = parallel_wisps(array)
        if not mask.any():
            break
        array[mask] = 0
        removed |= mask
    if removed.any():
        dust = dust_mask(array) & ~dust_mask(rgba)
        removed |= dust
    return removed


def drop_dust(image: Image.Image) -> tuple[Image.Image, int]:
    array = _rgba(image).copy()
    mask = dust_mask(array)
    array[mask] = 0
    return Image.fromarray(array, "RGBA"), int(mask.sum())


def elbow_tail_mask(rgba: np.ndarray | Image.Image) -> np.ndarray:
    """서기 맨몸 팔꿈치 높이([ELBOW_WINDOW])에서 팔 실루엣 밖으로 뻗은 가는 꼬리(승인 반팔 소매 모서리 외곽선 조각): 그 창 안의 3x3 굵은
    실루엣 밖 픽셀. 외곽선 자체는 굵어서 남는다."""
    array = _rgba(rgba)
    tail = _thin_mask(array)
    window = np.zeros(tail.shape, bool)
    y0, y1 = ELBOW_WINDOW
    window[y0:y1, : spec.STAND_LEFT_ARM[2]] = True
    window[y0:y1, spec.STAND_RIGHT_ARM[0] :] = True
    return tail & window


def body_outline(rgba: np.ndarray | Image.Image) -> np.ndarray:
    """맨몸 외곽선: 빈 곳에서 [EDGE_PX] 안의 짙은(밝기 [OUTLINE_DARK] 미만) 불투명 픽셀."""
    array = _rgba(rgba)
    dark = array[..., :3].astype(int).mean(axis=2) < OUTLINE_DARK
    return (array[..., 3] >= SOLID) & dark & _near_outside(array[..., 3], EDGE_PX)


def outline_reference(original: Image.Image, thin: bool = False) -> np.ndarray:
    """정리 전 맨몸(승인 원본)에서 지켜야 할 외곽선: [body_outline]에서 고립 점·팔꿈치 꼬리·팔과 몸통 사이 떨어진 조각, 서기는 외곽선 밖 나란한 가는 획을 뺀 것. [thin]이면
    (달리기 슬롯) 3x3 굵은 실루엣 밖 털도 뺀다."""
    array = _rgba(original)
    keep = body_outline(array) & ~dust_mask(array) & ~elbow_tail_mask(array)
    if thin:
        keep &= ~_thin_mask(array)
    else:
        keep &= ~wisp_strokes(array)  # 서기 손 외곽 털(판정 round1 E)은 지키지 않는다
    fragments = np.array(drop_gap_fragments(Image.fromarray(array, "RGBA")))[..., 3] == 0
    return keep & ~(fragments & (array[..., 3] > 0))


def outline_breaks(image: Image.Image | np.ndarray, reference: np.ndarray) -> np.ndarray:
    """[reference] 외곽선 가운데 지금 그림에서 비거나 밝아진 픽셀(끊긴 곳). 밝기 140 미만 불투명이면 이어진 것으로 본다."""
    array = _rgba(image)
    kept = (array[..., 3] >= SOLID) & (array[..., :3].astype(int).mean(axis=2) < 140)
    return reference & ~kept


LEG_OUTLINE = (70, 48, 40)  # 달리기 다리 하의 외곽선 색(맨몸 다리 외곽선과 같은 짙은 갈색)
LEG_OUTLINE_PX = 3  # 배경과 맞닿은 하의 가장자리에 칠하는 외곽선 폭(맨몸 다리 외곽선 약 4px과 비슷하게)
LEG_INK = 75  # 이보다 어두운 가장자리 픽셀은 이미 외곽선으로 보고 칠하지 않는다
LEG_DARK = 110  # 이보다 어두운 맨몸 픽셀은 맨몸 외곽선으로 보고 덮지 않는다(하의 외곽선 노릇을 한다)
LEG_HEM_SKIP = 6  # 하의 밑단 위 이 행 수는 덮지 않는다(밑단 아래 다리와 이어지는 곳)


def cover_run_leg(layer: Image.Image, body: Image.Image) -> tuple[Image.Image, int]:
    """긴 하의 달리기 다리 레이어(판정 round1 C): 하의가 있는 행에서 맨몸 다리 피부가 하의 밖으로 나온 곳(레깅스 무릎 겹침 사이 피부 쐐기,
    뒤 허벅지)을 가까운 하의 색으로 덮고(맨몸 외곽선은 덮지 않아 하의 외곽선이 된다), 배경과 맞닿은 하의 가장자리 [LEG_OUTLINE_PX]px은
    짙은 외곽선 색으로 칠한다(wide·jogger는 외곽선이 옅어 바지 전체에 외곽선이 없어 보였다). (레이어, 바꾼 픽셀 수)."""
    array = _rgba(layer).astype(np.float64)
    skin = _rgba(body)
    garment = array[..., 3] >= 128
    rows = np.nonzero(garment.sum(axis=1) >= 40)[0]  # 천이 넓게 있는 행(가는 외곽선 줄은 빼고)
    if len(rows) == 0:
        return layer, 0
    in_rows = np.zeros(garment.shape, bool)
    in_rows[rows.min() : rows.max() - LEG_HEM_SKIP] = True
    body_solid = skin[..., 3] >= 128
    body_dark = skin[..., :3].astype(int).mean(axis=-1) < LEG_DARK
    todo = body_solid & ~body_dark & ~garment & in_rows
    changed = int(todo.sum())
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
    covered = array[..., 3] >= 128
    background = ~covered & ~(body_solid & in_rows)
    # 텍스처 왼쪽·오른쪽 가장자리 너머도 배경이다(바지가 텍스처 끝 가까이 닿아 메시 가장자리에서 외곽선 없이 잘렸다)
    padded = np.pad(background, ((0, 0), (LEG_OUTLINE_PX, LEG_OUTLINE_PX)), constant_values=True)
    grown = Image.fromarray((padded * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(2 * LEG_OUTLINE_PX + 1))
    near = (np.array(grown) > 0)[:, LEG_OUTLINE_PX:-LEG_OUTLINE_PX]
    edge = covered & near & in_rows & (array[..., :3].mean(axis=-1) >= LEG_INK)
    array[edge, :3] = array[edge, :3] * 0.2 + np.array(LEG_OUTLINE) * 0.8
    changed += int(edge.sum())
    return Image.fromarray(array.round().clip(0, 255).astype(np.uint8), "RGBA"), changed


TORSO_GAP_ALPHA = 64  # 팔 띠에서 팔과 몸통 조각을 가르는 틈의 알파 상한
TORSO_GAP_MAX = 8  # 상의 옆 가장자리와 몸통 옆 외곽선 사이를 메우는 최대 폭


def meet_torso_outline(layer: Image.Image, body: Image.Image) -> tuple[Image.Image, int]:
    """서기 상의 레이어(판정 round1 A): 팔 띠(x < 145·x ≥ 367, y 185~301) 행마다 맨몸에서 바깥부터 처음 덩어리(팔) 뒤 틈 너머 조각(몸통 옆
    외곽선)을 찾고, 상의 옆 가장자리와 그 조각 사이 1~[TORSO_GAP_MAX]px을 상의 가장자리 안쪽 천 색으로 메운다. 민소매는 팔 띠를
    자르지 않아 몸통 옆 외곽선이 상의와 떨어져 밑단 옆에 짙은 세로 선으로 매달렸다. 메우면 그 외곽선이 상의 외곽선이 된다.
    (레이어, 메운 픽셀 수)."""
    array = _rgba(layer).copy()
    alpha = _rgba(body)[..., 3] >= TORSO_GAP_ALPHA
    solid = array[..., 3] >= SOLID
    added = 0
    for box, outward in ((spec.STAND_LEFT_ARM, True), (spec.STAND_RIGHT_ARM, False)):
        x0, y0, x1, y1 = box
        for y in range(y0, y1):
            xs = range(x0, x1) if outward else range(x1 - 1, x0 - 1, -1)
            state, sliver = 0, None  # 0 팔 앞, 1 팔, 2 틈, 3 몸통 조각
            for x in xs:
                on = alpha[y, x]
                if state == 0 and on:
                    state = 1
                elif state == 1 and not on:
                    state = 2
                elif state == 2 and on:
                    state = 3
                elif state == 3 and not on:
                    sliver = x  # 조각 바로 바깥(상의 쪽) 첫 픽셀
                    break
            if sliver is None:
                continue
            step = 1 if outward else -1
            edge = None
            for d in range(TORSO_GAP_MAX + 1):
                x = sliver + d * step
                if 0 <= x < solid.shape[1] and solid[y, x]:
                    edge = x
                    break
            if edge is None or edge == sliver:
                continue
            fabric = array[y, min(max(edge + 2 * step, 0), solid.shape[1] - 1)].copy()
            for x in range(sliver, edge, step):
                array[y, x] = fabric
                array[y, x, 3] = 255
                added += 1
    return Image.fromarray(array, "RGBA"), added
