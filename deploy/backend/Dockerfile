# 博客后端 Dockerfile（骨架草案）
# 构建 context 为仓库根目录（docker-compose 中 context: ..）
# 说明：多模块 Maven 构建；镜像分层与依赖下载策略待 claude 验证优化。
# 构建阶段
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# 先复制所有 pom 以利用依赖缓存
COPY pom.xml .
COPY blogs-common/pom.xml blogs-common/
COPY blogs-model/pom.xml blogs-model/
COPY blogs-security/pom.xml blogs-security/
COPY blogs-core/pom.xml blogs-core/
COPY blogs-admin/pom.xml blogs-admin/
RUN mvn -B dependency:go-offline

# 复制源码并打包（跳过测试；联调期如需要测试可去掉 -DskipTests）
COPY blogs-common blogs-common
COPY blogs-model blogs-model
COPY blogs-security blogs-security
COPY blogs-core blogs-core
COPY blogs-admin blogs-admin
RUN mvn -B -pl blogs-admin -am package -DskipTests

# 运行阶段
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/blogs-admin/target/*.jar app.jar
EXPOSE 8080
# 通过环境变量注入数据源/密钥，禁止在镜像内写死
ENTRYPOINT ["java", "-jar", "app.jar"]
