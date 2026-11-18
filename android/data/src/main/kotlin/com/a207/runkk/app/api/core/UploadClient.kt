package com.a207.runkk.app.api.core

import javax.inject.Qualifier

/**
 * 업로드 전용 Retrofit(요청 본문 gzip, Jira 327). 기본 클라이언트에 GzipRequestInterceptor를 얹은 것이라 연결 풀·인증·토큰 갱신은 같다.
 * 서버 압축 해제 허용 목록(POST /api/v1/runs)에 있는 요청만 이것으로 만든다(api.run RunUploadApi).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UploadClient
