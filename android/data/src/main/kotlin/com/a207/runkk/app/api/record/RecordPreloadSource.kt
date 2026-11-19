package com.a207.runkk.app.api.record

import com.a207.runkk.app.api.core.ScreenCopyStore
import com.a207.runkk.app.api.core.SelfMember
import com.a207.runkk.app.api.core.ServerTime
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.record.AggregationPeriod
import com.a207.runkk.app.domain.record.RecordRepository
import com.a207.runkk.app.domain.sync.AssetPreloadSource
import com.a207.runkk.app.domain.sync.PreloadAsset
import com.a207.runkk.app.domain.sync.PreloadStage
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/**
 * 시작 에셋 미리 받기 출처: 랭킹·기록(S15P31A207-345 2차, 포트 domain.sync.AssetPreloadSource).
 *
 * - 랭킹(ranking:personal): 랭킹 탭 첫 화면(개인·주간) 응답 1 + 첫 화면에 보이는 러너 머리 최대 [RANKING_FACES]개(목록 원 256, 화면과
 *   같은 FaceImageSource 열쇠). 응답은 [records]가 화면 사본(내 계정 칸 + 내 순위를 뺀 공용 칸)에 남기고, 머리는 얼굴 디스크 캐시(공용)에
 *   남는다. 머리가 [RANKING_FACES]보다 적으면 남은 칸은 다 받은 것으로 채운다.
 * - 기록(records:month): 기록 탭 첫 화면(이번 달 달력)의 대시보드(오늘)와 이번 달 내 기록 목록, 파일 2. 홈 대시보드와 같은 사본이다.
 * - 적중: 지금 부를 값(기간·오늘·이번 달)의 사본이 내 계정 칸에 있으면 받지 않는다. 지난 사본만 있으면 받되 실패해도 건너뛴다(오프라인
 *   시작을 막지 않는다, 화면이 지난 사본 대신 받아서 그린다).
 */
internal class RecordPreloadSource(
    private val records: RecordRepository,
    private val copies: ScreenCopyStore,
    private val self: SelfMember,
    private val clock: Clock,
    private val zone: ZoneId
) : AssetPreloadSource {
    @Inject
    constructor(records: RecordRepository, copies: ScreenCopyStore, self: SelfMember, clock: Clock) :
        this(records, copies, self, clock, ZoneId.systemDefault())

    override suspend fun publicAssets(): Outcome<List<PreloadAsset>> = Outcome.Success(emptyList())

    override suspend fun memberAssets(): Outcome<List<PreloadAsset>> {
        val member = self.memberId() ?: return Outcome.Failure(AppError.Unauthorized)
        val hasOldRecords = copies.paramOf(member, RecordRepositoryImpl.DASHBOARD) != null &&
            copies.paramOf(member, RecordRepositoryImpl.RUNS) != null
        return Outcome.Success(
            listOf(
                PreloadAsset(RANKING, 1 + RANKING_FACES, PreloadStage.RANKING),
                PreloadAsset(RECORDS, 2, PreloadStage.OTHERS, isOptional = hasOldRecords)
            )
        )
    }

    override fun isCached(asset: PreloadAsset): Boolean {
        val member = self.memberId() ?: return false
        return when (asset.key) {
            RANKING -> copies.paramOf(member, RANKING_COPY) == PERIOD.name

            RECORDS -> {
                val today = today()
                val month = YearMonth.from(today)
                copies.paramOf(member, RecordRepositoryImpl.DASHBOARD) == ServerTime.formatDate(today) &&
                    copies.paramOf(member, RecordRepositoryImpl.RUNS) == rangeOf(month)
            }

            else -> false
        }
    }

    override suspend fun fetch(asset: PreloadAsset, onFile: () -> Unit): Outcome<Unit> = when (asset.key) {
        RANKING -> ranking(onFile)
        RECORDS -> month(onFile)
        else -> Outcome.Success(Unit)
    }

    private suspend fun ranking(onFile: () -> Unit): Outcome<Unit> {
        val ranking = when (val result = records.personalRanking(PERIOD)) {
            is Outcome.Success -> result.value
            is Outcome.Failure -> return result
        }
        onFile()
        val faces = ranking.entries.take(RANKING_FACES).mapNotNull { it.look?.faceKey }
            .filter { key -> FACE_PREFIXES.any(key::startsWith) }
            .distinct()
        for (key in faces) {
            val result = records.faceLayers(key)
            // 연결이 끊기면 다시 받는다(받은 머리는 디스크 캐시라 다시 받지 않는다). 거부(비공개 등)는 건너뛴다.
            if (result is Outcome.Failure && (result.error is AppError.Network || result.error is AppError.Server)) {
                return result
            }
            onFile()
        }
        return Outcome.Success(Unit)
    }

    private suspend fun month(onFile: () -> Unit): Outcome<Unit> {
        val today = today()
        val month = YearMonth.from(today)
        when (val dashboard = records.dashboard(today)) {
            is Outcome.Success -> onFile()
            is Outcome.Failure -> return dashboard
        }
        return when (val runs = records.runs(RecordRepository.SELF, month.atDay(1), month.atEndOfMonth())) {
            is Outcome.Success -> Outcome.Success(Unit).also { onFile() }
            is Outcome.Failure -> runs
        }
    }

    private fun today(): LocalDate = clock.instant().atZone(zone).toLocalDate()

    private fun rangeOf(month: YearMonth): String =
        ServerTime.formatDate(month.atDay(1)) + RecordRepositoryImpl.RANGE + ServerTime.formatDate(month.atEndOfMonth())

    private companion object {
        const val RANKING = "ranking:personal"
        const val RECORDS = "records:month"

        /** 랭킹 탭 기본(개인·주간, RankingUiState). */
        val PERIOD = AggregationPeriod.WEEKLY
        val RANKING_COPY = RecordRepositoryImpl.RANKING + PERIOD.name

        /** 랭킹 첫 화면에 보이는 줄 수(시상대 3 + 목록 첫 줄들). */
        const val RANKING_FACES = 10
        val FACE_PREFIXES = listOf("head:", "selfie:")
    }
}
