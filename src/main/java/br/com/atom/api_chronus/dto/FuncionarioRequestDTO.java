package br.com.atom.api_chronus.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para criação e atualização de funcionários.
 *
 * Campos obrigatórios: pis, nome
 * Campos opcionais:    matricula, cargo, setor, email,
 *                      celular, dataAdmissao, observacoes
 */
@Data
@NoArgsConstructor
public class FuncionarioRequestDTO {

    /**
     * PIS do funcionário (obrigatório).
     * Aceita com ou sem zeros à esquerda.
     * Exemplo: "12952592162" ou "012952592162"
     */
    private String pis;

    /**
     * Nome completo do funcionário (obrigatório).
     * Convertido automaticamente para maiúsculas.
     * Exemplo: "DONATA APARECIDA MARTINS GARCIA"
     */
    private String nome;

    /**
     * Número de matrícula do funcionário (opcional).
     * Deve ser único entre os funcionários ativos.
     */
    private Integer matricula;

    /**
     * Cargo ou função do funcionário (opcional).
     * Exemplo: "Auxiliar Administrativo"
     */
    private String cargo;

    /**
     * Setor ou departamento (opcional).
     * Exemplo: "Recursos Humanos"
     */
    private String setor;

    /**
     * E-mail do funcionário (opcional).
     * Exemplo: "joao.silva@empresa.com.br"
     */
    private String email;

    /**
     * Celular do funcionário (opcional).
     * Exemplo: "(14) 99999-9999"
     */
    private String celular;

    /**
     * Data de admissão no formato ddMMyyyy (opcional).
     * Exemplo: "15032021" = 15/03/2021
     */
    private String dataAdmissao;

    /**
     * Observações gerais sobre o funcionário (opcional).
     */
    private String observacoes;
}