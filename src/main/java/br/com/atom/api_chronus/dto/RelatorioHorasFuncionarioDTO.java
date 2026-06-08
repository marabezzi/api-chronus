package br.com.atom.api_chronus.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Relatório de horas de um funcionário em um período.
 *
 * Contém: totais do período + breakdown semanal + breakdown diário.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RelatorioHorasFuncionarioDTO {

    /** PIS com 12 dígitos */
    private String pis;

    /** Nome completo do funcionário */
    private String nome;

    /** Total de dias com ponto no período */
    private int totalDias;

    /** Total de horas trabalhadas no período (HH:mm) */
    private String totalHoras;

    /** Média diária de horas (HH:mm) */
    private String mediaDiaria;

    /** Breakdown por semana */
    private List<EspelhoSemanaDTO> semanas;

    /** Breakdown por dia */
    private List<EspelhoDiaDTO> dias;
}