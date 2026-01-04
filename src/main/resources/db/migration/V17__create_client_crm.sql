-- V17__create_client_crm.sql
-- Create client_crm table for CRM integration (1:1 with clients)

CREATE TABLE client_crm (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id UUID NOT NULL UNIQUE REFERENCES clients(id) ON DELETE CASCADE,
    lead_stage VARCHAR(32) NOT NULL DEFAULT 'PROSPECT',
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_client_crm_client_id ON client_crm(client_id);
CREATE INDEX idx_client_crm_lead_stage ON client_crm(lead_stage);

-- Stages: PROSPECT, CONTACTED, PROPOSAL_SENT, WON, LOST
