package com.a207.runkk.app.api.core

/** 동의·코치 요청이 시작된 계정의 토큰만 쓰도록 고정한다. */
internal class BoundSession private constructor(val generation: Long, val accessToken: String) {
    fun isCurrent(tokens: TokenProvider): Boolean = tokens.sessionGeneration() == generation

    companion object {
        fun capture(tokens: TokenProvider): BoundSession? {
            val generation = tokens.sessionGeneration()
            val access = tokens.accessToken() ?: return null
            return BoundSession(generation, access).takeIf { it.isCurrent(tokens) }
        }
    }
}
