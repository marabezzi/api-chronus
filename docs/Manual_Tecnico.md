# Chronus API — Manual Técnico e Guia de Uso

**Versão:** 1.0.0  
**Data:** Junho/2026  
**Empresa:** JOSE NATAL CLERICE -ME  
**Sistema:** Registro Eletrônico de Ponto — iDClass ControlID

---

## Sumário

1. Visão Geral
2. Stack Tecnológica
3. Arquitetura
4. Instalação e Configuração
5. Banco de Dados
6. Endpoints — Autenticação
7. Endpoints — Sincronização
8. Endpoints — Espelho de Ponto
9. Endpoints — Espelho MTE (Portaria 1510)
10. Endpoints — Relatório de Horas
11. Endpoints — Funcionários (CRUD)
12. Endpoints — Tratamentos de Ponto
13. Endpoints — Configurações do Sistema
14. Notificações — E-mail
15. Notificações — WhatsApp (Evolution API)
16. Referência Rápida de Endpoints

---

## 1. Visão Geral

O **Chronus API** é um sistema de gerenciamento de ponto eletrônico desenvolvido em Java Spring Boot, integrado ao relógio biométrico **iDClass da ControlID**.

O sistema realiza sincronização incremental das batidas de ponto, gera espelhos de ponto conforme a Portaria 1510/MTE, envia comprovantes por e-mail e WhatsApp, e oferece um CRUD completo de funcionários com dados extras não disponíveis no relógio.

**Funcionalidades principais:**

- Sincronização automática com o relógio iDClass a cada N minutos (configurável)
- Geração de AFD e AFDT conforme Portaria 1510/MTE
- Espelho de ponto em JSON e PDF (modelo próprio e modelo oficial MTE)
- Relatório de horas por funcionário e período
- CRUD completo de funcionários (CPF, RG, endereço, salário, supervisor, WhatsApp)
- Tratamentos de ponto D/I/P com upload de comprovantes
- Envio de comprovante de batida por e-mail
- Notificações WhatsApp via Evolution API (por batida ou resumo diário)
- Painel de configurações gerenciado via API (sem necessidade de reiniciar)

---

## 2. Stack Tecnológica

| Componente | Tecnologia | Versão |
|---|---|---|
| Linguagem | Java | 21 |
| Framework | Spring Boot | 4.0.6 |
| Build | Maven | 3.x |
| Banco de Dados | PostgreSQL | 16 |
| ORM | Spring Data JPA / Hibernate | - |
| PDF | OpenPDF (LibrePDF fork) | 1.3.43 |
| E-mail | Spring Mail / JavaMailSender | - |
| WhatsApp | Evolution API (self-hosted) | latest |
| HTTP Client | Java 11 HttpClient | - |
| Containerização | Docker + Docker Compose | - |
| Serialização | Jackson | - |
| Boilerplate | Lombok | - |

---

## 3. Arquitetura

```
┌─────────────────────────────────────────────────────┐
│                    DOCKER COMPOSE                    │
│                                                      │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────┐ │
│  │ chronus-api  │  │  chronus-db  │  │ evolution │ │
│  │ Spring Boot  │◄─┤  PostgreSQL  │  │    api    │ │
│  │   :8081      │  │   :5432      │  │   :8082   │ │
│  └──────┬───────┘  └──────────────┘  └─────┬─────┘ │
│         │                                   │       │
└─────────┼───────────────────────────────────┼───────┘
          │                                   │
          ▼                                   ▼
   ┌─────────────┐                    ┌──────────────┐
   │  iDClass    │                    │  WhatsApp    │
   │  ControlID  │                    │  (QR Code)   │
   │  :443 HTTPS │                    └──────────────┘
   └─────────────┘
```

### Estrutura de Pacotes

```
br.com.atom.api_chronus/
├── config/          # Configurações Spring (iDClass, HTTP, Empresa, Logo)
├── controller/      # Controllers REST
├── dto/             # Data Transfer Objects
├── entity/          # Entidades JPA
├── repository/      # Repositórios Spring Data
└── service/         # Lógica de negócio
```

### Fluxo de Sincronização

```
Relógio iDClass ──► load_users.fcgi ──► UsuarioPonto (banco)
Relógio iDClass ──► get_afd.fcgi   ──► BatidaPonto  (banco)
                                              │
                                    ┌─────────┴─────────┐
                                    │                   │
                              E-mail SMTP         WhatsApp
                              (comprovante)    (Evolution API)
```

---

## 4. Instalação e Configuração

### Pré-requisitos

- Docker Desktop instalado
- Java 21 + Maven (para compilação local)
- Acesso à rede do relógio iDClass

### Passos de instalação

**1. Clone o repositório e configure o .env:**

```bash
cp .env.example .env
# Edite o .env com os dados do seu ambiente
```

**2. Arquivo `.env` completo:**

```properties
# Relógio iDClass
IDCLASS_HOST=192.168.1.201
IDCLASS_PORT=443
IDCLASS_USER=admin
IDCLASS_PASSWORD=admin

# PostgreSQL
POSTGRES_DB=chronus
POSTGRES_USER=chronus
POSTGRES_PASSWORD=chronus_senha_segura

# Empresa (valores iniciais — atualize via /api/config)
EMPRESA_CNPJ=74433293000137
EMPRESA_RAZAO_SOCIAL=JOSE NATAL CLERICE -ME
EMPRESA_LOCAL=RUA EPITACIO PESSOA, 201, CENTRO, SAO MANUEL-SP

# Evolution API (WhatsApp)
EVOLUTION_API_KEY=chronus_evolution_key
EVOLUTION_API_URL=http://evolution-api:8080
EVOLUTION_INSTANCIA=chronus

# E-mail SMTP
MAIL_HOST=mail.suaempresa.com.br
MAIL_PORT=587
MAIL_USERNAME=chronus@suaempresa.com.br
MAIL_PASSWORD=sua_senha
MAIL_FROM=Chronus Ponto <chronus@suaempresa.com.br>
```

**3. Compile e suba os containers:**

```bash
./mvnw clean package -DskipTests
docker-compose up -d
```

**4. Verifique se a API está rodando:**

```bash
curl http://localhost:8081/api/funcionarios
```

### Setup do WhatsApp (Evolution API)

```bash
# Cria a instância
curl -X POST http://localhost:8082/instance/create \
  -H "Content-Type: application/json" \
  -H "apikey: chronus_evolution_key" \
  -d '{"instanceName":"chronus","qrcode":true}'

# Obtém o QR Code (escaneie com o WhatsApp do responsável)
curl http://localhost:8082/instance/connect/chronus \
  -H "apikey: chronus_evolution_key"
```

---

## 5. Banco de Dados

### Tabelas

#### `usuarios_ponto` — Funcionários

| Coluna | Tipo | Descrição |
|---|---|---|
| id | BIGSERIAL PK | ID interno |
| pis | BIGINT UNIQUE | PIS numérico |
| pis_formatado | VARCHAR(12) | PIS com zeros à esquerda |
| name | VARCHAR(200) | Nome completo |
| code | INTEGER | Código no relógio |
| registration | INTEGER | Matrícula |
| cpf | VARCHAR(11) | CPF sem formatação |
| rg | VARCHAR(20) | RG |
| endereco | VARCHAR(300) | Endereço completo |
| cargo | VARCHAR(100) | Cargo/função |
| setor | VARCHAR(100) | Setor/departamento |
| email | VARCHAR(150) | E-mail |
| celular | VARCHAR(20) | Celular |
| salario | DECIMAL(10,2) | Salário |
| supervisor | BOOLEAN | É supervisor? |
| supervisor_id | BIGINT FK | Referência ao supervisor |
| whatsapp_numero | VARCHAR(20) | Número WhatsApp |
| whatsapp_habilitado | BOOLEAN | Notificações habilitadas |
| whatsapp_preferencia | VARCHAR(20) | CADA_BATIDA ou RESUMO_DIA |
| data_admissao | DATE | Data de admissão |
| ativo | BOOLEAN | Funcionário ativo? |
| data_inativacao | DATE | Data de inativação |
| ultima_sincronizacao | TIMESTAMP | Última sync com relógio |

#### `batidas_ponto` — Registros de Ponto

| Coluna | Tipo | Descrição |
|---|---|---|
| id | BIGSERIAL PK | ID interno |
| nsr | BIGINT UNIQUE | Número Sequencial de Registro |
| pis | VARCHAR(12) | PIS do funcionário |
| date_time | TIMESTAMP | Data e hora da batida |
| tipo | INTEGER | Tipo (do relógio) |
| tipo_descricao | VARCHAR(50) | Descrição do tipo |
| linha_original | VARCHAR(200) | Linha original do AFD |
| email_enviado | BOOLEAN | Comprovante e-mail enviado? |
| criado_em | TIMESTAMP | Momento da sincronização |

#### `tratamentos_ponto` — Tratamentos D/I/P

| Coluna | Tipo | Descrição |
|---|---|---|
| id | BIGSERIAL PK | ID interno |
| pis_formatado | VARCHAR(12) | PIS do funcionário |
| data | DATE | Data do tratamento |
| horario | VARCHAR(5) | Horário (HH:mm) |
| ocorrencia | VARCHAR(1) | I, D ou P |
| motivo | VARCHAR(500) | Motivo do tratamento |
| documento_nome | VARCHAR(255) | Nome do arquivo |
| documento_path | VARCHAR(500) | Caminho no servidor |
| documento_tipo | VARCHAR(50) | MIME type |
| criado_em | TIMESTAMP | Data do registro |

#### `afdt_gerados` — AFDTs Gerados

| Coluna | Tipo | Descrição |
|---|---|---|
| id | BIGSERIAL PK | ID interno |
| data_geracao | TIMESTAMP | Quando foi gerado |
| data_inicial | DATE | Início do período |
| data_final | DATE | Fim do período |
| conteudo | TEXT | Conteúdo do AFDT |

#### `logs_sincronizacao` — Logs de Sync

| Coluna | Tipo | Descrição |
|---|---|---|
| id | BIGSERIAL PK | ID interno |
| tipo | VARCHAR(20) | COMPLETA, AFD ou USUARIOS |
| status | VARCHAR(20) | EXECUTANDO, SUCESSO, ERRO |
| data_inicio | TIMESTAMP | Início da sync |
| data_fim | TIMESTAMP | Fim da sync |
| total_registros | INTEGER | Registros processados |
| ultimo_nsr | BIGINT | Maior NSR sincronizado |
| mensagem | TEXT | Mensagem de erro (se houver) |

#### `configuracoes_sistema` — Configurações

| Coluna | Tipo | Descrição |
|---|---|---|
| id | BIGSERIAL PK | ID interno |
| chave | VARCHAR(100) UNIQUE | Chave da configuração |
| valor | VARCHAR(500) | Valor |
| descricao | VARCHAR(300) | Descrição legível |
| categoria | VARCHAR(20) | SYNC, EMPRESA, EMAIL, WHATSAPP, GERAL |
| sensivel | BOOLEAN | Valor sensível (senha)? |
| updated_at | TIMESTAMP | Última atualização |

---

## 6. Endpoints — Autenticação

### `POST /api/auth/login`
Autentica no relógio iDClass e armazena a sessão.

**Body:**
```json
{
  "login": "admin",
  "password": "admin"
}
```

**Resposta:**
```json
{
  "session": "abc123",
  "mensagem": "Login realizado com sucesso"
}
```

### `GET /api/auth/status`
Retorna o status da sessão atual.

### `POST /api/auth/renovar`
Renova a sessão antes do timeout (480 segundos).

---

## 7. Endpoints — Sincronização

### `POST /api/sync/completo`
Sincroniza usuários + batidas do relógio.

### `POST /api/sync/batidas`
Sincroniza somente batidas (incremental por NSR).

```bash
curl -X POST http://localhost:8081/api/sync/batidas
```

**Resposta:**
```json
{
  "tipo": "AFD",
  "status": "SUCESSO",
  "totalRegistros": 12,
  "ultimoNsr": 19601,
  "dataInicio": "2026-06-09T10:00:00",
  "dataFim": "2026-06-09T10:00:03"
}
```

### `POST /api/sync/usuarios`
Sincroniza somente usuários do relógio.

### `GET /api/sync/logs`
Lista os últimos logs de sincronização.

---

## 8. Endpoints — Espelho de Ponto

### `POST /api/espelho/pis`
Gera espelho de ponto por PIS em JSON.

**Body:**
```json
{
  "pis": "012952592162",
  "dataInicial": "01052026",
  "dataFinal": "31052026"
}
```

### `POST /api/espelho/nome`
Gera espelho de ponto por nome em JSON.

**Body:**
```json
{
  "nome": "DONATA",
  "dataInicial": "01052026",
  "dataFinal": "31052026"
}
```

### `POST /api/espelho/pis/pdf`
Gera PDF do espelho por PIS. Retorna `application/pdf`.

### `POST /api/espelho/nome/pdf`
Gera PDF do espelho por nome. Retorna `application/pdf`.

---

## 9. Endpoints — Espelho MTE (Portaria 1510/Anexo II)

Espelhos fiéis ao modelo oficial do Ministério do Trabalho e Emprego.

### `POST /api/mte/espelho/pis`
JSON do espelho fiel ao Anexo II por PIS.

### `POST /api/mte/espelho/nome`
JSON do espelho fiel ao Anexo II por nome.

### `POST /api/mte/espelho/pis/pdf`
PDF fiel ao Anexo II da Portaria 1510/MTE por PIS.

```bash
curl -X POST http://localhost:8081/api/mte/espelho/pis/pdf \
  -H "Content-Type: application/json" \
  -d '{"pis":"012952592162","dataInicial":"01052026","dataFinal":"31052026"}' \
  -o EspelhoMTE_DONATA.pdf
```

### `POST /api/mte/espelho/nome/pdf`
PDF fiel ao Anexo II por nome.

### `POST /api/mte/espelho/todos/pdf`
PDF único com espelhos de TODOS os funcionários ativos.

```bash
curl -X POST http://localhost:8081/api/mte/espelho/todos/pdf \
  -H "Content-Type: application/json" \
  -d '{"dataInicial":"01052026","dataFinal":"31052026"}' \
  -o EspelhoMTE_Todos.pdf
```

---

## 10. Endpoints — Relatório de Horas

### `POST /api/relatorio/horas/todos`
Relatório JSON de todos os funcionários no período.

**Body:**
```json
{
  "dataInicial": "01052026",
  "dataFinal": "31052026"
}
```

### `POST /api/relatorio/horas/todos/pdf`
PDF do relatório de todos os funcionários.

### `POST /api/relatorio/horas/funcionario`
Relatório JSON de um funcionário.

**Body:**
```json
{
  "pis": "012952592162",
  "dataInicial": "01052026",
  "dataFinal": "31052026"
}
```

### `POST /api/relatorio/horas/funcionario/pdf`
PDF do relatório individual.

```bash
curl -X POST http://localhost:8081/api/relatorio/horas/funcionario/pdf \
  -H "Content-Type: application/json" \
  -d '{"pis":"012952592162","dataInicial":"01052026","dataFinal":"31052026"}' \
  -o Relatorio_DONATA.pdf
```

---

## 11. Endpoints — Funcionários (CRUD)

### `GET /api/funcionarios`
Lista todos os funcionários ativos ordenados por nome.

### `GET /api/funcionarios/{pis}`
Busca funcionário por PIS.

```bash
curl http://localhost:8081/api/funcionarios/012952592162
```

### `GET /api/funcionarios/nome/{nome}`
Busca por nome parcial (case-insensitive).

### `GET /api/funcionarios/supervisores`
Lista todos os supervisores ativos.

### `GET /api/funcionarios/supervisores/setor/{setor}`
Lista supervisores de um setor específico.

### `GET /api/funcionarios/{pis}/subordinados`
Lista subordinados de um supervisor.

### `POST /api/funcionarios`
Cria novo funcionário.

**Body completo:**
```json
{
  "pis":                "099999999999",
  "nome":               "JOAO DA SILVA",
  "matricula":          100,
  "cpf":                "12345678901",
  "rg":                 "12.345.678-9",
  "endereco":           "Rua Exemplo, 100 - Centro",
  "cargo":              "Auxiliar Administrativo",
  "setor":              "Administrativo",
  "email":              "joao@empresa.com.br",
  "celular":            "(14) 99999-9999",
  "salario":            2500.00,
  "supervisor":         false,
  "supervisorPis":      "012952592162",
  "whatsappNumero":     "5514999999999",
  "whatsappHabilitado": true,
  "whatsappPreferencia":"CADA_BATIDA",
  "dataAdmissao":       "01012024",
  "observacoes":        "Contrato por prazo determinado"
}
```

**Preferências WhatsApp:**
- `CADA_BATIDA` — mensagem a cada registro de ponto
- `RESUMO_DIA` — resumo único no horário configurado

### `PUT /api/funcionarios/{pis}`
Atualiza dados do funcionário.

### `DELETE /api/funcionarios/{pis}`
Inativa o funcionário (soft delete — preserva histórico).

### `PATCH /api/funcionarios/{pis}/reativar`
Reativa um funcionário inativo.

---

## 12. Endpoints — Tratamentos de Ponto

Gerencia as ocorrências D/I/P conforme Portaria 1510/MTE Anexo II.

**Tipos de ocorrência:**
- `I` — Horário incluído (horário + motivo obrigatórios)
- `D` — Horário desconsiderado (horário + motivo obrigatórios)
- `P` — Pré-assinalação do período de repouso (horário e motivo opcionais)

### `GET /api/tratamentos/{pis}`
Lista todos os tratamentos de um funcionário.

### `GET /api/tratamentos/{pis}/data/{ddMMyyyy}`
Lista tratamentos de uma data específica.

```bash
curl http://localhost:8081/api/tratamentos/012952592162/data/06052026
```

### `GET /api/tratamentos/{pis}/periodo?di={ddMMyyyy}&df={ddMMyyyy}`
Lista tratamentos em um período.

### `GET /api/tratamentos/id/{id}`
Busca tratamento por ID.

### `POST /api/tratamentos`
Cria novo tratamento.

```json
{
  "pis":        "012952592162",
  "data":       "06052026",
  "horario":    "17:09",
  "ocorrencia": "D",
  "motivo":     "Registro extra por falha no sensor biométrico"
}
```

### `POST /api/tratamentos/{id}/documento`
Anexa comprovante ao tratamento (multipart/form-data).
Formatos aceitos: **PDF, JPG, PNG**.

```bash
curl -X POST http://localhost:8081/api/tratamentos/1/documento \
  -F "arquivo=@atestado.pdf"
```

### `GET /api/tratamentos/{id}/documento`
Baixa o comprovante anexado.

### `DELETE /api/tratamentos/{id}`
Remove o tratamento e seu documento.

---

## 13. Endpoints — Configurações do Sistema

Todas as configurações são gerenciadas em tempo real via API — sem necessidade de reiniciar a aplicação.

### `GET /api/config`
Lista todas as configurações agrupadas por categoria.

### `GET /api/config/{categoria}`
Lista configurações de uma categoria.
Categorias: `SYNC`, `EMPRESA`, `EMAIL`, `WHATSAPP`, `GERAL`

```bash
curl http://localhost:8081/api/config/SYNC
```

### `PUT /api/config/{chave}`
Atualiza uma configuração.

```bash
# Muda intervalo de sync para 10 minutos
curl -X PUT http://localhost:8081/api/config/sync.intervalo.minutos \
  -H "Content-Type: application/json" \
  -d '{"valor": "10"}'
```

### `PUT /api/config/lote`
Atualiza múltiplas configurações de uma vez.

```bash
curl -X PUT http://localhost:8081/api/config/lote \
  -H "Content-Type: application/json" \
  -d '{
    "empresa.razao.social": "MINHA EMPRESA LTDA",
    "empresa.cnpj":         "12345678000199",
    "email.habilitado":     "true",
    "sync.intervalo.minutos": "5"
  }'
```

### Chaves de Configuração Disponíveis

| Chave | Categoria | Padrão | Descrição |
|---|---|---|---|
| sync.intervalo.minutos | SYNC | 5 | Intervalo de sync em minutos |
| sync.habilitado | SYNC | true | Habilita sync automática |
| empresa.razao.social | EMPRESA | - | Razão social |
| empresa.cnpj | EMPRESA | - | CNPJ |
| empresa.cei | EMPRESA | 000000000000 | CEI |
| empresa.endereco | EMPRESA | - | Endereço |
| empresa.num.fabricacao | EMPRESA | - | Número de fabricação REP |
| email.habilitado | EMAIL | false | Habilita e-mails |
| email.smtp.host | EMAIL | - | Servidor SMTP |
| email.smtp.port | EMAIL | 587 | Porta SMTP |
| email.smtp.username | EMAIL | - | Usuário SMTP |
| email.smtp.password | EMAIL | - | Senha SMTP (sensível) |
| email.from | EMAIL | - | Remetente |
| email.smtp.tls | EMAIL | true | Habilita TLS |
| whatsapp.habilitado | WHATSAPP | false | Habilita WhatsApp |
| whatsapp.evolution.url | WHATSAPP | http://evolution-api:8080 | URL Evolution API |
| whatsapp.evolution.apikey | WHATSAPP | - | API Key (sensível) |
| whatsapp.evolution.instancia | WHATSAPP | chronus | Nome da instância |
| whatsapp.resumo.hora | WHATSAPP | 18 | Hora do resumo diário |
| geral.timezone | GERAL | America/Sao_Paulo | Fuso horário |
| geral.nome.aplicacao | GERAL | Chronus | Nome da aplicação |

---

## 14. Notificações — E-mail

### Configuração SMTP

Configure via `/api/config/lote`:

```json
{
  "email.habilitado":    "true",
  "email.smtp.host":     "mail.suaempresa.com.br",
  "email.smtp.port":     "587",
  "email.smtp.username": "chronus@suaempresa.com.br",
  "email.smtp.password": "sua_senha",
  "email.from":          "Chronus Ponto <chronus@suaempresa.com.br>",
  "email.smtp.tls":      "true"
}
```

### Funcionamento

Para cada nova batida sincronizada, o sistema envia automaticamente um e-mail ao funcionário se:

1. `email.habilitado` = true nas configurações
2. O funcionário tem e-mail cadastrado em `/api/funcionarios`

O e-mail contém: horário da batida, data, nome, PIS e NSR formatados em HTML responsivo.

---

## 15. Notificações — WhatsApp (Evolution API)

### Setup Inicial

```bash
# 1. Suba o Evolution API (já no docker-compose)
docker-compose up -d evolution-api

# 2. Crie a instância
curl -X POST http://localhost:8082/instance/create \
  -H "apikey: chronus_evolution_key" \
  -H "Content-Type: application/json" \
  -d '{"instanceName":"chronus","qrcode":true}'

# 3. Escaneie o QR Code
curl http://localhost:8082/instance/connect/chronus \
  -H "apikey: chronus_evolution_key"
# Retorna QR Code em base64 — escaneie com o WhatsApp do responsável

# 4. Habilite no painel
curl -X PUT http://localhost:8081/api/config/lote \
  -H "Content-Type: application/json" \
  -d '{
    "whatsapp.habilitado": "true",
    "whatsapp.resumo.hora": "18"
  }'
```

### Preferências por Funcionário

```bash
# Cada batida
curl -X PUT http://localhost:8081/api/funcionarios/012952592162 \
  -H "Content-Type: application/json" \
  -d '{
    "nome":                "DONATA APARECIDA MARTINS GARCIA",
    "whatsappNumero":      "5514999999999",
    "whatsappHabilitado":  true,
    "whatsappPreferencia": "CADA_BATIDA"
  }'

# Resumo do dia (enviado às 18h por padrão)
curl -X PUT http://localhost:8081/api/funcionarios/012849194257 \
  -H "Content-Type: application/json" \
  -d '{
    "nome":                "PATRICIA APARECIDA ALVES",
    "whatsappNumero":      "5514988888888",
    "whatsappHabilitado":  true,
    "whatsappPreferencia": "RESUMO_DIA"
  }'
```

---

## 16. Referência Rápida de Endpoints

| Método | Endpoint | Descrição |
|---|---|---|
| POST | /api/auth/login | Login no relógio |
| GET | /api/auth/status | Status da sessão |
| POST | /api/sync/completo | Sync completo |
| POST | /api/sync/batidas | Sync somente batidas |
| POST | /api/sync/usuarios | Sync somente usuários |
| GET | /api/sync/logs | Logs de sincronização |
| POST | /api/espelho/pis | Espelho JSON por PIS |
| POST | /api/espelho/pis/pdf | Espelho PDF por PIS |
| POST | /api/mte/espelho/pis/pdf | Espelho MTE PDF por PIS |
| POST | /api/mte/espelho/todos/pdf | Espelho MTE PDF todos |
| POST | /api/relatorio/horas/todos | Relatório horas todos |
| POST | /api/relatorio/horas/todos/pdf | Relatório horas PDF todos |
| POST | /api/relatorio/horas/funcionario | Relatório horas individual |
| POST | /api/relatorio/horas/funcionario/pdf | Relatório horas PDF individual |
| GET | /api/funcionarios | Lista funcionários |
| GET | /api/funcionarios/{pis} | Busca por PIS |
| GET | /api/funcionarios/nome/{nome} | Busca por nome |
| GET | /api/funcionarios/supervisores | Lista supervisores |
| GET | /api/funcionarios/{pis}/subordinados | Lista subordinados |
| POST | /api/funcionarios | Cria funcionário |
| PUT | /api/funcionarios/{pis} | Atualiza funcionário |
| DELETE | /api/funcionarios/{pis} | Inativa funcionário |
| PATCH | /api/funcionarios/{pis}/reativar | Reativa funcionário |
| GET | /api/tratamentos/{pis} | Lista tratamentos |
| GET | /api/tratamentos/{pis}/data/{data} | Tratamentos por data |
| GET | /api/tratamentos/{pis}/periodo | Tratamentos por período |
| POST | /api/tratamentos | Cria tratamento |
| POST | /api/tratamentos/{id}/documento | Anexa comprovante |
| GET | /api/tratamentos/{id}/documento | Baixa comprovante |
| DELETE | /api/tratamentos/{id} | Remove tratamento |
| GET | /api/config | Lista configurações |
| GET | /api/config/{categoria} | Config por categoria |
| PUT | /api/config/{chave} | Atualiza config |
| PUT | /api/config/lote | Atualiza várias configs |
