# Smart Manufacturing Cloud Platform (znzz)

[简体中文](./README.zh-CN.md) | English

An open-source **outsourcing-collaboration platform for small and medium machining factories**.
Buyers post machining demands, qualified factories bid, and the platform coordinates the whole
lifecycle: quotation, contract, staged production, independent quality inspection, escrowed
settlement and credit scoring — driven by a server-side state machine with deadline automation.

![Landing page](docs/images/landing.png)

## Why this exists

Small manufacturers in China's machining clusters (Ningbo, Taizhou, Jiaxing, Dongguan, …)
still trade through phone calls, WeChat groups and paper contracts. Capacity is idle in one
factory while another over-commits; buyers cannot verify equipment, yield or on-time history;
disputes over staged payments are common. This project encodes those rules — deposits, thinking
periods, staged acceptance, inspection gates, forfeiture ratios — as **explicit, auditable state
transitions**, so that a small team can run a marketplace without a large operations staff.

It started as a 40-day industry-training project (Neusoft, 2026) and is now maintained as an
open reference implementation of a B2B manufacturing marketplace.

## Features

| Area | What is implemented |
|------|---------------------|
| **Four roles** | Buyer, Factory, Operator, Inspector (plus a super-admin). Role-specific web consoles and a HarmonyOS mobile client. |
| **Demand publishing** | Process breakdown, quality thresholds (AQL, tolerance, certifications, minimum yield/credit), intention period, attachments. |
| **Two-stage bidding** | Intention stage (small fixed intention fee) → deposit stage (locked quote + 5 % deposit). Factory and buyer each get a 24 h thinking period. |
| **Capacity matching** | Factories quote a `[min_qty, max_qty]` range per process; the platform splits volume across factories and shows process coverage. |
| **Solution generation** | 2–3 combined sourcing plans ranked by price / delivery / credit. Rule-based by default; an LLM generator (DeepSeek-compatible API) is used when a key is configured, with automatic fallback. |
| **Contract & fulfilment** | Per-factory contracts, signing deadlines, staged production plan, progress reporting, delay handling. |
| **Quality inspection** | Independent inspector role, inspection fee, pass-rate history feeding credit score. |
| **Escrow & settlement** | Intention fee, buyer/factory deposits, commission and forfeiture ratios all configurable in `application.yml`; Alipay sandbox adapter (disabled by default). |
| **Credit system** | Weighted score from survey, completion and pass-rate; credit events recorded per enterprise. |
| **Operations console** | Ticketing, demand/order supervision, manual close & re-route, audit log of every state change. |
| **Automation** | Scheduled jobs move demands/orders across deadlines and push in-app notifications. |

![Fulfilment console](docs/images/fulfillment-console.png)

## Architecture

```
client-web (Vue 3)   admin-web (Vue 3)   harmonyos-client (ArkTS)
        \                 |                    /
         \                |                   /
          +------ backend (Spring Boot 3.3, Java 21) ------+
          |  controller -> service -> domain (state machine, |
          |  quote, coverage, fund, credit, inspect, notify) |
          +-----------------+-------------------------------+
                            |
                   MySQL 8  +  Redis 7
```

![Module map](docs/images/module-map.png)

- **backend/** — Spring Boot 3.3.5, Spring Security + JWT, MyBatis-Plus, scheduled deadline engine,
  `SchemaPatcher` for idempotent schema upgrades, seeders for demo data.
- **client-web/** — Vue 3 + Vite + Element Plus. Buyer and factory workspaces.
- **admin-web/** — Vue 3 + Vite + Element Plus. Operator / inspector / super-admin console.
- **harmonyos-client/** — ArkTS app (DevEco Studio) covering buyer & factory core flows.
- **db/** — `schema.sql` plus incremental `alter_v*.sql`; `reset_demo_data.sql` for a clean demo.
- **docs/** — design notes, end-to-end flow spec, database design, course deliverables.

## Quick start

Prerequisites: JDK 21, Maven 3.9+, Node 18+, Docker (for MySQL/Redis) or local instances.

```bash
# 1. infrastructure
docker compose up -d            # MySQL 8 on 3306 (db dsh_platform), Redis on 6381

# 2. backend
cd backend
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
#    set dsh.jwt.secret (required, >= 32 random bytes) and the DB password;
#    leave dsh.ai.api-key empty to use the rule-based generator
mvn test                                 # unit tests (state machine, fund ledger, JWT, DTO validation)
mvn -DskipTests spring-boot:run          # http://localhost:8080

# 3. front-ends
cd ../client-web && npm install && npm run dev   # http://localhost:5173  (buyer / factory)
cd ../admin-web  && npm install && npm run dev   # http://localhost:5174  (operator / inspector)
```

Default demo accounts are created by the seeder on first start
(`admin / admin123` for the admin console; buyer and factory samples are listed on the login page).
Never reuse these credentials outside a local demo.

### Configuration

All business parameters live under `dsh.*` in `backend/src/main/resources/application.yml`:
fees and ratios (`dsh.fee`), credit weights (`dsh.credit`), deadlines in hours (`dsh.time`),
AI provider (`dsh.ai`), payment adapter (`dsh.pay`), allowed CORS origins (`dsh.cors.origins`).
Secrets go into the git-ignored `application-local.yml` or environment variables
(`DSH_JWT_SECRET`, `DSH_AI_API_KEY`, `DSH_ALIPAY_APP_ID`, …). The application refuses to start
without a JWT secret of at least 32 bytes.

## Project status & roadmap

The end-to-end flow (register → publish → bid → contract → produce → inspect → settle → credit)
runs and is covered by the manual test suite in `docs/deliverables`. Unit tests cover the demand
state machine, fund-ledger rules, JWT handling and request validation; CI runs them on every push.
Areas we are working on:

- [ ] Extend automated tests to order fulfilment, settlement and credit scoring (service layer, Testcontainers)
- [ ] Replace polling with WebSocket/SSE notifications
- [ ] Pluggable payment providers (WeChat Pay, bank escrow) behind the existing `pay` domain
- [ ] Multi-tenant hardening: tenant-scoped queries are in place, row-level policies are not
- [ ] i18n for the web clients (Chinese only today)
- [ ] Factory device telemetry import (MQTT) to feed real capacity instead of self-reported numbers

Issues and pull requests are welcome — see [CONTRIBUTING.md](./CONTRIBUTING.md).

## Team

Built by Du Qingtong (lead; demand publishing, bidding and solution matching), Li Zixuan
(HarmonyOS client), Bao Kangqi and Xiao Zhenhao (contract fulfilment & inspection, funds,
credit and the operations console).

## License

[MIT](./LICENSE)
