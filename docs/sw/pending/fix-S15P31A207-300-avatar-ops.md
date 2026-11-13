# 병합 대기: fix/S15P31A207-300-avatar-ops

> 작성: 2026-11-13 | 작성자: 이성현 | 스킬: sw-maintain(운영 보강, 2차 릴리스 v0.12)

## 고친 조각
- 없음(코드·infra/README.md만). backup 서비스가 avatar-data 볼륨을 읽기 전용으로 붙여 매일 DB 덤프와 같은 시각에 `runkk-<환경>-avatar-daily-*.tar.gz`를 같은 폴더·같은 7일 보존·0600으로 남긴다(배포 직전 덤프는 DB만). offsite-backup.sh가 묶음도 같은 26시간 규칙으로 올린다(50MB 초과는 경고 뒤 제외). smoke.sh가 COMPOSE_PROFILES에 avatar가 있으면 avatar healthy와 ai → avatar /api/health를 확인한다.

## 새 임시 번호
- 없음

## 공용 파일 반영
| 파일 | 넣을 내용 |
|------|-----------|
| NFR-018·019·037 근거(백업 대상) | 백업 대상에 아바타 결과 볼륨(사용자별 머리 그림)을 더한다는 한 줄. 서버 밖 사본은 묶음이 50MB 이하일 때만 |

## 다른 담당에게 요청
- 없음
