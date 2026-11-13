-- AV-5 랜드마크: 개성(KAESONG)을 빼고 춘천(CHUNCHEON, 국내, 왕복 150km)을 더한다(D-성-124, D-성-129, 사용자 결정 U-7).
-- 코드 기본값(SettingKey.Defaults.LANDMARKS)은 같은 MR에서 바꾼다. V25의 이름→키 표는 이미 적용된 DB가 있으므로 고치지 않는다.
DO $$
DECLARE
    setting_rows    INTEGER;
    unlocked_rows   INTEGER;
    selected_rows   INTEGER;
BEGIN
    -- (1) 운영 기준값 LANDMARKS 행이 있으면 key가 KAESONG인 원소를 빼고, CHUNCHEON 원소가 없을 때만 춘천을 거리 순 자리
    -- (150km보다 먼 첫 원소 앞, 없으면 끝)에 넣는다. 150km 원소가 이미 있으면 거리 오름차순 검사가 깨지므로 넣지 않는다.
    -- 행이 없거나 값이 JSON 객체가 아니거나 landmarks가 배열이 아니면 아무것도 하지 않는다. 내용이 바뀐 때만 version을 올린다.
    -- 다른 키의 행(JSON이 아닐 수 있다)에 ::jsonb 캐스트가 돌지 않게 LANDMARKS 행만 먼저 고르고(MATERIALIZED), JSON 객체인지 본
    -- 뒤에 캐스트한다(리뷰 R2-16).
    WITH picked AS MATERIALIZED (SELECT setting_key, setting_value
                                 FROM operation_setting
                                 WHERE setting_key = 'LANDMARKS'),
         target AS MATERIALIZED (SELECT setting_key,
                                        CASE WHEN setting_value IS JSON OBJECT THEN setting_value::jsonb END AS value
                                 FROM picked),
         valid AS (SELECT setting_key, value
                   FROM target
                   WHERE value IS NOT NULL
                     AND jsonb_typeof(value -> 'landmarks') = 'array'),
         src AS (SELECT a.elem,
                        a.ord::NUMERIC AS pos,
                        CASE
                            WHEN jsonb_typeof(a.elem -> 'distance' -> 'meters') = 'number'
                                THEN (a.elem -> 'distance' ->> 'meters')::NUMERIC
                            END        AS meters
                 FROM valid v,
                      jsonb_array_elements(v.value -> 'landmarks') WITH ORDINALITY AS a(elem, ord)),
         kept AS (SELECT elem, pos, meters
                  FROM src
                  WHERE NOT (jsonb_typeof(elem) = 'object' AND elem ->> 'key' = 'KAESONG')),
         added AS (SELECT jsonb_build_object(
                                  'key', 'CHUNCHEON',
                                  'stage', 'DOMESTIC',
                                  'name', '춘천',
                                  'distance', jsonb_build_object('meters', 150000),
                                  'roundTrip', TRUE) AS elem,
                          COALESCE((SELECT min(pos) FROM kept WHERE meters > 150000),
                                   (SELECT count(*) FROM src) + 1) - 0.5 AS pos
                   WHERE EXISTS (SELECT 1 FROM src)
                     AND NOT EXISTS (SELECT 1
                                     FROM kept
                                     WHERE (jsonb_typeof(elem) = 'object' AND elem ->> 'key' = 'CHUNCHEON')
                                        OR meters = 150000)),
         rebuilt AS (SELECT COALESCE(jsonb_agg(x.elem ORDER BY x.pos), '[]'::jsonb) AS landmarks
                     FROM (SELECT elem, pos FROM kept UNION ALL SELECT elem, pos FROM added) x),
         changed AS (SELECT v.setting_key, v.value || jsonb_build_object('landmarks', r.landmarks) AS value
                     FROM valid v,
                          rebuilt r
                     WHERE r.landmarks IS DISTINCT FROM v.value -> 'landmarks')
    UPDATE operation_setting s
    SET setting_value = c.value::text,
        version       = s.version + 1
    FROM changed c
    WHERE s.setting_key = c.setting_key;
    GET DIAGNOSTICS setting_rows = ROW_COUNT;

    -- (2) 지운 북한 도시 키는 해금도 남기지 않는다(FR-201 예외). 남겨 두면 BackgroundService가 표에 없는 키를 목록 끝에 계속 보인다.
    DELETE FROM avatar_background WHERE landmark_key = 'KAESONG';
    GET DIAGNOSTICS unlocked_rows = ROW_COUNT;

    -- (3) 그 배경을 고른 회원은 기본 배경(NULL)으로 되돌린다.
    UPDATE avatar SET selected_background = NULL WHERE selected_background = 'KAESONG';
    GET DIAGNOSTICS selected_rows = ROW_COUNT;

    -- (2)·(3)은 0건이 예상된다. 지운 건수를 마이그레이션 로그로 남긴다.
    RAISE NOTICE 'V65 landmark_remove_kaesong: landmarks_setting_rows=%, kaesong_unlocked_rows=%, kaesong_selected_rows=%',
        setting_rows, unlocked_rows, selected_rows;
END
$$;
