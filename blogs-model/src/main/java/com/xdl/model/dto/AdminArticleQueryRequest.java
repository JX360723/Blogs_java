package com.xdl.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 管理端文章列表查询参数
 * <p>
 * 对应 docs/API_CONTRACT.md 6.2.3「GET /api/v1/admin/articles」：分页、含草稿与已下架、支持 status 筛选。
 * <p>
 * <b>为什么必须与公开端的 {@link ArticleQueryRequest} 分成两个类</b>：
 * 公开接口的查询参数一旦带上 {@code status}，任何人都能请求
 * {@code GET /api/v1/articles?status=0} 把草稿拉出来——绕过「公开只暴露已发布」的隔离。
 * 两个端点的参数集合与语义本就不同（公开端恒等于 status=1，管理端可任意筛选），
 * 共用一个类迟早会把 status 泄进公开查询。故此处独立定义，公开类保持无 status 字段。
 * <p>
 * {@code status} 允许为 null，表示「全部状态」；前端「全部」页签不传该参数。
 *
 * @author xdl
 */
@Data
public class AdminArticleQueryRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 页码，从 1 开始
     */
    @Min(value = 1, message = "页码必须大于0")
    private Long pageNum = 1L;

    /**
     * 每页条数，最大 100（与 MybatisPlusConfig 的 maxLimit 一致）
     */
    @Min(value = 1, message = "每页条数必须大于0")
    @Max(value = 100, message = "每页条数不能超过100")
    private Long pageSize = 10L;

    /**
     * 文章状态筛选：0-草稿，1-已发布，2-已下架；null 表示全部
     * <p>
     * 注意与前端约定的差异：前端「全部」页签传的是空串 {@code status=}，
     * Spring 会把空串绑定成 null，正好落到「全部状态」。
     */
    @Min(value = 0, message = "状态筛选值非法，只能为0/1/2")
    @Max(value = 2, message = "状态筛选值非法，只能为0/1/2")
    private Integer status;

    /**
     * 分类筛选，null 或 <=0 表示不筛选
     */
    private Long categoryId;

    /**
     * 标题/摘要关键词，便于管理端快速定位文章
     */
    @Size(max = 50, message = "搜索关键词长度不能超过50位")
    private String keyword;
}
