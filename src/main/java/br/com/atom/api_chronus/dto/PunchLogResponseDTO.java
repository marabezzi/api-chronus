package br.com.atom.api_chronus.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resposta do endpoint /get_mftr_log.fcgi do iDClass.
 *
 * O relógio retorna:
 * {
 *   "punch_logs": [ { ...batida1 }, { ...batida2 } ],
 *   "total":       150,
 *   "next_nsr":    51        ← NSR para buscar a próxima página (0 = fim)
 * }
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PunchLogResponseDTO {

    /** Lista de batidas desta página */
    @JsonProperty("punch_logs")
    private List<PunchLogDTO> punchLogs;

    /** Total de registros armazenados no relógio */
    private Integer total;

    /**
     * NSR inicial para a próxima página.
     * Envie como "start_nsr" na próxima requisição.
     * 0 ou ausente = não há mais registros.
     */
    @JsonProperty("next_nsr")
    private Long nextNsr;

}
