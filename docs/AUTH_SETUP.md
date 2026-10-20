# 管理员登录：环境准备与凭证维护

> 对应实现：`docs/API_CONTRACT.md` 6.2.1（`POST /api/v1/auth/login`，已冻结）与 6.2.2（`GET /api/v1/auth/me`，已定稿）。

## 1. 必备环境变量

| 变量 | 必填 | 说明 |
| --- | --- | --- |
| `JWT_SECRET` | 是 | HS256 签名密钥，**不少于 32 字节**。缺失或过短时应用启动直接失败，不会以弱密钥对外服务。禁止写入代码仓库、数据库与日志。 |
| `ADMIN_INIT_PASSWORD` | 首次初始化时需要 | 首个管理员 `admin` 的初始密码（不少于 6 位）。仅在 `admin_user` 表为空时使用，服务端以 bcrypt 哈希写库，不保存明文。 |

PowerShell 设置示例（当前会话生效）：

```powershell
$env:JWT_SECRET = "请替换为至少32字节的随机字符串"
$env:ADMIN_INIT_PASSWORD = "请替换为初始管理员密码"
```

## 2. 构建与启动

构建需要 **JDK 21**（`pom.xml` 中 `java.version=21`）。若 `JAVA_HOME` 指向更低版本，编译会报「不支持发行版本 21」。

```powershell
$env:JAVA_HOME = "<本机 JDK 21 路径>"
$env:JWT_SECRET = "<至少 32 字节的随机字符串>"
$env:ADMIN_INIT_PASSWORD = "<初始管理员密码>"
mvn clean package -Dmaven.test.skip=true
java -jar blogs-admin/target/blogs-admin-0.0.1-SNAPSHOT.jar
```

两点注意：

- 这里用 `-Dmaven.test.skip=true` 而非 `-DskipTests`：各子模块的脚手架测试（`*ApplicationTests`）缺少 `spring-boot-starter-test` 依赖，`-DskipTests` 仍会编译测试代码并失败（既有问题，与登录实现无关）。
- 根工程是 `pom` 聚合模块，`mvn -pl blogs-admin -am spring-boot:run` 会在根工程上执行 run 目标并报「Unable to find a suitable main class」，因此改用 `java -jar` 启动已打包的 `blogs-admin`。

## 3. 数据库与首个管理员

- 建表脚本：`blogs-admin/src/main/resources/db/schema.sql`（`CREATE TABLE IF NOT EXISTS`，可重复执行）。
- `dev` profile 下由 `spring.sql.init` 自动执行；生产环境请手动执行或改用 Flyway/Liquibase 管理版本。
- `AdminUserInitializer` 在启动时检查 `admin_user` 表：表为空且设置了 `ADMIN_INIT_PASSWORD` 则创建 `admin`（角色 `ADMIN`、状态启用）；未设置该变量时只打印告警并跳过，不写入任何默认密码。

## 4. 凭证维护

- **修改管理员密码**：用新的 bcrypt 哈希更新 `admin_user.password`，禁止写入明文或可逆密文。
- **禁用账号**：将 `admin_user.status` 置 0；该账号密码正确也无法登录，返回 403 `账号已被禁用`。
- **轮换 `JWT_SECRET`**：轮换后所有已签发 token 立即失效，需重新登录（MVP 不做刷新 token 与服务端登出）。
- **删除账号**：使用逻辑删除（`deleted=1`），登录查询自动过滤。

## 5. 接口速览

| 方法/路径 | 权限 | 说明 |
| --- | --- | --- |
| `POST /api/v1/auth/login` | 公开 | 成功返回 `token` / `tokenType=Bearer` / `expiresIn=86400` / `userInfo` |
| `GET /api/v1/auth/me` | 需登录 | 返回当前管理员信息，用于前端刷新后恢复登录态 |
| `/api/v1/admin/**` | 管理员 | 未认证返回 401，非 `ADMIN` 角色返回 403 |

失败响应（HTTP 状态码与业务 `code` 一致）：401 `用户名或密码错误`（用户不存在与密码错误同文案，防账号枚举）、403 `账号已被禁用`、400 具体字段校验信息。
