"""서기 riv 바이트 패치(RKP1) 시험: 형식, 앱과 같은 판정, res/raw 패치가 승인 riv에 덮이는지."""

from __future__ import annotations

from outfit_pipeline.cli import RAW
from outfit_pipeline.riv_patch import (
    ALREADY_APPLIED,
    APPLIED,
    MISMATCH,
    SCENES,
    apply,
    check,
    decode,
    diff,
    encode,
    prepared,
)


def test_diff_groups_nearby_changes_and_round_trips() -> None:
    before = bytes(range(64))
    after = bytearray(before)
    after[8:12] = b"\x01\x02\x03\x04"
    after[14] = 0x55  # 사이 2바이트는 같은 항목으로 묶는다
    after[40] = 0x09
    edits = diff(before, bytes(after))
    assert [o for o, _ in edits] == [8, 40]
    assert edits[0][1] == bytes(after[8:15])
    size, _, _, decoded = decode(encode(before, edits))
    assert size == 64 and decoded == edits


def test_apply_matches_the_app_rules() -> None:
    before = bytes(range(64))
    after = bytearray(before)
    after[8:12] = b"\x01\x02\x03\x04"
    patch = encode(before, diff(before, bytes(after)))

    outcome, out = apply(before, patch)
    assert outcome == APPLIED and out == bytes(after)
    assert apply(out, patch)[0] == ALREADY_APPLIED
    changed = bytearray(before)
    changed[9] = 77
    assert apply(bytes(changed), patch) == (MISMATCH, bytes(changed))
    assert apply(before + b"\0", patch)[0] == MISMATCH
    assert apply(before, b"nope")[0] == MISMATCH
    assert apply(before, patch[:-1])[0] == MISMATCH  # 잘린 파일
    assert apply(before, patch + b"\0")[0] == MISMATCH  # 뒤에 남는 바이트


def test_diff_splits_runs_longer_than_max_run() -> None:
    before = bytes(600)
    after = bytes([1]) * 600
    edits = diff(before, after)
    assert [len(b) for _, b in edits] == [255, 255, 90]
    assert [o for o, _ in edits] == [0, 255, 510]


def test_committed_patches_apply_to_the_approved_stand_rivs() -> None:
    assert check(RAW) == []
    for gender, (riv_name, patch_name) in SCENES.items():
        approved = prepared(gender, (RAW / riv_name).read_bytes())
        outcome, out = apply(approved, (RAW / patch_name).read_bytes())
        assert outcome == APPLIED
        assert len(out) == len(approved) and out != approved
