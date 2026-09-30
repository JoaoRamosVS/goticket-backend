# GoTicket — Backend

Plataforma de venda de ingressos para eventos, com foco em **integridade e disponibilidade sob pico de demanda** (cenário "flash sale"). O backend expõe uma API REST para o ciclo completo: cadastro de eventos e lotes, autenticação, **sala de espera virtual (fila)**, reserva, pagamento via Stripe e emissão de ingressos.

> Projeto de Trabalho de Conclusão de Curso (TCC). O tema central é como impedir **overselling** e **cobrança duplicada** sob alta concorrência, e como manter o checkout **disponível** quando a demanda excede a capacidade — usando controle de concorrência no banco, idempotência e uma fila de admissão (backpressure).

---
#### Link do repositório do frontend: https://github.com/RecheEduardo/goticket-frontend/

## Índice

- [O que o projeto resolve](#o-que-o-projeto-resolve)
- [Stack](#stack)
- [Pré-requisitos](#pré-requisitos)
- [Como rodar](#como-rodar)
- [Variáveis de ambiente](#variáveis-de-ambiente)
- [Migrations e dados iniciais](#migrations-e-dados-iniciais)
- [Como funciona a fila de espera](#como-funciona-a-fila-de-espera)
- [Simular alta demanda (reproduzir a fila)](#simular-alta-demanda-reproduzir-a-fila)
- [Documentação da API (Scalar)](#documentação-da-api-scalar)
- [Endpoints](#endpoints)
- [Testes](#testes)
- [Credenciais iniciais](#credenciais-iniciais)
- [Aprendizados](#aprendizados)

---

## O que o projeto resolve

Quando um evento concorrido abre vendas, milhares de pessoas tentam comprar ao mesmo tempo. Sem os mecanismos certos, três problemas surgem:

1. **Overselling** — vender o mesmo assento duas vezes por corrida entre requisições.
2. **Cobrança duplicada** — o cliente reenvia/retenta e gera dois pedidos.
3. **Indisponibilidade** — o checkout satura e derruba a experiência de todos.

O GoTicket ataca os três:

- **Integridade do estoque:** controle de concorrência no PostgreSQL (bloqueio otimista com `@Version` + constraints), com retry e *jitter* — garante **zero overselling** mesmo sob milhares de acessos simultâneos.
- **Idempotência:** cada intenção de compra carrega uma `Idempotency-Key`; requisições repetidas devolvem o **mesmo** pedido, nunca um novo (semântica igual à da Stripe).
- **Disponibilidade:** uma **fila de espera virtual** (admission control) sobre Redis limita quantas pessoas entram no checkout ao mesmo tempo — o excedente espera de forma ordenada em vez de colapsar o sistema.

---

## Stack

| Camada | Tecnologia | Papel |
|---|---|---|
| Linguagem | **Java 25** | Definida no `pom.xml` |
| Framework | **Spring Boot** (Web, Data JPA, Security, OAuth2 Resource Server) | API, transações, segurança, agendamento |
| Banco | **PostgreSQL 16** | Persistência + garantias ACID (base da integridade) |
| Cache/Fila | **Redis 7** | Sala de espera virtual e admission control |
| Migrations | **Flyway** | Versionamento do schema |
| Pagamento | **Stripe** (modo teste) | PaymentIntent + webhooks |
| Storage | **AWS S3** (LocalStack em dev) | Imagens de eventos e mapas de setor |
| Auth | **JWT (RSA)** | Access + refresh token rotativo |
| Documentação | **springdoc-openapi** + **Scalar** | Especificação OpenAPI 3 gerada do código + UI interativa |
| Build | **Maven Wrapper** (`mvnw`) | Build/execução sem Maven global |
| Testes de carga | **k6** | Overselling, idempotência e disponibilidade |
| Infra local | **Docker Compose** | Postgres, Redis, LocalStack, Stripe CLI |

---

## Pré-requisitos

- **Docker** + **Docker Compose** — obrigatório. Sobe Postgres, Redis, LocalStack/S3, Stripe CLI e (na opção "tudo no Docker") a própria aplicação.
- **Git**.
- **Conta Stripe (modo teste)** — para as chaves de teste. A aplicação **sobe com placeholders**; apenas os fluxos reais de pagamento precisam de chaves válidas.
- **JDK 25** (ex.: Eclipse Temurin 25) — necessário **apenas** para rodar a aplicação no IntelliJ. Ao subir a aplicação com docker compose, o build Java acontece dentro do container, sem precisar de JDK na máquina.

> Maven não é necessário: o projeto inclui o wrapper (`mvnw` / `mvnw.cmd`).

---

## Como rodar

O perfil ativo padrão é `dev,docker` — no boot, o **Flyway** migra o schema e o **dev-seed** popula os dados de demonstração automaticamente. A API sobe em **http://localhost:8080** e a documentação interativa em **http://localhost:8080/scalar** (ver [Documentação da API](#documentação-da-api-scalar)).

### Opção A — Tudo no Docker (recomendado)

Sobe aplicação + infraestrutura de uma vez. Precisa **apenas de Docker** (o build Java roda dentro do container, via Dockerfile multi-stage).

```bash
git clone <URL_DO_REPOSITORIO>
cd goticket-backend
cp .env.example .env            # preencha as chaves da Stripe (teste)
docker compose up -d --build    # app + Postgres + Redis + LocalStack + Stripe CLI
```

Acompanhar o boot da aplicação: `docker compose logs -f app`.

### Opção B — Aplicação no IntelliJ (para desenvolvimento)

Para hot reload / debug pela IDE. Sobe **apenas a infraestrutura** no Docker e a aplicação no host (requer **JDK 25**).

```bash
cp .env.example .env
docker compose up -d postgres redis localstack stripe-cli   # só a infra (sem o serviço 'app')
./mvnw spring-boot:run                                        # Windows: mvnw.cmd spring-boot:run
```

> Na Opção B, **não** rode `docker compose up -d` sem listar os serviços — isso subiria também o container `app`, que colidiria na porta 8080 com a aplicação do host. Pela IntelliJ, rode a classe `BackendapiApplication`.

### Encerrar

```bash
docker compose down       # para os containers
docker compose down -v    # + apaga os dados do Postgres (reset total)
```

**Chaves JWT:** o par de desenvolvimento (`app.pub` / `app.key`) já vem versionado em `src/main/resources` — nenhuma ação necessária. *São chaves de dev, sem valor de produção; em produção o par RSA deve ser injetado por variável/secret manager, nunca versionado.*

---

## Variáveis de ambiente

As configurações sensíveis ficam num arquivo `.env` (não versionado). Use o `.env.example` como modelo:

| Variável | Descrição | Exemplo (dev) |
|---|---|---|
| `DB_URL` | JDBC do Postgres | `jdbc:postgresql://localhost:5433/GoTicketDB` |
| `DB_USER` / `DB_PASSWORD` | Credenciais do banco | `goticket` / `goticket` |
| `REDIS_HOST` / `REDIS_PORT` | Redis | `localhost` / `6379` |
| `BUCKET_NAME` / `AWS_REGION` | Bucket S3 e região | `goticket-dev` / `us-east-1` |
| `STRIPE_SECRET_KEY` | Chave secreta (teste) | `sk_test_...` |
| `STRIPE_PUBLISHABLE_KEY` | Chave publicável (teste) | `pk_test_...` |
| `STRIPE_WEBHOOK_SECRET` | Segredo do webhook | `whsec_...` |

> As credenciais de dev do Postgres já vêm preenchidas no `.env.example` (batem com o `docker-compose.yml`). Só as chaves da Stripe precisam ser trocadas pelas suas.

---

## Migrations e dados iniciais

- **Schema:** gerenciado por **Flyway** (`src/main/resources/db/migration`, `V1`…`V10`). As migrations rodam **automaticamente no boot** — não há passo manual. O JPA fica em `ddl-auto=validate` (valida o schema contra as entidades, não altera).
- **Dados de referência:** roles, status e tipos de ingresso são semeados por migration (`V2`).
- **Dados de demonstração (perfil `dev`):** `src/main/resources/db/dev-seed.sql` é carregado no boot (`spring.sql.init`) e popula um organizador de demonstração (`organizer@events.com`), venues, eventos e lotes — **não há clientes no seed** (cadastre via `POST /clients`) — incluindo o evento **"Allianz Live Experience"**, configurado para acionar a fila de espera.

---

## Como funciona a fila de espera

A fila (sala de espera virtual) é o coração do projeto. Ela só é acionada quando o evento está em **alta demanda** (tier `HIGH`); em demanda normal, a compra é direta.

**Detecção de demanda** (`demand` + job a cada 60s):
- **AUTO** — velocidade de vendas e ocupação cruzam limiares → promove para `HIGH`.
- **SCHEDULED** — evento marcado como `expected_high_demand` é **pré-armado** numa janela ao redor da abertura de vendas.
- **MANUAL** — organizador força o tier via endpoint (precedência `MANUAL > SCHEDULED > AUTO`).
- O tier vigente é espelhado no Redis (`demand:event:{id}:tier`).

**Fluxo de compra com fila ativa:**
1. `POST /events/{id}/queue` → o usuário entra numa fila ordenada (Redis *sorted set*). Recebe `WAITING` + posição.
2. `GET /events/{id}/queue/position` → *polling* da posição até virar `ADMITTED`.
3. Um job de admissão (a cada 5s) libera até `admit-batch-size` pessoas por vez, respeitando um teto de admitidos simultâneos (`max-active`) — é o **backpressure**. O admitido recebe um **token de fila** (JWT).
4. `POST /orders` com o header `X-Queue-Token` → o checkout só aceita quem foi admitido.

Assim, sob pico, o checkout recebe uma carga **limitada** (permanece rápido e íntegro) enquanto o excedente aguarda em ordem, em vez de o sistema inteiro colapsar.

---

## Simular alta demanda (reproduzir a fila)

O dev-seed já configura o evento **"Allianz Live Experience"** (id `21`) como alta demanda esperada (`expected_high_demand` + abertura de vendas "agora"), então a fila **arma sozinha ~1 min após o boot** (primeiro ciclo do job de detecção). Para reproduzir a experiência com tempo de sobra, popule a fila com usuários fictícios via Redis — assim você entra **atrás** deles e vê a posição cair.

Os comandos abaixo usam o container do Redis (`goticket_redis`), válido tanto no modo Docker quanto no host.

**1. (opcional) Armar a fila na hora** — sem esperar o job de 60s:
```bash
docker exec goticket_redis redis-cli SET demand:event:21:tier HIGH
```

**2. (recomendado) Deixar a fila andar devagar** — para dar tempo de abrir o front e observar. Reduza o lote de admissão para 5 por ciclo:
- **Docker:** adicione `GOTICKET_WAITINGROOM_ADMIT_BATCH_SIZE: "5"` ao serviço `app` no `docker-compose.yml` e recrie (`docker compose up -d`).
- **Host:** adicione `goticket.waitingroom.admit-batch-size=5` em `application-dev.properties` e reinicie a aplicação.

**3. Popular a fila com 300 usuários fictícios à frente** (score baixo = mais cedo na fila):
```bash
docker exec goticket_redis redis-cli EVAL "for i=1,tonumber(ARGV[1]) do redis.call('ZADD', KEYS[1], i, 'fake-'..i) end redis.call('SADD','waitingroom:active-events', ARGV[2]) return redis.call('ZCARD', KEYS[1])" 1 queue:event:21 300 21
```
Deve imprimir `300` (tamanho da fila).

**4. No front (ou Postman):** cadastre um cliente (`POST /clients` — o seed não traz clientes), faça login e, no evento 21, entre na fila:
- `POST /events/21/queue` → `WAITING` + posição (~301).
- `GET /events/21/queue/position` (polling) → a posição cai de 5 em 5 a cada 5s até `ADMITTED`; aí você recebe o token de fila e o checkout é liberado.

Com `admit-batch-size=5` e 300 fakes, a espera dura ~5 min. A duração segue a fórmula `tempo ≈ (nº de fakes ÷ admit-batch-size) × 5s` — ajuste o número de fakes para encurtar/alongar. **Mantenha `max-active` (500) maior que o total na fila**, senão o pool de admitidos enche e a fila congela.

**Resetar entre testes** (sem `FLUSHALL`, que apagaria o tier):
```bash
docker exec goticket_redis redis-cli DEL queue:event:21 admitted:event:21
docker exec goticket_redis redis-cli SREM waitingroom:active-events 21
```

---

## Documentação da API (Scalar)

A API é descrita em **OpenAPI 3**, gerada a partir do próprio código (springdoc-openapi), e publicada com o **Scalar**. Com a aplicação rodando:

| Recurso | URL |
|---|---|
| Documentação interativa (Scalar) | http://localhost:8080/scalar |
| Especificação OpenAPI (JSON) | http://localhost:8080/v3/api-docs |

As duas rotas são públicas (não exigem token). A URL do JSON também pode ser importada no Postman ou no Insomnia.

**O que está documentado:**
- Todos os endpoints, agrupados por domínio, com resumo, parâmetros, headers e schemas de request/response. O webhook da Stripe fica oculto, porque só a Stripe o chama.
- **Respostas de erro** no payload padrão (`ApiError`), com exemplos reais das mensagens. No checkout (`POST /orders`): `409` para estoque insuficiente (overselling), contenção na reserva e uso incompatível da `Idempotency-Key`; `403` para evento em alta demanda sem `X-Queue-Token`; `502` para falha na Stripe.
- Rotas protegidas mostram `401`/`403`; rotas públicas aparecem sem exigência de autenticação.
- Descrições e exemplos de preenchimento nos DTOs principais (`PlaceOrderRequest`, `CreateEventDTO`).

**Testar requisições pelo Scalar:**
1. Abra `POST /login` → **Test Request** e envie as credenciais (por exemplo, as do admin em [Credenciais iniciais](#credenciais-iniciais)).
2. Copie o `accessToken` da resposta.
3. No painel **Authentication** (`bearer-jwt`), cole o token em **Bearer Token**. As próximas requisições saem com `Authorization: Bearer <token>`.

**Ao criar ou alterar endpoints** (configuração em `shared/config/OpenApiConfig.java` e nas propriedades `scalar.*` de `application.properties`):
- Anote o controller com `@Tag` e cada método com `@Operation(summary = ...)`. Uma tag nova precisa entrar em `TAG_ORDER`, senão vai para o fim da lista.
- Endpoints com `@PreAuthorize` ganham `401`/`403` automaticamente, e bodies com `@Valid` ganham `400`. Sem `@PreAuthorize`, a rota é documentada como pública.
- Erros de domínio são declarados com `@ApiResponse` no método. Nesse caso, declare também a resposta de sucesso (`200`/`201`/`204`): com qualquer `@ApiResponse` explícito, o springdoc deixa de gerá-la sozinho.

---

## Endpoints

Visão geral por domínio. A referência completa e interativa está no [Scalar](#documentação-da-api-scalar), e há também uma coleção pré-montada no Postman (link ao final). Rotas protegidas exigem `Authorization: Bearer <token>` obtido em `/login`.

| Domínio | Rotas principais | Acesso |
|---|---|---|
| **Autenticação** | `POST /login`, `POST /auth/refresh`, `POST /auth/logout` | Público (login/refresh) |
| **Cadastro** | `POST /clients`, `POST /organizers` | Público |
| **Eventos (leitura)** | `GET /events`, `GET /events/{id}`, `GET /event-categories` | Público |
| **Eventos (gestão)** | `POST /events`, `PATCH /events/{id}`, `PUT /events/{id}/images`, `GET /events/mine`, `PATCH /events/{id}/status` | Organizer/Admin |
| **Locais** | `GET /venues/{id}`, `GET /venues/{id}/sector-map` (públicos); `GET /venues`, `POST /venues`, `PATCH /venues/{id}`, `PUT /venues/{id}/sectors`, `PUT /venues/{id}/sector-map` | Organizer/Admin |
| **Estrutura do evento** | `/events/{id}/sectors`, `/events/{id}/dates`, `.../date-sectors/{id}/batches` | Organizer/Admin |
| **Fila de espera** | `POST /events/{id}/queue`, `GET /events/{id}/queue/position` | Client |
| **Demanda** | `POST /events/{id}/demand-tier` (forçar HIGH/NORMAL) | Organizer/Admin |
| **Compra** | `POST /orders`, `POST /orders/quote`, `GET /orders`, `GET /orders/{id}`, `GET /orders/{id}/status`, `GET /orders/{id}/summary`, `POST /orders/{id}/cancel` | Client |
| **Ingressos** | `GET /orders/{id}/tickets`, `GET /tickets`, `GET /tickets/{id}` | Client |
| **Pagamento** | `POST /webhooks/stripe` | Stripe (assinado) |

Notas de contrato:
- Requisições `PATCH` usam `Content-Type: application/merge-patch+json`.
- `POST /orders` exige os headers `Idempotency-Key` e (em evento HIGH) `X-Queue-Token`.
- Erros seguem um payload padrão: `{ timestamp, code, status, errors: [...] }`.

**Coleção do Postman:** https://app.getpostman.com/join-team?invite_code=f8844a6a152d27d63065c140f59a9f8fe4969b2340405072e22d98bf59f49762&target_code=9ed7f0c37437cb80c5b860aa5467d861

---

## Testes

O esforço de validação concentrou-se em **testes de carga e concorrência** (o cerne do TCC), não em cobertura unitária ampla.

**Testes de unidade / contexto:**
```bash
./mvnw test
```

**Testes de carga (k6):** ficam em `k6/` e são orquestrados pelo `run.sh` (Git Bash). Cobrem quatro cenários:

| Cenário | Objetivo |
|---|---|
| **A** | Overselling sob contenção (deve dar 0) |
| **B** | Idempotência / cobrança duplicada (deve dar 0) |
| **C** | Disponibilidade **sem** fila (demonstra o colapso) |
| **E** | Disponibilidade **com** fila (checkout saudável) |

```bash
cd k6
./run.sh setup        # build + sobe o ambiente de carga (Docker) + gera tokens
./run.sh A            # (ou B, C, E)
```

Cada execução gera `k6/results/<cenário>.summary.json` (métricas) e `.verify.md` (invariantes via SQL: overselling, duplicatas, pedidos órfãos). Os gráficos consolidados (PNG e PDF) ficam em `k6/results/figuras/`.

---

## Credenciais iniciais

O componente `AdminUserConfig` cria um administrador no primeiro boot, se não existir:

| Campo | Valor |
|---|---|
| Email | `admin@admin.com` |
| Senha | `123` (codificada com BCrypt) |
| Role | `ADMIN` |

Use em `POST /login` para obter o token de acesso.

---

## Aprendizados

- **Concorrência é o problema difícil:** o bloqueio otimista (`@Version`) só se sustenta sob carga com **retry + backoff com *jitter*** — sem o jitter, as retentativas colidem em lockstep (*thundering herd*).
- **Idempotência precisa cobrir a corrida de registro:** tratar `DataIntegrityViolation` como replay/in-flight (à la Stripe) elimina os 500 sob requisições concorrentes na mesma chave.
- **Integridade e disponibilidade são independentes:** nos testes, o overselling ficou em **0 até no colapso total** — o que a fila protege é a **disponibilidade**, não a correção do dado.
- **Backpressure > força bruta:** admitir menos gente por vez mantém o checkout rápido; sem isso, aumentar hardware só antecipa o colapso.
- **Metodologia de teste importa:** modelo aberto (*arrival-rate*) revela saturação que o modelo fechado esconde (*coordinated omission*).
- **Externalizar configuração e segredos** (`.env`, perfis, chaves fora do código) é o que torna o projeto reprodutível por terceiros.
