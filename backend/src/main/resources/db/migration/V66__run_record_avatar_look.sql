-- 러닝 기록에 저장 시점의 아바타 모습을 남긴다(설계 05-31 B [임시:D-성-249], [임시:FR-성-26]). 기록 대결(나와의 싸움) 후보가
-- "그날의 나"를 그 모습으로 그린다. 전부 널 허용이고 옛 기록은 채우지 않는다(백필 없음, 후보는 지금 모습으로 대신한다).
-- 성별과 체형·러닝량은 모습이 있으면 함께 있고 없으면 함께 없다. 옷 세 부위와 얼굴 키는 모습이 있어도 없을 수 있다.
-- look_face_key는 Face.renderKey다: "default:이름", "head:{회원}:{작업 64자}", "selfie:{부위 이미지 묶음 키 최대 200자}"라 255자로 둔다.
ALTER TABLE run_record
    ADD COLUMN look_gender         VARCHAR(6),
    ADD COLUMN look_top_item_id    BIGINT,
    ADD COLUMN look_bottom_item_id BIGINT,
    ADD COLUMN look_shoes_item_id  BIGINT,
    ADD COLUMN look_body_variant   SMALLINT,
    ADD COLUMN look_run_amount     SMALLINT,
    ADD COLUMN look_face_key       VARCHAR(255),
    ADD CONSTRAINT ck_run_record_look CHECK (
        (look_gender IS NULL) = (look_body_variant IS NULL)
        AND (look_gender IS NULL) = (look_run_amount IS NULL)
        AND (look_gender IS NOT NULL
             OR (look_top_item_id IS NULL AND look_bottom_item_id IS NULL
                 AND look_shoes_item_id IS NULL AND look_face_key IS NULL)));
