-- v0.11 S4 랜드마크 배경(부록 F F.5.3 V25, FR-201, FR-202, D-107).

-- 고른 배경 키(Avatar.selectedBackgroundKey). null이면 기본 배경
ALTER TABLE avatar ADD COLUMN selected_background VARCHAR(40);

-- 해금한 배경(UnlockedBackground): 해금 때의 키·이름·단계·거리 스냅숏. 운영 기준값이 바뀌어도 지우지 않는다(D-107 (4)).
-- (member_id, landmark_key) 기본 키가 리스너·보정 배치·조회 때 채우기가 겹쳐도 한 키를 한 행으로 막는다(5.3).
CREATE TABLE avatar_background (
    member_id       BIGINT       NOT NULL REFERENCES avatar (member_id) ON DELETE CASCADE,
    landmark_key    VARCHAR(40)  NOT NULL,
    landmark_name   VARCHAR(40)  NOT NULL,
    stage           VARCHAR(20)  NOT NULL,
    distance_meters BIGINT       NOT NULL,
    unlocked_at     TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (member_id, landmark_key),
    CONSTRAINT ck_avatar_background_stage CHECK (stage IN ('DOMESTIC', 'EAST_ASIA', 'INTERCONTINENTAL'))
);

-- 운영 기준값 LANDMARKS 행이 있으면 키가 없는 원소마다 key를 붙인다. 기본 표 이름이면 D-107의 키, 아니면(이름이 없거나 바뀐 이름,
-- 같은 이름이 두 번째로 나온 원소) 'LM_' || 순번(1부터). 이미 넘은 랜드마크의 해금 행은 만들지 않는다: 배경 조회 때 알림 없이 채운다(D-107 (3)).
UPDATE operation_setting s
SET setting_value = (s.setting_value::jsonb || jsonb_build_object('landmarks', keyed.landmarks))::text
FROM (SELECT COALESCE(jsonb_agg(
                              CASE
                                  WHEN e.elem ? 'key' THEN e.elem
                                  ELSE e.elem || jsonb_build_object(
                                          'key',
                                          CASE
                                              WHEN d.landmark_key IS NOT NULL AND e.name_rank = 1 THEN d.landmark_key
                                              ELSE 'LM_' || e.ord
                                              END)
                                  END
                              ORDER BY e.ord), '[]'::jsonb) AS landmarks
      FROM (SELECT a.elem,
                   a.ord,
                   row_number() OVER (PARTITION BY a.elem ->> 'name' ORDER BY a.ord) AS name_rank
            FROM operation_setting o,
                 jsonb_array_elements(o.setting_value::jsonb -> 'landmarks') WITH ORDINALITY AS a(elem, ord)
            WHERE o.setting_key = 'LANDMARKS'
              AND jsonb_typeof(o.setting_value::jsonb -> 'landmarks') = 'array'
              AND jsonb_typeof(a.elem) = 'object') e
               LEFT JOIN (VALUES ('인천', 'INCHEON'),
                                 ('개성', 'KAESONG'),
                                 ('대전', 'DAEJEON'),
                                 ('전주', 'JEONJU'),
                                 ('여수', 'YEOSU'),
                                 ('부산', 'BUSAN'),
                                 ('제주도', 'JEJU'),
                                 ('베이징', 'BEIJING'),
                                 ('도쿄', 'TOKYO'),
                                 ('타이베이', 'TAIPEI'),
                                 ('홍콩', 'HONG_KONG'),
                                 ('하노이', 'HANOI'),
                                 ('방콕', 'BANGKOK'),
                                 ('쿠알라룸푸르', 'KUALA_LUMPUR'),
                                 ('호놀룰루', 'HONOLULU'),
                                 ('시드니', 'SYDNEY'),
                                 ('런던', 'LONDON'),
                                 ('파리', 'PARIS'),
                                 ('LA', 'LOS_ANGELES'),
                                 ('뉴욕', 'NEW_YORK'),
                                 ('산티아고', 'SANTIAGO'),
                                 ('지구 둘레 1바퀴', 'EARTH_LAP'),
                                 ('지구 2바퀴', 'EARTH_2_LAPS'),
                                 ('10만 km', 'KM_100K'),
                                 ('20만 km', 'KM_200K'),
                                 ('달나라 진입', 'MOON_APPROACH'),
                                 ('달까지', 'MOON'),
                                 ('50만 km', 'KM_500K'),
                                 ('달 왕복', 'MOON_ROUND_TRIP'),
                                 ('100만 km', 'KM_1M')) AS d(landmark_name, landmark_key)
                         ON d.landmark_name = e.elem ->> 'name') keyed
WHERE s.setting_key = 'LANDMARKS'
  AND jsonb_typeof(s.setting_value::jsonb -> 'landmarks') = 'array';
