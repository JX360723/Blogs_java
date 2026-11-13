package com.xdl.blogsadmin.config;

import com.xdl.blogscore.storage.FileStorageProperties;
import com.xdl.blogscore.storage.LocalFileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.time.Duration;

/**
 * 上传图片的静态资源映射
 * <p>
 * 把 {@code /uploads/**} 映射到本地存储目录，访客端不经过鉴权即可加载图片
 * （SecurityConfig 对 GET /uploads/** permitAll，两处必须配套改）。
 * <p>
 * 两个易错点（按施工单第 3 条）：
 * <ul>
 *   <li>location 必须用 {@code Path.toUri().toString()}：Windows 下会生成
 *       {@code file:///E:/...}，手拼 {@code "file:" + path} 在 Windows 会丢一个斜杠，
 *       导致资源永远 404；并确保以 {@code /} 结尾，否则 Spring 按相对路径解析</li>
 *   <li>缓存 365 天 + immutable：文件名是 UUID，内容永不变化，immutable 能让浏览器
 *       重访期间完全跳过条件请求</li>
 * </ul>
 *
 * @author xdl
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcUploadConfig implements WebMvcConfigurer {

    /**
     * 静态映射的 URL 前缀，与 LocalFileStorage 生成的相对 url 前缀保持一致
     */
    private static final String URL_PATTERN = "/uploads/**";

    private final FileStorageProperties properties;

    private final LocalFileStorage localFileStorage;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 与 LocalFileStorage 用同一个 storageRoot()，保证「写入路径」和「读取路径」永远一致
        Path root = localFileStorage.storageRoot();
        String location = root.toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler(URL_PATTERN)
                .addResourceLocations(location)
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
    }
}
