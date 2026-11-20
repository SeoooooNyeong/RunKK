package com.a207.runkk.avatar;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/**
 * 머리 층 PNG 축소(S15P31A207-300). 1024 공통 캔버스를 비율 그대로 줄이고 투명도를 지킨다.
 *
 * <ul>
 *   <li>축소: 2×2 알파 가중 평균을 거듭한다(1024→512→256). 투명 픽셀 색이 가장자리에 번지지 않고, 앱이 알파 경계로 재는 목·턱·정수리 위치가 원본
 *       비율과 같다.
 *   <li>인코딩: 무손실 PNG(JDK ImageIO, 최대 압축). WebP는 JDK에 인코더가 없어 네이티브 libwebp(JNI) 의존성이 필요하고, 손실 압축은 앱이
 *       알파·밝기 경계로 목·눈을 찾는 합성을 바꿀 수 있어 쓰지 않았다.
 * </ul>
 */
final class HeadLayerImages {
    private HeadLayerImages() {}

    /**
     * [png](한 변 canvas 기준 그림)를 [size]/[canvas] 비율로 줄인 PNG. 원본이 이미 그보다 작거나 같으면 원본 그대로다. PNG가 아니면 503
     * UPSTREAM_FAILED.
     */
    static byte[] downscale(byte[] png, int size, int canvas) {
        BufferedImage source = read(png);
        int width = source.getWidth();
        int height = source.getHeight();
        int targetWidth = Math.max(1, Math.round(width * (float) size / canvas));
        int targetHeight = Math.max(1, Math.round(height * (float) size / canvas));
        if (targetWidth >= width && targetHeight >= height) return png;
        int[] pixels = source.getRGB(0, 0, width, height, null, 0, width);
        while (width >= targetWidth * 2 && height >= targetHeight * 2) {
            pixels = halve(pixels, width, height);
            width = (width + 1) / 2;
            height = (height + 1) / 2;
        }
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        result.setRGB(0, 0, width, height, pixels, 0, width);
        return write(result);
    }

    /** 2×2 칸을 알파 가중 평균한다(알파는 단순 평균, 색은 알파 가중, 둘 다 반올림). 홀수 끝 줄·칸은 있는 픽셀만 쓴다. */
    static int[] halve(int[] pixels, int width, int height) {
        int halfWidth = (width + 1) / 2;
        int halfHeight = (height + 1) / 2;
        int[] out = new int[halfWidth * halfHeight];
        for (int y = 0; y < halfHeight; y++) {
            for (int x = 0; x < halfWidth; x++) {
                long alpha = 0;
                long red = 0;
                long green = 0;
                long blue = 0;
                int count = 0;
                for (int dy = 0; dy < 2; dy++) {
                    int sy = y * 2 + dy;
                    if (sy >= height) continue;
                    for (int dx = 0; dx < 2; dx++) {
                        int sx = x * 2 + dx;
                        if (sx >= width) continue;
                        int argb = pixels[sy * width + sx];
                        int a = argb >>> 24;
                        alpha += a;
                        red += (long) ((argb >> 16) & 0xff) * a;
                        green += (long) ((argb >> 8) & 0xff) * a;
                        blue += (long) (argb & 0xff) * a;
                        count++;
                    }
                }
                if (alpha == 0) continue;
                int a = (int) ((alpha + count / 2) / count);
                int r = (int) ((red + alpha / 2) / alpha);
                int g = (int) ((green + alpha / 2) / alpha);
                int b = (int) ((blue + alpha / 2) / alpha);
                out[y * halfWidth + x] = (a << 24) | (r << 16) | (g << 8) | b;
            }
        }
        return out;
    }

    private static BufferedImage read(byte[] png) {
        try {
            // 임시 파일 캐시를 쓰지 않게 메모리 스트림으로 읽는다.
            BufferedImage image =
                    ImageIO.read(new MemoryCacheImageInputStream(new ByteArrayInputStream(png)));
            if (image == null) throw upstreamFailed();
            return image;
        } catch (IOException error) {
            throw upstreamFailed();
        }
    }

    private static byte[] write(BufferedImage image) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("png");
        if (!writers.hasNext()) throw new IllegalStateException("PNG 인코더가 없다");
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
                ImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                // 0이 가장 작게(deflate 9). 무손실이라 그림은 같다.
                param.setCompressionQuality(0f);
            }
            writer.setOutput(stream);
            writer.write(null, new IIOImage(image, null, null), param);
            stream.flush();
            return out.toByteArray();
        } catch (IOException error) {
            throw new IllegalStateException("PNG를 만들지 못했다", error);
        } finally {
            writer.dispose();
        }
    }

    private static FaceJobRejectedException upstreamFailed() {
        return new FaceJobRejectedException(503, "UPSTREAM_FAILED");
    }
}
