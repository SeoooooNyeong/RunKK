package com.a207.runkk.crew;

import static com.a207.runkk.crew.JpegSamples.APP0;
import static com.a207.runkk.crew.JpegSamples.APP1;
import static com.a207.runkk.crew.JpegSamples.APP13;
import static com.a207.runkk.crew.JpegSamples.APP14;
import static com.a207.runkk.crew.JpegSamples.APP2;
import static com.a207.runkk.crew.JpegSamples.COM;
import static com.a207.runkk.crew.JpegSamples.DHT;
import static com.a207.runkk.crew.JpegSamples.DQT;
import static com.a207.runkk.crew.JpegSamples.DRI;
import static com.a207.runkk.crew.JpegSamples.SOF0;
import static com.a207.runkk.crew.JpegSamples.SOF2;
import static com.a207.runkk.crew.JpegSamples.SOS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 2.1.10.22 JpegMetadataStripper.strip와 PhotoUpload(NFR-042, D-142, TC-6210~TC-6215, TC-6099). */
@DisplayName("JPEG 위치 메타데이터 제거와 PhotoUpload")
class JpegMetadataStripperTest {

    private static final byte[] IMAGE = imageData();

    private static byte[] imageData() {
        byte[] data = new byte[100];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i * 7 + 1);
        }
        return data;
    }

    private static ReasonCode reasonOf(Throwable e) {
        return ((BusinessRuleException) e).reason();
    }

    private static byte[] tail(byte[] jpeg, int length) {
        return Arrays.copyOfRange(jpeg, jpeg.length - length, jpeg.length);
    }

    @Test
    @DisplayName("TC-6210: APP1(EXIF·XMP)·APP13·COM을 빼고 나머지는 원래 순서, SOS 뒤 영상 자료는 바이트 그대로")
    void stripsLocationSegments() {
        byte[] input = JpegSamples.withLocation(IMAGE);
        byte[] result = JpegMetadataStripper.strip(input);

        assertThat(JpegSamples.markers(result))
                .containsExactly(APP0, APP2, APP14, DQT, SOF0, DHT, 0xDA);
        for (String needle : List.of("Exif", "http://ns.adobe.com/xap", "GPS", "loc=", "IPTC")) {
            assertThat(JpegSamples.count(result, needle)).as(needle).isZero();
        }
        assertThat(tail(result, IMAGE.length + 2)).isEqualTo(tail(input, IMAGE.length + 2));
        assertThat(result).isEqualTo(JpegSamples.clean(IMAGE));
    }

    @Test
    @DisplayName("TC-6211: 보존 세그먼트만 있으면 그대로, APP1 하나만 더하면 그것만 빠진다. 입력 배열은 바뀌지 않는다")
    void keepsCleanImage() {
        byte[] clean = JpegSamples.clean(IMAGE);
        byte[] copy = clean.clone();
        assertThat(JpegMetadataStripper.strip(clean)).isEqualTo(clean);
        assertThat(clean).isEqualTo(copy);

        byte[] withExif =
                JpegSamples.soi()
                        .segment(APP1, "Exif\0\0GPS")
                        .raw(Arrays.copyOfRange(clean, 2, clean.length))
                        .bytes();
        assertThat(JpegMetadataStripper.strip(withExif)).isEqualTo(clean);
    }

    @Test
    @DisplayName("TC-6212: SOI 바로 뒤 SOS면 그대로, 영상 자료 안의 FF 00·RST·채움 FF는 바이트 그대로 둔다")
    void keepsEntropyDataBytes() {
        byte[] bare = JpegSamples.soi().scan(IMAGE).bytes();
        assertThat(JpegMetadataStripper.strip(bare)).isEqualTo(bare);

        byte[] entropy = {
            0x12,
            (byte) 0xFF,
            0x00,
            0x34,
            (byte) 0xFF,
            (byte) 0xD0,
            0x56,
            (byte) 0xFF,
            (byte) 0xD7,
            0x78,
            (byte) 0xFF,
            (byte) 0xFF
        };
        byte[] input = JpegSamples.soi().segment(APP0, "JFIF\0").scan(entropy).bytes();
        assertThat(JpegMetadataStripper.strip(input)).isEqualTo(input);
    }

    @Test
    @DisplayName(
            "TC-6211 허용 목록: JFIF APP0·ICC APP2·Adobe APP14·DQT·SOF·DHT·DRI만 남고 그 밖의 APPn·COM은 빠진다")
    void keepsOnlyAllowedSegments() {
        JpegSamples input =
                JpegSamples.soi()
                        .segment(APP0, "JFIF\0\1\1\0\0\1\0\1\0\0")
                        .segment(APP0, "JFXX\0\u0010thumb")
                        .segment(APP2, "MPF\0MM\0*GPS")
                        .segment(APP2, "ICC_PROFILE\0\1\1")
                        .segment(APP2, "FPXR\0loc");
        for (int app = 0xE3; app <= 0xEF; app++) {
            if (app != APP14) {
                input.segment(app, "META" + app + "GPS");
            }
        }
        byte[] result =
                JpegMetadataStripper.strip(
                        input.segment(APP14, "Adobe\0d\0\0\0\0")
                                .segment(APP14, "NotAdobe GPS")
                                .segment(COM, "GPS")
                                .segment(DQT, new byte[65])
                                .segment(DRI, new byte[] {0, 4})
                                .segment(SOF2, new byte[] {8, 0, 16, 0, 16, 1, 1, 0x11, 0})
                                .segment(DHT, new byte[20])
                                .scan(IMAGE)
                                .bytes());

        assertThat(JpegSamples.markers(result))
                .containsExactly(APP0, APP2, APP14, DQT, DRI, SOF2, DHT, SOS);
        assertThat(JpegSamples.count(result, "JFIF")).isOne();
        assertThat(JpegSamples.count(result, "ICC_PROFILE")).isOne();
        assertThat(JpegSamples.count(result, "Adobe")).isOne();
        for (String needle : List.of("GPS", "JFXX", "MPF", "FPXR", "META", "NotAdobe")) {
            assertThat(JpegSamples.count(result, needle)).as(needle).isZero();
        }
    }

    @Test
    @DisplayName("TC-6211 프로그레시브: 스캔 사이의 APP1·APP13·COM도 빠지고, 스캔 사이 DHT와 각 스캔 자료는 그대로, EOI 뒤는 버린다")
    void stripsSegmentsBetweenProgressiveScans() {
        byte[] first = {0x11, 0x22, (byte) 0xFF, 0x00, 0x33};
        byte[] second = {0x44, (byte) 0xFF, (byte) 0xD1, 0x55};
        byte[] input =
                JpegSamples.soi()
                        .segment(APP0, "JFIF\0")
                        .segment(DQT, new byte[65])
                        .segment(SOF2, new byte[] {8, 0, 16, 0, 16, 1, 1, 0x11, 0})
                        .segment(DHT, new byte[20])
                        .scanOnly(first)
                        .segment(APP1, "Exif\0\0MM\0*GPS 37.5665N")
                        .segment(APP13, "Photoshop 3.0\0IPTC city=Seoul")
                        .segment(COM, "loc=37.5,127.0")
                        .segment(DHT, new byte[18])
                        .scanOnly(second)
                        .eoi()
                        .raw("Exif GPS trailer".getBytes(StandardCharsets.ISO_8859_1))
                        .bytes();
        byte[] expected =
                JpegSamples.soi()
                        .segment(APP0, "JFIF\0")
                        .segment(DQT, new byte[65])
                        .segment(SOF2, new byte[] {8, 0, 16, 0, 16, 1, 1, 0x11, 0})
                        .segment(DHT, new byte[20])
                        .scanOnly(first)
                        .segment(DHT, new byte[18])
                        .scanOnly(second)
                        .eoi()
                        .bytes();

        byte[] result = JpegMetadataStripper.strip(input);
        assertThat(result).isEqualTo(expected);
        for (String needle : List.of("Exif", "GPS", "IPTC", "loc=")) {
            assertThat(JpegSamples.count(result, needle)).as(needle).isZero();
        }
    }

    @Test
    @DisplayName("리뷰 반영: EOI 뒤에 덧붙인 EXIF 꼬리·두 번째 JPEG는 버린다")
    void dropsTrailerAfterEndOfImage() {
        byte[] clean = JpegSamples.clean(IMAGE);
        byte[] trailer = JpegSamples.withLocation(new byte[10]);
        byte[] input = new byte[clean.length + trailer.length];
        System.arraycopy(clean, 0, input, 0, clean.length);
        System.arraycopy(trailer, 0, input, clean.length, trailer.length);
        byte[] result = JpegMetadataStripper.strip(input);
        assertThat(result).isEqualTo(clean);
        assertThat(JpegSamples.count(result, "GPS")).isZero();
    }

    @Test
    @DisplayName("TC-6213: 길이 2 미만·파일 끝 넘김·FF 아닌 바이트·SOS 없이 끝남은 UNSUPPORTED_IMAGE")
    void rejectsBrokenStructure() {
        List<byte[]> broken =
                List.of(
                        JpegSamples.soi().rawLength(APP1, 1, new byte[0]).scan(IMAGE).bytes(),
                        JpegSamples.soi().rawLength(APP1, 0xFFFF, new byte[20]).bytes(),
                        JpegSamples.soi().raw((byte) 0x00, (byte) 0x01).scan(IMAGE).bytes(),
                        JpegSamples.soi().segment(APP0, "JFIF\0").bytes());
        for (byte[] jpeg : broken) {
            assertThatThrownBy(() -> JpegMetadataStripper.strip(jpeg))
                    .satisfies(
                            e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.UNSUPPORTED_IMAGE));
        }
    }

    @Test
    @DisplayName("TC-6214·TC-6099: 크기(1MB) → 머리(FF D8 FF) → strip 순서로 검사한다")
    void photoUploadChecksInOrder() {
        assertThat(new PhotoUpload(JpegSamples.ofSize(PhotoUpload.MAX_BYTES - 1)).size())
                .isEqualTo(PhotoUpload.MAX_BYTES - 1);
        assertThat(new PhotoUpload(JpegSamples.ofSize(PhotoUpload.MAX_BYTES)).size())
                .isEqualTo(PhotoUpload.MAX_BYTES);

        byte[] tooLarge = new byte[PhotoUpload.MAX_BYTES + 1]; // 구조도 깨졌지만 크기 검사가 먼저
        assertThatThrownBy(() -> new PhotoUpload(tooLarge))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.TOO_LARGE));
        byte[] png = new byte[10_240];
        png[0] = (byte) 0x89;
        png[1] = 0x50;
        png[2] = 0x4E;
        png[3] = 0x47;
        byte[] brokenApp1 = JpegSamples.soi().rawLength(APP1, 0x7FFF, new byte[14]).bytes();
        for (byte[] bad :
                List.of(png, new byte[] {(byte) 0xFF, (byte) 0xD8}, new byte[0], brokenApp1)) {
            assertThatThrownBy(() -> new PhotoUpload(bad))
                    .satisfies(
                            e -> assertThat(reasonOf(e)).isEqualTo(ReasonCode.UNSUPPORTED_IMAGE));
        }
    }

    @Test
    @DisplayName("TC-6215: PhotoUpload.jpeg()는 strip 결과(위치 항목 0, 입력보다 짧음)이고 사본이다")
    void photoUploadKeepsOnlyStrippedBytes() {
        byte[] input = JpegSamples.withLocation(IMAGE);
        PhotoUpload upload = new PhotoUpload(input);
        byte[] jpeg = upload.jpeg();

        assertThat(jpeg).isEqualTo(JpegSamples.clean(IMAGE));
        assertThat(jpeg.length).isLessThan(input.length);
        assertThat(JpegSamples.count(jpeg, "GPS")).isZero();
        jpeg[0] = 0;
        assertThat(upload.jpeg()[0]).as("밖에서 바꿔도 안은 그대로").isEqualTo((byte) 0xFF);
    }
}
