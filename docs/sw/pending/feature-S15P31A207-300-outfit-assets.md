# 병합 대기: feature/S15P31A207-300-outfit-assets

> 작성: 2026-11-10 | 작성자: 이성현(에이전트 구현) | 스킬: sw-implement 5a-B(의상 자산 규격·파이프라인·21가지 이전·배경 4장), 상위 S15P31A207-300

설계 [design-oo 05-30](../design-oo/05-30-설계-아바타요구-성.md) 5절(D-성-110~116)과 4.3절(배경 규격)을 구현했다. 구현하며 설계와 다르게 정한 것과 설계에 없던 것을 아래 결정으로 남긴다. 공용 담당이 `sw-sdlc merge`로 05-30 1.2 표에 옮긴다.

## 새 임시 번호

| 임시 번호 | 종류 | 한 줄 설명 | 위치(옮길 곳) |
|-----------|------|------------|---------------|
| [임시:D-성-150] | 결정 | 맨몸 바탕 폴더는 `assets/outfits/_body`가 아니라 `assets/outfits/body`다. aapt 기본 무시 규칙 `<dir>_*`가 `_`로 시작하는 assets 폴더를 APK에서 뺀다(로보렉트릭 시험에서 확인) | 05-30 5.3 「맨몸 바탕」, `OUTFIT_SPEC.md` 2절 |
| [임시:D-성-151] | 결정 | `AvatarOutfit`은 이번 브랜치에서 번호(0..6) 그대로 둔다. 번호 → 묶음 키 대응은 `:core:avatar` `OutfitCatalog.LEGACY_KEYS`(5.7 표)가 하고, `:feature:avatar` `RIVE_OUTFITS`는 바꾸지 않는다(화면 코드·통합 브랜치 API를 건드리지 않기 위해). `AvatarOutfit(assetKey)` 공개 API 전환(5.4)은 통합 단계에서 한 MR로 한다 | 05-30 5.4 `AvatarOutfit` 행 |
| [임시:D-성-152] | 결정 | 맨몸 바탕의 「속옷 선」은 연회색 민소매 속옷(가는 끈, 깊은 팔 구멍) + 연회색 속바지다. 맨 몸통 그림은 그림 생성 도구가 안전 정책으로 막았다. 목·가슴 위는 피부로 채우고, 손·팔·다리 등 기본 의상에서 보이던 피부는 승인 기본 텍스처 픽셀을 그대로 썼다. 서기 어깨는 승인 기본 텍스처 실루엣 안으로 잘랐다(옷 밖으로 피부 테두리가 비치지 않게) | 05-30 5.3 「맨몸 바탕」 |
| [임시:D-성-153] | 결정 | 상점 키가 없는 6가지의 디자인: 와이드 팬츠 베이지 카키 치노, 카고 쇼츠 올리브, 라인 쇼츠 네이비 + 흰 줄 둘, 하이탑 로열 블루 캔버스, 슬립온 차콜 캔버스, 앵클 부츠 갈색 가죽. 상점 키가 있는 12가지는 상점 썸네일 원본(Codex 재작업본)과 같은 옷이다. `top_cherry_vest`는 썸네일대로 단색 코랄 집업 후드(안감 크림)로 다시 그렸다 | 05-30 5.7 |
| [임시:D-성-154] | 결정 | 배경 5장(기본 낮·밤, 국내·동아시아·대륙 너머)을 4.3 규격(1080x1200, 바닥선 y 984, 가운데 40% 바닥 비움, 위 20%·양옆 15% 랜드마크 없음)으로 다시 그려 `core/ui` `drawable-nodpi/*.webp`로 바꿨다. 단계 그림은 벡터 XML을 지우고 같은 리소스 이름의 webp다(코드 변경 없음). 톤은 기존과 같다(국내 밤 남색, 동아시아 자두빛 노을, 대륙 너머 깊은 바다 밤) | 05-30 4.3, D-성-125 |

## 고친 곳
- `android/core/avatar/OUTFIT_SPEC.md`(새), `tools/outfit-pipeline`(새, uv·Pillow: `uv-mask`, `templates`, `build`, `check`, pytest)
- `android/core/avatar/src/main/assets/outfits/`(새, index.json + 21묶음 + 맨몸 8장), 합성 경로(`OutfitCatalog`, `OutfitLibrary`, `OutfitLayerComposer`, `AvatarTextureComposer`), 벡터 의상 코드 다섯 파일과 피부 판정 삭제
- 시험: `OutfitAssetSpecTest`, `OutfitCompositeTest`(새), 기존 시험 정정
