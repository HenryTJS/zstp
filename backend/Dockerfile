# Build stage
FROM maven:3.9.9-eclipse-temurin-17-alpine AS build
WORKDIR /app

# 可选：配置 Maven 国内镜像（阿里云），加速依赖下载（如果你在国外可以删掉这一段）
COPY --chmod=644 <<EOF /usr/share/maven/conf/settings.xml
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd">
    <mirrors>
        <mirror>
            <id>aliyunmaven</id>
            <name>阿里云公共仓库</name>
            <url>https://maven.aliyun.com/repository/public</url>
            <mirrorOf>central</mirrorOf>
        </mirror>
    </mirrors>
</settings>
EOF

# 先复制 pom.xml 以利用 Docker 缓存（避免每次修改代码都重新下载依赖）
COPY pom.xml .
RUN mvn dependency:go-offline -B  # 预先下载依赖（可选，加速后续构建）

COPY src ./src
# 移除 -q，增加超时和重试参数，让 Maven 输出完整日志
RUN mvn -DskipTests package \
    -Dmaven.wagon.http.retryHandler.count=3 \
    -Dmaven.wagon.http.connectTimeout=30000

# Run stage
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN mkdir -p /app/uploads
# 明确指定 jar 文件名（如果你不确定实际生成的 jar 名称，可以保持通配符，但建议固定名称）
COPY --from=build /app/target/*.jar /app/app.jar
EXPOSE 5000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]