package br.com.atom.api_chronus.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de resposta para operações de CRUD de funcionários.
 *
 * Inclui aviso quando o relógio precisa ser atualizado manualmente,
 * pois o iDClass REP não possui API de escrita de usuários.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FuncionarioResponseDTO {

    /** PIS com 12 dígitos e zeros à esquerda */
    private String pis;

    /** Nome completo em maiúsculas */
    private String nome;

    /** Número de matrícula */
    private Integer matricula;

    /** Cargo ou função */
    private String cargo;

    /** Setor ou departamento */
    private String setor;

    /** E-mail */
    private String email;

    /** Celular */
    private String celular;

    /** Data de admissão no formato dd/MM/yyyy */
    private String dataAdmissao;

    /** Observações gerais */
    private String observacoes;

    /** Indica se o funcionário está ativo */
    private Boolean ativo;

    /** Data de inativação no formato dd/MM/yyyy (null se ativo) */
    private String dataInativacao;

    /**
     * Aviso sobre o relógio iDClass.
     * Preenchido apenas em operações de escrita (criar, atualizar,
     * inativar, reativar). Null em consultas.
     *
     * Exemplo:
     * "Funcionario criado no banco local. Atualize tambem no relogio
     *  iDClass via interface web: https://192.168.1.201"
     */
    private String avisoRelogio;
}