package com.a207.runkk.app.core.avatar

import android.os.Handler
import android.os.Looper
import app.rive.runtime.kotlin.core.File
import app.rive.runtime.kotlin.core.FileAssetLoader
import app.rive.runtime.kotlin.core.RendererType
import java.util.concurrent.Executor
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** 앱 공용 Rive 파일 가져오기. 가져오기 스레드 둘(나·상대 칸을 함께 가져온다). */
internal object RivFiles {
    private val serial = AtomicInteger()
    private val handler = Handler(Looper.getMainLooper())

    private val order = AtomicLong()

    /** 가져오기 스레드 둘. 칸이 청한 것(급함)을 미리 가져오기보다 먼저 꺼낸다. 같은 줄은 들어온 차례다. */
    private val pool = ThreadPoolExecutor(
        IMPORT_THREADS,
        IMPORT_THREADS,
        0L,
        TimeUnit.MILLISECONDS,
        PriorityBlockingQueue<Runnable>()
    ) { task -> Thread(task, "runkk-avatar-riv-${serial.incrementAndGet()}").apply { isDaemon = true } }

    private class Ordered(val isUrgent: Boolean, val seq: Long, val task: Runnable) :
        Runnable by task,
        Comparable<Ordered> {
        override fun compareTo(other: Ordered): Int = compareValuesBy(this, other, { !it.isUrgent }, { it.seq })
    }

    val shared: RivImports<RivKey, ImportedRiv> = RivImports(
        import = ::import,
        dispose = { it.file.release() },
        worker = { pool.execute(Ordered(isUrgent = true, order.incrementAndGet(), it)) },
        main = Executor { handler.post(it) },
        later = { delayMs, task -> handler.postDelayed(task, delayMs) },
        prefetcher = { pool.execute(Ordered(isUrgent = false, order.incrementAndGet(), it)) }
    )

    /** 막는 가져오기(가져오기 스레드). 로더가 가져오는 동안 내장 그림 자리를 잡는다. */
    private fun import(key: RivKey): ImportedRiv {
        val startedAt = AvatarPrepareTrace.now()
        val loader: FileAssetLoader = when (key.motion) {
            AvatarMotion.STAND -> FrontAvatarAssetLoader(key.prepared.legacyStandingBody)
            AvatarMotion.RUN -> RunAvatarAssetLoader()
        }
        return ImportedRiv(File(key.prepared.riv(key.motion), RendererType.Rive, loader), loader).also {
            AvatarPrepareTrace.mark("rive-import", startedAt, "motion=${key.motion} track=${key.forTrack}")
        }
    }

    private const val IMPORT_THREADS = 2
}
