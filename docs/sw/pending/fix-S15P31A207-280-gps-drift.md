# 병합 대기 - fix/S15P31A207-280-gps-drift

> 작성: 2026-11-09 | 작성자: 박재영 | 스킬: sw-maintain, sw-srs, sw-design-oo, sw-code-review, sw-test

## 고친 조각

- [유지보수 요청](../maintenance/2026-11-09_러닝-GPS-흔들림-박.md)
- [러닝 측정 요구](../srs/03-02-러닝-측정.md)
- [GPS 설계](../design-oo/러닝-GPS-흔들림-박.md)
- [GPS 시험](../test/러닝-GPS-흔들림-박.md)
- [GPS 리뷰](../code-review/러닝-GPS-흔들림-박.md)

## 새 임시 번호

| 임시 번호 | 종류 | 내용 |
|---|---|---|
| [임시:CR-박-1] | CR | 실내·정지 GPS 흔들림 완화 |
| [임시:FR-박-1] | FR | 폰 러닝 정지 중 위치 흔들림 완화 |
| [임시:D-박-1] | D | 속도 추정치 기반 좌표 고정 |
| [임시:TC-박-1]~[임시:TC-박-5] | TC | GPS 고정·이동 재개·기본 동작·수동 재개·실기기 |

## 공용 파일 반영

| 파일 | 넣을 내용 |
|---|---|
| srs.md 및 공용 추적표 | 변경 요청과 폰 러닝 위치 보정 요구를 연결한다. |
| design-oo/06-06b 및 06-07 | `LocationSample` 입력과 `LocationFilter` 좌표 고정, A-02 우선순위를 반영한다. |
| design-review.md 및 test.md | 기존 의존 방향 검토와 GPS 회귀 시험의 추적을 반영한다. |
