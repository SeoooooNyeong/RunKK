# Writer 모델 관리 설계

> 작성 2026-11-09 | v0.39(2026-11-09 병합, CR-179) | S15P31A207-243, D-446, FR-268~271

- `web.admin.model`은 기존 관리자 인터셉터의 actorId를 사용한다. `aimodel.AiModelService`는 입력과 URL 허용 목록을 확인하고 감사 기록을 남긴다.
- `infra.integration.ai.AiServerModelGateway`는 전용 HTTP 연결에 내부 토큰과 읽기 제한을 적용한다. 등록·활성화·롤백의 순서와 모델 저장은 AI의 writer 등록부가 맡는다. 변경 호출을 자동 재시도하지 않는다.
- `RUNKK_WRITER_MODEL_URLS`는 쉼표로 나눈 허용 URL이며 기본값은 `http://writer:8080`이다. 끝의 슬래시를 빼고 전체 URL을 대조한다. AI에 직접 등록한 모델과 배포 시드의 주소는 등록부 운영자가 관리한다.
- 관리 응답에서 기존 주소의 사용자 정보·쿼리·프래그먼트를 가린다. API 인스턴스 안에서 변경 요청을 한 건씩 처리한다. AI에 직접 들어오는 요청은 AI 등록부의 동시성 범위를 따른다.
- writer 연결이 꺼진 배포에서는 AI가 템플릿 문구를 계속 사용한다. writer의 모델 변경은 코치 모델에 적용되지 않는다.
- 등록·활성화·롤백 전 REQUESTED 감사를 먼저 커밋한다. AI의 정상 응답 뒤 SUCCEEDED를 남긴다. 서비스 사이 통신이 끊기면 REQUESTED만 남을 수 있으므로 결과를 재조회한다.
