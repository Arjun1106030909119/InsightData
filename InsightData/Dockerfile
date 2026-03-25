FROM openjdk:17-jdk-slim

WORKDIR /app

COPY src/main/java .

RUN chmod +x mvnw
RUN ./mvnw clean package -DskipTests

EXPOSE 8080

CMD ["sh", "-c", "java -jar target/*.jar"]