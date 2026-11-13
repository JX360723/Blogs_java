package com.xdl.blogscore.storage;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * 图片类型魔数检测器
 * <p>
 * 只认<b>文件头字节</b>，不信任客户端声明的 Content-Type 与原始文件名——两者都可伪造，
 * 是「把 SVG / HTML / 可执行文件伪装成图片」这类存储型 XSS 的惯用入口。
 * <p>
 * 支持：PNG / JPEG / GIF87a / GIF89a / WEBP（RIFF 容器）。
 * 刻意<b>不支持 SVG</b>：SVG 是文本格式的 XML，可内嵌 {@code <script>}，浏览器直接渲染
 * 同源 SVG 时脚本会执行，属于存储型 XSS 载体，从上传入口整体拒绝。
 *
 * @author xdl
 */
public final class ImageTypeDetector {

    /**
     * 判定出的图片类型：extension 用于生成存储文件名，mimeType 用于响应与浏览器渲染
     */
    public enum ImageType {

        /**
         * PNG：89 50 4E 47 0D 0A 1A 0A
         */
        PNG("png", "image/png"),

        /**
         * JPEG：FF D8 FF
         */
        JPEG("jpg", "image/jpeg"),

        /**
         * GIF：GIF87a / GIF89a
         */
        GIF("gif", "image/gif"),

        /**
         * WEBP：RIFF....WEBP（4-8 字节是长度，跳过）
         */
        WEBP("webp", "image/webp");

        private final String extension;

        private final String mimeType;

        ImageType(String extension, String mimeType) {
            this.extension = extension;
            this.mimeType = mimeType;
        }

        public String getExtension() {
            return extension;
        }

        public String getMimeType() {
            return mimeType;
        }
    }

    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47,
            0x0D, 0x0A, 0x1A, 0x0A};

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private static final byte[] GIF87A = "GIF87a".getBytes(StandardCharsets.US_ASCII);

    private static final byte[] GIF89A = "GIF89a".getBytes(StandardCharsets.US_ASCII);

    private static final byte[] RIFF = "RIFF".getBytes(StandardCharsets.US_ASCII);

    private static final byte[] WEBP = "WEBP".getBytes(StandardCharsets.US_ASCII);

    private ImageTypeDetector() {
    }

    /**
     * 按文件头魔数判定图片类型
     *
     * @param data 文件完整字节（至少要有文件头）
     * @return 匹配的图片类型；不属于任何受支持格式（含 SVG、文本、空内容等）时返回 empty
     */
    public static Optional<ImageType> detect(byte[] data) {
        if (data == null || data.length == 0) {
            return Optional.empty();
        }
        // 各 startsWith 自带长度不足判定：PNG 头 8 字节、JPEG 3 字节、GIF 6 字节，
        // 不要求凑满 12 字节；只有 WEBP 头（RIFF + 4 字节长度 + WEBP）需要 12 字节
        if (startsWith(data, PNG_MAGIC)) {
            return Optional.of(ImageType.PNG);
        }
        if (startsWith(data, JPEG_MAGIC)) {
            return Optional.of(ImageType.JPEG);
        }
        if (startsWith(data, GIF87A) || startsWith(data, GIF89A)) {
            return Optional.of(ImageType.GIF);
        }
        if (startsWith(data, RIFF) && startsWith(data, WEBP, 8)) {
            return Optional.of(ImageType.WEBP);
        }
        return Optional.empty();
    }

    /**
     * 判断 data 从 offset 起是否以 prefix 开头；长度不足视为不匹配
     */
    private static boolean startsWith(byte[] data, byte[] prefix) {
        return startsWith(data, prefix, 0);
    }

    private static boolean startsWith(byte[] data, byte[] prefix, int offset) {
        if (data.length - offset < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[offset + i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
