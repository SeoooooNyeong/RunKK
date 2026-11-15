# outfit-pipeline

아바타 의상 자산 도구다. 규격은 [`../../OUTFIT_SPEC.md`](../../OUTFIT_SPEC.md)이고 원본 설계는 design-oo 05-30 5절이다. Python 3.12, uv, Pillow, numpy를 쓴다.

```bash
cd android/core/avatar/tools/outfit-pipeline
uv sync
uv run outfit-pipeline uv-mask     # riv 메시 UV → templates/masks
uv run outfit-pipeline templates   # 템플릿 11장
uv run outfit-pipeline clean-body  # 맨몸·옷 레이어의 부스러기·허리 속옷·하의 허리를 정리(이미 만든 자산, 검사 10~14번)
uv run outfit-pipeline stand-fix   # 서기 마무리: 상의 자르기 칸, 긴소매 칼라 안감, 반팔 몸통 옆 외곽선, 신발 회색 띠·맨발 덮기(다시 걸어도 그대로)
uv run outfit-pipeline check       # assets/outfits 규격 검사
uv run outfit-pipeline render-check --rive <rive> --rml <PoC tools>   # 체형 100 서기 렌더 검사(검수 목록, Rive CLI 필요)
uv run pytest && uv run ruff check .
```

디자이너가 템플릿 위에 옷을 그렸다면 `uv run outfit-pipeline build <원본폴더> <assetKey>` 한 번이면 된다. 아래는 그림 생성 도구(Codex)로 21가지를 처음부터 다시 만드는 순서다.

## 그림 생성으로 다시 만들기

준비물(저장소 밖):
- 로컬 Codex CLI(`codex`, image_generation 켜짐). 비밀값은 Codex 로그인에 있고 이 저장소에는 없다.
- 작업 폴더 `<작업>`: 저장소 밖의 빈 폴더. Codex 작업 폴더로 저장소를 주지 않는다.
- 상점 썸네일 원본 폴더 `<썸네일>`: `<assetKey>.png`(상점 아이템 12개, 앞 작업에서 Codex로 만든 상용 썸네일). 없는 키는 지시문의 설명만으로 그린다.
- 검수 렌더(선택): Rive CLI `rive`와 아바타 PoC의 Rive 원본 폴더 `<PoC tools>`(`scene.rml` 넷, 승인 riv와 SHA가 같은 빌드 원본).

순서(명령은 이 폴더에서, `cd production` 뒤):

1. 참고 그림: `uv run python prepare_inputs.py --work <작업>`. `<작업>/in/`에 기본 의상 슬롯 텍스처를 2·4배로 만든다(승인 riv·PNG에서 읽는다).
2. 맨몸 그림 4장(Codex):
   ```bash
   ./codex.sh <작업> base_stand    prompts/base_stand.txt    <작업>/in/stand_default.png
   ./codex.sh <작업> base_run_body prompts/base_run_body.txt <작업>/in/run_body_default.png
   ./codex.sh <작업> base_run_arm  prompts/base_run_arm.txt  <작업>/in/run_arm_default.png
   ./codex.sh <작업> base_run_leg  prompts/base_run_leg.txt  <작업>/in/run_leg_default.png
   ```
   `prompts/base_stand_blocked.txt`(맨 몸통)는 생성 도구 안전 정책이 막았다. 그래서 속옷(연회색 민소매·속바지)으로 바꾼 것이 `base_stand.txt`다(D-784).
3. 맨몸 바탕: `uv run python make_body.py --work <작업>`으로 `<작업>/src_body/`를 만들고, `cd .. && uv run outfit-pipeline body <작업>/src_body`로 `assets/outfits/body/`(무손실 WebP, 상의 자르기 칸, 남녀 공용)를 만든다.
4. 의상 18가지(Codex, 4개씩 병렬 가능): `./codex.sh <작업> item <assetKey> <썸네일>`. 지시문은 `prompts/items/<assetKey>.txt`다. 긴소매 4벌은 기본 아틀라스(흰 반팔티) 위에 그리고, 승인 코랄 집업 후드를 품질 참고로 함께 준다.
5. 레이어 뽑기: `uv run python produce.py --work <작업>`(base_* 셋은 승인 기본 텍스처에서 뽑는다). 결과 `<작업>/src_outfits/<assetKey>/{g}_{slot}.png`.
6. 규격 묶음: `cd .. && uv run outfit-pipeline build <작업>/src_outfits && uv run outfit-pipeline stand-fix && uv run outfit-pipeline templates && uv run outfit-pipeline check`.
7. 검수: `uv run python production/review.py --rive <rive> --rml <PoC tools> --out <작업>/review/a0 <키...>`. 러닝량 100은 `--amount 100`, 체형은 `--shape 100`, 조합은 `top_x+bottom_y+shoes_z`로 준다. 앱과 같은 규칙(맨몸 피부톤과 얼굴 톤, 상의 자르기)으로 합성해 Rive로 남녀 서기·달리기를 그린 `sheet.png`가 나온다. 기준(코랄 집업 후드 품질, 상점 썸네일과 같은 옷, 어깨 테두리 없음)에 못 미치면 그 키만 4번부터 다시 한다(`runs/<assetKey>_v2/`에 두면 그것을 쓴다). 판정 회차마다 `uv run outfit-pipeline render-check --rive <rive> --rml <PoC tools> --out <작업>/review/s100`으로 체형 100 서기의 떠도는 조각(사다리 획)·비치는 틈도 본다(0 problem이어야 한다).
8. 배경: `./codex.sh <작업> bd_<단계> prompts/bd_<단계>.txt <기존 배경 그림>`(국내·동아시아·대륙 너머). 결과 1080x1200을 `core/ui/src/main/res/drawable-nodpi/avatar_backdrop_<단계>.webp`로 저장한다. 바닥선 y 984, 가운데 40% 비움, 위 20%·양옆 15% 랜드마크 없음을 직접 확인한다.

그림 생성은 매번 결과가 다르다. 같은 그림을 다시 얻는 것이 아니라 같은 절차와 기준으로 다시 만드는 순서다.

## riv 속성 표

`outfit_pipeline/riv_schema.py`는 생성물이다. Rive CLI의 `rive schema --list --json`으로 형식 이름을 받고, 형식마다 `rive schema <Type> --json --all`의 `properties[].key/type`을 모아 형식 코드(`u`·`s`·`f`·`c`)로 바꾼다. riv 판(RIVE 7.x)이 바뀌어 `unknown property key`가 나면 다시 만든다.
