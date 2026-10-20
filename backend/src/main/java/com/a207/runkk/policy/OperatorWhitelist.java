package com.a207.runkk.policy;

import java.util.Map;
import java.util.Set;

/**
 * 운영자 계정 목록(설계 6.1.9, FR-173). 제공자 이름("KAKAO"·"GOOGLE")마다 계정 ID 묶음이다. policy가 member.SocialAccount를
 * import하면 12.2 의존 목록(policy → audit·common)을 벗어나므로 문자열로 갖는다.
 *
 * @param accounts 제공자 → 계정 ID
 */
public record OperatorWhitelist(Map<String, Set<String>> accounts) {

    public OperatorWhitelist {
        accounts = Map.copyOf(accounts);
    }

    public boolean contains(String provider, String accountId) {
        return accounts.getOrDefault(provider, Set.of()).contains(accountId);
    }
}
