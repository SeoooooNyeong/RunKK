package com.a207.runkk.crew;

import com.a207.runkk.common.TagSet;

/**
 * 크루 내 표시 정보 변경(자료사전 크루내정보변경요청, FR-144).
 *
 * @param intro 크루 내 한줄 소개, 40자 이하
 * @param tags 성향 태그(TagOwner.RUNNER), null이면 그대로
 */
public record CrewProfileEdit(String intro, TagSet tags) {}
