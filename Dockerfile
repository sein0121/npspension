FROM openjdk:11-jdk-slim
LABEL authors="AGILESODA"

ADD /NpsPension-0.0.1-SNAPSHOT.jar NpsPension.jar
ENV JAVA_OPTS=""

# ENTRYPOINT ["java","-jar","/NpsPension.jar"]
# ENTRYPOINT ["java","-jar","/NpsPension.jar", "--spring.profiles.active=dev"]

ENTRYPOINT ["java","-jar","NpsPension.jar", "--spring.config.location=classpath:/application-test.properties"]
