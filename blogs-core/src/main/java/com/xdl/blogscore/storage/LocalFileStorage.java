package com.xdl.blogscore.storage;

import com.xdl.blogscommon.exception.BusinessException;
import com.xdl.model.vo.FileVO;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 本地磁盘图片存储
 * <p>
 * 核心安全约定（按施工单第 5 条）：
 * <ul>
 *   <li><b>不信任客户端声明的类型</b>：只按 {@link ImageTypeDetector} 的文件头魔数判定类型，
 *       扩展名由魔数决定，原始文件名不参与存储路径（也就不存在通过文件名注入 {@code ../}
 *       或后缀进行路径穿越 / 双扩展名解析的可能）</li>
 *   <li><b>拒绝 SVG</b>：SVG 是文本格式的 XML，可内嵌脚本，同源渲染即存储型 XSS，
 *       魔数判定天然不含它</li>
 *   <li><b>拒绝 0 字节</b>：空文件既不是合法图片，也会污染存储目录</li>
 *   <li>文件名 UUID，按 {@code yyyy/MM} 分目录，避免单目录文件过多；目标路径一律
 *       {@code root.resolve(...)} 在存储根内拼接，不拼接任何用户输入</li>
 * </ul>
 * 失败一律抛 {@link BusinessException}，code 与 HTTP 状态码一致（全部按 400 参数类错误返回，
 * 与施工单 E1 用例一致），经 GlobalExceptionHandler 输出 Result 包络。
 * 只有容器层 multipart 超限（{@code MaxUploadSizeExceededException}）才走 413。
 *
 * @author xdl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocalFileStorage {

    /**
     * 合法的 scope 白名单；缺省值 image。
     * scope 只做用途标记校验，<b>不参与存储路径拼接</b>，从源头杜绝穿越。
     */
    private static final Set<String> ALLOWED_SCOPES = Set.of("cover", "image");

    private static final String DEFAULT_SCOPE = "image";

    private static final DateTimeFormatter YEAR_FORMAT = DateTimeFormatter.ofPattern("yyyy", Locale.ROOT);

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("MM", Locale.ROOT);

    /**
     * 魔数判定只需文件头：PNG 8 字节 / JPEG 3 / GIF 6 / WEBP 12，取 12 足够，
     * 避免为判类型把 5MB 图片整个读进堆内存
     */
    private static final int HEAD_LENGTH = 12;

    private final FileStorageProperties properties;

    /**
     * 启动时建一次存储根目录（施工单 C1）
     * <p>
     * 不要在每个上传请求里判断目录是否存在：启动期一次 createDirectories，
     * 之后只建 yyyy/MM 子目录；同时能在启动日志里尽早暴露权限问题
     * （容器里 UPLOAD_DIR 属主不是 app 用户时，这里就会失败而不是等到用户上传）。
     */
    @PostConstruct
    void init() {
        Path root = storageRoot();
        try {
            Files.createDirectories(root);
            log.info("图片存储根目录就绪: {}", root);
        } catch (IOException e) {
            // 启动期目录不可建不直接终止应用（本地只读文件系统等场景），
            // 但必须打 ERROR，否则第一次上传 500 时才排查成本极高
            log.error("图片存储根目录创建失败，上传功能将不可用: {}", root, e);
        }
    }

    /**
     * 存储一张图片
     *
     * @param file  上传的 multipart 文件
     * @param scope 用途标记（cover/image），可空
     * @return FileVO，url 以 /uploads/ 开头的相对路径
     */
    public FileVO store(MultipartFile file, String scope) {
        // ---- 空文件：0 字节既不是合法图片，也污染存储目录（契约：400） ----
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "上传内容为空");
        }

        // ---- scope 白名单：非法值直接 400，不做任何模糊处理 ----
        String effectiveScope = scope == null ? DEFAULT_SCOPE : scope.toLowerCase(Locale.ROOT);
        if (!ALLOWED_SCOPES.contains(effectiveScope)) {
            throw new BusinessException(400, "scope 取值非法，仅支持 cover 或 image");
        }

        // ---- 魔数判定：只信文件头，不信 Content-Type 与原始文件名（先判类型再判体积） ----
        Optional<ImageTypeDetector.ImageType> detected = ImageTypeDetector.detect(readHead(file));
        if (detected.isEmpty()) {
            // SVG / HTML / 任意非图片内容统一 400（与施工单 E1 用例一致，不透露过多格式细节）
            throw new BusinessException(400, "不支持的图片格式，仅支持 PNG/JPEG/GIF/WEBP");
        }
        ImageTypeDetector.ImageType imageType = detected.get();

        // ---- 业务级大小上限（multipart 5MB 是容器级闸门，这里再按业务语义精确拦截） ----
        if (file.getSize() > properties.getMaxImageSize().toBytes()) {
            throw new BusinessException(400, "图片超出大小限制（上限 "
                    + properties.getMaxImageSize().toMegabytes() + "MB）");
        }

        // ---- 生成存储路径：UUID 文件名 + 魔数扩展名 + yyyy/MM 分目录 ----
        LocalDate today = LocalDate.now();
        String year = today.format(YEAR_FORMAT);
        String month = today.format(MONTH_FORMAT);
        // UUID 去掉横线：32 位十六进制，文件名更短且仍是 122 bit 随机，无碰撞风险
        String filename = UUID.randomUUID().toString().replace("-", "") + "."
                + imageType.getExtension();

        Path root = storageRoot();
        Path targetDir = root.resolve(year).resolve(month);
        Path target = targetDir.resolve(filename);
        try {
            Files.createDirectories(targetDir);
            // 流式落盘：类型已校验通过，不必把整份内容再读进内存
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("图片落盘失败: dir={}", root, e);
            throw new BusinessException(500, "文件保存失败，请稍后重试");
        }

        String url = "/uploads/" + year + "/" + month + "/" + filename;
        log.info("图片上传成功: scope={}, size={}, url={}", effectiveScope, file.getSize(), url);
        return new FileVO(url, filename, file.getSize(), imageType.getMimeType());
    }

    /**
     * 存储根目录的绝对规范化路径，供本类与 WebMvcUploadConfig（静态映射）共用同一份配置
     */
    public Path storageRoot() {
        return Paths.get(properties.getDir()).toAbsolutePath().normalize();
    }

    /**
     * 只读文件头若干字节用于魔数判定；读失败按 500 处理
     */
    private byte[] readHead(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(HEAD_LENGTH);
        } catch (IOException e) {
            log.error("读取上传内容失败", e);
            throw new BusinessException(500, "读取上传内容失败，请稍后重试");
        }
    }
}
