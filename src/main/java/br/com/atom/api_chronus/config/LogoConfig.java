package br.com.atom.api_chronus.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configurações dos logos usados no PDF do Espelho de Ponto.
 * Os arquivos PNG devem estar no volume mapeado em /app/logos/
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "logo")
public class LogoConfig {

    /** Caminho do logo da empresa no container: /app/logos/empresa.png */
    private String empresaPath = "/app/logos/empresa.png";

    /** Caminho do logo do Chronus no container: /app/logos/chronus.png */
    private String chronusPath = "/app/logos/chronus.png";
}