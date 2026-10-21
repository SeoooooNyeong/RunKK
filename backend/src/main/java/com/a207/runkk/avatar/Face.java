package com.a207.runkk.avatar;

/** 아바타 얼굴 구성의 계약(설계 6.1.8, 6.4). 그리는 쪽은 종류를 묻지 않고 renderKey만 쓴다. */
public interface Face {

    /** 앱이 얼굴 이미지를 고르는 키. */
    String renderKey();
}
