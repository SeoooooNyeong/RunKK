# 병합 대기: chore/S15P31A207-300-temp-refs-cleanup

> 작성: 2026-11-15 | 작성자: 이성현(공용 담당, PM 지시로 모든 담당 몫 대신 처리) | 스킬: 없음(코드 주석 정리와 infra/README.md 보충, 새 번호 없음)

docs/sw 밖 코드 주석의 임시 번호를 정식 번호로 바꾸고, infra/README.md에 빠진 운영 메모 4가지를 더했다. 새 번호는 없다. 아래는 공용 파일에 남길 기록과 요청이다.

## 1. 코드 주석의 임시 번호 → 정식 번호

- 근거: 병합 커밋 c827c6bb 본문의 대응 규칙(D-성-n → D-(632+n), FR-성-n → FR-(321+n), NFR-성-n → NFR-(57+n), DF-성-n → DF-(69+n), H-성-1~5 → H-71~H-75, DR·DV·AR은 숫자 그대로, TC-성-18~426 → TC-(10000+n))과 그 커밋의 문서 diff에서 실제로 바뀐 쌍을 맞춰 보았다. 이벤트 시험(S15P31A207-275)의 TC-성-n은 srs 07-21 대응표(TC-성-1~177 → TC-2601~TC-2777)를 따랐다.
- 겹친 임시 번호: D-성-10~14는 Grafana 이벤트 현황(D-860~D-867)과 겹치지만, 코드의 그 번호는 모두 피그마 맞춤 묶음(blame 44b56b2c, S15P31A207-289)이고 내용(MetaRow, 크루런 대기실 얼굴, 일시정지 표시, 첫 크루런 표시 제거)이 D-642~D-646 행과 같아 D-642~D-646으로 바꿨다. D-성-159는 문서 diff에 D-782·D-791 두 값이 나오나 코드(셀카 조회 정책)는 D-791 행과 같다.
- 바꾼 곳: `[임시:…]` 411곳과 꺾쇠 없이 쓴 임시 번호(예: `D-성-110~116`, `DV-성-9`) 223곳. android 211개 파일, backend 50개, ai 5개. 주석·문서 문자열·시험 표시 이름만 바뀌었다.
- 남긴 곳(Flyway 체크섬): `V49__event_seed_fresh_db.sql` 1행 `[임시:TZ-성-1]`(코드 리뷰 결함 TZ → PL9-B1~B6 중 어느 것인지 대응표에 없음), `V65__landmark_remove_kaesong.sql` 1행 `D-성-124, D-성-129`(= D-756, D-761). 적용된 마이그레이션 파일을 고치면 Flyway 검증으로 api가 뜨지 않으므로 그대로 둔다.
- 처리 완료로 표시할 곳: code-review 27 PL12-A18(코드 주석 임시 번호)과 PL12-B9(`[임시:D-성-219]` → D-851), design-oo.md 「다음 단계(v0.40)」의 「코드 주석의 임시 번호는 코드 MR에서 바꾼다」, code-review 25 끝의 「코드 주석에는 임시 번호가 남아 있다」 문장.

## 2. infra/README.md 보충 (maintenance/2026-11-10_운영보강-백업-성.md 3절 「남은 일」)

- 7절 복구 리허설: 복구 점검·오프사이트 복사가 `docker rm -fv`로 익명 볼륨까지 지운다는 문장, 손 리허설 명령도 `-fv`로 고쳤다.
- 3절: `SCHEDULER_POOL_SIZE` 27 이상(주기 작업 27개, `BootstrapConfigTest`).
- 3절 「2차 배포 기능 공개 스위치」 표: `COACH_ENABLED` 행(기본 false, 403 `FEATURE_CLOSED`, 동의 없으면 `CONSENT_REQUIRED`).
- 3절 새 소절 「서버 시간대 마이그레이션 V48」: 하는 일, V49, 배포 때 주의, 적용 뒤 확인.
- 남은 일: 4절 운영 주의(9시간 규칙)를 「한국 시각 그대로」로 바꾸는 일은 운영 V48 적용을 문서·코드로 확인하지 못해 하지 않았다. 운영 적용을 확인하면 그 줄을 바꾸고 DF-073을 닫는다. maintenance 3절 「남은 일」은 이 한 줄만 남는다.

## 3. 공용 담당에게 요청

- design-review의 DR-10, DR-13 행 「관련 결정」 칸에 임시 번호 꼴(D-104, D-105, D-117)이 남아 있다. 정식으로는 D-736, D-737, D-749다(확인 필요).

## 4. 검증(2026-11-15, JDK 21)

| 명령 | 결과 |
|------|------|
| backend `./gradlew spotlessApply` 뒤 `spotlessCheck compileJava compileTestJava` + 표시 이름이 바뀐 시험 15개 클래스 | 통과, 289건 실패 0 (spotless가 주석·표시 이름 문자열 줄바꿈만 다시 맞춤) |
| android `./gradlew ktlintFormat` 뒤 `ktlintCheck`, `:app:compileLocalDebugKotlin`, `:app:compileLocalDebugUnitTestKotlin`, 표시 이름이 바뀐 시험 39개 클래스(13개 모듈) | 통과, 437건 실패 0 |
| ai `uv run ruff format .`, `uv run ruff check .` | 통과(바뀐 곳은 주석과 docstring) |
| `git diff -U0`에서 번호 외 바뀐 줄 | 주석·표시 이름 문자열의 줄바꿈뿐 |
