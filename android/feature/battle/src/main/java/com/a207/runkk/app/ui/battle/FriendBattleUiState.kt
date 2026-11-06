package com.a207.runkk.app.ui.battle

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.Distance

/**
 * 친구 대결 화면(FR-286, 피그마 61312:14822·61312:14940). [friends]는 최근 함께 달린 친구(최대 3명), [selectedFriendId]는 고른 친구
 * (고르는 것은 선택이고 초대에 묶지 않는다, J24-18), [isDistanceMenuOpen]은 거리 상자 펼침이다.
 */
data class FriendBattleUiState(
    val distance: Distance = MatchUiState.DEFAULT_DISTANCE,
    val isDistanceMenuOpen: Boolean = false,
    val friends: List<RecentFriend> = emptyList(),
    val selectedFriendId: Long? = null,
    val isConsentSheetVisible: Boolean = false,
    val message: UiText? = null
)
