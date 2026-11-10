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
| 상의 | `{g}_stand{b}.webp`, `{g}_run_body{b}.webp`, `{g}_run_arm{b}.webp` | 512x768, 256x384, 256x1024 |
| 하의 | `{g}_stand{b}.webp`, `{g}_run_leg{b}.webp` | 512x768, 256x768 |
| 신발 | `{g}_stand{b}.webp`, `{g}_run_shoe{b}.webp` | 512x768, 256x160 |
| 모두 | `shop.webp`(파이프라인이 만든다) | 512x512 |

`index.json` 항목: `assetKey`, `part`(`TOP`|`BOTTOM`|`SHOES`), `maleTopSurface`(상의만, 1 또는 2), `bodyOverrides`(있는 체형 파일 이름, 확장자 없이).

자산 파일은 모두 **무손실 WebP**다(APK 크기, 조정자 결정 2026-11-10). 디자이너 원본(build 입력)은 PNG도 된다. 남녀 같은 내용의 파일은 한 장만 두고 `index.json`의 `shared`(이름 → 실제 파일 이름, 확장자 없이)로 함께 쓴다(예: `"shared": {"male_run_arm": "female_run_arm"}`). 공용 이름의 파일은 폴더에 없어야 한다. 파이프라인 `build`가 남녀 픽셀이 같으면 자동으로 공용으로 바꾼다.

맨몸 바탕은 `outfits/body/{g}_{stand|run_body|run_arm|run_leg}.webp` 8장(남녀 공용이면 `index.json` `body.shared`), 상의 자르기 칸은 `outfits/body/{g}_{stand|run_body|run_arm}_cut.webp`(알파 255 = 칸, 3절 「상의 자르기」)다. 설계서의 `_body` 대신 `body`를 쓴다: aapt는 `_`로 시작하는 assets 폴더를 APK에서 뺀다(기본 무시 규칙 `<dir>_*`).

## 3. 그림 규칙

| 항목 | 규칙 |
|------|------|
| 좌표 | 슬롯 텍스처와 같은 크기·좌표. 템플릿(아래 4절)을 밑그림으로 깔고 그린다 |
| 허용 영역 | 그 텍스처를 쓰는 riv 메시의 UV 덮임(화면에 보이는 픽셀) 안, 그리고 그 부위 칸 안. 위 부위가 아래 부위를 덮는 겹침 띠 8px(상의 밑단 ↔ 하의 허리, 하의 밑단 ↔ 신발 목)는 양쪽 허용. 단 하의는 상의 칸으로 24px까지 올라갈 수 있다(서기 아틀라스의 실제 허리선 y 약 300이 상의 칸 경계 y 319보다 높아, 허리 밴드가 상의 밑단 아래로 들어가야 맨몸이 비치지 않는다, [임시:D-성-158]) |
| 메시 밖 금지 | 옷 실루엣은 몸 메시 UV 안에서만 보인다. 넓은 옷도 영역 안에서 주름·음영으로 부피를 낸다 |
| 바탕 | RGBA, 옷이 아닌 곳은 알파 0. 반투명은 가장자리 안티에일리어싱 1px만 |
| 그 부위만 | 상의 파일에 하의·신발 픽셀 없음(금지 영역은 템플릿의 빨간 빗금) |
| 피부 | 의상 그림에 피부를 그리지 않는다. 민소매 어깨·반바지 다리는 알파 0으로 두어 맨몸 바탕이 보이게 한다. 피부톤 필터는 맨몸 바탕에만 걸린다 |
| 겹침 순서 | 맨몸 바탕(피부톤) → 신발 → 하의 → 상의 |
| 상의 자르기 ([임시:D-성-156]) | 앱이 합성할 때 상의 자르기 칸 안에서 상의 밖(옷으로 둘러싸인 빈 곳은 옷으로 친다)인 맨몸 픽셀 가운데 상의와 배경(맨몸도 상의도 없는 곳) 사이에 낀 얇은 띠를 지운다: 가장 가까운 상의·배경까지 거리 합 ≤ 32px(`RIM_PX` 30 + 2, 텍스처 가장자리 너머도 배경)이고 두 방향이 반대(코사인 < −0.5). 그래서 맨몸 실루엣보다 좁은 상의(실측 최대 약 25px, 후드티 어깨 곡선)도 어깨·몸통·소매 옆 피부 테두리가 옷 밖으로 비치지 않는다. 소매 끝 아래 팔(상의는 위, 배경은 옆)·민소매 팔·목·손은 남는다. 칸: 서기 = 상의 칸 − 손 칸 − 목·가슴 위(x 196~316, y 0~109)·맨 위 목 둘레(x 176~336, y 0~13), 달리기 몸통 = 전체 − 목 조각(x 100~199, y 0~39, 알파 240: 띠 판정 없이 상의 밖을 모두 지운다. 옆모습 몸통은 맨몸이 보일 곳이 없다), 달리기 팔 = 전체. 팔 띠(서기 x 0~144·367~511의 y 185~301, 허리 옆 모서리는 몸통 칸, 달리기 팔의 y 300 아래)는 상의가 그 띠의 맨몸을 60% 넘게 덮을 때(긴소매)만 자른다(반팔·민소매의 팔은 몸통에 붙어 있어도 그대로). 띠를 지운 뒤 남은 부스러기(자르기 칸 안 상의 밖 맨몸을 4방향으로 이은 덩어리 80px 이하, 모든 픽셀이 상의에서 30px 안)도 지운다. 칸 파일 알파(값마다 뜻이 다르다): 128 미만 = 자르지 않음, 255 = 몸통(띠 판정), 200 = 왼팔 띠·160 = 오른팔 띠(값마다 한 띠, 덮인 몫 판정 뒤 띠 판정), 240 = 띠 판정 없이 상의 밖을 모두 지움(달리기 몸통). 덮인 몫은 팔 띠 값만 센다(255·240은 팔 띠가 아니다). 앱 `TopBodyCut`, 파이프라인 `outfit_pipeline/cut.py`(검수 렌더)가 같은 규칙이고, 같은 픽스처(`src/test/resources/topcut`, `tests/make_topcut_fixture.py`)로 두 쪽 결과가 같은지 시험한다(TopBodyCutParityTest, `test_cut_matches_app_fixture`) |
| 신발 기준점 | 정면 발 칸 (96,627)-(257,768)·(260,627)-(421,768), 양말 위 끝은 y 616 이하로 올리지 않는다 |
| 하의 길이 | 달리기 다리(`run_leg`)의 하의는 기본 반바지 밑단(y 약 250)보다 짧게 끝나지 않는다(맨몸 허벅지의 이음선이 그 위에 있다) |
| 품질 | 코랄 집업 후드(`top_cherry_vest`) 기준: 외곽선 2px(옷 기본색의 어두운 톤, 검정 아님), 음영 3단, 주름은 관절·지퍼·밑단, 광택·질감 무늬 없음 |

## 4. 템플릿 11장

`tools/outfit-pipeline/templates/`: `female_stand`, `male_stand_s1`, `male_stand_s2`, `{female,male}_run_{body,arm,leg,shoe}`.

각 템플릿은 세 층이다. (1) 맨몸 바탕(신발 슬롯은 기본 신발을 옅게), (2) 하늘색 = UV 허용 영역, (3) 부위 칸 경계 실선(상의 파랑, 하의 초록, 신발 보라, 손 주황), 부위별 허용 경계 점선(겹침 띠 포함), 빨간 빗금 = 어느 부위도 그릴 수 없는 곳(UV 밖, 손 칸).

사용법: 그림 도구에서 템플릿을 맨 아래 레이어로 깔고(같은 캔버스 크기, 2배로 그릴 때는 정확히 2배), 옷만 새 레이어에 그린 뒤 그 레이어만 투명 PNG(또는 WebP)로 내보낸다. 남자 상의는 묶음의 `maleTopSurface`에 맞는 `male_stand_s1`(반팔·민소매)·`male_stand_s2`(긴소매)를 쓴다. 하의·신발의 남자 서기는 `male_stand_s1`이다. 마스크(`templates/masks/<템플릿>.png` = UV, `<템플릿>_<부위>.png` = 그 부위 허용)는 검사가 쓰는 1비트 PNG다.

## 5. 파이프라인 (`tools/outfit-pipeline`, Python 3.12, uv, Pillow)

```bash
cd android/core/avatar/tools/outfit-pipeline
uv run outfit-pipeline uv-mask          # riv Mesh·MeshVertex(u, v)로 templates/masks 생성(riv가 바뀌면 다시, H-성-1)
uv run outfit-pipeline templates        # 템플릿 11장
uv run outfit-pipeline body <맨몸 원본폴더>         # {g}_{slot}.png → body/(무손실 WebP)·상의 자르기 칸·index.json body.shared
uv run outfit-pipeline build <원본폴더> [assetKey...]   # <원본>/<assetKey>/{g}_{slot}.png → 정리·잘라내기·WebP·shop.webp·남녀 공용·index.json
uv run outfit-pipeline strip-skin       # 이미 만든 묶음의 옷 레이어에서 드러난 맨살을 지운다(6절 9번)
uv run outfit-pipeline check            # 아래 6절 아홉 항목
uv run pytest
```

`build`는 원본이 슬롯의 정수배 크기면 줄이고, 허용 영역 밖 알파와 옅은 잡티(알파 8 미만)를 지운다. 모듈 `outfit_pipeline.build`에는 그림 도구 출력용 함수가 더 있다: `align`(실루엣 IoU로 배율·이동 맞춤), `align_box`(불투명 상자를 가로·세로 따로 맞춤), `extract`(맨몸 위에 옷을 입힌 그림에서 맨몸과 다른 옷 픽셀만 뽑기). riv 속성 형식 표 `riv_schema.py`는 Rive CLI `rive schema <Type> --json --all`로 만든 생성물이다.

## 6. 규격 검사 (파이프라인 `check` = 앱 시험 `OutfitAssetSpecTest`)

1. `index.json`의 모든 키에 폴더가 있고 부위별 필수 파일이 모두 있다. 목록에 없는 폴더가 없다(맨몸 `body` 제외).
2. 파일 크기가 2절 표와 같고 RGBA 8비트다.
3. 금지 영역(그 부위 칸 밖)에서 알파가 0이다.
4. 알파가 0이 아닌 픽셀이 UV 허용 영역 안에만 있고 1% 이상이다.
5. `shop.webp`가 512x512, 불투명 영역 긴 변 420~460px, 가운데에서 ±8px.
6. 파일 이름이 규칙(`{g}_{stand|run_body|run_arm|run_leg|run_shoe}{|_b1|_b2}.webp`, `shop.webp`) 안이고 그 부위 슬롯이다. `shared`의 이름은 파일이 없고 가리키는 파일이 있다. 공용 파일은 그 이름(성별)의 템플릿으로도 검사한다.
7. 묶음 하나 합계 1.5MB 이하.
8. 상의 `maleTopSurface`가 1 또는 2(남자 서기 검사는 그 표면 템플릿), `bodyOverrides` 파일이 있다.
9. 옷 레이어에 드러난 맨살이 없다([임시:D-성-174]): 밝은 피부 색조(R ≥ 170, G ≥ 130, 30 ≤ R−G ≤ 80, G−B ≥ 4, 2(G−B) ≤ (R−G)+4, 알파 32 이상) 픽셀을 8방향으로 이은 덩어리가 빈 곳이나 가장자리에 닿고 4픽셀 이상이면 실패다. 피부는 맨몸 바탕에서만 와야 러닝량 톤이 걸린다(옷 레이어의 피부는 톤이 걸리지 않아 짙은 팔 옆에 밝은 분홍으로 보인다). 천 안에 갇힌 피부색 무늬(끈, 패치, 하이라이트)는 세지 않는다. `build`가 지우고(`outfit_pipeline/skin.py`), 이미 만든 묶음은 `strip-skin`으로 지운다. 앱 시험은 러닝량 100 합성에서도 옷 없는 곳에 닿은 밝은 맨살이 없는지 본다(OutfitCompositeTest).

## 7. 기존 21가지 대응 (앱 `AvatarOutfit` 번호 → 묶음, `OutfitCatalog.LEGACY_KEYS`)

| 부위 | 0 | 1 | 2 | 3 | 4 | 5 | 6 |
|------|---|---|---|---|---|---|---|
| 상의 | `base_top` | `top_lime_singlet` | `top_crew_tee` | `top_finisher_tee` | `top_winter_hoodie` | `top_dawn_windbreaker` | `top_cherry_vest` |
| 하의 | `base_bottom` | `bottom_running_shorts` | `bottom_leggings` | `bottom_jogger_pants` | `bottom_wide_pants` | `bottom_cargo_shorts` | `bottom_line_shorts` |
| 신발 | `base_shoes` | `shoes_basic_runner` | `shoes_high_top` | `shoes_carbon_racer` | `shoes_slip_on` | `shoes_ankle_boots` | `shoes_lime_runner` |

상의 `maleTopSurface`: 0·1·2 → 1, 3~6 → 2. 목록에 없는 키는 그 부위 `base_*`로 그린다.

## 8. 다시 만들기(제작 스크립트)

그림 도구 출력에서 이 저장소의 자산까지 다시 만드는 순서는 `tools/outfit-pipeline/README.md`에 있다(제작 스크립트 `production/`, 그림 도구 지시문 `production/prompts/`).
