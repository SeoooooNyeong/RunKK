package com.a207.runkk.crew;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** 시험용 JPEG 바이트 조립(2.1.10.22 표본). 영상 자료는 실제로 풀 수 없는 가짜 바이트다(서버는 SOS 뒤를 읽지 않는다). */
public final class JpegSamples {

    public static final int APP0 = 0xE0;
    public static final int APP1 = 0xE1;
    public static final int APP2 = 0xE2;
    public static final int APP13 = 0xED;
    public static final int APP14 = 0xEE;
    public static final int COM = 0xFE;
    public static final int DQT = 0xDB;
    public static final int SOF0 = 0xC0;
    public static final int DHT = 0xC4;
    public static final int SOF2 = 0xC2;
    public static final int DRI = 0xDD;
    public static final int SOS = 0xDA;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    private JpegSamples() {
        out.write(0xFF);
        out.write(0xD8);
    }

    public static JpegSamples soi() {
        return new JpegSamples();
    }

    /** 마커 + 2바이트 길이(길이 칸 포함) + 내용. */
    public JpegSamples segment(int marker, byte[] payload) {
        int length = payload.length + 2;
        out.write(0xFF);
        out.write(marker);
        out.write((length >> 8) & 0xFF);
        out.write(length & 0xFF);
        out.writeBytes(payload);
        return this;
    }

    public JpegSamples segment(int marker, String payload) {
        return segment(marker, payload.getBytes(StandardCharsets.ISO_8859_1));
    }

    /** 길이 칸을 그대로 쓴다(깨진 구조 표본). */
    public JpegSamples rawLength(int marker, int length, byte[] payload) {
        out.write(0xFF);
        out.write(marker);
        out.write((length >> 8) & 0xFF);
        out.write(length & 0xFF);
        out.writeBytes(payload);
        return this;
    }

    public JpegSamples raw(byte... bytes) {
        out.writeBytes(bytes);
        return this;
    }

    /** SOS(길이 8 머리) + 영상 자료 + EOI. */
    public JpegSamples scan(byte[] imageData) {
        segment(0xDA, new byte[] {1, 1, 0, 0, 0x3F, 0});
        out.writeBytes(imageData);
        out.write(0xFF);
        out.write(0xD9);
        return this;
    }

    /** SOS(길이 8 머리) + 영상 자료, EOI 없음(프로그레시브 JPEG의 스캔 하나). */
    public JpegSamples scanOnly(byte[] imageData) {
        segment(0xDA, new byte[] {1, 1, 0, 0, 0x3F, 0});
        out.writeBytes(imageData);
        return this;
    }

    /** EOI. */
    public JpegSamples eoi() {
        out.write(0xFF);
        out.write(0xD9);
        return this;
    }

    public byte[] bytes() {
        return out.toByteArray();
    }

    /** 위치가 든 메타데이터가 섞인 표본(TC-6210). */
    public static byte[] withLocation(byte[] imageData) {
        return soi().segment(APP0, "JFIF\0\1\1\0\0\1\0\1\0\0")
                .segment(APP1, "Exif\0\0MM\0*GPS 37.5665N 126.9780E")
                .segment(
                        APP1,
                        "http://ns.adobe.com/xap/1.0/\0<x:xmpmeta exif:GPSLatitude=\"37,30N\"/>")
                .segment(APP13, "Photoshop 3.0\0IPTC city=Seoul")
                .segment(COM, "loc=37.5,127.0")
                .segment(APP2, "ICC_PROFILE\0\1\1")
                .segment(APP14, "Adobe\0d\0\0\0\0")
                .segment(DQT, new byte[65])
                .segment(SOF0, new byte[] {8, 0, 16, 0, 16, 1, 1, 0x11, 0})
                .segment(DHT, new byte[20])
                .scan(imageData)
                .bytes();
    }

    /** 위치 메타데이터 없는 표본(TC-6211 ①). */
    public static byte[] clean(byte[] imageData) {
        return soi().segment(APP0, "JFIF\0\1\1\0\0\1\0\1\0\0")
                .segment(APP2, "ICC_PROFILE\0\1\1")
                .segment(APP14, "Adobe\0d\0\0\0\0")
                .segment(DQT, new byte[65])
                .segment(SOF0, new byte[] {8, 0, 16, 0, 16, 1, 1, 0x11, 0})
                .segment(DHT, new byte[20])
                .scan(imageData)
                .bytes();
    }

    /** 정확히 size바이트가 되도록 영상 자료를 채운 정상 표본(TC-6099). */
    public static byte[] ofSize(int size) {
        int overhead = clean(new byte[0]).length;
        return clean(new byte[size - overhead]);
    }

    /** 바이트열 안에 needle이 몇 번 있는가. */
    public static int count(byte[] haystack, String needle) {
        byte[] n = needle.getBytes(StandardCharsets.ISO_8859_1);
        int found = 0;
        outer:
        for (int i = 0; i + n.length <= haystack.length; i++) {
            for (int j = 0; j < n.length; j++) {
                if (haystack[i + j] != n[j]) {
                    continue outer;
                }
            }
            found++;
        }
        return found;
    }

    /** SOS 전까지 세그먼트 마커 목록(TC-6210 순서 확인). */
    public static java.util.List<Integer> markers(byte[] jpeg) {
        java.util.List<Integer> markers = new java.util.ArrayList<>();
        int pos = 2;
        while (pos + 3 < jpeg.length) {
            int marker = jpeg[pos + 1] & 0xFF;
            markers.add(marker);
            if (marker == 0xDA) {
                break;
            }
            int length = ((jpeg[pos + 2] & 0xFF) << 8) | (jpeg[pos + 3] & 0xFF);
            pos += 2 + length;
        }
        return markers;
    }
}
