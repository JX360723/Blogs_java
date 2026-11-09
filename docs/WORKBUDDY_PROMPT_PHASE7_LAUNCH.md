# PHASE 7 施工单：上线部署与运维交付

> 交付对象：运维/后端开发（WorkBuddy）
> 基线：PHASE 6 完成后的 `main`（联调 42 条全 PASS）
> 前置条件：整栈本地跑通、无 P0/P1 缺陷
> 目标：**公网可访问、HTTPS 有效、数据可恢复、故障可自愈**，产出可写进简历的上线成果

## 一、这一阶段要拿到什么

PHASE 5 解决「本机能跑」，PHASE 7 解决「别人能访问、坏了能修、数据不会丢」。

这是项目从「课程作业」到「真实产品」的最后一步。做完之后你应该能回答面试官的三个问题：

1. 你的项目部署在哪？—— 有公网地址，能现场打开
2. 用户量涨了怎么办？—— 有资源监控数据和扩容方案
3. 数据丢了怎么办？—— 有备份策略和恢复演练记录

## 二、任务分解

### A1. 服务器与网络

**规格建议**：2 vCPU / 4 GiB / 40 GiB SSD（个人博客足够，MySQL + 后端 + Nginx 三个容器常驻约 1.5 GiB）

**要做**：
- 系统盘用 Ubuntu 22.04 LTS 或 Debian 12
- 创建非 root 运维账号，`sudo` 提权，禁用 root 密码登录
- SSH 改用密钥认证，`PasswordAuthentication no`
- 安全组只开 22 / 80 / 443，**MySQL 3306 绝对不能对公网开放**
- 安装 Docker Engine + Compose V2（用官方脚本，不要用发行版自带的老版本）
- 配置 Docker 日志轮转（见 A6）

**验收**：`ssh` 密钥能登、密码不能登、`nmap` 扫只有 22/80/443 开放。

### A2. 域名与 DNS

- 一个域名，A 记录指向服务器公网 IP
- 建议 `www` 也解析（用 CNAME 或另一条 A）
- **备案**：国内服务器必须完成 ICP 备案才能用 80/443 提供网站服务；香港/海外服务器不需要备案但延迟高
- 未备案前的临时方案：用非标端口（如 8080）访问，但这不是长期方案

**验收**：`dig 域名` 返回正确 IP，`ping` 通。

### A3. HTTPS

用 Let's Encrypt + certbot：

```bash
sudo apt install certbot python3-certbot-nginx
sudo certbot --nginx -d example.com -d www.example.com
```

**注意**：
- certbot 需要 80 端口可访问才能做 HTTP-01 验证
- 如果 Nginx 跑在容器里，certbot 的 `--nginx` 插件改不到容器内的配置，改用 `--webroot` 模式，把 `.well-known/acme-challenge` 目录挂进容器
- 续期：`certbot renew --dry-run` 验证自动续期可用，证书有效期 90 天

Nginx 侧必须加：

```nginx
# HTTP 强制跳 HTTPS
server {
    listen 80;
    server_name example.com www.example.com;
    location /.well-known/acme-challenge/ { root /var/www/certbot; }
    location / { return 301 https://$host$request_uri; }
}
```

**安全响应头**（加分项，也是面试考点）：

```nginx
add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
add_header X-Content-Type-Options "nosniff" always;
add_header X-Frame-Options "SAMEORIGIN" always;
add_header Referrer-Policy "strict-origin-when-cross-origin" always;
```

**验收**：`https://域名` 能访问，SSLLabs 评级 A 以上，HTTP 自动跳 HTTPS。

### A4. 生产配置收紧

- `SPRING_PROFILES_ACTIVE=prod`（PHASE 5 已配）
- MySQL root 密码、应用数据库密码、`ADMIN_INIT_PASSWORD` 全部用强随机（≥ 16 位），只存在服务器 `.env`，权限 `chmod 600`
- **不要**把 `.env` 提交到仓库
- MySQL 只监听容器网络，不 `ports: 3306:3306` 暴露到宿主机
- 后端端口也不要暴露到宿主机，只让 Nginx 通过容器网络访问
- 关闭 actuator 的敏感端点，只留 `health`：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: never
```

**验收**：`curl 公网IP:3306` 不通、`curl 公网IP:8080/actuator/env` 404、`/actuator/health` 正常返回。

### A5. 数据备份

**策略**：每日全量 + 保留 7 天，异地存一份。

```bash
# /opt/blogs/backup.sh
#!/bin/bash
set -e
BACKUP_DIR=/opt/blogs/backups
DATE=$(date +%Y%m%d_%H%M%S)
mkdir -p "$BACKUP_DIR"
docker exec blogs-mysql mysqldump \
  -u root -p"$MYSQL_ROOT_PASSWORD" \
  --single-transaction --routines --triggers \
  "$MYSQL_DATABASE" | gzip > "$BACKUP_DIR/blogs_$DATE.sql.gz"
# 删除 7 天前的
find "$BACKUP_DIR" -name "blogs_*.sql.gz" -mtime +7 -delete
```

crontab：

```
0 3 * * * /opt/blogs/backup.sh >> /var/log/blogs-backup.log 2>&1
```

**关键**：`--single-transaction` 保证 InnoDB 备份一致性，不锁表。

**恢复演练（必须做一次）**：

```bash
gunzip -c blogs_20261110_030000.sql.gz | docker exec -i blogs-mysql \
  mysql -u root -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"
```

**验收**：备份文件每天生成、能成功恢复到干净数据库、恢复后数据完整。**没做过恢复演练的备份等于没有备份。**

### A6. 日志与资源

**Docker 日志轮转**（否则磁盘会被日志写满）：

```yaml
# docker-compose.yml 每个服务加
logging:
  driver: json-file
  options:
    max-size: "10m"
    max-file: "3"
```

**资源限制**：

```yaml
deploy:
  resources:
    limits:
      memory: 512M
```

按服务分配：mysql 768M、backend 768M、frontend 128M。

**验收**：`docker stats` 查看各容器内存占用，不超限；`du -sh /var/lib/docker` 稳定不增长。

### A7. 自愈与监控

- 所有服务 `restart: unless-stopped`（服务器重启后自动拉起）
- 健康检查（PHASE 5 已配 backend / mysql）
- 简单可用性监控：外部定时 `curl https://域名/api/v1/articles?pageNum=1&pageSize=1`，失败告警
- 免费方案：UptimeRobot / 阿里云云监控

**验收**：`sudo reboot` 后 3 分钟内服务自动恢复，无需人工介入。

### A8. CI/CD（可选但推荐）

GitHub Actions：

```yaml
# .github/workflows/deploy.yml
on:
  push:
    branches: [main]
jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Deploy to server
        uses: appleboy/ssh-action@v1
        with:
          host: ${{ secrets.SSH_HOST }}
          username: ${{ secrets.SSH_USER }}
          key: ${{ secrets.SSH_KEY }}
          script: |
            cd /opt/blogs
            git pull origin main
            docker compose -f deploy/docker-compose.yml up -d --build
```

**注意**：SSH 私钥放 GitHub Secrets，不要写进仓库。CI 里跑测试作为合并门禁更佳。

**验收**：push 到 main 后自动部署，页面更新。

## 三、坑点

1. **国内服务器不备案不能用 80/443** —— 会直接被拦截，别以为是配置问题
2. **`certbot --nginx` 改不到容器内配置** —— 用 `--webroot` 或把证书挂进容器
3. **MySQL 端口别暴露** —— `ports: 3306:3306` 是最常见的低级安全事故
4. **Docker 日志会吃满磁盘** —— 不配轮转，几周后磁盘 100%
5. **`.env` 权限** —— `chmod 600`，否则同机其他用户可读
6. **备份没恢复过 = 没备份** —— 必须做一次真实恢复演练
7. **`restart: unless-stopped` 不等于自愈** —— 进程活着但接口 500 时不会重启，需要健康检查
8. **时间时区** —— 容器默认 UTC，MySQL 和后端都要设 `Asia/Shanghai`，否则文章时间差 8 小时
9. **HTTPS 证书 90 天到期** —— 配好自动续期并验证 `--dry-run`
10. **`git pull` 后不 `--build`** —— 代码变了镜像没变，页面还是旧的

## 四、交付物

1. 公网可访问地址（`https://域名`）
2. `deploy/OPERATIONS.md` 运维手册：
   - 服务器规格与架构图
   - 首次部署完整步骤
   - 日常运维命令（查看状态、日志、重启、更新）
   - 备份与恢复流程（含实测记录）
   - 故障排查手册（服务起不来 / 502 / 数据库连不上 / 磁盘满）
   - 扩容方案
3. `docs/PHASE7_LAUNCH_REPORT.md`：上线检查清单逐项结果、SSLLabs 评级截图、备份恢复演练记录、压测数据（可选）
4. `.github/workflows/deploy.yml`（若做 CI/CD）

## 五、验收标准

1. `https://域名` 公网可访问，HTTP 自动跳 HTTPS，SSLLabs ≥ A
2. 管理员能登录、能发文，访客能看文章、能按分类/标签筛选
3. `nmap` 扫公网 IP 只有 22/80/443 开放，3306 和 8080 不可达
4. 每日备份正常生成，**完成一次真实恢复演练并记录**
5. `sudo reboot` 后服务自动恢复
6. 容器日志有轮转，磁盘占用稳定
7. 容器时区为 `Asia/Shanghai`，文章时间正确
8. `deploy/OPERATIONS.md` 完整，另一个人照着能重新部署一遍

## 六、可复制的完整提示词

```
你是这个个人知识博客项目的运维开发。仓库：Blogs_java，基线 main。

【前置条件】
PHASE 6 已完成：docker compose 本地整栈跑通，42 条联调用例全 PASS，
无 P0/P1 缺陷。如果没达到，先停下来反馈。

【本轮目标】
把项目部署到公网，做到：公网可访问、HTTPS 有效、数据可恢复、故障可自愈。
产出可写进简历的上线成果。

【任务清单】

A1 服务器与网络
- 2 vCPU / 4 GiB / 40 GiB，Ubuntu 22.04 或 Debian 12
- 非 root 运维账号 + sudo，禁用 root 密码登录，SSH 密钥认证
- 安全组只开 22/80/443，MySQL 3306 绝不对公网开放
- 装 Docker Engine + Compose V2（官方脚本，不用发行版老版本）

A2 域名与 DNS
- 域名 A 记录指向服务器 IP，www 也解析
- 国内服务器必须 ICP 备案才能用 80/443，香港/海外不需要但延迟高

A3 HTTPS
- certbot + Let's Encrypt，有效期 90 天，配自动续期并 --dry-run 验证
- 若 Nginx 在容器里，certbot 用 --webroot 模式，证书挂进容器
- HTTP 强制跳 HTTPS
- 加安全响应头：HSTS、X-Content-Type-Options、X-Frame-Options、Referrer-Policy

A4 生产配置收紧
- SPRING_PROFILES_ACTIVE=prod
- 所有密码强随机 ≥16 位，只存服务器 .env，chmod 600
- MySQL 不暴露 3306 到宿主机，后端 8080 也不暴露，只让 Nginx 走容器网络
- actuator 只暴露 health，show-details: never

A5 数据备份
- 每日 3 点 mysqldump 全量备份，--single-transaction 不锁表，gzip 压缩，保留 7 天
- 必须做一次真实恢复演练并记录结果
- 没做过恢复演练的备份等于没有备份

A6 日志与资源
- 所有服务配 Docker 日志轮转 max-size 10m / max-file 3
- 资源限制：mysql 768M、backend 768M、frontend 128M

A7 自愈与监控
- 所有服务 restart: unless-stopped
- 健康检查（PHASE 5 已配）
- 外部可用性监控，失败告警

A8 CI/CD（可选）
- GitHub Actions push 到 main 自动部署
- SSH 私钥放 GitHub Secrets，绝不写进仓库

【必须避开的 10 个坑】
1. 国内服务器不备案不能用 80/443
2. certbot --nginx 改不到容器内配置，要用 --webroot
3. MySQL 端口别暴露到公网（最常见安全事故）
4. Docker 日志不轮转会吃满磁盘
5. .env 要 chmod 600
6. 备份必须做恢复演练
7. restart: unless-stopped 不等于自愈，接口 500 时不会重启
8. 容器时区默认 UTC，要设 Asia/Shanghai，否则文章时间差 8 小时
9. 证书 90 天到期，要验证自动续期
10. git pull 后不 --build 等于没更新

【硬性约束】
- 不在仓库里写任何真实密码、密钥、IP
- 不提交 .env
- 不改业务代码（本轮只做部署运维）
- 前端 Vue3 + 纯 JavaScript，禁用 TypeScript

【交付物】
1. 公网可访问地址
2. deploy/OPERATIONS.md：架构、首次部署、日常运维、备份恢复、故障排查、扩容
3. docs/PHASE7_LAUNCH_REPORT.md：上线检查清单结果、SSLLabs 评级、备份恢复演练记录
4. .github/workflows/deploy.yml（若做 CI/CD）

【验收标准】
1. https://域名 公网可访问，HTTP 跳 HTTPS，SSLLabs ≥ A
2. 管理员能登录发文，访客能看文章和筛选
3. nmap 扫公网 IP 只有 22/80/443 开放
4. 每日备份正常，完成一次真实恢复演练
5. sudo reboot 后服务自动恢复
6. 容器日志有轮转，磁盘占用稳定
7. 容器时区 Asia/Shanghai，文章时间正确
8. OPERATIONS.md 完整到另一个人能照着重部署
```
