-- 피그마 맞춤 W7(S15P31A207-298, 결정 C5): 운영진 지정·해제 화면을 없애므로 기존 운영진을 크루원으로 바꾼다.
-- 운영진 API(PUT·DELETE /crews/{id}/managers/{memberId})와 MANAGER 역할 값·CHECK 제약은 남긴다.
-- 퇴장한 행의 역할도 함께 바꾼다(퇴장 행의 역할은 어디서도 읽지 않는다). 가입 일시·소개 등 다른 열은 그대로다.
UPDATE crew_membership SET role = 'MEMBER' WHERE role = 'MANAGER';
