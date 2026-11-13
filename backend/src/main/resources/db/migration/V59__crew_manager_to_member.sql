-- 피그마 맞춤 W7(S15P31A207-298, 결정 C5): 운영진 지정·해제 화면을 없애므로 기존 운영진을 크루원으로 바꾼다.
-- 운영진 API(PUT·DELETE /crews/{id}/managers/{memberId})와 MANAGER 역할 값·CHECK 제약은 남긴다.
-- 퇴장한 행의 역할도 함께 바꾼다(퇴장 행의 역할은 어디서도 읽지 않는다). 가입 일시·소개 등 다른 열은 그대로다.
-- 되돌림 근거(코드 리뷰 M5): 바꾸기 전 운영진 소속 행 ID·크루·회원을 남긴다. 운영진이 없는 DB면 빈 표다.
-- 되돌리기: UPDATE crew_membership SET role = 'MANAGER' WHERE id IN (SELECT membership_id FROM crew_manager_backup_v59);
-- 운영 확인이 끝나면 뒤 마이그레이션에서 이 표를 지운다(후속 이슈).
CREATE TABLE crew_manager_backup_v59 AS
    SELECT id AS membership_id, crew_id, member_id FROM crew_membership WHERE role = 'MANAGER';
UPDATE crew_membership SET role = 'MEMBER' WHERE role = 'MANAGER';
