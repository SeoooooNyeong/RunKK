"""그림 도구에 줄 참고 그림을 작업 폴더 in/에 만든다: 기본 의상 슬롯 텍스처를 2배(달리기 몸통·신발은 4배)로 키운 투명 PNG.

usage: uv run python production/prepare_inputs.py --work <작업 폴더>
"""

from __future__ import annotations

import argparse
from pathlib import Path

from common import default_texture, embedded, work_paths
from PIL import Image

SCALES = {"stand": 2, "run_body": 4, "run_arm": 2, "run_leg": 2, "run_shoe": 4}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--work", type=Path, required=True)
    args = parser.parse_args()
    out = work_paths(args.work)["in"]
    for slot, scale in SCALES.items():
        image = default_texture(slot)
        image.resize((image.width * scale, image.height * scale), Image.Resampling.LANCZOS).save(
            out / f"{slot}_default.png"
        )
    coral = embedded("avatar_idle_layered.riv", "standing_body")  # 승인 코랄 집업 후드(품질 참고)
    coral.resize((coral.width * 2, coral.height * 2), Image.Resampling.LANCZOS).save(out / "stand_coral_hoodie.png")
    print(out)


if __name__ == "__main__":
    main()
