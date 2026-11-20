# 병합 대기: feature/S15P31A207-300-avatar-layer-heads

> 작성: 2026-11-17 | 작성자: 이성현(에이전트 작성) | 입력: 운영 측정(2026-11-17, 핫픽스 v0.13.2 뒤), 핫픽스 병합 대기 `hotfix-S15P31A207-300-avatar-head-slim.md`

## 변경 요약

아바타 머리를 "Base64 PNG 여러 장이 든 JSON 한 개" 대신 "이미지 없는 메타 + 층별 PNG"로 받는다(레이어 합성 방식).

- 서버: 새 엔드포인트 두 개(메타, 층). 머리마다 ai를 한 번만 불러 원본 층과 줄인 층을 크기 상한 있는 메모리 캐시에 둔다. 기존 `GET /heads/{ownerId}/{jobId}`는 그대로다.
- 앱
  - 얼굴 원(랭킹·크루·크루런·채팅·계정 등 다른 러너): 정면 세 층(face·eyes·hair)만 512로 받는다. 메타는 받지 않는다(얼굴 원 구도는 층 알파로 잰다).
  - 내 얼굴 원과 홈 큰 아바타(Rive): 같은 1024 층을 쓴다. 같은 주소는 받는 중 표로 합쳐 한 번만 보낸다(첫 실행에 같은 머리를 두 번 받던 문제를 없앰).
  - 큰 아바타: 메타 + 1024 층 여섯 장(정면 셋, 옆모습 셋). 앱이 쓰지 않는 완성 원본(frontImage·rightImage)과 mobile.renderFaceImage는 받지 않는다.
  - 층·메타는 서버가 immutable로 주어 OkHttp 디스크 캐시(상한 10MB → 64MB)가 앱을 다시 켜도 서버에 묻지 않고 준다. 앱을 새로 켰을 때 디스크 캐시를 남긴 회원이 같으면 비우지 않는다(`SelfFaceStore.owner/claim`). 계정이 바뀌거나 로그아웃하면 비운다.
  - `MyAvatarStore.warm`이 프로필(성별) 응답을 기다리지 않고 기기에 남긴 마지막 얼굴 키(`AvatarRepository.savedSelfFaceKey`)로 내 머리를 받아(디스크면 바로) 합성을 시작한다.
  - 서버가 새 주소를 모르면(404·405) 기존 통짜 응답으로 받는 대체 경로를 남겼다.
  - `GeneratedHeadLayers.fromLayers`: 층 바이트와 메타로 Rive 머리를 만든다. 통짜 JSON(`fromHeadJson`)과 같은 결과다(픽셀 비교 시험).

## API 계약

### GET /api/v1/avatar/heads/{ownerId}/{jobId}/meta

- 권한: 기존 머리와 같다(`SelfieJobService.requireVisibleHead`, 탈퇴·없는 회원 403, 없는 작업 404 `JOB_NOT_FOUND`, 끝나지 않았거나 실패한 작업 404 `HEAD_NOT_FOUND`). 매 요청 확인한다.
- 200 `application/json`:

```json
{"format":"png1","headId":"…","canvas":1024,"traits":{…},"hairLength":"short","alignment":{…},
 "rightLayerLayout":{…},"mobile":{"neckMask":{"centerX":512,"chinY":750},"skinColor":[200,150,120]},
 "layers":["face","eyes","hair","rightFace","rightEyes","rightHair"],"createdAt":"…"}
```

  - `mobile`은 ai가 준 값에서 이미지(Data URL) 값만 뺀 것이다. `rightLayerLayout`은 없으면 빠진다. `layers`는 이 머리에 있는 층(예전 머리는 정면 셋만).
- 머리글: `Cache-Control: max-age=31536000, private, immutable`, `ETag: "meta-png1-<SHA-256(owner:job:meta:png1) 앞 16바이트 hex>"`. If-None-Match가 맞으면 공개 규칙만 확인하고 ai를 부르지 않은 채 304.

### GET /api/v1/avatar/heads/{ownerId}/{jobId}/layers/{layer}?size={256|512|1024}

- `layer` ∈ `face`, `eyes`, `hair`, `rightFace`, `rightEyes`, `rightHair`(앱 합성이 실제로 쓰는 여섯 장: `GeneratedAvatarLayers.prepare`, `PortraitHeadFrame`). 모르는 이름·크기는 400 `BAD_REQUEST`. 이 머리에 없는 층은 404 `{"reason":"LAYER_NOT_FOUND"}`. `size`를 빼면 1024.
- 200 `image/png`: 1024는 ai 원본 PNG 바이트 그대로(다시 인코딩하지 않음). 512·256은 1024 공통 캔버스를 비율 그대로 2×2 알파 가중 평균으로 거듭 줄인 무손실 PNG(투명도 유지).
- 머리글: `Cache-Control: max-age=31536000, private, immutable`, `ETag: "layer-png1-<SHA-256(owner:job:layer:size:png1) 앞 16바이트 hex>"`, If-None-Match면 304(공개 규칙만 확인).
- 권한: 메타와 같다. 매 요청 확인한다.

### 서버 캐시

- `HeadLayerCache`: (소유자, 작업)마다 ai `result`를 한 번 받아 원본 층과 메타를 둔다. 512·256은 그 층·크기가 처음 요청될 때 한 번 만든다(얼굴 원은 정면 셋만, 큰 아바타는 1024만 써서 쓰지 않는 축소로 첫 응답을 늦추지 않으려고 모든 크기를 미리 만들지 않았다).
- 상한: 담은 바이트 합 64MiB(`runkk.avatar.head-layer-cache-bytes`, 운영 api 힙 약 0.9GiB의 7%), 가장 오래 안 쓴 것부터 버린다. 같은 키 동시 요청은 하나만 받거나 만들고 나머지는 기다린다. 실패는 담지 않는다.

### 인코딩 결정(WebP 대신 PNG)

- JDK ImageIO에 WebP 인코더가 없다. 무손실 WebP는 네이티브 libwebp(JNI, 예: webp-imageio) 의존성을 운영 이미지에 들여야 하고, 1024 층 무손실 인코딩은 장당 수백 ms라 첫 요청이 지금보다 늦어진다.
- 손실 WebP는 앱이 알파·밝기 경계로 목·턱·눈을 찾는 합성(`GeneratedHeadGeometry`, `PortraitHeadFrame`)을 바꿀 수 있다.
- 그래서 1024는 원본 PNG를 그대로 주고(합성 결과가 통짜 응답과 비트 단위로 같다), 512·256은 무손실 PNG로 준다. 의존성 추가는 없다. 나중에 형식을 바꾸면 `HeadLayerCache.FORMAT`을 올린다.

## 기존 엔드포인트를 남긴 이유

- 운영 v0.13.x 앱은 `GET /heads/{ownerId}/{jobId}`만 안다. 서버를 먼저 배포해도 기존 앱이 그대로 동작해야 한다.
- 새 앱은 새 주소가 404·405면 이 주소로 대체하므로, 서버 배포가 늦어도 새 앱이 깨지지 않는다.
- 기존 앱 사용률이 충분히 줄면 별도 이슈로 지운다.

## 측정 근거와 예상 효과

| 항목 | 이전(v0.13.2) | 이번 |
|---|---|---|
| 다른 러너 얼굴 원(셀카 러너 1명) | 머리 JSON 약 5.8~6.6MB(Base64 6층) | 512 PNG 3장(1024 원본 장당 약 0.7~0.8MB의 1/4 화소, 어림 0.5~0.8MB) |
| 큰 아바타 첫 실행 | 같은 JSON을 두 경로가 따로 받음(최대 약 12MB), JSON 두 번 파싱, PNG 풀고 다시 인코딩 | 메타 + 1024 PNG 6장 한 번(어림 4.4~5MB, Base64·JSON 없음) |
| 재실행(같은 머리) | 내 머리 디스크 캐시 없음, ETag 304로 재검증 왕복 | 디스크(immutable)에서 0바이트, 프로필 응답 전에 합성 시작 |
| 서버 처리 | 요청마다 ai 중계 + 검사·재직렬화 약 1.3초 | 머리당 ai 1회, 뒤 요청은 메모리 캐시 |

- 실제 층 PNG 크기는 운영 머리로 다시 재야 한다(시험 도형으로는 대표성이 없다). 배포 뒤 nginx 로그의 `/layers/` 응답 크기와 `/heads/` 요청 수 감소로 확인한다.

## 운영 규칙과 위험

- 층·메타는 앱 디스크에 1년(immutable) 남는다. 같은 작업 ID의 머리를 avatar-ai가 다시 만들어 덮어쓰면 앱은 옛 그림을 계속 쓴다(디스크 캐시 LRU 밀림·계정 변경·앱 데이터 삭제 전까지). 머리를 다시 만들 때는 새 작업 ID를 쓴다. 형식 변경은 `HeadLayerCache.FORMAT`을 올려도 이미 받은 앱 사본은 바뀌지 않는다.
- 탈퇴·공개 범위 변경 뒤에도 이미 받은 다른 러너 층은 그 기기 디스크에 남는다(서버는 새 요청마다 권한을 확인한다). 화면은 서버가 준 얼굴 키로만 찾으므로 새로 보이지는 않는다.
- 배포 순서: 서버 먼저(기존 앱 영향 없음, 새 엔드포인트만 더함) → 앱. 앱 먼저여도 404 대체 경로로 동작한다.

## 공용 파일 반영

- 계약 문서(W8 contract 6.2, 얼굴 비동기 SRS)에 위 두 엔드포인트와 캐시 정책을 더한다. 머리 조회는 "통짜(호환용)"와 "메타 + 층"으로 나눠 적는다.
- 서버 설계(design-oo) avatar 패키지에 `HeadLayer`, `HeadMeta`, `HeadLayerCache`, `HeadLayerImages`, `HeadLayerService`를, 앱 설계에 `FaceImageSource` 층 받기·`SelfFaceStore.owner/claim`·`AvatarRepository.savedSelfFaceKey`·`GeneratedHeadLayers.fromLayers`를 더한다.
- 위험 R-102(plan 09-2)에 위 "같은 작업 ID 재생성 시 옛 그림" 위험을 덧붙인다.
