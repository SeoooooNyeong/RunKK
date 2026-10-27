package com.a207.runkk.member;

/**
 * 소셜 계정 연동 결과(설계 6.1.2, FR-198). web.auth가 SocialLinkResponse로 바꾼다.
 *
 * @param accountType 연동 뒤 계정 유형(LINKED)
 * @param provider 연동한 제공자
 */
public record SocialLinkResult(AccountType accountType, ProviderType provider) {}
