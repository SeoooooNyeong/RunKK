package com.a207.runkk.app.ui.avatar

import android.content.Context
import com.a207.runkk.app.core.avatar.AvatarLookDisk
import com.a207.runkk.app.core.avatar.AvatarMotion
import com.a207.runkk.app.core.avatar.RiveAvatarLook
import com.a207.runkk.app.core.avatar.preloadRiveAvatarStills
import com.a207.runkk.app.core.avatar.prepareRiveAvatarLook
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.getOrNull
import com.a207.runkk.app.core.navigation.AvatarStillSubject
import com.a207.runkk.app.domain.avatar.PodiumAvatarPreparation
import com.a207.runkk.app.domain.record.AggregationPeriod
import com.a207.runkk.app.domain.record.Ranking
import com.a207.runkk.app.domain.record.RecordRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * 랭킹 시상대 아바타 미리 준비(S15P31A207-345 9회차, 포트 domain.avatar.PodiumAvatarPreparation). 시상대(RankingParts.PodiumStand)는
 * 전신 정지 그림 칸(SlotKey.AVATAR_STILL → [AvatarStillSlotContent])에 1~3위를 세우고, 칸은 Rive 서기를 한 번 그려 읽은 그림을 프로세스
 * 캐시(모습·같은 머리 인스턴스·칸 크기 열쇠)에 둔다. 그래서 여기서는 칸과 같은 값으로:
 *
 * - 다른 러너: 셀카 머리를 [SelfieHeads]로 받아(머리 레이어는 기기 공용 칸 사본) 칸과 같은 모습([runnerLookOf])을 만들고, 기기 공용 칸에
 *   남은 그 모습의 정지 그림을 캐시에 올린다([preloadRiveAvatarStills]). 그림이 아직 없으면(처음) 그 모습의 서기 합성을 미리 해 둔다
 *   (공용 칸 사본, 칸이 처음 그릴 때 합성을 기다리지 않는다).
 * - 나: 내 아바타 저장소 모습 그대로(홈 칸과 같은 것)의 정지 그림을 올린다.
 * - 랭킹은 받아 보고(시상대가 바뀌었을 수 있다) 받지 못하면 받아 둔 사본을 쓴다. 둘 다 없으면 연결 실패다. 머리를 받지 못한 러너는
 *   건너뛴다(칸이 스스로 받는다).
 */
@Singleton
internal class RankingPodiumPreparation(
    private val ranking: suspend () -> Ranking?,
    private val store: MyAvatarStore,
    private val heads: SelfieHeads,
    /** 그 모습의 서기 합성(기기 공용 칸 사본). */
    private val prepareLook: suspend (RiveAvatarLook) -> Unit,
    /** 기기 공용 칸의 정지 그림을 캐시에 올린다. 올린 수. */
    private val loadStills: suspend (RiveAvatarLook) -> Int,
    private val main: CoroutineDispatcher = Dispatchers.Main.immediate
) : PodiumAvatarPreparation {
    @Inject
    constructor(records: RecordRepository, store: MyAvatarStore, @ApplicationContext context: Context) : this(
        ranking = {
            records.personalRanking(AggregationPeriod.WEEKLY).getOrNull()
                ?: records.cachedPersonalRanking(AggregationPeriod.WEEKLY)
        },
        store = store,
        heads = store.heads,
        prepareLook = { look ->
            prepareRiveAvatarLook(
                context,
                look.gender,
                look.outfit,
                look.bodyShape,
                look.runningAmount,
                look.headLayers,
                AvatarMotion.STAND,
                AvatarLookDisk.shared(context)
            )
        },
        loadStills = { look -> preloadRiveAvatarStills(context, look) }
    )

    @Volatile
    private var isDone = false

    override fun isPrepared(): Boolean = isDone

    override suspend fun prepare(onStep: () -> Unit): Outcome<Unit> {
        val list = ranking() ?: return Outcome.Failure(AppError.Network)
        val podium = list.entries.take(PodiumAvatarPreparation.PODIUM_SIZE)
        coroutineScope {
            podium.map { entry ->
                async {
                    quietly { if (entry == list.mine) mine() else runner(entry) }
                    onStep()
                }
            }.awaitAll()
        }
        repeat(PodiumAvatarPreparation.PODIUM_SIZE - podium.size) { onStep() }
        isDone = true
        return Outcome.Success(Unit)
    }

    private suspend fun mine() {
        val look = withContext(main) {
            store.warm()
            (store.state.first { it !is MyAvatarState.Loading } as? MyAvatarState.Ready)?.look?.rive
        } ?: return
        loadStills(look)
    }

    private suspend fun runner(entry: Ranking.Entry) {
        val subject = subjectOf(entry)
        val faceKey = subject.look?.faceKey?.takeIf(::isSelfieHead)
        val face = if (faceKey == null) FaceLook.NONE else heads.look(faceKey)
        if (faceKey != null && face.headLayers == null) return
        val look = runnerLookOf(subject, face.headLayers)
        if (loadStills(look) == 0) prepareLook(look)
    }

    /** 앞당기기라 그림·합성 실패는 준비를 막지 않는다(칸이 스스로 다시 그린다). 취소는 넘긴다. */
    private suspend fun quietly(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 칸이 스스로 받고 그린다.
        } catch (e: OutOfMemoryError) {
            // 칸이 스스로 받고 그린다.
        }
    }

    internal companion object {
        /** 시상대 칸이 받는 값과 같다(RankingParts.PodiumStand, stillLookOf). */
        fun subjectOf(entry: Ranking.Entry): AvatarStillSubject = AvatarStillSubject(
            isMine = false,
            isFemale = entry.shownGender?.let { it == Gender.FEMALE },
            look = entry.look?.let { look ->
                AvatarStillSubject.Look(
                    faceKey = look.faceKey,
                    topKey = look.topKey,
                    bottomKey = look.bottomKey,
                    shoesKey = look.shoesKey,
                    bodyVariant = look.bodyVariant,
                    runAmount = look.runAmount
                )
            }
        )
    }
}
