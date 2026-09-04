# 说明：Maven 依赖已由宿主机在编译时拉取，这里直接打包构建好的 Spring Boot 可执行 jar。
# ============ 运行阶段 ============
FROM eclipse-temurin:21-jre
WORKDIR /app

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms256m -Xmx512m"

COPY backend/app.jar app.jar

RUN mkdir -p /app/upload
VOLUME ["/app/upload"]

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]