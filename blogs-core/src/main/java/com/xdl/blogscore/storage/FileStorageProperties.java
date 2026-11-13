package com.xdl.blogscore.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

/**
 * 文件上传存储配置
 * <p>
 * 注册方式与 {@code JwtProperties} 一致（@Component + @ConfigurationProperties）。
 * <ul>
 *   <li>{@code dir}：图片落盘根目录。容器环境用 {@code UPLOAD_DIR=/app/uploads} 注入，
 *       本地开发默认 {@code ./uploads}（相对运行目录）</li>
 *   <li>{@code maxImageSize}：单张图片业务上限，独立于 {@code spring.servlet.multipart.max-file-size}。
 *       multipart 上限是「整个请求」的容器级闸门，这里再按业务语义精确拦截并返回
 *       明确的业务错误，两层各管各的</li>
 * </ul>
 *
 * @author xdl
 */
@Data
@Component
@ConfigurationProperties(prefix = "blogs.upload")
public class FileStorageProperties {

    /**
     * 图片落盘根目录，容器内由 UPLOAD_DIR 环境变量指向挂载卷
     */
    private String dir = "./uploads";

    /**
     * 单张图片大小上限（业务层校验）
     */
    private DataSize maxImageSize = DataSize.ofMegabytes(5);
}
