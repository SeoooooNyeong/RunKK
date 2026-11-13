"""그림 도구 출력(runs/<assetKey>/{stand,run_body,run_arm,run_leg,run_shoe}.png) → 슬롯 크기 의상 레이어 원본(src_outfits/<assetKey>/).

1. 서기는 기본 아틀라스 실루엣에 배율·이동으로, 달리기는 불투명 상자를 가로·세로 따로 맞춘다(그림 도구가 비율을 조금 바꾼다).
2. 맨몸 바탕(src_body)과 다른 옷 픽셀만 뽑는다(`outfit_pipeline.build.extract`). 피부색과 비슷한 옷(크림·회색·베이지·오프화이트)은
   피부 판정을 맨몸이 보일 수 있는 곳(긴소매의 목·손, 신발의 발목 위)에만 한다. 기본 의상에서 다른 부위 옷이던 픽셀이 그대로면 버린다.
3. base_*는 그림 도구 없이 승인 기본 텍스처에서 뽑는다.
그다음 `outfit-pipeline build <작업>/src_outfits`가 규격 묶음(무손실 WebP, 남녀 공용 파일)으로 옮긴다.

usage: uv run python production/produce.py --work <작업 폴더> [assetKey...]   (없으면 21가지, base_*를 먼저)
"""

from __future__ import annotations

import argparse
from pathlib import Path

import numpy as np
from common import LONG_BOTTOMS, LONG_TOPS, MASKS, RAW, default_texture, run_output, work_paths
from PIL import Image, ImageDraw

from outfit_pipeline import spec
from outfit_pipeline.build import align, align_box, drop_specks, extract, to_slot
from outfit_pipeline.masks import load_mask, stand_zones

HAND_TOP = 845  # 달리기 팔 텍스처에서 손이 시작하는 행


def body(work: Path, slot: str) -> Image.Image | None:
    path = work / "src_body" / f"female_{slot}.png"
    return Image.open(path).convert("RGBA") if path.is_file() else None


def to_fit(image: Image.Image, slot: str) -> Image.Image:
    """비율이 같으면 줄이고, 다르면 세로 기준으로 맞춰 가운데에 둔다."""
    s = spec.SLOTS[slot]
    image = image.convert("RGBA")
    if image.width * s.height == image.height * s.width:
        return to_slot(image, s)
    resized = image.resize((round(image.width * s.height / image.height), s.height), Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", (s.width, s.height), (0, 0, 0, 0))
    left = (resized.width - s.width) // 2
    canvas.paste(resized.crop((left, 0, left + s.width, s.height)), (0, 0))
    return canvas


def skin_area(key: str, slot: str) -> Image.Image | None:
    """피부 판정을 할 곳(나머지 불투명 픽셀은 모두 옷). None이면 어디서나 판정한다."""
    s = spec.SLOTS[slot]
    mask = Image.new("L", (s.width, s.height), 0)
    draw = ImageDraw.Draw(mask)
    if key.startswith("shoes_") and slot == "stand":
        draw.rectangle((0, 0, s.width, 635), fill=255)  # 발은 신발이 덮는다. 발목 위만
        return mask
    if key in LONG_BOTTOMS and slot == "stand":
        return mask  # 발목까지 덮는다
    if key not in LONG_TOPS:
        return None
    if slot == "stand":
        draw.rectangle((196, 0, 316, 48), fill=255)
    elif slot == "run_arm":
        draw.rectangle((0, 760, s.width, s.height), fill=255)
    elif slot == "run_body":
        draw.rectangle((0, 0, s.width, 40), fill=255)
    return mask


def foreign(work: Path, part: str) -> Image.Image | None:
    """기본 의상에서 다른 부위 옷이 있던 서기 픽셀."""
    result = None
    for other, base in (("TOP", "base_top"), ("BOTTOM", "base_bottom"), ("SHOES", "base_shoes")):
        path = work / "src_outfits" / base / "female_stand.png"
        if other == part or not path.is_file():
            continue
        alpha = np.array(Image.open(path).getchannel("A")) > 0
        result = alpha if result is None else (result | alpha)
    return None if result is None else Image.fromarray((result * 255).astype(np.uint8), "L")


def layer(work: Path, key: str, slot: str, generated: Image.Image | None) -> Image.Image:
    part = spec.part_of_key(key)
    ref = default_texture(slot)
    template = "female_stand" if slot == "stand" else f"female_{slot}"
    allowed = load_mask(MASKS, template, part)
    if generated is None:
        dressed = ref
    elif slot == "stand":
        dressed = align(to_fit(generated, slot), ref, search=16)
    else:
        dressed = align_box(generated.convert("RGBA"), ref)
    if slot == "run_shoe":
        array = np.array(dressed)
        array[..., 3] = np.where(np.array(allowed) > 0, array[..., 3], 0)
        return Image.fromarray(array, "RGBA")
    if slot == "stand":
        out = extract(
            dressed,
            body(work, slot),
            allowed,
            reference=ref
            if generated is not None
            else None,  # 기본 의상은 원래 그림 그대로라 겹침 띠 비교를 하지 않는다
            own=stand_zones(RAW)[part],
            skin_area=skin_area(key, slot),
            foreign=foreign(work, part),
            exact_body=generated is None,
        )
        if generated is None:
            # 기본 의상: 다른 기본 부위가 가진 픽셀(겹침 띠의 흰 반팔티 밑단 등)은 이 부위 레이어에서 뺀다
            other = foreign(work, part)
            if other is not None:
                array = np.array(out)
                array[np.array(other) > 0] = 0
                out = Image.fromarray(array, "RGBA")
        return drop_default_shorts(out, dressed, ref) if part == "TOP" else out
    # 달리기 슬롯은 맨몸 외곽선이 옷 레이어로 딸려 오는 일이 없어 같은 자리로만 비교한다(얇은 민소매 끈이 깎이지 않게)
    out = extract(
        dressed, body(work, slot), allowed, skin_area=skin_area(key, slot), exact_body=generated is None, shift=0
    )
    if slot == "run_arm" and key in LONG_TOPS:
        # 긴소매 소매 끝(y 약 820) 아래는 손이다. 손 위에 남은 옷 조각(크림색처럼 피부와 비슷한 색의 오판)을 뺀다
        array = np.array(out)
        array[HAND_TOP:] = 0
        out = Image.fromarray(array, "RGBA")
    return out


def drop_default_shorts(layer: Image.Image, dressed: Image.Image, ref: Image.Image) -> Image.Image:
    """상의 서기 레이어에서 밑단 아래로 딸려 온 기본 검은 반바지 허리(y 285 아래, 채도 낮은 짙은 회색, 원래 그림과 같은 색)를 뺀다."""
    array = np.array(layer)
    rgb = array[..., :3].astype(int)
    gray = ((rgb.max(axis=2) - rgb.min(axis=2)) < 25) & (rgb.max(axis=2) < 150)
    same = np.abs(np.array(dressed)[..., :3].astype(int) - np.array(ref)[..., :3].astype(int)).sum(axis=2) <= 42
    drop = gray & same
    drop[:285] = False
    array[drop] = 0
    return Image.fromarray(array, "RGBA")


def produce(work: Path, key: str) -> None:
    part = spec.part_of_key(key)
    folder = work / "src_outfits" / key
    folder.mkdir(parents=True, exist_ok=True)
    for slot in spec.PART_SLOTS[part]:
        generated = None
        if not key.startswith("base_"):
            path = run_output(work, key, f"{slot}.png")
            if path is None:
                print(key, "missing", slot)
                continue
            generated = Image.open(path)
        out = layer(work, key, slot, generated)
        for gender in spec.GENDERS:
            out.save(folder / f"{gender}_{slot}.png")
    print("produced", key)


def fill_base_gaps(work: Path) -> None:
    """기본 의상 셋을 겹친 결과가 승인 기본 텍스처와 같도록: 셋 어디에도 없고 맨몸 바탕도 승인 픽셀과 다른 서기 픽셀을 그 부위 칸의
    기본 레이어에 승인 픽셀 그대로 넣는다(밑단 옆 모서리처럼 추출에서 빠진 몇 픽셀, 맨몸이 비치지 않게)."""
    ref = np.array(default_texture("stand"))
    body_stand = np.array(body(work, "stand"))
    zones = stand_zones(RAW)
    layers = {
        p: work / "src_outfits" / k / "female_stand.png"
        for p, k in (("TOP", "base_top"), ("BOTTOM", "base_bottom"), ("SHOES", "base_shoes"))
    }
    if not all(path.is_file() for path in layers.values()):
        return
    arrays = {p: np.array(Image.open(path).convert("RGBA")) for p, path in layers.items()}
    covered = np.zeros(ref.shape[:2], bool)
    for array in arrays.values():
        covered |= array[..., 3] > 0
    differs = np.abs(body_stand[..., :3].astype(int) - ref[..., :3].astype(int)).sum(axis=2) > 12
    gap = (ref[..., 3] > 0) & ~covered & (differs | (body_stand[..., 3] == 0))
    # 승인 피부는 옷 레이어에 넣지 않는다(넣으면 러닝량 톤을 받지 않는 밝은 피부가 된다: 하의 칸 허벅지 DV-성-11, 상의 칸 소매 밑
    # 팔 안쪽 DV3-8). 그 피부는 맨몸 바탕이 승인 픽셀로 그린다(make_body). 피부가 아닌 승인 픽셀(소매 밑단 선, 몸통 옆 외곽선)은
    # 팔 칸이어도 넣는다: 빠지면 기본 의상에서 소매 밑단·옷 옆에 구멍(배경)이 점점이 비쳤다(실기기 3차 재확인).
    from outfit_pipeline.build import _skin_like
    from outfit_pipeline.skin import skin_mask

    # 상의 칸은 맨살 규칙(skin_mask)으로만 뺀다: 넓은 피부색 판정(_skin_like)은 흰 반팔티의 분홍 회색 그늘도 피부로 봐서 빠지면 구멍이 났다
    gap &= ~((_skin_like(ref[..., :3]) & (np.array(zones["TOP"]) == 0)) | skin_mask(ref))
    for part, array in arrays.items():
        allowed = np.array(load_mask(MASKS, "female_stand", part)) > 0
        take = gap & (np.array(zones[part]) > 0) & allowed  # 한 점짜리 구멍도 채운다
        array[take] = ref[take]
        gap &= ~take
        for gender in spec.GENDERS:
            Image.fromarray(array, "RGBA").save(layers[part].parent / f"{gender}_stand.png")


def fill_run_gaps(work: Path) -> None:
    """달리기 슬롯도 기본 의상 셋 + 맨몸이 승인 텍스처와 같도록, 빠진 승인 픽셀을 그 슬롯 부위의 기본 레이어에 넣는다."""
    for slot, key in (("run_body", "base_top"), ("run_arm", "base_top"), ("run_leg", "base_bottom")):
        path = work / "src_outfits" / key / f"female_{slot}.png"
        if not path.is_file():
            continue
        ref = np.array(default_texture(slot))
        body_slot = np.array(body(work, slot))
        array = np.array(Image.open(path).convert("RGBA"))
        differs = np.abs(body_slot[..., :3].astype(int) - ref[..., :3].astype(int)).sum(axis=2) > 12
        gap = (ref[..., 3] > 0) & (array[..., 3] == 0) & (differs | (body_slot[..., 3] == 0))
        from outfit_pipeline.build import _skin_like

        gap &= ~_skin_like(ref[..., :3])
        part = spec.RUN_SLOT_PART[slot]
        allowed = np.array(load_mask(MASKS, f"female_{slot}", part)) > 0
        take = drop_specks(gap & allowed, 6)
        array[take] = ref[take]
        for gender in spec.GENDERS:
            Image.fromarray(array, "RGBA").save(path.parent / f"{gender}_{slot}.png")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--work", type=Path, required=True)
    parser.add_argument("keys", nargs="*")
    args = parser.parse_args()
    work_paths(args.work)
    keys = args.keys or [k for p in spec.PARTS for k in spec.LEGACY_KEYS[p]]
    for key in sorted(keys, key=lambda k: not k.startswith("base_")):
        produce(args.work, key)
    if any(k.startswith("base_") for k in keys):
        fill_base_gaps(args.work)
        fill_run_gaps(args.work)


if __name__ == "__main__":
    main()
