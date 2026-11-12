# WorkBuddy 施工提示词：文件上传能力（封面图 / 正文配图）

> 交付对象：WorkBuddy
> 前置状态：PHASE 0–5 已收口，`docker compose up` 三容器 healthy 已实测通过，前端 `USE_MOCK=false` 走真实请求
> 本文档只管一件事：**让管理员能把图片传上来，拿到一个能直接用的 URL**
> 前端已同步实现，契约以本文档第 2 节为准，服务端必须对齐

---

## 1. 背景：为什么现在做这个

`article.cover_image` 这个字段一直存在，但它是个纯字符串，前端只能让用户手填 URL。
实际使用时的路径是「先把图传到别的地方（图床 / 对象存储）→ 复制链接 → 粘贴」，
对个人博客来说这个前置动作完全是多余的。

同时前端刚上线了 Markdown 文档导入功能，导入的文章里图片通常是相对路径
（`![](./images/a.png)`），发布后一律裂图，也需要一个本地上传落点。

所以本阶段做两件事：

| 能力 | 用途 | 谁来做 |
| --- | --- | --- |
| 图片上传接口 + 静态访问 | 封面、正文配图 | **WorkBuddy（本文档）** |
| Markdown 文档导入解析 | 把本地 .md 搬进编辑器 | 已完成，纯前端 |

**不做**：文件管理页、对象存储（OSS/MinIO）、附件下载、图片裁剪、多文件批量上传。
理由见第 8 节，做了都是负收益。

---

## 2. 前端已实现的契约（服务端必须对齐）

前端代码位置：

- `frontend/src/api/files.js` —— 上传封装
- `frontend/src/views/admin/ArticleManage.vue` —— 封面字段的上传按钮、正文区的「从文件导入」
- `frontend/src/utils/docImport.js` —— Markdown 解析（纯前端，不涉及后端）

### 2.1 接口

```
POST /api/v1/admin/files
Content-Type: multipart/form-data
Authorization: Bearer <admin token>
```

表单字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `file` | binary | 是 | 文件本体，前端固定用这个字段名 |
| `scope` | string | 否 | 用途标记，`cover`=封面 / `image`=正文配图，缺省 `image` |

`scope` 目前只用于服务端做差异化校验（比如封面要求最小宽度），**不影响存储路径**。
服务端不认识的值一律按 `image` 处理，不要报错——前端以后可能加新值。

### 2.2 响应

成功：

```json
{
  "code": 200,
  "message": "上传成功",
  "data": {
    "url": "/uploads/2026/11/8f3a2b1c9d4e4f6a8b0c1d2e3f4a5b6c.png",
    "name": "cover.png",
    "size": 183421,
    "contentType": "image/png"
  },
  "timestamp": 1763000000000
}
```

字段要求：

- `url` **必须是相对路径**（以 `/` 开头），不要拼 `http://host:8080`。
  换域名、上 HTTPS、走 Nginx 反代时相对路径自动跟随；写死域名是运维事故的经典来源。
- `name` 原始文件名，仅用于前端提示，不参与存储路径。
- `size` 字节数。
- `contentType` 服务端**实际识别**的 MIME（按魔数判定，不是客户端声明的）。

失败：沿用 `GlobalExceptionHandler` 的 `Result` 包络，HTTP 状态码与 `code` 一致：

| 场景 | HTTP | message 示例 |
| --- | --- | --- |
| 未登录 / 非管理员 | 401 / 403 | 由 `SecurityConfig` 统一处理，不用自己写 |
| 没带 file 字段 | 400 | 请选择要上传的文件 |
| 空文件（0 字节） | 400 | 文件内容为空 |
| 格式不支持 | 400 | 仅支持 png / jpg / jpeg / gif / webp 格式 |
| 超过 5 MB | 400 | 图片大小不能超过 5 MB |
| 磁盘写入失败 | 500 | 文件保存失败，请稍后重试 |

**不要**返回 200 + 业务错误码，前端 `utils/request.js` 是按 HTTP 状态码分支处理的。

### 2.3 前端已做的前置校验（不是替代，是减少无效请求）

- 扩展名白名单 `.png .jpg .jpeg .gif .webp`
- 体积上限 5 MB

用户完全可以绕过页面直接 POST，所以服务端**必须重新校验**，且校验要基于文件内容而不是客户端声明。

---

## 3. 任务 A：上传接口实现

### A1 配置项

`application.yaml` 新增：

```yaml
spring:
  servlet:
    multipart:
      # 默认 1MB，不调的话传一张手机照片就 500，且报错信息完全看不出是大小问题
      max-file-size: 5MB
      max-request-size: 6MB

blogs:
  upload:
    # 容器里用 /app/uploads（挂 volume），本地开发默认 ./uploads
    dir: ${UPLOAD_DIR:./uploads}
    max-image-size: 5242880
```

`application-prod.yml` 同样加 `blogs.upload.dir: ${UPLOAD_DIR:/app/uploads}`，
或者直接用 `application.yaml` 的 `${UPLOAD_DIR:./uploads}` 也行，容器里注入环境变量即可。

**不要**把上传目录放进 `src/main/resources` 或 `target/` 下——打包成 jar 后那些路径不可写。

### A2 新增类

建议结构（与现有分层保持一致）：

```
blogs-common/src/main/java/com/xdl/blogscommon/file/
  FileStorageProperties.java       # @ConfigurationProperties("blogs.upload")
  ImageTypeDetector.java           # 魔数识别
  LocalFileStorage.java            # 落盘、路径生成

blogs-admin/src/main/java/com/xdl/blogsadmin/
  controller/AdminFileController.java
  config/WebMvcUploadConfig.java   # 静态资源映射
```

`FileVO` 放 `blogs-model/.../vo/FileVO.java`，与其它 VO 一致。

### A3 落盘规则

1. 生成 `UUID.randomUUID().toString().replace("-", "")` 作为文件名主体。
2. 扩展名**由魔数识别结果决定**，不取用户上传的文件名。
3. 按 `yyyy/MM` 分目录：`uploads/2026/11/<uuid>.png`。
   单目录堆几万个文件后 `ls` 和备份都会很难受，分片成本几乎为零。
4. 最终路径：`Paths.get(uploadDir, yyyy, MM, filename)`，全程用 `Path.resolve`，
   不要字符串拼 `"/"`。
5. 写完返回相对 URL：`/uploads/2026/11/<uuid>.png`。

### A4 魔数识别（重点）

**只允许 5 种图片，其余一律拒绝。**

| 格式 | 文件头（十六进制） |
| --- | --- |
| PNG | `89 50 4E 47 0D 0A 1A 0A` |
| JPEG | `FF D8 FF` |
| GIF | `47 49 46 38`（`GIF8`） |
| WEBP | `52 49 46 46` ... 第 8-11 字节 `57 45 42 50`（`RIFF....WEBP`） |

实现要点：

- 只读文件前 12 字节做判断，不要把整个文件读进内存。
- 校验顺序：先魔数 → 再体积 → 再落盘。魔数不通过时**不要**先写到临时目录再删，直接拒。
- 客户端的 `Content-Type` 和 `getOriginalFilename()` 的扩展名**都不可信**，
  改个后缀就是 `image/png`，两个都能伪造。

### A5 明确拒绝 SVG

`image/svg+xml` 是 XML，可以内嵌 `<script>` 和 `<foreignObject>`。
同域托管的 SVG 被直接打开时会以站点源执行脚本，等于给了一个存储型 XSS 入口。

个人博客没有必须支持 SVG 上传的场景，直接进黑名单。
如果以后真要做，唯一可接受的方案是上传后转成 PNG 再存，而不是原样落盘。

---

## 4. 任务 B：静态资源访问

### B1 资源映射

```java
@Configuration
public class WebMvcUploadConfig implements WebMvcConfigurer {

    private final FileStorageProperties properties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(properties.getDir())
                .toAbsolutePath()
                .normalize()
                .toUri()
                .toString();
        // 必须确保以 / 结尾，否则 Spring 会把它当文件而不是目录，访问一律 404
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location)
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
    }
}
```

用 `toUri().toString()` 而不是手拼 `"file:" + dir`：
Windows 下盘符路径拼出来是 `file:C:/...`，缺斜杠会失效；`toUri()` 已经处理了这些差异。

### B2 放行匿名访问（最容易漏的一步）

`SecurityConfig` 里现在是这样：

```java
.requestMatchers(HttpMethod.GET, PUBLIC_GET_ENDPOINTS).permitAll()
.requestMatchers(ADMIN_ENDPOINT_PATTERN).hasRole(ROLE_ADMIN)
.anyRequest().authenticated())
```

`/uploads/**` 不匹配任何一条，会落到 `anyRequest().authenticated()`，**访客端全部 401**。
封面在后台看着正常（浏览器带 token），退出登录一看全是裂图。

必须加：

```java
private static final String[] PUBLIC_STATIC_ENDPOINTS = {"/uploads/**"};

.requestMatchers(HttpMethod.GET, PUBLIC_STATIC_ENDPOINTS).permitAll()
```

**只放行 GET**。`POST /uploads/**` 之类不该存在，也不该放行。

### B3 安全响应头

静态资源建议加：

- `X-Content-Type-Options: nosniff` —— 防止浏览器把 `image/png` 猜成别的类型去执行
- `Content-Security-Policy: default-src 'none'`（可选）—— 图片响应里不应加载任何资源

Spring 的 `addResourceHandlers` 不支持直接加自定义头，用 `OncePerRequestFilter`
判断路径前缀 `/uploads/` 后 `response.setHeader(...)`，或直接在 Nginx 里配（更简单，见第 6 节）。

---

## 5. 任务 C：目录初始化与权限

### C1 启动时建目录

```java
@PostConstruct
void init() {
    Path dir = Paths.get(properties.getDir()).toAbsolutePath().normalize();
    Files.createDirectories(dir);
}
```

不要在 `LocalFileStorage` 的每个方法里判断"目录存不存在"，启动时建一次即可。

### C2 Docker 里的写权限（会直接导致 500）

`deploy/backend/Dockerfile` 现在以非 root 用户 `app` 运行：

```dockerfile
RUN groupadd -r app && useradd -r -g app -d /app app
...
USER app
```

如果 `UPLOAD_DIR=/app/uploads` 而 `/app/uploads` 由 root 创建（或者压根不存在、`app` 用户没权限建），
第一次上传就是 `AccessDeniedException` → 500，日志里只有一行 Java 异常，很容易误判成"代码问题"。

Dockerfile 必须补：

```dockerfile
RUN mkdir -p /app/uploads && chown -R app:app /app
```

（`chown` 放在 `USER app` **之前**。）

### C3 compose 挂卷（不做的话容器一重建图片全丢）

`deploy/docker-compose.yml` 的 backend 服务加：

```yaml
    volumes:
      - ./data/uploads:/app/uploads
    environment:
      UPLOAD_DIR: /app/uploads
```

并在 `.gitignore` 里加 `deploy/data/`（上传的图片不进版本库）。

---

## 6. 任务 D：Nginx 转发

`deploy/frontend/nginx.conf` 现在应该有 `location /api/ { proxy_pass http://backend:8080; }`。
`/uploads/` 不走这个前缀，需要单独一条：

```nginx
# 上传的图片：转发给后端静态资源处理器
location /uploads/ {
    proxy_pass http://backend:8080;
    proxy_set_header Host $host;
    expires 365d;
    add_header Cache-Control "public, immutable";
    add_header X-Content-Type-Options "nosniff";
}

# 请求体上限，默认 1MB：不调的话 5MB 的图会被 Nginx 直接 413，
# 后端 max-file-size 配了也白配，而且前端只看到 "网络异常"
client_max_body_size 6m;
```

`client_max_body_size` 必须放在 `server` 或 `http` 块，且要比后端 `max-request-size` 略大
（多出来的空间是 multipart 的边界与表单字段开销）。

> 进阶（本次不做）：让 Nginx 直接从共享卷读文件，不经过后端。
> 需要给 frontend 容器也挂同一个 volume，MVP 阶段没必要，多一层转发没有性能问题。

---

## 7. 任务 E：测试

### E1 单元测试（MockMvc，与现有测试风格一致）

至少覆盖：

1. 合法 PNG（用最小的 1x1 PNG 字节数组）→ 200，`data.url` 以 `/uploads/` 开头
2. 文件名伪装：`evil.txt` 内容是 PNG → 200，返回的 `url` 扩展名是 `.png`（以魔数为准）
3. 文件名伪装反向：`fake.png` 内容是纯文本 → 400
4. 0 字节 → 400
5. 超 5MB → 400（用 `MockMultipartFile` 造一个 5MB+1 字节的数组）
6. SVG → 400
7. 未带 token → 401
8. 普通用户 token → 403
9. 路径穿越：`getOriginalFilename()` 返回 `../../etc/passwd.png` → 落盘路径仍在 uploadDir 内

第 9 条要断言实际落盘路径，不能只看返回 200：
```java
Path saved = Paths.get(uploadDir).resolve(...);
assertTrue(saved.normalize().startsWith(Paths.get(uploadDir).toAbsolutePath().normalize()));
```

### E2 容器实测

```bash
docker compose up -d --build
# 造一张真图
python3 -c "import base64;open('/tmp/t.png','wb').write(base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=='))"
curl -s -X POST http://localhost/api/v1/admin/files \
  -H "Authorization: Bearer $TOKEN" -F "file=@/tmp/t.png" -F "scope=cover"
# 取返回的 url 再访问
curl -sI http://localhost/uploads/2026/11/<uuid>.png
```

最后一条要看到 `200` 且 `Content-Type: image/png`，**且不带 Authorization 头也要 200**。

---

## 8. 明确不做的事

| 不做 | 理由 |
| --- | --- |
| 文件表（`file` 表） | 文件只被 `article.cover_image` 以 URL 引用，没有独立生命周期。加了表就要处理"文章删了图还在""图删了文章引用空"两类一致性问题，MVP 收益为负 |
| OSS / MinIO / 七牛 | 单机个人博客，本地磁盘 + volume 足够。引入对象存储要多一套凭证管理、多一个故障点、多一份费用 |
| Markdown 解析接口 | 前端已实现（`utils/docImport.js`，40 项断言覆盖）。放服务端要引入 YAML 解析依赖、多一次网络往返，且文件内容最终还是要传给前端 |
| 附件上传（pdf/zip） | 没有下载页、没有权限分级，传上来就是裸文件目录。等真有"资源下载"需求再做，那时才需要 `file` 表 |
| 图片裁剪 / 压缩 / 生成缩略图 | 个人博客的封面是手动选的图，不是用户上传头像。裁剪是体验优化，不是功能缺口 |
| 批量上传 | 前端一次只处理一个文件，接口保持单文件语义。批量由前端循环调用即可，不需要 multipart 多文件 |

---

## 9. 十个坑

1. **`USER app` 之后写不了 root 拥有的目录** —— 上传 500，日志只有 Java 异常，`chown` 必须在 `USER` 之前。
2. **`/uploads/**` 没放行 → 访客端全裂图** —— 后台看着正常是因为浏览器带了 token。
3. **SVG 存储型 XSS** —— 同域托管的 SVG 可以直接执行脚本。
4. **只信 `Content-Type` 和扩展名** —— 改个后缀就能传任意文件，必须查魔数。
5. **`getOriginalFilename()` 可含 `../`** —— 直接拼路径就是任意文件写入。
6. **`spring.servlet.multipart` 默认 1MB** —— 手机照片普遍 3–8MB，不调就是满屏 500。
7. **Nginx `client_max_body_size` 默认 1MB** —— 后端配了也没用，前端只看到"网络异常"。
8. **compose 不挂 volume** —— `docker compose up -d` 重建容器，之前传的图全没了。
9. **上传目录放进 `resources` / `target`** —— 打成 jar 后不可写。
10. **`addResourceLocations` 路径末尾少 `/`** —— Spring 当文件处理，访问一律 404，且不报错。

前端侧还有一个对应的坑：**axios 传 `FormData` 时手动设 `Content-Type: multipart/form-data` 会丢 boundary**，
后端收到 `MultipartException`。当前 `api/files.js` 没有设，是对的，不要"顺手补上"。

---

## 10. 验收标准

全部满足才算完成：

- [ ] `POST /api/v1/admin/files` 上传合法 PNG，返回 `data.url` 以 `/uploads/` 开头
- [ ] 该 URL 在**未登录**的浏览器里能直接打开，`Content-Type: image/png`
- [ ] 改名的文本文件（`.txt` 内容不是图片）返回 400，不是 500
- [ ] 5MB+1 字节的图返回 400（或 Nginx 413），不是 500
- [ ] SVG 返回 400
- [ ] 0 字节文件返回 400
- [ ] 未带 token 返回 401，普通用户 token 返回 403
- [ ] 文件名含 `../../` 时不产生路径穿越，文件落在 `uploadDir` 内
- [ ] `docker compose restart backend` 之后，之前上传的图仍可访问（volume 生效）
- [ ] 前端管理端「新建文章 → 封面 → 上传」全流程可用，图片立即显示
- [ ] 上传后点「保存」，文章列表封面列点「查看封面」能打开
- [ ] 后端 27 个既有测试 + 新增上传测试全绿
- [ ] `deploy/README.md` 补充上传目录说明（挂载点、备份范围、容量预估）

---

## 11. 可复制提示词

```
实现文件上传能力（只做图片，封面 + 正文配图）。

现状：前端已实现并上线，前端调 POST /api/v1/admin/files，multipart 字段
file（必填）+ scope（cover/image，可缺省），期望返回
Result<FileVO>，FileVO 含 url/name/size/contentType，url 必须是
以 /uploads/ 开头的相对路径。

要做的：
1. 配置 spring.servlet.multipart.max-file-size=5MB、max-request-size=6MB；
   新增 blogs.upload.dir（默认 ${UPLOAD_DIR:./uploads}）、blogs.upload.max-image-size。
2. 新增 FileStorageProperties、ImageTypeDetector（魔数识别 PNG/JPEG/GIF/WEBP）、
   LocalFileStorage（UUID 文件名 + 按 yyyy/MM 分目录 + Path.resolve 落盘）、
   AdminFileController、FileVO、WebMvcUploadConfig。
3. 静态资源映射 /uploads/** 到 uploads 目录，路径必须用 toUri().toString()
   并确保以 / 结尾；缓存 365 天 immutable。
4. SecurityConfig 加 GET /uploads/** permitAll —— 不加的话访客端所有图片 401。
5. 拒绝 SVG（存储型 XSS），拒绝 0 字节，只按魔数判定类型，
   扩展名由魔数决定而不是原始文件名。
6. backend Dockerfile 在 USER app 之前 mkdir -p /app/uploads && chown -R app:app /app；
   docker-compose backend 加 volume ./data/uploads:/app/uploads 和
   environment UPLOAD_DIR=/app/uploads；.gitignore 加 deploy/data/。
7. nginx.conf 加 location /uploads/ 转发到 backend，并加 client_max_body_size 6m。
8. 写 MockMvc 测试覆盖第 7 节 E1 的 9 条，特别是路径穿越要断言实际落盘路径。
9. 跑 docker compose up -d --build，按第 10 节验收标准逐条验。

不要做：文件表、对象存储、Markdown 解析接口、附件上传、图片裁剪、批量上传。
不要返回 200 + 业务错误码，失败必须用对应 HTTP 状态码 + Result 包络。
不要在 url 里拼域名，必须是相对路径。
```

---

## 12. 前端部分已完成的部分（供对照，无需重复实现）

| 文件 | 内容 |
| --- | --- |
| `frontend/src/utils/docImport.js` | Markdown 解析：front matter（行内数组 / 多行列表 / 未闭合容错 / 引号）、标题三级兜底（front matter → 首个 H1 → 文件名）、摘要语法剥离、相对路径图片计数、编码回退（UTF-8 严格 → GBK） |
| `frontend/scripts/doc-import-test.mjs` | 40 项断言，`npm run test:doc` |
| `frontend/src/api/files.js` | `uploadFile(file, {scope})`，mock 模式下生成本地 blob URL 供预览 |
| `frontend/src/views/admin/ArticleManage.vue` | 工具栏「导入文档」按钮 + 导入预览弹窗（标题/摘要/正文行数/标签匹配情况/警告列表/正文预览）、「填入编辑器」与「直接存为草稿」两个出口；封面字段改为「URL 输入 + 上传按钮 + 预览」；正文区「从文件导入」 |

前端在 mock 模式下上传只生成 `blob:` URL，刷新页面即失效——这是预期行为，
真实持久化必须等本施工单完成后把 `VITE_USE_MOCK=false`。
