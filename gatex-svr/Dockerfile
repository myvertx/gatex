ARG VERSION

# 基础镜像
FROM nnzbz/vertx-app:4.5.7-alpine
# 镜像的作者和邮箱
LABEL maintainer="nnzbz@163.com"
# 镜像的版本
LABEL version=${VERSION}
# 镜像的描述
LABEL description="gatex-svr"

# 定义形参，实参可在下列情况下传入
# 1. 在pom.xml文件中的buildArgs节点下定义实参)
# 2. 在docker build命令中以--build-arg a_name=a_value形式赋值
ARG JAR_FILE

# 加入jar包
COPY target/${JAR_FILE} /usr/local/vertx/myservice.jar
# 加入资源
COPY src/main/resources/config/application.yml /usr/local/vertx/config/
COPY src/main/resources/config/option.json /usr/local/vertx/config/