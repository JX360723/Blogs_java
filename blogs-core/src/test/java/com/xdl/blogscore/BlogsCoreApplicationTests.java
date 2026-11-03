package com.xdl.blogscore;

import com.xdl.model.vo.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分页边界测试
 * <p>
 * 原为空壳 contextLoads，业务模块没有可启动的上下文。
 * 这里覆盖 MyBatis-Plus 分页转前端结构时最容易算错的边界：
 * 单页只有 1 条、总数刚好等于 pageSize、超大总数。
 *
 * @author xdl
 */
@DisplayName("分页边界")
class BlogsCoreApplicationTests {

    @Test
    @DisplayName("每页 1 条：7 条数据 = 7 页")
    void pageSizeOne() {
        PageResult<String> result = PageResult.of(List.of("a"), 7, 1, 1);
        assertEquals(7, result.getPages());
    }

    @Test
    @DisplayName("总数恰好等于每页条数：只应算出 1 页，不能多出一页空白页")
    void totalEqualsPageSize() {
        PageResult<String> result = PageResult.of(List.of("a"), 10, 1, 10);
        assertEquals(1, result.getPages());
    }

    @Test
    @DisplayName("超大总数：不能溢出成负数页数")
    void largeTotal() {
        PageResult<String> result = PageResult.of(List.of("a"), 5_000_000_000L, 1, 10);
        assertEquals(500_000_000L, result.getPages());
        assertTrue(result.getPages() > 0, "页数溢出为负会让前端分页器直接崩");
    }
}
