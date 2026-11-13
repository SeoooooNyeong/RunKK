# 병합 대기: fix/S15P31A207-275-backup-volume-cleanup

> 작성: 2026-11-10 | 작성자: 이성현 | 스킬: sw-maintain(운영 결함 수정)

## 고친 조각
- 없음(코드만). infra/restore-check.sh, infra/offsite-backup.sh의 정리 함수가 `docker rm -f`로 컨테이너만 지워 postgres 이미지의 익명 데이터 볼륨이 남았다. `docker rm -fv`로 바꿨다.

## 경위와 영향
- 2026-11-09 서버 점검에서 연결 없는 익명 볼륨 22개를 찾아 지웠다: 복구 점검이 남긴 postgres 데이터 10개(45~71MB, 복원한 운영 백업 사본일 수 있음, 개인정보), 매일 오프사이트 백업이 남긴 빈 볼륨 12개(4KB).
- 서버에서 `docker rm -fv`가 익명 볼륨을 함께 지우는 것을 일회용 컨테이너로 확인했다.

## 공용 파일 반영
| 파일 | 넣을 내용 |
|------|-----------|
| infra/README.md 7절(백업과 복구) | 복구 점검과 오프사이트 복사는 끝나면 익명 볼륨까지 지운다는 한 줄 |
| 운영 기록 | 2026-11-09 EC2 디스크 91%: /srv/gitlab-runner/cache 브랜치 캐시 154GB 정리(41%), /etc/cron.d/runkk-runner-cache-cleanup으로 매일 04:30 KST 3일 지난 cache.zip 자동 삭제 |

## 다른 담당에게 요청
- 없음
