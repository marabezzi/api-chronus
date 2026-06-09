package br.com.atom.api_chronus.service;

import br.com.atom.api_chronus.entity.ConfiguracaoSistema;
import br.com.atom.api_chronus.repository.ConfiguracaoSistemaRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Serviço de configurações do sistema.
 *
 * Ao iniciar, popula o banco com as configurações padrão
 * se ainda não existirem.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfiguracaoService {

    private final ConfiguracaoSistemaRepository repo;

    // ── Chaves de configuração ────────────────────────────────────────────

    // SYNC
    public static final String SYNC_INTERVALO   = "sync.intervalo.minutos";
    public static final String SYNC_HABILITADO  = "sync.habilitado";

    // EMPRESA
    public static final String EMP_RAZAO_SOCIAL = "empresa.razao.social";
    public static final String EMP_CNPJ         = "empresa.cnpj";
    public static final String EMP_CEI          = "empresa.cei";
    public static final String EMP_ENDERECO     = "empresa.endereco";
    public static final String EMP_NUM_FAB      = "empresa.num.fabricacao";

    // EMAIL
    public static final String EMAIL_HABILITADO = "email.habilitado";
    public static final String EMAIL_HOST       = "email.smtp.host";
    public static final String EMAIL_PORT       = "email.smtp.port";
    public static final String EMAIL_USERNAME   = "email.smtp.username";
    public static final String EMAIL_PASSWORD   = "email.smtp.password";
    public static final String EMAIL_FROM       = "email.from";
    public static final String EMAIL_TLS        = "email.smtp.tls";

    // GERAL
    public static final String GERAL_TIMEZONE   = "geral.timezone";
    public static final String GERAL_NOME_APP   = "geral.nome.aplicacao";

    // ─────────────────────────────────────────────────────────────────────
    // INICIALIZAÇÃO
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Popula as configurações padrão no banco na primeira inicialização.
     * Nunca sobrescreve valores já existentes.
     */
    @PostConstruct
    @Transactional
    public void inicializarConfiguracoesPadrao() {
        salvarPadrao(SYNC_INTERVALO,  "5",
                "Intervalo de sincronização em minutos",
                "SYNC", false);
        salvarPadrao(SYNC_HABILITADO, "true",
                "Habilita sincronização automática com o relógio",
                "SYNC", false);

        salvarPadrao(EMP_RAZAO_SOCIAL, "",
                "Razão social da empresa",
                "EMPRESA", false);
        salvarPadrao(EMP_CNPJ, "",
                "CNPJ da empresa (somente dígitos)",
                "EMPRESA", false);
        salvarPadrao(EMP_CEI, "000000000000",
                "CEI da obra (se aplicável)",
                "EMPRESA", false);
        salvarPadrao(EMP_ENDERECO, "",
                "Endereço do local de prestação de serviço",
                "EMPRESA", false);
        salvarPadrao(EMP_NUM_FAB, "00000000000000000",
                "Número de fabricação do REP",
                "EMPRESA", false);

        salvarPadrao(EMAIL_HABILITADO, "false",
                "Habilita envio de comprovantes por e-mail",
                "EMAIL", false);
        salvarPadrao(EMAIL_HOST, "",
                "Servidor SMTP. Ex: mail.suaempresa.com.br",
                "EMAIL", false);
        salvarPadrao(EMAIL_PORT, "587",
                "Porta SMTP. Padrão TLS: 587, SSL: 465",
                "EMAIL", false);
        salvarPadrao(EMAIL_USERNAME, "",
                "Usuário do servidor SMTP",
                "EMAIL", false);
        salvarPadrao(EMAIL_PASSWORD, "",
                "Senha do servidor SMTP",
                "EMAIL", true);
        salvarPadrao(EMAIL_FROM, "",
                "Remetente. Ex: Chronus Ponto <ponto@empresa.com.br>",
                "EMAIL", false);
        salvarPadrao(EMAIL_TLS, "true",
                "Habilita TLS/STARTTLS no SMTP",
                "EMAIL", false);

        salvarPadrao(GERAL_TIMEZONE, "America/Sao_Paulo",
                "Fuso horário. Ex: America/Sao_Paulo",
                "GERAL", false);
        salvarPadrao(GERAL_NOME_APP, "Chronus",
                "Nome da aplicação exibido nos e-mails e PDFs",
                "GERAL", false);

                // WHATSAPP
        salvarPadrao("whatsapp.habilitado",         "false",
        "Habilita notificacoes WhatsApp via Evolution API",
        "WHATSAPP", false);
        salvarPadrao("whatsapp.evolution.url",      "http://evolution-api:8080",
        "URL da Evolution API",
        "WHATSAPP", false);
        salvarPadrao("whatsapp.evolution.apikey",   "chronus_evolution_key",
        "API Key da Evolution API",
        "WHATSAPP", true);
        salvarPadrao("whatsapp.evolution.instancia","chronus",
        "Nome da instancia no Evolution API",
        "WHATSAPP", false);
        salvarPadrao("whatsapp.resumo.hora",        "18",
        "Hora de envio do resumo diario (0-23)",
        "WHATSAPP", false);

        log.info("Configurações do sistema inicializadas.");
    }

    // ─────────────────────────────────────────────────────────────────────
    // CONSULTAS
    // ─────────────────────────────────────────────────────────────────────

    /** Lista todas as configurações de uma categoria. */
    public List<ConfiguracaoSistema> listarPorCategoria(String categoria) {
        return repo.findByCategoriaOrderByChaveAsc(
                categoria.toUpperCase());
    }

    /** Lista todas as configurações agrupadas por categoria. */
    public Map<String, List<ConfiguracaoSistema>> listarTodas() {
        return repo.findAll().stream()
                .collect(Collectors.groupingBy(
                        ConfiguracaoSistema::getCategoria));
    }

    /** Lê o valor de uma configuração. Retorna o padrão se não existir. */
    public String get(String chave, String padrao) {
        return repo.findByChave(chave)
                .map(ConfiguracaoSistema::getValor)
                .filter(v -> v != null && !v.isBlank())
                .orElse(padrao);
    }

    /** Lê o valor como inteiro. */
    public int getInt(String chave, int padrao) {
        try {
            return Integer.parseInt(get(chave, String.valueOf(padrao)));
        } catch (NumberFormatException e) {
            return padrao;
        }
    }

    /** Lê o valor como boolean. */
    public boolean getBool(String chave, boolean padrao) {
        return Boolean.parseBoolean(get(chave, String.valueOf(padrao)));
    }

    // ── Atalhos ───────────────────────────────────────────────────────────

    public int     getSyncIntervalo()    { return getInt(SYNC_INTERVALO,  5);           }
    public boolean isSyncHabilitado()   { return getBool(SYNC_HABILITADO, true);        }
    public boolean isEmailHabilitado()  { return getBool(EMAIL_HABILITADO, false);      }
    public String  getTimezone()        { return get(GERAL_TIMEZONE, "America/Sao_Paulo"); }
    public String  getNomeApp()         { return get(GERAL_NOME_APP, "Chronus");        }
    public String  getEmailFrom()       { return get(EMAIL_FROM, "");                  }
    public String  getEmailHost()       { return get(EMAIL_HOST, "");                  }
    public int     getEmailPort()       { return getInt(EMAIL_PORT, 587);               }
    public String  getEmailUsername()   { return get(EMAIL_USERNAME, "");              }
    public String  getEmailPassword()   { return get(EMAIL_PASSWORD, "");              }
    public boolean isEmailTls()         { return getBool(EMAIL_TLS, true);              }
    public String  getEmpresaNome()     { return get(EMP_RAZAO_SOCIAL, "");            }
    public String  getEmpresaCnpj()     { return get(EMP_CNPJ, "");                    }
    public String  getEmpresaEndereco() { return get(EMP_ENDERECO, "");               }
    public String  getEmpresaCei()      { return get(EMP_CEI, "000000000000");         }
    public String  getEmpresaNumFab()   { return get(EMP_NUM_FAB, "00000000000000000");}

    public boolean isWhatsappHabilitado() {
        return getBool("whatsapp.habilitado", false);
    }
    public String getWhatsappUrl() {
        return get("whatsapp.evolution.url",
                "http://evolution-api:8080");
    }
    public String getWhatsappApiKey() {
        return get("whatsapp.evolution.apikey",
                "chronus_evolution_key");
    }
    public String getWhatsappInstancia() {
        return get("whatsapp.evolution.instancia", "chronus");
    }
    public int getWhatsappResumoHora() {
        return getInt("whatsapp.resumo.hora", 18);
    }

    // ─────────────────────────────────────────────────────────────────────
    // ATUALIZAÇÃO
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Atualiza o valor de uma configuração.
     *
     * @throws IllegalArgumentException se a chave não existir
     */
    @Transactional
    public ConfiguracaoSistema atualizar(String chave, String novoValor) {
        ConfiguracaoSistema config = repo.findByChave(chave)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Configuracao nao encontrada: " + chave));

        config.setValor(novoValor);
        config.setUpdatedAt(LocalDateTime.now());
        repo.save(config);

        log.info("Configuracao atualizada: {} = {}",
                chave,
                config.getSensivel() ? "***" : novoValor);

        return config;
    }

    /**
     * Atualiza múltiplas configurações de uma vez.
     * Ignora chaves que não existem.
     */
    @Transactional
    public void atualizarLote(Map<String, String> valores) {
        valores.forEach((chave, valor) -> {
            try {
                atualizar(chave, valor);
            } catch (IllegalArgumentException e) {
                log.warn("Chave ignorada no lote: {}", chave);
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────
    // HELPER
    // ─────────────────────────────────────────────────────────────────────

    private void salvarPadrao(String chave, String valor,
                               String descricao, String categoria,
                               boolean sensivel) {
        if (repo.findByChave(chave).isEmpty()) {
            ConfiguracaoSistema c = new ConfiguracaoSistema();
            c.setChave(chave);
            c.setValor(valor);
            c.setDescricao(descricao);
            c.setCategoria(categoria);
            c.setSensivel(sensivel);
            c.setUpdatedAt(LocalDateTime.now());
            repo.save(c);
        }
    }
}