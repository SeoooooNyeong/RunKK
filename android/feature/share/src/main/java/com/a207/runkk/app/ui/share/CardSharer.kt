package com.a207.runkk.app.ui.share

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import com.a207.runkk.app.domain.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 카드 이미지를 공유한다(6.1.14). 뒷면(코스 지도)인데 코스 지도 공유가 꺼져 있으면 거부한다(FR-069).
 * 이미지는 앱 캐시(shared_cards/)에 PNG로 쓰고 FileProvider 주소로 [ShareTarget]에 넘긴다.
 */
class CardSharer internal constructor(
    private val context: Context,
    private val settings: SettingsRepository,
    private val uriFor: (Context, File) -> Uri
) {
    @Inject
    constructor(@ApplicationContext context: Context, settings: SettingsRepository) :
        this(context, settings, { ctx, file ->
            FileProvider.getUriForFile(ctx, "${ctx.packageName}$AUTHORITY_SUFFIX", file)
        })

    suspend fun share(card: Bitmap, side: CardSide, target: ShareTarget): ShareOutcome {
        if (side == CardSide.BACK && !settings.courseMapShared()) {
            return ShareOutcome.Failed(COURSE_MAP_NOT_SHARED, "코스 지도 공유가 꺼져 있어 뒷면을 공유할 수 없어요. 마이 > 앱 설정에서 켤 수 있어요")
        }
        val uri = withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
                val file = File(dir, "card_${side.name.lowercase()}.png")
                file.outputStream().use { card.compress(Bitmap.CompressFormat.PNG, QUALITY, it) }
                uriFor(context, file)
            }.getOrNull()
        } ?: return ShareOutcome.Failed(WRITE_FAILED, "카드 이미지를 만들지 못했어요")
        return withContext(Dispatchers.IO) { target.share(context, uri) }
    }

    companion object {
        /** 뒷면 공유 거부 사유 코드. */
        const val COURSE_MAP_NOT_SHARED = "COURSE_MAP_NOT_SHARED"
        private const val WRITE_FAILED = "WRITE_FAILED"
        private const val CACHE_DIR = "shared_cards"
        private const val AUTHORITY_SUFFIX = ".runkk.cards"
        private const val QUALITY = 100
    }
}
