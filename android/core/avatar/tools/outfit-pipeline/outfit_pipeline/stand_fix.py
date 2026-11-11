"""서기 레이어 마무리(`outfit-pipeline stand-fix`, 판정 round3): 이미 만든 묶음에 차례로 건다. 다시 걸어도 더 바뀌지 않는다.

1. 상의 자르기 칸(body/{g}_stand_cut)을 지금 규칙(`build.cut_area`, 칼라 옆 칸 포함)으로 다시 쓴다.
2. 긴소매 상의 서기 레이어의 칼라·후드 안 빈 곳을 안감 색으로 메운다(`holes.line_collar`).
3. 반팔·민소매 상의 서기 레이어의 몸통 옆 외곽선 밖 밝은 점을 외곽선 색으로 칠하고([solid_side_outline]), 민소매는 몸통 옆 맨살 띠와
   밑단 아래 맨살을 덮는다([close_side_skin]).
4. 신발 서기 레이어의 외곽선 밖 회색 띠를 지우고(`feet.drop_halo`) 맨발 실루엣을 덮게 한다(`feet.cover_feet`).
2·4 뒤에 새로 갇힌 좁은 틈은 검사 16번 규칙으로 메운다(`build.fill_layer_holes`).
바뀐 묶음은 상점 그림도 다시 만든다.
"""

from __future__ import annotations

from pathlib import Path

import numpy as np
from PIL import Image

from outfit_pipeline import spec

COLLAR_KEYS = ("top_finisher_tee", "top_winter_hoodie", "top_dawn_windbreaker", "top_cherry_vest")
PASSES = 4
SIDE_ROWS = (spec.STAND_LEFT_ARM[1], spec.STAND_LEFT_ARM[3])  # 반팔·민소매 소매 끝 아래 몸통 옆(겨드랑이~허리)
SIDE_WINDOW = 6  # 몸통 옆 가장자리에서 안쪽으로 보는 폭
SIDE_DARK = 110  # 이보다 어두운 상의 픽셀은 옆 외곽선
SIDE_LIGHT = 60  # 외곽선보다 이만큼 밝으면 외곽선 밖에 뜬 밝은 점


def solid_side_outline(layer: Image.Image, _body: Image.Image | None = None) -> tuple[Image.Image, int]:
    """반팔·민소매 서기 상의의 몸통 옆 외곽선 밖에 박힌 밝은 점을 외곽선 색으로 칠한다(판정 round3: base s100 겨드랑이 아래 사다리 획).
    행마다(소매 끝 아래 몸통 옆) 바깥에서 처음 만나는 상의 가장자리 [SIDE_WINDOW]px 안에서, 안쪽의 짙은 외곽선 픽셀보다 바깥에 있는 밝은
    픽셀(생성 그림 추출에서 외곽선 밖에 남은 천 점)을 그 외곽선 색으로 바꾼다. 체형 100에서 몸통 옆이 늘면 짙은 외곽선 사이 흰 점이 가로 줄
    무늬(사다리)로 보였다. (레이어, 바꾼 픽셀 수)."""
    array = np.array(layer.convert("RGBA"))
    solid = array[..., 3] >= 128
    lum = array[..., :3].astype(int).mean(axis=-1)
    width = array.shape[1]
    changed = 0
    for y in range(*SIDE_ROWS):
        for xs in (range(0, width // 2), range(width - 1, width // 2 - 1, -1)):
            xs = list(xs)
            edge = next((i for i, x in enumerate(xs) if solid[y, x]), None)
            if edge is None:
                continue
            window = xs[edge : edge + SIDE_WINDOW]
            dark = [i for i, x in enumerate(window) if solid[y, x] and lum[y, x] < SIDE_DARK]
            if not dark:
                continue
            ink = array[y, window[dark[0]]].copy()
            for x in window[: dark[-1]]:
                if solid[y, x] and lum[y, x] >= lum[y, window[dark[0]]] + SIDE_LIGHT:
                    array[y, x, :3] = ink[:3]
                    changed += 1
    return Image.fromarray(array, "RGBA"), changed


def fix_stand(outfits_dir: Path, raw_dir: Path, masks_dir: Path) -> dict[str, int]:
    """[outfits_dir]의 서기 레이어를 마무리한다. 메운 뒤 새로 갇힌 좁은 틈(목 구멍 아래 끝, 덮은 신발 옆)은 검사 16번 규칙으로 메운다
    (`build.fill_layer_holes`). 파일별 바뀐 픽셀 수."""
    from functools import partial

    from outfit_pipeline.body import drop_strays, wisp_layer
    from outfit_pipeline.build import cut_area, fill_layer_holes, make_shop
    from outfit_pipeline.feet import HALO_LIGHT, HALO_LIGHT_BY_KEY, cover_feet, drop_halo
    from outfit_pipeline.holes import line_collar

    changed: dict[str, int] = {}
    folder = outfits_dir / spec.BODY_FOLDER
    name = f"female_stand_cut{spec.EXTENSION}"
    area = cut_area(raw_dir, "stand")
    mask = Image.new("RGBA", area.size, (0, 0, 0, 0))
    mask.putalpha(area)
    before = np.array(Image.open(folder / name).convert("RGBA"))[..., 3] if (folder / name).is_file() else None
    if before is None or not np.array_equal(before, np.array(area)):
        spec.save_webp(mask, folder / name)
        changed[f"{spec.BODY_FOLDER}/{name}"] = (
            int((before != np.array(area)).sum()) if before is not None else area.size[0] * area.size[1]
        )
    body = Image.open(folder / f"female_stand{spec.EXTENSION}").convert("RGBA")
    short = [k for k in spec.LEGACY_KEYS["TOP"] if spec.default_male_top_surface(k) == 1]
    jobs = (
        [(key, line_collar) for key in COLLAR_KEYS]
        + [(key, solid_side_outline) for key in short]
        + [(key, close_side_skin) for key in SIDE_SKIN_KEYS]
        + [(key, partial(drop_halo, light=HALO_LIGHT_BY_KEY.get(key, HALO_LIGHT))) for key in spec.LEGACY_KEYS["SHOES"]]
        + [(key, cover_feet) for key in spec.LEGACY_KEYS["SHOES"]]
    )
    # 한 단계가 다른 단계의 결과를 바꿀 수 있어(회색 띠를 지우면 맨발이 새로 드러난다) 더 바뀌지 않을 때까지(최대 [PASSES]번) 되풀이한다
    for _ in range(PASSES):
        touched = False
        for key, fix in jobs:
            path = outfits_dir / key / f"female_stand{spec.EXTENSION}"
            if not path.is_file():
                continue
            layer = Image.open(path).convert("RGBA")
            out, count = fix(layer, body)
            if count:
                # 바꾼 가장자리에 남은 부스러기·가는 털(검사 10번)과 새로 갇힌 틈(검사 16번)은 build와 같은 규칙으로 정리한다
                out, _ = drop_strays(out, garment=wisp_layer(key, path.name), thin=True)
                part = spec.part_of_key(key)
                out = fill_layer_holes(out, masks_dir, path.name, part, spec.default_male_top_surface(key))
            if count:
                # 투명 픽셀의 색은 비운다(지운 픽셀에 남은 색 때문에 다시 걸 때마다 바뀐 것으로 셌다)
                cleaned = np.array(out.convert("RGBA"))
                cleaned[cleaned[..., 3] == 0] = 0
                out = Image.fromarray(cleaned, "RGBA")
                before = np.array(layer)
                before[before[..., 3] == 0] = 0
                if np.array_equal(cleaned, before):
                    continue
            if count and not np.array_equal(np.array(out), np.array(layer)):
                spec.save_webp(out, path)
                spec.save_webp(make_shop(out), outfits_dir / key / spec.SHOP_FILE)
                diff = int((np.array(out) != np.array(layer)).any(axis=2).sum())
                changed[f"{key}/{path.name}"] = changed.get(f"{key}/{path.name}", 0) + diff
                touched = True
        if not touched:
            break
    return changed


# 민소매 몸통 옆 맨살 띠(판정 round4 표지 색 렌더: singlet 밑단 옆 짧은 짙은 획 = 맨몸 아틀라스 x 100~124·376~392, y 256~295의 몸통 옆
# 외곽선과 그 안쪽 2~4px 맨살). 민소매는 맨몸 몸통보다 좁아 몸통 옆 외곽선과 상의 옆 가장자리 사이에 맨살 띠가 남고, 밑단과 하의 허리(y 296)
# 사이 3~4행도 맨살이었다. 1배에서 상의 옆에 따로 선 짙은 획과 밝은 점으로 보였다. 행마다 상의 옆 가장자리 바깥의 맨살(몸통 옆 외곽선
# 앞까지, [SKIN_MAX]px 이하)을 상의 가장자리 색으로, 밑단 아래 하의 허리까지의 맨살은 그 열 마지막 상의 색으로 덮는다. 몸통 옆 외곽선은
# 그대로라 상의의 옆 외곽선이 된다.
SIDE_SKIN_KEYS = ("top_lime_singlet",)
SIDE_SKIN_ROWS = (200, 296)  # 겨드랑이 아래 ~ 하의 허리 위
SIDE_SKIN_X = ((95, 200), (312, 417))
SKIN_MAX = 6
SKIN_INK = 110  # 이보다 어두운 맨몸 픽셀은 몸통 옆 외곽선
HEM_REACH = 6  # 밑단 아래로 덮는 최대 행 수
HEM_COLUMNS = 60  # 밑단 아래를 보는 옆 칸 폭(바깥 끝에서)
CLOTH_IN = 3  # 덮는 색을 가져오는 상의 가장자리 안쪽 거리
EDGE_KEEP = 4  # 밑단 아래를 덮을 때 남기는 몸통 옆 외곽선 폭(빈 곳에서)


def _grow(mask: np.ndarray, size: int) -> np.ndarray:
    from PIL import ImageFilter

    return np.array(Image.fromarray((mask * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(size))) > 0


def close_side_skin(layer: Image.Image, body: Image.Image) -> tuple[Image.Image, int]:
    """민소매 서기 상의 레이어의 몸통 옆 맨살 띠와 밑단 아래 맨살을 상의로 덮는다. (레이어, 덮은 픽셀 수)."""
    array = np.array(layer.convert("RGBA"))
    skin = np.array(body.convert("RGBA"))
    solid = array[..., 3] >= 128
    bare = (skin[..., 3] >= 128) & (skin[..., :3].astype(int).mean(axis=-1) >= SKIN_INK)
    y0, y1 = SIDE_SKIN_ROWS
    changed = 0
    for (x0, x1), inward in ((SIDE_SKIN_X[0], 1), (SIDE_SKIN_X[1], -1)):
        xs = list(range(x0, x1)) if inward == 1 else list(range(x1 - 1, x0 - 1, -1))
        for y in range(y0, y1):
            edge = next((x for x in xs if solid[y, x]), None)
            if edge is None:
                continue
            run = []
            x = edge - inward
            while x0 <= x < x1 and bare[y, x] and not solid[y, x] and len(run) <= SKIN_MAX:
                run.append(x)
                x -= inward
            if run and len(run) <= SKIN_MAX and x0 <= x < x1 and skin[y, x, 3] >= 128 and not bare[y, x]:
                # 천 색(가장자리 외곽선 안쪽 [CLOTH_IN]px): 외곽선 색으로 칠하면 갈색 실오라기(검사 10번)로 지워져 되풀이했다
                array[y, run] = array[y, edge + CLOTH_IN * inward]
                solid[y, run] = True
                changed += len(run)
        # 밑단 아래 하의 허리 위까지: 위 [HEM_REACH]행 안에 상의가 있는 열의 맨몸(맨살과 그 사이 짙은 선)을 덮는다. 맨살만 덮으면 짙은
        # 선에 막혀 상의와 떨어진 조각이 되어 부스러기 정리(검사 10번)에 지워졌다. 빈 곳에서 [EDGE_KEEP]px 안의 몸통 옆 외곽선은 남긴다
        opaque = skin[..., 3] >= 128
        rim = _grow(~opaque & ~solid, 2 * EDGE_KEEP + 1)  # 상의 밑 맨몸 구멍은 빈 곳이 아니다
        # 천 색: 그 옆 칸 밑단 위 밝은 천의 가운데값(밑단 짙은 단으로 칠하면 갈색 실오라기로 지워졌다)
        band = np.zeros(solid.shape, bool)
        band[y1 - 40 : y1, min(xs[:HEM_COLUMNS]) : max(xs[:HEM_COLUMNS]) + 1] = True
        light = band & solid & (array[..., :3].astype(int).mean(axis=-1) >= SKIN_INK)
        if not light.any():
            continue
        cloth = np.median(array[light], axis=0).round().astype(np.uint8)
        cloth[3] = 255
        for x in xs[:HEM_COLUMNS]:
            above = [y for y in range(y1 - HEM_REACH - 1, y1) if solid[y, x]]
            if not above:
                continue
            last = max(above)
            for y in range(last + 1, y1):
                if opaque[y, x] and not solid[y, x] and not rim[y, x]:
                    array[y, x] = cloth
                    array[y, x, 3] = 255
                    changed += 1
    return Image.fromarray(array, "RGBA"), changed
