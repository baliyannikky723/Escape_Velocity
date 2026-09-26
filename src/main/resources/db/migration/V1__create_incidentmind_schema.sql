-- IncidentMind Schema Migration V1
-- Phase 1: Core Domain Entities and Relational Schema

-- Sequence for human-readable incident keys (e.g. INC-000001)
CREATE SEQUENCE IF NOT EXISTS incident_key_seq START WITH 1 INCREMENT BY 1;

-- 1. INCIDENTS TABLE
CREATE TABLE incidents (
    id UUID PRIMARY KEY,
    incident_key VARCHAR(50) UNIQUE NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    severity VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    service_name VARCHAR(150) NOT NULL,
    environment VARCHAR(50) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    resolved_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_incident_severity CHECK (severity IN ('P1', 'P2', 'P3', 'P4')),
    CONSTRAINT chk_incident_status CHECK (status IN ('OPEN', 'INVESTIGATING', 'MITIGATED', 'RESOLVED', 'CLOSED'))
);

CREATE INDEX idx_incidents_key ON incidents(incident_key);
CREATE INDEX idx_incidents_status ON incidents(status);
CREATE INDEX idx_incidents_created_at ON incidents(created_at);

-- 2. INVESTIGATIONS TABLE
CREATE TABLE investigations (
    id UUID PRIMARY KEY,
    incident_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL,
    objective TEXT NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    max_tasks INTEGER NOT NULL,
    max_retries_per_task INTEGER NOT NULL,
    max_runtime_seconds INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_investigations_incident FOREIGN KEY (incident_id) REFERENCES incidents(id) ON DELETE CASCADE,
    CONSTRAINT chk_investigation_status CHECK (status IN ('CREATED', 'PLANNING', 'RUNNING', 'WAITING', 'COMPLETED', 'FAILED', 'STOPPED'))
);

CREATE INDEX idx_investigations_incident_id ON investigations(incident_id);
CREATE INDEX idx_investigations_status ON investigations(status);
CREATE INDEX idx_investigations_created_at ON investigations(created_at);

-- 3. INVESTIGATION TASKS TABLE
CREATE TABLE investigation_tasks (
    id UUID PRIMARY KEY,
    investigation_id UUID NOT NULL,
    parent_task_id UUID,
    task_type VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    priority VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 3,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_tasks_investigation FOREIGN KEY (investigation_id) REFERENCES investigations(id) ON DELETE CASCADE,
    CONSTRAINT fk_tasks_parent_task FOREIGN KEY (parent_task_id) REFERENCES investigation_tasks(id) ON DELETE SET NULL,
    CONSTRAINT chk_task_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT chk_task_status CHECK (status IN ('PENDING', 'READY', 'RUNNING', 'BLOCKED', 'COMPLETED', 'FAILED', 'SKIPPED'))
);

CREATE INDEX idx_tasks_investigation_id ON investigation_tasks(investigation_id);
CREATE INDEX idx_tasks_parent_task_id ON investigation_tasks(parent_task_id);
CREATE INDEX idx_tasks_status ON investigation_tasks(status);
CREATE INDEX idx_tasks_created_at ON investigation_tasks(created_at);

-- 4. AGENT RUNS TABLE
CREATE TABLE agent_runs (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL,
    agent_type VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL,
    model_name VARCHAR(100),
    input_payload JSONB,
    output_payload JSONB,
    error_code VARCHAR(100),
    error_message TEXT,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    duration_ms BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_agent_runs_task FOREIGN KEY (task_id) REFERENCES investigation_tasks(id) ON DELETE CASCADE,
    CONSTRAINT chk_agent_run_status CHECK (status IN ('STARTED', 'COMPLETED', 'FAILED', 'TIMED_OUT', 'INVALID_OUTPUT'))
);

CREATE INDEX idx_agent_runs_task_id ON agent_runs(task_id);
CREATE INDEX idx_agent_runs_status ON agent_runs(status);
CREATE INDEX idx_agent_runs_created_at ON agent_runs(created_at);

-- 5. TOOL CALLS TABLE
CREATE TABLE tool_calls (
    id UUID PRIMARY KEY,
    agent_run_id UUID NOT NULL,
    tool_name VARCHAR(150) NOT NULL,
    tool_type VARCHAR(100) NOT NULL,
    request_payload JSONB,
    response_payload JSONB,
    http_status INTEGER,
    status VARCHAR(30) NOT NULL,
    error_code VARCHAR(100),
    error_message TEXT,
    attempt_number INTEGER NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    duration_ms BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_tool_calls_agent_run FOREIGN KEY (agent_run_id) REFERENCES agent_runs(id) ON DELETE CASCADE,
    CONSTRAINT chk_tool_call_status CHECK (status IN ('STARTED', 'SUCCESS', 'FAILED', 'TIMEOUT', 'MALFORMED_RESPONSE'))
);

CREATE INDEX idx_tool_calls_agent_run_id ON tool_calls(agent_run_id);
CREATE INDEX idx_tool_calls_status ON tool_calls(status);
CREATE INDEX idx_tool_calls_created_at ON tool_calls(created_at);

-- 6. EVIDENCE TABLE
CREATE TABLE evidence (
    id UUID PRIMARY KEY,
    investigation_id UUID NOT NULL,
    task_id UUID,
    agent_run_id UUID,
    tool_call_id UUID,
    source_type VARCHAR(100) NOT NULL,
    source_reference VARCHAR(500),
    claim TEXT NOT NULL,
    raw_data JSONB,
    confidence DECIMAL(5,4),
    collected_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_evidence_investigation FOREIGN KEY (investigation_id) REFERENCES investigations(id) ON DELETE CASCADE,
    CONSTRAINT fk_evidence_task FOREIGN KEY (task_id) REFERENCES investigation_tasks(id) ON DELETE SET NULL,
    CONSTRAINT fk_evidence_agent_run FOREIGN KEY (agent_run_id) REFERENCES agent_runs(id) ON DELETE SET NULL,
    CONSTRAINT fk_evidence_tool_call FOREIGN KEY (tool_call_id) REFERENCES tool_calls(id) ON DELETE SET NULL
);

CREATE INDEX idx_evidence_investigation_id ON evidence(investigation_id);
CREATE INDEX idx_evidence_task_id ON evidence(task_id);
CREATE INDEX idx_evidence_agent_run_id ON evidence(agent_run_id);
CREATE INDEX idx_evidence_tool_call_id ON evidence(tool_call_id);
CREATE INDEX idx_evidence_created_at ON evidence(created_at);

-- 7. RECOVERY ATTEMPTS TABLE
CREATE TABLE recovery_attempts (
    id UUID PRIMARY KEY,
    investigation_id UUID NOT NULL,
    task_id UUID,
    tool_call_id UUID,
    recovery_type VARCHAR(50) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    attempt_number INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    details JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_recovery_investigation FOREIGN KEY (investigation_id) REFERENCES investigations(id) ON DELETE CASCADE,
    CONSTRAINT fk_recovery_task FOREIGN KEY (task_id) REFERENCES investigation_tasks(id) ON DELETE SET NULL,
    CONSTRAINT fk_recovery_tool_call FOREIGN KEY (tool_call_id) REFERENCES tool_calls(id) ON DELETE SET NULL,
    CONSTRAINT chk_recovery_type CHECK (recovery_type IN ('RETRY', 'BACKOFF', 'FALLBACK_TOOL', 'REPLAN', 'SKIP', 'ABORT')),
    CONSTRAINT chk_recovery_status CHECK (status IN ('STARTED', 'SUCCESS', 'FAILED'))
);

CREATE INDEX idx_recovery_investigation_id ON recovery_attempts(investigation_id);
CREATE INDEX idx_recovery_task_id ON recovery_attempts(task_id);
CREATE INDEX idx_recovery_tool_call_id ON recovery_attempts(tool_call_id);
CREATE INDEX idx_recovery_created_at ON recovery_attempts(created_at);

-- 8. AUDIT EVENTS TABLE (Immutable / Append-only)
CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    investigation_id UUID,
    task_id UUID,
    agent_run_id UUID,
    event_type VARCHAR(100) NOT NULL,
    actor_type VARCHAR(50) NOT NULL,
    actor_id VARCHAR(255),
    event_data JSONB,
    correlation_id UUID NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_audit_investigation FOREIGN KEY (investigation_id) REFERENCES investigations(id) ON DELETE SET NULL,
    CONSTRAINT fk_audit_task FOREIGN KEY (task_id) REFERENCES investigation_tasks(id) ON DELETE SET NULL,
    CONSTRAINT fk_audit_agent_run FOREIGN KEY (agent_run_id) REFERENCES agent_runs(id) ON DELETE SET NULL,
    CONSTRAINT chk_audit_actor_type CHECK (actor_type IN ('USER', 'SYSTEM', 'AGENT', 'TOOL', 'ORCHESTRATOR'))
);

CREATE INDEX idx_audit_investigation_id ON audit_events(investigation_id);
CREATE INDEX idx_audit_task_id ON audit_events(task_id);
CREATE INDEX idx_audit_agent_run_id ON audit_events(agent_run_id);
CREATE INDEX idx_audit_correlation_id ON audit_events(correlation_id);
CREATE INDEX idx_audit_occurred_at ON audit_events(occurred_at);
