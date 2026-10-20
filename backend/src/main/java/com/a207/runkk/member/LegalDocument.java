package com.a207.runkk.member;

/**
 * 법정 고지 문서(자료사전 고지문서 = 문서종류 + 문서주소, FR-018).
 *
 * @param type 문서 종류
 * @param url 앱 밖 공개 주소
 */
public record LegalDocument(DocumentType type, String url) {}
