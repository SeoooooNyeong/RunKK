package com.a207.runkk.member;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * 소셜 제공자 계정 하나(설계 6.1.2). (provider, providerAccountId)가 고유하다. equals·hashCode는 두 값으로 정한다.
 *
 * @param provider 제공자
 * @param providerAccountId 제공자 계정 ID, 비지 않음
 */
@Embeddable
public record SocialAccount(
        @Enumerated(EnumType.STRING) @Column(name = "provider", length = 10) ProviderType provider,
        @Column(name = "provider_account_id", length = 100) String providerAccountId) {

    public SocialAccount {
        if (provider == null || providerAccountId == null || providerAccountId.isBlank()) {
            throw new IllegalArgumentException("제공자 계정이 비었다");
        }
    }
}
