"""의상 자산 규격(design-oo 05-30 5절, android/core/avatar/OUTFIT_SPEC.md). 수치를 고치면 두 문서와 OutfitAssetSpecTest를 함께 고친다."""

from __future__ import annotations

import re
from dataclasses import dataclass

GENDERS = ("female", "male")
PARTS = ("TOP", "BOTTOM", "SHOES")


@dataclass(frozen=True)
class Slot:
    """의상 파일 하나가 대응하는 riv 이미지 슬롯."""

    name: str  # 파일 이름의 슬롯 부분(stand, run_body, …)
    asset: str  # riv 이미지 자산 이름
    width: int
    height: int


SLOTS: dict[str, Slot] = {
    "stand": Slot("stand", "standing_body", 512, 768),
    "run_body": Slot("run_body", "side_body_coral", 256, 384),
    "run_arm": Slot("run_arm", "continuous_arm", 256, 1024),
    "run_leg": Slot("run_leg", "side_leg_skin", 256, 768),
    "run_shoe": Slot("run_shoe", "side_shoe", 256, 160),
}

# 부위별 필수 슬롯(5.2). 성별마다 하나씩, 그리고 shop.png.
PART_SLOTS: dict[str, tuple[str, ...]] = {
    "TOP": ("stand", "run_body", "run_arm"),
    "BOTTOM": ("stand", "run_leg"),
    "SHOES": ("stand", "run_shoe"),
}

BODY_SUFFIXES = ("", "_b1", "_b2")
FILE_PATTERN = re.compile(r"^(female|male)_(stand|run_body|run_arm|run_leg|run_shoe)(_b1|_b2)?\.png$")
SHOP_FILE = "shop.png"
SHOP_SIZE = 512
SHOP_LONG_SIDE = 440  # 파이프라인이 맞추는 값
SHOP_LONG_SIDE_RANGE = (420, 460)  # 검사 허용(5.5 5번)
SHOP_CENTER_TOLERANCE = 8
MIN_COVERAGE = 0.01  # 빈 그림 거부(5.5 4번)
MAX_BUNDLE_BYTES = 1_572_864  # 1.5MB(5.5 7번)
OVERLAP = 8  # 겹침 띠(512 기준, 5.3)


@dataclass(frozen=True)
class Template:
    """슬롯 × 성별 × (남자 서기면 상의 표면) 템플릿 하나(5.3, 11장)."""

    name: str
    gender: str
    slot: str
    riv: str  # res/raw 파일 이름
    # 이 템플릿의 허용 영역을 만들 상태들. 상태 = 차례로 거는 애니메이션 이름 목록. 상태마다 보이는 Image를 모두 합친다.
    states: tuple[tuple[str, ...], ...]


_BODY_SHAPES = ("Body shape base", "Body shape medium", "Body shape fuller")
_RUN = (("",),)


def _male_states(surface: int) -> tuple[tuple[str, ...], ...]:
    return tuple((shape, f"top surface {surface}", "bottom surface 1", "shoes surface 1") for shape in _BODY_SHAPES)


TEMPLATES: dict[str, Template] = {
    t.name: t
    for t in [
        Template("female_stand", "female", "stand", "avatar_idle_layered.riv", tuple((s,) for s in _BODY_SHAPES)),
        Template("male_stand_s1", "male", "stand", "avatar_male_idle.riv", _male_states(1)),
        Template("male_stand_s2", "male", "stand", "avatar_male_idle.riv", _male_states(2)),
        *[
            Template(f"{g}_{slot}", g, slot, riv, _RUN)
            for g, riv in (("female", "avatar_run_approved.riv"), ("male", "avatar_male_run.riv"))
            for slot in ("run_body", "run_arm", "run_leg", "run_shoe")
        ],
    ]
}

# 서기 아틀라스(512x768) 부위 칸. 남자 서기 메시(avatar_male_idle의 Existing wardrobe top·bottom·shoes, Preserved approved hands)가
# 아틀라스를 나누는 경계를 남녀 공통 칸으로 쓴다(남녀가 같은 아틀라스 좌표, DR-성-1). 칸 이름 → riv Image 이름.
STAND_ZONE_IMAGES: dict[str, tuple[str, ...]] = {
    "TOP": ("Existing wardrobe top", "Long sleeve wardrobe top"),
    "BOTTOM": ("Existing wardrobe bottom",),
    "SHOES": ("Existing wardrobe shoes",),
    "HANDS": ("Preserved approved hands",),
}
STAND_ZONE_RIV = "avatar_male_idle.riv"
# 겹침 띠(5.3): 이 부위가 아래(위) 칸으로 OVERLAP px까지 넘어가도 된다.
OVERLAP_DOWN: dict[str, tuple[str, ...]] = {"TOP": ("BOTTOM", "HANDS"), "BOTTOM": ("SHOES",), "SHOES": ()}
OVERLAP_UP: dict[str, tuple[str, ...]] = {"TOP": (), "BOTTOM": ("TOP",), "SHOES": ("BOTTOM",)}

# 달리기 슬롯은 통째로 한 부위다.
RUN_SLOT_PART = {"run_body": "TOP", "run_arm": "TOP", "run_leg": "BOTTOM", "run_shoe": "SHOES"}

# 맨몸 바탕(5.3): 신발 슬롯을 뺀 서기 1 + 달리기 3, 남녀 각각.
BODY_SLOTS = ("stand", "run_body", "run_arm", "run_leg")
BODY_FOLDER = "body"  # aapt는 _로 시작하는 assets 폴더를 APK에서 뺀다(<dir>_*). 설계의 _body 대신 body

# 기존 21가지(5.7): 부위별 번호 0..6 → 묶음 폴더. 상의 maleTopSurface는 0·1·2 → 1, 3..6 → 2.
LEGACY_KEYS: dict[str, tuple[str, ...]] = {
    "TOP": (
        "base_top",
        "top_lime_singlet",
        "top_crew_tee",
        "top_finisher_tee",
        "top_winter_hoodie",
        "top_dawn_windbreaker",
        "top_cherry_vest",
    ),
    "BOTTOM": (
        "base_bottom",
        "bottom_running_shorts",
        "bottom_leggings",
        "bottom_jogger_pants",
        "bottom_wide_pants",
        "bottom_cargo_shorts",
        "bottom_line_shorts",
    ),
    "SHOES": (
        "base_shoes",
        "shoes_basic_runner",
        "shoes_high_top",
        "shoes_carbon_racer",
        "shoes_slip_on",
        "shoes_ankle_boots",
        "shoes_lime_runner",
    ),
}


def part_of_key(asset_key: str) -> str:
    for part, keys in LEGACY_KEYS.items():
        if asset_key in keys:
            return part
    prefix = asset_key.split("_", 1)[0]
    return {"top": "TOP", "bottom": "BOTTOM", "shoes": "SHOES"}[prefix]


def default_male_top_surface(asset_key: str) -> int:
    keys = LEGACY_KEYS["TOP"]
    return 2 if asset_key in keys and keys.index(asset_key) >= 3 else 1


def stand_template(gender: str, male_top_surface: int) -> str:
    return "female_stand" if gender == "female" else f"male_stand_s{male_top_surface}"
