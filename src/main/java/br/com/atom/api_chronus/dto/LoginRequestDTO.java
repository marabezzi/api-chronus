package br.com.atom.api_chronus.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
* DTO de requisição de login ao iDClass.
*
* JSON enviado ao relógio:
* { "login": "admin", "password": "admin" }
*
* Anotações Lombok:
* @Data — gera getters, setters, toString, equals, hashCode
* @NoArgsConstructor — construtor vazio (exigido pelo Jackson para (de)serializar)
* @AllArgsConstructor — construtor com todos os campos (usado no IdClassAuthService)
*
* Boas práticas:
* - DTO imutável seria ideal (@Value do Lombok), mas o Jackson precisa de
* setter/construtor vazio para deserializar. @Data + @NoArgsConstructor
* é o padrão mais simples e compatível.
* - Nunca reutilize DTOs entre camadas diferentes (request != response != entity).
*/
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequestDTO {

    /** Nome do campo esperado pela API do relógio — deve ser "login" */
    private String login;
    /** Senha correspondente ao usuário */
    private String password;

}
