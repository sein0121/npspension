FROM openjdk:11-jdk-slim
LABEL authors="AGILESODA"

ADD /install/NpsPension-0.0.1-SNAPSHOT.jar NpsPension.jar
ADD /install/application.properties application.properties
ENV JAVA_OPTS=""

# [jar 파일 실행 방식]
# 1. 기본 실행
# ENTRYPOINT ["java","-jar","/NpsPension.jar"]
# 2. profile 지정해서 실행
# ENTRYPOINT ["java","-jar","/NpsPension.jar","--spring.profiles.active=dev"]
# 3. 외부 properties 적용
ENTRYPOINT ["java","-jar","NpsPension.jar","--spring.config.location=application.properties"]
