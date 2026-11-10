"""맨몸 바탕 원본(src_body/{g}_{slot}.png) 만들기([임시:D-성-152]).

그림 도구 출력(runs/base_stand·base_run_body·base_run_leg)을 승인 기본 텍스처에 정렬하고, 기본 의상에서 보이던 피부(손·팔·다리·목)는
승인 픽셀을 그대로 쓴다. 달리기 팔은 소매 끝 아래 피부 행을 위로 늘린다. 서기는 목·가슴 위를 피부로 채우고 어깨를 기본 실루엣 안으로
자른다. 그다음 `outfit-pipeline body <작업>/src_body`로 body/(무손실 WebP)와 상의 자르기 칸을 만든다.

usage: uv run python production/make_body.py --work <작업 폴더>
"""

from __future__ import annotations

import argparse
from pathlib import Path

import numpy as np
from common import LEGACY_STAND, RAW, default_texture, work_paths
from PIL import Image, ImageFilter

from outfit_pipeline import spec
from outfit_pipeline.build import align, drop_specks, to_slot
from outfit_pipeline.masks import stand_zones
from outfit_pipeline.skin import skin_mask

OUTLINE = (92, 58, 46)


def _first_png(folder: Path) -> Path:
    return sorted(folder.glob("*_a.png"))[0] if list(folder.glob("*_a.png")) else sorted(folder.glob("*.png"))[0]


def merge(gen: Image.Image, ref: Image.Image, thresh: int = 40) -> Image.Image:
    g = np.array(gen).astype(int)
    r = np.array(ref).astype(int)
    same = (r[..., 3] > 0) & (g[..., 3] > 0) & (np.abs(g[..., :3] - r[..., :3]).sum(2) < thresh)
    out = g.copy()
    out[same] = r[same]
    return Image.fromarray(out.clip(0, 255).astype(np.uint8), "RGBA")


def stand(work: Path) -> Image.Image:
    ref = Image.open(LEGACY_STAND).convert("RGBA")
    gen = align(to_slot(Image.open(_first_png(work / "runs/base_stand")), spec.SLOTS["stand"]), ref, search=12)
    hands = np.array(stand_zones(RAW)["HANDS"]) > 0
    yy, xx = np.mgrid[0:768, 0:512]
    region = ((yy >= 425) & (yy <= 605)) | ((yy >= 190) & (yy < 304) & ((xx < 120) | (xx > 392)))
    g = np.array(gen).astype(float)
    r = np.array(ref).astype(float)
    same = (np.abs(g[..., :3] - r[..., :3]).sum(2) < 60) & (r[..., 3] > 0) & region
    keep = (hands & (r[..., 3] > 0)) | same
    weight = np.array(Image.fromarray((keep * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(5))) / 255
    weight = weight.astype(float)[..., None]
    weight[hands & (r[..., 3] > 0)] = 1.0
    both = (r[..., 3] > 0) & (g[..., 3] > 0)
    g[both] = (weight * r + (1 - weight) * g)[both]
    # 팔(소매 끝 아래, 바깥 열)은 승인 기본 텍스처의 피부·외곽선을 그대로 옮기고 실루엣도 그대로 둔다(팔 안쪽 윤곽 보존).
    rgb = r[..., :3]
    tee = (rgb.min(axis=2) > 215) & ((rgb.max(axis=2) - rgb.min(axis=2)) < 30)
    shorts = ((rgb.max(axis=2) - rgb.min(axis=2)) < 25) & (rgb.max(axis=2) < 150)
    arms = (yy >= 150) & (yy < 304) & ((xx < 140) | (xx > 372))
    # 소매 끝 아래만 옮긴다(열마다 흰 소매 가장 아래 행 + 3px 아래). 소매 밑단 외곽선이 민소매 맨팔에 줄로 남지 않게.
    below_hem = np.zeros_like(arms)
    ref_skin = skin_mask(r.clip(0, 255).astype(np.uint8))
    for x in range(r.shape[1]):
        rows = np.where(tee[:304, x] & (r[:304, x, 3] > 0))[0]
        start = rows.max() + 4 if len(rows) else 150
        # 밑단 외곽선(짙은 선)이 끝나 피부가 시작하는 행부터 옮긴다: 민소매 맨팔에 소매 밑단 선이 남지 않게(DV3-8)
        for _ in range(8):
            if start >= 304 or ref_skin[start, x] or r[start, x, 3] == 0:
                break
            start += 1
        below_hem[start:, x] = True
    copy = arms & below_hem & (r[..., 3] > 0) & ~tee & ~shorts
    g[copy] = r[copy]
    # 팔과 몸통 사이 틈은 승인 텍스처처럼 비운다(그림 도구 맨몸은 팔이 몸통에 붙어 있다)
    gap = (yy >= 150) & (yy < 304) & ((xx < 165) | (xx > 347)) & (r[..., 3] == 0)
    # 팔과 몸통이 만나는 겨드랑이 밑(승인 텍스처에서는 흰 반팔티가 덮던 곳)도 비운다: 민소매에서 팔과 옷 사이로 얇은 맨몸 조각이 남지 않게
    # 어깨 높이(y 185 위)는 몸통 옆 띠(x 140~164·348~372)만 비운다: 위팔까지 비우면 민소매에서 어깨가 구멍으로 보였다(DV3-8 점검)
    side_rows = (yy >= 185) & (yy < 304) & (((xx >= 112) & (xx < 165)) | ((xx > 347) & (xx < 404)))
    shoulder_rows = (yy >= 150) & (yy < 185) & (((xx >= 140) & (xx < 165)) | ((xx > 347) & (xx <= 372)))
    armpit = (side_rows | shoulder_rows) & tee
    g[gap | armpit] = 0
    # 팔 칸에서 승인 텍스처가 반투명·투명한 곳(팔 외곽선 안티에일리어싱, 알파 240 미만)은 승인 픽셀 그대로 둔다: 그림 도구 맨몸의 팔 외곽선이 그 자리에
    # 부스러기로 남아 러닝량이 높을 때 팔 윤곽이 털처럼 거칠게 보였다(실기기 3차 DV3-8).
    # 손 높이(y 304)부터는 하지 않는다: 팔과 엉덩이 사이를 비우면 상의 자르기가 허리 옆 맨몸을 테두리로 보고 지워 밑단과 허리 사이가
    # 비었다(실기기 3차 재확인)
    halo = (yy >= 150) & (yy < 304) & ((xx < 165) | (xx > 347)) & (r[..., 3] < 240)
    g[halo] = r[halo]
    # 팔 칸의 승인 피부(소매 밑 겨드랑이 쪽 팔 안쪽, 흰 반팔티 소매와 몸통 사이에 보이던 곳 포함)는 맨몸에 승인 픽셀 그대로 둔다.
    # 옷 레이어에서는 맨살을 지우므로(outfit_pipeline.skin) 맨몸이 그 자리를 덮어야 톤이 걸린다.
    # 팔 안쪽 외곽선(짙은 선)도 승인 픽셀로 둔다: 없으면 팔과 셔츠 사이가 점점이 비었다(실기기 3차 재확인)
    outline = rgb.max(axis=2) < 120
    inner = (yy >= 150) & (yy < 304) & ((xx < 165) | (xx > 347)) & (r[..., 3] >= 128) & ~tee & ~shorts
    # 외곽선은 소매 높이(y 185) 아래만: 그 위 짙은 선은 소매 밑단 선이라 기본 상의 레이어가 그린다(민소매 팔에 선이 남지 않게)
    inner &= skin_mask(r) | (outline & (yy >= 185))
    g[inner] = r[inner]
    # 팔과 몸통 사이 띠에 남은 그림 도구의 외곽선·속옷 조각(피부가 아닌 픽셀)도 지운다. 밑단 높이(y 280)부터는 두어야 한다: 비우면
    # 상의 자르기가 허리 밴드를 밑단과 배경 사이 테두리로 보고 지워 밑단과 허리 사이가 비었다(실기기 3차 재확인)
    band = (yy >= 185) & (yy < 280) & (((xx >= 140) & (xx < 165)) | ((xx > 347) & (xx <= 372)))
    g[band & ~inner & ~skin_mask(g.clip(0, 255).astype(np.uint8))] = 0
    # 팔과 몸통 사이(겨드랑이~허리 옆)에 떨어져 남은 작은 조각도 지운다(맨몸 본체는 한 덩어리다)
    side = (yy >= 185) & (yy < 280) & (((xx >= 100) & (xx < 165)) | ((xx > 347) & (xx < 412)))
    alone = (g[..., 3] > 0) & ~drop_specks(g[..., 3] > 0)
    g[side & alone] = 0
    # 허리(y 296~321, 몸통 열): 승인 텍스처의 짙은 회색 허리 밴드를 맨몸 속옷 허리로 둔다. 상의 밑단과 하의 허리 사이가 비어도
    # 밝은 맨몸 대신 속옷 허리선이 보인다(맨몸 바탕의 「속옷 선」, [임시:D-성-152]).
    waist = (yy >= 296) & (yy < 322) & (xx >= 100) & (xx < 412) & shorts & (r[..., 3] > 0)
    g[waist] = r[waist]
    image = Image.fromarray(g.clip(0, 255).astype(np.uint8), "RGBA")
    return clip_to_legacy(fill_neck(image))


def fill_neck(image: Image.Image) -> Image.Image:
    """목·가슴 위(그림 도구 출력의 빈 목 구멍과 속옷 끈)를 좌우 어깨 피부색 보간으로 채우고 목은 승인 텍스처 픽셀로."""
    im = np.array(image).astype(float)
    ref = np.array(Image.open(LEGACY_STAND).convert("RGBA")).astype(float)

    def skin(p: np.ndarray) -> bool:
        return p[3] > 250 and p[0] - p[2] > 25 and p[0] > 200

    for y in range(142):
        xs = [x for x in range(100, 412) if skin(im[y, x])]
        left = [x for x in xs if x < 230]
        right = [x for x in xs if x > 282]
        if not left or not right or min(right) - max(left) < 4:
            continue
        lo, hi = max(left), min(right)
        cl, cr = im[y, lo - 2].copy(), im[y, hi + 2].copy()
        for x in range(lo + 1, hi):
            t = (x - lo) / (hi - lo)
            shade = 1.0 - 0.06 * np.exp(-(((x - 256) / 40) ** 2)) * (1 if y < 40 else 0.3)
            im[y, x, :3] = (cl * (1 - t) + cr * t)[:3] * shade
            im[y, x, 3] = 255
    for y in range(40):
        for x in range(210, 304):
            if skin(ref[y, x]):
                im[y, x] = ref[y, x]
    for x in range(212, 300):
        first = next((y for y in range(60) if skin(im[y, x])), None)
        if first:
            im[:first, x] = im[first, x]
    return Image.fromarray(im.clip(0, 255).astype(np.uint8), "RGBA")


def clip_to_legacy(image: Image.Image) -> Image.Image:
    """서기 어깨(y 160 위)를 승인 기본 텍스처 실루엣 안으로 자르고 잘린 가장자리에 외곽선을 다시 칠한다."""
    im = np.array(image)
    ref = np.array(Image.open(LEGACY_STAND).convert("RGBA"))[..., 3] > 0
    alpha = im[..., 3] > 0
    cut = alpha & ~ref
    yy, xx = np.mgrid[0 : im.shape[0], 0 : im.shape[1]]
    # 어깨(y 160 위)는 전부, 그 아래 몸통 열(x 100~411, 허리 y 330까지)도 승인 실루엣 안으로(밑단·허리 옆으로 맨몸이 비치지 않게)
    cut &= (yy < 160) | ((yy < 330) & (xx >= 100) & (xx < 412))
    keep = alpha & ~cut
    im[cut] = 0
    inner = np.array(Image.fromarray((keep * 255).astype(np.uint8)).filter(ImageFilter.MinFilter(5))) > 0
    near = np.array(Image.fromarray((cut * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(5))) > 0
    line = keep & ~inner & near
    im[line, :3] = OUTLINE
    im[line, 3] = 255
    return Image.fromarray(im, "RGBA")


def clip_silhouette(image: Image.Image, ref: Image.Image) -> Image.Image:
    """승인 기본 텍스처에 픽셀이 없는 곳은 맨몸도 비운다(달리기 몸통·다리는 기본 옷 실루엣이 곧 몸 실루엣)."""
    array = np.array(image)
    array[np.array(ref)[..., 3] == 0] = 0
    return Image.fromarray(array, "RGBA")


def run_body(work: Path) -> Image.Image:
    ref = default_texture("run_body")
    gen = to_slot(Image.open(_first_png(work / "runs/base_run_body")), spec.SLOTS["run_body"])
    return clip_silhouette(merge(align(gen, ref, search=8), ref), ref)


def run_arm() -> Image.Image:
    """기본 팔의 소매 끝 아래 40px 행을 위로 늘린 맨팔(위팔은 민소매에서만 보이고 어깨는 민소매 끈이 덮는다)."""
    ref = np.array(default_texture("run_arm"))
    rgb = ref[..., :3].astype(int)
    white = (ref[..., 3] > 0) & (rgb.min(2) > 215) & ((rgb.max(2) - rgb.min(2)) < 22)
    hem = int(np.where(white[:420].sum(1) > 12)[0].max())
    out = ref.copy()
    out[: hem + 40] = ref[hem + 40]
    return Image.fromarray(out, "RGBA")


def run_leg(work: Path) -> Image.Image:
    ref = default_texture("run_leg")
    runs = work / "runs/base_run_leg"
    source = runs / "base_run_leg_b.png" if (runs / "base_run_leg_b.png").is_file() else _first_png(runs)
    gen = to_slot(Image.open(source), spec.SLOTS["run_leg"])
    return clip_silhouette(merge(align(gen, ref, search=8), ref), ref)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--work", type=Path, required=True)
    args = parser.parse_args()
    out = work_paths(args.work)["src_body"]
    made = {
        "stand": stand(args.work),
        "run_body": run_body(args.work),
        "run_arm": run_arm(),
        "run_leg": run_leg(args.work),
    }
    for gender in spec.GENDERS:
        for slot, image in made.items():
            image.save(out / f"{gender}_{slot}.png")
    print(out)


if __name__ == "__main__":
    main()
