package br.com.atom.api_chronus.repository;

import br.com.atom.api_chronus.entity.UsuarioPonto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositório JPA para funcionários.
 */
@Repository
public interface UsuarioPontoRepository
        extends JpaRepository<UsuarioPonto, Long> {

    // ── Consultas básicas ─────────────────────────────────────────────────

    Optional<UsuarioPonto> findByPis(Long pis);

    Optional<UsuarioPonto> findByPisFormatado(String pisFormatado);

    Optional<UsuarioPonto> findByNameContainingIgnoreCase(String nome);

    Optional<UsuarioPonto> findByRegistration(Integer registration);

    // ── Listagens ─────────────────────────────────────────────────────────

    /** Lista todos os ativos ordenados por nome */
    List<UsuarioPonto> findByAtivoTrueOrderByNameAsc();

    /** Busca ativos por nome parcial (case-insensitive) */
    List<UsuarioPonto> findByNameContainingIgnoreCaseAndAtivoTrue(
            String nome);

    // ── Supervisor ────────────────────────────────────────────────────────

    /** Lista todos os supervisores ativos */
    List<UsuarioPonto> findBySupervisorTrueAndAtivoTrue();

    /** Lista supervisores ativos de um setor específico */
    List<UsuarioPonto> findBySupervisorTrueAndAtivoTrueAndSetor(
            String setor);

    /** Lista subordinados de um supervisor pelo ID */
    List<UsuarioPonto> findBySupervisorRefId(Long supervisorId);

    // ── Validações ────────────────────────────────────────────────────────

    boolean existsByPisFormatado(String pisFormatado);

    boolean existsByRegistrationAndPisFormatadoNot(
            Integer registration, String pisFormatado);
}