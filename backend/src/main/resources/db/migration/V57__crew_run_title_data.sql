-- W6 크루 일정(S15P31A207-297) 데이터 옮기기. 스키마는 V56.

-- 크루런 제목·내용 분리: 앱이 "제목\n내용"으로 합쳐 보내던 본문의 첫 줄을 제목, 나머지 줄을 내용으로 나눈다.
--    앞뒤 공백·빈 줄은 걷어 내고, 첫 줄이 비면 제목은 '크루런'이다. 다른 종류 글은 그대로다.
UPDATE crew_post
SET title = COALESCE(
        NULLIF(left(btrim(split_part(btrim(body, E' \t\r\n'), E'\n', 1), E' \t\r'), 200), ''),
        '크루런'),
    body  = CASE
                WHEN position(E'\n' IN btrim(body, E' \t\r\n')) > 0
                    THEN btrim(substr(btrim(body, E' \t\r\n'),
                                      position(E'\n' IN btrim(body, E' \t\r\n')) + 1), E' \t\r\n')
                ELSE ''
            END
WHERE post_kind = 'CREW_RUN';
