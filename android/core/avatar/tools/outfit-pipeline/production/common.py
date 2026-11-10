"""제작 스크립트 공용: 저장소 경로, 작업 폴더, riv 내장 텍스처 읽기. 절대 경로·비밀값을 두지 않는다(작업 폴더는 인자로 받는다)."""

from __future__ import annotations

import io
import sys
from functools import cache
from pathlib import Path

from PIL import Image

TOOL = Path(__file__).resolve().parents[1]
MODULE = TOOL.parents[1]  # android/core/avatar
RAW = MODULE / "src/main/res/raw"
OUTFITS = MODULE / "src/main/assets/outfits"
MASKS = TOOL / "templates/masks"
PROMPTS = Path(__file__).resolve().parent / "prompts"
sys.path.insert(0, str(TOOL))

from outfit_pipeline.riv import read_scene  # noqa: E402

LEGACY_STAND = RAW / "avatar_legacy_standing_body.png"
LONG_TOPS = {"top_finisher_tee", "top_winter_hoodie", "top_dawn_windbreaker", "top_cherry_vest"}
LONG_BOTTOMS = {"bottom_wide_pants", "bottom_jogger_pants", "bottom_leggings"}


@cache
def embedded(riv: str, asset: str) -> Image.Image:
    """riv에 내장된 승인 텍스처(예: avatar_run_approved.riv의 continuous_arm)."""
    data = read_scene(RAW / riv).asset_bytes[asset]
    return Image.open(io.BytesIO(data)).convert("RGBA")


def default_texture(slot: str) -> Image.Image:
    """기본 의상(흰 반팔티·검은 반바지·흰 신발)이 그려진 승인 슬롯 텍스처."""
    if slot == "stand":
        return Image.open(LEGACY_STAND).convert("RGBA")
    asset = {
        "run_body": "side_body_coral",
        "run_arm": "continuous_arm",
        "run_leg": "side_leg_skin",
        "run_shoe": "side_shoe",
    }
    return embedded("avatar_run_approved.riv", asset[slot])


def work_paths(work: Path) -> dict[str, Path]:
    """작업 폴더 배치: in/(그림 도구에 줄 참고 그림), runs/<이름>/(그림 도구 출력), src_body/, src_outfits/(build 입력), review/."""
    paths = {name: work / name for name in ("in", "runs", "src_body", "src_outfits", "review")}
    for path in paths.values():
        path.mkdir(parents=True, exist_ok=True)
    return paths


def run_output(work: Path, key: str, name: str) -> Path | None:
    """그림 도구 출력. 다시 그린 판(<key>_v2)이 있으면 그것을 쓴다."""
    for folder in (work / "runs" / f"{key}_v2", work / "runs" / key):
        if (folder / name).is_file():
            return folder / name
    return None
