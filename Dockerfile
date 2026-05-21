FROM eclipse-temurin:17-jre
WORKDIR /app
# Copy the application files
COPY build/libs/wallet-transfer-0.1.0.jar app.jar
# Expose the port the application runs on
EXPOSE 8080
# Run the application
ENTRYPOINT ["java","-jar","app.jar"]