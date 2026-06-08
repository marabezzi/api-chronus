package br.com.atom.api_chronus.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entidade que representa um usuário cadastrado no relógio iDClass.
 *
 * Mapeada para a tabela "usuarios_ponto".
 * Sincronizada via /api/sync/usuarios (load_users.fcgi).
 * Campos extras (cargo, setor, email, etc.) são gerenciados
 * localmente via CRUD — não existem no relógio.
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

    /** Cargo/função do funcionário */
    @Column(length = 100)
    private String cargo;

    /** Setor/departamento do funcionário */
    @Column(length = 100)
    private String setor;

    /** E-mail do funcionário */
    @Column(length = 150)
    private String email;

    /** Celular do funcionário */
    @Column(length = 20)
    private String celular;

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
}