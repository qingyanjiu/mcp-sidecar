FROM ubuntu:18.04

# 用于sba监控，监控端ip
ENV MONITOR_IP=127.0.0.1
# 用于sba监控，自己的ip
ENV SELF_IP=127.0.0.1
ENV MEDIA_SERVER_IP=127.0.0.1
ENV STREAM_IP=127.0.0.1
ENV LC_ALL zh_CN.UTF-8
ENV spring_profiles_active=server
ENV TZ=Asia/Shanghai

EXPOSE 9980/tcp

#ADD sources.list /etc/apt/sources.list

RUN export DEBIAN_FRONTEND=noninteractive &&\
     apt-get update && \
     apt-get install -y --no-install-recommends openjdk-11-jre apt-utils ca-certificates ffmpeg language-pack-zh-hans tzdata && \
     apt-get autoremove -y && \
     apt-get clean -y && \
     rm -rf /var/lib/apt/lists/*dic

COPY /target/*.jar /app/
COPY ./src/main/resources/application-server.yml /app/config/
COPY ./Shanghai /app/
COPY ./docker-start.sh /app/
RUN ln -fs /app/Shanghai /etc/localtime

WORKDIR /app
CMD ["sh", "docker-start.sh"]