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
 * Regras de supervisor:
 *   - supervisor=true  → sem supervisorRef; pode ter subordinados
 *   - supervisor=false → pode ter supervisorRef apontando para
 *                        um supervisor ativo
 *   - Múltiplos supervisores por setor são permitidos
 *   - Ao inativar supervisor, subordinados são desvinculados
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

    // ─────────────────────────────────────────────────────────────────────
    // AVISO RELÓGIO
    // ─────────────────────────────────────────────────────────────────────

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

    /** Lista todos os supervisores ativos. */
    public List<FuncionarioResponseDTO> listarSupervisores() {
        return repo.findBySupervisorTrueAndAtivoTrue()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /** Lista supervisores ativos de um setor. */
    public List<FuncionarioResponseDTO> listarSupervisoresPorSetor(
            String setor) {
        return repo.findBySupervisorTrueAndAtivoTrueAndSetor(setor)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /** Lista subordinados de um supervisor pelo PIS. */
    public List<FuncionarioResponseDTO> listarSubordinados(
            String pisSupervisor) {
        String pisNorm = normalizarPis(pisSupervisor);
        UsuarioPonto supervisor = repo.findByPisFormatado(pisNorm)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Supervisor nao encontrado: " + pisNorm));

        return repo.findBySupervisorRefId(supervisor.getId())
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────────
    // CRUD
    // ─────────────────────────────────────────────────────────────────────

    /** Cria novo funcionário no banco. */
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
        log.info("Funcionario criado: {} ({}) | Supervisor: {}",
                entity.getName(), pisNorm, entity.getSupervisor());

        FuncionarioResponseDTO dto = toDto(entity);
        dto.setAvisoRelogio(avisoRelogio("criado"));
        return dto;
    }

    /** Atualiza dados de um funcionário existente. */
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
        log.info("Funcionario atualizado: {} ({})",
                entity.getName(), pisNorm);

        FuncionarioResponseDTO dto = toDto(entity);
        dto.setAvisoRelogio(avisoRelogio("atualizado"));
        return dto;
    }

    /**
     * Inativa um funcionário (soft delete).
     * Se for supervisor, desvincula automaticamente seus subordinados.
     */
    @Transactional
    public FuncionarioResponseDTO inativar(String pis) {
        String pisNorm = normalizarPis(pis);

        UsuarioPonto entity = repo.findByPisFormatado(pisNorm)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Funcionario nao encontrado: " + pisNorm));

        // Desvincula subordinados se era supervisor
        if (Boolean.TRUE.equals(entity.getSupervisor())) {
            List<UsuarioPonto> subordinados =
                    repo.findBySupervisorRefId(entity.getId());
            subordinados.forEach(s -> s.setSupervisorRef(null));
            repo.saveAll(subordinados);
            log.info("{} subordinados desvinculados do supervisor {}.",
                    subordinados.size(), pisNorm);
        }

        entity.setAtivo(false);
        entity.setDataInativacao(LocalDate.now());
        repo.save(entity);
        log.info("Funcionario inativado: {} ({})",
                entity.getName(), pisNorm);

        FuncionarioResponseDTO dto = toDto(entity);
        dto.setAvisoRelogio(avisoRelogio("inativado")
                + ". Remova tambem o cadastro no relogio iDClass.");
        return dto;
    }

    /** Reativa um funcionário inativo. */
    @Transactional
    public FuncionarioResponseDTO reativar(String pis) {
        String pisNorm = normalizarPis(pis);

        UsuarioPonto entity = repo.findByPisFormatado(pisNorm)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Funcionario nao encontrado: " + pisNorm));

        entity.setAtivo(true);
        entity.setDataInativacao(null);
        repo.save(entity);
        log.info("Funcionario reativado: {} ({})",
                entity.getName(), pisNorm);

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
        e.setCpf(limparCpf(req.getCpf()));
        e.setRg(req.getRg());
        e.setEndereco(req.getEndereco());
        e.setCargo(req.getCargo());
        e.setSetor(req.getSetor());
        e.setEmail(req.getEmail());
        e.setCelular(req.getCelular());
        e.setSalario(req.getSalario());
        e.setObservacoes(req.getObservacoes());

        // Supervisor
        boolean isSupervisor = Boolean.TRUE.equals(req.getSupervisor());
        e.setSupervisor(isSupervisor);

        if (isSupervisor) {
            // Supervisor não tem supervisorRef
            e.setSupervisorRef(null);
        } else if (req.getSupervisorPis() != null
                && !req.getSupervisorPis().isBlank()) {
            // Vincula ao supervisor informado
            String supPisNorm = normalizarPis(req.getSupervisorPis());

            UsuarioPonto supervisor = repo.findByPisFormatado(supPisNorm)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Supervisor nao encontrado: " + supPisNorm));

            if (!Boolean.TRUE.equals(supervisor.getSupervisor())) {
                throw new IllegalArgumentException(
                        "O funcionario informado nao e supervisor: "
                        + supPisNorm);
            }
            if (!Boolean.TRUE.equals(supervisor.getAtivo())) {
                throw new IllegalArgumentException(
                        "O supervisor informado esta inativo: "
                        + supPisNorm);
            }

            e.setSupervisorRef(supervisor);
        }

        // Data de admissão
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
        String supPis  = null;
        String supNome = null;

        if (e.getSupervisorRef() != null) {
            supPis  = e.getSupervisorRef().getPisFormatado();
            supNome = e.getSupervisorRef().getName();
        }

        return new FuncionarioResponseDTO(
                e.getPisFormatado(),
                e.getName(),
                e.getRegistration(),
                formatarCpf(e.getCpf()),
                e.getRg(),
                e.getEndereco(),
                e.getCargo(),
                e.getSetor(),
                e.getEmail(),
                e.getCelular(),
                e.getSalario(),
                e.getSupervisor(),
                supPis,
                supNome,
                e.getDataAdmissao() != null
                        ? e.getDataAdmissao().format(FMT_SAIDA) : null,
                e.getObservacoes(),
                e.getAtivo(),
                e.getDataInativacao() != null
                        ? e.getDataInativacao().format(FMT_SAIDA) : null,
                null
        );
    }

    /** Formata CPF: "12345678901" → "123.456.789-01" */
    private String formatarCpf(String cpf) {
        if (cpf == null || cpf.length() != 11) return cpf;
        return cpf.substring(0, 3) + "."
                + cpf.substring(3, 6) + "."
                + cpf.substring(6, 9) + "-"
                + cpf.substring(9);
    }

    /** Remove não-dígitos do CPF */
    private String limparCpf(String cpf) {
        if (cpf == null) return null;
        String d = cpf.replaceAll("\\D", "");
        return d.isEmpty() ? null : d;
    }

    private String normalizarPis(String pis) {
        if (pis == null || pis.isBlank()) return "000000000000";
        String d = pis.replaceAll("\\D", "");
        return String.format("%012d", Long.parseLong(d));
    }
}