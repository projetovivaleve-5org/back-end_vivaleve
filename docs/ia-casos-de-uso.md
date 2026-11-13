# IA no Pluma

Camada de IA do back-end, usando o **Groq** (`https://api.groq.com/openai/v1`, plano gratuito) como modelo do chat.

| Card | O que foi feito | Onde |
|---|---|---|
| PL-22 | Casos de uso definidos (abaixo) | `model/enums/AiUseCase.java`, este arquivo |
| PL-23 | Serviço de integração com a IA | `service/ai/AiClient.java`, `GroqClient.java`, `AiService.java`, `controller/AiController.java`, `config/AiConfig.java` |
| PL-24 | Prompts e respostas padronizadas | `src/main/resources/prompts/*.txt`, `service/ai/PromptFactory.java`, `dto/AiDtos.java` (`AiResponse`) |
| PL-25 | Armazenamento das interações | `model/AiConversationEntity.java`, `model/AiMessageEntity.java`, `repository/Ai*Repository.java` |
| PL-26 | Testes da camada de IA | `src/test/java/vivaleve/proj/**/Ai*Test.java`, `CrisisDetectorTest`, `PromptFactoryTest`, `GroqClientTest` |

---

## PL-22 — Casos de uso

### 1. `CHAT_APOIO` — Chat de apoio emocional (tela `chat.html`)
- **Quem usa:** qualquer usuário logado, na aba de chat.
- **Entrada:** mensagem livre (até 2000 caracteres) + `conversationId` opcional para continuar a conversa.
- **O que a IA faz:** escuta, valida o sentimento e sugere no máximo uma ação prática por vez (respiração, pausa, diário).
- **Contexto enviado:** prompt de sistema + últimas 10 mensagens da conversa (`pluma.ai.history-limit`).
- **Substitui:** as respostas fixas por palavra-chave que hoje estão em `js/chat.js` (`analisarTexto` / `getRandomResponse`).

### 2. `INSIGHT_HUMOR` — Leitura das estatísticas de humor (tela `historico.html`)
- **Entrada:** `days` (7 ou 30). O usuário vem do token.
- **O que a IA faz:** transforma o `StatsSummary` (já calculado pelo `StatsService`) em um texto curto e gentil: um ponto positivo real + um cuidado, sem inventar números.

### 3. `REFLEXAO_REGISTRO` — Comentário sobre um registro (telas `humor.html` / `diario.html`)
- **Entrada:** id do registro (`EntryEntity`) do próprio usuário.
- **O que a IA faz:** comenta humor + atividades + nota em até 2 parágrafos, sem conclusões clínicas.

### Regra transversal — Detecção de risco (sem IA)
- `CrisisDetector` roda **antes** do modelo em toda mensagem do chat e na nota do registro.
- Se detectar expressões de risco (ex.: "não quero mais viver", "me machucar"), **não chama o modelo**: devolve na hora a resposta fixa de `prompts/resposta-crise.txt` com CVV 188 e SAMU 192, `status = CRISIS` e `helpResources` preenchido.
- Motivo: o modelo pode falhar, demorar ou responder mal justamente no momento mais crítico.
- É uma rede de segurança por palavras-chave, não um classificador clínico.

### Fora do escopo (de propósito)
- Diagnóstico, indicação de remédios, terapia. O prompt de sistema proíbe isso explicitamente.
- Assuntos fora de bem-estar (a IA redireciona com gentileza).

---

## PL-23 — Integração

```
Front (chat.js) ──POST /v1/ai/chat──▶ AiController ──▶ AiService
                                                       ├─ CrisisDetector  (risco? resposta fixa)
                                                       ├─ PromptFactory   (monta system + histórico)
                                                       ├─ AiClient ──▶ GroqClient ──▶ api.groq.com/openai/v1/chat/completions
                                                       └─ AiConversationRepository / AiMessageRepository
```

- `AiClient` é uma interface: trocar de provedor = nova implementação, sem mexer no resto.
- O plano gratuito do Groq tem limite de requisições por minuto/dia (ver https://console.groq.com/settings/limits).
- Se o provedor falhar (sem chave, timeout, erro HTTP, limite 429 do plano gratuito, resposta vazia), o usuário **não recebe erro**: recebe `status = FALLBACK` com o texto de `prompts/resposta-fallback.txt`.
- Nos modelos `openai/gpt-oss-*`, enviamos `reasoning_effort: low` para o raciocínio interno não consumir todo o limite de tokens.
- Chamadas ao modelo ficam fora de transação do banco (podem levar segundos).

### Configuração (arquivo `.env` local ou variáveis de ambiente no Render)

Copie `.env.example` para `.env` na raiz do back. O `.env` está no `.gitignore`.

| Variável | Padrão | Observação |
|---|---|---|
| `GROQ_API_KEY` | (vazio) | Chave grátis em https://console.groq.com/keys. Nunca commitar. |
| `GROQ_MODEL` | `openai/gpt-oss-20b` | Outros: `openai/gpt-oss-120b`, `qwen/qwen3.8-27b` (ver https://console.groq.com/docs/models). |
| `SERVER_PORT` | `8080` | Troque se a porta estiver ocupada. |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5500,http://127.0.0.1:5500` | Adicionar a URL do front na Vercel. |

No Render: *Environment* → adicionar `GROQ_API_KEY` e `CORS_ALLOWED_ORIGINS` (com a URL da Vercel).

---

## PL-24 — Prompts e respostas padronizadas

**Prompts** ficam em texto puro em `src/main/resources/prompts/` para qualquer pessoa do grupo revisar sem mexer em Java:

- `sistema.txt` — persona da Pluma, limites (não diagnosticar, CVV), formato (pt-BR, até N palavras, sem markdown).
- `chat-apoio.txt`, `insight-humor.txt`, `reflexao-registro.txt` — instruções de cada caso de uso.
- `resposta-crise.txt`, `resposta-fallback.txt` — respostas fixas.

**Pós-processamento** (`PromptFactory.sanitizeReply`): remove blocos `<think>`, títulos/negrito de markdown, linhas em branco extras e corta em `max-response-chars` (2000).

**Envelope padrão** — toda rota de IA devolve `AiResponse`:

```json
{
  "conversationId": 12,
  "messageId": 34,
  "useCase": "CHAT_APOIO",
  "status": "SUCCESS",
  "reply": "Sinto muito que o dia esteja pesado...",
  "crisisDetected": false,
  "helpResources": [],
  "model": "openai/gpt-oss-20b",
  "createdAt": "2026-11-10T14:03:21"
}
```

| `status` | Significado | O que o front faz |
|---|---|---|
| `SUCCESS` | Resposta do modelo | Mostra `reply` |
| `FALLBACK` | Modelo indisponível | Mostra `reply` normalmente |
| `CRISIS` | Risco detectado | Mostra `reply` e `helpResources` em destaque (ex.: card vermelho com botão "Ligar 188") |

---

## PL-25 — Armazenamento

Tabelas criadas pelo Hibernate (`ddl-auto: update`):

**`ia_conversas`**: `id`, `usuario_id`, `caso_uso`, `titulo`, `ultima_mensagem_em`, `created_at`, `updated_at`

**`ia_mensagens`**: `id`, `conversa_id`, `papel` (USER/ASSISTANT), `conteudo` (TEXT), `status` (RECEIVED/SUCCESS/FALLBACK/CRISIS), `modelo`, `tokens_prompt`, `tokens_resposta`, `latencia_ms`, `risco_detectado`, `created_at`, `updated_at`

Isso permite: retomar conversas, mostrar histórico, medir custo (tokens) e tempo de resposta, e auditar quantas vezes o fallback/crise foi acionado.

> Privacidade: as mensagens são dados sensíveis de saúde (LGPD, art. 11). O log da aplicação nunca imprime o conteúdo das mensagens. O usuário pode apagar a conversa (`DELETE`).

---

## Rotas

Todas exigem o header `Authorization: Bearer <token>` (o token devolvido por `/v1/auth/login`, `/v1/auth/register` ou `/v1/auth/google`).

| Método | Rota | Corpo |
|---|---|---|
| POST | `/v1/ai/chat` | `{ "conversationId": null, "message": "Estou ansioso", "userName": "Ana" }` |
| POST | `/v1/ai/insights/mood` | `{ "days": 7, "userName": "Ana" }` |
| POST | `/v1/ai/entries/{entryId}/reflection` | `{ "userName": "Ana" }` |
| GET | `/v1/ai/conversations` | lista de conversas |
| GET | `/v1/ai/conversations/{id}/messages` | mensagens em ordem |
| DELETE | `/v1/ai/conversations/{id}` | 204 |

Erros: `400` (validação, `days` diferente de 7/30), `401` (sem token ou token inválido/expirado), `404` (usuário, conversa ou registro não encontrado / de outro usuário).

> O usuário vem SEMPRE do token (`CurrentUser.id(jwt)`), nunca de um `userId` enviado pelo front: assim ninguém lê ou apaga as conversas de outra pessoa.

### Ligando no front (`js/chat.js`)

Hoje o `chat.js` usa IDs (`chat-input`, `chat-mensagens`, `btn-enviar`) que **não batem** com o `chat.html` (`chatInput`, `chatMessages`, `sendBtn`), então o chat não funciona nem com as respostas fixas. Ao integrar, trocar `simularResposta` por algo como:

```js
const API_URL = 'https://SEU-BACKEND.onrender.com';

async function perguntarPluma(texto) {
  const user = JSON.parse(localStorage.getItem('pluma_user') || '{}');
  const token = localStorage.getItem('pluma_token'); // token salvo no login
  const conversationId = localStorage.getItem('pluma_chat_conversation_id');

  const res = await fetch(`${API_URL}/v1/ai/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
    body: JSON.stringify({
      conversationId: conversationId ? Number(conversationId) : null,
      message: texto,
      userName: user.name
    })
  });
  if (!res.ok) throw new Error('Falha no chat: ' + res.status);

  const data = await res.json();
  localStorage.setItem('pluma_chat_conversation_id', data.conversationId);
  return data; // usar data.reply; se data.status === 'CRISIS', destacar data.helpResources
}
```

Obs.: o front precisa guardar o `token` devolvido pelo login (`/v1/auth/login`, `/v1/auth/register` ou `/v1/auth/google`). Se a API responder `401`, o token expirou: mandar o usuário para o login de novo.

---

## PL-26 — Testes

Rodar: `./mvnw test` (não precisa de Postgres nem de chave do Groq — usa H2 em memória e o Groq simulado).

| Classe | Tipo | O que cobre |
|---|---|---|
| `CrisisDetectorTest` | unitário | frases de risco com/sem acento, falsos positivos comuns, vazio |
| `PromptFactoryTest` | unitário | templates carregam, placeholders preenchidos, ordem system/histórico/usuário, descrição de stats e registro, sanitização e corte |
| `GroqClientTest` | HTTP simulado (`MockRestServiceServer`) | formato OpenAI enviado, header Bearer, `reasoning_effort` só nos gpt-oss, leitura de tokens, limite 429, resposta vazia, sem chave |
| `AiServiceTest` | unitário (Mockito) | sucesso, crise não chama modelo, fallback, histórico sem fallbacks, 404 de usuário/conversa/registro de outro usuário, insight e reflexão |
| `AiControllerTest` | `@WebMvcTest` + segurança real | rotas, 401 sem token, `userId` do cliente ignorado, validação (400), 404, 204, formato do envelope |
| `AiPersistenceIntegrationTest` | integração (H2) | conversa de 2 turnos gravada com tokens/latência/modelo, listagem e exclusão |
