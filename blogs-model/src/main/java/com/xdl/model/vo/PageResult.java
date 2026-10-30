package com.xdl.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 统一分页响应结构
 * <p>
 * 对应 docs/BACKEND_DESIGN_DB_AND_API.md 4.3 分页规范。
 * MyBatis-Plus 的 {@code IPage} 字段是 {@code records / current / size}，
 * 与前端约定的 {@code list / pageNum / pageSize} 不一致，必须在 Service 层完成转换，
 * 不允许把 IPage 直接返回给前端，否则前端分页拿不到数据。
 *
 * @param <T> 列表元素类型
 * @author xdl
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 当前页数据
     */
    private List<T> list;

    /**
     * 总记录数
     */
    private long total;

    /**
     * 当前页码，从 1 开始
     */
    private long pageNum;

    /**
     * 每页条数
     */
    private long pageSize;

    /**
     * 总页数
     */
    private long pages;

    /**
     * 构造分页响应，总页数由 total 与 pageSize 计算得出
     *
     * @param list     当前页数据
     * @param total    总记录数
     * @param pageNum  当前页码
     * @param pageSize 每页条数
     * @param <T>      列表元素类型
     * @return 前端约定的分页结构
     */
    public static <T> PageResult<T> of(List<T> list, long total, long pageNum, long pageSize) {
        long safePageSize = pageSize <= 0 ? 0 : pageSize;
        long pages = safePageSize == 0 ? 0 : (total + safePageSize - 1) / safePageSize;
        return PageResult.<T>builder()
                .list(list == null ? List.of() : list)
                .total(total)
                .pageNum(pageNum)
                .pageSize(pageSize)
                .pages(pages)
                .build();
    }
}
