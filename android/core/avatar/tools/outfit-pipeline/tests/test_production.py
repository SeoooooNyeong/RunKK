"""제작 스크립트 시험: Codex 실패는 0이 아닌 종료로 알리고, 검수 렌더는 정해진 작업 폴더만 지우며, 묶음 빌드는 실패해도 이전 결과를 남긴다."""

from __future__ import annotations

import os
import shutil
import subprocess
import sys
from pathlib import Path

import pytest
from PIL import UnidentifiedImageError

from outfit_pipeline import spec
from outfit_pipeline.build import build_bundle
from outfit_pipeline.cli import MASKS

PRODUCTION = Path(__file__).resolve().parents[1] / "production"


@pytest.fixture
def review():
    sys.path.insert(0, str(PRODUCTION))
    try:
        import review as module

        yield module
    finally:
        sys.path.remove(str(PRODUCTION))


def test_clear_work_removes_only_a_folder_inside_the_root(tmp_path: Path, review) -> None:
    root = tmp_path / "out"
    work = root / "_scene"
    (work / "inner").mkdir(parents=True)
    other = tmp_path / "keep"
    other.mkdir()
    for wrong in (other, root, tmp_path, root / ".." / "keep"):
        with pytest.raises(ValueError):
            review.clear_work(wrong, root)
    assert other.is_dir() and root.is_dir()
    review.clear_work(work, root)
    assert not work.exists()
    review.clear_work(work, root)  # 없으면 그대로


@pytest.mark.skipif(shutil.which("bash") is None, reason="bash 없음")
def test_codex_failure_is_recorded_and_exits_nonzero(tmp_path: Path) -> None:
    fake = tmp_path / "fake_codex.sh"
    fake.write_text("#!/bin/bash\necho boom >&2\nexit 3\n", encoding="utf-8")
    fake.chmod(0o755)
    prompt = tmp_path / "prompt.txt"
    prompt.write_text("draw", encoding="utf-8")
    work = tmp_path / "work"
    work.mkdir()
    env = {**os.environ, "CODEX": fake.as_posix()}
    result = subprocess.run(
        # Windows의 이름만 "bash"는 WSL bash.exe를 먼저 찾으므로 PATH에서 찾은 경로를 쓴다
        [str(shutil.which("bash")), (PRODUCTION / "codex.sh").as_posix(), work.as_posix(), "one", prompt.as_posix()],
        env=env,
        capture_output=True,
        text=True,
        check=False,
    )
    assert result.returncode != 0
    log = (work / "codex.log").read_text(encoding="utf-8")
    assert "failed one" in log and "exit 3" in log


def test_build_bundle_keeps_previous_files_when_a_layer_is_broken(tmp_path: Path) -> None:
    outfits = tmp_path / "outfits"
    target = outfits / "base_shoes"
    target.mkdir(parents=True)
    (outfits / spec.BODY_FOLDER).mkdir()
    previous = target / f"female_stand{spec.EXTENSION}"
    previous.write_bytes(b"previous")
    source = tmp_path / "src"
    source.mkdir()
    (source / "female_stand.png").write_bytes(b"not an image")
    with pytest.raises(UnidentifiedImageError):
        build_bundle(source, outfits, MASKS, "base_shoes")
    assert previous.read_bytes() == b"previous"
    assert {p.name for p in outfits.iterdir()} == {spec.BODY_FOLDER, "base_shoes"}, "임시 폴더를 남기지 않는다"
