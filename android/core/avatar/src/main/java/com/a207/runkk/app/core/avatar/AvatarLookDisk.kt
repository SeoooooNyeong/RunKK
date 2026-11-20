package com.a207.runkk.app.core.avatar

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * 아바타 합성 결과의 기기 칸 하나(S15P31A207-345 9회차): files/avatar-look/m{회원}(내 머리·홈 아바타 톤) 또는 files/avatar-look/shared(다른
 * 러너 머리·톤, 시상대 전신 정지 그림). 앱을 다시 켜도 같은 입력이면 다시 합성하지 않고 이것을 읽는다.
 *
 * - 열쇠: 합성 입력의 해시. 머리는 머리 키·층·메타 바이트, 톤은 성별·동작·의상·체형·러닝량·머리 열쇠, 정지 그림은 모습·칸 크기다. 모두 앱 판
 *   표지([stamp], 설치·갱신 시각과 판 번호)를 넣어 앱을 갱신하면(그림·합성 규칙이 바뀔 수 있다) 다시 합성한다.
 * - 교체: 묶음(머리, 성별×동작별 톤, 정지 그림)마다 최근 [keep]개를 둔다. 새 사본을 다 쓴 뒤에만 그 묶음의 오래된 사본을 지운다(회원 칸은
 *   하나씩이라 입력이 바뀌면 새 것으로 바꾼다). 로그아웃·계정 바뀜에는 지우지 않는다(계정마다 칸이 따로다).
 * - 읽기·쓰기 실패는 사본이 없는 것으로 본다(합성한다). 깨진 사본은 지운다. 파일을 읽고 쓰므로 주 스레드에서 부르지 않는다.
 */
class AvatarLookDisk internal constructor(internal val dir: File, private val stamp: String, private val keep: Int) {
    /** 생성 머리 사본 열쇠: 머리 키·메타·층 바이트·앱 판. */
    internal fun headKey(faceKey: String, meta: String, layers: List<ByteArray>): String = digest { md ->
        md.update(text("head|$stamp|$faceKey|$meta"))
        layers.forEach { md.update(it) }
    }

    /** 톤 사본 열쇠. 머리가 기기 사본으로 가를 수 없는 생성 머리면 null(남기지 않는다). */
    internal fun toneKey(
        gender: AvatarGender,
        motion: AvatarMotion,
        look: AvatarLookKey,
        head: GeneratedHeadLayers?
    ): String? {
        val headKey = if (head == null) BUILT_IN else head.sourceKey ?: return null
        val outfit = look.outfit
        return digest { md ->
            md.update(
                text(
                    "tone|$stamp|$gender|$motion|${outfit.top}.${outfit.bottom}.${outfit.shoes}|" +
                        "${look.shape}|${look.amount}|$headKey"
                )
            )
        }
    }

    /** 전신 정지 그림의 모습 열쇠(칸 크기 뺌). 머리를 가를 수 없으면 null. */
    internal fun stillKey(look: RiveAvatarLook): String? {
        val head = look.headLayers
        val headKey = if (head == null) BUILT_IN else head.sourceKey ?: return null
        val outfit = look.outfit
        val amount = look.runningAmount.coerceIn(MIN_RUNNING_AMOUNT, MAX_RUNNING_AMOUNT)
        return digest { md ->
            md.update(
                text(
                    "still|$stamp|${look.gender}|${outfit.top}.${outfit.bottom}.${outfit.shoes}|" +
                        "${look.bodyShape}|$amount|$headKey"
                )
            )
        }
    }

    internal fun readHead(key: String): GeneratedHeadLayers? = read(fileOf(HEAD, key)) {
        AvatarLookCodec.readHead(it, key)
    }

    internal fun writeHead(key: String, head: GeneratedHeadLayers) =
        write(HEAD, fileOf(HEAD, key)) { AvatarLookCodec.writeHead(it, head) }

    internal fun readTone(key: String, gender: AvatarGender, motion: AvatarMotion): AvatarToneCache? =
        read(fileOf(toneGroup(gender, motion), key), AvatarLookCodec::readTone)

    internal fun writeTone(key: String, gender: AvatarGender, motion: AvatarMotion, cache: AvatarToneCache) =
        write(toneGroup(gender, motion), fileOf(toneGroup(gender, motion), key)) {
            AvatarLookCodec.writeTone(it, cache)
        }

    /** [lookKey] 모습으로 남긴 정지 그림들(칸 너비·높이 px, 그림). */
    internal fun readStills(lookKey: String): List<Triple<Int, Int, Bitmap>> {
        val prefix = "$STILL-$lookKey-"
        val files =
            synchronized(LOCK) { dir.listFiles()?.filter { it.name.startsWith(prefix) && it.name.endsWith(PNG) } }
        return files.orEmpty().mapNotNull { file ->
            val size = file.name.removePrefix(prefix).removeSuffix(PNG).split('x')
            val width = size.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            val height = size.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
            val bitmap = try {
                BitmapFactory.decodeFile(file.path)
            } catch (e: OutOfMemoryError) {
                null
            } ?: return@mapNotNull null
            file.setLastModified(System.currentTimeMillis())
            Triple(width, height, bitmap)
        }
    }

    internal fun writeStill(lookKey: String, width: Int, height: Int, still: Bitmap) {
        val file = File(dir, "$STILL-$lookKey-${width}x$height$PNG")
        synchronized(LOCK) {
            try {
                dir.mkdirs()
                val temp = File(dir, file.name + TEMP)
                temp.outputStream().use { check(still.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it)) }
                replace(temp, file)
                prune(STILL, file)
            } catch (e: IOException) {
                // 남기지 못하면 다음에 다시 그린다.
            } catch (e: IllegalStateException) {
                // 그림을 묶지 못했다.
            }
        }
    }

    private fun <T> read(file: File, decode: (DataInputStream) -> T): T? = synchronized(LOCK) {
        if (!file.isFile) return null
        try {
            DataInputStream(BufferedInputStream(file.inputStream())).use(decode).also {
                file.setLastModified(System.currentTimeMillis())
            }
        } catch (e: IOException) {
            file.delete()
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    private fun write(group: String, file: File, encode: (DataOutputStream) -> Unit) = synchronized(LOCK) {
        try {
            dir.mkdirs()
            val temp = File(dir, file.name + TEMP)
            DataOutputStream(BufferedOutputStream(temp.outputStream())).use(encode)
            replace(temp, file)
            // 새 사본을 다 쓴 뒤에만 같은 묶음의 옛 사본을 지운다.
            prune(group, file)
        } catch (e: IOException) {
            // 남기지 못하면 다음에 다시 합성한다.
        } catch (e: IllegalArgumentException) {
            // 남길 수 없는 머리(내장 머리)다.
        }
    }

    private fun replace(temp: File, file: File) {
        if (temp.renameTo(file)) return
        file.delete()
        if (!temp.renameTo(file)) {
            temp.delete()
            throw IOException("rename failed: ${file.name}")
        }
    }

    /** [group] 묶음에서 [kept]와 최근 것까지 [keep]개만 남긴다. */
    private fun prune(group: String, kept: File) {
        val prefix = "$group-"
        dir.listFiles()
            ?.filter { it.name.startsWith(prefix) && it.name != kept.name && !it.name.endsWith(TEMP) }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(keep - 1)
            ?.forEach { it.delete() }
    }

    private fun fileOf(group: String, key: String) = File(dir, "$group-$key$BIN")

    private fun toneGroup(gender: AvatarGender, motion: AvatarMotion) = "$TONE-$gender-$motion"

    companion object {
        /** files 아래 칸들의 뿌리. */
        const val ROOT = "avatar-look"
        private const val SHARED = "shared"
        private const val MEMBER_PREFIX = "m"
        private const val HEAD = "head"
        private const val TONE = "tone"
        private const val STILL = "still"
        private const val BUILT_IN = "built-in"
        private const val BIN = ".bin"
        private const val PNG = ".png"
        private const val TEMP = ".tmp"
        private const val PNG_QUALITY = 100
        private const val KEY_LENGTH = 32

        /** 회원 칸에 묶음마다 둘 사본 수(입력이 바뀌면 바꾼다). */
        private const val MEMBER_KEEP = 1

        /** 공용 칸에 묶음마다 둘 사본 수(시상대 셋과 대결 상대 몇 명). */
        private const val SHARED_KEEP = 12

        private val LOCK = Any()

        @Volatile
        private var appStamp: String? = null

        /** [memberId] 회원 칸(내 머리·홈 아바타 톤). */
        fun member(context: Context, memberId: Long): AvatarLookDisk =
            AvatarLookDisk(File(File(context.filesDir, ROOT), MEMBER_PREFIX + memberId), stampOf(context), MEMBER_KEEP)

        /** 공용 칸(다른 러너 머리·톤, 전신 정지 그림). */
        fun shared(context: Context): AvatarLookDisk =
            AvatarLookDisk(File(File(context.filesDir, ROOT), SHARED), stampOf(context), SHARED_KEEP)

        /** 시험용: [dir] 칸, 앱 판 표지 [stamp], 묶음마다 [keep]개. */
        internal fun of(dir: File, stamp: String, keep: Int) = AvatarLookDisk(dir, stamp, keep)

        private fun stampOf(context: Context): String = appStamp ?: run {
            val stamp = try {
                val info = context.packageManager.getPackageInfo(context.packageName, 0)

                @Suppress("DEPRECATION")
                val code = if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.P
                ) {
                    info.longVersionCode
                } else {
                    info.versionCode.toLong()
                }
                "$code.${info.lastUpdateTime}"
            } catch (e: PackageManager.NameNotFoundException) {
                "unknown"
            }
            stamp.also { appStamp = it }
        }

        private fun text(value: String) = value.toByteArray(Charsets.UTF_8)

        private inline fun digest(block: (MessageDigest) -> Unit): String {
            val md = MessageDigest.getInstance("SHA-256")
            block(md)
            return md.digest().joinToString("") { "%02x".format(it) }.take(KEY_LENGTH)
        }
    }
}
