package br.com.atom.api_chronus.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO de resposta para operações de CRUD de funcionários.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FuncionarioResponseDTO {

    /** PIS com 12 dígitos */
    private String pis;

    /** Nome completo em maiúsculas */
    private String nome;

    /** Número de matrícula */
    private Integer matricula;

    /** CPF formatado: 000.000.000-00 */
    private String cpf;

    /** RG */
    private String rg;

    /** Endereço */
    private String endereco;

    /** Cargo */
    private String cargo;

    /** Setor */
    private String setor;

    /** E-mail */
    private String email;

    /** Celular */
    private String celular;

    /** Salário */
    private BigDecimal salario;

    /** Indica se é supervisor */
    private Boolean supervisor;

    /**
     * PIS do supervisor deste funcionário.
     * Null se for supervisor ou não tiver supervisor vinculado.
     */
    private String supervisorPis;

    /**
     * Nome do supervisor deste funcionário.
     * Null se for supervisor ou não tiver supervisor vinculado.
     */
    private String supervisorNome;

    /** Data de admissão no formato dd/MM/yyyy */
    private String dataAdmissao;

    /** Observações */
    private String observacoes;

    /** Indica se está ativo */
    private Boolean ativo;

    /** Data de inativação no formato dd/MM/yyyy (null se ativo) */
    private String dataInativacao;

    /**
     * Aviso sobre o relógio iDClass.
     * Preenchido apenas em operações de escrita.
     * Null em consultas.
     */
    private String avisoRelogio;
}