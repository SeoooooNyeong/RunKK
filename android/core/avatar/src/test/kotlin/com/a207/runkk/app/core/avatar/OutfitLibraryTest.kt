package com.a207.runkk.app.core.avatar

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 공용 의상 자산([OutfitLibrary]): 설치는 한 번만, 목록 해석이 실패하면 아무것도 바꾸지 않아 다음 설치가 다시 시도하고, 없는 파일은
 * 한 번만 찾아본다. 남자 서기 상의 표면은 실제로 그리는 묶음(고른 묶음 파일이 없으면 base_top)의 값이다.
 */
@RunWith(RobolectricTestRunner::class)
class OutfitLibraryTest {
    private val reads = ConcurrentHashMap<String, AtomicInteger>()

    @AfterTest
    fun restore() {
        // 다른 시험이 쓰는 실제 자산으로 되돌린다.
        OutfitLibrary.install(OutfitFiles { File("src/main/assets", it).takeIf(File::isFile)?.readBytes() })
    }

    @Test
    fun `목록 해석이 실패하면 설치하지 않은 채로 남아 다음 설치가 다시 시도한다`() {
        OutfitLibrary.reset()
        assertFailsWith<IllegalArgumentException> { OutfitLibrary.installOnce(source(index = "not json")) }
        assertNull(OutfitLibrary.catalog)
        assertFailsWith<IllegalStateException>("파일 출처도 열지 않는다") { OutfitLibrary.read("outfits/a.webp") }

        OutfitLibrary.installOnce(source())
        assertNotNull(OutfitLibrary.catalog?.bundle(OutfitPart.TOP, "base_top"))
        assertTrue(OutfitLibrary.read("outfits/base_top/female_stand.webp") != null)
    }

    @Test
    fun `여러 스레드가 함께 설치해도 목록은 한 번만 읽는다`() {
        OutfitLibrary.reset()
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(THREADS)
        val failures = AtomicInteger()
        val source = source()
        repeat(THREADS) {
            pool.execute {
                start.await()
                runCatching { OutfitLibrary.installOnce(source) }.onFailure { failures.incrementAndGet() }
                if (OutfitLibrary.catalog == null) failures.incrementAndGet()
            }
        }
        start.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        assertEquals(0, failures.get(), "설치 중에 목록이 비어 보이거나 실패하면 안 된다")
        assertEquals(1, reads.getValue(OutfitCatalog.INDEX).get())
    }

    @Test
    fun `없는 파일은 한 번만 찾아보고 다시 설치하면 잊는다`() {
        OutfitLibrary.install(source())
        val missing = "outfits/top_crew_tee/female_stand.webp"
        assertNull(OutfitLibrary.read(missing))
        assertNull(OutfitLibrary.read(missing))
        assertEquals(1, reads.getValue(missing).get())

        OutfitLibrary.install(source())
        assertNull(OutfitLibrary.read(missing))
        assertEquals(2, reads.getValue(missing).get())
    }

    @Test
    fun `남자 서기 상의 파일이 없으면 상의 표면도 base_top 값이다`() {
        OutfitLibrary.install(source())
        // top_winter_hoodie는 표면 2(긴소매)지만 그 묶음에 male_stand 파일이 없어 base_top(표면 1)을 그린다.
        assertEquals(1f, RiveAvatarScene.topSurface(AvatarGender.MALE, 4))
        assertEquals(1f, RiveAvatarScene.topSurface(AvatarGender.MALE, 0))
    }

    /** base_top(표면 1, 파일 있음)과 top_winter_hoodie(표면 2, 파일 없음)만 있는 자산. */
    private fun source(index: String = INDEX): OutfitFiles = OutfitFiles { path ->
        reads.getOrPut(path) { AtomicInteger() }.incrementAndGet()
        when (path) {
            OutfitCatalog.INDEX -> index.encodeToByteArray()
            "outfits/base_top/female_stand.webp", "outfits/base_top/male_stand.webp" -> byteArrayOf(1)
            else -> null
        }
    }

    private companion object {
        const val THREADS = 8
        val INDEX = """
            {"version": 2, "outfits": [
              {"assetKey": "base_top", "part": "TOP", "maleTopSurface": 1},
              {"assetKey": "top_winter_hoodie", "part": "TOP", "maleTopSurface": 2}
            ]}
        """.trimIndent()
    }
}
