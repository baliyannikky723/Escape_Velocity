# IncidentMind — Autonomous Multi-Agent Incident Response Platform

[![Java](https://img.shields.io/badge/Java-21%20LTS-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.3-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Python](https://img.shields.io/badge/Python-3.12-blue.svg)](https://www.python.org/)
[![FastAPI](https://img.shields.io/badge/FastAPI-0.115-009688.svg)](https://fastapi.tiangolo.com/)
[![React](https://img.shields.io/badge/React-18-61dafb.svg)](https://react.dev/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.7-3178c6.svg)](https://www.typescriptlang.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791.svg)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ed.svg)](https://www.docker.com/)
[![Tests](https://img.shields.io/badge/Tests-143%20Passing%20(0%20Failures)-success.svg)](https://github.com/baliyannikky723/Escape_Velocity)

> **IncidentMind** is an enterprise-grade autonomous multi-agent incident response platform designed to autonomously investigate, triage, and diagnose production cloud outages. It dynamically decomposes complex incidents into specialized task DAGs, invokes real operational tools (such as the real GitHub REST API), validates causal claims through an anti-hallucination Critic, recovers autonomously from upstream faults, enforces strict Human-in-the-Loop (HITL) safety guardrails, and synthesizes structured, auditable investigation reports.

---

## 📑 Table of Contents
1. [The Problem & Why Single-Agent LLMs Fail](#-1-the-problem--why-single-agent-llms-fail)
2. [What Makes IncidentMind Different?](#-2-what-makes-incidentmind-different)
3. [System Architecture & Separation of Planes](#-3-system-architecture--separation-of-planes)
4. [Multi-Agent Directory & Communication Model](#-4-multi-agent-directory--communication-model)
5. [Autonomous Multi-Agent Loop (Step-by-Step)](#-5-autonomous-multi-agent-loop-step-by-step)
6. [Core Capabilities & Safety Features](#-6-core-capabilities--safety-features)
7. [Current MVP vs. Future Scope (Real-Time Server Log Analysis)](#-7-current-mvp-vs-future-scope-real-time-server-log-analysis)
8. [Quick Start Guide (Run with 1 Command)](#-8-quick-start-guide-run-with-1-command)
9. [Canonical Golden Demo Walkthrough](#-9-canonical-golden-demo-walkthrough)
10. [REST API Endpoint Catalog](#-10-rest-api-endpoint-catalog)
11. [Automated Test Suite (143 Tests Passing)](#-11-automated-test-suite-143-tests-passing)
12. [Security & Auditability](#-12-security--auditability)

---

## 🚨 1. The Problem & Why Single-Agent LLMs Fail

During a high-severity production outage, SRE teams face immense cognitive load. Typical AI demos attempt to solve outages with **one model answering one single prompt**. 

In real-world engineering, single-prompt AI approaches fail because:
1. **Happy-Path Fragility**: They collapse when an API returns an HTTP 500, 429 rate-limit, or malformed JSON.
2. **Hallucinated Causality**: They jump to conclusions based on weak temporal correlation (*"A commit happened 8 minutes ago, so it must be the bug"*).
3. **No Audit Trail**: Black-box chatbot outputs cannot be audited or inspected by incident commanders.
4. **Unbounded Risk**: Autonomous models attempting uncontrolled rollbacks or database mutations without human guardrails pose catastrophic risks to production.

---

## 💡 2. What Makes IncidentMind Different?

IncidentMind is **engineered as a resilient distributed system**, not a wrapper around a prompt:

- **True Multi-Agent Specialization**: Tasks are delegated strictly by capability across domain agents.
- **Real External Tool Gateway**: Executes real API calls against GitHub REST endpoints with payload sanitization.
- **Anti-Hallucination Critic**: Validates all agent assertions against raw evidence before accepting them.
- **Blackboard State Machine**: Shared, immutable working memory prevents duplicate work and maintains ground truth.
- **Autonomous Recovery Engine**: Exponential backoff, jittered retries, and dynamic replanning when tools fail.
- **Bounded Autonomy & HITL**: Irreversible actions require explicit human operator approval.
- **Fail-Safe Deterministic Fallback**: If external LLMs are down, deterministic heuristics ensure zero downtime.

---

## 🏗️ 3. System Architecture & Separation of Planes

IncidentMind enforces a strict **Separation of Concerns across 3 Planes**:

```
                  ┌────────────────────────────────────────┐
                  │    React / Vite Cyber-SRE Dashboard    │
                  │        (Presentation Layer)            │
                  └───────────────────┬────────────────────┘
                                      │ HTTP / REST (Port 5173 -> 8080)
                                      ▼
                  ┌────────────────────────────────────────┐
                  │    Spring Boot 3.4 Control Plane       │
                  │          (Java 21 / Port 8080)         │
                  │  ┌──────────────┐    ┌──────────────┐  │
                  │  │ Task Graph   │    │  Blackboard  │  │
                  │  │  Scheduler   │    │ Working Mem  │  │
                  │  └──────┬───────┘    └──────┬───────┘  │
                  │         │                   │          │
                  │         ▼                   ▼          │
                  │  ┌──────────────┐    ┌──────────────┐  │
                  │  │ Tool Gateway │    │  PostgreSQL  │  │
                  │  └──────┬───────┘    │  Database 16 │  │
                  └─────────┼────────────┴──────┬───────┘──┘
                            │                   │
             ┌──────────────┴──────┐            │ JSON RPC / REST (Port 8000)
             ▼                     ▼            ▼
      ┌─────────────┐       ┌─────────────┐ ┌─────────────────────────┐
      │ Real GitHub │       │ Failure     │ │ FastAPI AI Intelligence │
      │  REST API   │       │  Injector   │ │    Plane (Python 3.12)  │
      └─────────────┘       └─────────────┘ └───────────┬─────────────┘
                                                        │
                                                        ▼
                                            ┌─────────────────────────┐
                                            │ LLM Reasoning Engine    │
                                            │ (OpenAI/Gemini/Fallback)│
                                            └─────────────────────────┘
```

### Plane Responsibilities:
1. **System Control Plane (Java 21 / Spring Boot)**:
   - Maintains deterministic state and relational integrity in PostgreSQL 16.
   - Enforces task dependency graphs (Task DAG), recovery policies, and stopping conditions.
   - Houses the secure **Tool Gateway** and audit event stream.
2. **AI Intelligence Plane (Python 3.12 / FastAPI)**:
   - Houses the **Dynamic LLM Planner**, **Anti-Hallucination Critic**, and **Evidence Reasoner**.
   - Validates all AI inputs and structured outputs with strict **Pydantic v2 schemas**.
3. **Presentation Layer (React 18 / TypeScript / Vite)**:
   - SRE dark-mode operations dashboard.
   - Visualizes live Task DAGs, tool call logs, evidence boards, and human approval modals.

---

## 🤖 4. Multi-Agent Directory & Communication Model

### How do agents communicate?
In IncidentMind, agents **do NOT use un-monitored peer-to-peer gossip**, which in production leads to infinite communication loops and hallucination compounding.

Instead, IncidentMind implements the **Blackboard Architecture Pattern**:
- The **Blackboard** is a shared working memory where agents publish observed facts and read peer evidence.
- The **InvestigationExecutor** orchestrates task assignments based on registered agent capabilities.
- The **Critic** intercepts evidence claims and validates them before they become accepted facts.

```
                      ┌──────────────────────┐
                      │    LLM Planner       │
                      └──────────┬───────────┘
                                 │ 1. Decomposes into Task DAG
                                 ▼
                      ┌──────────────────────┐
                      │ InvestigationExecutor│
                      └──────────┬───────────┘
                                 │ 2. Dispatches task by capability
                                 ▼
            ┌────────────────────┼────────────────────┐
            ▼                    ▼                    ▼
   ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐
   │IncidentTriageAgnt│ │ChangeAnalysisAgnt│ │DependencyAgent   │
   └────────┬─────────┘ └────────┬─────────┘ └────────┬─────────┘
            │                    │                    │
            │                    │ 3. Executes Tool   │
            │                    ▼ (GitHub REST API)  │
            │           ┌──────────────────┐          │
            │           │   Tool Gateway   │          │
            │           └────────┬─────────┘          │
            │                    │                    │
            └────────────────────┼────────────────────┘
                                 │ 4. Publishes raw findings
                                 ▼
                      ┌──────────────────────┐
                      │     BLACKBOARD       │ ◄── (Shared Verified Memory)
                      └──────────┬───────────┘
                                 │ 5. Validates causal assertions
                                 ▼
                      ┌──────────────────────┐
                      │  LLM Anti-Critic     │
                      └──────────┬───────────┘
                                 │ 6. ACCEPT / REJECT
                                 ▼
                      ┌──────────────────────┐
                      │ Dynamic Replanning   │ ──► Feedback sent back to Planner
                      └──────────────────────┘
```

### The 7 Specialized Agents & Meta-Components:

| Agent / Component | Type | Primary Role & Capabilities |
|---|---|---|
| **`Dynamic LLM Planner`** | AI Orchestrator | Decomposes high-level incident context into targeted task dependency graphs; triggers dynamic replanning when assumptions are challenged. |
| **`IncidentTriageAgent`** | Domain Agent | Analyzes incident severity, blast radius, anomaly patterns, and affected services (`TRIAGE`, `INCIDENT_TRIAGE`). |
| **`ChangeAnalysisAgent`** | Domain Agent | Investigates Git commits, pull requests, author logs, and deployment timestamps via the Tool Gateway (`CHANGE_ANALYSIS`, `INVESTIGATE_RECENT_COMMITS`, `INVESTIGATE_PULL_REQUESTS`). |
| **`DependencyAnalysisAgent`** | Domain Agent | Maps upstream and downstream dependencies (e.g. payment gateways, auth providers) to eliminate false positives (`DEPENDENCY_ANALYSIS`, `EXTERNAL_DEPENDENCY_INVESTIGATION`). |
| **`InvestigationExecutor`** | Deterministic Engine | Schedules tasks in topological order, resolves dependencies, dispatches tasks to agents, and tracks timeouts. |
| **`Anti-Hallucination Critic`** | Safety AI Component | Evaluates causal assertions against raw tool outputs to prevent ungrounded leaps (*"Temporal proximity $\neq$ Causality"*). |
| **`Recovery Engine`** | Resilience Engine | Intercepts HTTP 500s/timeouts, computes exponential backoffs with jitter, executes alternative tools, and escalates to HITL when retry budgets expire. |

---

## 🔄 5. Autonomous Multi-Agent Loop (Step-by-Step)

```
INCIDENT INGESTION
       ↓
LLM DYNAMIC PLANNER  ──► Decomposes into Task DAG
       ↓
INVESTIGATION EXECUTOR  ──► Dispatches to Specialized Agent
       ↓
SPECIALIZED AGENT  ──► Invokes Tool via Tool Gateway
       ↓
TOOL GATEWAY  ──► Calls Real GitHub REST API
       ↓
BLACKBOARD EVIDENCE  ──► Persists Raw Observation & Claims
       ↓
ANTI-HALLUCINATION CRITIC  ──► Evaluates Grounding & Causality
       ├──► [REJECT] ──► DYNAMIC REPLANNER ──► Generates Follow-up Tasks (Loop)
       └──► [ACCEPT]
              ↓
STOPPING POLICY  ──► Evaluates Goal Satisfaction & Limits
       ↓
FINAL INVESTIGATION REPORT  ──► Structured Causal Findings
```

---

## 🛡️ 6. Core Capabilities & Safety Features

### 1. Controlled Tool Gateway & Real GitHub Integration
- External tools cannot be called directly by LLMs. All tool invocations route through `ToolGateway`.
- Implements real GitHub API calls (`/repos/{owner}/{repo}/commits`, `/pulls`) with automated bearer token management, connect/read timeouts, and payload sanitization (masking sensitive tokens and secrets).

### 2. Anti-Hallucination Critic
- Validates every claim before it is marked as an accepted fact.
- Checks:
  - Is the finding backed by actual tool output?
  - Does the agent confuse correlation with causation?
  - Are there missing links in the causal chain?

### 3. Recovery Engine & Resilience
- **Exponential Backoff Strategy**: Retries transient faults with configurable backoff multiplier and jitter.
- **Dynamic Replanning**: Automatically switches strategies when a tool is blocked or unavailable.
- **Failure Injector**: Built-in test harness allowing judges to inject HTTP 500, HTTP 429, or timeouts to witness automated recovery live.

### 4. Human-in-the-Loop (HITL) Guardrails
- Automatically halts automated execution and requests human review when:
  - A sensitive action is planned (`ROLLBACK_PRODUCTION`, `UPDATE_DATABASE`, `DEPLOY_PRODUCTION`).
  - 3 consecutive Critic rejections occur.
  - The maximum retry budget is exhausted.
- Operators can `APPROVE`, `REJECT`, `MODIFY_PLAN`, or `CONTINUE` with audit logging.

### 5. Fail-Safe Deterministic Fallback
- If the external LLM or AI service is unreachable, Spring Boot automatically logs `LLM_FALLBACK_ACTIVATED` and runs deterministic heuristics to complete the investigation without downtime.

---

## 🔭 7. Current MVP vs. Future Scope (Real-Time Server Log Analysis)

To maintain complete transparency for judges:

### 🌟 What is REAL & LIVE in the Current Implementation:
- ✅ Production Incident Ingestion & Management
- ✅ Dynamic LLM Task Planning & Dynamic Replanning
- ✅ Multi-Agent Orchestration & Blackboard Working Memory
- ✅ **Real GitHub REST API Integration** (Live commit and pull request fetching)
- ✅ Anti-Hallucination LLM Critic Verdicts
- ✅ Autonomous Recovery Engine with Exponential Backoff
- ✅ Human-in-the-Loop (HITL) Approval Modal & Guardrails
- ✅ Complete Reactive SRE Dashboard

### 🔮 Future Scope & Production Roadmap:
In enterprise production environments, IncidentMind does not need to poll every server log continuously; rather, existing APM & Monitoring platforms detect anomalies and feed alerts into IncidentMind for autonomous investigation.

```
                      PRODUCTION ENVIRONMENT
                                │
                 ┌──────────────┼──────────────┐
                 ▼              ▼              ▼
            System Logs     APM Metrics    Pod Alerts
                 │              │              │
                 └──────────────┼──────────────┘
                                ▼
                       Monitoring Platform
                  (Datadog / Grafana / Sentry)
                                │
                                │ Webhook Alert (POST /api/v1/webhooks/alerts)
                                ▼
                 ┌─────────────────────────────┐
                 │  IncidentMind Tool Gateway  │
                 └──────────────┬──────────────┘
                                │
                 ┌──────────────┴──────────────┐
                 ▼                             ▼
        ┌──────────────────┐          ┌──────────────────┐
        │  GitHub Tool     │          │  Log Stream Tool │ (Roadmap)
        │ (Live in MVP)    │          │ (Datadog/ES/K8s) │
        └──────────────────┘          └──────────────────┘
```

#### Production Extensions (Takes 2–4 Hours to Implement):
1. **Alert Ingestion Webhook** (`/api/v1/webhooks/alerts`): Receives real-time alerts from Datadog, Prometheus Alertmanager, or PagerDuty.
2. **Log Fetching Tool** (`logs.query_recent_errors`): Queries Elasticsearch, AWS CloudWatch, or Datadog APIs for stack traces within the incident detection window.
3. **`LogAnalysisAgent`**: Specialized domain agent to parse stack traces, extract exception root causes, and correlate with Git commit diffs.

---

## 🚀 8. Quick Start Guide (Run with 1 Command)

### Prerequisites:
- [Docker](https://www.docker.com/) & Docker Compose
- [Node.js 18+](https://nodejs.org/) & [Java 21](https://adoptium.net/) (for local standalone development)

---

### Option A: Run via Docker Compose (Recommended)

```bash
# 1. Clone repository
git clone https://github.com/baliyannikky723/Escape_Velocity.git
cd Escape_Velocity

# 2. Configure environment
cp .env.example .env

# 3. Launch all backend services (Postgres + FastAPI + Spring Boot)
docker-compose up -d --build

# 4. Launch React Frontend
cd frontend
npm install
npm run dev
```

Open **[http://localhost:5173](http://localhost:5173)** in your browser.

---

### Option B: Run Standalone Services Locally

#### Step 1: Start PostgreSQL
```bash
docker run -d --name incidentmind-postgres -e POSTGRES_DB=incidentmind -e POSTGRES_USER=incidentmind -e POSTGRES_PASSWORD=incidentmind -p 5432:5432 postgres:16-alpine
```

#### Step 2: Start FastAPI AI Service (Port 8000)
```bash
cd ai_service
pip install -r requirements.txt
python run.py
```

#### Step 3: Start Spring Boot Backend (Port 8080)
```bash
mvn spring-boot:run
```

#### Step 4: Start React Dashboard (Port 5173)
```bash
cd frontend
npm install
npm run dev
```

---

## 🎯 9. Canonical Golden Demo Walkthrough

Try this scenario directly in the UI dashboard:

1. Open **`http://localhost:5173`**.
2. Click **"+ New Incident"** and submit:
   - **Title**: `Payment Gateway P99 latency exceeded 1800ms threshold`
   - **Service**: `payment-service` | **Environment**: `production` | **Severity**: `P2`
3. Click **"Launch AI Investigation"**.
4. **Observe the Autonomous Multi-Agent Loop**:
   - **Task 1 (TRIAGE)**: `IncidentTriageAgent` establishes baseline characteristics $\rightarrow$ Critic **ACCEPTS**.
   - **Task 2 (CHANGE_ANALYSIS)**: `ChangeAnalysisAgent` invokes **Real GitHub REST API** on repository `octocat/Hello-World` $\rightarrow$ Fetches real commit `7fd1a60` $\rightarrow$ Publishes evidence claim to Blackboard $\rightarrow$ Critic **ACCEPTS**.
   - **Task 3 (DEPENDENCY_ANALYSIS)**: `DependencyAnalysisAgent` analyzes external dependencies $\rightarrow$ Critic **ACCEPTS**.
   - **AI Re-evaluation**: FastAPI LLM Planner checks Blackboard state and determines all hypotheses are verified $\rightarrow$ Issues `STOP_SUCCESS`.
   - **Final Synthesis**: View structured investigation report with root causes, confidence score, and verified timeline.

---

## 📡 10. REST API Endpoint Catalog

All endpoints support distributed tracing via `X-Correlation-ID`.

### Incidents API
- `POST /api/v1/incidents` — Register a new production incident.
- `GET /api/v1/incidents` — List all registered incidents.
- `GET /api/v1/incidents/{id}` — Get incident details by UUID.

### Investigation & Orchestration API
- `POST /api/v1/incidents/{id}/investigations` — Create an investigation for an incident.
- `POST /api/v1/investigations/{id}/start` — Start the autonomous multi-agent loop.
- `GET /api/v1/investigations/{id}` — Get live investigation status and metadata.
- `GET /api/v1/investigations/{id}/tasks` — Get Task DAG nodes and execution statuses.
- `GET /api/v1/investigations/{id}/evidence` — Get verified Blackboard evidence items.
- `GET /api/v1/investigations/{id}/report` — Get synthesized final investigation report.
- `POST /api/v1/investigations/{id}/human-action` — Submit operator decision (`APPROVE_ACTION`, `REJECT_ACTION`, `MODIFY_PLAN`, `CONTINUE`).

### Tool Gateway API
- `GET /api/v1/tools` — List registered tools in the Tool Gateway.
- `POST /api/v1/tools/invoke` — Securely invoke a tool with payload validation.

---

## 🧪 11. Automated Test Suite (143 Tests Passing)

The platform is rigorously tested across all three tiers with **143 passing automated tests (0 failures, 0 errors)**:

| Test Suite | Component | Framework | Passing | Failures | Errors |
|---|---|---|---|---|---|
| **Spring Boot Backend** | Control Plane & Agents | JUnit 5 / MockMvc / AssertJ | **120** | 0 | 0 |
| **FastAPI Service** | AI Intelligence & Reasoning | Pytest / Pytest-Asyncio / HTTPX | **15** | 0 | 0 |
| **React Frontend** | Dashboard & DAG Components | Vitest / React Testing Library | **8** | 0 | 0 |
| **Total Test Suite** | **Entire Platform** | | **143** | **0** | **0** |

```bash
# Run Spring Boot test suite
mvn clean test

# Run FastAPI test suite
python -m pytest ai_service/tests -v

# Run React frontend test suite
cd frontend && npm test
```

---

## 🔒 12. Security & Auditability

- **Zero Secrets in Git**: Sensitive credentials and tokens are loaded strictly via environment variables; `.gitignore` prevents secret leakage.
- **Payload Sanitization**: `PayloadSanitizer` scrubs authorization tokens, credentials, and API keys before tool responses are stored in PostgreSQL.
- **End-to-End Traceability**: Every event, planning cycle, tool execution, and human decision is tagged with an immutable `correlationId` and persisted to the `audit_events` table.
- **Bounded Execution**: Hard stopping policies guarantee that no multi-agent loop can execute runaway iterations or exceed runtime boundaries.

---

## 👥 Authors & License
Built for the Hackathon by the **Escape Velocity** Team.
Distributed under the **MIT License**.