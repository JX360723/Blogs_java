package com.xdl.blogsadmin.web;

import com.xdl.blogsadmin.config.WebMvcUploadConfig;
import com.xdl.blogsadmin.controller.AdminFileController;
import com.xdl.blogscommon.jwt.JwtPrincipal;
import com.xdl.blogscommon.jwt.JwtTokenProvider;
import com.xdl.blogscommon.exception.GlobalExceptionHandler;
import com.xdl.bologssecurity.config.SecurityConfig;
import com.xdl.bologssecurity.config.SecurityCorsProperties;
import com.xdl.bologssecurity.filter.JwtAuthenticationFilter;
import com.xdl.bologssecurity.handler.RestSecurityExceptionHandler;
import com.xdl.blogscore.storage.FileStorageProperties;
import com.xdl.blogscore.storage.LocalFileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 图片上传接口测试（MockMvc，覆盖施工单第 7 节 E1 用例）
 * <p>
 * 锁死的安全约定：
 * <ul>
 *   <li>类型只认<b>魔数</b>：伪造扩展名 / 伪造 Content-Type 都骗不过，存储扩展名由魔数决定</li>
 *   <li>SVG 拒绝（存储型 XSS 载体）、0 字节拒绝</li>
 *   <li>原始文件名与 scope 都不参与落盘路径——路径穿越用例要断言<b>实际落盘路径</b>，
 *       而不只是响应里的 url（响应可以是假的，磁盘不会说谎）</li>
 *   <li>url 必须以 /uploads/ 开头的相对路径</li>
 * </ul>
 * 落盘目录指向 {@code target/test-uploads}，每个用例前清空，互相不污染。
 *
 * @author xdl
 */
@WebMvcTest(controllers = AdminFileController.class)
@AutoConfigureMockMvc
// 与 AdminArticleSecurityTest 同样的原因：只装 Web 层 + 安全链 + 上传三件套，
// 不让 @ComponentScan("com.xdl") 把 Mapper 拖进来
@ContextConfiguration(classes = {AdminFileController.class, SecurityConfig.class,
        JwtAuthenticationFilter.class, RestSecurityExceptionHandler.class,
        SecurityCorsProperties.class, GlobalExceptionHandler.class,
        LocalFileStorage.class, FileStorageProperties.class, WebMvcUploadConfig.class})
@TestPropertySource(properties = {
        "blogs.upload.dir=target/test-uploads",
        "blogs.jwt.secret=test-secret-0123456789abcdef0123456789abcdef"
})
@DisplayName("图片上传（E1）")
class AdminFileUploadTest {

    private static final String UPLOAD_URL = "/api/v1/admin/files";

    private static final String BEARER_PREFIX = "Bearer ";

    private static final String ADMIN_TOKEN = BEARER_PREFIX + "admin-token";

    /**
     * 合法落盘路径：/uploads/yyyy/MM/uuid.ext（uuid 去横线后 32 位十六进制）
     */
    private static final Pattern URL_PATTERN =
            Pattern.compile("^/uploads/\\d{4}/\\d{2}/[0-9a-f]{32}\\.(png|jpg|gif|webp)$");

    private static final byte[] PNG_BYTES = {(byte) 0x89, 0x50, 0x4E, 0x47,
            0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D};

    private static final byte[] JPEG_BYTES = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF,
            (byte) 0xE0, 0x00, 0x10};

    private static final byte[] GIF_BYTES = "GIF89a".getBytes() /* 头 */ ;

    private static final byte[] WEBP_BYTES = {
            'R', 'I', 'F', 'F', 0x24, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P'};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LocalFileStorage localFileStorage;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    private Path root;

    @BeforeEach
    void setUp() throws Exception {
        given(jwtTokenProvider.parseToken("admin-token"))
                .willReturn(new JwtPrincipal(1L, "admin", "ADMIN"));
        root = localFileStorage.storageRoot();
        // 清空落盘目录，保证「目录里没有新增文件」类断言可信
        if (Files.exists(root)) {
            try (Stream<Path> walk = Files.walk(root)) {
                walk.sorted(java.util.Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(java.io.File::delete);
            }
        }
        Files.createDirectories(root);
    }

    private MockMultipartFile pngFile(String originalName) {
        return new MockMultipartFile("file", originalName, "image/png", PNG_BYTES);
    }

    /**
     * 上传并返回响应体 JSON（用于断言实际落盘路径）
     */
    private MvcResult upload(MockMultipartFile file, String scope) throws Exception {
        var builder = multipart(UPLOAD_URL).file(file).header("Authorization", ADMIN_TOKEN);
        if (scope != null) {
            builder = builder.param("scope", scope);
        }
        return mockMvc.perform(builder).andExpect(status().isOk()).andReturn();
    }

    private String jsonField(MvcResult result, String field) throws Exception {
        // JsonPath.read 返回 Object（数字字段是 Integer），统一转字符串断言
        Object value = com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8),
                "$.data." + field);
        return String.valueOf(value);
    }

    @Test
    @DisplayName("E1-1 PNG：200，url 为 /uploads/ 相对路径，实际落盘存在")
    void pngUploadShouldPass() throws Exception {
        MvcResult result = upload(pngFile("photo.png"), "cover");

        String url = jsonField(result, "url");
        assertThat(url).matches(URL_PATTERN);
        assertThat(jsonField(result, "name")).endsWith(".png");
        assertThat(jsonField(result, "contentType")).isEqualTo("image/png");
        assertThat(jsonField(result, "size")).isEqualTo(String.valueOf(PNG_BYTES.length));
        // 响应给的相对路径必须能对应到真实落盘文件
        assertThat(root.resolve(url.replaceFirst("^/uploads/", "")))
                .exists();
    }

    @Test
    @DisplayName("E1-2 JPEG：按魔数判为 jpg")
    void jpegUploadShouldPass() throws Exception {
        MvcResult result = upload(new MockMultipartFile("file", "pic.jpeg",
                "image/jpeg", JPEG_BYTES), null);

        assertThat(jsonField(result, "url")).matches("^/uploads/\\d{4}/\\d{2}/[0-9a-f]{32}\\.jpg$");
        assertThat(jsonField(result, "contentType")).isEqualTo("image/jpeg");
    }

    @Test
    @DisplayName("E1-3 GIF：按魔数判为 gif")
    void gifUploadShouldPass() throws Exception {
        byte[] gif = concatenate(GIF_BYTES, new byte[]{0x00, 0x00});
        MvcResult result = upload(new MockMultipartFile("file", "ani.gif",
                "image/gif", gif), null);

        assertThat(jsonField(result, "url")).matches("^/uploads/\\d{4}/\\d{2}/[0-9a-f]{32}\\.gif$");
        assertThat(jsonField(result, "contentType")).isEqualTo("image/gif");
    }

    @Test
    @DisplayName("E1-4 WEBP：按魔数判为 webp")
    void webpUploadShouldPass() throws Exception {
        MvcResult result = upload(new MockMultipartFile("file", "pic.webp",
                "image/webp", WEBP_BYTES), null);

        assertThat(jsonField(result, "url")).matches("^/uploads/\\d{4}/\\d{2}/[0-9a-f]{32}\\.webp$");
        assertThat(jsonField(result, "contentType")).isEqualTo("image/webp");
    }

    @Test
    @DisplayName("E1-5 scope 缺省：200，等价于 image")
    void missingScopeShouldPass() throws Exception {
        MvcResult result = upload(pngFile("photo.png"), null);

        assertThat(jsonField(result, "url")).matches(URL_PATTERN);
    }

    @Test
    @DisplayName("E1-6 SVG 内容：400 拒绝，且不落盘（存储型 XSS 防线）")
    void svgShouldBeRejected() throws Exception {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                .getBytes();

        mockMvc.perform(multipart(UPLOAD_URL)
                        .file(new MockMultipartFile("file", "evil.svg", "image/svg+xml", svg))
                        .header("Authorization", ADMIN_TOKEN))
                .andExpect(status().isBadRequest());

        assertThat(diskFileCount()).isZero();
    }

    @Test
    @DisplayName("E1-6b 反向伪装：fake.png 内容是纯文本 → 400（只认魔数，不信扩展名）")
    void textContentWithPngNameShouldBeRejected() throws Exception {
        mockMvc.perform(multipart(UPLOAD_URL)
                        .file(new MockMultipartFile("file", "fake.png", "image/png",
                                "这不是图片，只是把后缀改成了 png".getBytes()))
                        .header("Authorization", ADMIN_TOKEN))
                .andExpect(status().isBadRequest());

        assertThat(diskFileCount()).isZero();
    }

    @Test
    @DisplayName("E1-6c 超 5MB：400 拒绝，不落盘")
    void oversizedImageShouldBeRejected() throws Exception {
        byte[] tooLarge = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(PNG_BYTES, 0, tooLarge, 0, PNG_BYTES.length);

        mockMvc.perform(multipart(UPLOAD_URL)
                        .file(new MockMultipartFile("file", "big.png", "image/png", tooLarge))
                        .header("Authorization", ADMIN_TOKEN))
                .andExpect(status().isBadRequest());

        assertThat(diskFileCount()).isZero();
    }

    @Test
    @DisplayName("E1-7 未带 token：401")
    void missingTokenShouldBeRejected() throws Exception {
        mockMvc.perform(multipart(UPLOAD_URL).file(pngFile("photo.png")))
                .andExpect(status().isUnauthorized());

        assertThat(diskFileCount()).isZero();
    }

    @Test
    @DisplayName("E1-8 普通用户 token：403（上传只给管理员）")
    void nonAdminTokenShouldBeForbidden() throws Exception {
        given(jwtTokenProvider.parseToken("user-token"))
                .willReturn(new JwtPrincipal(2L, "user", "USER"));

        mockMvc.perform(multipart(UPLOAD_URL)
                        .file(pngFile("photo.png"))
                        .header("Authorization", BEARER_PREFIX + "user-token"))
                .andExpect(status().isForbidden());

        assertThat(diskFileCount()).isZero();
    }

    @Test
    @DisplayName("E1-7 0 字节文件：400 拒绝")
    void emptyFileShouldBeRejected() throws Exception {
        mockMvc.perform(multipart(UPLOAD_URL)
                        .file(new MockMultipartFile("file", "empty.png", "image/png", new byte[0]))
                        .header("Authorization", ADMIN_TOKEN))
                .andExpect(status().isBadRequest());

        assertThat(diskFileCount()).isZero();
    }

    @Test
    @DisplayName("E1-8 伪造扩展名：.exe 内容是 PNG → 落盘扩展名由魔数决定为 .png")
    void forgedExtensionShouldFollowMagicNumber() throws Exception {
        MvcResult result = upload(new MockMultipartFile("file", "shell.exe",
                "application/octet-stream", PNG_BYTES), null);

        String name = jsonField(result, "name");
        assertThat(name).endsWith(".png");
        // 实际落盘的也是 .png，而不是 .exe
        Path stored = root.resolve(jsonField(result, "url").replaceFirst("^/uploads/", ""));
        assertThat(stored).exists();
        assertThat(stored.getFileName().toString()).endsWith(".png");
    }

    @Test
    @DisplayName("E1-9 路径穿越：恶意 scope 直接 400；穿越文件名不影响落盘路径")
    void pathTraversalShouldStayInStorageRoot() throws Exception {
        // 恶意 scope：白名单外的取值一律 400（比静默忽略更严格），不落盘
        mockMvc.perform(multipart(UPLOAD_URL)
                        .file(pngFile("normal.png"))
                        .param("scope", "../../etc")
                        .header("Authorization", ADMIN_TOKEN))
                .andExpect(status().isBadRequest());
        assertThat(diskFileCount()).isZero();

        // 恶意原始文件名（携带 ../）：文件名根本不参与落盘路径，正常上传
        MvcResult result = upload(pngFile("../../evil.png"), "cover");

        // 响应 url 是规范的 /uploads/yyyy/MM/uuid.png
        String url = jsonField(result, "url");
        assertThat(url).matches(URL_PATTERN);

        // 实际落盘路径必须与 url 一致，且真实存在于存储根内
        Path stored = root.resolve(url.replaceFirst("^/uploads/", "")).normalize();
        assertThat(stored).exists();
        assertThat(stored.normalize().startsWith(root.normalize())).isTrue();

        // 整棵落盘树里不允许出现任何非 uuid.png 命名的文件、不允许逃出存储根
        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> files = walk.filter(Files::isRegularFile).toList();
            assertThat(files).hasSize(1);
            assertThat(files.get(0).getFileName().toString())
                    .matches("[0-9a-f]{32}\\.(png|jpg|gif|webp)");
        }
        // 逃逸文件不存在：相对 root 上跳两级的位置没有 evil.png
        assertThat(root.getParent().getParent().resolve("evil.png")).doesNotExist();
    }

    private long diskFileCount() throws Exception {
        if (!Files.exists(root)) {
            return 0;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile).count();
        }
    }

    private byte[] concatenate(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
