"""검수 렌더: 묶음을 앱과 같은 규칙(맨몸 피부톤 → 상의 자르기 → 신발·하의·상의)으로 슬롯 텍스처에 합성하고, Rive CLI로 남녀 서기·달리기를
그려 한 장(sheet.png)으로 붙인다. 얼굴도 앱처럼 같은 러닝량 톤을 준다(서기 얼굴 레이어, 달리기 측면 머리의 피부색 픽셀).

Rive 원본(scene.rml 프로젝트 넷)과 `rive` 실행 파일은 저장소 밖에 있다(아바타 PoC). 경로는 인자로 받는다.
usage: uv run python production/review.py --rive <rive.exe> --rml <PoC tools 폴더> --out <폴더> [--amount 0..100] [--shape 0|50|100]
       keys... (assetKey, 또는 top_x+bottom_y+shoes_z 조합)
"""

from __future__ import annotations

import argparse
import re
import shutil
import subprocess
from pathlib import Path

import numpy as np
from common import OUTFITS
from PIL import Image, ImageDraw

from outfit_pipeline import spec
from outfit_pipeline.check import read_index, resolve

SCENES = {  # PoC tools 폴더 기준 Rive 원본(승인 riv 넷과 SHA가 같은 빌드 원본)
    "female_stand": ("female-forearm-refine-2026-11-09/female", "Idle"),
    "male_stand": ("front-neck-arm-polish-2026-11-09/male", "Idle"),
    "female_run": ("latest-side-2026-11-09/female", "Runner"),
    "male_run": ("latest-side-2026-11-09/male", "Runner"),
}
FILES = {
    "stand": ["front_body.png"],
    "run_body": ["side_body_coral.png", "side_body_cream.png", "side_body_blue.png"],
    "run_arm": ["continuous_arm.png"],
    "run_leg": ["side_leg_skin.png"],
    "run_shoe": ["side_shoe.png"],
}
TONE = (0.30, 0.50, 0.68)  # SkinToneFilter
BASE = {"TOP": "base_top", "BOTTOM": "base_bottom", "SHOES": "base_shoes"}


def tone(image: Image.Image, amount: int, only_skin: bool = False) -> Image.Image:
    a = np.array(image.convert("RGBA")).astype(np.float32)
    factor = np.array([1 - d * amount / 100 for d in TONE], np.float32)
    if only_skin:
        r, g, b = a[..., 0], a[..., 1], a[..., 2]
        mask = (r - b > 30) & (r > 150) & (g > 90)
        a[mask, :3] *= factor
    else:
        a[..., :3] *= factor
    return Image.fromarray(a.clip(0, 255).astype(np.uint8), "RGBA")


def read(folder: Path, name: str, shared: dict) -> Image.Image | None:
    path = resolve(folder, name, shared)
    return Image.open(path).convert("RGBA") if path.is_file() else None


def cut(body: Image.Image, top: Image.Image, area: Image.Image) -> Image.Image:
    """앱 TopBodyCut과 같은 규칙(outfit_pipeline.cut)."""
    from outfit_pipeline.cut import apply

    return apply(body, top, area)


def compose(index: dict, gender: str, slot: str, chosen: dict, amount: int) -> Image.Image:
    s = spec.SLOTS[slot]
    body_shared = index.get("body", {}).get("shared", {})
    entries = {e["assetKey"]: e for e in index["outfits"]}
    out = Image.new("RGBA", (s.width, s.height), (0, 0, 0, 0))
    layers = []
    for part in ("SHOES", "BOTTOM", "TOP"):
        if slot in spec.PART_SLOTS[part]:
            key = chosen[part]
            layer = read(OUTFITS / key, f"{gender}_{slot}", entries[key].get("shared", {}))
            if layer is not None:
                layers.append((part, layer))
    body = read(OUTFITS / spec.BODY_FOLDER, f"{gender}_{slot}", body_shared)
    if body is not None:
        top = next((layer for part, layer in layers if part == "TOP"), None)
        bottom = next((layer for part, layer in layers if part == "BOTTOM"), None)
        if top is not None and bottom is not None:  # 앱과 같이 하의 허리도 옷으로 친다
            merged = bottom.copy()
            merged.alpha_composite(top)
            top = merged
        area = read(OUTFITS / spec.BODY_FOLDER, f"{gender}_{slot}_cut", body_shared)
        if top is not None and area is not None:
            body = cut(body, top, area)
        out.alpha_composite(tone(body, amount))
    for _, layer in layers:
        out.alpha_composite(layer)
    return out


def clear_work(work: Path, root: Path) -> None:
    """검수 작업 폴더 [work]를 지운다. [root](--out) 안의 하위 폴더일 때만 지우고 아니면 ValueError(잘못된 경로로 다른 폴더를 지우지 않는다)."""
    resolved, base = work.resolve(), root.resolve()
    if resolved == base or base not in resolved.parents:
        raise ValueError(f"작업 폴더는 {base} 안이어야 한다: {resolved}")
    if resolved.exists():
        shutil.rmtree(resolved)


def render(
    rive: Path, rml: Path, scene: str, out: Path, textures: dict, amount: int, shape: int, surface: int, work: Path
) -> None:
    source, artboard = SCENES[scene]
    clear_work(work, out.parent)
    shutil.copytree(rml / source, work, ignore=shutil.ignore_patterns("build"))
    for slot, image in textures.items():
        for name in FILES[slot]:
            image.save(work / name)
    if amount and scene.endswith("stand"):
        tone(Image.open(work / "faceImage.png"), amount).save(work / "faceImage.png")
    # 달리기 측면 머리는 Rive 원본에 얼굴·헤어가 합쳐진 한 장이라 얼굴만 골라 톤을 줄 수 없어 원본 그대로 둔다(앱은 얼굴 레이어에만 준다)
    text = (work / "scene.rml").read_text(encoding="utf-8")

    def number(name: str, value: int) -> None:
        nonlocal text
        text = re.sub(rf'(<StateMachineNumber name="{name}"[^>]*value=")[^"]*', lambda m: m.group(1) + str(value), text)

    number("body_shape", shape)
    if scene == "male_stand":
        number("selected_top", surface)
        number("selected_bottom", 1)
        number("selected_shoes", 1)
    (work / "scene.rml").write_text(text, encoding="utf-8")
    frame = "5" if scene.endswith("stand") else "8"  # 서기는 표면 입력이 걸린 뒤의 장면
    command = [str(rive), str(work), f"--screenshot={out}", f"--advance={frame}", f"--artboard={artboard}"]
    subprocess.run([*command, "--fit=contain", "--viewport=800x1000"], check=True, capture_output=True)


def part_of(key: str) -> str:
    return spec.part_of_key(key)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--rive", type=Path, required=True)
    parser.add_argument("--rml", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--amount", type=int, default=0)
    parser.add_argument("--shape", type=int, default=0)
    parser.add_argument("keys", nargs="+")
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    index = read_index(OUTFITS)
    tiles = []
    for key in args.keys:
        chosen = dict(BASE)
        for k in key.split("+"):
            chosen[part_of(k)] = k
        entry = next(e for e in index["outfits"] if e["assetKey"] == chosen["TOP"])
        shots = []
        for gender in spec.GENDERS:
            textures = {slot: compose(index, gender, slot, chosen, args.amount) for slot in spec.SLOTS}
            for motion in ("stand", "run"):
                shot = args.out / f"{key}_{gender}_{motion}.png"
                use = {k: v for k, v in textures.items() if (k == "stand") == (motion == "stand")}
                render(
                    args.rive,
                    args.rml,
                    f"{gender}_{motion}",
                    shot,
                    use,
                    args.amount,
                    args.shape,
                    entry.get("maleTopSurface", 1),
                    args.out / "_scene",
                )
                shots.append(shot)
        tile = Image.new("RGB", (1000, 410), (255, 255, 255))
        for i, path in enumerate(shots):
            tile.paste(Image.open(path).convert("RGB").crop((150, 120, 650, 900)).resize((250, 390)), (i * 250, 0))
        label = f"{key}  amount={args.amount} shape={args.shape}" + ("  (run head not toned)" if args.amount else "")
        ImageDraw.Draw(tile).text((4, 394), label, fill=(0, 0, 0))
        tiles.append(tile)
    sheet = Image.new("RGB", (1000, 410 * len(tiles)), (255, 255, 255))
    for i, tile in enumerate(tiles):
        sheet.paste(tile, (0, 410 * i))
    sheet.save(args.out / "sheet.png")
    print(args.out / "sheet.png")


if __name__ == "__main__":
    main()
