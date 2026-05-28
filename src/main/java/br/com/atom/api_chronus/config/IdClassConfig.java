package br.com.atom.api_chronus.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

/**
* Propriedades de conexão com o relógio iDClass.
*
* @ConfigurationProperties(prefix = "idclass"):
* O Spring lê idclass.host, idclass.port, idclass.user, idclass.password
* do application.properties e injeta automaticamente nos campos abaixo.
* Mais limpo e type-safe que usar @Value campo a campo.
*
* @Getter / @Setter (Lombok):
* Geram todos os getters e setters sem código manual.
* @Setter é obrigatório para que o Spring consiga injetar os valores.
*/
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "idclass")
public class IdClassConfig {

    /** IP ou hostname do relógio na rede (ex: 192.168.0.1) */
    private String host;

    /** Porta HTTPS do relógio — padrão iDClass: 443 */
    private int port;

    /** Usuário da API do relógio — padrão de fábrica: "admin" */
    private String user;

    /** Senha da API do relógio — padrão de fábrica: "admin" */
    private String password;
    /**
    * Constrói a URL base do relógio.
    * Método customizado — não tem equivalente no Lombok, fica explícito.
    *
    * @return Ex: "https://192.168.0.1:443"
    */
    public String getBaseUrl() {
    return "https://" + host + ":" + port;
    }
}
