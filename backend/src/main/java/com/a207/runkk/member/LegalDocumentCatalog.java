package com.a207.runkk.member;

import java.util.EnumMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 법정 고지 문서 주소 목록(설계 6.1.2 MemberSettingService 의존 "LegalDocumentCatalog(설정 파일)"). 문서는 앱 밖 공개 주소에
 * 둔다(FR-018, plan.md R-016). 주소는 runkk.legal.* 설정 값이다.
 */
@Component
public class LegalDocumentCatalog {

    private final Map<DocumentType, String> urls = new EnumMap<>(DocumentType.class);

    public LegalDocumentCatalog(
            @Value("${runkk.legal.terms-url}") String termsUrl,
            @Value("${runkk.legal.privacy-policy-url}") String privacyPolicyUrl,
            @Value("${runkk.legal.location-terms-url}") String locationTermsUrl,
            @Value("${runkk.legal.help-url}") String helpUrl) {
        urls.put(DocumentType.TERMS, termsUrl);
        urls.put(DocumentType.PRIVACY_POLICY, privacyPolicyUrl);
        urls.put(DocumentType.LOCATION_TERMS, locationTermsUrl);
        urls.put(DocumentType.HELP, helpUrl);
    }

    public LegalDocument find(DocumentType type) {
        return new LegalDocument(type, urls.get(type));
    }
}
