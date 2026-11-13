package com.xdl.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 文件上传结果 VO
 * <p>
 * 对应「图片上传」接口约定（POST /api/v1/admin/files）：
 * <ul>
 *   <li>{@code url} 必须是 {@code /uploads/} 开头的<b>相对路径</b>，不拼域名，
 *       由前端自行拼接站点根（同源部署下天然正确，也便于以后换 CDN 域名）</li>
 *   <li>{@code name} 是服务端生成的存储文件名（UUID + 魔数决定的扩展名），
 *       不回传客户端原始文件名——原始文件名不可信，回传反而诱导前端 XSS</li>
 *   <li>{@code contentType} 是<b>服务端按魔数判定</b>的标准 MIME 类型，
 *       不透传客户端声明的 Content-Type（可伪造）</li>
 * </ul>
 *
 * @author xdl
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 相对 URL，形如 /uploads/2026/11/uuid.png，以 /uploads/ 开头
     */
    private String url;

    /**
     * 服务端存储文件名：UUID + 按魔数判定的扩展名
     */
    private String name;

    /**
     * 文件字节数
     */
    private Long size;

    /**
     * 服务端按魔数判定的标准 MIME 类型（image/png 等）
     */
    private String contentType;
}
