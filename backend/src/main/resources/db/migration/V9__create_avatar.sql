-- avatar 패키지 소유 테이블(설계 5.1.1, D11 캐릭터 파일의 외형 부분: 얼굴·체형·피부색·착용).
-- 셀카 원본은 어디에도 저장하지 않는다. 변환된 SD 부위 PNG만 face_part_image에 둔다(D-59).

CREATE TABLE avatar (
    member_id           BIGINT      PRIMARY KEY,
    default_character   VARCHAR(20),
    creation_count      INTEGER     NOT NULL,
    last_created_on     DATE,
    created_on_last_day INTEGER     NOT NULL,
    body_shape          VARCHAR(10) NOT NULL,
    skin_tone           VARCHAR(10) NOT NULL,
    version             BIGINT      NOT NULL,
    CONSTRAINT ck_avatar_default_character
        CHECK (default_character IS NULL OR default_character IN ('RABBIT', 'BEAR', 'FOX', 'CAT')),
    CONSTRAINT ck_avatar_body_shape CHECK (body_shape IN ('STAGE1', 'STAGE2', 'STAGE3', 'STAGE4')),
    CONSTRAINT ck_avatar_skin_tone CHECK (skin_tone IN ('DAWN', 'MORNING', 'DAYTIME', 'EVENING', 'NIGHT')),
    CONSTRAINT ck_avatar_counts CHECK (creation_count >= 0 AND created_on_last_day >= 0)
);

-- 셀카 얼굴의 부위별 이미지 저장 키(SdFaceParts)
CREATE TABLE avatar_face_part (
    member_id BIGINT       NOT NULL REFERENCES avatar (member_id) ON DELETE CASCADE,
    part      VARCHAR(10)  NOT NULL,
    image_key VARCHAR(200) NOT NULL,
    PRIMARY KEY (member_id, part),
    CONSTRAINT ck_avatar_face_part CHECK (part IN ('EYES', 'NOSE', 'MOUTH', 'EARS', 'HAIR'))
);

-- 부위마다 착용 아이템 하나(FR-161). 아이템 ID는 운영 기준값 상점 목록의 ID이고 지워지지 않는다(D-44)
CREATE TABLE avatar_equipment (
    member_id BIGINT      NOT NULL REFERENCES avatar (member_id) ON DELETE CASCADE,
    slot      VARCHAR(10) NOT NULL,
    item_id   BIGINT      NOT NULL,
    PRIMARY KEY (member_id, slot),
    CONSTRAINT ck_avatar_equipment_slot CHECK (slot IN ('TOP', 'BOTTOM', 'SHOES', 'HAT'))
);

-- 변환된 SD 부위 PNG(FacePartImageStoreImpl). 회원마다 가장 최근 묶음만 남긴다.
CREATE TABLE face_part_image (
    image_key  VARCHAR(200) PRIMARY KEY,
    member_id  BIGINT       NOT NULL,
    part       VARCHAR(10)  NOT NULL,
    png        BYTEA        NOT NULL,
    created_at TIMESTAMP(6) NOT NULL
);

-- 탈퇴 삭제(FR-021)·재생성 때 이전 묶음 삭제
CREATE INDEX idx_face_part_image_member ON face_part_image (member_id);
