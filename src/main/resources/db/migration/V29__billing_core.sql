-- V29: Create billing tables for SaaS subscription management

-- Plans table
CREATE TABLE plans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL,
    price_monthly NUMERIC(10,2) NOT NULL,
    price_yearly NUMERIC(10,2) NOT NULL,
    currency_code VARCHAR(10) NOT NULL DEFAULT 'INR',
    max_users INT NOT NULL,
    max_projects INT NOT NULL,
    feature_flags JSONB NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP
);

CREATE INDEX idx_plans_code ON plans(code);
CREATE INDEX idx_plans_is_active ON plans(is_active);

-- Subscriptions table
CREATE TABLE subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES companies(id),
    plan_code VARCHAR(50) NOT NULL REFERENCES plans(code),
    status VARCHAR(30) NOT NULL,
    billing_cycle VARCHAR(20) NOT NULL,
    current_period_start TIMESTAMP NULL,
    current_period_end TIMESTAMP NULL,
    cancel_at_period_end BOOLEAN NOT NULL DEFAULT FALSE,
    
    -- Razorpay fields
    rz_customer_id VARCHAR(64) NULL,
    rz_subscription_id VARCHAR(64) NULL,
    rz_plan_id VARCHAR(64) NULL,
    rz_payment_id VARCHAR(64) NULL,
    rz_order_id VARCHAR(64) NULL,
    rz_signature VARCHAR(256) NULL,
    last_event_id VARCHAR(128) NULL,
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP
);

CREATE INDEX idx_subscriptions_company_id ON subscriptions(company_id);
CREATE INDEX idx_subscriptions_status ON subscriptions(status);
CREATE UNIQUE INDEX idx_subscriptions_rz_subscription_id ON subscriptions(rz_subscription_id) WHERE rz_subscription_id IS NOT NULL;
CREATE INDEX idx_subscriptions_plan_code ON subscriptions(plan_code);

-- Billing events table (idempotency + audit)
CREATE TABLE billing_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider VARCHAR(20) NOT NULL DEFAULT 'RAZORPAY',
    event_id VARCHAR(128) UNIQUE NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    company_id UUID NULL,
    subscription_id UUID NULL,
    received_at TIMESTAMP NOT NULL DEFAULT now(),
    processed_at TIMESTAMP NULL,
    payload JSONB NOT NULL
);

CREATE INDEX idx_billing_events_event_id ON billing_events(event_id);
CREATE INDEX idx_billing_events_company_id ON billing_events(company_id);
CREATE INDEX idx_billing_events_subscription_id ON billing_events(subscription_id);
CREATE INDEX idx_billing_events_processed_at ON billing_events(processed_at);
