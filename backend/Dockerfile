FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC"
COPY --from=build /workspace/target/*.jar app.jar
EXPOSE 2000
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
