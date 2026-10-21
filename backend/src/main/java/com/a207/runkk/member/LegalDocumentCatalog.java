package com.a207.runkk.member;

import java.util.EnumMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 법정 고지 문서 주소 목록(설계 6.1.2 MemberSettingService 의존 "LegalDocumentCatalog(설정 파일)"). 문서는 앱 밖 공개 주소에
 * 둔다(FR-018, plan.md R-016). 주소는 runkk.legal.* 설정 값이다.
 */
@Component
public class LegalDocumentCatalog {

    private final Map<DocumentType, String> urls = new EnumMap<>(DocumentType.class);

    @Autowired
    public LegalDocumentCatalog(Links links) {
        this(links.termsUrl(), links.privacyPolicyUrl(), links.locationTermsUrl(), links.helpUrl());
    }

    LegalDocumentCatalog(
            String termsUrl, String privacyPolicyUrl, String locationTermsUrl, String helpUrl) {
        urls.put(DocumentType.TERMS, required(termsUrl, "terms-url"));
        urls.put(DocumentType.PRIVACY_POLICY, required(privacyPolicyUrl, "privacy-policy-url"));
        urls.put(DocumentType.LOCATION_TERMS, required(locationTermsUrl, "location-terms-url"));
        urls.put(DocumentType.HELP, required(helpUrl, "help-url"));
    }

    /**
     * 빈 주소로 뜨면 앱이 null 문서 주소를 받는다. @Value가 하던 대로 설정 누락은 기동 실패로 드러낸다(코드 리뷰 3차 B-L7). 값은 설정 키 이름만
     * 알린다.
     */
    private static String required(String url, String key) {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("runkk.legal." + key + " 설정이 비었다");
        }
        return url;
    }

    public LegalDocument find(DocumentType type) {
        return new LegalDocument(type, urls.get(type));
    }

    /**
     * 법정 고지 문서 공개 주소(runkk.legal.*, FR-018, 코드 리뷰 2차 L1).
     *
     * @param termsUrl 이용약관
     * @param privacyPolicyUrl 개인정보 처리방침
     * @param locationTermsUrl 위치정보 이용약관
     * @param helpUrl 도움말
     */
    @ConfigurationProperties("runkk.legal")
    public record Links(
            String termsUrl, String privacyPolicyUrl, String locationTermsUrl, String helpUrl) {}
}
