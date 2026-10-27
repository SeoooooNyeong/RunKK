package com.a207.runkk.app.ui.me

object SignUpConsents {
    val items: List<ConsentLine> = listOf(
        ConsentLine("AGE_14_PLUS", R.string.me_signup_consent_age, isRequired = true),
        ConsentLine("TERMS", R.string.me_legal_terms, isRequired = true, legalType = LegalTypes.TERMS),
        ConsentLine(
            "PRIVACY",
            R.string.me_signup_consent_privacy,
            isRequired = true,
            legalType = LegalTypes.PRIVACY_POLICY
        ),
        ConsentLine(
            "LOCATION",
            R.string.me_signup_consent_location,
            isRequired = true,
            legalType = LegalTypes.LOCATION_TERMS
        ),
        ConsentLine("MARKETING", R.string.me_signup_consent_marketing, isRequired = false),
        ConsentLine(
            "LLM_TRANSFER",
            R.string.me_signup_consent_llm,
            isRequired = false,
            legalType = LegalTypes.PRIVACY_POLICY
        )
    )
}
