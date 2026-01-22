# Use uma imagem base OpenJDK compatível com sua versão Java
FROM eclipse-temurin:23-jdk-alpine

# Define o diretório de trabalho
WORKDIR /app

# Copia o JAR da sua aplicação para a imagem
# O nome do JAR deve corresponder ao gerado pelo Maven/Gradle
ARG JAR_FILE=target/mensageria-0.0.1-SNAPSHOT.jar
COPY ${JAR_FILE} app.jar

# Expõe a porta que sua aplicação usa (configurada em server.port)
EXPOSE 8082

# Comando para executar a aplicação
ENTRYPOINT ["java", "-jar", "app.jar"]