package com.a207.runkk.app.api.core

import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.model.FacePart
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.Properties

/**
 * 내 얼굴(로그인한 회원 본인)의 마지막 모습을 앱 전용 저장소에 남긴다: 프로필의 얼굴 키·배경 키, 마지막 성별, 내 셀카 머리 층 PNG(정면
 * 세 층, 큰 아바타를 받았으면 옆모습 층과 메타 JSON까지). 앱을 새로 켜도 홈·마이·결과 화면이 첫 그림부터 내 얼굴을 그리고, 홈 큰 아바타가
 * 프로필(성별) 응답을 기다리지 않고 합성을 시작하게 한다(Jira 307). 다른 회원 얼굴은 남기지 않는다.
 *
 * - 계정별 칸(S15P31A207-345 2차, 받아 둔 것은 지우지 않는다): 회원마다 [root] 아래 따로 칸(m{회원ID})을 두고, 계정이 바뀌거나
 *   로그아웃해도 지우지 않는다. 다른 계정은 앞 계정 칸을 보지 못하고(앞 계정 얼굴이 섞이지 않는다), 앞 계정으로 돌아오면 그 칸을 바로
 *   준다. 한 칸만 두던 예전 사본([root] 바로 아래)은 처음 열 때 그 회원 칸으로 옮긴다.
 * - 한 칸의 내 머리 층은 디스크에 한 벌만 둔다(내 머리 요청은 HTTP 캐시에 남기지 않는다, FaceImageSource).
 * - 처음 물을 때 한 번 읽어 메모리에 두고 그 뒤로는 메모리에서 준다(작은 파일 몇 개라 주 스레드에서 읽어도 짧다).
 * - 읽기·쓰기 실패는 남긴 것이 없는 것으로 본다(화면은 받아서 그린다).
 * - 형식 판(S15P31A207-345): 머리 층은 남길 때의 앱 형식 판 [format]과 함께 둔다. 판이 다르거나(앱 갱신으로 판을 올림) 판을 적지 않은
 *   옛 사본은 없는 것으로 보아 다시 받고, 다시 받은 것은 같은 얼굴 키여도 덮어쓴다. 얼굴·배경 키와 성별은 판과 상관없다.
 * 여러 스레드가 함께 써도 된다.
 */
internal class SelfFaceStore(private val root: File, private val format: String = HEAD_FORMAT) {

    /** 내 프로필의 얼굴 키·배경 키. */
    data class Keys(val faceKey: String?, val backgroundKey: String?)

    /** 남긴 내 머리 전체(큰 아바타 입력): 정면 세 층, 옆모습 층(없으면 빈 목록), 메타 JSON. */
    class Head(val faceLayers: List<FaceLayer>, val rightLayers: List<ByteArray>, val meta: String)

    private data class Saved(
        val keys: Keys? = null,
        val layersKey: String? = null,
        val headKey: String? = null,
        val sideCount: Int = 0,
        val gender: String? = null,
        /** 남긴 머리 층의 형식 판. 없으면(옛 사본) 지금 판과 다르다. */
        val format: String? = null
    )

    private val lock = Any()
    private var isMigrated = false
    private val slots = HashMap<Long, Slot>()

    /** [member]의 마지막 얼굴·배경 키. 남긴 적이 없거나 로그아웃(null)이면 null. */
    fun keys(member: Long?): Keys? = synchronized(lock) { slot(member)?.load()?.keys }

    /** [member]의 마지막 성별(이름). 남긴 적이 없거나 로그아웃(null)이면 null. */
    fun gender(member: Long?): String? = synchronized(lock) { slot(member)?.load()?.gender }

    /** [member]가 남긴 [faceKey] 얼굴의 정면 층. 다른 얼굴이거나 없으면 null. */
    fun layers(member: Long?, faceKey: String): List<FaceLayer>? = synchronized(lock) {
        val slot = slot(member) ?: return null
        slot.load()?.takeIf { it.layersKey == faceKey && it.format == format } ?: return null
        slot.frontOf(faceKey)
    }

    /**
     * [member]가 [faceKey] 머리 전체(정면·옆모습·메타)를 지금 형식 판으로 다 남겼는가. 그림을 읽지 않고 파일이 있는지만 본다(시작 에셋
     * 미리 받기의 캐시 적중 판단, S15P31A207-345).
     */
    fun hasHead(member: Long?, faceKey: String): Boolean = synchronized(lock) {
        val slot = slot(member) ?: return false
        val current = slot.load()?.takeIf { isHeadOf(it, faceKey) } ?: return false
        File(slot.dir, META_JSON).isFile &&
            FRONT_PARTS.all { File(slot.dir, it.name + PNG).isFile } &&
            (0 until current.sideCount).all { File(slot.dir, SIDE + it + PNG).isFile }
    }

    private fun isHeadOf(saved: Saved, faceKey: String): Boolean =
        saved.headKey == faceKey && saved.layersKey == faceKey && saved.format == format

    /** [member]가 남긴 [faceKey] 머리 전체(정면·옆모습·메타). 큰 아바타를 받아 남긴 적이 없으면 null. */
    fun head(member: Long?, faceKey: String): Head? = synchronized(lock) {
        val slot = slot(member) ?: return null
        val current = slot.load()?.takeIf { isHeadOf(it, faceKey) } ?: return null
        val front = slot.frontOf(faceKey) ?: return null
        try {
            val side = (0 until current.sideCount).map { File(slot.dir, SIDE + it + PNG).readBytes() }
            Head(front, side, File(slot.dir, META_JSON).readText())
        } catch (e: IOException) {
            null
        }
    }

    fun saveKeys(member: Long, keys: Keys) = synchronized(lock) {
        val slot = slotOf(member)
        val before = slot.load()
        if (before?.keys == keys) return@synchronized
        slot.write((before ?: Saved()).copy(keys = keys))
    }

    fun saveGender(member: Long, gender: String) = synchronized(lock) {
        val slot = slotOf(member)
        val before = slot.load()
        if (before?.gender == gender) return@synchronized
        slot.write((before ?: Saved()).copy(gender = gender))
    }

    fun saveLayers(member: Long, faceKey: String, faceLayers: List<FaceLayer>) = synchronized(lock) {
        val slot = slotOf(member)
        val before = slot.load()
        val isCurrent = before?.format == format
        if (before?.layersKey == faceKey && isCurrent && slot.layers?.first == faceKey) return@synchronized
        if (!slot.writeFront(faceLayers)) return@synchronized
        slot.layers = faceKey to faceLayers
        // 정면을 바꿨으므로 다른 얼굴(또는 옛 형식 판)의 옆모습·메타는 더 이상 짝이 아니다.
        val headKey = before?.headKey?.takeIf { it == faceKey && isCurrent }
        slot.write((before ?: Saved()).copy(layersKey = faceKey, headKey = headKey, format = format))
    }

    /** 내 머리 전체를 남긴다(정면 층은 [saveLayers]와 같은 파일 한 벌). */
    fun saveHead(member: Long, faceKey: String, head: Head) = synchronized(lock) {
        val slot = slotOf(member)
        val before = slot.load()
        val isCurrent = before?.format == format
        if (before?.headKey == faceKey && before.layersKey == faceKey && isCurrent) return@synchronized
        val isFrontSaved = before?.layersKey == faceKey && isCurrent && slot.layers?.first == faceKey
        if (!isFrontSaved && !slot.writeFront(head.faceLayers)) return@synchronized
        try {
            head.rightLayers.forEachIndexed { index, png -> slot.atomicWrite(File(slot.dir, SIDE + index + PNG), png) }
            slot.atomicWrite(File(slot.dir, META_JSON), head.meta.toByteArray())
        } catch (e: IOException) {
            return@synchronized
        }
        slot.layers = faceKey to head.faceLayers
        slot.write(
            (before ?: Saved()).copy(
                layersKey = faceKey,
                headKey = faceKey,
                sideCount = head.rightLayers.size,
                format = format
            )
        )
    }

    /** 로그인한 회원의 칸. 로그아웃(null)이면 칸이 없다. [lock] 안에서 부른다. */
    private fun slot(member: Long?): Slot? = member?.let(::slotOf)

    private fun slotOf(member: Long): Slot {
        migrate()
        return slots.getOrPut(member) { Slot(member, File(root, SLOT_PREFIX + member)) }
    }

    /** 한 칸만 두던 예전 사본([root] 바로 아래 파일)을 그 회원 칸으로 옮긴다. 옮기지 못한 파일은 그대로 둔다(지우지 않는다). */
    private fun migrate() {
        if (isMigrated) return
        isMigrated = true
        val meta = File(root, META)
        if (!meta.isFile) return
        val member = try {
            Properties().apply { meta.inputStream().use(::load) }.getProperty(MEMBER)?.toLongOrNull()
        } catch (e: IOException) {
            null
        } ?: return
        val target = File(root, SLOT_PREFIX + member)
        if (target.exists() || !target.mkdirs()) return
        root.listFiles()?.filter { it.isFile }?.forEach { it.renameTo(File(target, it.name)) }
    }

    /** 회원 한 명의 칸(파일 폴더 [dir]). 바깥 [lock] 안에서만 쓴다. */
    private inner class Slot(private val member: Long, val dir: File) {
        private var isLoaded = false
        private var saved: Saved? = null

        /** 읽어 둔 층(층 키와 함께). */
        var layers: Pair<String, List<FaceLayer>>? = null

        fun frontOf(faceKey: String): List<FaceLayer>? {
            layers?.takeIf { it.first == faceKey }?.let { return it.second }
            val read = readLayers() ?: return null
            layers = faceKey to read
            return read
        }

        fun writeFront(faceLayers: List<FaceLayer>): Boolean = try {
            dir.mkdirs()
            FacePart.entries.forEach { File(dir, it.name + PNG).delete() }
            faceLayers.forEach { layer -> atomicWrite(File(dir, layer.part.name + PNG), layer.png) }
            true
        } catch (e: IOException) {
            false
        }

        /** 남긴 메타를 한 번 읽는다. 다른 회원 것이 적혀 있으면(손상) 없는 것으로 본다. */
        fun load(): Saved? {
            if (isLoaded) return saved
            isLoaded = true
            saved = try {
                val file = File(dir, META)
                if (!file.isFile) return null
                val props = Properties().apply { file.inputStream().use(::load) }
                if (props.getProperty(MEMBER)?.toLongOrNull() != member) return null
                val keys = if (props.getProperty(HAS_KEYS) == "1") {
                    Keys(props.getProperty(FACE_KEY), props.getProperty(BACKGROUND_KEY))
                } else {
                    null
                }
                Saved(
                    keys = keys,
                    layersKey = props.getProperty(LAYERS_KEY),
                    headKey = props.getProperty(HEAD_KEY),
                    sideCount = props.getProperty(SIDE_COUNT)?.toIntOrNull() ?: 0,
                    gender = props.getProperty(GENDER),
                    format = props.getProperty(FORMAT)
                )
            } catch (e: IOException) {
                null
            } catch (e: IllegalArgumentException) {
                null
            }
            return saved
        }

        fun write(next: Saved) {
            val props = Properties()
            props.setProperty(MEMBER, member.toString())
            next.keys?.let { keys ->
                props.setProperty(HAS_KEYS, "1")
                keys.faceKey?.let { props.setProperty(FACE_KEY, it) }
                keys.backgroundKey?.let { props.setProperty(BACKGROUND_KEY, it) }
            }
            next.layersKey?.let { props.setProperty(LAYERS_KEY, it) }
            next.headKey?.let {
                props.setProperty(HEAD_KEY, it)
                props.setProperty(SIDE_COUNT, next.sideCount.toString())
            }
            next.gender?.let { props.setProperty(GENDER, it) }
            next.format?.let { props.setProperty(FORMAT, it) }
            try {
                val bytes = ByteArrayOutputStream().also { props.store(it, null) }.toByteArray()
                atomicWrite(File(dir, META), bytes)
                saved = next
            } catch (e: IOException) {
                // 남기지 못하면 다음 실행에서 받아서 그린다.
            }
        }

        private fun readLayers(): List<FaceLayer>? = try {
            FacePart.entries.mapNotNull { part ->
                File(dir, part.name + PNG).takeIf { it.isFile }?.let { FaceLayer(part, it.readBytes()) }
            }.takeIf { it.isNotEmpty() }
        } catch (e: IOException) {
            null
        }

        fun atomicWrite(target: File, bytes: ByteArray) {
            dir.mkdirs()
            val temp = File(dir, target.name + ".tmp")
            temp.writeBytes(bytes)
            if (!temp.renameTo(target)) {
                target.delete()
                if (!temp.renameTo(target)) throw IOException("rename failed: ${target.name}")
            }
        }
    }

    companion object {
        /**
         * 앱이 아는 머리 형식 판(서버 avatar.HeadFormat.VERSION과 같다, 층 주소의 f). 머리 층 형식을 바꾸면 서버와 함께 올린다. 올리면
         * 기기에 남긴 내 머리를 다시 받는다(S15P31A207-345).
         */
        internal const val HEAD_FORMAT = "v3"

        /** 큰 아바타 정면 세 층 자리(얼굴·눈·머리카락). */
        private val FRONT_PARTS = listOf(FacePart.EARS, FacePart.EYES, FacePart.HAIR)

        /** 회원 칸 폴더 이름 앞부분(m{회원ID}). */
        private const val SLOT_PREFIX = "m"
        private const val FORMAT = "format"
        private const val META = "meta.properties"
        private const val META_JSON = "head-meta.json"
        private const val SIDE = "side"
        private const val PNG = ".png"
        private const val MEMBER = "member"
        private const val HAS_KEYS = "hasKeys"
        private const val FACE_KEY = "faceKey"
        private const val BACKGROUND_KEY = "backgroundKey"
        private const val LAYERS_KEY = "layersKey"
        private const val HEAD_KEY = "headKey"
        private const val SIDE_COUNT = "sideCount"
        private const val GENDER = "gender"
    }
}
