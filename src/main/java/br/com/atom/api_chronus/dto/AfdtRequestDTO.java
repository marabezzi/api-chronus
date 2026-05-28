package br.com.atom.api_chronus.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Parâmetros para geração do AFDT.
 *
 * Permite filtrar por período e funcionário.
 * Se não informados, gera o AFDT completo.
 */
@Data
@NoArgsConstructor
public class AfdtRequestDTO {

    /**
     * Data inicial no formato ddMMyyyy.
     * Se não informada, usa a data da batida mais antiga.
     */
    private String dataInicial;

    /**
     * Data final no formato ddMMyyyy.
     * Se não informada, usa a data da batida mais recente.
     */
    private String dataFinal;

    /**
     * PIS do funcionário para filtrar (opcional).
     * Se não informado, gera para todos os funcionários.
     */
    private String pis;
}