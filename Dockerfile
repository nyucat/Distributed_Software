FROM maven:3.8.5-openjdk-8 AS builder
WORKDIR /app
COPY pom.xml .
# 这一步可以先下载部分依赖，加快构建速度
# RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:8-jre
WORKDIR /app
COPY --from=builder /app/target/distributed-seckill-1.0-SNAPSHOT.jar app.jar

# 暴露端口由环境变量 SERVER_PORT 决定，默认为 8081
EXPOSE 8081 8082
ENTRYPOINT ["java", "-jar", "app.jar"]