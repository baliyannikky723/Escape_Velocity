-- IncidentMind Schema Migration V3
-- Add assigned_agent_type to investigation_tasks for capability-based agent routing

ALTER TABLE investigation_tasks ADD COLUMN IF NOT EXISTS assigned_agent_type VARCHAR(100);
