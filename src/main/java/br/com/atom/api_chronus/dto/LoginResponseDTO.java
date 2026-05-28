package br.com.atom.api_chronus.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
* DTO da resposta de login do iDClass.
*
* JSON retornado pelo relógio em caso de sucesso:
* { "session": "xYz9AbC..." }
*
* O token "session" deve ser enviado como cookie em todos
* os requests subsequentes:
* Cookie: session=xYz9AbC...
*
* @JsonIgnoreProperties(ignoreUnknown = true):
* Se o relógio retornar campos extras (ex: versão do firmware),
* o Jackson os ignora em vez de lançar UnrecognizedPropertyException.
* Boa prática em integrações com hardware — contratos podem mudar.
*
* Lombok:
* @Data — getters, setters, toString, equals, hashCode
* @NoArgsConstructor — construtor vazio obrigatório para o Jackson
*/
/**
 * DTO da resposta de login do iDClass.
 * { "session": "abc123xyz..." }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor                        // ← adicionado
@JsonIgnoreProperties(ignoreUnknown = true)
public class LoginResponseDTO {

    // Token de sessão — usado como: Cookie: session=abc123...
    private String session;
}
