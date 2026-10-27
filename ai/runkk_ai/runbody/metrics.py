"""축별 지표. 전부 (1초 그리드, ...) → dict 순수 함수다. 데이터가 모자라면 {"insufficient": True}."""

from __future__ import annotations

import math
from collections.abc import Sequence
from typing import Any

import numpy as np
import pandas as pd

from runkk_ai.runbody.schemas import Metrics, RecoveryHeartRateIn

ZONE_BOUNDS = [0.5, 0.6, 0.7, 0.8, 0.9]  # 최고 심박 비율 경계 → Z1~Z5
MIN_MOVING_S = 120  # 지구력·분할 판단에 필요한 최소 이동 시간
MIN_STEADY_S = 90  # 안정성 판단에 필요한 최소 이동 시간(예열 제외)
MIN_FITNESS_S = 300  # VO2 추정에 필요한 최소 이동 시간
MIN_RUNNING_CADENCE = 100  # 이보다 낮은 케이던스는 걷기로 본다
OVERSTRIDE_CADENCE = 170  # 보폭 과다 판단 기준(코칭 플래그와 5각 폼 라벨이 같이 쓴다)
OVERSTRIDE_STRIDE_M = 1.0
GPS_ACCURACY_OK_M = 20
GPS_JUMP_MPS = 8  # 1초에 이보다 멀리 뛴 점은 잘못 잡힌 위치로 본다
LAP_M = 1000.0
_PARTIAL_LAP_MIN_M = 20
_EARTH_RADIUS_M = 6_371_000.0
_HR_REST = 60  # VO2 근사용 안정 심박(참고값 수준)


def _f(value: Any) -> float:
    return float(value)


# ---------- 강도 ----------
def intensity(g: pd.DataFrame, hr_max: int) -> Metrics:
    hr = g["hr"].dropna()
    if hr.empty:
        return {}
    zones = np.digitize((hr / hr_max).to_numpy(), ZONE_BOUNDS)  # 0=Z0, 1=Z1 ... 5=Z5
    total = max(len(zones), 1)
    pct = {f"z{i}": round(100 * float((zones == i).sum()) / total, 1) for i in range(1, 6)}
    return {
        "hr_zone_pct": pct,
        "hr_max_used": hr_max,
        "avg_hr": round(_f(hr.mean())),
        "max_hr": round(_f(hr.max())),
        "z4_plus_pct": round(pct["z4"] + pct["z5"], 1),
    }


# ---------- 지구력 ----------
def endurance(g: pd.DataFrame) -> Metrics:
    moving = g[g["moving"] & g["hr"].notna()]
    if len(moving) < MIN_MOVING_S:
        return {"insufficient": True}
    half = len(moving) // 2
    first, second = moving.iloc[:half], moving.iloc[half:]
    hr_a, hr_b = _f(first["hr"].mean()), _f(second["hr"].mean())
    sp_a, sp_b = _f(first["speed"].mean()), _f(second["speed"].mean())
    if min(hr_a, hr_b, sp_a, sp_b) <= 0:
        return {"insufficient": True}
    return {
        "hr_drift_bpm": round(hr_b - hr_a, 1),
        "efficiency_change": round((sp_b / hr_b) / (sp_a / hr_a) - 1, 3),
        "pace_decay_s_per_km": round(1000 / sp_b - 1000 / sp_a),
        "first_half_avg_hr": round(hr_a),
        "second_half_avg_hr": round(hr_b),
    }


# ---------- 안정성 ----------
def stability(g: pd.DataFrame, laps: Sequence[Metrics]) -> Metrics:
    moving = g[g["moving"] & ~g["warmup"]].copy()
    if len(moving) < MIN_STEADY_S:
        return {"insufficient": True}
    moving["bin"] = moving["t"] // 30
    bin_speed = moving.groupby("bin")["speed"].mean()
    bin_speed = bin_speed[bin_speed > 0]
    pace_bins = 1000 / bin_speed / 60  # 분/km
    cv = _f(pace_bins.std(ddof=0) / pace_bins.mean()) if len(pace_bins) > 1 else 0.0
    full = [int(lap["pace_s"]) for lap in laps if not lap.get("partial")]
    spread = max(full) - min(full) if len(full) > 1 else 0
    return {
        "pace_cv": round(cv, 3),
        "lap_spread_s": spread,
        "pace_bins_min_per_km": [round(float(x), 2) for x in pace_bins.tolist()],
    }


# ---------- 폼 ----------
def form(g: pd.DataFrame) -> Metrics:
    running = g[g["moving"] & (g["cadence"] > MIN_RUNNING_CADENCE)]
    if running.empty:
        return {"insufficient": True}
    cadence = _f(running["cadence"].median())
    stride = _f((running["speed"] / (running["cadence"] / 60)).mean())
    half = len(running) // 2
    change = 0.0
    if half > 30:
        change = _f(running["cadence"].iloc[half:].median()) - _f(running["cadence"].iloc[:half].median())
    return {"cadence_median": round(cadence), "stride_m": round(stride, 2), "cadence_change_spm": round(change, 1)}


# ---------- 체력 ----------
def fitness(g: pd.DataFrame, hr_max: int, recovery: Sequence[RecoveryHeartRateIn]) -> Metrics:
    moving = g[g["moving"] & g["hr"].notna()]
    out: Metrics = {}
    if len(moving) >= MIN_FITNESS_S:
        # 심박 예비량 대비 속도로 VO2max를 어림한다(ACSM 러닝 공식 + %HRR 근사). 참고값 수준
        hrr = (_f(moving["hr"].mean()) - _HR_REST) / max(hr_max - _HR_REST, 1)
        vo2_at_speed = 3.5 + 0.2 * (_f(moving["speed"].mean()) * 60)
        out["vo2max_est"] = round(vo2_at_speed / max(hrr, 0.3), 1)
    if recovery:
        hr0 = recovery[0].heartRate

        def change_at(seconds: int) -> int | None:
            later = [r.heartRate for r in recovery if r.tMs >= seconds * 1000]
            return round(later[0] - hr0) if later else None

        if (one := change_at(60)) is not None:
            out["recovery_hr_1m"] = one
        if (two := change_at(115)) is not None:
            out["recovery_hr_2m"] = two
    return out


# ---------- 코스·GPS ----------
def _haversine(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp, dl = p2 - p1, math.radians(lon2 - lon1)
    h = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * _EARTH_RADIUS_M * math.asin(math.sqrt(h))


def course(g: pd.DataFrame, distance_m: float) -> Metrics:
    points = g.dropna(subset=["lat", "lon"])
    if len(points) < 10:
        return {"gps": False}
    lock = int(points["t"].iloc[0])
    pre_gps = _f(g.loc[g["t"] < lock, "dist_delta"].sum())
    accurate = points["acc"].fillna(0) <= GPS_ACCURACY_OK_M
    good = points[accurate]
    lats, lons, ts = good["lat"].tolist(), good["lon"].tolist(), good["t"].tolist()
    raw = 0.0
    corrected = 0.0
    jumps = 0
    for i in range(1, len(lats)):
        d = _haversine(lats[i - 1], lons[i - 1], lats[i], lons[i])
        raw += d
        if d / max(ts[i] - ts[i - 1], 1) > GPS_JUMP_MPS:
            jumps += 1
        else:
            corrected += d
    alt = points["alt"].dropna().rolling(15, min_periods=1, center=True).median()  # GPS 고도 잡음 완화
    gain = _f(np.clip(alt.diff().fillna(0).to_numpy(), 0, None).sum()) if len(alt) > 1 else 0.0
    total = corrected + pre_gps
    return {
        "gps": True,
        "gps_lock_delay_s": lock,
        "pre_gps_distance_m": round(pre_gps),
        "gps_accuracy_ok_pct": round(100 * _f(accurate.mean()), 1),
        "gps_accuracy_median_m": _f(points["acc"].median()) if points["acc"].notna().any() else None,
        "gps_raw_distance_m": round(raw),
        "gps_jumps": jumps,
        "distance_corrected_m": round(total),
        "distance_official_m": round(distance_m),
        "distance_error_pct": round(100 * (total - distance_m) / distance_m, 1),
        "altitude_gain_m": round(gain, 1),
    }


# ---------- 랩 ----------
def laps(g: pd.DataFrame, lap_m: float = LAP_M) -> list[Metrics]:
    out: list[Metrics] = []
    start_t, start_d, km = 0, 0.0, 1
    times, dists = g["t"].tolist(), g["dist_cum"].tolist()
    for t, dist in zip(times, dists, strict=True):
        if dist - start_d >= lap_m:
            seg = g[(g["t"] > start_t) & (g["t"] <= t)]["hr"]
            avg_hr = round(_f(seg.mean())) if seg.notna().any() else None
            out.append({"km": km, "pace_s": int(t - start_t), "avg_hr": avg_hr})
            start_t, start_d, km = int(t), start_d + lap_m, km + 1
    remaining = dists[-1] - start_d
    if remaining > _PARTIAL_LAP_MIN_M:
        elapsed = int(times[-1] - start_t)
        out.append({"km": round(remaining / 1000, 2), "pace_s": int(elapsed / remaining * 1000), "partial": True})
    return out
