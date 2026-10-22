package com.a207.runkk.crew;

import com.a207.runkk.common.PersonalityTag;
import java.util.Set;

/**
 * 크루 생성 요청(자료사전 크루생성요청). CrewService.create가 CrewName·TagSet(CREW)으로 바꾼다.
 *
 * @param name 크루명
 * @param intro 한줄소개
 * @param tags 성향 태그
 * @param visibility 공개 여부
 * @param joinPolicy 가입 방식
 * @param logoUrl 로고 이미지 주소, 없을 수 있음
 */
public record CrewCreateCommand(
        String name,
        String intro,
        Set<PersonalityTag> tags,
        CrewVisibility visibility,
        JoinPolicy joinPolicy,
        String logoUrl) {}
