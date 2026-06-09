package br.com.atom.api_chronus.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para criação de tratamento de ponto.
 *
 * Campos obrigatórios: pis, data, ocorrencia
 * Campos obrigatórios para I e D: horario, motivo
 */
@Data
@NoArgsConstructor
public class TratamentoPontoRequestDTO {

    /**
     * PIS do funcionário (obrigatório).
     * Aceita com ou sem zeros à esquerda.
     */
    private String pis;

    /**
     * Data do tratamento no formato ddMMyyyy (obrigatório).
     * Exemplo: "06052026" = 06/05/2026
     */
    private String data;

    /**
     * Horário tratado no formato HH:mm.
     * Obrigatório para ocorrências I e D.
     * Exemplo: "08:27"
     */
    private String horario;

    /**
     * Tipo de ocorrência (obrigatório):
     *   I = Horário incluído
     *   D = Horário desconsiderado
     *   P = Pré-assinalação do período de repouso
     */
    private String ocorrencia;

    /**
     * Motivo do tratamento.
     * Obrigatório para I e D.
     * Exemplo: "Atestado médico CRM 12345"
     */
    private String motivo;
}