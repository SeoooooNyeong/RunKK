"""서기 riv 바이트 패치(RKP1) 만들기·읽기·덮기. 앱 쪽은 RivBytePatch.kt.

승인 riv는 그대로 두고, 앱이 읽은 바이트(검수 바탕·그림자 숨김, 남자 서기 소매 이음매 닫기 뒤)와 목표 riv의 차이만 패치 파일로 둔다
(2026-11-12 PM: 허리 네모·엉덩이 뼈 묶음·체형 2·3단계 키). 형식(빅엔디언): b"RKP1", int32 riv 길이, 바꾸기 전 바이트를 이은 SHA-256,
바꾼 뒤 SHA-256, int32 개수, 항목마다 (int32 자리, uint8 길이, 바이트).
"""

from __future__ import annotations

import hashlib
import struct
from pathlib import Path

from outfit_pipeline.seam import close_seam, keep_overlap

MAGIC = b"RKP1"
MAX_RUN = 255
APPLIED, ALREADY_APPLIED, MISMATCH = "APPLIED", "ALREADY_APPLIED", "MISMATCH"
SCENES = {
    "male": ("avatar_male_idle.riv", "avatar_male_idle_patch.bin"),
    "female": ("avatar_idle_layered.riv", "avatar_female_idle_patch.bin"),
}


def prepared(gender: str, approved: bytes) -> bytes:
    """앱이 패치를 덮기 직전 바이트(메시 자리 기준). 남자 서기는 소매 이음매 닫기·겹침 지키기 뒤(RivSleeveSeam).

    앱은 그 앞에 검수 바탕·바닥 그림자 숨김(RivReviewBackground·RivGroundShadow)도 하지만 메시 자리를 건드리지 않아 여기서는 뺀다.
    그래서 check()는 메시 자리 기준 확인이고, 앱 전처리 전체 뒤 APPLIED는 앱 시험 RivBytePatchTest가 본다.
    """
    return keep_overlap(close_seam(approved)) if gender == "male" else approved


def diff(before: bytes, after: bytes, join: int = 3) -> list[tuple[int, bytes]]:
    """바뀐 바이트를 자리 순서로 묶는다(사이 join바이트 이하 같은 바이트는 한 항목, 항목은 MAX_RUN바이트 이하)."""
    if len(before) != len(after):
        raise ValueError("riv 길이가 다르다")
    changed = [i for i in range(len(before)) if before[i] != after[i]]
    edits: list[tuple[int, bytes]] = []
    start = end = -1
    for i in changed:
        if start >= 0 and i - end <= join and i + 1 - start <= MAX_RUN:
            end = i + 1
            continue
        if start >= 0:
            edits.append((start, after[start:end]))
        start, end = i, i + 1
    if start >= 0:
        edits.append((start, after[start:end]))
    return edits


def _sha(parts: list[bytes]) -> bytes:
    h = hashlib.sha256()
    for p in parts:
        h.update(p)
    return h.digest()


def encode(before: bytes, edits: list[tuple[int, bytes]]) -> bytes:
    out = bytearray(MAGIC)
    out += struct.pack(">i", len(before))
    out += _sha([before[o : o + len(b)] for o, b in edits])
    out += _sha([b for _, b in edits])
    out += struct.pack(">i", len(edits))
    for o, b in edits:
        out += struct.pack(">iB", o, len(b)) + b
    return bytes(out)


def decode(patch: bytes) -> tuple[int, bytes, bytes, list[tuple[int, bytes]]]:
    if patch[:4] != MAGIC:
        raise ValueError("RKP1 패치가 아니다")
    size, at = struct.unpack_from(">i", patch, 4)[0], 8
    before, after = patch[at : at + 32], patch[at + 32 : at + 64]
    at += 64
    count = struct.unpack_from(">i", patch, at)[0]
    at += 4
    if not 0 <= count <= (len(patch) - at) // 5:
        raise ValueError("항목 수가 파일 길이와 맞지 않다")
    edits = []
    for _ in range(count):
        o, n = struct.unpack_from(">iB", patch, at)
        at += 5
        if o < 0 or at + n > len(patch):
            raise ValueError("잘린 항목")
        edits.append((o, patch[at : at + n]))
        at += n
    if at != len(patch):
        raise ValueError("뒤에 남는 바이트")  # 앱(RivBytePatch.parse)과 같은 엄격도
    return size, before, after, edits


def apply(riv: bytes, patch: bytes) -> tuple[str, bytes]:
    """앱(RivBytePatch.applyInPlace)과 같은 판정."""
    try:
        size, before, after, edits = decode(patch)
    except (ValueError, struct.error):
        return MISMATCH, riv
    if size != len(riv) or any(o < 0 or o + len(b) > len(riv) for o, b in edits):
        return MISMATCH, riv
    current = _sha([riv[o : o + len(b)] for o, b in edits])
    if current == after:
        return ALREADY_APPLIED, riv
    if current != before:
        return MISMATCH, riv
    out = bytearray(riv)
    for o, b in edits:
        out[o : o + len(b)] = b
    return APPLIED, bytes(out)


def build(raw: Path, gender: str, target: Path) -> tuple[Path, int]:
    """목표 riv(target)와 앱이 읽은 승인 riv의 차이로 res/raw 패치 파일을 쓴다. (경로, 항목 수)."""
    riv_name, patch_name = SCENES[gender]
    before = prepared(gender, (raw / riv_name).read_bytes())
    edits = diff(before, target.read_bytes())
    path = raw / patch_name
    path.write_bytes(encode(before, edits))
    return path, len(edits)


def check(raw: Path) -> list[str]:
    """res/raw 패치가 승인 riv에 덮이는지(APPLIED) 본다. 문제 목록."""
    problems = []
    for gender, (riv_name, patch_name) in SCENES.items():
        path = raw / patch_name
        if not path.exists():
            problems.append(f"{patch_name}: 없음")
            continue
        outcome, _ = apply(prepared(gender, (raw / riv_name).read_bytes()), path.read_bytes())
        if outcome != APPLIED:
            problems.append(f"{patch_name}: {outcome}")
    return problems
