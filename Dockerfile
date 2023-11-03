FROM openjdk:11-jdk-slim
LABEL authors="LUNA"

ADD /target/NpsPension-0.0.1-SNAPSHOT.jar NpsPension.jar
ENV JAVA_OPTS=""

ENTRYPOINT ["java","-jar","/NpsPension.jar"]