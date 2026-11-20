package com.a207.runkk.app

import android.content.Context
import androidx.core.content.res.ResourcesCompat
import com.a207.runkk.app.core.designsystem.R as DesignR
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.sync.AssetPreloadSource
import com.a207.runkk.app.domain.sync.PreloadAsset
import com.a207.runkk.app.domain.sync.PreloadStage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 시작 에셋 미리 받기의 대결 카운트다운 데우기 출처(S15P31A207-335 9회차). 셀프런·셀럽런을 누른 판과 랜덤 대결 성사 판에 쓰는
 * 클래스(진행 화면·ViewModel·조정자 한 회·성사 화면, 첫 판 다음 트랙 아바타 데우기)를 기본 스레드에서 미리 읽고 초기화해(클래스 확인을
 * 주 스레드에서 하지 않게) 숫자 글꼴(Barlow Condensed 기울임)을 읽어 둔다. 재부팅 뒤 첫 실행에서 누른 판이 이 일을 주 스레드에서 하지 않게
 * 한다. 카운트다운 첫 판은 Rive를 쓰지 않는다(VS 칸은 기록런 탭이 만든 정지 그림, 8회차). Rive 런타임과 내 성별 Rive 파일은 홈 아바타 준비
 * 출처가 같은 시작 로딩에서 읽고, 트랙 아바타 Rive는 카운트다운 다음 판에 붙잡는다.
 *
 * 프로세스 메모리 일이라 앱을 켤 때마다 한다. 앞당기기라 한 번만 하고 실패해도 건너뛴다. 클래스 이름은 릴리스(R8)에서 바뀌면 그 클래스만
 * 건너뛴다(이름이 낡았는지는 시험이 본다). 화면 모듈을 보지 않도록 이름으로 읽는다(app 허용 의존 그대로).
 */
internal class CountdownWarmupSource(private val warm: () -> Unit) : AssetPreloadSource {
    @Inject
    constructor(@ApplicationContext context: Context) : this({ warmCountdown(context) })

    @Volatile
    private var isWarmed = false

    override suspend fun publicAssets(): Outcome<List<PreloadAsset>> = Outcome.Success(emptyList())

    override suspend fun memberAssets(): Outcome<List<PreloadAsset>> = Outcome.Success(listOf(ASSET))

    override fun isCached(asset: PreloadAsset): Boolean = isWarmed

    override suspend fun fetch(asset: PreloadAsset, onFile: () -> Unit): Outcome<Unit> {
        withContext(Dispatchers.Default) { warm() }
        isWarmed = true
        onFile()
        return Outcome.Success(Unit)
    }

    internal companion object {
        val ASSET = PreloadAsset("battle:countdown", files = 1, stage = PreloadStage.OTHERS, isOptional = true)

        /** 누른 판·성사 판에서 처음 읽히던 클래스(재부팅 뒤 첫 회 atrace VerifyClass, 9회차). */
        val CLASSES: List<String> = listOf(
            "com.a207.runkk.app.ui.battle.RecordBattleViewModel",
            "com.a207.runkk.app.ui.battle.RecordBattleEvent\$Start",
            "com.a207.runkk.app.ui.battle.BattleRunningScreenKt",
            "com.a207.runkk.app.ui.battle.BattlePartsKt",
            "com.a207.runkk.app.ui.battle.BattleRunningViewModel",
            "com.a207.runkk.app.ui.battle.BattleRunningUiState",
            "com.a207.runkk.app.ui.battle.BattlePhase",
            "com.a207.runkk.app.ui.battle.CountdownSeconds",
            "com.a207.runkk.app.ui.battle.MatchScreenKt",
            "com.a207.runkk.app.ui.battle.MatchViewModel",
            "com.a207.runkk.app.ui.battle.MatchUiState",
            "com.a207.runkk.app.domain.battle.BattleRunCoordinator",
            "com.a207.runkk.app.domain.battle.BattleRunCoordinator\$BattleRun",
            "com.a207.runkk.app.domain.battle.BattleRunState\$Countdown",
            "com.a207.runkk.app.domain.run.CoordinatedRound",
            "com.a207.runkk.app.domain.run.RunStartSpec\$StartDevice",
            "com.a207.runkk.app.core.designsystem.RunPermissionEffectKt",
            "com.a207.runkk.app.core.designsystem.RunkkTrackCardKt"
        )

        /** 카운트다운 숫자(display, Barlow Condensed ExtraBold·Bold 기울임). */
        val FONTS: List<Int> = listOf(
            DesignR.font.barlow_condensed_extrabold_italic,
            DesignR.font.barlow_condensed_bold_italic
        )

        private fun warmCountdown(context: Context) {
            val loader = CountdownWarmupSource::class.java.classLoader
            // 릴리스에서 이름이 바뀐 클래스·읽지 못한 글꼴은 건너뛴다(그 화면이 처음 쓸 때 다시 읽는다).
            CLASSES.forEach { name -> runCatching { Class.forName(name, true, loader) } }
            FONTS.forEach { id -> runCatching { ResourcesCompat.getFont(context, id) } }
        }
    }
}
