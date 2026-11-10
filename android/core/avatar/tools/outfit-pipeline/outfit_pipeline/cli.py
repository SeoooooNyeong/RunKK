"""명령: uv-mask, templates, build, check. 경로 기본값은 이 도구 폴더 기준(android/core/avatar)."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

from outfit_pipeline import spec
from outfit_pipeline.build import build_bundle, update_index
from outfit_pipeline.check import check_all
from outfit_pipeline.masks import write_masks
from outfit_pipeline.templates import write_templates

TOOL = Path(__file__).resolve().parents[1]
MODULE = TOOL.parents[1]
RAW = MODULE / "src/main/res/raw"
OUTFITS = MODULE / "src/main/assets/outfits"
MASKS = TOOL / "templates/masks"
TEMPLATES = TOOL / "templates"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="outfit-pipeline")
    parser.add_argument("--raw", type=Path, default=RAW, help="riv 폴더(res/raw)")
    parser.add_argument("--outfits", type=Path, default=OUTFITS, help="assets/outfits")
    parser.add_argument("--masks", type=Path, default=MASKS)
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("uv-mask", help="riv 메시 UV로 허용 영역 마스크를 만든다")
    sub.add_parser("templates", help="슬롯 템플릿 11장을 만든다")
    build = sub.add_parser("build", help="원본 폴더를 규격 묶음으로 옮긴다")
    build.add_argument("source", type=Path, help="원본 폴더(<source>/<assetKey>/*.png)")
    build.add_argument("keys", nargs="*", help="옮길 assetKey(없으면 전부)")
    sub.add_parser("check", help="assets/outfits 전체 규격 검사(5.5)")
    args = parser.parse_args(argv)

    if args.command == "uv-mask":
        for path in write_masks(args.raw, args.masks):
            print(path.relative_to(args.masks.parent))
        return 0
    if args.command == "templates":
        for path in write_templates(args.outfits, args.raw, TEMPLATES):
            print(path.name)
        return 0
    if args.command == "build":
        keys = args.keys or sorted(p.name for p in args.source.iterdir() if p.is_dir())
        failed = False
        for key in keys:
            entry, problems = build_bundle(args.source / key, args.outfits, args.masks, key)
            update_index(args.outfits, entry)
            for problem in problems:
                print(problem)
            failed |= bool(problems)
            print(f"{key}: {'FAIL' if problems else 'ok'}")
        return 1 if failed else 0
    problems = check_all(args.outfits, args.masks)
    for problem in problems:
        print(problem)
    print(f"{len(problems)} problem(s), {len(spec.TEMPLATES)} templates")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
