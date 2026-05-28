package br.com.atom.api_chronus.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de resposta do endpoint GET /api/auth/status.
 *
 * Informa o estado atual da sessão com o relógio sem expor o token.
 *
 * Exemplo de resposta:
 * {
 *   "sessaoAtiva": true,
 *   "segundosRestantes": 342,
 *   "mensagem": "Sessão ativa — expira em 342 segundos"
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionStatusDTO {

     /** Indica se há uma sessão válida com o relógio no momento */
     private boolean sessaoAtiva;

     /** Segundos restantes até o token expirar (0 se inativo) */
     private long segundosRestantes;
 
     /** Mensagem legível sobre o estado da sessão */
     private String mensagem;

}
