package br.com.atom.api_chronus.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entidade que representa um usuário cadastrado no relógio iDClass.
 *
 * Mapeada para a tabela "usuarios_ponto".
 * O PIS é único — garante que não há duplicatas.
 * Atualizado a cada sincronização com os dados mais recentes do relógio.
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
}