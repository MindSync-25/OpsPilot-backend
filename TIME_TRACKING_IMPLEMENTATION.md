# Time Tracking Module Implementation

## Overview
Comprehensive timesheet and leave management system with strict role-based scoping using existing team hierarchy.

## Components Implemented

### 1. OrgScopeService
**File:** `src/main/java/com/itops/service/OrgScopeService.java`

Central service for role-based user visibility:
- **TOP_USER**: Views all company users
- **SUPER_USER**: Views users in teams they created (ADMIN/USER roles only, excludes other SUPER_USERs)
- **ADMIN**: Views users in their own team
- **USER**: Views only themselves

**Key Methods:**
- `getAllowedUserIds(requesterId, role, companyId)` - Returns Set<UUID> of viewable users
- `canAccessUser(requesterId, role, targetUserId, companyId)` - Boolean check for single user

### 2. Timesheets Module

#### Database Migration
**File:** `src/main/resources/db/migration/V22__create_timesheets.sql`

Creates `timesheets` table with:
- Weekly tracking (week_start = Monday)
- Status workflow: DRAFT → SUBMITTED → APPROVED/REJECTED
- Minutes tracking: total_minutes, billable_minutes, non_billable_minutes
- Approval metadata: submitted_at, approved_at, approved_by, rejection_reason
- Soft delete support (deleted_at)
- Unique constraint: (company_id, user_id, week_start)

#### Domain Layer
**Files:**
- `src/main/java/com/itops/domain/Timesheet.java` - JPA entity
- `src/main/java/com/itops/repository/TimesheetRepository.java` - Repository with custom queries

#### DTOs
**Files:**
- `src/main/java/com/itops/dto/TimesheetResponse.java` - Response with optional project breakdown
- `src/main/java/com/itops/dto/UpsertTimesheetRequest.java` - Create/update request
- `src/main/java/com/itops/dto/SubmitTimesheetRequest.java` - Submit for approval
- `src/main/java/com/itops/dto/ReviewTimesheetRequest.java` - Approve/reject request

#### Business Logic
**File:** `src/main/java/com/itops/service/TimesheetService.java`

**Key Features:**
- **Auto-calculation**: Derives totals from time_entries for the week range
- **Get/Create Pattern**: `getOrCreateTimesheet(userId, weekStart, companyId)` returns existing or creates new
- **Submit Workflow**: Changes status to SUBMITTED, validates ownership
- **Review Workflow**: Approvers see only users in their scope, can approve/reject with reason
- **Filtering**: Support for weekStart, status, userId filters

**Key Methods:**
- `getOrCreateTimesheet()` - Get my timesheet for a week
- `submitTimesheet()` - Submit for approval
- `reviewTimesheet()` - Approve/reject (role-scoped)
- `getTimesheets()` - List with filters (role-scoped)
- `updateTimesheetTotals()` - Recalculate from time_entries

#### REST API
**File:** `src/main/java/com/itops/controller/TimesheetController.java`

**Endpoints:**
- `GET /api/v1/timesheets/me?weekStart={date}` - Get/create my timesheet
- `POST /api/v1/timesheets/me/submit` - Submit my timesheet
- `GET /api/v1/timesheets?weekStart={date}&status={status}&userId={uuid}` - List timesheets
- `PATCH /api/v1/timesheets/{id}/review` - Approve/reject timesheet

### 3. Leave Management Module

#### Database Migration
**File:** `src/main/resources/db/migration/V23__create_leave_requests.sql`

Creates `leave_requests` table with:
- Date range: start_date, end_date
- Leave types: PTO, SICK, HOLIDAY, UNPAID
- Status: PENDING, APPROVED, REJECTED, CANCELLED
- Approval metadata: approver_id, approved_at, rejection_reason
- Soft delete support (deleted_at)

#### Domain Layer
**Files:**
- `src/main/java/com/itops/domain/LeaveRequest.java` - JPA entity
- `src/main/java/com/itops/repository/LeaveRequestRepository.java` - Repository with date range queries

#### DTOs
**Files:**
- `src/main/java/com/itops/dto/CreateLeaveRequest.java` - Create request (validates type)
- `src/main/java/com/itops/dto/UpdateLeaveStatusRequest.java` - Status update (APPROVED|REJECTED|CANCELLED)
- `src/main/java/com/itops/dto/LeaveResponse.java` - Response with all fields

#### Business Logic
**File:** `src/main/java/com/itops/service/LeaveService.java`

**Key Features:**
- **Create**: Validates end_date >= start_date
- **Role Scoping**: Users see only leaves in their scope
- **User Actions**: Can cancel their own requests (status → CANCELLED)
- **Manager Actions**: Can approve/reject requests in their scope
- **SUPER_USER Rule**: Cannot approve another SUPER_USER's leave

**Key Methods:**
- `createLeaveRequest()` - Create new leave request
- `getMyLeaveRequests()` - Get my requests
- `getAllLeaveRequests()` - List with filters (role-scoped)
- `updateLeaveStatus()` - Approve/reject/cancel (role-based)

#### REST API
**File:** `src/main/java/com/itops/controller/LeaveController.java`

**Endpoints:**
- `GET /api/v1/leave/me` - Get my leave requests
- `POST /api/v1/leave/me` - Create new leave request
- `GET /api/v1/leave?status={status}&fromDate={date}&toDate={date}&userId={uuid}` - List requests
- `PATCH /api/v1/leave/{id}/status` - Approve/reject/cancel request

## Role-Based Access Rules

### TOP_USER
- Views all company users
- Can approve/reject anyone's timesheets and leave
- Can view all timesheets and leave requests

### SUPER_USER
- Views users in teams they created (ADMIN and USER roles only)
- Can approve/reject team members' timesheets and leave
- **Cannot** approve another SUPER_USER's leave
- **Cannot** see other SUPER_USERs' data

### ADMIN
- Views users in their own team
- Can approve/reject team members' timesheets and leave
- Cannot see users outside their team

### USER
- Views only themselves
- Can submit their own timesheets
- Can create leave requests
- Can cancel their own leave requests
- Cannot approve/reject anything

## Technical Details

### Multi-Tenant Scoping
All queries filter by `companyId` extracted from JWT claims:
```java
UUID companyId = UUID.fromString(authentication.getPrincipal().toString());
```

### Soft Delete
All entities use `deleted_at` column:
- Repository queries include `AndDeletedAtIsNull` clause
- Entities have `@Column(name = "deleted_at")` field
- Service layer sets `deletedAt = LocalDateTime.now()` instead of physical delete

### JWT Claims Extraction
Controllers use helper methods:
```java
private UUID getCompanyIdFromAuth(Authentication authentication) {
    return UUID.fromString(((Map<String, String>) authentication.getPrincipal()).get("companyId"));
}

private UUID getUserIdFromAuth(Authentication authentication) {
    return UUID.fromString(((Map<String, String>) authentication.getPrincipal()).get("userId"));
}

private String getRoleFromAuth(Authentication authentication) {
    return ((Map<String, String>) authentication.getPrincipal()).get("role");
}
```

### Timesheet Calculation
Timesheets derive totals from `time_entries`:
1. Query time_entries for user in date range [week_start, week_start + 6 days]
2. Convert hours to minutes: `hours * 60`
3. Sum total_minutes
4. Sum billable_minutes (where is_billable = true)
5. Calculate non_billable_minutes = total - billable

### Validation
All request DTOs use Bean Validation:
- `@NotNull` - Required fields
- `@Pattern(regexp = "APPROVED|REJECTED")` - Status validation
- `@Pattern(regexp = "PTO|SICK|HOLIDAY|UNPAID")` - Leave type validation

## Database Schema

### Timesheets Table
```sql
CREATE TABLE timesheets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL,
    user_id UUID NOT NULL,
    week_start DATE NOT NULL, -- Always Monday
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    total_minutes INTEGER NOT NULL DEFAULT 0,
    billable_minutes INTEGER NOT NULL DEFAULT 0,
    non_billable_minutes INTEGER NOT NULL DEFAULT 0,
    submitted_at TIMESTAMP,
    approved_at TIMESTAMP,
    approved_by UUID,
    rejection_reason TEXT,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (company_id, user_id, week_start)
);
```

### Leave Requests Table
```sql
CREATE TABLE leave_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL,
    user_id UUID NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    type VARCHAR(20) NOT NULL, -- PTO, SICK, HOLIDAY, UNPAID
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reason TEXT,
    approver_id UUID,
    approved_at TIMESTAMP,
    rejection_reason TEXT,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

## Build & Deploy

### Compilation
```bash
mvn clean compile -DskipTests
```
✅ Successfully compiles with no errors (122 source files)

### Migrations
Flyway auto-applies V22 and V23 on application startup:
```
Current version of schema "public": 23
Schema "public" is up to date.
```

### Running
```bash
mvn spring-boot:run
```
✅ Application starts on port 8081 with context path `/api/v1`

## Next Steps - Frontend Implementation

### Required Pages/Components
1. **Timesheet List View** - Weekly grid showing time entries
2. **Timesheet Detail View** - Individual timesheet with project breakdown
3. **Timesheet Submission** - Button to submit for approval
4. **Manager Approval Dashboard** - List of pending timesheets
5. **Leave Request Form** - Create new leave request
6. **Leave Calendar** - Visual display of team leave
7. **Leave Approval Dashboard** - Manager view of pending requests

### API Integration
- Use existing `api.ts` for HTTP calls
- Add role-based rendering using `useUserRole` hook
- Filter UI based on user's role (TOP_USER, SUPER_USER, ADMIN, USER)

### State Management
- Add timesheet slice to Redux store
- Add leave request slice to Redux store
- Handle optimistic updates for submissions
- Real-time status updates

### Role-Based UI Rules
- **USER**: See only own timesheets/leave, submit/cancel buttons
- **ADMIN/SUPER_USER/TOP_USER**: See approval dashboard, approve/reject buttons
- **SUPER_USER**: Hide other SUPER_USERs from leave approval list
- **TOP_USER**: See everything without restrictions
