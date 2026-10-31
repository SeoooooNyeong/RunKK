-- 셀럽 캐릭터 구분과 기록 넣기(2026-10-31 결정). 앱은 셀럽마다 전용 캐릭터 그림을 키("lee", "hwang", "son")로 고른다.

-- 1. 유명인 기록의 캐릭터 그림 키. NULL이면 앱이 공통 셀럽 그림을 쓴다.
ALTER TABLE celebrity_record ADD COLUMN figure_key VARCHAR(20);

-- 2. 대결에 남기는 유명인 캐릭터 그림 키. 유명인 기록이 지워져도 결과 화면이 같은 그림이다. 기존 대결은 NULL.
ALTER TABLE battle ADD COLUMN opponent_figure VARCHAR(20);

-- 3. 유명인 기록 10건(풀코스 4건, 5km·10km·하프는 이O주 개인 최고, 연예인 기O84·임O미·진O규, 2026-10-31 사용자 요청). 구간 기록이 없어 평균 페이스로 추정하고(A-006), 실명 동의가 없어 가운데 글자를 가린 익명 이름이다.
--    목록은 등록 시각 내림차순이라 아래 순서대로 보이도록 1초씩 앞당긴다. local 프로필은 import-local.sql이 같은 행을 넣는다.
INSERT INTO celebrity_record (anonymous_name, distance_meters, finish_millis, splits, source_name, source_url, registered_at, figure_key) VALUES ('이O주', 42195, 7640000, NULL, '2000 도쿄국제마라톤', 'https://m.seoul.co.kr/news/2025/12/09/20251209028003', TIMESTAMP '2026-10-31 00:00:03', 'lee');
INSERT INTO celebrity_record (anonymous_name, distance_meters, finish_millis, splits, source_name, source_url, registered_at, figure_key) VALUES ('황O조', 42195, 7689000, NULL, '1994 보스턴마라톤', 'https://en.wikipedia.org/wiki/Hwang_Young-cho', TIMESTAMP '2026-10-31 00:00:02', 'hwang');
INSERT INTO celebrity_record (anonymous_name, distance_meters, finish_millis, splits, source_name, source_url, registered_at, figure_key) VALUES ('황O조', 42195, 8003000, NULL, '1992 바르셀로나 올림픽', 'https://en.wikipedia.org/wiki/Hwang_Young-cho', TIMESTAMP '2026-10-31 00:00:01', 'hwang');
INSERT INTO celebrity_record (anonymous_name, distance_meters, finish_millis, splits, source_name, source_url, registered_at, figure_key) VALUES ('손O정', 42195, 8959000, NULL, '1936 베를린 올림픽', 'https://en.wikipedia.org/wiki/Sohn_Kee-chung', TIMESTAMP '2026-10-31 00:00:00', 'son');
INSERT INTO celebrity_record (anonymous_name, distance_meters, finish_millis, splits, source_name, source_url, registered_at, figure_key) VALUES ('이O주', 21097, 3664000, NULL, '하프마라톤 개인 최고', 'https://khan.co.kr/article/200910211733405', TIMESTAMP '2026-10-30 23:59:59', 'lee');
INSERT INTO celebrity_record (anonymous_name, distance_meters, finish_millis, splits, source_name, source_url, registered_at, figure_key) VALUES ('이O주', 10000, 1784110, NULL, '10000m 개인 최고', 'https://khan.co.kr/article/200910211733405', TIMESTAMP '2026-10-30 23:59:58', 'lee');
INSERT INTO celebrity_record (anonymous_name, distance_meters, finish_millis, splits, source_name, source_url, registered_at, figure_key) VALUES ('이O주', 5000, 852270, NULL, '5000m 개인 최고', 'https://khan.co.kr/article/200910211733405', TIMESTAMP '2026-10-30 23:59:57', 'lee');
INSERT INTO celebrity_record (anonymous_name, distance_meters, finish_millis, splits, source_name, source_url, registered_at, figure_key) VALUES ('기O84', 42195, 17296000, NULL, '2024 뉴욕마라톤', 'https://www.hankyung.com/article/2024110493257', TIMESTAMP '2026-10-30 23:59:56', 'gian');
INSERT INTO celebrity_record (anonymous_name, distance_meters, finish_millis, splits, source_name, source_url, registered_at, figure_key) VALUES ('임O미', 42195, 15643000, NULL, '2023 JTBC 서울마라톤', 'https://www.mindgil.com/news/articleView.html?idxno=85194', TIMESTAMP '2026-10-30 23:59:55', 'lim');
INSERT INTO celebrity_record (anonymous_name, distance_meters, finish_millis, splits, source_name, source_url, registered_at, figure_key) VALUES ('진O규', 10000, 3332000, NULL, '2026 양평 마라톤', 'https://www.topstarnews.net/news/articleView.html?idxno=16086280', TIMESTAMP '2026-10-30 23:59:54', 'jin');
