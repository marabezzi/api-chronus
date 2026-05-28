package br.com.atom.api_chronus.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Representa uma batida de ponto retornada pelo iDClass via get_mftr_log.fcgi.
 *
 * O relógio retorna os campos em snake_case.
 * @JsonProperty mapeia cada campo do JSON para o atributo Java em camelCase.
 *
 * Exemplo de objeto retornado:
 * {
 *   "nsr":         1,
 *   "user_id":     42,
 *   "user_pis":    "12345678900",
 *   "date_time":   "2026-05-28T08:00:00",
 *   "type":        0
 * }
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PunchLogDTO {

     /**
     * NSR — Número Sequencial de Registro.
     * Identificador único e sequencial de cada batida no relógio.
     * Exigido pela Portaria 671/INMETRO.
     */
    private Long nsr;

    /** ID do usuário que bateu o ponto */
    @JsonProperty("user_id")
    private Long userId;

    /**
     * PIS/CPF do funcionário.
     * Na Portaria 671, o campo muda de "user_pis" para "user_cpf".
     * Mapeamos ambos para cobrir as duas versões de firmware.
     */
    @JsonProperty("user_pis")
    private String userPis;

    @JsonProperty("user_cpf")
    private String userCpf;

    /**
     * Data e hora da batida no formato ISO: "2026-05-28T08:00:00"
     * Retornado como string pelo relógio — converta com LocalDateTime.parse() se necessário.
     */
    @JsonProperty("date_time")
    private String dateTime;

    /**
     * Tipo da batida:
     *   0 = Entrada
     *   1 = Saída
     *   2 = Entrada intervalo
     *   3 = Saída intervalo
     *  -1 = Não classificado
     */
    private Integer type;

}
