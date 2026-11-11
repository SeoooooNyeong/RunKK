"""명령: uv-mask, templates, build, body, strip-skin, clean-body, check, stand-neck, render-check. 경로 기본값은 이 도구 폴더 기준(android/core/avatar)."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

from outfit_pipeline import spec
from outfit_pipeline.build import build_body, build_bundle, clean_assets, strip_bundles, update_index
from outfit_pipeline.check import check_all
from outfit_pipeline.masks import write_masks
from outfit_pipeline.templates import write_templates

TOOL = Path(__file__).resolve().parents[1]
MODULE = TOOL.parents[1]
RAW = MODULE / "src/main/res/raw"
OUTFITS = MODULE / "src/main/assets/outfits"
AVATARS = MODULE / "src/main/assets/avatars"
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
    build.add_argument("source", type=Path, help="원본 폴더(<source>/<assetKey>/*.png|webp)")
    build.add_argument("keys", nargs="*", help="옮길 assetKey(없으면 전부)")
    body = sub.add_parser("body", help="맨몸 바탕 원본 폴더를 body/(무손실 WebP)와 상의 자르기 칸으로 옮긴다")
    body.add_argument("source", type=Path, help="원본 폴더(<source>/{g}_{stand|run_body|run_arm|run_leg}.png)")
    sub.add_parser("strip-skin", help="옷 레이어에서 드러난 맨살을 지운다(검사 9번)")
    sub.add_parser("clean-body", help="맨몸 바탕·자르기 칸·옷 레이어의 부스러기·속옷을 정리한다(검사 10·11번)")
    sub.add_parser("check", help="assets/outfits 전체 규격 검사(5.5)")
    sub.add_parser("stand-fix", help="서기 마무리: 자르기 칸, 긴소매 칼라 안감, 신발이 맨발 덮기(판정 round3)")
    render = sub.add_parser("render-check", help="체형 100 서기 렌더의 떠도는 조각·비치는 틈 검사(Rive CLI 필요)")
    render.add_argument("--rive", type=Path, required=True, help="Rive CLI 실행 파일")
    render.add_argument("--rml", type=Path, required=True, help="Rive 원본 폴더(PoC tools, scene.rml)")
    render.add_argument("--shape", type=int, default=100, help="체형 0·50·100")
    render.add_argument("--out", type=Path, help="렌더를 저장할 폴더")
    render.add_argument("keys", nargs="*", help="assetKey(없으면 21가지 전부)")
    args = parser.parse_args(argv)

    if args.command == "stand-fix":
        from outfit_pipeline.stand_fix import fix_stand

        for name, count in fix_stand(args.outfits, args.raw, args.masks).items():
            print(f"{name}: {count}")
        return 0
    if args.command == "render-check":
        from outfit_pipeline.render_check import check_renders

        problems = check_renders(args.outfits, args.rive, args.rml, AVATARS, args.keys, args.shape, args.out)
        for problem in problems:
            print(problem)
        print(f"{len(problems)} problem(s)")
        return 1 if problems else 0

    if args.command == "uv-mask":
        for path in write_masks(args.raw, args.masks):
            print(path.relative_to(args.masks.parent))
        return 0
    if args.command == "templates":
        for path in write_templates(args.outfits, args.raw, TEMPLATES):
            print(path.name)
        return 0
    if args.command == "body":
        shared = build_body(args.source, args.outfits, args.raw)
        update_index(args.outfits, body_shared=shared)
        print(f"body: {len(shared)} shared")
        return 0
    if args.command == "strip-skin":
        for name, count in strip_bundles(args.outfits).items():
            print(f"{name}: {count}")
        return 0
    if args.command == "clean-body":
        for name, count in clean_assets(args.outfits, args.raw, args.masks).items():
            print(f"{name}: {count}")
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
