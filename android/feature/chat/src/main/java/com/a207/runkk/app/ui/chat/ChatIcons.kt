package com.a207.runkk.app.ui.chat

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.domain.chat.ReactionKind

/** 반응 종류의 줄선 아이콘(좋아요 엄지, 하트, 체크). */
internal fun ReactionKind.icon(): ImageVector = when (this) {
    ReactionKind.LIKE -> RunkkIcons.ThumbsUp
    ReactionKind.HEART -> RunkkIcons.Heart
    ReactionKind.CHECK -> RunkkIcons.Check
}

/** 반응 종류 이름(좋아요, 하트, 체크). */
@StringRes
internal fun ReactionKind.labelRes(): Int = when (this) {
    ReactionKind.LIKE -> R.string.chat_reaction_like
    ReactionKind.HEART -> R.string.chat_reaction_heart
    ReactionKind.CHECK -> R.string.chat_reaction_check
}
