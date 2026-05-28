package br.com.atom.api_chronus.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resposta do endpoint GET /api/ponto do Chronus.
 *
 * Retorna os registros parseados do AFD junto com metadados úteis.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AfdResponseDTO {

    /** Total de linhas recebidas do relógio (incluindo especiais) */
    private int totalLinhas;

    /** Total de batidas válidas parseadas */
    private int totalBatidas;

    /** Lista de batidas parseadas e prontas para uso */
    private List<AfdLineDTO> batidas;
}