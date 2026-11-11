"""맨몸 바탕·옷 레이어 정리([임시:D-성-184] 민소매 팔 선, [임시:D-성-185] 허리 띠·반바지 가장자리).

riv 메시는 체형과 숨쉬기 동작에서 팔과 몸통 사이, 엉덩이 옆을 늘린다. 그래서 텍스처의 작은 흠이 화면에서 선으로 커진다.

- 부스러기(`drop_strays`): 굵은 실루엣(알파 128 이상을 3x3로 열고 1px 넓힌 곳) 밖의 픽셀. 실루엣에서 뻗은 1~2px 털(소매 모서리
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
EDGE_PX = 2  # 실루엣 가장자리(빈 곳까지 이 거리 안)
EMPTY = 16  # 이보다 옅으면 빈 곳(속바지 밑단 선 안쪽의 반투명 픽셀은 빈 곳이 아니다)
LEG_REACH = 40  # 허리·엉덩이 칸 아래에서 다리 피부를 찾는 행 수
PINHOLE = 8  # 몸 안 투명 구멍을 채우는 최대 크기
LEG_SMOOTH = 21  # 허리·엉덩이 피부색을 가로로 고르는 폭
DARK = 120  # 허리·엉덩이 칸 안쪽에서 이보다 어두우면 옛 반바지 선
SLEEVE_ROWS = (145, 201)  # 서기 팔에서 소매 밑단 줄과 그림자 띠가 있는 행(위 끝 행·아래 끝 행은 보간 기준)
SLEEVE_REACH = 10  # 기준 행이 팔 안쪽이 아니면 위·아래로 이만큼까지 찾는다


def _rgba(image: Image.Image | np.ndarray) -> np.ndarray:
    return np.array(image.convert("RGBA")) if isinstance(image, Image.Image) else image


def _thick(solid: np.ndarray, size: int) -> np.ndarray:
    """[solid]를 size x size로 열고(깎았다 넓힘) 1px 더 넓힌 굵은 실루엣."""
    image = Image.fromarray((solid * 255).astype(np.uint8))
    opened = image.filter(ImageFilter.MinFilter(size)).filter(ImageFilter.MaxFilter(size))
    return np.array(opened.filter(ImageFilter.MaxFilter(3))) > 0


def stray_mask(rgba: np.ndarray, garment: bool = False) -> np.ndarray:
    """굵은 실루엣(3x3) 밖의 알파가 있는 픽셀(부스러기·털·먼지). [garment]면 옷 레이어에 남은 갈색 맨몸 외곽선 실오라기(5x5 굵은 실루엣 밖 불투명 픽셀,
    R > G + 10, R > B + 20, 밝기 [WISP_DARK] 미만)도 센다. 맨몸 바탕의 갈색 외곽선은 몸의 일부라 빼고 본다."""
    solid = rgba[..., 3] >= SOLID
    mask = (rgba[..., 3] > 0) & ~_thick(solid, 3)
    if garment:
        rgb = rgba[..., :3].astype(int)
        brown = (rgb[..., 0] > rgb[..., 1] + 10) & (rgb[..., 0] > rgb[..., 2] + 20) & (rgb.mean(axis=2) < WISP_DARK)
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


def count_strays(image: Image.Image, garment: bool = False) -> int:
    return int(stray_mask(_rgba(image), garment).sum())


def drop_strays(image: Image.Image, garment: bool = False) -> tuple[Image.Image, int]:
    """부스러기를 투명하게 지운다(지우면 새 털이 드러날 수 있어 더 없을 때까지). 지운 픽셀 수도 돌려준다."""
    array = _rgba(image).copy()
    total = 0
    while (mask := stray_mask(array, garment)).any():
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


def underwear_mask(rgba: np.ndarray | Image.Image) -> np.ndarray:
    """서기 맨몸의 허리·엉덩이 칸 안 옷 픽셀: 실루엣 가장자리([EDGE_PX]) 안쪽의 무채색(속옷 회색·짙은 허리 밴드)과 짙은 선(옛 반바지 밑단·주름
    선, 밝기 [DARK] 미만). 가장자리의 몸 외곽선은 빠진다."""
    array = _rgba(rgba)
    rgb = array[..., :3].astype(int)
    dark = rgb.mean(axis=2) < DARK
    inner = ~_near_outside(array[..., 3], EDGE_PX)
    return (array[..., 3] >= SOLID) & (_neutral(array) | dark) & inner & _zone(array.shape, spec.STAND_HIPS)


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


def bare_hips(image: Image.Image) -> tuple[Image.Image, Image.Image]:
    """서기 맨몸의 허리·엉덩이 속옷을 맨몸으로 바꾼다. (맨몸, 기본 하의로 옮길 허리 밴드 레이어)."""
    array = _rgba(image).copy()
    zone = _zone(array.shape, spec.STAND_HIPS)
    near = _near_outside(array[..., 3], EDGE_PX)
    under = underwear_mask(array)
    band = np.zeros_like(array)
    waist = (array[..., 3] >= SOLID) & _neutral(array) & zone & _zone(array.shape, spec.STAND_WAIST)
    band[waist] = array[waist]
    # 몸 안에 갇힌 반투명 구멍(옛 밑단 선 사이)도 피부로 채운다
    holes = zone & (array[..., 3] < SOLID) & ~_near_outside(array[..., 3], 0)
    inner = under | holes
    colors = _leg_colors(array)
    x0 = spec.STAND_HIPS[0]
    ys, xs = np.nonzero(inner)
    array[ys, xs, :3] = colors[xs - x0]
    array[ys, xs, 3] = 255
    # 가장자리의 무채색(옛 반바지 외곽선)은 몸 외곽선 갈색으로
    edge = zone & near & (array[..., 3] >= SOLID) & _neutral(array)
    array[edge, :3] = BODY_OUTLINE
    return Image.fromarray(array, "RGBA"), Image.fromarray(band, "RGBA")


def smooth_sleeve_line(image: Image.Image) -> Image.Image:
    """서기 맨몸 팔(소매 높이)의 가로 줄을 지운다: 팔 안쪽 픽셀을 열마다 위(소매 높이 위 첫 안쪽 행)·아래(`SLEEVE_ROWS` 끝) 피부 사이 직선
    보간으로. 위쪽 기준이 없는 열(팔꿈치 바깥)은 아래 피부색으로 채운다."""
    array = _rgba(image).copy()
    alpha = array[..., 3]
    inside = (alpha >= 250) & ~_near_edge(alpha, 250, 3)
    top, bottom = SLEEVE_ROWS
    columns = [*range(spec.STAND_LEFT_ARM[0], spec.STAND_LEFT_ARM[2]), *range(spec.STAND_RIGHT_ARM[0], array.shape[1])]
    out = array.astype(np.float64)
    for x in columns:
        below = next((y for y in range(bottom, bottom + SLEEVE_REACH) if inside[y, x]), None)
        if below is None:
            continue
        above = next((y for y in range(top - SLEEVE_REACH, top + SLEEVE_REACH) if inside[y, x]), None)
        start = above if above is not None else top - SLEEVE_REACH
        rows = np.arange(start + 1, below)
        rows = rows[inside[rows, x]]
        if not len(rows):
            continue
        if above is None:
            out[rows, x, :3] = out[below, x, :3]
            continue
        t = ((rows - above) / (below - above))[:, None]
        out[rows, x, :3] = out[above, x, :3] * (1 - t) + out[below, x, :3] * t
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
    추출 찌꺼기). 가는 털로만 이어진 조각도 떨어진 것으로 본다. 상의 밑단이 짧으면 맨몸 위로 검은 얼룩·들쭉날쭉한 허리선으로 보였다([임시:D-성-185])."""
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


def extend_waist(image: Image.Image, body: Image.Image) -> tuple[Image.Image, int]:
    """하의 서기 레이어의 허리를 허리 겹침 띠 위(y 296)까지 늘린다(상의 밑단 밑으로 들어가게, [임시:D-성-158]·[임시:D-성-185]). 열마다 하의가
    5행 이어지기 시작하는 행 아래 4~8행의 색(양옆 10열 가운데값)으로, 맨몸 실루엣 안만 채운다. 하의가 그 띠에서 시작하지 않는 열은 그대로.
    밑단이 높은 상의와 짧은 허리 사이로 맨몸(예전엔 맨몸의 짙은 허리 밴드)이 띠로 보이지 않게 한다. (레이어, 채운 픽셀 수)."""
    array = _rgba(image).astype(np.float64)
    inside = _rgba(body)[..., 3] >= SOLID
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
    """허리 겹침 띠의 열마다 하의가 시작하는 행: 하의가 5행 이어지는 곳이 y 296~329에 있는 열에서 처음 불투명한 행(끈 고리 안쪽 같은 옷
    안의 빈 곳은 건너뛴다). 그런 열 사이 [WAIST_BRIDGE]px 안의 틈(허리 가운데 끈 사이로 트인 곳)도 넣는다: 그 열은 처음 불투명한 행(y 345
    까지, 없으면 345). 하의가 없는 열은 빠진다."""
    x0, y0, x1, _ = spec.STAND_WAIST
    solid = rgba[..., 3] >= SOLID
    starts = {}
    for x in range(x0, x1):
        if any(solid[r : r + 5, x].all() for r in range(y0, WAIST_ROOT)):
            starts[x] = y0 + int(np.argmax(solid[y0:WAIST_ROOT, x]))
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
    inside = _rgba(body)[..., 3] >= SOLID
    y0 = spec.STAND_WAIST[1]
    return sum(int((inside[y0:r, x] & (array[y0:r, x, 3] < SOLID)).sum()) for x, r in waist_starts(array).items())


SEAM = 4  # 하의 레이어에서 위·아래가 모두 천인 이 높이 이하 세로 틈은 그림 도구 추출 틈(밑단 안감 사이)으로 보고 메운다


def seam_mask(rgba: np.ndarray | Image.Image) -> np.ndarray:
    """하의 레이어의 세로 [SEAM]px 이하 틈: 열마다 불투명 천 사이에 낀 반투명·투명 픽셀 줄. 예전엔 맨몸 바탕의 회색 속바지가 메워
    보이지 않다가, 맨몸이 피부가 되자 밑단에 살색 점선으로 보였다([임시:D-성-185])."""
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
