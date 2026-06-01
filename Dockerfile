# Usa apenas o JRE — o jar já foi compilado localmente com ./mvnw
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copia o jar pré-compilado da máquina host
COPY target/api-chronus-0.0.1-SNAPSHOT.jar chronus.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "chronus.jar"]