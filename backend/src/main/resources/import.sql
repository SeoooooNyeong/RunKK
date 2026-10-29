-- local·test 프로필(H2 create-drop) 전용: Hibernate가 스키마를 만든 뒤 한 번 실행한다. develop·prod는 Flyway(V22)가 원본이고 이 파일을 쓰지 않는다.
-- 게시 사진 바이트 칼럼(D-100). 엔티티(PostPhoto)는 메타만 매핑하고 바이트는 JdbcPostPhotoStore가 SQL로 읽고 쓴다.
ALTER TABLE crew_photo ADD COLUMN jpeg VARBINARY(1048576) DEFAULT X'' NOT NULL;
-- 기록 삭제 묘비(v0.14 D-232, V30). 엔티티 없이 running 저장소가 SQL로 읽고 쓴다. 같은 기록 ID의 재업로드를 막는다.
CREATE TABLE run_record_tombstone (record_id VARCHAR(36) PRIMARY KEY, member_id BIGINT NOT NULL, deleted_at TIMESTAMP(6) NOT NULL);
