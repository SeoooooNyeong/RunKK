package com.a207.runkk.app.api.core

import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.model.FacePart
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.Properties

/**
 * 내 얼굴(로그인한 회원 본인)의 마지막 모습을 앱 전용 저장소에 남긴다: 프로필의 얼굴 키·배경 키와 내 셀카 머리 층 PNG. 앱을 새로 켜도
 * 홈·마이·결과 화면이 첫 그림부터 내 얼굴을 그리게 한다(기본 머리가 보였다가 바뀌지 않게). 다른 회원 얼굴은 남기지 않는다.
 *
 * - 저장한 회원과 지금 로그인한 회원이 다르면(계정 바뀜, 로그아웃) 아무것도 내주지 않고, [clearUnless]가 지운다.
 * - 처음 물을 때 한 번 읽어 메모리에 두고 그 뒤로는 메모리에서 준다(작은 파일 몇 개라 주 스레드에서 읽어도 짧다).
 * - 읽기·쓰기 실패는 남긴 것이 없는 것으로 본다(화면은 받아서 그린다).
 * 여러 스레드가 함께 써도 된다.
 */
internal class SelfFaceStore(private val dir: File) {
    /** 내 프로필의 얼굴 키·배경 키. */
    data class Keys(val faceKey: String?, val backgroundKey: String?)

    private class Saved(val member: Long, val keys: Keys?, val layersKey: String?)

    private val lock = Any()
    private var isLoaded = false
    private var saved: Saved? = null

    /** 읽어 둔 층(층 키와 함께). */
    private var layers: Pair<String, List<FaceLayer>>? = null

    /** [member]의 마지막 얼굴·배경 키. 남긴 적이 없거나 다른 회원 것이면 null. */
    fun keys(member: Long?): Keys? = synchronized(lock) { load()?.takeIf { it.member == member }?.keys }

    /** [member]가 남긴 [faceKey] 얼굴의 층. 다른 얼굴이거나 없으면 null. */
    fun layers(member: Long?, faceKey: String): List<FaceLayer>? = synchronized(lock) {
        load()?.takeIf { it.member == member && it.layersKey == faceKey } ?: return null
        layers?.takeIf { it.first == faceKey }?.let { return it.second }
        val read = readLayers() ?: return null
        layers = faceKey to read
        read
    }

    fun saveKeys(member: Long, keys: Keys) = synchronized(lock) {
        val before = load()?.takeIf { it.member == member }
        if (before?.keys == keys) return@synchronized
        write(Saved(member, keys, before?.layersKey))
    }

    fun saveLayers(member: Long, faceKey: String, faceLayers: List<FaceLayer>) = synchronized(lock) {
        val before = load()?.takeIf { it.member == member }
        if (before?.layersKey == faceKey && layers?.first == faceKey) return@synchronized
        try {
            dir.mkdirs()
            FacePart.entries.forEach { File(dir, it.name + PNG).delete() }
            faceLayers.forEach { layer -> atomicWrite(File(dir, layer.part.name + PNG), layer.png) }
        } catch (e: IOException) {
            return@synchronized
        }
        layers = faceKey to faceLayers
        write(Saved(member, before?.keys, faceKey))
    }

    /** 남긴 것의 회원(이 기기의 얼굴 캐시를 마지막으로 쓴 회원). 남긴 것이 없으면 null. */
    fun owner(): Long? = synchronized(lock) { load()?.member }

    /**
     * 얼굴 캐시(디스크 HTTP 캐시 포함)를 [member]가 쓴다고 남긴다. 다른 회원 것이 남아 있으면 먼저 [clearUnless]로 지운 뒤에 부른다.
     * 앱을 새로 켰을 때 디스크 캐시가 같은 회원 것인지 이것으로 가린다(S15P31A207-300).
     */
    fun claim(member: Long) = synchronized(lock) {
        val before = load()
        if (before?.member == member) return@synchronized
        write(Saved(member, null, null))
    }

    /** 저장한 회원이 [member]가 아니면(계정 바뀜·로그아웃이면 null) 지운다. */
    fun clearUnless(member: Long?) = synchronized(lock) {
        val current = load() ?: return@synchronized
        if (current.member == member) return@synchronized
        dir.listFiles()?.forEach { it.delete() }
        saved = null
        layers = null
    }

    /** 남긴 메타를 한 번 읽는다. */
    private fun load(): Saved? {
        if (isLoaded) return saved
        isLoaded = true
        saved = try {
            val file = File(dir, META)
            if (!file.isFile) return null
            val props = Properties().apply { file.inputStream().use(::load) }
            val member = props.getProperty(MEMBER)?.toLongOrNull() ?: return null
            val keys = if (props.getProperty(HAS_KEYS) == "1") {
                Keys(props.getProperty(FACE_KEY), props.getProperty(BACKGROUND_KEY))
            } else {
                null
            }
            Saved(member, keys, props.getProperty(LAYERS_KEY))
        } catch (e: IOException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
        return saved
    }

    private fun write(next: Saved) {
        val props = Properties()
        props.setProperty(MEMBER, next.member.toString())
        next.keys?.let { keys ->
            props.setProperty(HAS_KEYS, "1")
            keys.faceKey?.let { props.setProperty(FACE_KEY, it) }
            keys.backgroundKey?.let { props.setProperty(BACKGROUND_KEY, it) }
        }
        next.layersKey?.let { props.setProperty(LAYERS_KEY, it) }
        try {
            dir.mkdirs()
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

    private fun atomicWrite(target: File, bytes: ByteArray) {
        val temp = File(dir, target.name + ".tmp")
        temp.writeBytes(bytes)
        if (!temp.renameTo(target)) {
            target.delete()
            if (!temp.renameTo(target)) throw IOException("rename failed: ${target.name}")
        }
    }

    private companion object {
        const val META = "meta.properties"
        const val PNG = ".png"
        const val MEMBER = "member"
        const val HAS_KEYS = "hasKeys"
        const val FACE_KEY = "faceKey"
        const val BACKGROUND_KEY = "backgroundKey"
        const val LAYERS_KEY = "layersKey"
    }
}
