# 博客后端镜像：多模块 Maven 构建 → JRE 运行
# 构建上下文 = 仓库根目录（compose 里 context: ..，另见根目录 .dockerignore）
#
# 为什么不用 `mvn dependency:go-offline` 预热依赖：
#   blogs-admin 依赖同版本号的兄弟模块（blogs-core / blogs-model / ...），
#   go-offline 阶段这些模块还没 install 到本地仓库，也不在 reactor 产物中，
#   Maven 会去远程仓库找 0.0.1-SNAPSHOT 并直接失败（maven-dependency-plugin MDEP-516）。
#   想要依赖缓存请用 BuildKit cache mount（见文件末尾注释）或本地先跑一次 mvn package。

# ---------- 构建阶段 ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# 先只拷各模块 pom：模块结构/依赖变更时才会让后续层失效
COPY pom.xml ./
COPY blogs-common/pom.xml blogs-common/
COPY blogs-model/pom.xml blogs-model/
COPY blogs-security/pom.xml blogs-security/
COPY blogs-core/pom.xml blogs-core/
COPY blogs-admin/pom.xml blogs-admin/

# 再拷源码（.dockerignore 已排除 target，避免把宿主机产物带进来）
COPY blogs-common/src blogs-common/src
COPY blogs-model/src blogs-model/src
COPY blogs-security/src blogs-security/src
COPY blogs-core/src blogs-core/src
COPY blogs-admin/src blogs-admin/src

# maven.test.skip：连测试编译一起跳过，构建更快且不依赖测试夹具
RUN mvn -B -pl blogs-admin -am package -Dmaven.test.skip=true

# ---------- 运行阶段 ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# 镜像里没有 curl，但 compose 的 healthcheck 依赖它（前端 depends_on 又依赖后端 healthy）。
# 不装 curl 的表现是：容器 Up 但永远 unhealthy，前端因依赖条件不满足而一直不启动。
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# 非 root 运行：容器被攻破时少一分横向移动的余地
RUN groupadd -r app && useradd -r -g app -d /app app

COPY --from=build --chown=app:app /app/blogs-admin/target/*.jar app.jar

USER app
EXPOSE 8080

# 数据源、JWT 密钥、初始管理员密码全部由环境变量注入（见 deploy/.env.example），
# 镜像内不写任何真实凭据
ENTRYPOINT ["java", "-jar", "app.jar"]

# 可选优化：若构建环境启用 BuildKit（Docker 23 + Compose V2 默认开启），
# 可把上面的 RUN mvn 换成下面这行，跨次构建复用本地仓库：
#   RUN --mount=type=cache,target=/root/.m2 mvn -B -pl blogs-admin -am package -Dmaven.test.skip=true
