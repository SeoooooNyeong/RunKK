package com.a207.runkk.crew;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * JPEG 바이트에서 메타데이터 세그먼트를 허용 목록 방식으로 지운다(설계 6.1.7, NFR-042, D-142 보강, TC-6211). 남기는 것: SOI, APP0
 * (식별자 JFIF), APP2(식별자 ICC_PROFILE), APP14(식별자 Adobe), DQT, SOF*, DHT, DAC, DRI, DNL, SOS와 그 뒤 영상
 * 자료, EOI. 그 밖의 APPn(APP1 EXIF·XMP, APP13 IPTC, MPF 등)·COM·모르는 마커는 뺀다. 프로그레시브 JPEG는 스캔이 여럿이라 SOS 뒤
 * 영상 자료가 끝나는 마커에서 다시 세그먼트를 읽고, 스캔 사이의 APPn·COM도 뺀다. EOI 뒤에 덧붙인 바이트는 버린다. 마커·길이가 깨졌거나 SOS 없이 끝나면
 * UNSUPPORTED_IMAGE. 외부 라이브러리를 쓰지 않는 상태 없는 순수 함수이고 앱 PhotoCompressor 재인코딩과 함께 이중 방어다.
 */
final class JpegMetadataStripper {

    private static final int MARKER = 0xFF;
    private static final int STUFFED = 0x00;
    private static final int SOI = 0xD8;
    private static final int SOS = 0xDA;
    private static final int EOI = 0xD9;
    private static final int APP0 = 0xE0;
    private static final int APP2 = 0xE2;
    private static final int APP14 = 0xEE;
    private static final int DQT = 0xDB;
    private static final int DNL = 0xDC;
    private static final int DRI = 0xDD;
    private static final int SOF0 = 0xC0;
    private static final int SOF15 = 0xCF;
    private static final int DHT = 0xC4;
    private static final int JPG = 0xC8;
    private static final int DAC = 0xCC;
    private static final int TEM = 0x01;
    private static final int RST0 = 0xD0;
    private static final int RST7 = 0xD7;

    private static final byte[] JFIF = identifier("JFIF\0");
    private static final byte[] ICC_PROFILE = identifier("ICC_PROFILE\0");
    private static final byte[] ADOBE = identifier("Adobe");

    private JpegMetadataStripper() {}

    /** 읽는 중인 위치와 출력. 한 마커씩 step이 갱신한다. */
    private static final class Cursor {
        private final ByteArrayOutputStream out;
        private int pos = 2;
        private boolean scanned;
        private boolean finished;

        private Cursor(int capacity) {
            this.out = new ByteArrayOutputStream(capacity);
        }
    }

    static byte[] strip(byte[] jpeg) {
        if (jpeg.length < 2 || u8(jpeg, 0) != MARKER || u8(jpeg, 1) != SOI) {
            throw unsupported();
        }
        Cursor cursor = new Cursor(jpeg.length);
        cursor.out.write(jpeg, 0, 2);
        while (!cursor.finished && cursor.pos < jpeg.length) {
            step(jpeg, cursor);
        }
        if (!cursor.scanned) {
            throw unsupported(); // SOS 없이 끝남 또는 SOS 전 EOI(TF-BE-44: 깨진 파일로 본다)
        }
        return cursor.out.toByteArray(); // EOI 없이 영상 자료로 끝나면 받은 데까지 잇는다
    }

    /** 마커 하나를 읽고 cursor를 옮긴다. EOI를 쓰면 finished(EOI 뒤 덧붙인 바이트는 버린다). */
    private static void step(byte[] jpeg, Cursor cursor) {
        int pos = cursor.pos;
        if (u8(jpeg, pos) != MARKER || pos + 1 >= jpeg.length) {
            throw unsupported();
        }
        int marker = u8(jpeg, pos + 1);
        if (marker == MARKER) { // 채움 바이트(FF FF …)는 건너뛴다
            cursor.pos = pos + 1;
        } else if (marker == EOI) {
            cursor.out.write(jpeg, pos, 2);
            cursor.finished = true;
        } else if (isStandalone(marker)) {
            cursor.out.write(jpeg, pos, 2);
            cursor.pos = pos + 2;
        } else {
            copySegment(jpeg, cursor, marker);
        }
    }

    /** 길이 없는 단독 마커: TEM, RST0~RST7. */
    private static boolean isStandalone(int marker) {
        return marker == TEM || (marker >= RST0 && marker <= RST7);
    }

    /** 길이 있는 세그먼트 하나를 허용 목록에 따라 옮기고, SOS면 뒤 영상 자료를 그대로 잇는다. */
    private static void copySegment(byte[] jpeg, Cursor cursor, int marker) {
        int pos = cursor.pos;
        int end = segmentEnd(jpeg, pos);
        if (keeps(jpeg, pos, marker)) {
            cursor.out.write(jpeg, pos, end - pos);
        }
        cursor.pos = end;
        if (marker == SOS) {
            int dataEnd = scanDataEnd(jpeg, end);
            cursor.scanned = true;
            cursor.out.write(jpeg, end, dataEnd - end);
            cursor.pos = dataEnd;
        }
    }

    /** pos의 길이 있는 세그먼트가 끝나는 위치. 길이가 깨졌으면 UNSUPPORTED_IMAGE. */
    private static int segmentEnd(byte[] jpeg, int pos) {
        if (pos + 3 >= jpeg.length) {
            throw unsupported();
        }
        int length = (u8(jpeg, pos + 2) << 8) | u8(jpeg, pos + 3);
        if (length < 2 || pos + 2 + length > jpeg.length) {
            throw unsupported();
        }
        return pos + 2 + length;
    }

    /** 허용 목록(TC-6211). APP0·APP2·APP14는 식별자까지 맞아야 남긴다. */
    private static boolean keeps(byte[] jpeg, int pos, int marker) {
        return switch (marker) {
            case APP0 -> hasIdentifier(jpeg, pos, JFIF);
            case APP2 -> hasIdentifier(jpeg, pos, ICC_PROFILE);
            case APP14 -> hasIdentifier(jpeg, pos, ADOBE);
            case SOS, DQT, DRI, DNL, DHT, DAC -> true;
            default -> isFrameHeader(marker);
        };
    }

    /** SOF0~SOF15. 같은 범위의 DHT(C4)·JPG(C8, 예약)·DAC(CC)는 프레임 머리가 아니다. */
    private static boolean isFrameHeader(int marker) {
        return marker >= SOF0 && marker <= SOF15 && marker != DHT && marker != JPG && marker != DAC;
    }

    /** 세그먼트 내용(길이 칸 뒤)이 identifier로 시작하는가. 길이는 segmentEnd가 이미 확인했다. */
    private static boolean hasIdentifier(byte[] jpeg, int pos, byte[] identifier) {
        int length = (u8(jpeg, pos + 2) << 8) | u8(jpeg, pos + 3);
        if (length - 2 < identifier.length) {
            return false;
        }
        int start = pos + 4;
        return Arrays.equals(
                jpeg, start, start + identifier.length, identifier, 0, identifier.length);
    }

    /**
     * pos부터 영상 자료가 끝나는 위치(다음 마커의 FF). 영상 자료 안의 FF는 FF 00(채움 0)·RST로만 오고, 마커 앞의 채움 FF(FF FF …)는 영상
     * 자료와 함께 바이트 그대로 잇는다. 마커가 없으면 파일 끝.
     */
    private static int scanDataEnd(byte[] jpeg, int pos) {
        int i = pos;
        boolean found = false;
        while (!found && i + 1 < jpeg.length) {
            int skip = dataSkip(jpeg, i);
            found = skip == 0;
            i += skip;
        }
        return found ? i : jpeg.length;
    }

    /** 영상 자료의 i에서 건너뛸 바이트 수. 0이면 i가 다음 마커의 FF다. */
    private static int dataSkip(byte[] jpeg, int i) {
        int skip = 1;
        if (u8(jpeg, i) == MARKER) {
            int next = u8(jpeg, i + 1);
            if (next == STUFFED || (next >= RST0 && next <= RST7)) {
                skip = 2;
            } else if (next != MARKER) {
                skip = 0;
            }
        }
        return skip;
    }

    private static int u8(byte[] bytes, int index) {
        return bytes[index] & 0xFF;
    }

    private static byte[] identifier(String text) {
        return text.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static BusinessRuleException unsupported() {
        return new BusinessRuleException(ReasonCode.UNSUPPORTED_IMAGE);
    }
}
