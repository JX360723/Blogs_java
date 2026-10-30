package com.xdl.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 公开文章列表查询参数
 * <p>
 * 对应 docs/BACKEND_DESIGN_DB_AND_API.md 4.5「GET /api/v1/articles」的 Query 参数。
 * 参数非法时由 GlobalExceptionHandler 统一转 400，不回显原始输入。
 * <p>
 * {@code categoryId} 不加 @Min 校验：前端「全部」页签可能传 0，
 * Service 层对 {@code null} 与 {@code <=0} 一律视为不过滤。
 *
 * @author xdl
 */
@Data
public class ArticleQueryRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 页码，从 1 开始
     */
    @Min(value = 1, message = "页码必须大于0")
    private Long pageNum = 1L;

    /**
     * 每页条数，最大 100
     */
    @Min(value = 1, message = "每页条数必须大于0")
    @Max(value = 100, message = "每页条数不能超过100")
    private Long pageSize = 10L;

    /**
     * 分类ID，null 或 <=0 表示不按分类过滤
     */
    private Long categoryId;

    /**
     * 关键词，对标题与摘要做模糊匹配
     */
    @Size(max = 50, message = "搜索关键词长度不能超过50位")
    private String keyword;
}
