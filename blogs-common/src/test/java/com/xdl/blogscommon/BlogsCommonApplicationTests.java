package com.xdl.blogscommon;

import com.xdl.blogscommon.result.Result;
import com.xdl.blogscommon.result.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 统一响应包络测试
 * <p>
 * 原为 Spring Initializr 生成的空壳 contextLoads，库模块没有可启动的上下文，
 * 改成直接验证 Result 包络的字段约定（契约第 2 节：code / message / data / timestamp）。
 * 字段名必须是 message 而不是 msg，前端 request.js 依赖这个字段名取错误提示。
 *
 * @author xdl
 */
@DisplayName("统一响应包络")
class BlogsCommonApplicationTests {

    @Test
    @DisplayName("成功响应：code=200、message 非空、data 为传入值、timestamp 为毫秒时间戳")
    void successEnvelope() {
        long before = System.currentTimeMillis();
        Result<String> result = Result.success("payload");
        long after = System.currentTimeMillis();

        assertEquals(ResultCode.SUCCESS.getCode(), result.getCode());
        assertNotNull(result.getMessage());
        assertEquals("payload", result.getData());
        assertTrue(result.getTimestamp() >= before && result.getTimestamp() <= after,
                "timestamp 应为服务端毫秒时间戳");
    }

    @Test
    @DisplayName("失败响应：data 必须为 null，message 为可直接展示的文案")
    void errorEnvelope() {
        Result<String> result = Result.error(ResultCode.NOT_FOUND, "文章不存在或未发布");

        assertEquals(404, result.getCode());
        assertEquals("文章不存在或未发布", result.getMessage());
        assertNull(result.getData(), "失败响应 data 必须为 null，否则前端会误判为有数据");
    }
}
