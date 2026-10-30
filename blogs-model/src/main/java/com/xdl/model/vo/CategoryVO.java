package com.xdl.model.vo;

import com.xdl.model.entity.Category;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 分类视图对象
 * <p>
 * 对应 docs/BACKEND_DESIGN_DB_AND_API.md 4.5「GET /api/v1/categories」：
 * 返回<b>扁平数组</b>（非分页），按 {@code sortOrder} 升序。
 * 不返回 {@code createdAt} / {@code updatedAt} / {@code deleted}，前端分类栏用不到。
 *
 * @author xdl
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 分类ID
     */
    private Long id;

    /**
     * 分类名称
     */
    private String name;

    /**
     * 父分类ID，0 表示顶级
     */
    private Long parentId;

    /**
     * 分类描述
     */
    private String description;

    /**
     * 排序权重，数值越小越靠前
     */
    private Integer sortOrder;

    /**
     * 由实体转换为视图对象
     *
     * @param category 分类实体
     * @return 分类视图
     */
    public static CategoryVO from(Category category) {
        return CategoryVO.builder()
                .id(category.getId())
                .name(category.getName())
                .parentId(category.getParentId() == null ? 0L : category.getParentId())
                .description(category.getDescription())
                .sortOrder(category.getSortOrder() == null ? 0 : category.getSortOrder())
                .build();
    }
}
