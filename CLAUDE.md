@AGENTS.md

# Claude Code 전용 메모

- ECC 플러그인(`ecc@ecc`)이 프로젝트 스코프로 켜져 있다(`.claude/settings.json`). 처음이면 `npx ecc-universal setup`으로 설치하거나 갱신한다. 훅 프로필은 `standard`.
- GateGuard 훅이 문서 작업을 번번이 막으면 세션 시작 전에 `ECC_DISABLED_HOOKS=pre:bash:gateguard-fact-force,pre:edit-write:gateguard-fact-force`를 설정한다. 코드 작업에서는 켜 둔다.
- 커맨드 대응: 이슈 `/jira get KEY` · 계획 `/plan` · TDD `/tdd` · 빌드 복구 `/build-fix`(backend), `/gradle-build`(android) · 검증 `verification-loop` 스킬 · 리뷰 `/code-review`, `/kotlin-review` · 세션 `/save-session`, `/resume-session`, `/learn`.
- `/pr`은 GitHub 전용이라 쓰지 않는다. MR은 아래처럼 열고 GitLab에서 템플릿 본문을 채운다.

```bash
git push -u origin <branch> -o merge_request.create -o merge_request.target=develop \
  -o "merge_request.title=<타입>: <내용> [<Jira 키>]" -o "merge_request.description=Closes <Jira 키>" \
  -o merge_request.squash -o merge_request.remove_source_branch
```

- 릴리스 수정 `fix/…`는 `target=release/vX.Y`로 연다. `release/*`·`hotfix/*` → `main`과 `main` → `develop` MR은 `merge_request.squash`를 빼서 merge commit으로 병합하고, release 브랜치는 역병합이 끝날 때까지 남기도록 `remove_source_branch`도 뺀다.

- 병렬 에이전트는 Agent 도구의 `isolation: "worktree"`로 띄운다.
- 참고 스킬: springboot-patterns, springboot-tdd, jpa-patterns, android-clean-architecture, kotlin-coroutines-flows, kotlin-testing(Kotest 전제이므로 명령은 AGENTS.md의 Gradle 작업을 쓴다), git-workflow, jira-integration.
- 로컬 환경 메모: Windows. `gradlew` 호출 전 JDK 17 이상이 `JAVA_HOME`에 있어야 한다. 에뮬레이터 AVD `runkk_api36`.
