package com.xdl.blogscore.init;

import com.xdl.blogscore.service.AdminUserService;
import com.xdl.model.entity.AdminUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 首个管理员初始化器
 * <p>
 * 对应契约 6.2.1「后端落地要求」第 2 条（K2）：
 * 表中无管理员时，用环境变量 ADMIN_INIT_PASSWORD 的 bcrypt 哈希创建账号 admin；
 * 密码不硬编码、不入库明文、不写日志。未设置该环境变量时仅告警并跳过。
 *
 * @author xdl
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminUserInitializer implements ApplicationRunner {

    /**
     * 初始管理员用户名
     */
    private static final String INIT_ADMIN_USERNAME = "admin";

    /**
     * 初始管理员昵称
     */
    private static final String INIT_ADMIN_NICKNAME = "系统管理员";

    /**
     * 初始密码环境变量名
     */
    private static final String INIT_PASSWORD_PROPERTY = "ADMIN_INIT_PASSWORD";

    private final AdminUserService adminUserService;

    private final PasswordEncoder passwordEncoder;

    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        if (adminUserService.count() > 0) {
            return;
        }
        String initPassword = environment.getProperty(INIT_PASSWORD_PROPERTY);
        if (!StringUtils.hasText(initPassword)) {
            log.warn("admin_user 表为空且未设置环境变量 {}，跳过管理员初始化；"
                    + "请设置该环境变量（不少于 6 位）后重启，以创建首个管理员账号 {}",
                    INIT_PASSWORD_PROPERTY, INIT_ADMIN_USERNAME);
            return;
        }
        AdminUser adminUser = AdminUser.builder()
                .username(INIT_ADMIN_USERNAME)
                .password(passwordEncoder.encode(initPassword))
                .nickname(INIT_ADMIN_NICKNAME)
                .role(AdminUser.ROLE_ADMIN)
                .status(AdminUser.STATUS_ENABLED)
                .deleted(0)
                .build();
        adminUserService.save(adminUser);
        log.info("已创建首个管理员账号: {}", INIT_ADMIN_USERNAME);
    }
}
