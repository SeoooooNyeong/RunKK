# 아바타 의상 자산 규격

원본은 설계서 [design-oo 05-30 5절](../../../docs/sw/design-oo/05-30-설계-아바타요구-성.md)(D-성-110~116, 설계 검증 DR-성-1·3·4)이다. 두 곳이 다르면 설계서가 맞다. 이 파일은 수치를 그대로 옮기고, 구현에서 정한 것(템플릿 사용법, 파이프라인 명령, 맨몸 바탕 폴더 이름)을 더한다.

## 1. riv 이미지 슬롯

riv 네 파일(`res/raw`, 승인본 SHA 고정, 읽기만)의 아트보드는 800x1000이다. 앱은 같은 이름의 슬롯 이미지를 합성한 PNG로 바꿔 넣는다(`FrontAvatarAssetLoader`, `RunAvatarAssetLoader`).

| riv | 의상 슬롯(px) |
|-----|---------------|
| `avatar_idle_layered`(여 서기) | `standing_body` 512x768(정면 몸 아틀라스) |
| `avatar_male_idle`(남 서기) | `standing_body` 512x768(여자와 같은 아틀라스 좌표). 입력 `selected_top`(1 반팔·민소매, 2 긴소매)·`selected_bottom`(1)·`selected_shoes`(1)이 이 아틀라스를 쓰는 메시 표면을 고른다. 표면 0(`male_selected_body` 1122x1402, riv 내장 기본 옷 몸)은 쓰지 않는다 |
| `avatar_run_approved`·`avatar_male_run`(달리기) | `side_body_coral` 256x384(몸통), `continuous_arm` 256x1024(팔·소매), `side_leg_skin` 256x768(엉덩이·다리), `side_shoe` 256x160(신발). `side_body_cream`·`side_body_blue`에도 몸통과 같은 그림을 넣는다 |

체형(`body_shape` 0·50·100)은 메시 변형이라 텍스처 한 장이 늘어나 맞는다.

메시 실측(`outfit-pipeline uv-mask`): 여자 서기 메시(Regional·Smooth standing body)는 512x768 전체를 덮는 격자다. 남자 서기 메시는 아틀라스를 띠로 나눈다: 상의 표면 y 0~319(가운데 x 101~412는 y 319까지, 바깥은 y 304까지), 손 x 0~101·412~511 y 304~416, 하의 y 319~624, 신발 y 624~767. 이 네 칸을 남녀 공통 부위 칸으로 쓴다. 달리기 팔 메시는 팔 실루엣을 따라가고(위 7%는 보이지 않음), 몸통·다리는 거의 전체, 신발은 메시 없이 전체가 보인다.

## 2. 묶음과 파일 이름

폴더 `src/main/assets/outfits/<assetKey>/`, 목록 `src/main/assets/outfits/index.json`. `{g}` = `female` | `male`, `{b}` = 생략(체형 0) | `_b1` | `_b2`(선택).

| 부위 | 필수 파일 | 크기(px) |
|------|-----------|----------|
| 상의 | `{g}_stand{b}.png`, `{g}_run_body{b}.png`, `{g}_run_arm{b}.png` | 512x768, 256x384, 256x1024 |
| 하의 | `{g}_stand{b}.png`, `{g}_run_leg{b}.png` | 512x768, 256x768 |
| 신발 | `{g}_stand{b}.png`, `{g}_run_shoe{b}.png` | 512x768, 256x160 |
| 모두 | `shop.png`(파이프라인이 만든다) | 512x512 |

`index.json` 항목: `assetKey`, `part`(`TOP`|`BOTTOM`|`SHOES`), `maleTopSurface`(상의만, 1 또는 2), `bodyOverrides`(있는 체형 파일 이름, 확장자 없이).

맨몸 바탕은 `outfits/body/{g}_{stand|run_body|run_arm|run_leg}.png` 8장이다. 설계서의 `_body` 대신 `body`를 쓴다: aapt는 `_`로 시작하는 assets 폴더를 APK에서 뺀다(기본 무시 규칙 `<dir>_*`).

## 3. 그림 규칙

| 항목 | 규칙 |
|------|------|
| 좌표 | 슬롯 텍스처와 같은 크기·좌표. 템플릿(아래 4절)을 밑그림으로 깔고 그린다 |
| 허용 영역 | 그 텍스처를 쓰는 riv 메시의 UV 덮임(화면에 보이는 픽셀) 안, 그리고 그 부위 칸 안. 위 부위가 아래 부위를 덮는 겹침 띠 8px(상의 밑단 ↔ 하의 허리, 하의 밑단 ↔ 신발 목)는 양쪽 허용 |
| 메시 밖 금지 | 옷 실루엣은 몸 메시 UV 안에서만 보인다. 넓은 옷도 영역 안에서 주름·음영으로 부피를 낸다 |
| 바탕 | RGBA, 옷이 아닌 곳은 알파 0. 반투명은 가장자리 안티에일리어싱 1px만 |
| 그 부위만 | 상의 파일에 하의·신발 픽셀 없음(금지 영역은 템플릿의 빨간 빗금) |
| 피부 | 의상 PNG에 피부를 그리지 않는다. 민소매 어깨·반바지 다리는 알파 0으로 두어 맨몸 바탕이 보이게 한다. 피부톤 필터는 맨몸 바탕에만 걸린다 |
| 겹침 순서 | 맨몸 바탕(피부톤) → 신발 → 하의 → 상의 |
| 신발 기준점 | 정면 발 칸 (96,627)-(257,768)·(260,627)-(421,768), 양말 위 끝은 y 616 이하로 올리지 않는다 |
| 하의 길이 | 달리기 다리(`run_leg`)의 하의는 기본 반바지 밑단(y 약 250)보다 짧게 끝나지 않는다(맨몸 허벅지의 이음선이 그 위에 있다) |
| 품질 | 코랄 집업 후드(`top_cherry_vest`) 기준: 외곽선 2px(옷 기본색의 어두운 톤, 검정 아님), 음영 3단, 주름은 관절·지퍼·밑단, 광택·질감 무늬 없음 |

## 4. 템플릿 11장

`tools/outfit-pipeline/templates/`: `female_stand`, `male_stand_s1`, `male_stand_s2`, `{female,male}_run_{body,arm,leg,shoe}`.

각 템플릿은 세 층이다. (1) 맨몸 바탕(신발 슬롯은 기본 신발을 옅게), (2) 하늘색 = UV 허용 영역, (3) 부위 칸 경계 실선(상의 파랑, 하의 초록, 신발 보라, 손 주황), 부위별 허용 경계 점선(겹침 띠 포함), 빨간 빗금 = 어느 부위도 그릴 수 없는 곳(UV 밖, 손 칸).

사용법: 그림 도구에서 템플릿을 맨 아래 레이어로 깔고(같은 캔버스 크기, 2배로 그릴 때는 정확히 2배), 옷만 새 레이어에 그린 뒤 그 레이어만 투명 PNG로 내보낸다. 남자 상의는 묶음의 `maleTopSurface`에 맞는 `male_stand_s1`(반팔·민소매)·`male_stand_s2`(긴소매)를 쓴다. 하의·신발의 남자 서기는 `male_stand_s1`이다. 마스크(`templates/masks/<템플릿>.png` = UV, `<템플릿>_<부위>.png` = 그 부위 허용)는 검사가 쓰는 1비트 PNG다.

## 5. 파이프라인 (`tools/outfit-pipeline`, Python 3.12, uv, Pillow)

```bash
cd android/core/avatar/tools/outfit-pipeline
uv run outfit-pipeline uv-mask          # riv Mesh·MeshVertex(u, v)로 templates/masks 생성(riv가 바뀌면 다시, H-성-1)
uv run outfit-pipeline templates        # 템플릿 11장
uv run outfit-pipeline build <원본폴더> [assetKey...]   # <원본>/<assetKey>/{g}_{slot}.png → 정리·잘라내기·shop.png·index.json
uv run outfit-pipeline check            # 아래 6절 여덟 항목
uv run pytest
```

`build`는 원본이 슬롯의 정수배 크기면 줄이고, 허용 영역 밖 알파와 옅은 잡티(알파 8 미만)를 지운다. 모듈 `outfit_pipeline.build`에는 그림 도구 출력용 함수가 더 있다: `align`(실루엣 IoU로 배율·이동 맞춤), `align_box`(불투명 상자를 가로·세로 따로 맞춤), `extract`(맨몸 위에 옷을 입힌 그림에서 맨몸과 다른 옷 픽셀만 뽑기). riv 속성 형식 표 `riv_schema.py`는 Rive CLI `rive schema <Type> --json --all`로 만든 생성물이다.

## 6. 규격 검사 (파이프라인 `check` = 앱 시험 `OutfitAssetSpecTest`)

1. `index.json`의 모든 키에 폴더가 있고 부위별 필수 파일이 모두 있다. 목록에 없는 폴더가 없다(맨몸 `body` 제외).
2. 파일 크기가 2절 표와 같고 RGBA 8비트다.
3. 금지 영역(그 부위 칸 밖)에서 알파가 0이다.
4. 알파가 0이 아닌 픽셀이 UV 허용 영역 안에만 있고 1% 이상이다.
5. `shop.png`가 512x512, 불투명 영역 긴 변 420~460px, 가운데에서 ±8px.
6. 파일 이름이 규칙(`{g}_{stand|run_body|run_arm|run_leg|run_shoe}{|_b1|_b2}.png`, `shop.png`) 안이고 그 부위 슬롯이다.
7. 묶음 하나 합계 1.5MB 이하.
8. 상의 `maleTopSurface`가 1 또는 2(남자 서기 검사는 그 표면 템플릿), `bodyOverrides` 파일이 있다.

## 7. 기존 21가지 대응 (앱 `AvatarOutfit` 번호 → 묶음, `OutfitCatalog.LEGACY_KEYS`)

| 부위 | 0 | 1 | 2 | 3 | 4 | 5 | 6 |
|------|---|---|---|---|---|---|---|
| 상의 | `base_top` | `top_lime_singlet` | `top_crew_tee` | `top_finisher_tee` | `top_winter_hoodie` | `top_dawn_windbreaker` | `top_cherry_vest` |
| 하의 | `base_bottom` | `bottom_running_shorts` | `bottom_leggings` | `bottom_jogger_pants` | `bottom_wide_pants` | `bottom_cargo_shorts` | `bottom_line_shorts` |
| 신발 | `base_shoes` | `shoes_basic_runner` | `shoes_high_top` | `shoes_carbon_racer` | `shoes_slip_on` | `shoes_ankle_boots` | `shoes_lime_runner` |

상의 `maleTopSurface`: 0·1·2 → 1, 3~6 → 2. 목록에 없는 키는 그 부위 `base_*`로 그린다.
