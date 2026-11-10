package com.a207.runkk.app.api.avatar

import android.content.Context
import com.a207.runkk.app.api.core.SelfMember
import com.a207.runkk.app.domain.avatar.PendingSelfiePhoto
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 보낸 사진 한 장을 앱 전용 파일 `files/selfie/pending-{회원ID}.jpg`에 둔다(design-oo 05-30 [임시:D-성-119]). 이미 줄인 JPEG을 그대로
 * 쓰고 다시 줄이지 않는다. 앱은 백업을 끈다(매니페스트 allowBackup=false)라 기기 밖으로 나가지 않는다. 로그인한 회원 것만 읽고, 새로
 * 남기면 다른 회원이 남긴 사진은 지운다(로그아웃 뒤 다음 회원이 앞 회원 사진을 보지 않게). 파일을 읽고 쓰지 못하면 없는 것으로 본다.
 */
@Singleton
internal class FilePendingSelfiePhoto
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val self: SelfMember
) : PendingSelfiePhoto {
    private val dir: File get() = File(context.filesDir, DIR)

    override suspend fun save(jpeg: ByteArray) {
        val member = self.memberId() ?: return
        withContext(Dispatchers.IO) {
            try {
                dir.mkdirs()
                dir.listFiles()?.forEach { it.delete() }
                val temp = File(dir, "$PREFIX$member$TEMP")
                temp.writeBytes(jpeg)
                if (!temp.renameTo(fileOf(member))) temp.delete()
            } catch (e: IOException) {
                fileOf(member).delete()
            }
        }
    }

    override suspend fun load(): ByteArray? = withContext(Dispatchers.IO) {
        val member = self.memberId() ?: return@withContext null
        val file = fileOf(member)
        try {
            if (file.isFile) file.readBytes() else null
        } catch (e: IOException) {
            null
        }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) { dir.listFiles()?.forEach { it.delete() } }
    }

    private fun fileOf(member: Long): File = File(dir, "$PREFIX$member$JPG")

    private companion object {
        const val DIR = "selfie"
        const val PREFIX = "pending-"
        const val JPG = ".jpg"
        const val TEMP = ".tmp"
    }
}
