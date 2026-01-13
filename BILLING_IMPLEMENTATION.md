# SaaS Billing Implementation - Razorpay Integration

## Overview
Fully automated SaaS billing system using Razorpay Subscriptions with webhook-based activation. NO manual payment marking - all subscription activations happen via verified webhooks.

## Tech Stack
- Spring Boot 3 / Java 17
- PostgreSQL + Flyway migrations
- Razorpay Subscriptions API
- Multi-tenant architecture with JWT authentication

## Database Schema

### Plans Table
Stores subscription plan configurations (FREE, STARTER, GROWTH, AGENCY).
- Configurable user and project limits
- Monthly and yearly pricing
- Feature flags stored as JSONB
- Currency support (default: INR)

### Subscriptions Table
Tracks company subscriptions with full Razorpay integration.
- Status: TRIALING, ACTIVE, PAST_DUE, CANCELED
- Billing cycle: MONTHLY, YEARLY
- Razorpay customer, subscription, and payment IDs
- Period tracking (current_period_start/end)

### Billing Events Table
Idempotent webhook event storage and audit trail.
- Provider-agnostic design (currently RAZORPAY)
- Full payload storage as JSONB
- Processed timestamp tracking

## Environment Variables

Required environment variables for Razorpay integration:

```bash
# Razorpay API Keys (get from Razorpay Dashboard)
RAZORPAY_KEY_ID=rzp_test_your_key_id
RAZORPAY_KEY_SECRET=your_key_secret

# Razorpay Webhook Secret (for signature verification)
RAZORPAY_WEBHOOK_SECRET=your_webhook_secret
```

### Getting Razorpay Credentials

1. **API Keys**: 
   - Login to [Razorpay Dashboard](https://dashboard.razorpay.com/)
   - Go to Settings → API Keys
   - Generate test/live keys

2. **Webhook Secret**:
   - Go to Settings → Webhooks
   - Create webhook pointing to: `https://yourdomain.com/api/v1/billing/webhook/razorpay`
   - Copy the webhook secret

3. **Create Plans on Razorpay Dashboard**:
   You need to create subscription plans on Razorpay matching the naming convention:
   - `plan_free_monthly`
   - `plan_free_yearly`
   - `plan_starter_monthly`
   - `plan_starter_yearly`
   - `plan_growth_monthly`
   - `plan_growth_yearly`
   - `plan_agency_monthly`
   - `plan_agency_yearly`

## API Endpoints

### Public Endpoints

#### GET /api/v1/public/plans
List all active subscription plans.
- No authentication required
- Returns plan details with pricing and limits

**Response Example:**
```json
[
  {
    "id": "uuid",
    "code": "STARTER",
    "name": "Starter Plan",
    "priceMonthly": 999.00,
    "priceYearly": 9990.00,
    "currencyCode": "INR",
    "maxUsers": 5,
    "maxProjects": 10,
    "featureFlags": {
      "kanban": true,
      "time_tracking": true,
      "invoicing": true,
      "reports": true
    },
    "isActive": true
  }
]
```

### Authenticated Endpoints

#### GET /api/v1/billing/subscription
Get current company subscription with usage metrics.
- Requires JWT authentication
- Returns subscription status, limits, and current usage

**Response Example:**
```json
{
  "id": "uuid",
  "companyId": "uuid",
  "planCode": "STARTER",
  "planName": "Starter Plan",
  "status": "ACTIVE",
  "billingCycle": "MONTHLY",
  "currentPeriodStart": "2026-01-01T00:00:00",
  "currentPeriodEnd": "2026-02-01T00:00:00",
  "cancelAtPeriodEnd": false,
  "maxUsers": 5,
  "maxProjects": 10,
  "currentUsers": 3,
  "currentProjects": 5,
  "rzSubscriptionId": "sub_xxx"
}
```

#### POST /api/v1/billing/checkout
Create Razorpay checkout session for subscription.
- Requires JWT authentication
- Only TOP_USER, SUPER_USER, or ADMIN can create checkouts
- Returns details needed for frontend Razorpay integration

**Request Body:**
```json
{
  "planCode": "GROWTH",
  "billingCycle": "MONTHLY"
}
```

**Response Example:**
```json
{
  "keyId": "rzp_test_xxx",
  "subscriptionId": "sub_xxx",
  "customerId": "cust_xxx",
  "planCode": "GROWTH",
  "billingCycle": "MONTHLY",
  "amount": 2999.00,
  "currency": "INR",
  "companyName": "ACME Inc",
  "userName": "John Doe",
  "userEmail": "john@acme.com"
}
```

### Webhook Endpoint

#### POST /api/v1/billing/webhook/razorpay
Razorpay webhook handler (NO authentication).
- Signature verification using RAZORPAY_WEBHOOK_SECRET
- Idempotent processing via event_id
- Automatic subscription status updates

**Headers:**
```
X-Razorpay-Signature: <signature>
```

**Supported Events:**
- `subscription.activated` → Status: ACTIVE
- `subscription.charged` → Status: ACTIVE (renew period)
- `subscription.completed` → Status: CANCELED
- `subscription.cancelled` → Status: CANCELED
- `subscription.halted` → Status: PAST_DUE
- `payment.failed` → Status: PAST_DUE

## Subscription Enforcement

### User Creation
Before creating a new user:
1. Checks subscription status (must be ACTIVE or TRIALING)
2. Counts current active users
3. Validates against plan's `maxUsers` limit
4. Returns `409 CONFLICT` if limit exceeded
5. Returns `402 PAYMENT_REQUIRED` if subscription inactive

### Project Creation
Before creating a new project:
1. Checks subscription status (must be ACTIVE or TRIALING)
2. Counts current projects
3. Validates against plan's `maxProjects` limit
4. Returns `409 CONFLICT` if limit exceeded
5. Returns `402 PAYMENT_REQUIRED` if subscription inactive

## Default Plans

| Plan | Max Users | Max Projects | Price (Monthly) | Price (Yearly) |
|------|-----------|--------------|-----------------|----------------|
| FREE | 2 | 2 | ₹0 | ₹0 |
| STARTER | 5 | 10 | ₹999 | ₹9,990 |
| GROWTH | 15 | 9999 | ₹2,999 | ₹29,990 |
| AGENCY | 50 | 9999 | ₹7,999 | ₹79,990 |

## Frontend Integration Example

```javascript
// 1. Call checkout endpoint to get subscription details
const checkoutResponse = await fetch('/api/v1/billing/checkout', {
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    planCode: 'GROWTH',
    billingCycle: 'MONTHLY'
  })
});

const checkout = await checkoutResponse.json();

// 2. Open Razorpay checkout
const options = {
  key: checkout.keyId,
  subscription_id: checkout.subscriptionId,
  name: checkout.companyName,
  description: `${checkout.planCode} - ${checkout.billingCycle}`,
  prefill: {
    name: checkout.userName,
    email: checkout.userEmail
  },
  theme: {
    color: '#3399cc'
  },
  handler: function(response) {
    // Payment successful - webhook will activate subscription
    console.log('Payment successful:', response);
    // Redirect to dashboard or show success message
  }
};

const rzp = new Razorpay(options);
rzp.open();
```

## Security Features

1. **Webhook Signature Verification**: All webhooks verified using HMAC-SHA256
2. **Idempotency**: Duplicate events automatically ignored via event_id
3. **Role-Based Access**: Only admin roles can create checkouts
4. **No Card Storage**: All payment data handled by Razorpay
5. **Automatic Enforcement**: Limits checked on every user/project creation

## Database Migrations

Migrations are automatically applied on startup via Flyway:
- `V29__billing_core.sql` - Creates billing tables
- `V30__seed_plans.sql` - Seeds default plans

## Error Handling

| HTTP Code | Error | Description |
|-----------|-------|-------------|
| 402 | Payment Required | Subscription inactive or missing |
| 409 | Conflict | Plan limit reached |
| 403 | Forbidden | Insufficient permissions |
| 400 | Bad Request | Invalid webhook signature |

## Testing Webhooks Locally

Use ngrok to expose local server for webhook testing:

```bash
# Start ngrok
ngrok http 8081

# Update Razorpay webhook URL to:
https://your-ngrok-url.ngrok.io/api/v1/billing/webhook/razorpay
```

## Deployment Checklist

- [ ] Set production Razorpay API keys
- [ ] Create production plans on Razorpay dashboard
- [ ] Configure webhook URL in Razorpay dashboard
- [ ] Set webhook secret in environment
- [ ] Test webhook delivery
- [ ] Monitor billing_events table for errors
- [ ] Set up alerts for failed payments

## Monitoring

Key metrics to monitor:
1. Webhook delivery success rate (check `processed_at` in billing_events)
2. Subscription status distribution
3. Plan usage vs limits
4. Failed payment events

## Support

For Razorpay API documentation:
- [Subscriptions API](https://razorpay.com/docs/api/subscriptions/)
- [Webhooks](https://razorpay.com/docs/webhooks/)
- [Testing](https://razorpay.com/docs/payments/payments/test-card-details/)
