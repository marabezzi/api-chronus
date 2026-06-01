package br.com.atom.api_chronus.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.atom.api_chronus.entity.UsuarioPonto;

/**
 * Repositório JPA para usuários do relógio.
 */
@Repository
public interface UsuarioPontoRepository extends JpaRepository<UsuarioPonto, Long> {

    Optional<UsuarioPonto> findByPis(Long pis);

    Optional<UsuarioPonto> findByPisFormatado(String pisFormatado);

    Optional<UsuarioPonto> findByNameContainingIgnoreCase(String nome);

    Optional<UsuarioPonto> findByRegistration(Integer registration);
}