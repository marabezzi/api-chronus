package br.com.atom.api_chronus.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO para criação e atualização de funcionários.
 *
 * Campos obrigatórios: pis, nome
 */
@Data
@NoArgsConstructor
public class FuncionarioRequestDTO {

    /** PIS (obrigatório). Aceita com ou sem zeros à esquerda. */
    private String pis;

    /** Nome completo (obrigatório). Convertido para maiúsculas. */
    private String nome;

    /** Número de matrícula */
    private Integer matricula;

    /**
     * CPF — apenas dígitos ou formatado.
     * Exemplos aceitos: "12345678901" ou "123.456.789-01"
     * Armazenado sem formatação, exibido formatado.
     */
    private String cpf;

    /** RG */
    private String rg;

    /** Endereço completo */
    private String endereco;

    /** Cargo ou função */
    private String cargo;

    /** Setor ou departamento */
    private String setor;

    /** E-mail */
    private String email;

    /** Celular */
    private String celular;

    /** Salário */
    private BigDecimal salario;

    /**
     * Indica se o funcionário é supervisor.
     * Default: false.
     * Se true, supervisorPis é ignorado.
     */
    private Boolean supervisor = false;

    /**
     * PIS do supervisor deste funcionário.
     * Usado apenas quando supervisor=false.
     * O supervisor informado deve estar ativo e ter supervisor=true.
     */
    private String supervisorPis;

    /** Data de admissão no formato ddMMyyyy. Ex: "15032021" */
    private String dataAdmissao;

    /** Observações gerais */
    private String observacoes;
}