# Codex CLI에서 작업자 띄우기

`sw-implement` 3·4·7단계의 "작업자를 띄운다", "스킬을 불러온다", "모델을 고른다"를 Codex CLI로 옮긴 표다. 절차와 판정 기준은 SKILL.md가 정하고, 이 문서는 도구 이름과 sandbox 제약만 다룬다.

## 1. 준비 (세션 시작 전 한 번)

1. `node scripts/install-codex.mjs`로 작업자·리뷰어 에이전트(`~/.codex/agents/`)와 ECC 스킬(`~/.agents/skills/`)을 설치한다.
2. 구현 세션은 코드 저장소 루트에서 연다. 다른 경로(스킬 저장소, worktree 상위 폴더)에 써야 하면 `--add-dir <경로>`로 연다.
3. 의존성을 내려받는 빌드(Gradle, pip)는 네트워크가 필요하다. 아래 설정으로 세션을 연다.

```
codex -s workspace-write \
  -c sandbox_workspace_write.network_access=true \
  -c agents.max_concurrent_threads_per_session=3
```

## 2. 도구 대응

| 할 일 | Codex CLI에서 |
|-------|---------------|
| 작업자 띄우기 | "`sw-worker` 에이전트를 띄워 아래 지시서를 맡긴다"처럼 자연어로 지시한다. 독립 단계는 한 번에 "단위마다 하나씩 병렬로" 띄운다. 지시서(`references/agent-brief.md`)는 그대로 붙인다 |
| 상위 모델 | `sw-worker`(추론 강도 high). 모델은 세션 모델 또는 `agents.default_subagent_model`을 따른다 |
| 중위 모델 | `sw-worker-light`(추론 강도 medium). 병합·연결, 리뷰, 결과서 정리, 빌드 복구, 기계적 결함 수정 |
| 스킬 불러오기 | `$스킬이름`. ECC 스킬은 접두어 없이 `$springboot-tdd`처럼 부른다 |
| 자가 리뷰 | `ecc-java-reviewer`, `ecc-kotlin-reviewer`, `ecc-python-reviewer`, `ecc-fastapi-reviewer`, `ecc-security-reviewer` 에이전트를 띄운다 |
| 빌드 복구 | `ecc-java-build-resolver`, `ecc-kotlin-build-resolver` 에이전트. Claude의 `/gradle-build` 명령은 없다 |
| 멈춘 작업자 이어서 | 같은 에이전트 스레드에 `agent-brief.md`의 "이어서" 양식을 보낸다. 스레드가 끝났으면 새 작업자에게 같은 양식과 처음 지시서를 함께 준다 |

## 3. sandbox 제약

| 제약 | 대응 |
|------|------|
| workspace-write는 세션 작업 폴더 밖에 쓰지 못한다 | worktree를 `<저장소>/.worktrees/<단위>`에 만든다. 팀 `.gitignore`를 고치지 않도록 `.git/info/exclude`에 `.worktrees/`를 넣는다. 밖에 두려면 `--add-dir`로 연다 |
| 작업자는 부모의 sandbox와 승인 설정을 그대로 물려받는다 | 에이전트 정의로 읽기 전용을 강제할 수 없다. 리뷰어의 "수정하지 않는다"는 지시문일 뿐이므로 리뷰 뒤 `git status`로 작업 트리가 그대로인지 확인한다 |
| 승인 요청이 다른 작업자 스레드에서 올라온다 | CLI 오버레이에서 해당 스레드를 열어 확인한 뒤 승인한다. 내용을 보지 않고 일괄 승인하지 않는다 |
| Windows의 비대화식 `codex exec`에 `-s workspace-write`만 주면 셸 명령이 모두 "blocked by policy"로 거부된다 | `codex exec --approve-for-me`를 쓴다(`-s`와 함께 쓸 수 없다). 대화식 세션은 승인 요청이 사용자에게 오므로 해당 없다 |
| `codex exec --ephemeral`은 세션 기록을 남기지 않아 작업자 생성이 "no rollout found for thread id"로 실패한다 | 작업자를 띄우는 실행에는 `--ephemeral`을 쓰지 않는다 |
| Windows PowerShell 5.1은 BOM 없는 UTF-8 파일을 시스템 코드 페이지로 읽어 한글이 깨진다 | 파일을 읽을 때 `Get-Content -Encoding utf8`을 쓴다. 지시서의 "실행 환경"에도 적는다 |
| Windows에서 PowerShell로 쓴 파일은 BOM으로 시작한다 | 커밋 전에 `git diff`로 첫 줄을 확인하고, 팀 규칙이 BOM 없는 UTF-8이면 지시서에 "BOM 없이 저장"을 적는다 |
| 병렬 작업자가 같은 폴더를 고치면 충돌한다 | SKILL.md 2단계 규칙 그대로 worktree 하나에 작업자 하나 |

## 4. 주의

- 작업자 결과 보고는 모델 출력이다. 사용자 승인이나 검증 통과로 취급하지 않고 4단계 게이트에서 직접 다시 돌린다.
- 설치된 ECC 스킬이 없으면 `references/ecc-workflows.md` 표의 "할 일" 열을 지시서에 그대로 적어 지시문으로 쓴다.
