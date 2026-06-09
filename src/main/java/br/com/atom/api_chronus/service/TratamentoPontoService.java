package br.com.atom.api_chronus.service;

import br.com.atom.api_chronus.dto.TratamentoPontoRequestDTO;
import br.com.atom.api_chronus.dto.TratamentoPontoResponseDTO;
import br.com.atom.api_chronus.entity.TratamentoPonto;
import br.com.atom.api_chronus.entity.UsuarioPonto;
import br.com.atom.api_chronus.repository.TratamentoPontoRepository;
import br.com.atom.api_chronus.repository.UsuarioPontoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Serviço de tratamentos de ponto.
 *
 * Gerencia registros de ocorrências (I/D/P) com comprovantes.
 * Documentos são armazenados em /app/tratamentos/{pis}/{data}/
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TratamentoPontoService {

    private final TratamentoPontoRepository tratamentoRepo;
    private final UsuarioPontoRepository    usuarioRepo;

    @Value("${tratamentos.path:/app/tratamentos}")
    private String tratamentosPath;

    private static final DateTimeFormatter FMT_ENTRADA =
            DateTimeFormatter.ofPattern("ddMMyyyy");
    private static final DateTimeFormatter FMT_SAIDA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FMT_SAIDA_DT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // ─────────────────────────────────────────────────────────────────────
    // CONSULTAS
    // ─────────────────────────────────────────────────────────────────────

    /** Lista todos os tratamentos de um funcionário. */
    public List<TratamentoPontoResponseDTO> listarPorPis(String pis) {
        String pisNorm = normalizarPis(pis);
        return tratamentoRepo
                .findByPisFormatadoOrderByDataAscHorarioAsc(pisNorm)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /** Lista tratamentos de um funcionário em uma data. */
    public List<TratamentoPontoResponseDTO> listarPorPisEData(
            String pis, String data) {
        String pisNorm = normalizarPis(pis);
        LocalDate dt = LocalDate.parse(data, FMT_ENTRADA);
        return tratamentoRepo.findByPisFormatadoAndData(pisNorm, dt)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /** Lista tratamentos de um funcionário em um período. */
    public List<TratamentoPontoResponseDTO> listarPorPisEPeriodo(
            String pis, String dataInicial, String dataFinal) {
        String pisNorm = normalizarPis(pis);
        LocalDate ini = LocalDate.parse(dataInicial, FMT_ENTRADA);
        LocalDate fim = LocalDate.parse(dataFinal,   FMT_ENTRADA);
        return tratamentoRepo
                .findByPisFormatadoAndDataBetweenOrderByDataAsc(
                        pisNorm, ini, fim)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /** Busca um tratamento por ID. */
    public TratamentoPontoResponseDTO buscarPorId(Long id) {
        return tratamentoRepo.findById(id)
                .map(this::toDto)
                .orElse(null);
    }

    // ─────────────────────────────────────────────────────────────────────
    // CRUD
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Cria um novo tratamento de ponto.
     *
     * @throws IllegalArgumentException se campos obrigatórios inválidos
     */
    @Transactional
    public TratamentoPontoResponseDTO criar(
            TratamentoPontoRequestDTO req) {

        validarRequest(req);

        String pisNorm = normalizarPis(req.getPis());
        LocalDate data = LocalDate.parse(req.getData(), FMT_ENTRADA);

        TratamentoPonto entity = new TratamentoPonto();
        entity.setPisFormatado(pisNorm);
        entity.setData(data);
        entity.setHorario(req.getHorario());
        entity.setOcorrencia(req.getOcorrencia().toUpperCase());
        entity.setMotivo(req.getMotivo());
        entity.setCriadoEm(LocalDateTime.now());

        tratamentoRepo.save(entity);
        log.info("Tratamento criado: PIS={} data={} ocor={}",
                pisNorm, data, entity.getOcorrencia());

        return toDto(entity);
    }

    /**
     * Anexa documento comprovante a um tratamento existente.
     * Aceita: PDF (application/pdf), JPG (image/jpeg), PNG (image/png).
     *
     * @throws IllegalArgumentException se formato inválido ou
     *                                  tratamento não encontrado
     */
    @Transactional
    public TratamentoPontoResponseDTO anexarDocumento(
            Long id, MultipartFile arquivo) throws IOException {

        TratamentoPonto entity = tratamentoRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Tratamento nao encontrado: " + id));

        // Valida formato
        String contentType = arquivo.getContentType();
        if (!isFormatoAceito(contentType)) {
            throw new IllegalArgumentException(
                    "Formato invalido. Aceitos: PDF, JPG, PNG. "
                    + "Recebido: " + contentType);
        }

        // Remove documento anterior se existir
        if (entity.getDocumentoPath() != null) {
            removerArquivo(entity.getDocumentoPath());
        }

        // Salva o arquivo no volume
        String caminho = salvarArquivo(
                entity.getPisFormatado(),
                entity.getData(),
                arquivo);

        entity.setDocumentoNome(arquivo.getOriginalFilename());
        entity.setDocumentoPath(caminho);
        entity.setDocumentoTipo(contentType);
        tratamentoRepo.save(entity);

        log.info("Documento anexado ao tratamento {}: {}",
                id, arquivo.getOriginalFilename());

        return toDto(entity);
    }

    /**
     * Retorna o conteúdo do documento comprovante.
     *
     * @return array de bytes do arquivo, ou null se não existir
     */
    public byte[] baixarDocumento(Long id) throws IOException {
        TratamentoPonto entity = tratamentoRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Tratamento nao encontrado: " + id));

        if (entity.getDocumentoPath() == null) {
            throw new IllegalArgumentException(
                    "Tratamento " + id + " nao possui documento.");
        }

        Path path = Paths.get(entity.getDocumentoPath());
        if (!Files.exists(path)) {
            throw new IllegalArgumentException(
                    "Arquivo nao encontrado no servidor: "
                    + entity.getDocumentoNome());
        }

        return Files.readAllBytes(path);
    }

    /** Retorna o content-type do documento. */
    public String getTipoDocumento(Long id) {
        return tratamentoRepo.findById(id)
                .map(TratamentoPonto::getDocumentoTipo)
                .orElse("application/octet-stream");
    }

    /** Retorna o nome do documento. */
    public String getNomeDocumento(Long id) {
        return tratamentoRepo.findById(id)
                .map(TratamentoPonto::getDocumentoNome)
                .orElse("documento");
    }

    /**
     * Remove um tratamento e seu documento do servidor.
     *
     * @throws IllegalArgumentException se não encontrado
     */
    @Transactional
    public void excluir(Long id) {
        TratamentoPonto entity = tratamentoRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Tratamento nao encontrado: " + id));

        // Remove arquivo do disco
        if (entity.getDocumentoPath() != null) {
            removerArquivo(entity.getDocumentoPath());
        }

        tratamentoRepo.deleteById(id);
        log.info("Tratamento excluido: id={} PIS={} data={}",
                id, entity.getPisFormatado(), entity.getData());
    }

    // ─────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────

    private void validarRequest(TratamentoPontoRequestDTO req) {
        if (req.getPis() == null || req.getPis().isBlank()) {
            throw new IllegalArgumentException("PIS obrigatorio.");
        }
        if (req.getData() == null || req.getData().isBlank()) {
            throw new IllegalArgumentException("Data obrigatoria.");
        }
        if (req.getOcorrencia() == null
                || !req.getOcorrencia().matches("[IiDdPp]")) {
            throw new IllegalArgumentException(
                    "Ocorrencia invalida. Use I, D ou P.");
        }
        // I e D exigem horário e motivo
        String oc = req.getOcorrencia().toUpperCase();
        if (("I".equals(oc) || "D".equals(oc))) {
            if (req.getHorario() == null || req.getHorario().isBlank()) {
                throw new IllegalArgumentException(
                        "Horario obrigatorio para ocorrencia " + oc);
            }
            if (req.getMotivo() == null || req.getMotivo().isBlank()) {
                throw new IllegalArgumentException(
                        "Motivo obrigatorio para ocorrencia " + oc);
            }
        }
    }

    /**
     * Salva o arquivo em:
     * {tratamentosPath}/{pis}/{yyyy-MM-dd}/{timestamp}_{nomeOriginal}
     */
    private String salvarArquivo(String pis, LocalDate data,
                                  MultipartFile arquivo)
            throws IOException {

        String dir = tratamentosPath + File.separator
                + pis + File.separator
                + data.toString();

        Files.createDirectories(Paths.get(dir));

        String nomeUnico = System.currentTimeMillis()
                + "_" + sanitizarNome(arquivo.getOriginalFilename());

        Path destino = Paths.get(dir, nomeUnico);
        arquivo.transferTo(destino.toFile());

        return destino.toString();
    }

    private void removerArquivo(String caminho) {
        try {
            Files.deleteIfExists(Paths.get(caminho));
        } catch (IOException e) {
            log.warn("Nao foi possivel remover arquivo: {}", caminho);
        }
    }

    private boolean isFormatoAceito(String contentType) {
        return contentType != null && (
                contentType.equals("application/pdf")
                || contentType.equals("image/jpeg")
                || contentType.equals("image/jpg")
                || contentType.equals("image/png"));
    }

    private String sanitizarNome(String nome) {
        if (nome == null) return "documento";
        return nome.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String nomeFuncionario(String pisFormatado) {
        return usuarioRepo.findByPisFormatado(pisFormatado)
                .map(UsuarioPonto::getName)
                .orElse("Nao identificado");
    }

    private String descricaoOcorrencia(String oc) {
        if (oc == null) return "-";
        return switch (oc.toUpperCase()) {
            case "I" -> "Incluido";
            case "D" -> "Desconsiderado";
            case "P" -> "Pre-assinalacao de repouso";
            default  -> oc;
        };
    }

    private TratamentoPontoResponseDTO toDto(TratamentoPonto e) {
        String url = e.getDocumentoPath() != null
                ? "/api/tratamentos/" + e.getId() + "/documento"
                : null;

        return new TratamentoPontoResponseDTO(
                e.getId(),
                e.getPisFormatado(),
                nomeFuncionario(e.getPisFormatado()),
                e.getData() != null
                        ? e.getData().format(FMT_SAIDA) : null,
                e.getHorario(),
                e.getOcorrencia(),
                descricaoOcorrencia(e.getOcorrencia()),
                e.getMotivo(),
                e.getDocumentoNome(),
                url,
                e.getDocumentoTipo(),
                e.getCriadoEm() != null
                        ? e.getCriadoEm().format(FMT_SAIDA_DT) : null
        );
    }

    private String normalizarPis(String pis) {
        if (pis == null || pis.isBlank()) return "000000000000";
        String d = pis.replaceAll("\\D", "");
        return String.format("%012d", Long.parseLong(d));
    }
}