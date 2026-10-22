-- V15 backfill이 남긴 활성 대결 칸 불일치 정리(코드 리뷰 3차 B-L1). V15는 이미 적용된 DB가 있어 고치지 않는다.
--
-- V15는 회원마다 가장 최근 활성 대결 하나에만 active_member_id를 채웠다. 이전 데이터에 한 회원의 활성 대결이 둘 이상이면 나머지 대결은
-- 활성 상태인데 그 회원의 칸이 NULL로 남는다. 그 대결이 IN_PROGRESS → COUNTDOWN처럼 활성 → 활성으로 넘어가면 Battle.moveTo가 칸을
-- 채워 최신 대결과 uq_battle_participant_active_member 충돌이 나고, 시계·도착 처리가 그 대결에서 계속 실패한다.
--
-- 엔티티 규칙(Battle.moveTo → BattleParticipant.syncActive)은 "활성이면 모든 참가자의 칸 = 회원 ID, 아니면 NULL"이다. 칸이 빈 활성 대결은
-- 그 회원의 칸이 이미 최신 대결에 있으므로 이 규칙대로 채울 수 없다(회원당 활성 대결 1개 불변식). 그래서 칸을 채우지 않고 끝 상태로 보낸다:
-- 빈 칸이 하나라도 있는 활성 대결을 CANCELLED(활성 아님, 판정·리워드·전적·이력 없음 — 버려진 대결 정리와 같은 끝 상태)로 바꾸고,
-- 활성이 아닌 대결의 칸을 모두 비워 엔티티 규칙과 맞춘다. 회원마다 가장 최근 활성 대결은 V15가 칸을 채웠으므로 그대로 남는다.
-- 일부 참가자만 칸이 찬 대결(상대 회원의 최신 대결이 따로 있는 경우)도 같은 이유로 취소한다.

UPDATE battle b
SET status    = 'CANCELLED',
    closed_at = COALESCE(b.closed_at, LOCALTIMESTAMP),
    version   = b.version + 1
WHERE b.status IN ('WAITING_START', 'IN_PROGRESS', 'COUNTDOWN')
  AND EXISTS (
    SELECT 1
    FROM battle_participant p
    WHERE p.battle_id = b.id
      AND p.active_member_id IS NULL
  );

UPDATE battle_participant p
SET active_member_id = NULL
WHERE p.active_member_id IS NOT NULL
  AND EXISTS (
    SELECT 1
    FROM battle b
    WHERE b.id = p.battle_id
      AND b.status NOT IN ('WAITING_START', 'IN_PROGRESS', 'COUNTDOWN')
  );
