package com.xdl.blogsadmin.web;

import com.xdl.blogsadmin.controller.AdminArticleController;
import com.xdl.bologssecurity.config.SecurityConfig;
import com.xdl.bologssecurity.config.SecurityCorsProperties;
import com.xdl.bologssecurity.filter.JwtAuthenticationFilter;
import com.xdl.bologssecurity.handler.RestSecurityExceptionHandler;
import com.xdl.blogscommon.jwt.JwtPrincipal;
import com.xdl.blogscommon.jwt.JwtTokenProvider;
import com.xdl.blogscore.service.ArticleService;
import com.xdl.model.vo.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理端接口鉴权测试（MockMvc）
 * <p>
 * 保护的核心约定：{@code /api/v1/admin/**} 的权限由<b>服务端</b>强制，前端路由守卫不算安全措施。
 * <ul>
 *   <li>不带 token → 401，且请求根本不该进到业务层</li>
 *   <li>带非管理员 token → 403，同样不该进业务层</li>
 *   <li>带管理员 token → 200</li>
 * </ul>
 * 只断言 HTTP 状态码，不断言响应体文案：文案属于展示层，改动不该让安全测试变红；
 * 真正要锁死的是「拦不拦得住」。响应包络格式由 RestSecurityExceptionHandlerTest 覆盖。
 * <p>
 * 用 {@code @WebMvcTest} 而不是 {@code @SpringBootTest}：后者会拉起 DataSource，
 * 测试就得依赖本机 MySQL。这里用 {@code @MockBean} 顶掉 ArticleService 与 JwtTokenProvider，
 * 只加载 Web 层 + 安全过滤器链。
 *
 * @author xdl
 */
@WebMvcTest(controllers = AdminArticleController.class)
@AutoConfigureMockMvc
// 显式指定切片上下文：BlogsApplication 上有 @ComponentScan("com.xdl")，
// 会让 @WebMvcTest 把全部 Service / Mapper 也扫进来，而 Mapper 没有 sqlSessionFactory 直接起不来。
// 这里只装 Web 层需要的 4 个 Bean，DataSource 与 MyBatis 完全不参与。
@ContextConfiguration(classes = {AdminArticleController.class, SecurityConfig.class,
        JwtAuthenticationFilter.class, RestSecurityExceptionHandler.class, SecurityCorsProperties.class})
@DisplayName("管理端接口鉴权")
class AdminArticleSecurityTest {

    private static final String ADMIN_LIST_URL = "/api/v1/admin/articles";

    private static final String BEARER_PREFIX = "Bearer ";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ArticleService articleService;

    /**
     * 过滤器链依赖它解析 token；真实实现需要 JWT_SECRET 且会验签，
     * 这里用 mock 直接返回登录主体，把测试焦点放在「授权判定」而不是密码学上。
     */
    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("不带 token：401，且不进入业务层")
    void withoutTokenShouldBeUnauthorized() throws Exception {
        mockMvc.perform(get(ADMIN_LIST_URL))
                .andExpect(status().isUnauthorized());

        // 安全第一原则：未认证请求不能触达 Service，否则一旦过滤器顺序被改错，
        // 数据已经在返回路上了才发现漏权限，为时已晚
        verify(articleService, never()).pageForAdmin(any());
    }

    @Test
    @DisplayName("非管理员 token：403，且不进入业务层")
    void withNonAdminTokenShouldBeForbidden() throws Exception {
        given(jwtTokenProvider.parseToken("user-token"))
                .willReturn(new JwtPrincipal(2L, "writer", "USER"));

        mockMvc.perform(get(ADMIN_LIST_URL).header("Authorization", BEARER_PREFIX + "user-token"))
                .andExpect(status().isForbidden());

        verify(articleService, never()).pageForAdmin(any());
    }

    @Test
    @DisplayName("管理员 token：200")
    void withAdminTokenShouldBeOk() throws Exception {
        given(jwtTokenProvider.parseToken("admin-token"))
                .willReturn(new JwtPrincipal(1L, "admin", "ADMIN"));
        given(articleService.pageForAdmin(any()))
                .willReturn(PageResult.of(List.of(), 0L, 1L, 10L));

        mockMvc.perform(get(ADMIN_LIST_URL).header("Authorization", BEARER_PREFIX + "admin-token"))
                .andExpect(status().isOk());
    }
}
