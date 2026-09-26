# IncidentMind — Autonomous Multi-Agent Incident Response Platform

IncidentMind is an enterprise-grade, autonomous multi-agent incident response and engineering investigation platform. It decomposes complex cloud outages, delegates tasks dynamically across specialized agents, executes real external APIs via a controlled Tool Gateway, validates causal assertions through an anti-hallucination Critic, recovers autonomously from upstream faults, enforces Human-in-the-Loop (HITL) safety guardrails, and synthesizes structured, auditable investigation reports.

---

## 1. System Architecture

IncidentMind enforces a strict **Separation of Planes**:
- **System Control Plane (Java 21 / Spring Boot)**: Holds deterministic state, orchestrates task graphs, manages persistence in PostgreSQL, executes tools via Tool Gateway, enforces stopping policies, and handles HITL review.
- **AI Intelligence Plane (Python 3.12 / FastAPI)**: Provides LLM Planner reasoning, anti-hallucination Critic evaluation, and evidence extraction formatted as strict, validated JSON.
- **Presentation Layer (React / Vite / TypeScript)**: Dark cyber-SRE dashboard visualizing the live Task DAG, event timeline, real tool calls, Blackboard state, and human review modals.

```
                 ┌──────────────────────────────────┐
                 │       React Judge Dashboard      │
                 └────────────────┬─────────────────┘
                                  │ (HTTP / REST)
                                  ▼
                 ┌──────────────────────────────────┐
                 │     Spring Boot Control Plane    │
                 │         (PostgreSQL 16)          │
                 └────────┬────────────────┬────────┘
                          │                │
            ┌─────────────┴──────┐         │
            ▼                    ▼         ▼
     ┌─────────────┐      ┌─────────────┐ ┌──────────────┐
     │ LLM Planner │      │  Executor   │ │  Blackboard  │
     └──────┬──────┘      └──────┬──────┘ └──────┬───────┘
            │                    │               │
            ▼                    ▼               │
     ┌─────────────┐      ┌─────────────┐        │
     │ FastAPI AI  │      │ Specialized │        │
     │ Intelligence│      │   Agents    │        │
     └──────┬──────┘      └──────┬──────┘        │
            │                    │               │
            ▼                    ▼               │
     ┌─────────────┐      ┌─────────────┐        │
     │LLM Provider │      │Tool Gateway │        │
     │ (OpenAI/    │      └──────┬──────┘        │
     │ Gemini/Mock)│             │               │
     └─────────────┘             ▼               │
                          ┌─────────────┐        │
                          │ Real GitHub │        │
                          │  REST API   │        │
                          └──────┬──────┘        │
                                 │               │
                                 └───────┬───────┘
                                         ▼
                                   ┌───────────┐
                                   │ Evidence  │
                                   └─────┬─────┘
                                         ▼
                                   ┌───────────┐
                                   │LLM Critic │
                                   └─────┬─────┘
                                         │
                        ┌────────────────┴────────────────┐
                        ▼                                 ▼
                     ACCEPT                             REJECT
                        │                                 │
                        │                                 ▼
                        │                        ┌─────────────────┐
                        │                        │ Dynamic Replan  │
                        │                        └─────────────────┘
                        ▼
                 ┌──────────────┐
                 │Stopping Policy│
                 └──────┬───────┘
                        ▼
                 ┌──────────────┐
                 │ Final Report │
                 └──────────────┘
```

---

## 2. Core Capabilities

### 🤖 1. Dynamic LLM Planning & Reconciliation
- The Planner analyzes current Blackboard evidence, remaining unknowns, and previous task outcomes.
- It returns strict JSON conformant to Pydantic schemas specifying `CREATE_TASK`, `REPLAN`, `WAIT`, `STOP_SUCCESS`, or `HUMAN_REVIEW_REQUIRED`.
- **Plan Reconciliation**: If new evidence contradicts initial assumptions (or if the Critic rejects a finding), the Planner dynamically deprioritizes irrelevant branches and creates targeted follow-up tasks (e.g. `INVESTIGATE_PULL_REQUESTS`).

### 🕵️ 2. Specialized Multi-Agent Delegation
- **`IncidentTriageAgent`**: Evaluates telemetry anomalies, severity level, blast radius, and affected services.
- **`ChangeAnalysisAgent`**: Inspects recent Git commits, authors, and deployment timestamps.
- **`DependencyAnalysisAgent`**: Assesses upstream/downstream payment gateway dependencies to rule out external failures.

### 🌐 3. Controlled Tool Gateway & Real GitHub Integration
- External tools are invoked exclusively through the `ToolGateway` (FastAPI and LLMs never call GitHub directly).
- Integrates with the **real GitHub REST API** (`/repos/{owner}/{repo}/commits`, `/pulls`) with payload sanitization and latency metrics.

### 🛡️ 4. Anti-Hallucination LLM Critic
- Evaluates agent findings before they are written to the permanent investigation record.
- Flags ungrounded causal leaps: *"Temporal correlation (e.g. commit 8 min before incident) does not prove causality without stack traces or PR diffs."*
- Returns `REJECT` with specific missing evidence recommendations, directly triggering Planner reconciliation.

### 🔄 5. Recovery & Resilience Engine
- Automatically recovers from transient upstream failures (e.g. HTTP 500, HTTP 503, HTTP 429).
- Applies exponential backoff with configurable jitter and bounded retry budgets.
- Falls back to alternative tools or initiates replanning if retries are exhausted.

### 👤 6. Bounded Autonomy & Human-in-the-Loop (HITL)
- **Sensitive Action Gate**: Irreversible actions (`ROLLBACK_PRODUCTION`, `UPDATE_DATABASE`, `DEPLOY_PRODUCTION`) immediately halt automation and demand `HUMAN_APPROVAL_REQUIRED`.
- **Repeated Failure Guard**: 3 consecutive Critic rejections or 2 planning failures automatically escalate to `HUMAN_REVIEW_REQUIRED`.
- **Operator Actions**: `CONTINUE`, `APPROVE_ACTION`, `REJECT_ACTION`, `MODIFY_PLAN`, `STOP` (persisted to audit log).

### ⚡ 7. Deterministic Fallback
- If the external LLM provider or FastAPI service becomes unavailable, Spring Boot automatically logs `LLM_FALLBACK_ACTIVATED` and activates the `DeterministicPlanner` to ensure zero downtime.

---

## 3. Quick Start Guide

### Prerequisites
- Docker & Docker Compose
- Node.js 18+ (for local frontend dev)
- Java 21 & Maven (optional, if running backend without Docker)
- Python 3.11+ (optional, if running AI service without Docker)

### 1. Configure Environment
```bash
# Copy template configuration
cp .env.example .env
```

### 2. Launch Backend Multi-Service Stack (PostgreSQL + FastAPI + Spring Boot)
```bash
docker-compose up -d --build
```

Verify service health:
```bash
# Check running containers and health status
docker-compose ps
```

| Service | Container | Port | Health Endpoint |
|---|---|---|---|
| **PostgreSQL** | `incidentmind-postgres` | `5432` | `pg_isready` |
| **FastAPI AI Service** | `incidentmind-ai-service` | `8000` | `http://localhost:8000/ai/v1/health` |
| **Spring Boot Control Plane** | `incidentmind-backend` | `8080` | `http://localhost:8080/actuator/health` |

### 3. Launch React Dashboard
```bash
cd frontend
npm install
npm run dev
```
Open **[http://localhost:5173](http://localhost:5173)** in your browser.

---

## 4. Canonical Golden Demo Scenario

### "Checkout API 5xx Surge Shortly After Deployment"
1. **Trigger**: An incident alert is created reporting that checkout 5xx errors spiked from 2% to 18%.
2. **Triage**: `IncidentTriageAgent` confirms production anomaly in `checkout-service`.
3. **Planner**: Dynamically creates `CHANGE_ANALYSIS` task.
4. **Tool Gateway**: Calls the real GitHub REST API and retrieves commit `abc1234` pushed 8 minutes prior.
5. **Agent Inference**: Agent claims the commit caused the outage.
6. **Critic Evaluation**: Critic detects ungrounded causal assertion and issues **`REJECT`** (*"Temporal proximity alone does not prove causality"*).
7. **Plan Reconciliation**: The Planner receives the rejection and dynamically creates an `INVESTIGATE_PULL_REQUESTS` task to inspect code diffs and CI status.
8. **Stopping Policy**: Verified evidence gathered $\rightarrow$ Policy issues `STOP_SUCCESS`.
9. **Final Synthesis**: Synthesis report categorizes findings into `FACT`, `SUPPORTED_FINDING`, `HYPOTHESIS`, and `UNKNOWN`.

---

## 5. Automated Test Suite Results

All 143 automated tests across the backend, AI intelligence service, and React frontend pass with 0 errors and 0 failures:

| Test Suite | Framework | Passing | Failures | Errors |
|---|---|---|---|---|
| **Spring Boot Backend** | JUnit 5 / MockMvc / AssertJ | **120** | 0 | 0 |
| **FastAPI AI Service** | Pytest / Pytest-Asyncio / AnyIO | **15** | 0 | 0 |
| **React Frontend** | Vitest / Testing Library | **8** | 0 | 0 |
| **Total Automated Tests** | | **143** | **0** | **0** |

```bash
# Run backend tests
mvn clean test

# Run AI service tests
python -m pytest ai_service/tests -v

# Run frontend tests & build
cd frontend && npm test && npm run build
```

---

## 6. Security & Auditability

- **Zero Secrets in Git**: `.env` is ignored by Git; `.env.example` contains only non-sensitive placeholders.
- **No Direct Browser Access**: Frontend communicates exclusively with Spring Boot; LLM API keys and GitHub tokens never reach the client.
- **End-to-End Correlation**: `X-Correlation-ID` propagates across Browser $\rightarrow$ Spring Boot $\rightarrow$ FastAPI $\rightarrow$ Tool Gateway $\rightarrow$ Audit Trail.