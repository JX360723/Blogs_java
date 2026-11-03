package com.xdl.blogsadmin.unit;

import com.xdl.model.vo.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分页结构计算测试
 * <p>
 * MyBatis-Plus 返回的是 records / current / size，前端要的是 list / pageNum / pageSize / pages，
 * 转换在 PageResult.of() 里完成。pages 的向上取整算错会让前端分页少一页。
 *
 * @author xdl
 */
@DisplayName("分页结构")
class PageResultTest {

    @Test
    @DisplayName("整除：25 条 / 每页 5 条 = 5 页")
    void exactDivision() {
        PageResult<String> result = PageResult.of(List.of("a"), 25, 1, 5);

        assertEquals(25, result.getTotal());
        assertEquals(1, result.getPageNum());
        assertEquals(5, result.getPageSize());
        assertEquals(5, result.getPages());
    }

    @Test
    @DisplayName("向上取整：25 条 / 每页 10 条 = 3 页")
    void roundUp() {
        PageResult<String> result = PageResult.of(List.of("a"), 25, 1, 10);

        assertEquals(3, result.getPages(), "25 条按每页 10 条应算出 3 页，不能是 2 页");
    }

    @Test
    @DisplayName("总数 0：0 页，列表为空数组而不是 null")
    void emptyResult() {
        PageResult<String> result = PageResult.of(null, 0, 1, 10);

        assertEquals(0, result.getPages());
        assertTrue(result.getList().isEmpty(), "list 不能为 null，否则前端 table 会报错");
    }

    @Test
    @DisplayName("pageSize 为 0：不抛除零异常，页数按 0 处理")
    void zeroPageSize() {
        PageResult<String> result = PageResult.of(List.of("a"), 10, 1, 0);

        assertEquals(0, result.getPages());
    }
}
