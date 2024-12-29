FROM docker.io/eclipse-temurin:21-alpine

EXPOSE 8080

COPY build/libs/docker-build*.jar /opt/boudicca-enricher/docker-build.jar
WORKDIR /opt/docker-build/
CMD ["java", "-Dserver.port=8080", "-jar", "docker-build.jar"]