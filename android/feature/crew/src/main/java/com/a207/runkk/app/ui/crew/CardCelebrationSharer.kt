package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.domain.crew.CelebrationCard
import com.a207.runkk.app.ui.share.CardSharer
import com.a207.runkk.app.ui.share.CardSide
import com.a207.runkk.app.ui.share.CrewGoalCardRenderer
import com.a207.runkk.app.ui.share.ShareOutcome
import com.a207.runkk.app.ui.share.SystemShareTarget
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 축하 카드를 그려(ui.share CrewGoalCardRenderer) 시스템 공유 창으로 보낸다(ui.share CardSharer·SystemShareTarget). */
internal class CardCelebrationSharer @Inject constructor(
    private val renderer: CrewGoalCardRenderer,
    private val sharer: CardSharer,
    private val target: SystemShareTarget
) : CelebrationSharer {
    override suspend fun share(card: CelebrationCard): UiText? {
        val image = withContext(Dispatchers.Default) { renderer.render(card) }
        return when (val outcome = sharer.share(image, CardSide.FRONT, target)) {
            ShareOutcome.Shared -> null
            ShareOutcome.Saved -> UiText.Res(R.string.crew_celebration_saved)
            is ShareOutcome.Fallback -> UiText.Res(outcome.messageRes)
            is ShareOutcome.Failed -> UiText.Res(outcome.messageRes)
        }
    }
}
