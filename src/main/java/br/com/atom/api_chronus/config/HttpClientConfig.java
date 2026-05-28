package br.com.atom.api_chronus.config;

import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
* Configuração de infraestrutura de rede para o iDClass.
*
* Por que SSL customizado?
* O iDClass usa HTTPS com certificado auto-assinado (não emitido por CA pública).
* O HttpClient padrão do Java rejeita esses certificados.
* Solução: TrustManager permissivo + SSLParameters sem verificação de hostname.
*
* ATENÇÃO: esta configuração é adequada APENAS para redes internas/locais.
* Nunca use para conexões expostas à internet.
*
* Lombok não se aplica aqui: a classe não tem campos, apenas um @Bean.
*/
@Configuration
public class HttpClientConfig {

    /**
* HttpClient que aceita qualquer certificado SSL (incluindo auto-assinados).
*
* Dois ajustes necessários para o iDClass:
* 1. TrustManager permissivo — aceita qualquer certificado
* 2. SSLParameters sem endpointIdentificationAlgorithm — desativa
* verificação de hostname (a API java.net.http usa SSLParameters,
* diferente da antiga HttpsURLConnection)
*
* @return HttpClient configurado e pronto para uso
* @throws Exception se houver problema na inicialização do contexto SSL
*/
@Bean
public HttpClient httpClient() throws Exception {

    //  1. TrustManager que aceita qualquer certificado 
    TrustManager[] trustAll = new TrustManager[]{
    new X509TrustManager() {
    // Retorna array vazio (não null) — boa prática
    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
    }
    // Métodos de verificação sem implementação = aceita tudo
    public void checkClientTrusted(X509Certificate[] c, String a) {}
    public void checkServerTrusted(X509Certificate[] c, String a) {}
    }
    };

    //  2. Contexto SSL com o TrustManager permissivo
    SSLContext sslContext = SSLContext.getInstance("TLS");
    sslContext.init(null, trustAll, new SecureRandom());

    // 3. SSLParameters sem verificação de hostname 
    // String vazia = desativa o HTTPS endpoint identification algorithm
    // Necessário porque o certificado do relógio não tem hostname válido
    SSLParameters sslParams = new SSLParameters();
    sslParams.setEndpointIdentificationAlgorithm("");

    //  4. Monta e retorna o HttpClient configurado 
    return HttpClient.newBuilder()
    .sslContext(sslContext)
    .sslParameters(sslParams)
    .build();
    
    }
}