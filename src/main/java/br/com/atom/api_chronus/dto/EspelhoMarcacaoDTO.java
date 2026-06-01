package br.com.atom.api_chronus.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Representa uma marcação de ponto dentro de um dia.
 *
 * Cada marcação tem horário e tipo (Entrada ou Saída).
 * Pares E/S são identificados pelo sequencial.
 *
 * Exemplo de resposta:
 * {
 *   "horario":    "08:27",
 *   "tipo":       "Entrada",
 *   "sequencial": 1
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EspelhoMarcacaoDTO {

    /** Horário da marcação no formato HH:mm */
    private String horario;

    /** Tipo: "Entrada", "Saída", "Entrada intervalo", "Saída intervalo" */
    private String tipo;

    /**
     * Sequencial do par Entrada/Saída na jornada.
     * Par 1 = primeira entrada + primeira saída.
     * Par 2 = segunda entrada + segunda saída.
     */
    private int sequencial;
}