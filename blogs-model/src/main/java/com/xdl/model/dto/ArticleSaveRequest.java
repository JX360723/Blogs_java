package com.xdl.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 管理端文章创建/更新请求体
 * <p>
 * 对应 docs/API_CONTRACT.md 6.2.3 的创建/更新请求体。
 * 前端 {@code ArticleManage.vue} 保存时提交的就是这份结构
 * （PUT 请求里还会多带一个 {@code id}，由 Jackson 忽略未知字段处理）。
 * <p>
 * 刻意<b>不接收</b>以下字段，防止前端越权篡改：
 * <ul>
 *   <li>{@code userId}：作者一律取当前登录管理员的 id</li>
 *   <li>{@code createdAt} / {@code updatedAt}：由 MybatisMetaObjectHandler 自动填充</li>
 *   <li>{@code viewCount}：新建时固定 0，更新时不做变更</li>
 *   <li>{@code contentHtml}：由后端根据 {@code contentMd} 渲染生成</li>
 * </ul>
 *
 * @author xdl
 */
@Data
// 前端保存提交的是整个 form 对象，会多带一个 id（新建时为 null）。
// 显式忽略未知字段，避免多出字段就 400 —— 同时反向保证：即便前端误传
// userId / viewCount / contentHtml，也进不了实体，不会被越权篡改。
@JsonIgnoreProperties(ignoreUnknown = true)
public class ArticleSaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 标题，必填，与表字段 title VARCHAR(120) 对齐
     */
    @NotBlank(message = "标题不能为空")
    @Size(max = 120, message = "标题长度不能超过120位")
    private String title;

    /**
     * 摘要，可选，与表字段 summary VARCHAR(500) 对齐
     */
    @Size(max = 500, message = "摘要长度不能超过500位")
    private String summary;

    /**
     * Markdown 正文
     * <p>
     * 表字段为 LONGTEXT，这里给出 20 万字符上限，纯粹是防止误贴超大文本把服务撑爆，
     * 正常文章远达不到该量级。
     */
    @Size(max = 200000, message = "正文长度不能超过200000字符")
    private String contentMd;

    /**
     * 所属分类ID，可为 null（未分类）
     */
    private Long categoryId;

    /**
     * 封面图URL，与表字段 cover_image VARCHAR(255) 对齐
     */
    @Size(max = 255, message = "封面地址长度不能超过255位")
    private String coverImage;

    /**
     * 是否置顶：0-否，1-是；不传时按 0 处理
     */
    @Min(value = 0, message = "置顶标识只能为0或1")
    @Max(value = 1, message = "置顶标识只能为0或1")
    private Integer isTop;

    /**
     * 文章状态：0-草稿，1-已发布，2-已下架
     * <p>
     * 必填。契约 6.2.3 明确「非法值返回 400」，用 @Min/@Max 在参数边界拦下，
     * 避免把 3、-1 之类的值写进库导致公开接口按 status=1 永远查不到它。
     */
    @NotNull(message = "文章状态不能为空")
    @Min(value = 0, message = "文章状态值非法，只能为0（草稿）/1（已发布）/2（已下架）")
    @Max(value = 2, message = "文章状态值非法，只能为0（草稿）/1（已发布）/2（已下架）")
    private Integer status;

    /**
     * 标签ID列表
     * <p>
     * 三态语义（详见 ArticleServiceImpl#syncArticleTags）：
     * <ul>
     *   <li>字段不传 / 传 null：<b>不修改</b>已绑定标签，保留原样。
     *       编辑页若没有动标签选择器，前端可能只提交标题等字段，此时不能把标签清空</li>
     *   <li>传空数组 {@code []}：<b>清空</b>该文章的全部标签</li>
     *   <li>传非空数组：全量覆盖（先删后插），库里已不存在的 tagId 会被过滤掉，
     *       去重后超过 10 个返回 400</li>
     * </ul>
     * 不加 {@code @NotNull}：不传就代表「不动标签」，是合法请求。
     */
    private List<Long> tagIds;
}
