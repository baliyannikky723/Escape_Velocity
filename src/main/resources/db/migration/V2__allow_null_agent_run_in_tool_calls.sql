-- IncidentMind Schema Migration V2
-- Allow direct Tool Gateway execution where agent_run_id is optional/null

ALTER TABLE tool_calls ALTER COLUMN agent_run_id DROP NOT NULL;
