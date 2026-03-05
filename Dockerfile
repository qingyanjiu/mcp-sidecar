FROM ubuntu:22.04

ENV spring_profiles_active=server
ENV TZ=Asia/Shanghai

EXPOSE 2222/tcp
EXPOSE 9970/tcp

RUN apt-get update && \
     apt-get install -y --no-install-recommends openjdk-17-jre vim wget curl net-tools language-pack-zh-hans tzdata && \
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