# 병합 대기: hotfix/S15P31A207-300-avatar-head-slim

> 작성: 2026-11-17 | 작성자: 이성현 | 입력: 운영 측정(Prometheus 7일, nginx 로그), 핫픽스 커밋 666bcbd1

## 변경 요약

홈 아바타 첫 표시가 4~6초 걸리던 원인은 `GET /api/v1/avatar/heads/{ownerId}/{jobId}` 응답 하나의 크기(압축 전 11.8MB, 전송 8.4~9.0MB)와 앱이 켤 때마다 다시 받는 것이었다(7일 p50 1.66초, p95 4.5초, 클라이언트 끊김 약 15%). 서버만으로 두 가지를 고쳤다.

- 머리 응답에서 앱이 쓰지 않는 `frontImage`, `rightImage`, `mobile.renderFaceImage`를 뺀다(약 44% 축소). 셀카 작업 결과 등 다른 응답은 그대로다.
- 같은 응답에 강한 ETag(`"head-v2-<소유자·작업 ID SHA-256 앞 16바이트>"`)와 `Cache-Control: no-cache, private`를 준다. If-None-Match가 맞으면 공개 규칙 검사만 거쳐 ai를 부르지 않고 304를 준다.

## 공용 파일 반영

- 계약 문서(W8 contract, 얼굴 비동기 SRS)의 머리 조회 응답 필드 목록에서 세 필드를 빼고, 캐시 정책을 no-store에서 위 ETag 재검증으로 고친다.
- 위험 R-102(plan 09-2) 회피책 "응답을 private 캐시 가능으로"를 반영됨으로 표시한다.

## 운영 규칙

- 같은 작업 ID의 머리 내용이 바뀌는 변경(avatar-ai가 저장된 머리를 다시 만들어 덮어쓰는 경우, 응답 형식 변경)을 배포할 때는 `SelfieJobController.HEAD_FORMAT`을 올린다. 올리지 않으면 앱이 옛 사본을 계속 쓴다.

## 다음 앱 버전 후속

- 내 머리를 디스크에 남기고 마지막 얼굴 키로 바로 합성 시작(`SelfFaceStore`, `SelfieHeads`, `MyAvatarStore.load`). 첫 표시 0.5~1초 목표.
- 같은 머리 두 번 받기 제거, JSON 한 번만 파싱, 얼굴 이미지 HTTP 캐시 상한 10MB → 64MB.
