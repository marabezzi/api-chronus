package br.com.atom.api_chronus.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resumo de horas de uma semana de trabalho.
 *
 * Exemplo:
 * {
 *   "semana":          "Seg 05/05 - Dom 11/05",
 *   "diasTrabalhados": 5,
 *   "totalHoras":      "44:20",
 *   "mediaDiaria":     "08:52"
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EspelhoSemanaDTO {

    /** Identificação da semana: "Seg DD/MM - Dom DD/MM" */
    private String semana;

    /** Quantidade de dias com pelo menos uma marcação na semana */
    private int diasTrabalhados;

    /** Total de horas trabalhadas na semana no formato HH:mm */
    private String totalHoras;

    /** Média de horas por dia trabalhado no formato HH:mm */
    private String mediaDiaria;
}