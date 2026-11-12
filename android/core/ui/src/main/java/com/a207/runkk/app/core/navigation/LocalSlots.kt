package com.a207.runkk.app.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 칸 이름 → 모든 FeatureEntry가 낸 그리기 함수 목록(D-255, D-298). RunkkNavHost가 FeatureEntry.slots를 모아 넣는다.
 * 넣지 않으면 빈 표다(미리보기·시험).
 */
val LocalSlots = staticCompositionLocalOf<Map<SlotKey, List<@Composable () -> Unit>>> { emptyMap() }

/** 기능 등록 묶음의 칸 기여를 칸 이름별로 모은다. */
fun Collection<FeatureEntry>.collectSlots(): Map<SlotKey, List<@Composable () -> Unit>> =
    flatMap { it.slots.entries }.groupBy({ it.key }, { it.value })

/** 칸을 그리는 화면이 칸 내용에 넘기는 크루 ID(SlotKey.CREW_CHAT_TAB). 크루 화면 밖이면 null이다. */
val LocalSlotCrewId = staticCompositionLocalOf<Long?> { null }

/**
 * 칸을 그리는 측정 화면이 아바타 칸(SlotKey.RUN_AVATAR)에 넘기는 동작([임시:D-성-101], [임시:D-성-122]). 측정 중이면 RUN, 준비·일시정지·
 * 마감은 STAND다. 칸 밖이면 STAND. 바뀌는 값이라 compositionLocalOf로 둔다(읽는 칸 내용만 다시 그린다).
 */
val LocalSlotAvatarPose = compositionLocalOf { AvatarSlotPose.STAND }

/**
 * 칸을 그리는 측정 화면이 아바타 칸에 넘기는 진행 방향 원값([임시:D-성-130], [임시:D-성-133]): `rememberHeadingLeft(route)` 값을 그대로
 * 넘기고, 흔들림 거르기(2초 유지)와 뒤집기는 칸 내용이 한다. 처음·칸 밖은 오른쪽(false).
 */
val LocalSlotAvatarFacingLeft = compositionLocalOf { false }

/** 전신 정지 그림 칸(SlotKey.AVATAR_STILL)에 넘기는 사람. 칸 밖이면 null이다. [AvatarStillSlot] 참고. */
val LocalSlotAvatarStill = compositionLocalOf<AvatarStillSubject?> { null }

/** 전신 정지 그림 칸을 부른 쪽의 지금 그림. 칸 내용이 그릴 수 없을 때(꾸미기 닫힘) 대신 그린다. 칸 밖이면 null이다. */
val LocalSlotAvatarStillFallback = compositionLocalOf<(@Composable () -> Unit)?> { null }
