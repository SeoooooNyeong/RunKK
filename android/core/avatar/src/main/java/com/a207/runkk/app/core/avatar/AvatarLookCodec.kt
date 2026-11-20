package com.a207.runkk.app.core.avatar

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * 합성 결과의 기기 사본 형식(S15P31A207-345 9회차, [AvatarLookDisk]). 바이트 그대로 적고 읽는다(그림을 풀거나 다시 묶지 않는다).
 * 맨 앞 표지와 형식 판([FORMAT])이 다르면 읽지 않는다([IOException]).
 *
 * - 톤([AvatarToneCache]): 맨몸 바탕, 의상 자리별 텍스처(키 포함), 정면 얼굴·헤어, 정면·측면 볼 덮개.
 * - 생성 머리([GeneratedHeadLayers]): 얼굴 피부색과 남녀 몸체별 머리 원본(정면 다섯 층, 측면 세 층, 체형 1·2 볼 덮개). 측면 목 층은
 *   피부색과 성별로 다시 만들고, 내장 샘플 목 등록값이 있는 머리(내장 머리)는 적지 않는다.
 */
internal object AvatarLookCodec {
    private const val TONE_MAGIC = 0x52544f4e
    private const val HEAD_MAGIC = 0x52484544

    /** 형식 판. 적는 내용이 바뀌면 올린다(옛 사본은 읽지 않고 다시 합성한다). */
    private const val FORMAT = 1

    fun writeTone(out: DataOutputStream, cache: AvatarToneCache) {
        out.writeInt(TONE_MAGIC)
        out.writeInt(FORMAT)
        writeBytesMap(out, cache.skinBases)
        out.writeInt(cache.skinRenders.size)
        cache.skinRenders.forEach { (name, skin) ->
            out.writeUTF(name)
            out.writeInt(skin.key.amount)
            out.writeInt(skin.key.top)
            out.writeInt(skin.key.bottom)
            out.writeInt(skin.key.shoes)
            out.writeInt(skin.key.shape)
            writeBytes(out, skin.bytes)
        }
        out.writeBoolean(cache.face != null)
        cache.face?.let { face ->
            out.writeInt(face.amount)
            writeBytes(out, face.bytes)
            writeNullable(out, face.hair)
        }
        writeOverlay(out, cache.frontOverlay)
        writeOverlay(out, cache.runOverlay)
    }

    fun readTone(input: DataInputStream): AvatarToneCache {
        expect(input, TONE_MAGIC)
        val bases = readBytesMap(input)
        val renders = (0 until count(input)).associate {
            val name = input.readUTF()
            val key = SkinRenderKey(input.readInt(), input.readInt(), input.readInt(), input.readInt(), input.readInt())
            name to AvatarToneCache.RenderedSkin(key, readBytes(input))
        }
        val face = if (input.readBoolean()) {
            AvatarToneCache.RenderedFace(input.readInt(), readBytes(input), readNullable(input))
        } else {
            null
        }
        return AvatarToneCache(bases, renders, face, readOverlay(input), readOverlay(input))
    }

    /** 내장 머리(목 등록값이 있는 측면)는 적지 못한다([IllegalArgumentException]). */
    fun writeHead(out: DataOutputStream, head: GeneratedHeadLayers) {
        require(head.variants.values.all { it.sideLayers.neckRegistration == null }) { "built-in head is not cached" }
        out.writeInt(HEAD_MAGIC)
        out.writeInt(FORMAT)
        out.writeInt(head.skinColor)
        out.writeInt(head.variants.size)
        head.variants.forEach { (gender, variant) ->
            out.writeUTF(gender.name)
            writeBytesMap(out, variant.front)
            writeBytes(out, variant.sideLayers.face)
            writeBytes(out, variant.sideLayers.eyes)
            writeBytes(out, variant.sideLayers.hair)
            out.writeBoolean(variant.sideLayers.neckUnderlay != null)
            writeIntMap(out, variant.frontFullness)
            writeIntMap(out, variant.sideFullness)
        }
    }

    fun readHead(input: DataInputStream, sourceKey: String): GeneratedHeadLayers {
        expect(input, HEAD_MAGIC)
        val skin = input.readInt()
        val variants = (0 until count(input)).associate {
            val gender = enumOf<AvatarGender>(input.readUTF())
            val front = readBytesMap(input)
            val side = SideHeadLayers(
                face = readBytes(input),
                eyes = readBytes(input),
                hair = readBytes(input),
                neckUnderlay = if (input.readBoolean()) NeckUnderlay(BodyNeck.side(gender), skin) else null
            )
            gender to AvatarVariantAssets(front, side, readIntMap(input), readIntMap(input))
        }
        return GeneratedHeadLayers(variants, skin, sourceKey)
    }

    private fun expect(input: DataInputStream, magic: Int) {
        if (input.readInt() != magic || input.readInt() != FORMAT) throw IOException("avatar look copy format mismatch")
    }

    private fun writeOverlay(out: DataOutputStream, overlay: AvatarToneCache.RenderedOverlay?) {
        out.writeBoolean(overlay != null)
        overlay ?: return
        out.writeUTF(overlay.shape.name)
        out.writeInt(overlay.amount)
        writeBytes(out, overlay.bytes)
    }

    private fun readOverlay(input: DataInputStream): AvatarToneCache.RenderedOverlay? {
        if (!input.readBoolean()) return null
        return AvatarToneCache.RenderedOverlay(enumOf(input.readUTF()), input.readInt(), readBytes(input))
    }

    private fun writeBytesMap(out: DataOutputStream, map: Map<String, ByteArray>) {
        out.writeInt(map.size)
        map.forEach { (name, bytes) ->
            out.writeUTF(name)
            writeBytes(out, bytes)
        }
    }

    private fun readBytesMap(input: DataInputStream): Map<String, ByteArray> =
        (0 until count(input)).associate { input.readUTF() to readBytes(input) }

    private fun writeIntMap(out: DataOutputStream, map: Map<Int, ByteArray>) {
        out.writeInt(map.size)
        map.forEach { (key, bytes) ->
            out.writeInt(key)
            writeBytes(out, bytes)
        }
    }

    private fun readIntMap(input: DataInputStream): Map<Int, ByteArray> =
        (0 until count(input)).associate { input.readInt() to readBytes(input) }

    private fun writeNullable(out: DataOutputStream, bytes: ByteArray?) {
        out.writeBoolean(bytes != null)
        bytes?.let { writeBytes(out, it) }
    }

    private fun readNullable(input: DataInputStream): ByteArray? = if (input.readBoolean()) readBytes(input) else null

    private fun writeBytes(out: DataOutputStream, bytes: ByteArray) {
        out.writeInt(bytes.size)
        out.write(bytes)
    }

    private fun readBytes(input: DataInputStream): ByteArray {
        val size = input.readInt()
        if (size !in 0..MAX_BYTES) throw IOException("avatar look copy is broken")
        return ByteArray(size).also(input::readFully)
    }

    private fun count(input: DataInputStream): Int =
        input.readInt().also { if (it !in 0..MAX_ENTRIES) throw IOException("avatar look copy is broken") }

    private inline fun <reified E : Enum<E>> enumOf(name: String): E =
        enumValues<E>().firstOrNull { it.name == name } ?: throw IOException("avatar look copy is broken")

    /** 그림 하나의 최대 바이트(1024 PNG 여유). 깨진 사본이 큰 배열을 만들지 않게 한다. */
    private const val MAX_BYTES = 32 * 1024 * 1024
    private const val MAX_ENTRIES = 256
}
