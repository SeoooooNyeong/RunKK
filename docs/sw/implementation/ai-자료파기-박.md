# AI 자료 파기 구현 기록

> 작성: 2026-11-04 | 버전: v0.13 | 입력: S15P31A207-244, 사용자 승인 정책, ai-자료파기 설계·시험

탈퇴 BEFORE_COMMIT에서 AiPurgeRequest를 등록하고 AFTER_COMMIT에서 AiDataEraser를 호출한다. MemberPurged에서는 첫 성공 여부와 관계없이 필요한 7일 재확인을 수행한다.

매일 03시 05분 배치는 기존 탈퇴 회원 누락 요청을 먼저 보정한 뒤 due 요청을 처리한다. 실패는 다음 날짜로 예약하고 7일 이후에도 재시도한다. 첫 경과 실패는 AI_DATA_PURGE_OVERDUE 감사와 ERROR 로그를 남긴다.

AiServerClient의 privacy 연결은 기존 내부 토큰과 기준 주소를 사용하며 읽기 제한은 15초다. 응답에서 sessionResults·coachMessages를 검사하고 선택적인 avatarImages 0 또는 1을 허용한다. 토큰·URL·원격 오류 본문은 로그에 넣지 않는다.

V37과 새 감사 enum을 포함한다. 앱과 공개 HTTP 계약은 바뀌지 않는다. 운영 환경변수는 추가하지 않는다.

코드와 문서는 feature/S15P31A207-244-ai-privacy에서 검토한다. 기존 #241 계약 초안은 이번 변경에 포함하지 않는다.

## 최종 로컬 검증

2026-11-04에 JDK 21로 backend spotlessApply build를 실행했다. 전체 1,808건 중 실행 1,653건 통과, 실패·오류 0건, Docker 의존 155건 건너뜀이다. #244 신규 계약 관련 24건이 통과했고 Java·보안 재검토에 남은 조치 필요 지적은 없다. 실제 AI·Node 삭제·운영 배치·DB 적용은 확인 전이다.
