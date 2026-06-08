package br.com.atom.api_chronus.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resposta do relatório de horas consolidado para todos os funcionários.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RelatorioHorasResponseDTO {

    /** Data inicial do período no formato ISO: yyyy-MM-dd */
    private String dataInicial;

    /** Data final do período no formato ISO: yyyy-MM-dd */
    private String dataFinal;

    /** Total de funcionários com ponto no período */
    private int totalFuncionarios;

    /** Total geral de horas de todos os funcionários (HH:mm) */
    private String totalGeralHoras;

    /** Lista de relatórios individuais por funcionário */
    private List<RelatorioHorasFuncionarioDTO> funcionarios;
}