"""서기 레이어 마무리(`outfit-pipeline stand-fix`, 판정 round3): 이미 만든 묶음에 차례로 건다. 다시 걸어도 더 바뀌지 않는다.

1. 상의 자르기 칸(body/{g}_stand_cut)을 지금 규칙(`build.cut_area`, 칼라 옆 칸 포함)으로 다시 쓴다.
2. 긴소매 상의 서기 레이어의 칼라·후드 안 빈 곳을 안감 색으로 메운다(`holes.line_collar`).
3. 반팔·민소매 상의 서기 레이어의 몸통 옆 외곽선 밖 밝은 점을 외곽선 색으로 칠한다([solid_side_outline]).
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
            if count and not np.array_equal(np.array(out), np.array(layer)):
                spec.save_webp(out, path)
                spec.save_webp(make_shop(out), outfits_dir / key / spec.SHOP_FILE)
                diff = int((np.array(out) != np.array(layer)).any(axis=2).sum())
                changed[f"{key}/{path.name}"] = changed.get(f"{key}/{path.name}", 0) + diff
                touched = True
        if not touched:
            break
    return changed
