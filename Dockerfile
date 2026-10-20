# ---- build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---- run stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 appuser \
 && mkdir -p /app/uploads && chown appuser /app/uploads
COPY --from=build /app/target/civictrack-*.jar app.jar
USER appuser
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
# The app reads PORT (Render sets it) and binds to all interfaces by default.
ENTRYPOINT ["java", "-Xmx300m", "-XX:+UseSerialGC", "-Xss512k", "-XX:MaxMetaspaceSize=128m", "-jar", "app.jar"]
