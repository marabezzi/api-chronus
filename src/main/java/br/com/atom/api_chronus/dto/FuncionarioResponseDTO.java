package br.com.atom.api_chronus.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO de resposta para operações de CRUD de funcionários.
 * Ordem dos campos deve corresponder exatamente ao toDto()
 * do FuncionarioService.
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

    /** PIS do supervisor (null se for supervisor ou não tiver) */
    private String supervisorPis;

    /** Nome do supervisor (null se for supervisor ou não tiver) */
    private String supervisorNome;

    /** Número WhatsApp com DDI+DDD. Ex: "5514999999999" */
    private String whatsappNumero;

    /** Notificações WhatsApp habilitadas */
    private Boolean whatsappHabilitado;

    /**
     * Preferência de notificação WhatsApp:
     *   CADA_BATIDA → mensagem a cada batida
     *   RESUMO_DIA  → resumo diário no horário configurado
     */
    private String whatsappPreferencia;

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
     * Preenchido apenas em operações de escrita. Null em consultas.
     */
    private String avisoRelogio;
}