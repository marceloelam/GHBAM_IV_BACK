FROM eclipse-temurin:21-jre

WORKDIR /app

COPY target/*-runner.jar app.jar

EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseG1GC"

CMD ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]