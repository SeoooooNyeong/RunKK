package com.a207.runkk.app.ui.share

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.core.content.FileProvider
import com.a207.runkk.app.domain.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 카드 이미지를 공유한다(6.1.14). 뒷면(코스 지도)인데 코스 지도 공유가 꺼져 있으면 거부한다(FR-069).
 * 이미지는 앱 캐시(shared_cards/)에 PNG로 쓰고 FileProvider 주소로 [ShareTarget]에 넘긴다.
 */
class CardSharer @VisibleForTesting constructor(
    private val context: Context,
    private val settings: SettingsRepository,
    /** 파일 쓰기·공유 디스패처(리뷰 N-10: 하드코딩하지 않고 받는다). */
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val uriFor: (Context, File) -> Uri
) {
    @Inject
    constructor(@ApplicationContext context: Context, settings: SettingsRepository) :
        this(context, settings, Dispatchers.IO, { ctx, file ->
            FileProvider.getUriForFile(ctx, "${ctx.packageName}$AUTHORITY_SUFFIX", file)
        })

    suspend fun share(card: Bitmap, side: CardSide, target: ShareTarget): ShareOutcome {
        if (side == CardSide.BACK && !settings.courseMapShared()) {
            return ShareOutcome.Failed(COURSE_MAP_NOT_SHARED, R.string.share_course_map_off)
        }
        val uri = withContext(ioDispatcher) {
            shareFailureOrNull("카드 이미지 파일 쓰기 실패") {
                val dir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
                // 이전에 넘긴 주소가 다른 카드 내용을 가리키지 않게 매번 새 이름으로 쓰고 지난 파일은 지운다.
                dir.listFiles()?.forEach { it.delete() }
                val file = File(dir, "card_${side.name.lowercase()}_${System.currentTimeMillis()}.png")
                val written = file.outputStream().use { card.compress(Bitmap.CompressFormat.PNG, QUALITY, it) }
                check(written) { "PNG compress failed" }
                uriFor(context, file)
            }
        } ?: return ShareOutcome.Failed(WRITE_FAILED, R.string.share_card_write_failed)
        return withContext(ioDispatcher) { target.share(context, uri) }
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
