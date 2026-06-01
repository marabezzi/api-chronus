package br.com.atom.api_chronus.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resposta do endpoint /load_users.fcgi do iDClass.
 *
 * O relógio retorna:
 * {
 *   "users": [ {...usuario1}, {...usuario2} ]
 * }
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UsuarioResponseDTO {

    /** Lista de usuários cadastrados no relógio */
    private List<UsuarioDTO> users;
}