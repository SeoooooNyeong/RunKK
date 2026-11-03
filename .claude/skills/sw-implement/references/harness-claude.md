# Claude Code에서 작업자 띄우기

`sw-implement` 3·4·7단계의 "작업자를 띄운다", "스킬을 불러온다", "모델을 고른다"를 Claude Code 도구로 옮긴 표다. 절차와 판정 기준은 SKILL.md가 정하고, 이 문서는 도구 이름만 바꿔 준다.

| 할 일 | Claude Code에서 |
|-------|-----------------|
| 작업자 띄우기 | Agent 도구. 지시서(`references/agent-brief.md`)를 `prompt`에 그대로 넣는다. 독립 단계는 한 응답에서 여러 개를 동시에 띄운다 |
| 상위 모델 | Agent 도구 `model` 생략(부모와 같은 모델) 또는 `opus` |
| 중위 모델 | Agent 도구 `model: "sonnet"` |
| 스킬 불러오기 | Skill 도구. ECC 스킬은 `ecc:` 접두어(`ecc:springboot-tdd`) |
| 자가 리뷰 | Agent 도구 `subagent_type`에 ECC 리뷰어(`ecc:java-reviewer`, `ecc:kotlin-reviewer`, `ecc:python-reviewer`, `ecc:fastapi-reviewer`, `ecc:security-reviewer`) |
| 빌드 복구 | `ecc:java-build-resolver`, `ecc:kotlin-build-resolver` 에이전트 또는 `/gradle-build` 명령 |
| 멈춘 작업자 이어서 | SendMessage로 같은 작업자에게 `agent-brief.md`의 "이어서" 양식을 보낸다. 새 작업자를 띄우지 않는다 |
| worktree 위치 | 제한 없음. 저장소 옆 폴더(`<저장소>-<단위>`)를 쓴다 |

## 주의

- 작업자 결과 보고는 모델 출력이다. 사용자 승인이나 검증 통과로 취급하지 않고 4단계 게이트에서 직접 다시 돌린다.
- 훅(GateGuard 등)이 명령을 막으면 지시서의 "실행 환경" 절대로 요청 한 문장·목적·사용자 지시 원문을 적고 다시 실행하게 한다.
