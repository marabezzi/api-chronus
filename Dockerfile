#  Stage 1: Build
# Usa imagem completa do Maven para compilar o projeto.
# "AS build" nomeia este estágio para referenciar depois.
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Copia o pom.xml ANTES do código-fonte.
# Isso aproveita o cache de camadas do Docker:
# se o pom.xml não mudar, o Docker não baixa as dependências novamente.
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Agora copia o código e compila
COPY src ./src
RUN mvn clean package -DskipTests

#  Stage 2: Runtime
# Imagem final leve: apenas o JRE Alpine (sem Maven, sem código-fonte).
# Resultado: imagem ~100MB em vez de ~500MB.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copia apenas o .jar gerado no stage anterior
COPY --from=build /app/target/*.jar chronus.jar

# Documenta que a aplicação escuta na porta 8081
EXPOSE 8081

# Comando de inicialização da aplicação
ENTRYPOINT ["java", "-jar", "chronus.jar"]