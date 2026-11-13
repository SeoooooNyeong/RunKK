-- develop 전용 더미 데이터(S15P31A207-222). infra/dev-seed.sh가 develop DB 컨테이너의 psql로 한 트랜잭션에 돌린다. 운영 DB에 돌리지 않는다.
-- 넣는 것
--   회원    게스트형 더미 회원 18명(닉네임 "더미러너01"~"더미러너18", 로그인 수단 없음: 소셜 계정·게스트 자격 증명 해시 없음), 가입 동의 6행
--   크루    공개 크루 5개("더미 …", 크루원 3~5명). 크루원은 한 명이 활성 크루 하나만 가질 수 있어(uq_crew_membership_active)
--           크루 5개 × 3명 이상을 채우려고 더미 회원을 18명으로 했다
--           다가오는 크루런 모집글 2~3개(참석 응답), 지난 크루런 1~2개(합산 방식 결과와 연결 기록), 크루 채팅, 이번 주·지난주 주간 레이스와 목표
--   러닝    더미 회원마다 최근 6주 유효 러닝 10~25회(경로 포함). 티어·레벨·배치고사 완료·Runimal은 그 기록으로 계산해 바로 채운다
--   대상 계정  psql 변수 dev_seed_targets(쉼표 구분 닉네임, 기본 "러너4171,러너1699", dev-seed.sh가 DEV_SEED_TARGETS로 넘긴다)의
--           닉네임마다 그 회원이 있으면 더미 크루에 넣고(활성 크루가 없을 때만), 출생연도가 비었으면 1998로 채우고,
--           최근 6주 유효 러닝 22회(경로·심박·움직임 표본)와 런바디 작업(PENDING)을 넣는다. 없는 닉네임은 경고만 하고 건너뛴다.
--           대상 계정 러닝 기록 ID는 회원 ID·순번에서 만든다(닉네임이 아니다)
-- 멱등   더미 회원·크루는 닉네임·이름으로 찾아 이미 있으면 그 회원·크루에 딸린 자료를 다시 만들지 않는다. 러닝 기록 ID는 회원·순번에서 만든
--        고정 UUID라 다시 돌려도 같은 행이다(ON CONFLICT DO NOTHING). 주간 레이스 합계는 기여 행에서 다시 센다
-- 저장 형식은 서버 코드를 따른다
--   route_data   RunTrack.encodePoints: GZIP(int 개수, 점마다 long 시작 기준 ms, double 위도, double 경도, float 정확도, byte 신호 순번)
--   heart_rate_data  HeartRateSeries.encodeSamples: 압축 없음(int 개수, 표본마다 long ms, short bpm)
--   motion_data  MotionSamples.encode: GZIP(int 개수, 표본마다 long ms, double 속도 m/s, double 케이던스 spm)
--   route_shape  RouteShape.encode: 최대 24점, 0~1 상자, "x,y;x,y"(소수 셋째 자리)
--   GZIP은 PostgreSQL에 압축 함수가 없어 deflate 저장 블록(압축하지 않은 블록)과 CRC-32로 직접 만든다. Java GZIPInputStream이 그대로 푼다
-- 시각은 서버처럼 한국 시간 LocalDateTime으로 넣는다(timestamp without time zone).

SET client_min_messages = notice;
SET TIME ZONE 'Asia/Seoul';
\if :{?dev_seed_targets}
\else
  \set dev_seed_targets '러너4171,러너1699'
\endif
BEGIN;
-- DO 본문에는 psql 변수가 들어가지 않아 트랜잭션 설정 값으로 넘긴다
SET LOCAL runkk.dev_seed_targets = :'dev_seed_targets';
DO $$ BEGIN PERFORM setseed(0.4171); END $$;

-- ---------- 보조 함수(세션 임시 스키마, 끝나면 사라진다) ----------

CREATE FUNCTION pg_temp.le16(v integer) RETURNS bytea LANGUAGE sql IMMUTABLE AS $$
  SELECT set_byte(set_byte('\x0000'::bytea, 0, v & 255), 1, (v >> 8) & 255)
$$;

CREATE FUNCTION pg_temp.le32(v bigint) RETURNS bytea LANGUAGE sql IMMUTABLE AS $$
  SELECT set_byte(set_byte(set_byte(set_byte('\x00000000'::bytea,
    0, (v & 255)::int), 1, ((v >> 8) & 255)::int), 2, ((v >> 16) & 255)::int), 3, ((v >> 24) & 255)::int)
$$;

CREATE FUNCTION pg_temp.crc32(data bytea) RETURNS bigint LANGUAGE plpgsql IMMUTABLE AS $$
DECLARE
  tbl bigint[] := ARRAY[]::bigint[];
  c bigint;
  n integer := length(data);
BEGIN
  FOR i IN 0..255 LOOP
    c := i;
    FOR k IN 1..8 LOOP
      IF (c & 1) = 1 THEN c := 3988292384 # (c >> 1); ELSE c := c >> 1; END IF;
    END LOOP;
    tbl := tbl || c;
  END LOOP;
  c := 4294967295;
  FOR i IN 0..n - 1 LOOP
    c := tbl[((c # get_byte(data, i)) & 255) + 1] # (c >> 8);
  END LOOP;
  RETURN c # 4294967295;
END
$$;

-- GZIP 한 멤버(헤더 10바이트, deflate 저장 블록, CRC-32·길이 꼬리)
CREATE FUNCTION pg_temp.gzip_stored(raw bytea) RETURNS bytea LANGUAGE plpgsql IMMUTABLE AS $$
DECLARE
  n integer := length(raw);
  off integer := 0;
  len integer;
  parts bytea[] := ARRAY['\x1f8b08000000000000ff'::bytea];
BEGIN
  IF n = 0 THEN
    parts := parts || '\x010000ffff'::bytea;
  END IF;
  WHILE off < n LOOP
    len := least(65535, n - off);
    parts := parts || (CASE WHEN off + len >= n THEN '\x01'::bytea ELSE '\x00'::bytea END
                       || pg_temp.le16(len) || pg_temp.le16(65535 - len) || substring(raw FROM off + 1 FOR len));
    off := off + len;
  END LOOP;
  parts := parts || (pg_temp.le32(pg_temp.crc32(raw)) || pg_temp.le32(n::bigint));
  RETURN pg_temp.concat_bytes(parts);
END
$$;

CREATE FUNCTION pg_temp.concat_bytes(parts bytea[]) RETURNS bytea LANGUAGE sql IMMUTABLE AS $$
  SELECT coalesce(string_agg(p, ''::bytea ORDER BY o), ''::bytea) FROM unnest(parts) WITH ORDINALITY AS u(p, o)
$$;

-- RunningScore.vdot(미터, 분)
CREATE FUNCTION pg_temp.vdot(meters double precision, minutes double precision) RETURNS double precision
LANGUAGE sql IMMUTABLE AS $$
  SELECT (-4.60 + 0.182258 * (meters / minutes) + 0.000104 * (meters / minutes) ^ 2)
       / (0.8 + 0.1894393 * exp(-0.012778 * minutes) + 0.2989558 * exp(-0.1932605 * minutes))
$$;

-- TierBoundary 기본값 {"lowerBounds":[30,35,40,45]}
CREATE FUNCTION pg_temp.tier_of(score double precision) RETURNS text LANGUAGE sql IMMUTABLE AS $$
  SELECT CASE WHEN score >= 45 THEN 'MASTER' WHEN score >= 40 THEN 'PLATINUM' WHEN score >= 35 THEN 'GOLD'
              WHEN score >= 30 THEN 'SILVER' ELSE 'BRONZE' END
$$;

CREATE FUNCTION pg_temp.haversine(lat1 double precision, lon1 double precision, lat2 double precision, lon2 double precision)
RETURNS double precision LANGUAGE sql IMMUTABLE AS $$
  SELECT 6371000.0 * 2 * atan2(sqrt(h), sqrt(1 - h))
  FROM (SELECT sin(radians(lat2 - lat1) / 2) ^ 2
             + cos(radians(lat1)) * cos(radians(lat2)) * sin(radians(lon2 - lon1) / 2) ^ 2 AS h) s
$$;

-- RouteShape.of(경로).encode()
CREATE FUNCTION pg_temp.route_shape(lats double precision[], lons double precision[]) RETURNS text
LANGUAGE plpgsql IMMUTABLE AS $$
DECLARE
  n integer := coalesce(array_length(lats, 1), 0);
  idx integer[] := ARRAY[]::integer[];
  step double precision;
  min_lat double precision := 'Infinity';
  max_lat double precision := '-Infinity';
  min_lon double precision := 'Infinity';
  max_lon double precision := '-Infinity';
  lon_scale double precision;
  width double precision;
  height double precision;
  span double precision;
  x double precision;
  y double precision;
  out text[] := ARRAY[]::text[];
  i integer;
BEGIN
  IF n < 2 THEN
    RETURN NULL;
  END IF;
  IF n <= 24 THEN
    FOR k IN 1..n LOOP idx := idx || k; END LOOP;
  ELSE
    step := (n - 1) / 23.0;
    FOR k IN 0..23 LOOP idx := idx || (floor(k * step + 0.5)::integer + 1); END LOOP;
  END IF;
  FOREACH i IN ARRAY idx LOOP
    min_lat := least(min_lat, lats[i]); max_lat := greatest(max_lat, lats[i]);
    min_lon := least(min_lon, lons[i]); max_lon := greatest(max_lon, lons[i]);
  END LOOP;
  lon_scale := cos(radians((min_lat + max_lat) / 2));
  width := (max_lon - min_lon) * lon_scale;
  height := max_lat - min_lat;
  span := greatest(width, height);
  FOREACH i IN ARRAY idx LOOP
    IF span = 0 THEN
      x := 0.5; y := 0.5;
    ELSE
      x := ((lons[i] - min_lon) * lon_scale + (span - width) / 2) / span;
      y := ((lats[i] - min_lat) + (span - height) / 2) / span;
    END IF;
    x := floor(greatest(0, least(1, x)) * 1000 + 0.5) / 1000.0;
    y := floor(greatest(0, least(1, y)) * 1000 + 0.5) / 1000.0;
    out := out || (to_char(x, 'FM0.000') || ',' || to_char(y, 'FM0.000'));
  END LOOP;
  RETURN array_to_string(out, ';');
END
$$;

-- 러닝 한 회를 만든다. 공원 둘레를 도는 타원 코스를 목표 거리까지 dt초 간격으로 달린다(이웃 점 거리 100m 미만, 런바디 ai 상한).
-- rich면 심박·움직임 표본도 점마다 넣는다. 기록이 이미 있으면 넣지 않고 그 거리·시간을 돌려준다.
CREATE FUNCTION pg_temp.seed_run(
    p_record text, p_member bigint, p_started timestamp, p_target_m integer, p_pace_s double precision,
    p_lat double precision, p_lon double precision, p_dt integer, p_rich boolean,
    p_mode text, p_post bigint, p_saved timestamp, p_tier_applied boolean,
    OUT o_distance bigint, OUT o_elapsed bigint, OUT o_inserted boolean)
LANGUAGE plpgsql AS $$
DECLARE
  a double precision := 500 + random() * 900;       -- 타원 반축(m)
  b double precision := 250 + random() * 450;
  rot double precision := random() * pi();
  theta double precision := random() * 2 * pi();
  m_lat double precision := 111320.0;
  m_lon double precision := 111320.0 * cos(radians(p_lat));
  base_speed double precision := 1000.0 / p_pace_s;
  t integer := 0;
  dist double precision := 0;
  speed double precision := base_speed;
  x double precision; y double precision; xr double precision; yr double precision;
  lat double precision; lon double precision;
  lats double precision[] := ARRAY[]::double precision[];
  lons double precision[] := ARRAY[]::double precision[];
  route_parts bytea[] := ARRAY[]::bytea[];
  hr_parts bytea[] := ARRAY[]::bytea[];
  motion_parts bytea[] := ARRAY[]::bytea[];
  n integer := 0;
  hr integer; hr_sum bigint := 0; hr_max integer := 0;
  cadence double precision;
  ended timestamp;
  score double precision;
BEGIN
  SELECT r.distance_meters, r.elapsed_millis INTO o_distance, o_elapsed FROM run_record r WHERE r.record_id = p_record;
  IF FOUND THEN
    o_inserted := false;
    RETURN;
  END IF;
  LOOP
    x := a * cos(theta); y := b * sin(theta);
    xr := x * cos(rot) - y * sin(rot); yr := x * sin(rot) + y * cos(rot);
    lat := p_lat + (yr + (random() - 0.5) * 3) / m_lat;
    lon := p_lon + (xr + (random() - 0.5) * 3) / m_lon;
    IF n > 0 THEN
      dist := dist + pg_temp.haversine(lats[n], lons[n], lat, lon);
    END IF;
    n := n + 1;
    lats := lats || lat; lons := lons || lon;
    route_parts := route_parts || (int8send(t * 1000::bigint) || float8send(lat) || float8send(lon)
                                   || float4send((3.5 + random() * 5)::real) || '\x00'::bytea);
    IF p_rich THEN
      hr := round(150 + (speed - base_speed) * 28 + least(t / 60.0, 40) * 0.45 + (random() - 0.5) * 6)::integer;
      hr := round(98 + (hr - 98) * least(1.0, (t + 20) / 150.0))::integer;
      hr := greatest(90, least(195, hr));
      hr_sum := hr_sum + hr; hr_max := greatest(hr_max, hr);
      hr_parts := hr_parts || (int8send(t * 1000::bigint) || int2send(hr::smallint));
      cadence := round((158 + (speed - 2.4) * 11 + (random() - 0.5) * 4)::numeric, 1);
      motion_parts := motion_parts || (int8send(t * 1000::bigint) || float8send(round(speed::numeric, 2)::double precision)
                                       || float8send(cadence));
    END IF;
    EXIT WHEN dist >= p_target_m;
    speed := base_speed * (1 + 0.035 * sin(t / 95.0) + (random() - 0.5) * 0.08);
    theta := theta + speed * p_dt / sqrt((a * sin(theta)) ^ 2 + (b * cos(theta)) ^ 2);
    t := t + p_dt;
  END LOOP;
  o_distance := round(dist);
  o_elapsed := t * 1000::bigint;
  ended := p_started + make_interval(secs => t);
  score := pg_temp.vdot(o_distance, o_elapsed / 60000.0);
  INSERT INTO run_record (
      record_id, member_id, started_at, ended_at, distance_meters, elapsed_millis, average_pace_seconds,
      mode, card_kind, battle_id, crew_run_post_id, valid, judged, score, weather_temperature, weather_sky,
      point_count, route_data, route_purged, unstable_segments, heart_rate_data, heart_rate_purged,
      heart_rate_avg, heart_rate_max, new_records, saved_at, pauses, paused_millis, tier_applied,
      excluded_meters, excluded_millis, rejudge_state, route_shape, start_device, motion_data)
  VALUES (
      p_record, p_member, p_started, ended, o_distance, o_elapsed, greatest(1, round(o_elapsed::numeric / o_distance))::integer,
      p_mode, CASE WHEN p_mode = 'CREW_RUN' THEN 'CREW_RUN' ELSE 'FREE_RUN' END, NULL, p_post, true, true, score, NULL, NULL,
      n, pg_temp.gzip_stored(int4send(n) || pg_temp.concat_bytes(route_parts)), false, '',
      CASE WHEN p_rich THEN int4send(n) || pg_temp.concat_bytes(hr_parts) END, false,
      CASE WHEN p_rich THEN round(hr_sum::numeric / n)::integer END, CASE WHEN p_rich THEN hr_max END,
      '', coalesce(p_saved, ended + interval '15 seconds'), '', 0, p_tier_applied,
      0, 0, NULL, pg_temp.route_shape(lats, lons), 'PHONE',
      CASE WHEN p_rich THEN pg_temp.gzip_stored(int4send(n) || pg_temp.concat_bytes(motion_parts)) END);
  o_inserted := true;
END
$$;

-- ---------- 본문 ----------

DO $seed$
DECLARE
  v_now timestamp := date_trunc('second', localtimestamp);
  v_today date := current_date;
  v_week date := date_trunc('week', localtimestamp)::date;   -- 이번 주 월요일(Week.of)
  -- 공원 중심(위도, 경도): 여의도, 서울숲, 반포, 뚝섬, 올림픽공원, 망원, 보라매
  park_lat double precision[] := ARRAY[37.5283, 37.5444, 37.5110, 37.5296, 37.5207, 37.5550, 37.4925];
  park_lon double precision[] := ARRAY[126.9326, 127.0374, 126.9960, 127.0700, 127.1215, 126.8950, 126.9190];
  m record;
  c record;
  r record;
  v_member bigint;
  v_crew bigint;
  v_post bigint;
  v_new_members integer := 0;
  v_new_crews integer := 0;
  v_new_runs integer := 0;
  v_posts integer := 0;
  v_chats integer := 0;
  v_day integer;
  v_start timestamp;
  v_target integer;
  v_pace double precision;
  v_park integer;
  v_rec text;
  v_run record;
  v_code text;
  v_ids bigint[];
  v_scores double precision[];
  v_count integer;
  v_total bigint;
  v_avg double precision;
  v_tier text;
  v_third timestamp;
  v_judged timestamp;
  v_type text;
  v_lean text;
  v_emulator bigint;
  v_nick text;
  v_sched timestamp;
  v_answer text;
  v_runs_k integer;
BEGIN
  -- 1. 더미 회원 18명 ------------------------------------------------------------
  CREATE TEMP TABLE seed_member ON COMMIT DROP AS
  SELECT * FROM (VALUES
    (1, 1988, 'MALE',        290, 22,  5, 1, '새벽 공기 좋아하는 러너',  'LEADER_BIRD'),
    (2, 1992, 'FEMALE',      330, 18,  6, 1, '한강 10km가 목표',        'PILLAR_ELEPHANT'),
    (3, 1996, 'MALE',        270, 25,  5, 1, '하프 마라톤 준비 중',      'COMMUTER_TURTLE'),
    (4, 1999, 'FEMALE',      365, 14,  6, 1, '러닝 시작한 지 두 달',     'WALKING_PUPPY'),
    (5, 1985, 'UNDISCLOSED', 315, 16,  5, 1, NULL,                     'PENGUIN_SQUAD'),
    (6, 1994, 'FEMALE',      345, 20, 12, 2, '점심 30분 러닝',          'WHEEL_HAMSTER'),
    (7, 1990, 'MALE',        300, 12, 12, 2, '회사 근처 공원 러너',      'MORNING_SPARROW'),
    (8, 2001, 'MALE',        385, 10, 12, 2, '천천히 꾸준히',           'PLAYGROUND_MONKEY'),
    (9, 1983, 'FEMALE',      320, 15, 12, 2, NULL,                     'ALLEY_SQUIRREL'),
    (10, 1997, 'MALE',       280, 24, 20, 3, '퇴근 후 스트레스 해소',    'LONE_WOLF'),
    (11, 1993, 'FEMALE',     355, 17, 19, 3, '저녁 러닝 메이트 구해요',  'STROLLING_CAT'),
    (12, 2003, 'MALE',       400, 11, 21, 3, NULL,                     'PICNIC_RABBIT'),
    (13, 1979, 'MALE',       285, 21,  7, 4, '주말엔 무조건 장거리',     'SKY_EAGLE'),
    (14, 1991, 'FEMALE',     310, 19,  8, 4, '풀코스 완주가 꿈',        'STEADY_CAMEL'),
    (15, 1987, 'MALE',       340, 13,  7, 4, NULL,                     'WILD_HORSE'),
    (16, 2000, 'FEMALE',     420, 10, 19, 5, '5km 완주 도전 중',        'EXPLORER_OTTER'),
    (17, 1995, 'UNDISCLOSED', 395, 12, 7, 5, '걷뛰부터 시작',           'PLAYGROUND_MONKEY'),
    (18, 1998, 'MALE',       370, 16, 20, 5, NULL,                     'WALKING_PUPPY')
  ) AS t(no, birth_year, gender, pace_s, runs, hour, crew_no, intro, runimal);
  ALTER TABLE seed_member ADD COLUMN nickname text, ADD COLUMN member_id bigint, ADD COLUMN created boolean DEFAULT false;
  UPDATE seed_member SET nickname = '더미러너' || lpad(no::text, 2, '0');

  FOR m IN SELECT * FROM seed_member ORDER BY no LOOP
    SELECT id INTO v_member FROM member WHERE nickname = m.nickname;
    IF NOT FOUND THEN
      INSERT INTO member (
          provider, provider_account_id, nickname, birth_year, gender, photo_url, intro, tier, level, joined_at,
          unit, record_visibility, course_map_shared, profile_visibility, muted_notification_types, watch_linked,
          run_target, run_voice, run_vibration, run_follow_coach_plan, battle_default_distance, battle_voice,
          battle_vibration, battle_gap_alert_interval, valid_run_count, recent_scores, total_valid_meters, status,
          withdrawn_at, created_at, updated_at, version, account_type, last_access_at, guest_credential_hash,
          screen_theme, battle_proximity_alert, guide_seen, age_band_visibility, gender_visibility)
      VALUES (
          NULL, NULL, m.nickname, m.birth_year, m.gender, NULL, m.intro, 'UNRANKED', 1, v_now - interval '60 days',
          'KM', 'PUBLIC', false, 'PUBLIC', '', false,
          'FREE', true, true, false, 'KM5', true,
          true, 'KM1', 0, '', 0, 'ACTIVE',
          NULL, v_now - interval '60 days', v_now - interval '60 days', 0, 'GUEST', v_now - make_interval(hours => m.no * 5), NULL,
          'SYSTEM', true, true, 'PUBLIC', 'PUBLIC')
      RETURNING id INTO v_member;
      -- 가입 때 받는 동의 6개(RequiredConsents.atSignUp, ConsentItem 순서, seq는 0부터)
      INSERT INTO member_consent (member_id, seq, item, agreed, changed_at, notice_version)
      SELECT v_member, ord - 1, item, item NOT IN ('MARKETING', 'LLM_TRANSFER'), v_now - interval '60 days', 1
      FROM unnest(ARRAY['AGE_14_PLUS', 'TERMS', 'PRIVACY', 'LOCATION', 'MARKETING', 'LLM_TRANSFER']) WITH ORDINALITY AS u(item, ord)
      ON CONFLICT DO NOTHING;
      UPDATE seed_member SET member_id = v_member, created = true WHERE no = m.no;
      v_new_members := v_new_members + 1;
    ELSE
      UPDATE seed_member SET member_id = v_member WHERE no = m.no;
    END IF;
  END LOOP;

  -- 2. 더미 크루 5개와 크루원 ------------------------------------------------------
  CREATE TEMP TABLE seed_crew ON COMMIT DROP AS
  SELECT * FROM (VALUES
    (1, '더미 새벽한강 크루',   '출근 전 여의도 한강에서 5km',  'WEEKDAY_DAWN,WEEKEND_DAWN',                      'FREE',     1, 5, 45000, 'KM5'),
    (2, '더미 점심런 크루',     '점심시간 30분 가볍게 뛰어요',   'WEEKDAY_LUNCH',                                  'FREE',     2, 12, 30000, 'KM3'),
    (3, '더미 퇴근길 러닝',     '퇴근하고 반포에서 같이 달려요', 'WEEKDAY_EVENING,WEEKEND_EVENING',                'APPROVAL', 3, 20, 35000, 'KM5'),
    (4, '더미 주말 장거리',     '주말 아침 10km 이상 장거리',    'WEEKEND_LUNCH,WEEKEND_MORNING',                  'FREE',     4, 7, 60000, 'KM10'),
    (5, '더미 초보 환영 크루',  '천천히 같이 시작해요',          'WEEKDAY_EVENING,WEEKDAY_MORNING,WEEKEND_MORNING', 'FREE',     5, 19, 25000, 'KM3')
  ) AS t(no, name, intro, tags, join_policy, park, hour, goal_m, run_distance);
  ALTER TABLE seed_crew ADD COLUMN crew_id bigint, ADD COLUMN created boolean DEFAULT false;

  FOR c IN SELECT * FROM seed_crew ORDER BY no LOOP
    SELECT id INTO v_crew FROM crew WHERE name = c.name;
    IF NOT FOUND THEN
      v_code := 'DMY' || lpad(c.no::text, 3, '0');
      WHILE EXISTS (SELECT 1 FROM crew WHERE invite_code = v_code) LOOP
        v_code := upper(substr(md5(random()::text), 1, 6));
      END LOOP;
      INSERT INTO crew (name, intro, tags, visibility, join_policy, logo_url, invite_code, invite_code_expires_at, created_at, version)
      VALUES (c.name, c.intro, c.tags, 'PUBLIC', c.join_policy, NULL, v_code, v_now + interval '7 days',
              v_now - interval '55 days' + make_interval(hours => c.no), 0)
      RETURNING id INTO v_crew;
      UPDATE seed_crew SET crew_id = v_crew, created = true WHERE no = c.no;
      v_new_crews := v_new_crews + 1;
    ELSE
      UPDATE seed_crew SET crew_id = v_crew WHERE no = c.no;
    END IF;
  END LOOP;

  -- 크루원: 크루 번호가 같은 더미 회원, 번호가 가장 작은 사람이 크루장, 크루원 4명 이상이면 둘째가 운영진. 활성 크루가 이미 있는 회원은 건너뛴다
  INSERT INTO crew_membership (crew_id, member_id, role, joined_at, left_at, leave_reason, crew_intro)
  SELECT sc.crew_id, sm.member_id,
         CASE WHEN rn = 1 THEN 'LEADER' WHEN rn = 2 AND cnt >= 4 THEN 'MANAGER' ELSE 'MEMBER' END,
         v_now - interval '50 days' + make_interval(hours => rn::int), NULL, NULL, NULL
  FROM (SELECT *, row_number() OVER (PARTITION BY crew_no ORDER BY no) AS rn, count(*) OVER (PARTITION BY crew_no) AS cnt
        FROM seed_member) sm
  JOIN seed_crew sc ON sc.no = sm.crew_no
  WHERE sc.created
    AND NOT EXISTS (SELECT 1 FROM crew_membership x WHERE x.member_id = sm.member_id AND x.left_at IS NULL);

  -- 3. 지난 크루런(합산 방식 결과): 크루마다 1~2개, 참석한 크루원은 CREW_RUN 기록을 남기고 끝낸다 ---------------------
  FOR c IN SELECT * FROM seed_crew WHERE created ORDER BY no LOOP
    FOR k IN 1..(CASE WHEN c.no % 2 = 1 THEN 2 ELSE 1 END) LOOP
      v_sched := (v_today - (k * 9 + c.no))::timestamp + make_interval(hours => c.hour, mins => 30);
      INSERT INTO crew_post (post_kind, crew_id, author_id, body, created_at, deleted, version, scheduled_at, distance,
                             vote_deadline, status, departed_at, ended_at, reminded, hidden)
      SELECT 'CREW_RUN', c.crew_id, sm.member_id,
             CASE k WHEN 1 THEN '정기 크루런 같이 달려요' ELSE '이번 주 번개런' END,
             v_sched - interval '3 days', false, 0, v_sched, c.run_distance, v_sched - interval '1 hour', 'FINISHED',
             v_sched + interval '3 seconds', v_sched + interval '75 minutes', true, false
      FROM seed_member sm WHERE sm.crew_no = c.no ORDER BY sm.no LIMIT 1
      RETURNING id INTO v_post;
      v_posts := v_posts + 1;
      FOR m IN SELECT sm.*, row_number() OVER (ORDER BY sm.no) AS rn FROM seed_member sm WHERE sm.crew_no = c.no ORDER BY sm.no LOOP
        IF m.rn = 3 AND k = 1 THEN
          -- 한 명은 불참으로 답했다
          INSERT INTO crew_run_participation (post_id, member_id, voted_at, answer, readiness, runner, ended,
                                              linked_record_id, final_distance_m, final_elapsed_ms)
          VALUES (v_post, m.member_id, v_sched - interval '2 days', 'NOT_GOING', 'NOT_ENTERED', false, false, NULL, NULL, NULL);
          CONTINUE;
        END IF;
        v_rec := md5('runkk-devseed:crewrun:' || m.nickname || ':' || c.no || ':' || k)::uuid::text;
        v_target := CASE c.run_distance WHEN 'KM3' THEN 3000 WHEN 'KM5' THEN 5000 ELSE 10000 END + (random() * 250)::integer;
        SELECT * INTO v_run FROM pg_temp.seed_run(
            v_rec, m.member_id, v_sched + interval '3 seconds', v_target, m.pace_s * (0.97 + random() * 0.06),
            park_lat[c.park], park_lon[c.park], 10, false, 'CREW_RUN', v_post, NULL, true);
        IF v_run.o_inserted THEN v_new_runs := v_new_runs + 1; END IF;
        INSERT INTO crew_run_participation (post_id, member_id, voted_at, answer, readiness, runner, ended,
                                            linked_record_id, final_distance_m, final_elapsed_ms)
        VALUES (v_post, m.member_id, v_sched - interval '2 days' + make_interval(mins => m.rn::int * 7), 'GOING', 'READY', true, true,
                v_rec, v_run.o_distance, v_run.o_elapsed);
      END LOOP;
      UPDATE crew_post p SET ended_at = p.departed_at + make_interval(secs => (
               SELECT max(final_elapsed_ms) / 1000.0 FROM crew_run_participation WHERE post_id = v_post))
      WHERE p.id = v_post;
    END LOOP;
  END LOOP;

  -- 4. 더미 회원의 자유 러닝: 최근 6주(어제까지)에 고르게, 거리 3~12km -----------------------------------
  FOR m IN SELECT * FROM seed_member WHERE created ORDER BY no LOOP
    FOR k IN 1..m.runs LOOP
      v_day := greatest(1, round(42 - (k - 1) * 41.0 / greatest(1, m.runs - 1) + (random() - 0.5) * 1.5)::integer);
      v_start := (v_today - v_day)::timestamp + make_interval(hours => m.hour, mins => (random() * 50)::integer);
      IF k = m.runs AND m.no % 2 = 0 THEN
        -- 짝수 번 회원의 마지막 러닝은 몇 시간 전으로 둬 이번 주 레이스·오늘 기록에 보이게 한다
        v_start := v_now - make_interval(mins => 200 + (random() * 120)::integer);
      END IF;
      v_target := CASE WHEN m.runimal IN ('LEADER_BIRD', 'PILLAR_ELEPHANT', 'COMMUTER_TURTLE', 'PENGUIN_SQUAD', 'LONE_WOLF',
                                          'SKY_EAGLE', 'STEADY_CAMEL', 'WILD_HORSE')
                       THEN 6000 + (random() * 6000)::integer
                       ELSE 3000 + (random() * 4000)::integer END;
      v_pace := m.pace_s * (0.94 + random() * 0.13) * (1 + (v_target - 5000) / 100000.0);
      v_park := CASE WHEN random() < 0.75 THEN (SELECT park FROM seed_crew WHERE no = m.crew_no) ELSE 6 + (k % 2) END;
      v_rec := md5('runkk-devseed:run:' || m.nickname || ':' || k)::uuid::text;
      SELECT * INTO v_run FROM pg_temp.seed_run(
          v_rec, m.member_id, v_start, v_target, v_pace, park_lat[v_park], park_lon[v_park], 10, false,
          'FREE', NULL, NULL, true);
      IF v_run.o_inserted THEN v_new_runs := v_new_runs + 1; END IF;
    END LOOP;
  END LOOP;

  -- 5. 티어·레벨·배치고사·Runimal: 새로 만든 더미 회원만, 저장 순서의 유효 기록으로 센다 -----------------------
  FOR m IN SELECT * FROM seed_member WHERE created ORDER BY no LOOP
    SELECT array_agg(score ORDER BY saved_at, record_id), count(*), sum(distance_meters)
      INTO v_scores, v_count, v_total
      FROM run_record WHERE member_id = m.member_id AND valid;
    IF v_count = 0 THEN CONTINUE; END IF;
    v_scores := v_scores[greatest(1, v_count - 9):v_count];   -- 최근 10개(오래된 것부터)
    SELECT avg(s) INTO v_avg FROM unnest(v_scores) s;
    v_tier := pg_temp.tier_of(v_avg);
    UPDATE member SET
        tier = v_tier,
        level = 1 + (v_total / 50000)::integer,
        valid_run_count = v_count,
        recent_scores = (SELECT string_agg(to_char(s, 'FM999990.0000'), ',' ORDER BY o) FROM unnest(v_scores) WITH ORDINALITY u(s, o)),
        total_valid_meters = v_total
    WHERE id = m.member_id AND valid_run_count = 0;
    SELECT saved_at INTO v_third FROM run_record WHERE member_id = m.member_id AND valid
      ORDER BY saved_at, record_id OFFSET 2 LIMIT 1;
    INSERT INTO placement_progress (member_id, counted_rounds, provisional_tier, confirmed_run_ordinal, completed_at, version)
    VALUES (m.member_id, 3, v_tier, 3, v_third, 0)
    ON CONFLICT DO NOTHING;
    -- Runimal: 3회째 임시 유형, 10의 배수마다 확정(RunimalProfile.onValidRun). 비율은 유형의 A/B 쪽에 맞춘 0~1 값
    SELECT saved_at INTO v_judged FROM run_record WHERE member_id = m.member_id AND valid
      ORDER BY saved_at, record_id OFFSET (CASE WHEN v_count >= 10 THEN (v_count / 10) * 10 ELSE 3 END) - 1 LIMIT 1;
    v_lean := CASE m.runimal
      WHEN 'MORNING_SPARROW' THEN 'AAAA' WHEN 'WHEEL_HAMSTER' THEN 'AAAB' WHEN 'ALLEY_SQUIRREL' THEN 'AABA'
      WHEN 'STROLLING_CAT' THEN 'AABB' WHEN 'COMMUTER_TURTLE' THEN 'ABAA' WHEN 'STEADY_CAMEL' THEN 'ABAB'
      WHEN 'SKY_EAGLE' THEN 'ABBA' WHEN 'LONE_WOLF' THEN 'ABBB' WHEN 'WALKING_PUPPY' THEN 'BAAA'
      WHEN 'PLAYGROUND_MONKEY' THEN 'BAAB' WHEN 'EXPLORER_OTTER' THEN 'BABA' WHEN 'PICNIC_RABBIT' THEN 'BABB'
      WHEN 'PILLAR_ELEPHANT' THEN 'BBAA' WHEN 'PENGUIN_SQUAD' THEN 'BBAB' WHEN 'LEADER_BIRD' THEN 'BBBA' ELSE 'BBBB' END;
    INSERT INTO runimal_profile (member_id, valid_run_count, provisional_type, confirmed_type, companion_ratio, distance_ratio,
                                 course_ratio, habit_ratio, judged_at, version)
    VALUES (m.member_id, v_count, m.runimal, CASE WHEN v_count >= 10 THEN m.runimal END,
            round((CASE substr(v_lean, 1, 1) WHEN 'A' THEN 0.58 + random() * 0.3 ELSE 0.12 + random() * 0.3 END)::numeric, 2),
            round((CASE substr(v_lean, 2, 1) WHEN 'A' THEN 0.58 + random() * 0.3 ELSE 0.12 + random() * 0.3 END)::numeric, 2),
            round((CASE substr(v_lean, 3, 1) WHEN 'A' THEN 0.58 + random() * 0.3 ELSE 0.12 + random() * 0.3 END)::numeric, 2),
            round((CASE substr(v_lean, 4, 1) WHEN 'A' THEN 0.58 + random() * 0.3 ELSE 0.12 + random() * 0.3 END)::numeric, 2),
            v_judged, 0)
    ON CONFLICT DO NOTHING;
    IF v_count >= 10 THEN
      INSERT INTO runimal_confirmed_history (member_id, type) VALUES (m.member_id, m.runimal) ON CONFLICT DO NOTHING;
    END IF;
  END LOOP;

  -- 6. 다가오는 크루런 모집글 2~3개와 참석 응답, 크루 채팅 -------------------------------------------
  FOR c IN SELECT * FROM seed_crew WHERE created ORDER BY no LOOP
    FOR k IN 1..(CASE WHEN c.no <= 3 THEN 3 ELSE 2 END) LOOP
      v_sched := (v_today + (k * 2 + c.no % 2))::timestamp + make_interval(hours => c.hour, mins => 30);
      INSERT INTO crew_post (post_kind, crew_id, author_id, body, created_at, deleted, version, scheduled_at, distance,
                             vote_deadline, status, departed_at, ended_at, reminded, hidden)
      SELECT 'CREW_RUN', c.crew_id, sm.member_id,
             CASE k WHEN 1 THEN '이번 주 정기 크루런 참석 응답 부탁해요' WHEN 2 THEN '가볍게 회복런 해요' ELSE '페이스 맞춰서 같이 뛰어요' END,
             v_now - make_interval(hours => 30 - k * 5), false, 0, v_sched, c.run_distance,
             v_sched - interval '1 hour', 'VOTING', NULL, NULL, false, false
      FROM seed_member sm WHERE sm.crew_no = c.no ORDER BY sm.no LIMIT 1
      RETURNING id INTO v_post;
      v_posts := v_posts + 1;
      FOR m IN SELECT sm.*, row_number() OVER (ORDER BY sm.no) AS rn FROM seed_member sm WHERE sm.crew_no = c.no ORDER BY sm.no LOOP
        -- 작성자(크루장)는 GOING(제안), 나머지는 GOING·UNDECIDED·NOT_GOING을 섞고 한 명은 아직 답하지 않았다
        v_answer := CASE WHEN m.rn = 1 THEN 'GOING'
                         WHEN (m.rn + k) % 4 = 0 THEN NULL
                         WHEN (m.rn + k) % 4 = 1 THEN 'UNDECIDED'
                         WHEN (m.rn + k) % 5 = 2 THEN 'NOT_GOING'
                         ELSE 'GOING' END;
        CONTINUE WHEN v_answer IS NULL;
        INSERT INTO crew_run_participation (post_id, member_id, voted_at, answer, readiness, runner, ended,
                                            linked_record_id, final_distance_m, final_elapsed_ms)
        VALUES (v_post, m.member_id, v_now - make_interval(hours => 30 - k * 5) + make_interval(mins => m.rn::int * 13),
                v_answer, 'NOT_ENTERED', false, false, NULL, NULL, NULL);
      END LOOP;
    END LOOP;

    INSERT INTO crew_chat_message (crew_id, author_id, client_message_id, content, sent_at, deleted, hidden)
    SELECT c.crew_id, sm.member_id, md5('runkk-devseed:chat:' || c.no || ':' || u.o)::uuid::text, u.body,
           v_now - make_interval(mins => (7 - u.o::int) * 47), false, false
    FROM unnest(ARRAY['다들 이번 주도 화이팅해요',
                      '어제 비 와서 실내에서 뛰었어요',
                      '이번 주 목표 거리 거의 다 왔네요',
                      '크루런 시간 괜찮으신가요?',
                      '저는 참석할게요',
                      '물 챙겨 오세요 오늘 덥대요']) WITH ORDINALITY AS u(body, o)
    JOIN (SELECT x.member_id, row_number() OVER (ORDER BY x.no) - 1 AS i, count(*) OVER () AS cnt
          FROM seed_member x WHERE x.crew_no = c.no) sm ON sm.i = (u.o - 1) % sm.cnt;
    v_chats := v_chats + 6;
  END LOOP;

  -- 7. 대상 계정(dev_seed_targets, 기본 "러너4171,러너1699") ------------------------------------------------
  FOR v_nick IN
    SELECT DISTINCT btrim(t) FROM unnest(string_to_array(current_setting('runkk.dev_seed_targets', true), ',')) AS t
    WHERE btrim(t) <> ''
  LOOP
    SELECT id INTO v_emulator FROM member WHERE nickname = v_nick AND status = 'ACTIVE';
    IF NOT FOUND THEN
      RAISE WARNING 'dev-seed: 회원 "%"이 없어 대상 계정 단계를 건너뛴다', v_nick;
    ELSE
      UPDATE member SET birth_year = 1998 WHERE id = v_emulator AND birth_year IS NULL;
      IF EXISTS (SELECT 1 FROM crew_membership WHERE member_id = v_emulator AND left_at IS NULL) THEN
        RAISE NOTICE 'dev-seed: %은 이미 활성 크루(%)가 있어 크루 가입은 건너뛴다', v_nick,
          (SELECT c2.name FROM crew_membership cm JOIN crew c2 ON c2.id = cm.crew_id
           WHERE cm.member_id = v_emulator AND cm.left_at IS NULL LIMIT 1);
      ELSE
        INSERT INTO crew_membership (crew_id, member_id, role, joined_at, left_at, leave_reason, crew_intro)
        SELECT crew_id, v_emulator, 'MEMBER', v_now - interval '43 days', NULL, NULL, NULL FROM seed_crew WHERE no = 1;
        RAISE NOTICE 'dev-seed: %을 "더미 새벽한강 크루" 크루원으로 넣었다', v_nick;
      END IF;
      -- 유효 러닝 22회: 경로·심박·움직임 표본(5초 간격). 티어 반영 표시는 세우지 않아 0시 10분 티어 보정(TierReconcileService,
      -- 저장 일시가 최근 2일인 반영 전 유효 기록)이 저장 순서대로 반영한다. 저장 일시는 지금 직전으로 시작 순서를 따른다
      v_runs_k := 0;
      FOR k IN 1..22 LOOP
        v_day := greatest(1, round(42 - (k - 1) * 41.0 / 21 + (random() - 0.5) * 1.2)::integer);
        v_start := (v_today - v_day)::timestamp
                   + make_interval(hours => CASE WHEN k % 3 = 0 THEN 7 ELSE 20 END, mins => (random() * 40)::integer);
        IF k = 22 THEN
          v_start := v_now - make_interval(mins => 240 + (random() * 60)::integer);
        END IF;
        v_target := 3000 + (random() * 7000)::integer;
        v_rec := md5('runkk-devseed:emulator:' || v_emulator || ':' || k)::uuid::text;
        SELECT * INTO v_run FROM pg_temp.seed_run(
            v_rec, v_emulator, v_start, v_target, (330 + random() * 50) * (1 + (v_target - 5000) / 120000.0),
            park_lat[1], park_lon[1], 5, true, 'FREE', NULL, v_now - make_interval(secs => 23 - k), false);
        IF v_run.o_inserted THEN
          v_new_runs := v_new_runs + 1;
          v_runs_k := v_runs_k + 1;
        END IF;
        -- 런바디 분석 작업: 서버 RunBodyPollJob(5초)의 pollDue가 next_poll_at이 지난 PENDING을 ai에 제출한다
        INSERT INTO runbody_job (record_id, member_id, status, attempts, polls, created_at, submitted_at, next_poll_at, version,
                                 overall_score, failure_reason, run_started_at)
        SELECT v_rec, v_emulator, 'PENDING', 0, 0, v_now, NULL, v_now, 0, NULL, NULL, r2.started_at
        FROM run_record r2 WHERE r2.record_id = v_rec AND r2.valid
        ON CONFLICT DO NOTHING;
      END LOOP;
      RAISE NOTICE 'dev-seed: % 러닝 % 회 추가(memberId=%)', v_nick, v_runs_k, v_emulator;
    END IF;
  END LOOP;

  -- 8. 주간 레이스: 이번 주·지난주, 그 주 시작일시에 소속이던 크루의 유효 기록 기여(WeeklyRaceService.onRunSaved와 같은 규칙) ---------
  INSERT INTO race_contribution (crew_id, week_start, member_id, record_id, meters)
  SELECT cm.crew_id, date_trunc('week', rr.started_at)::date, rr.member_id, rr.record_id, rr.distance_meters
  FROM run_record rr
  JOIN crew_membership cm ON cm.member_id = rr.member_id AND cm.joined_at <= rr.started_at
                          AND (cm.left_at IS NULL OR cm.left_at > rr.started_at)
  JOIN seed_crew sc ON sc.crew_id = cm.crew_id
  WHERE rr.valid AND rr.started_at >= (v_week - 7)
  ON CONFLICT DO NOTHING;

  INSERT INTO weekly_race (crew_id, week_start, total_meters, goal_meters, goal_set_by, achieved_at)
  SELECT sc.crew_id, w.week_start, 0, sc.goal_m,
         (SELECT member_id FROM crew_membership WHERE crew_id = sc.crew_id AND role = 'LEADER' AND left_at IS NULL LIMIT 1), NULL
  FROM seed_crew sc CROSS JOIN (VALUES (v_week), (v_week - 7)) AS w(week_start)
  ON CONFLICT DO NOTHING;

  UPDATE weekly_race wr SET total_meters = s.total
  FROM (SELECT rc.crew_id, rc.week_start, sum(rc.meters) AS total FROM race_contribution rc
        JOIN seed_crew sc ON sc.crew_id = rc.crew_id GROUP BY 1, 2) s
  WHERE wr.crew_id = s.crew_id AND wr.week_start = s.week_start AND wr.total_meters <> s.total;

  -- 목표에 닿은 주는 달성 시각을 남긴다(그 주 기여를 시작 순으로 더해 목표를 넘긴 기록의 저장 시각)
  UPDATE weekly_race wr SET achieved_at = x.at
  FROM (SELECT DISTINCT ON (rc.crew_id, rc.week_start) rc.crew_id, rc.week_start, rr.saved_at AS at
        FROM (SELECT rc.*, sum(rc.meters) OVER (PARTITION BY rc.crew_id, rc.week_start
                                                 ORDER BY rr2.saved_at, rc.record_id) AS running_total
              FROM race_contribution rc JOIN run_record rr2 ON rr2.record_id = rc.record_id) rc
        JOIN run_record rr ON rr.record_id = rc.record_id
        JOIN weekly_race w ON w.crew_id = rc.crew_id AND w.week_start = rc.week_start
        JOIN seed_crew sc ON sc.crew_id = rc.crew_id
        WHERE w.goal_meters IS NOT NULL AND rc.running_total >= w.goal_meters
        ORDER BY rc.crew_id, rc.week_start, rc.running_total) x
  WHERE wr.crew_id = x.crew_id AND wr.week_start = x.week_start AND wr.achieved_at IS NULL;

  RAISE NOTICE 'dev-seed: 새 더미 회원 %, 새 크루 %, 새 러닝 %, 새 크루런 글 %, 새 채팅 %',
    v_new_members, v_new_crews, v_new_runs, v_posts, v_chats;
  RAISE NOTICE 'dev-seed: 합계 더미 회원 %, 더미 크루 %, 더미 크루 활성 크루원 %, 더미 회원 러닝 %',
    (SELECT count(*) FROM member WHERE nickname LIKE '더미러너%'),
    (SELECT count(*) FROM crew WHERE name LIKE '더미 %'),
    (SELECT count(*) FROM crew_membership cm JOIN crew c2 ON c2.id = cm.crew_id WHERE c2.name LIKE '더미 %' AND cm.left_at IS NULL),
    (SELECT count(*) FROM run_record rr JOIN member mm ON mm.id = rr.member_id WHERE mm.nickname LIKE '더미러너%');
END
$seed$;

COMMIT;
