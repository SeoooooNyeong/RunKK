package com.a207.runkk.app.ui.avatar

/**
 * 아이템 칸 112의 상태(피그마 AV-9 칸 상태·상태 규칙, 꾸미기 A안·상점 A안 공용).
 * - [WEARING] 입는 중: 라임 2 테두리 + 오른쪽 위 라임 체크.
 * - [TRYING] 상점에서 입어 보는 중: 보라 2 테두리 + 보라 체크.
 * - [OWNED] 상점에서 이미 가진 것: 왼쪽 위 「보유」 칩.
 * - [LOCKED] 잠김: 그림 35% + 왼쪽 아래 자물쇠·조건 칩.
 * - [PLAIN] 그 밖.
 */
internal enum class ItemCellLook {
    WEARING,
    TRYING,
    OWNED,
    LOCKED,
    PLAIN
}
