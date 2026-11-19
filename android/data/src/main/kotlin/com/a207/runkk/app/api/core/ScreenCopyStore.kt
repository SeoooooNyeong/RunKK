package com.a207.runkk.app.api.core

import java.io.File
import java.io.IOException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * 화면 첫 응답의 기기 사본(S15P31A207-345 2차). 시작 로딩 화면이 받아 두고, 화면은 이것을 먼저 그린 뒤 뒤에서 새로 받아 바꾼다.
 * 오래돼도(하루를 넘어도) 지우지 않고 준다(화면이 열 때마다 새로 받는다).
 *
 * - 칸: 개인 데이터(내 순위가 든 랭킹, 내 기록·크루·친구·요약)는 회원 칸(m{회원ID}), 계정과 상관없는 데이터는 공용 칸(shared)에 둔다.
 *   로그아웃·계정 바뀜에도 지우지 않는다. 다른 계정은 앞 계정 칸을 보지 못하고, 앞 계정으로 돌아오면 그 칸을 바로 준다.
 * - 사본 하나는 열쇠([key])마다 마지막 응답 하나와 그 응답을 부른 값([Copy.param], 예: 기준 날짜·기간)이다. 부른 값이 다르면 화면이
 *   쓰지 않는다(지난달 기록을 이번 달에 그리지 않게).
 * - 처음 읽은 것은 메모리에 두고 다음부터 메모리에서 준다. 읽기·쓰기 실패는 사본이 없는 것으로 본다(화면은 받아서 그린다).
 * 여러 스레드가 함께 써도 된다. 파일 읽기·쓰기라 주 스레드 밖(IO)에서 부른다.
 */
internal class ScreenCopyStore(private val root: File, private val json: Json) {
    /** 사본 하나: 부른 값과 응답 JSON. */
    data class Copy(val param: String, val body: String)

    private val lock = Any()
    private val memory = HashMap<String, Copy?>()

    /** [member] 칸(null이면 공용 칸)의 [key] 사본을 [serializer]로 푼다. 없거나 풀지 못하면 null. */
    fun <T> read(member: Long?, key: String, serializer: KSerializer<T>): Pair<String, T>? {
        val copy = copyOf(member, key) ?: return null
        return try {
            copy.param to json.decodeFromString(serializer, copy.body)
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /** [member] 칸(null이면 공용 칸)에 남긴 [key] 사본의 부른 값. 사본이 없으면 null. */
    fun paramOf(member: Long?, key: String): String? = copyOf(member, key)?.param

    /** [member] 칸(null이면 공용 칸)의 [key] 사본을 [value]로 바꾼다. */
    fun <T> write(member: Long?, key: String, param: String, serializer: KSerializer<T>, value: T) {
        val copy = Copy(param, json.encodeToString(serializer, value))
        val file = fileOf(member, key)
        synchronized(lock) {
            try {
                file.parentFile?.mkdirs()
                val temp = File(file.parentFile, file.name + ".tmp")
                temp.writeText(copy.param + "\n" + copy.body)
                if (!temp.renameTo(file)) {
                    file.delete()
                    if (!temp.renameTo(file)) throw IOException("rename failed: ${file.name}")
                }
                memory[file.path] = copy
            } catch (e: IOException) {
                // 남기지 못하면 화면이 받아서 그린다.
            }
        }
    }

    private fun copyOf(member: Long?, key: String): Copy? {
        val file = fileOf(member, key)
        synchronized(lock) {
            if (memory.containsKey(file.path)) return memory[file.path]
            val copy = try {
                file.takeIf { it.isFile }?.readText()?.let { text ->
                    val split = text.indexOf('\n')
                    if (split < 0) null else Copy(text.substring(0, split), text.substring(split + 1))
                }
            } catch (e: IOException) {
                null
            }
            memory[file.path] = copy
            return copy
        }
    }

    private fun fileOf(member: Long?, key: String): File =
        File(File(root, member?.let { MEMBER_PREFIX + it } ?: SHARED), key.replace(UNSAFE, "_") + SUFFIX)

    private companion object {
        const val SHARED = "shared"
        const val MEMBER_PREFIX = "m"
        const val SUFFIX = ".json"
        val UNSAFE = Regex("[^A-Za-z0-9._-]")
    }
}
