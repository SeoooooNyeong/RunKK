package com.a207.runkk.app.api.account

import kotlinx.serialization.Serializable

/** GET /legal/{type} 응답(서버 member.LegalDocument): 문서는 앱 밖 공개 주소(url)에 있다. body는 주소 대신 본문을 줄 때를 위한 여분이다. */
@Serializable
internal data class LegalDocumentResponse(val type: String? = null, val url: String? = null, val body: String? = null)
