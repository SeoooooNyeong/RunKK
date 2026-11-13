package com.a207.runkk.app.domain.battle

/** 프로세스 메모리에만 두는 [PendingForfeitStore](시험용, 남은 그만두기를 들여다볼 수 있다). */
class InMemoryPendingForfeitStore : PendingForfeitStore {
    var pending: PendingForfeit? = null

    override fun load(): PendingForfeit? = pending

    override fun save(pending: PendingForfeit) {
        this.pending = pending
    }

    override fun clear() {
        pending = null
    }
}
