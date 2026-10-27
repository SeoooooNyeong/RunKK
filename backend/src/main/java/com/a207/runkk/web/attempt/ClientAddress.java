package com.a207.runkk.web.attempt;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 요청을 보낸 접속지(시도 제한 키). nginx가 덮어써 넣는 X-Real-IP는 요청이 같은 호스트·사설망(nginx 컨테이너)에서 왔을 때만 믿는다. 밖에서 바로 온
 * 요청이 헤더를 바꿔 제한을 피하지 못하게 한다(관리툴 로그인 자가 리뷰 MEDIUM, 코드 리뷰 2차 M5). 불변 값이다.
 *
 * @param value 접속지 IP 문자열
 */
public record ClientAddress(String value) {

    private static final Pattern IP_LITERAL = Pattern.compile("[0-9a-fA-F.:]{2,45}");

    public ClientAddress {
        Objects.requireNonNull(value, "value");
    }

    /** 원격 주소와 X-Real-IP 헤더 값(없으면 null)으로 접속지를 정한다. */
    public static ClientAddress resolve(String remoteAddr, String realIpHeader) {
        String remote = remoteAddr == null ? "" : remoteAddr;
        if (realIpHeader == null || realIpHeader.isBlank() || !fromProxy(remote)) {
            return new ClientAddress(remote);
        }
        String header = realIpHeader.strip();
        // 프록시 뒤라도 IP 글자가 아닌 값(긴 문자열·쓰레기 값)은 키로 쓰지 않는다(보안 리뷰 MEDIUM)
        return new ClientAddress(IP_LITERAL.matcher(header).matches() ? header : remote);
    }

    private static boolean fromProxy(String remote) {
        if (!IP_LITERAL.matcher(remote).matches()) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(remote); // IP 글자라 이름 조회 없음
            return address.isLoopbackAddress() || address.isSiteLocalAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
