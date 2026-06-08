package br.com.atom.api_chronus.service;

import br.com.atom.api_chronus.config.IdClassConfig;
import br.com.atom.api_chronus.dto.FuncionarioRequestDTO;
import br.com.atom.api_chronus.dto.FuncionarioResponseDTO;
import br.com.atom.api_chronus.entity.UsuarioPonto;
import br.com.atom.api_chronus.repository.UsuarioPontoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Serviço de CRUD de funcionários.
 *
 * Opera exclusivamente no banco PostgreSQL local.
 * O relógio iDClass REP não possui API de escrita de usuários —
 * alterações no relógio devem ser feitas manualmente via
 * interface web do equipamento.
 *
 * Soft delete: inativar() marca ativo=false e preserva o histórico
 * de batidas. reativar() desfaz a inativação.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FuncionarioService {

    private final UsuarioPontoRepository repo;
    private final IdClassConfig          idClassConfig;

    private static final DateTimeFormatter FMT_ENTRADA =
            DateTimeFormatter.ofPattern("ddMMyyyy");
    private static final DateTimeFormatter FMT_SAIDA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // ── Aviso padrão sobre o relógio ──────────────────────────────────────

    private String avisoRelogio(String acao) {
        return "Funcionario " + acao + " no banco local. "
                + "Atualize tambem no relogio iDClass via interface web: "
                + "https://" + idClassConfig.getHost();
    }

    // ─────────────────────────────────────────────────────────────────────
    // CONSULTAS
    // ─────────────────────────────────────────────────────────────────────

    /** Lista todos os funcionários ativos ordenados por nome. */
    public List<FuncionarioResponseDTO> listar() {
        return repo.findByAtivoTrueOrderByNameAsc()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /** Busca funcionário pelo PIS. Retorna null se não encontrado. */
    public FuncionarioResponseDTO buscarPorPis(String pis) {
        return repo.findByPisFormatado(normalizarPis(pis))
                .map(this::toDto)
                .orElse(null);
    }

    /** Busca funcionários ativos por nome (parcial, case-insensitive). */
    public List<FuncionarioResponseDTO> buscarPorNome(String nome) {
        return repo.findByNameContainingIgnoreCaseAndAtivoTrue(nome)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────────
    // CRUD
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Cria novo funcionário no banco.
     *
     * @throws IllegalArgumentException se PIS já existir ou
     *                                  matrícula já estiver em uso
     */
    @Transactional
    public FuncionarioResponseDTO criar(FuncionarioRequestDTO req) {
        String pisNorm = normalizarPis(req.getPis());

        if (repo.existsByPisFormatado(pisNorm)) {
            throw new IllegalArgumentException(
                    "PIS ja cadastrado: " + pisNorm);
        }

        if (req.getMatricula() != null
                && repo.existsByRegistrationAndPisFormatadoNot(
                        req.getMatricula(), pisNorm)) {
            throw new IllegalArgumentException(
                    "Matricula ja em uso: " + req.getMatricula());
        }

        UsuarioPonto entity = new UsuarioPonto();
        preencherEntity(entity, req, pisNorm);
        entity.setAtivo(true);
        entity.setUltimaSincronizacao(LocalDateTime.now());

        repo.save(entity);
        log.info("Funcionario criado: {} ({})", entity.getName(), pisNorm);

        FuncionarioResponseDTO dto = toDto(entity);
        dto.setAvisoRelogio(avisoRelogio("criado"));
        return dto;
    }

    /**
     * Atualiza dados de um funcionário existente.
     *
     * @throws IllegalArgumentException se não encontrado ou
     *                                  matrícula já em uso
     */
    @Transactional
    public FuncionarioResponseDTO atualizar(String pis,
                                             FuncionarioRequestDTO req) {
        String pisNorm = normalizarPis(pis);

        UsuarioPonto entity = repo.findByPisFormatado(pisNorm)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Funcionario nao encontrado: " + pisNorm));

        if (req.getMatricula() != null
                && !req.getMatricula().equals(entity.getRegistration())
                && repo.existsByRegistrationAndPisFormatadoNot(
                        req.getMatricula(), pisNorm)) {
            throw new IllegalArgumentException(
                    "Matricula ja em uso: " + req.getMatricula());
        }

        preencherEntity(entity, req, pisNorm);
        repo.save(entity);
        log.info("Funcionario atualizado: {} ({})", entity.getName(), pisNorm);

        FuncionarioResponseDTO dto = toDto(entity);
        dto.setAvisoRelogio(avisoRelogio("atualizado"));
        return dto;
    }

    /**
     * Inativa um funcionário (soft delete).
     * Preserva todo o histórico de batidas de ponto.
     *
     * @throws IllegalArgumentException se não encontrado
     */
    @Transactional
    public FuncionarioResponseDTO inativar(String pis) {
        String pisNorm = normalizarPis(pis);

        UsuarioPonto entity = repo.findByPisFormatado(pisNorm)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Funcionario nao encontrado: " + pisNorm));

        entity.setAtivo(false);
        entity.setDataInativacao(LocalDate.now());
        repo.save(entity);
        log.info("Funcionario inativado: {} ({})", entity.getName(), pisNorm);

        FuncionarioResponseDTO dto = toDto(entity);
        dto.setAvisoRelogio(avisoRelogio("inativado")
                + ". Remova tambem o cadastro no relogio iDClass.");
        return dto;
    }

    /**
     * Reativa um funcionário inativo.
     *
     * @throws IllegalArgumentException se não encontrado
     */
    @Transactional
    public FuncionarioResponseDTO reativar(String pis) {
        String pisNorm = normalizarPis(pis);

        UsuarioPonto entity = repo.findByPisFormatado(pisNorm)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Funcionario nao encontrado: " + pisNorm));

        entity.setAtivo(true);
        entity.setDataInativacao(null);
        repo.save(entity);
        log.info("Funcionario reativado: {} ({})", entity.getName(), pisNorm);

        FuncionarioResponseDTO dto = toDto(entity);
        dto.setAvisoRelogio(avisoRelogio("reativado")
                + ". Verifique tambem o cadastro no relogio iDClass.");
        return dto;
    }

    // ─────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────

    private void preencherEntity(UsuarioPonto e,
                                  FuncionarioRequestDTO req,
                                  String pisNorm) {
        e.setPis(Long.parseLong(pisNorm));
        e.setPisFormatado(pisNorm);

        if (req.getNome() != null && !req.getNome().isBlank()) {
            e.setName(req.getNome().toUpperCase().trim());
        }

        e.setRegistration(req.getMatricula());
        e.setCargo(req.getCargo());
        e.setSetor(req.getSetor());
        e.setEmail(req.getEmail());
        e.setCelular(req.getCelular());
        e.setObservacoes(req.getObservacoes());

        if (req.getDataAdmissao() != null
                && !req.getDataAdmissao().isBlank()) {
            try {
                e.setDataAdmissao(LocalDate.parse(
                        req.getDataAdmissao(), FMT_ENTRADA));
            } catch (Exception ex) {
                log.warn("Data de admissao invalida: {}",
                        req.getDataAdmissao());
            }
        }
    }

    private FuncionarioResponseDTO toDto(UsuarioPonto e) {
        return new FuncionarioResponseDTO(
                e.getPisFormatado(),
                e.getName(),
                e.getRegistration(),
                e.getCargo(),
                e.getSetor(),
                e.getEmail(),
                e.getCelular(),
                e.getDataAdmissao() != null
                        ? e.getDataAdmissao().format(FMT_SAIDA) : null,
                e.getObservacoes(),
                e.getAtivo(),
                e.getDataInativacao() != null
                        ? e.getDataInativacao().format(FMT_SAIDA) : null,
                null
        );
    }

    private String normalizarPis(String pis) {
        if (pis == null || pis.isBlank()) return "000000000000";
        String d = pis.replaceAll("\\D", "");
        return String.format("%012d", Long.parseLong(d));
    }
}