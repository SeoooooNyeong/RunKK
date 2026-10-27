package com.a207.runkk.watch.link

import kotlinx.coroutines.flow.Flow

/** 받은 메시지 한 건(경로와 본문). */
class WearMessage(val path: String, val data: ByteArray)
