# A207 자율프로젝트 온보딩

A207 팀에 합류한 분을 위한 첫 안내 문서입니다. 자세한 규칙, 회의록, 기획 자료는 모두 팀 위키(AppFlowy)에 있으니 **이 문서를 읽은 뒤 위키 홈으로 이동**하면 됩니다.

## 1. 팀 위키 홈으로 가기

- **A207 Home**: https://appflowy.kktrkkt.dev/app/60f43234-b29b-4fe8-b1e1-3528d8815da1/ded3ab26-2325-441d-b99a-8c36784884d1
- 접속 조건
  - SSAFY **내부망**에서만 열립니다.
  - Google 계정으로 로그인합니다. 워크스페이스 초대 링크는 팀장에게 요청하세요.

홈 페이지에서 바로 갈 수 있는 것들:

| 항목 | 내용 |
| --- | --- |
| 정규 회의 | 매일 09:30 |
| 멤버 | 팀원 소개와 역할 |
| 팀 규칙 | 협업 시 지켜야 할 규칙 |
| 컨벤션 | 코드·커밋·브랜치 컨벤션, 개발 사이클(ECC 하네스) |
| 기획서 | 기획안, 타당성 검토, 일정표(WBS·간트·PERT), 요구사항 명세서, 기술 스택 |
| 회의록 | 날짜별 회의 기록 (템플릿 포함) |
| 회고록 | 스프린트 회고 |
| 문서 | 기술 가이드 모음 (Git Workflow, AppFlowy API 사용 가이드 등) |
| 외부 링크 | 영상 포트폴리오, GitHub 저장소, Figma |

## 2. 작업 도구

| 도구 | 주소 | 용도 |
| --- | --- | --- |
| Git 저장소 | https://lab.ssafy.com/s15-final/S15P31A207 | 소스 코드, MR |
| Jira 백로그 | https://ssafy.atlassian.net/jira/software/c/projects/S15P31A207/boards/16675/backlog | 이슈와 스프린트 관리 |
| 팀 위키 | 위 A207 Home 링크 | 규칙, 회의록, 기획 문서 |

## 3. 개발 환경

저장소는 모노레포입니다. 폴더 구조·실행 명령·브랜치 규칙은 루트 `AGENTS.md`와 위키 **컨벤션** 에 있습니다.

| 영역 | 필요한 것 | 처음 확인할 명령 |
| --- | --- | --- |
| 백엔드 (`backend/`) | JDK 17 이상 (JDK 21은 Gradle이 내려받음) | `./gradlew build` |
| 안드로이드 (`android/`) | Android SDK Platform 37, `local.properties`에 `sdk.dir` | `./gradlew assembleLocalDebug` |
| 서버 스택 (`infra/`) | Docker Desktop (EC2 배정 뒤 이관) | `infra/README.md` 참고 |

- 코드 규칙은 위키 **컨벤션 > Code Convention** 을 따릅니다. 포맷은 도구가 정하므로 커밋 전에 백엔드 `./gradlew spotlessApply`, 앱 `./gradlew ktlintFormat`을 실행합니다.
- 브랜치는 `{feature|fix|hotfix}/{Jira 키}-{작업명}`, 커밋·MR 제목은 `{타입}: {내용} [{Jira 키}]`. `main`·`develop`·`release/*`에는 직접 push 하지 않고 MR로만 반영합니다. 평소 작업은 develop으로, 배포는 `release/vX.Y` → `main`으로, 배포 버전의 긴급 수정은 `hotfix/…` → `main`으로 올립니다(`AGENTS.md` "브랜치 흐름"). MR 본문은 템플릿이 자동으로 채워지고, `Closes {Jira 키}`를 적으면 병합 시 Jira가 자동으로 Done 됩니다.

## 4. AI 코딩 도구 (Claude Code, Codex)

팀은 ECC(everything-claude-code) 플러그인을 하네스로 씁니다. 규칙은 저장소 루트의 `AGENTS.md`(Claude Code·Codex 공통)와 `CLAUDE.md`(Claude 전용 메모)에 있고, 두 도구 모두 clone 뒤 자동으로 읽습니다.

| 도구 | 설치 | 비고 |
| --- | --- | --- |
| Claude Code | `npx ecc-universal setup` → 스코프 `project`, 훅 프로필 `standard` | `.claude/settings.json`에 플러그인과 권한이 들어 있어 clone만 하면 같은 구성 |
| Codex CLI | `npx ecc-universal install --guided --harness codex` | 프로젝트 설정은 `.codex/config.toml`. Codex에는 훅이 없어 포맷·빌드·테스트를 직접 돌려야 합니다 |

- 한 이슈를 어떻게 진행하는지(이슈 선택 → 브랜치 → 계획 → 테스트 먼저 → 구현 → 검증 → 리뷰 → MR → 병합)는 위키 **컨벤션 > 런크크 개발 사이클 (ECC 하네스)** 를 따릅니다. 백엔드와 안드로이드 각각의 체크리스트와 ECC 커맨드 대응표가 거기 있습니다.
- Jira 연동: `/jira get {키}`로 이슈를 읽고, 브랜치를 만들면서 In Progress로 바꿉니다.

## 5. 첫날 체크리스트

1. 위키 홈에 접속해 **멤버** 페이지에 본인 정보를 추가합니다.
2. **팀 규칙**과 **컨벤션** 페이지를 읽습니다.
3. Jira 프로젝트에 초대받았는지 확인하고 백로그를 한번 훑어봅니다.
4. 저장소를 클론하고 담당 영역의 빌드가 통과하는지 확인합니다.

   ```bash
   git clone https://lab.ssafy.com/s15-final/S15P31A207.git
   cd S15P31A207/backend && ./gradlew build        # 백엔드
   cd ../android && ./gradlew assembleLocalDebug    # 안드로이드
   ```

5. AI 코딩 도구를 설정합니다(4장). Claude Code에서 `/ecc-guide`가 뜨면 됩니다.
6. 위키 **컨벤션 > 런크크 개발 사이클** 을 읽고, 첫 이슈를 그 순서대로 진행합니다.
7. 매일 09:30 정규 회의에 참석합니다. 회의록은 위키의 **회의록** 템플릿으로 작성합니다.

## 6. 궁금한 점이 있으면

- 규칙이나 일정 관련: 위키 **팀 규칙** 페이지를 먼저 확인한 뒤 팀장에게 문의합니다.
- 위키 API로 문서를 자동 생성하고 싶다면: 위키 **문서 > API 사용 가이드**를 참고합니다.
