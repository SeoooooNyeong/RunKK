"""체형 100 서기 렌더 검사(판정 round3: 검사 17번은 아틀라스만 봐서 체형 100 메시 변형 뒤의 외곽선 끊김·사다리 획을 놓쳤다).

앱과 같은 규칙으로 서기 아틀라스를 합성하고(맨몸 → 상의 자르기 → 신발·하의·상의, `production/review.py`와 같은 순서), Rive CLI로 남녀
서기를 체형 100(기본)으로 그려 폰 화면 배율(판정 렌더와 같은 760/1211, 같은 잘라내기)로 줄인 뒤, 배경을 단색(자홍)으로 두고 본다.

- 떠도는 조각: 아바타 실루엣(배경과 색이 먼 픽셀)을 8방향으로 이은 덩어리 가운데 가장 큰 것(몸) 밖의 [FLOAT_MIN]px 이상 덩어리.
  체형 변형으로 외곽선이 몸에서 떨어져 생긴 사다리 획, 옷 밖에 뜬 획·점.
- 비치는 틈: 실루엣 안에 갇힌 배경 덩어리 가운데 [SLIT_MAX]px 이하이고 폭 2px 이하(3x3로 열면 사라짐)인 것(턱 아래 [BODY_TOP]행부터,
  가로 [BODY_X] 안: 그 밖은 여자 긴 머리카락 사이 틈). 끊긴 외곽선 사이·메시 이음매로 배경이 비치는 곳. 팔이 몸통에 닿아 갇힌 넓은
  배경은 그림 그대로라 뺀다.

Rive CLI와 Rive 원본(scene.rml)은 저장소 밖에 있어(README 「검수 렌더」) 단위 시험이 아니라 명령(`outfit-pipeline render-check`)과
검수 목록의 한 항목으로 둔다. 판정 함수([defects])는 단위 시험이 있다.
"""

from __future__ import annotations

import re
import shutil
import subprocess
import tempfile
from collections import deque
from pathlib import Path

import numpy as np
from PIL import Image

from outfit_pipeline import spec

KEY = (255, 0, 255)  # 배경 단색(옷·몸에 없는 자홍)
KEY_HEX = "FFFF00FF"
NEAR_KEY = 120  # 배경과의 채널 차 합이 이 이하면 배경(안티앨리어싱 가장자리 반쯤 섞인 픽셀은 실루엣)
FLOAT_MIN = 3
SLIT_MAX = 150
BODY_TOP = 430  # 잘라낸 판정 그림에서 턱 아래 행
BODY_X = (160, 380)
GAP_TIP = 3  # 자연스러운 큰 틈에서 이 거리 안의 작은 틈은 그 틈의 끝으로 본다  # 비치는 틈을 보는 가로 범위(그 밖은 여자 긴 머리카락 사이 틈)
SCALE = 760 / 1211
CROP = (230, 200, 770, 1050)
SCENES = {  # Rive 원본 폴더 기준(production/review.py와 같은 승인 riv 빌드 원본)
    "female": ("female-forearm-refine-2026-11-09/female", "Idle"),
    "male": ("front-neck-arm-polish-2026-11-09/male", "Idle"),
}
HEAD = (
    ("face.png", "faceImage.png"),
    ("hair.png", "hairImage.png"),
    ("eyes_open.png", "eyesImage.png"),
    ("eyes_half.png", "eyesHalfImage.png"),
    ("eyes_closed.png", "eyesClosedImage.png"),
)


def _components8(mask: np.ndarray) -> list[np.ndarray]:
    height, width = mask.shape
    seen = np.zeros_like(mask)
    out = []
    for y, x in zip(*np.nonzero(mask), strict=True):
        if seen[y, x]:
            continue
        queue = deque([(int(y), int(x))])
        seen[y, x] = True
        points = []
        while queue:
            cy, cx = queue.popleft()
            points.append((cy, cx))
            for dy in (-1, 0, 1):
                for dx in (-1, 0, 1):
                    ny, nx = cy + dy, cx + dx
                    if 0 <= ny < height and 0 <= nx < width and mask[ny, nx] and not seen[ny, nx]:
                        seen[ny, nx] = True
                        queue.append((ny, nx))
        out.append(np.array(points))
    return out


def _grow(mask: np.ndarray, size: int) -> np.ndarray:
    from PIL import ImageFilter

    return np.array(Image.fromarray((mask * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(size))) > 0


def _open3(mask: np.ndarray) -> np.ndarray:
    from PIL import ImageFilter

    image = Image.fromarray((mask * 255).astype(np.uint8))
    return np.array(image.filter(ImageFilter.MinFilter(3)).filter(ImageFilter.MaxFilter(3))) > 0


def defects(rgb: np.ndarray, key: tuple[int, int, int] = KEY, body_top: int = BODY_TOP) -> list[str]:
    """판정 크기 렌더([rgb], 배경 [key])에서 떠도는 조각과 비치는 틈. 하나라도 있으면 그 목록(빈 목록이면 통과)."""
    from outfit_pipeline.build import _fill_holes

    distance = np.abs(rgb[..., :3].astype(int) - np.array(key)).sum(axis=-1)
    avatar = distance > NEAR_KEY
    found = []
    pieces = sorted(_components8(avatar), key=len, reverse=True)
    for piece in pieces[1:]:
        if len(piece) >= FLOAT_MIN:
            y, x = piece.mean(axis=0).round().astype(int)
            found.append(f"floating {len(piece)}px at ({x},{y})")
    filled = np.array(_fill_holes(Image.fromarray((avatar * 255).astype(np.uint8)))) > 0
    holes = filled & ~avatar
    holes[:body_top] = False
    # 가는 틈만(3x3로 열면 사라지는 폭 2px 이하): 체형 100에서 팔이 몸통에 닿아 갇힌 넓은 배경은 그림 그대로다
    thin = holes & ~_open3(holes)
    # 큰 배경(바깥 배경이나 SLIT_MAX보다 큰 갇힌 배경: 팔과 몸통 사이 자연스러운 틈)에서 [GAP_TIP]px 안의 작은 틈은 그 틈의 끝(외곽선 한
    # 줄이 가로지른 끝)이라 세지 않는다(판정 round4: 여자 체형 100 겨드랑이 약 5px)
    big = (~avatar & ~filled) | np.zeros_like(avatar)
    pieces = _components8(holes)
    for piece in pieces:
        if len(piece) > SLIT_MAX:
            big[piece[:, 0], piece[:, 1]] = True
    near_big = _grow(big, 2 * GAP_TIP + 1)
    for piece in pieces:
        _, cx = piece.mean(axis=0)
        if (
            len(piece) <= SLIT_MAX
            and piece[:, 0].min() >= body_top
            and BODY_X[0] <= cx <= BODY_X[1]
            and thin[piece[:, 0], piece[:, 1]].all()
            and not near_big[piece[:, 0], piece[:, 1]].any()
        ):
            y, x = piece.mean(axis=0).round().astype(int)
            found.append(f"see-through {len(piece)}px at ({x},{y})")
    return found


def compose_stand(outfits_dir: Path, gender: str, chosen: dict[str, str]) -> Image.Image:
    """앱 합성과 같은 서기 아틀라스(러닝량 0): 맨몸(상의·하의 허리를 옷으로 친 상의 자르기) → 신발 → 하의 → 상의."""
    from outfit_pipeline.check import read_index, resolve
    from outfit_pipeline.cut import apply

    index = read_index(outfits_dir)
    entries = {e["assetKey"]: e for e in index["outfits"]}
    body_shared = index.get("body", {}).get("shared", {})

    def read(folder: Path, name: str, shared: dict) -> Image.Image | None:
        path = resolve(folder, name, shared)
        return Image.open(path).convert("RGBA") if path.is_file() else None

    layers = {}
    for part in ("SHOES", "BOTTOM", "TOP"):
        key = chosen[part]
        layer = read(outfits_dir / key, f"{gender}_stand", entries[key].get("shared", {}))
        if layer is not None:
            layers[part] = layer
    body = read(outfits_dir / spec.BODY_FOLDER, f"{gender}_stand", body_shared)
    out = Image.new("RGBA", (spec.SLOTS["stand"].width, spec.SLOTS["stand"].height), (0, 0, 0, 0))
    if body is not None:
        top = layers.get("TOP")
        if top is not None and "BOTTOM" in layers:
            merged = layers["BOTTOM"].copy()
            merged.alpha_composite(top)
            top = merged
        area = read(outfits_dir / spec.BODY_FOLDER, f"{gender}_stand_cut", body_shared)
        if top is not None and area is not None:
            body = apply(body, top, area)
        out.alpha_composite(body)
    for part in ("SHOES", "BOTTOM", "TOP"):
        if part in layers:
            out.alpha_composite(layers[part])
    return out


def render_stand(
    rive: Path, rml: Path, avatars: Path, gender: str, atlas: Image.Image, surface: int, shape: int
) -> np.ndarray:
    """Rive CLI로 서기를 그려 판정 크기(760/1211 배, 판정 잘라내기)의 RGB 배열로."""
    from outfit_pipeline.seam import close_seam_rml, keep_overlap_rml

    source, artboard = SCENES[gender]
    work = Path(tempfile.mkdtemp(prefix="render_check_"))
    try:
        scene = work / "scene"
        shutil.copytree(rml / source, scene, ignore=shutil.ignore_patterns("build"))
        atlas.save(scene / "front_body.png")
        for src, dst in HEAD:
            if (avatars / gender / src).is_file():
                shutil.copy(avatars / gender / src, scene / dst)
        fullness = {100: "front_fullness_2.png", 50: "front_fullness_1.png"}.get(shape)
        if fullness and (avatars / gender / fullness).is_file():
            shutil.copy(avatars / gender / fullness, scene / "fullnessOverlay.png")
        text = (scene / "scene.rml").read_text(encoding="utf-8")
        text = text.replace('colorValue="FFF6EFE8"', f'colorValue="{KEY_HEX}"')

        def number(name: str, value: int) -> None:
            nonlocal text
            text = re.sub(
                rf'(<StateMachineNumber name="{name}"[^>]*value=")[^"]*', lambda m: m.group(1) + str(value), text
            )

        number("body_shape", shape)
        if gender == "male":
            number("selected_top", surface)
            number("selected_bottom", 1)
            number("selected_shoes", 1)
            text = keep_overlap_rml(close_seam_rml(text))  # 앱 RivSleeveSeam과 같은 긴소매 이음매·반팔 겹침
        (scene / "scene.rml").write_text(text, encoding="utf-8")
        shot = work / "shot.png"
        command = [str(rive), str(scene), f"--screenshot={shot}", "--advance=360", f"--artboard={artboard}"]
        for attempt in range(3):  # Rive CLI가 가끔 비정상 종료한다
            try:
                subprocess.run([*command, "--fit=contain", "--viewport=1600x2000"], check=True, capture_output=True)
                break
            except subprocess.CalledProcessError:
                if attempt == 2:
                    raise
        image = Image.open(shot).convert("RGB")
        image = image.resize((round(image.width * SCALE), round(image.height * SCALE)), Image.Resampling.LANCZOS)
        return np.array(image.crop(CROP))
    finally:
        shutil.rmtree(work, ignore_errors=True)


def check_renders(
    outfits_dir: Path, rive: Path, rml: Path, avatars: Path, keys: list[str], shape: int = 100, out: Path | None = None
) -> list[str]:
    """[keys](없으면 상의·하의·신발 전부, 나머지 부위는 base_*)를 남녀 서기 체형 [shape]로 그려 [defects]. [out]이 있으면 렌더를 저장한다."""
    from outfit_pipeline.check import read_index

    index = read_index(outfits_dir)
    entries = {e["assetKey"]: e for e in index["outfits"]}
    base = {part: spec.LEGACY_KEYS[part][0] for part in spec.PARTS}
    problems = []
    for key in keys or [k for part in spec.PARTS for k in spec.LEGACY_KEYS[part]]:
        chosen = dict(base)
        chosen[spec.part_of_key(key)] = key
        surface = int(entries[chosen["TOP"]].get("maleTopSurface", 1))
        for gender in spec.GENDERS:
            atlas = compose_stand(outfits_dir, gender, chosen)
            rgb = render_stand(rive, rml, avatars, gender, atlas, surface, shape)
            if out is not None:
                out.mkdir(parents=True, exist_ok=True)
                Image.fromarray(rgb).save(out / f"{key}_{gender}_s{shape}.png")
            problems += [f"{key} {gender} s{shape}: {d}" for d in defects(rgb)]
    return problems
