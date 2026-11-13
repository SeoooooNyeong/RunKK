package com.a207.runkk.app.core.avatar

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import timber.log.Timber

/**
 * 서기 riv 바이트 패치(2026-11-12 PM 요구: 움직일 때 허리 뾰족함을 네모로, 체형 2·3단계를 시안 실루엣에 맞춤). 승인 riv 파일은 그대로 두고
 * 앱이 읽은 바이트에 패치 파일(res/raw `avatar_*_idle_patch.bin`)을 덮는다([RivSleeveSeam]과 같은 원칙, 바꿀 자리가 수천 개라 표 대신 파일).
 * 패치는 파이프라인 `tools/outfit-pipeline`의 `riv-patch` 명령이 만든다: 허리 옆 꼭짓점 위치·팔 무게, 오른팔 뼈에 잘못 묶인 엉덩이 꼭짓점,
 * 체형 애니메이션(medium·fuller) x 키.
 *
 * 형식(빅엔디언): `RKP1`, int32 riv 길이, 고칠 자리 바꾸기 전 바이트를 이은 SHA-256(32), 바꾼 뒤 SHA-256(32), int32 개수, 항목마다
 * (int32 자리, uint8 길이, 바이트). 길이와 바꾸기 전 지문이 맞을 때만 덮는다. 바꾼 뒤 지문이 맞으면 이미 덮은 것으로 보고 그대로 둔다.
 * 다르면(다시 내보낸 riv) 고치지 않고 경고를 한 번 남긴다. [PreparedRiveAssets.hideBuiltInShapes] 뒤 바이트를 받는다.
 */
internal object RivBytePatch {
    enum class Outcome { APPLIED, ALREADY_APPLIED, MISMATCH }

    private val MAGIC = "RKP1".toByteArray()
    private const val SHA_BYTES = 32
    private const val EDIT_HEADER_BYTES = 5
    private val warned = AtomicBoolean(false)

    /** [gender] 서기 패치의 res/raw 이름(확장자 없음). */
    fun resourceName(gender: AvatarGender): String =
        if (gender == AvatarGender.MALE) "avatar_male_idle_patch" else "avatar_female_idle_patch"

    /** [gender]·[motion] riv에 덮을 패치 res/raw. 달리기에는 패치가 없다. */
    fun resource(gender: AvatarGender, motion: AvatarMotion): Int? = when {
        motion == AvatarMotion.RUN -> null
        gender == AvatarGender.MALE -> R.raw.avatar_male_idle_patch
        else -> R.raw.avatar_female_idle_patch
    }

    private class Edit(val offset: Int, val bytes: ByteArray)

    private class Patch(val size: Int, val before: ByteArray, val after: ByteArray, val edits: List<Edit>)

    fun applyInPlace(riv: ByteArray, patch: ByteArray): Outcome {
        val parsed = parse(patch)
        // 자리 + 길이가 Int를 넘지 않게 뺄셈으로 견준다
        if (parsed == null || parsed.size != riv.size || parsed.edits.any { it.offset > riv.size - it.bytes.size }) {
            return mismatch()
        }
        val current = digest(parsed.edits.map { riv.copyOfRange(it.offset, it.offset + it.bytes.size) })
        return when {
            current.contentEquals(parsed.after) -> Outcome.ALREADY_APPLIED

            !current.contentEquals(parsed.before) -> mismatch()

            else -> {
                parsed.edits.forEach { it.bytes.copyInto(riv, it.offset) }
                Outcome.APPLIED
            }
        }
    }

    private fun mismatch(): Outcome {
        if (warned.compareAndSet(false, true)) {
            Timber.w("stand riv byte patch fingerprint differs from the approved original, not patched")
        }
        return Outcome.MISMATCH
    }

    private fun parse(patch: ByteArray): Patch? = runCatching {
        val buffer = ByteBuffer.wrap(patch)
        val magic = ByteArray(MAGIC.size).also { buffer.get(it) }
        require(magic.contentEquals(MAGIC))
        val size = buffer.int
        val before = ByteArray(SHA_BYTES).also { buffer.get(it) }
        val after = ByteArray(SHA_BYTES).also { buffer.get(it) }
        val count = buffer.int
        require(count in 0..buffer.remaining() / EDIT_HEADER_BYTES) // 항목마다 머리 5바이트 이상
        val edits = List(count) {
            val offset = buffer.int
            val bytes = ByteArray(buffer.get().toInt() and 0xff).also { buffer.get(it) }
            require(offset >= 0)
            Edit(offset, bytes)
        }
        require(!buffer.hasRemaining()) // 뒤에 남는 바이트가 있으면 잘못 만든 파일
        Patch(size, before, after, edits)
    }.getOrNull()

    private fun digest(parts: List<ByteArray>): ByteArray =
        MessageDigest.getInstance("SHA-256").apply { parts.forEach { update(it) } }.digest()
}
