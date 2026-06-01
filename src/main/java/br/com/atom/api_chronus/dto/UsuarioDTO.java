package br.com.atom.api_chronus.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Representa um usuário cadastrado no relógio iDClass.
 *
 * Retornado pelo endpoint POST /load_users.fcgi
 * com parâmetros: limit, offset, include_templates=false
 *
 * Exemplo de objeto retornado pelo relógio:
 * {
 *   "name":            "PATRICIA APARECIDA ALVES",
 *   "pis":             12849194257,
 *   "code":            0,
 *   "templates_count": 1,
 *   "password":        "",
 *   "admin":           false,
 *   "rfid":            0,
 *   "bars":            "",
 *   "registration":    95
 * }
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UsuarioDTO {

    /** Nome completo do funcionário */
    private String name;

    /**
     * PIS do funcionário (numérico).
     * Atenção: o relógio retorna como número inteiro (sem zeros à esquerda).
     * Use getPisFormatado() para obter com 12 dígitos.
     */
    private Long pis;

    /** Código interno do usuário no relógio */
    private Integer code;

    /** Quantidade de digitais cadastradas */
    private Integer templates_count;

    /** Indica se o usuário tem perfil de administrador no relógio */
    private Boolean admin;

    /** Código RFID do cartão (0 = sem cartão) */
    private Long rfid;

    /** Código de barras (vazio se não cadastrado) */
    private String bars;

    /** Número de matrícula do funcionário */
    private Integer registration;

    /**
     * Retorna o PIS formatado com 12 dígitos e zeros à esquerda.
     * Necessário para cruzar com os registros do AFD.
     *
     * Exemplo: pis=12849194257 → "012849194257"
     */
    public String getPisFormatado() {
        if (pis == null) return "000000000000";
        return String.format("%012d", pis);
    }
}