package br.com.atom.api_chronus.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Parâmetros para geração do Espelho de Ponto.
 *
 * Todos os campos são obrigatórios.
 * O período é informado no formato ddMMyyyy.
 */
@Data
@NoArgsConstructor
public class EspelhoRequestDTO {

    /**
     * PIS do funcionário (com ou sem zeros à esquerda).
     * Exemplos aceitos: "12952592162" ou "012952592162"
     */
    private String pis;

    /**
     * Data inicial do período no formato ddMMyyyy.
     * Exemplo: "01052026" = 01/05/2026
     */
    private String dataInicial;

    /**
     * Data final do período no formato ddMMyyyy.
     * Exemplo: "31052026" = 31/05/2026
     */
    private String dataFinal;
}
