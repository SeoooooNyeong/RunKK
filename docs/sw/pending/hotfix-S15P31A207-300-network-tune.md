# 병합 대기: hotfix/S15P31A207-300-network-tune

> 작성: 2026-11-18 | 작성자: 이성현 | 입력: 운영 측정(Prometheus 7일, nginx 로그), 회원 머리 6레이어 압축 실측, 커밋 cddfa953·d0b58fb5와 이 브랜치 후속 커밋

## 변경 요약

머리 응답 축소·ETag 핫픽스(hotfix/S15P31A207-300-avatar-head-slim)에 이어, 연결 재사용과 머리 응답 자체의 바이트를 줄였다.

### nginx (cddfa953)

- 쉬는 연결 유지 `keepalive_timeout` 65초 → 300초(앱 OkHttp 연결 풀 5분에 맞춤). TLS 세션 캐시(`ssl_session_cache shared:SSL:10m`, `ssl_session_timeout 1d`)로 다시 맺을 때 전체 핸드셰이크를 줄인다.
- `/api/v1/avatar/` 경로만 프록시 버퍼를 키운다(`proxy_buffer_size 128k`, `proxy_buffers 64 128k`, 요청당 최대 약 8MB 메모리, 넘는 부분은 전처럼 임시 파일). 수 MB 머리 응답을 임시 파일에 썼다 읽지 않는다.
- 접근 로그 끝에 `$server_port $request_time $upstream_response_time`를 덧붙인다(앞 항목 위치 그대로). 443·8443 구분과 느린 응답 확인용.
- 봇 요청(점으로 시작하는 경로 조각, `/.well-known/` 제외 / 서버 측 스크립트 확장자 / 흔한 CMS·관리 도구 경로)을 api로 넘기지 않고 444로 끊는다.

### 머리 PNG 압축 (d0b58fb5, 이 브랜치 후속)

- 결정: 2026-11-18 PM이 팔레트 q95(`HEAD_PNG_COMPRESSION=palette`)를 운영에 쓰기로 정했다. 실측 회원 머리 6레이어 합계 -82%, 합성 PSNR 약 51dB. 크기 1024×1024·PNG·알파는 그대로이고 앱은 PNG 서명과 크기만 본다.
- 대상: 다른 러너에게 보내는 6레이어(face, eyes, hair, rightFace, rightEyes, rightHair)만. 원본(master)·합성(composite)·우측 전체(right)·`mobile.renderFaceImage`는 그대로다.
- 운영·develop: `infra/docker-compose.yml` avatar 환경에 `HEAD_PNG_COMPRESSION: ${HEAD_PNG_COMPRESSION:-palette}`. 코드 기본값과 로컬 `avatar-ai/compose.yaml`은 off. 압축이 실패하거나 결과가 같거나 크면 원본을 그대로 저장한다.
- 이미 저장된 머리: `avatar-ai/scripts/recompress-heads.mjs`로 `heads/<머리 ID>-{face,eyes,hair,right-face,right-eyes,right-hair}.png`와 `jobs/<작업 ID>/result.json`의 같은 6레이어를 줄인다. 이미 팔레트면 건너뛰고(멱등), 커지면 그대로, 파일마다 임시 파일 → rename, 읽은 뒤 바뀐 파일은 건너뛴다. 옵션 `--data`, `--dry-run`, `--limit`. 기록에는 개수·바이트만 남긴다. avatar 이미지에 `scripts/`를 넣었다.
- backend: `SelfieJobController.HEAD_FORMAT` v2 → v3. 머리 응답 ETag가 `head-v3-…`가 되어 압축 전 사본(v2)을 가진 앱이 한 번 다시 받는다.

## 운영 절차 (infra/README.md 4절 「아바타 머리 PNG 압축」)

1. 배포로 palette를 켠다(health의 `headPngCompression`이 palette인지 확인).
2. avatar 볼륨(`runkk-<환경>_avatar-data`)을 tar.gz로 백업한다(`/var/backups/runkk/<환경>/runkk-<환경>-avatar-before-recompress-<시각>.tar.gz`, 7일 보존).
3. `docker exec runkk-<환경>-avatar-1 node scripts/recompress-heads.mjs --data /app/data --dry-run`으로 대상 수·예상 감소량을 본다.
4. `--limit 20`으로 몇 개만 바꿔 앱에서 확인한 뒤 전체를 돌린다.
5. 되돌리기: `HEAD_PNG_COMPRESSION=off`로 다시 배포하고 7절 「실제 복구」의 아바타 볼륨 절차로 백업 묶음을 푼다(백업 뒤 만든 머리는 잃는다).

## 공용 파일 반영

- 계약 문서(W8 contract, 얼굴 비동기 SRS)의 머리 조회 캐시 설명에서 ETag 형식 버전을 `head-v3`로 고친다. 레이어 PNG가 팔레트(색 형식 3 + tRNS)일 수 있음을 적는다(크기·알파는 그대로).
- 위험 R-102(plan 09-2)에 「머리 레이어 팔레트 압축(-82%)」을 회피책으로 더한다.
- 운영 설정 목록(자료사전 또는 설계서의 환경 변수 표)에 `HEAD_PNG_COMPRESSION`(palette | lossless | off, 운영 기본 palette)을 더한다.

## 위험

- 배포 뒤 재압축 전에 머리를 받은 앱은 압축 전 사본을 v3 태그로 들고 있어 그 머리는 다시 받지 않는다(화면은 같고 절감만 놓친다). 배포 직후 바로 재압축한다.
- 팔레트는 RGBA를 함께 양자화하므로 부드러운 알파 경계가 조금 움직인다(시험: 불투명 픽셀 최소 알파 224 이상, 투명 픽셀이 보이게 되는 비율 0.01% 미만). 실기기 확인 뒤 문제가 있으면 off로 되돌린다.
- 재압축 중 같은 파일을 서버가 다시 쓰면 그 파일은 건너뛴다. 사용이 적은 시간에 돌린다.
