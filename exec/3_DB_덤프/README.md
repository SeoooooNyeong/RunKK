# DB 덤프

| 파일 | 내용 |
|---|---|
| `runkk_schema.sql` | 운영 DB(`runkk-prod-postgres-1`, DB `runkk`) 스키마 덤프. 테이블·제약·인덱스·트리거·함수만 있고 행 데이터는 없다 |

- 뜬 날짜: 2026-11-18. PostgreSQL 17.12, `pg_dump --schema-only --no-owner --no-privileges`. 운영 DB는 읽기만 했다.
- 덤프 시점 Flyway 버전은 V67(성공 62건)이고 main의 `backend/src/main/resources/db/migration`과 같다.
- 스키마 두 개: `public`(api 서버, Flyway 테이블 68개), `analytics`(ai 서버, alembic 테이블 5개).
- 소유자·권한 문은 뺐다. 그래서 `runkk_ai`, `runkk_grafana_ro` 계정 권한은 이 파일에 없다(계정은 Flyway V29와 `infra/monitoring/grafana/runkk-db/readonly-role.sql`이 만든다).

## 데이터를 넣지 않은 이유

회원 정보, 러닝 경로, 심박 같은 개인정보가 있어 데이터는 넣지 않았다. 시험용 더미 데이터는 `infra/dev-seed.sql`로 넣는다(CI 수동 잡 `dev:seed`, develop 환경 전용, `infra/README.md` 4절). 게스트형 더미 회원 18명, 공개 크루 5개, 회원마다 최근 6주 러닝 10~25회가 들어간다.

## 복원 방법

새 DB에 스키마만 만들 때:

```bash
createdb -U postgres runkk
psql -U postgres -d runkk -f runkk_schema.sql
```

- 파일 첫 줄 근처의 `\restrict`와 끝의 `\unrestrict`는 pg_dump 17.6 이후 psql이 쓰는 안전 장치다. 그보다 오래된 psql이면 이 두 줄을 지우고 넣는다.
- 보통은 이 파일 대신 api 서버(develop·prod 프로필)를 빈 DB에 띄우면 Flyway가 같은 스키마를 만든다. 이 파일은 구조 확인용이다.
