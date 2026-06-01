package br.com.atom.api_chronus.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resposta completa do Espelho de Ponto.
 *
 * Contém dados do funcionário, período, dias e totalizadores.
 *
 * Exemplo de resposta:
 * {
 *   "pis":              "012952592162",
 *   "nome":             "DONATA APARECIDA MARTINS GARCIA",
 *   "dataInicial":      "2026-05-01",
 *   "dataFinal":        "2026-05-31",
 *   "dias":             [ {...dia1}, {...dia2} ],
 *   "totalDias":        22,
 *   "totalDiasComPonto": 20,
 *   "totalHorasTrabalhadas": "176:40"
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EspelhoResponseDTO {

    /** PIS do funcionário com 12 dígitos e zeros à esquerda */
    private String pis;

    /**
     * Nome do funcionário buscado automaticamente via /api/usuarios.
     * "Não identificado" se o PIS não estiver cadastrado no relógio.
     */
    private String nome;

    /** Data inicial do período no formato ISO: yyyy-MM-dd */
    private String dataInicial;

    /** Data final do período no formato ISO: yyyy-MM-dd */
    private String dataFinal;

    /** Lista de dias com marcações no período */
    private List<EspelhoDiaDTO> dias;

    /** Total de dias com pelo menos uma marcação no período */
    private int totalDiasComPonto;

    /**
     * Total geral de horas trabalhadas no período no formato HH:mm.
     * Soma de todos os dias.
     */
    private String totalHorasTrabalhadas;
}