# Auto-Invoice Generation from Time Entries - Implementation Complete

## Overview
Implemented automatic invoice generation from unbilled time tracking entries with preview-confirm workflow.

## Database Changes

### Migration V26: Link Time Entries to Invoices
- Added `invoice_id UUID` to time_entries table
- Added `billed_at TIMESTAMP` to time_entries table
- Created indexes for performance (invoice_id, unbilled queries, date ranges)
- Prevents double billing with NULL checks

### Migration V27: Add Hourly Rate to Users
- Added `hourly_rate DECIMAL(10,2)` to users table
- Created index for rate validation queries
- Required for calculating invoice amounts from time entries

## Backend Components

### DTOs (6 files)

**InvoiceGenerationPreviewRequest.java**
- clientId (required)
- projectId (optional - defaults to all projects for client)
- fromDate, toDate (required date range)
- billableOnly (default true - filters only billable entries)
- groupBy (USER or TASK - determines line item grouping)
- includeDescriptions (boolean - aggregates notes in line items)

**PreviewLineItem.java**
- description
- quantityMinutes, quantityHours (both included for clarity)
- unitPrice (hourly rate)
- amount (hours × rate)
- userId/userName (for USER grouping)
- taskId/taskTitle (for TASK grouping)
- aggregatedNotes (if includeDescriptions=true)

**MissingRateUser.java**
- userId, name, email
- message explaining missing rate
- Returned in preview when users lack hourly_rate configuration

**InvoiceGenerationPreviewResponse.java**
- Client and project information
- Date range
- totalMinutes, totalHours, subtotal
- taxRate, taxAmount, total
- List<PreviewLineItem> lineItems
- List<MissingRateUser> missingRates
- entriesCount, canGenerate (boolean validation result)
- message (explanation why generation blocked if canGenerate=false)

**InvoiceGenerateRequest.java**
- Extends InvoiceGenerationPreviewRequest
- taxRate (override default 18%)
- notes (for invoice)
- confirmed (boolean - must be true to proceed)

**InvoiceGenerateResponse.java**
- invoiceId, invoiceNumber
- total amount
- billedEntriesCount
- message (success confirmation)

### Domain Updates

**TimeEntry.java**
- Added `UUID invoiceId` field
- Added `LocalDateTime billedAt` field
- Tracks which invoice billed this entry and when

**User.java**
- Added `BigDecimal hourlyRate` field
- Used for calculating invoice line item amounts

### Repository Changes

**TimeEntryRepository.java**
- `findUnbilledEntriesForProjects()` - JPQL query with filters:
  - companyId (multi-tenant isolation)
  - projectIds (client's projects)
  - fromDate/toDate (date range)
  - billableOnly (optional filter)
  - WHERE invoice_id IS NULL (prevent double billing)
  
- `updateInvoiceIdForEntries()` - @Modifying bulk update:
  - Sets invoice_id and billed_at for selected entries
  - Atomic operation to link entries to invoice

**ProjectRepository.java**
- Added `findByClientIdAndCompanyId()` - get all projects for client

**ClientRepository.java**
- Already had `findByIdAndCompanyIdAndDeletedAtIsNull()` - used for validation

### Service Layer

**InvoiceGenerationService.java** (465 lines)

**Key Methods:**

1. `previewFromTime()` - Preview invoice before generating
   - Validates date range
   - Verifies client ownership
   - Fetches unbilled time entries
   - Checks all users have hourly rates
   - Groups entries by USER or TASK
   - Calculates totals with BigDecimal precision
   - Returns preview with canGenerate flag

2. `generateDraftInvoiceFromTime()` - Create actual invoice
   - Runs preview validation first
   - Checks confirmed=true
   - Ensures canGenerate=true
   - Creates Invoice entity (status=DRAFT, due_date=+15 days)
   - Generates invoice number
   - Creates InvoiceItem records from preview line items
   - Atomically links time entries (sets invoice_id + billed_at)
   - All in @Transactional for rollback safety

3. `buildLineItemsByUser()` - USER grouping strategy
   - Groups time entries by user
   - Sums minutes per user
   - Calculates: hours = minutes/60, amount = hours × hourly_rate
   - One line item per user with time in date range

4. `buildLineItemsByTask()` - TASK grouping strategy
   - Groups time entries by task
   - For tasks with multiple users, calculates blended rate
   - Sums minutes per task
   - One line item per task with aggregated time

5. `checkMissingRates()` - Rate validation
   - Returns list of users without configured hourly_rate
   - Sets canGenerate=false if any missing
   - Provides clear message with user names

**Business Rules:**
- Prevents double billing (WHERE invoice_id IS NULL)
- Requires hourly rates for all users
- Default tax rate: 18%
- Default due date: +15 days from today
- BigDecimal with HALF_UP rounding, scale 2
- Transaction safety (rollback on conflicts)

**InvoiceService.java**
- Changed `generateInvoiceNumber()` from private → public
- Allows InvoiceGenerationService to generate numbers

### Controller Layer

**InvoiceGenerationController.java**

**Endpoints:**

1. `POST /api/v1/invoices/generation/preview`
   - Security: @PreAuthorize TOP_USER, SUPER_USER, ADMIN
   - Input: InvoiceGenerationPreviewRequest
   - Output: InvoiceGenerationPreviewResponse
   - Purpose: Show what invoice would contain WITHOUT creating

2. `POST /api/v1/invoices/generation/generate`
   - Security: @PreAuthorize TOP_USER, SUPER_USER, ADMIN
   - Input: InvoiceGenerateRequest (includes confirmed=true)
   - Output: InvoiceGenerateResponse
   - Purpose: Create DRAFT invoice and link time entries

**Authentication:**
- Uses JwtUtil to extract companyId and userId from token
- Helper methods: getCompanyIdFromRequest(), getUserIdFromRequest()
- Follows same pattern as other controllers

### Exception Handling

**BusinessException.java** (created)
- Runtime exception for business logic violations
- Thrown when generation blocked (missing rates, not confirmed, no entries)

**IllegalArgumentException**
- Used for validation errors (invalid date range, project-client mismatch)

## Workflow

### Preview Flow
1. Manager selects client, date range, grouping (USER/TASK)
2. POST /api/v1/invoices/generation/preview
3. System fetches unbilled entries for client's projects
4. Validates all users have hourly rates
5. Groups entries and calculates totals
6. Returns preview with:
   - Line items showing hours × rate
   - Total with tax
   - Missing rates (if any)
   - canGenerate boolean
7. Manager reviews amounts and line items

### Generate Flow
1. Manager confirms preview (may adjust tax rate)
2. POST /api/v1/invoices/generation/generate with confirmed=true
3. System re-validates (no entries billed since preview)
4. Creates Invoice entity (status=DRAFT)
5. Creates InvoiceItem records for each line item
6. Atomically updates time_entries.invoice_id + billed_at
7. Returns invoice number and ID
8. Manager can edit DRAFT invoice before sending

## Security

- Role-based access: TOP_USER, SUPER_USER, ADMIN only
- Multi-tenant isolation: All queries filter by companyId from JWT
- Prevents double billing: invoice_id IS NULL checks
- Transaction safety: @Transactional rollback on errors

## Testing Recommendations

1. **Unit Tests:**
   - buildLineItemsByUser with various time combinations
   - buildLineItemsByTask with multi-user tasks
   - checkMissingRates validation
   - BigDecimal calculation precision

2. **Integration Tests:**
   - Full preview → generate workflow
   - Double billing prevention (try billing same entries twice)
   - Missing rate handling (user without hourly_rate)
   - Transaction rollback (simulate failure mid-generate)

3. **API Tests:**
   - Preview endpoint with various filters
   - Generate with confirmed=false (should fail)
   - Generate without preview (should work, runs validation)
   - Role-based access (USER/CLIENT should be denied)

## Future Enhancements

1. **Email notification** when invoice generated
2. **PDF generation** from preview
3. **Recurring invoices** from time entries (monthly automatic)
4. **Rate overrides** per project or client
5. **Approval workflow** before generating
6. **Batch generation** for multiple clients
7. **Time entry locking** after billing (prevent edits)

## Configuration

No additional configuration needed. Uses existing:
- JWT authentication
- Database connection
- Transaction management
- Role-based security

## Deployment Notes

1. Run Flyway migrations (V26, V27 will be applied automatically)
2. Backend compiles successfully (BUILD SUCCESS)
3. No frontend changes needed yet
4. Update user profiles to set hourly_rate before using
5. Test with sample time entries in development

---

**Status:** ✅ Backend Implementation Complete (Compilation Successful)
**Date:** 2026-01-03
**Lines of Code:** ~1,200 (service, DTOs, controller, migrations)
