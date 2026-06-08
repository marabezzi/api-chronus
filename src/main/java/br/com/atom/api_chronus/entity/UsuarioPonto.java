package br.com.atom.api_chronus.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entidade que representa um funcionário cadastrado no sistema.
 *
 * Sincronizada com o relógio iDClass via /api/sync/usuarios.
 * Campos extras (cargo, setor, cpf, supervisor, etc.) são
 * gerenciados localmente via CRUD — não existem no relógio.
 *
 * Regras de supervisor:
 *   - supervisor=true  → pode ter subordinados via supervisorRef
 *   - supervisor=false → pode ter um supervisorRef apontando para
 *                        um supervisor ativo
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "usuarios_ponto", indexes = {
        @Index(name = "idx_usuario_pis",  columnList = "pis",  unique = true),
        @Index(name = "idx_usuario_nome", columnList = "name")
})
public class UsuarioPonto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Campos do relógio iDClass ─────────────────────────────────────────

    /** PIS numérico como retornado pelo relógio */
    @Column(nullable = false, unique = true)
    private Long pis;

    /** PIS formatado com 12 dígitos e zeros à esquerda */
    @Column(name = "pis_formatado", nullable = false, length = 12)
    private String pisFormatado;

    /** Nome completo do funcionário */
    @Column(nullable = false, length = 200)
    private String name;

    /** Código interno do usuário no relógio */
    private Integer code;

    /** Número de digitais cadastradas no relógio */
    @Column(name = "templates_count")
    private Integer templatesCount;

    /** Indica se o usuário é administrador do relógio */
    private Boolean admin;

    /** Código RFID do cartão (0 = sem cartão) */
    private Long rfid;

    /** Número de matrícula do funcionário */
    private Integer registration;

    /** Momento da última sincronização com o relógio */
    @Column(name = "ultima_sincronizacao")
    private LocalDateTime ultimaSincronizacao;

    // ── Campos extras — gerenciados localmente ────────────────────────────

    /** CPF (11 dígitos, sem formatação) */
    @Column(length = 11)
    private String cpf;

    /** RG */
    @Column(length = 20)
    private String rg;

    /** Endereço completo */
    @Column(length = 300)
    private String endereco;

    /** Cargo ou função */
    @Column(length = 100)
    private String cargo;

    /** Setor ou departamento */
    @Column(length = 100)
    private String setor;

    /** E-mail */
    @Column(length = 150)
    private String email;

    /** Celular */
    @Column(length = 20)
    private String celular;

    /** Salário */
    @Column(precision = 10, scale = 2)
    private BigDecimal salario;

    /** Data de admissão */
    @Column(name = "data_admissao")
    private LocalDate dataAdmissao;

    /** Observações gerais */
    @Column(length = 500)
    private String observacoes;

    /** Indica se o funcionário está ativo */
    @Column(nullable = false)
    private Boolean ativo = true;

    /** Data de inativação (null se ativo) */
    @Column(name = "data_inativacao")
    private LocalDate dataInativacao;

    // ── Supervisor ────────────────────────────────────────────────────────

    /**
     * Indica se este funcionário é supervisor.
     * Supervisores podem ter múltiplos subordinados vinculados.
     * Default: false.
     */
    @Column(nullable = false)
    private Boolean supervisor = false;

    /**
     * Referência ao supervisor deste funcionário.
     * Null se o funcionário for supervisor ou não tiver supervisor.
     * Carregamento LAZY para não impactar performance nas listagens.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supervisor_id")
    private UsuarioPonto supervisorRef;
}