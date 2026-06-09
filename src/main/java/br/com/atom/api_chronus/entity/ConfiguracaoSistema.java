package br.com.atom.api_chronus.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Configurações do sistema armazenadas no banco.
 *
 * Categorias:
 *   SYNC    → sincronização com o relógio
 *   EMPRESA → dados da empresa
 *   EMAIL   → configurações SMTP
 *   GERAL   → configurações gerais
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "configuracoes_sistema")
public class ConfiguracaoSistema {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Chave única da configuração. Ex: "sync.intervalo.minutos" */
    @Column(nullable = false, unique = true, length = 100)
    private String chave;

    /** Valor da configuração */
    @Column(length = 500)
    private String valor;

    /** Descrição legível para o usuário */
    @Column(length = 300)
    private String descricao;

    /**
     * Categoria:
     *   SYNC    → sincronização
     *   EMPRESA → dados da empresa
     *   EMAIL   → SMTP
     *   GERAL   → configurações gerais
     */
    @Column(nullable = false, length = 20)
    private String categoria;

    /** Indica se o valor é sensível (senha, etc.) */
    @Column(nullable = false)
    private Boolean sensivel = false;

    /** Última atualização */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}