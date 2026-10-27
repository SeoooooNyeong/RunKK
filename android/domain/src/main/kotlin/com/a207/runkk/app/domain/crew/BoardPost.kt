package com.a207.runkk.app.domain.crew

/** 게시판 글(FR-211): 본문과 사진(최대 5장, D-113). 사진은 인증된 연결로 [Photo.url]을 읽는다(D-100). */
data class BoardPost(val body: String, val photos: List<Photo>) {
    data class Photo(val photoId: Long, val url: String)
}
