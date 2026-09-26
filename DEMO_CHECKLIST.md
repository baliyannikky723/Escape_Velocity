# IncidentMind — Judge & Evaluator Demo Checklist

This checklist guides evaluators through verifying the end-to-end multi-agent incident response lifecycle.

---

## 1. System Startup Verification

- [ ] **Docker Compose Stack Initialization**:
  ```bash
  docker-compose up -d --build
  docker-compose ps
  ```
- [ ] **PostgreSQL Health**: Status is `healthy` on port `5432` (`pg_isready`).
- [ ] **FastAPI AI Intelligence Service Health**: Status is `healthy` on port `8000` (`http://localhost:8000/ai/v1/health`).
- [ ] **Spring Boot Control Plane Health**: Status is `healthy` on port `8080` (`http://localhost:8080/actuator/health`).
- [ ] **React Frontend Launch**:
  ```bash
  cd frontend
  npm run dev
  ```
  Accessible at `http://localhost:5173`.

---

## 2. Canonical Golden Demo: "Checkout API 5xx Spike"

- [ ] **Step 1: Open Dashboard**:
  - Navigate to `http://localhost:5173`.
  - Notice the header pill `Control Plane: Active` and global incident metrics.
- [ ] **Step 2: Create Incident**:
  - Click **"+ New Incident"**.
  - Click the **"Checkout API 5xx Spike (Golden Demo)"** preload button.
  - Click **"Create & Investigate"**.
- [ ] **Step 3: Observe Dynamic Task DAG**:
  - Task 1: `INCIDENT_TRIAGE` assigned to `incident-triage-agent`.
  - Click **"Execute AI Step / Replan"**.
  - `IncidentTriageAgent` evaluates blast radius and detects deployment anomaly.
- [ ] **Step 4: Real GitHub REST API Invocation**:
  - LLM Planner creates `CHANGE_ANALYSIS` task.
  - `ChangeAnalysisAgent` invokes real GitHub API via `ToolGateway` (`github.get_recent_commits`).
  - View the **"Real Tool Calls"** tab: Confirms HTTP 200 with response latency and commit SHA metadata.
- [ ] **Step 5: Anti-Hallucination Critic REJECT**:
  - Agent infers that the recent commit caused the outage.
  - Navigate to the **"Critic Verdicts"** tab:
  - Critic emits **`REJECT (UNGROUNDED)`** with reason: *"Temporal correlation alone does not prove causality without stack trace or PR diff evidence."*
- [ ] **Step 6: Plan Reconciliation in Action**:
  - The Planner receives the rejection and dynamically creates task 3: `INVESTIGATE_PULL_REQUESTS`.
  - View the **"Task DAG & Event Stream"** tab to see the updated DAG graph reflecting the new direction.
- [ ] **Step 7: Stopping Policy & Final Report**:
  - Verified evidence gathered $\rightarrow$ Stopping policy issues `STOP_SUCCESS`.
  - Click **"View Final Synthesis Report"**:
  - Clear separation between `VERIFIED FACT` (e.g. Commit occurred 8 min before outage) and `HYPOTHESIS` (e.g. Suspected code path regression).

---

## 3. Recovery & Resilience Demonstration

- [ ] **Tool Gateway Error Handling**:
  - When upstream GitHub returns HTTP 503 / 500, `RecoveryEngine` intercepts the failure.
  - Applies exponential backoff (e.g. 200ms $\rightarrow$ 400ms $\rightarrow$ 800ms) with bounded retry budget.
  - If retry is exhausted, engages fallback tool (`github.get_pull_requests`) or triggers Planner reconciliation.
  - View the **"Recovery & Resilience"** tab to inspect all attempt logs.

---

## 4. Human-in-the-Loop (HITL) Guardrails

- [ ] **Path A: Repeated Critic Rejections**:
  - If 3 consecutive Critic rejections occur, status transitions to `HUMAN_REVIEW_REQUIRED`.
  - Glowing amber alert appears in header $\rightarrow$ Operator clicks **"Human Review Required"**.
  - Operator selects **`CONTINUE`**, **`MODIFY_PLAN`**, or **`STOP`**.
- [ ] **Path B: Sensitive Action Gate**:
  - If a sensitive action is proposed (e.g. `ROLLBACK_PRODUCTION`, `UPDATE_DATABASE`), status transitions to `HUMAN_APPROVAL_REQUIRED`.
  - The modal prompts for **`APPROVE ACTION`** or **`REJECT ACTION`**.
  - Spring Boot enforces the decision and logs `HUMAN_ACTION_RECEIVED` in the audit trail.
  - *The frontend never directly executes production actions.*

---

## 5. Deterministic Fallback Verification

- [ ] **AI Service Outage Simulation**:
  - If the FastAPI AI service or external LLM provider is unreachable, Spring Boot logs `LLM_FALLBACK_ACTIVATED`.
  - Automatically activates the `DeterministicPlanner` so the investigation continues safely without downtime.

---

## 6. Auditability & Correlation ID Verification

- [ ] Inspect any event in the Timeline:
  - Every agent action, tool invocation, critic decision, and recovery attempt is tagged with an immutable `X-Correlation-ID`.
  - Complete timeline is exportable and auditable via `GET /api/v1/investigations/{id}/audit`.

---

## 7. Full Test Suite Verification

- [ ] Spring Boot backend tests: `mvn clean test` (**120 passing**)
- [ ] FastAPI AI service tests: `python -m pytest ai_service/tests -v` (**15 passing**)
- [ ] React frontend tests: `cd frontend && npm test` (**8 passing**)
- [ ] Frontend production build: `cd frontend && npm run build` (**Build succeeds**)
- [ ] Total: **143 automated tests passing cleanly**.
