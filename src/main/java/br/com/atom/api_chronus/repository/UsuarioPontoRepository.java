package br.com.atom.api_chronus.repository;

import br.com.atom.api_chronus.entity.UsuarioPonto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositório JPA para usuários do relógio e funcionários cadastrados.
 */
@Repository
public interface UsuarioPontoRepository extends JpaRepository<UsuarioPonto, Long> {

    /** Busca pelo PIS numérico */
    Optional<UsuarioPonto> findByPis(Long pis);

    /** Busca pelo PIS formatado (12 dígitos) */
    Optional<UsuarioPonto> findByPisFormatado(String pisFormatado);

    /** Busca por nome contendo o termo (case-insensitive) */
    Optional<UsuarioPonto> findByNameContainingIgnoreCase(String nome);

    /** Busca por matrícula */
    Optional<UsuarioPonto> findByRegistration(Integer registration);

    /** Lista todos os funcionários ativos ordenados por nome */
    List<UsuarioPonto> findByAtivoTrueOrderByNameAsc();

    /** Busca ativos por nome contendo o termo (case-insensitive) */
    List<UsuarioPonto> findByNameContainingIgnoreCaseAndAtivoTrue(String nome);

    /** Verifica se PIS formatado já existe */
    boolean existsByPisFormatado(String pisFormatado);

    /**
     * Verifica se matrícula já está em uso por outro funcionário.
     * Usado para validar unicidade na criação e atualização.
     */
    boolean existsByRegistrationAndPisFormatadoNot(
            Integer registration, String pisFormatado);
}