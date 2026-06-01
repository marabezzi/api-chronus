package br.com.atom.api_chronus.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Representa um dia do espelho de ponto.
 *
 * Contém todas as marcações do dia e os totais calculados.
 *
 * Exemplo de resposta:
 * {
 *   "data":             "2026-05-06",
 *   "diaSemana":        "Quarta-feira",
 *   "marcacoes":        [ {...}, {...} ],
 *   "totalMarcacoes":   4,
 *   "totalTrabalhado":  "08:30",
 *   "temInconsistencia": false
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EspelhoDiaDTO {

    /** Data no formato ISO: yyyy-MM-dd */
    private String data;

    /** Nome do dia da semana em português */
    private String diaSemana;

    /** Lista de marcações do dia ordenadas cronologicamente */
    private List<EspelhoMarcacaoDTO> marcacoes;

    /** Total de marcações no dia */
    private int totalMarcacoes;

    /**
     * Total de horas trabalhadas no dia no formato HH:mm.
     * Calculado somando todos os pares Entrada/Saída.
     * "00:00" se não houver pares completos.
     */
    private String totalTrabalhado;

    /**
     * Indica se o dia tem inconsistência.
     * true quando o número de marcações é ímpar
     * (entrada sem saída correspondente).
     */
    private boolean temInconsistencia;
}