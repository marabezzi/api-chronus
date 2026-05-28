package br.com.atom.api_chronus.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

/**
 * Dados da empresa para o cabeçalho do AFDT.
 * Lidos do application.properties via @ConfigurationProperties.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "empresa")
public class AfdtEmpresaConfig {

    /** CNPJ da empresa (14 dígitos sem formatação) */
    private String cnpj;

    /** Razão social (até 150 chars) */
    private String razaoSocial;

    /** CEI do empregador (12 dígitos, opcional) */
    private String cei = "000000000000";

    /** Endereço do local de prestação de serviços */
    private String local;

    /** Número de fabricação do REP (17 dígitos) */
    private String numFabricacao = "00000000000000000";
}