# Invoice Module Backend Implementation - Summary

## Files Created/Modified

### DTOs (Data Transfer Objects) - 7 files
1. **InvoiceItemRequest.java** - Request DTO for invoice line items
   - Location: src/main/java/com/itops/dto/
   - Fields: description, quantity, unitPrice
   - Validation: @NotBlank, @NotNull, @Min

2. **InvoiceItemResponse.java** - Response DTO for invoice line items
   - Location: src/main/java/com/itops/dto/
   - Fields: id, description, quantity, unitPrice, amount
   - Uses @Builder pattern

3. **CreateInvoiceRequest.java** - Request DTO for creating invoices
   - Location: src/main/java/com/itops/dto/
   - Fields: clientId, projectId (optional), issueDate, dueDate, taxRate, notes, items
   - Validation: @NotNull, @NotEmpty, @Valid
   - Default tax rate: 18.00%

4. **UpdateInvoiceRequest.java** - Request DTO for updating invoices
   - Location: src/main/java/com/itops/dto/
   - Fields: clientId, projectId, issueDate, dueDate, taxRate, notes, items
   - All fields optional for partial updates

5. **UpdateInvoiceStatusRequest.java** - Request DTO for status updates
   - Location: src/main/java/com/itops/dto/
   - Fields: status
   - Validation: @Pattern matching DRAFT|SENT|PAID|OVERDUE|CANCELLED

6. **InvoiceResponse.java** - Response DTO for invoices
   - Location: src/main/java/com/itops/dto/
   - Fields: id, companyId, clientId, projectId, invoiceNumber, status, issueDate, dueDate, subtotal, taxRate, taxAmount, total, notes, createdAt, updatedAt, isOverdue, items
   - Computed field: isOverdue (true if dueDate < today && status == SENT)

### Repository Layer - 1 file
7. **InvoiceRepository.java** - JPA repository for Invoice entity
   - Location: src/main/java/com/itops/repository/
   - Methods:
     - findAllByCompanyIdAndDeletedAtIsNull()
     - findByIdAndCompanyIdAndDeletedAtIsNull()
     - findByInvoiceNumberAndCompanyId()
     - existsByInvoiceNumberAndCompanyId()
     - findFilteredInvoices() - Custom @Query for filtering by clientId, projectId, status, date ranges

### Service Layer - 1 file
8. **InvoiceService.java** - Business logic for invoice management (~350 lines)
   - Location: src/main/java/com/itops/service/
   - Public Methods:
     - getAllInvoices() - Retrieve all invoices with filtering and sorting
     - getInvoice() - Get single invoice by ID
     - createInvoice() - Create new invoice with auto-generated invoice number
     - updateInvoice() - Update invoice (blocks PAID/CANCELLED)
     - updateInvoiceStatus() - Change invoice status with validation
     - deleteInvoice() - Soft delete invoice and items (blocks PAID)
   
   - Private Helper Methods:
     - generateInvoiceNumber() - Format: INV-YYYYMMDD-XXXXXX (6 random digits, retry on collision)
     - calculateTotals() - BigDecimal calculations with HALF_UP rounding
     - validateStatusTransition() - Enforce status state machine
     - createInvoiceItems() - Map request DTOs to entities
     - sortInvoices() - Support sorting by issueDate, dueDate, total
     - toResponse() - Entity to DTO conversion
   
   - Business Rules:
     - Default tax rate: 18.00%
     - Invoice number retries: up to 10 attempts
     - Cannot update PAID/CANCELLED invoices
     - Cannot delete PAID invoices
     - Validates client and project existence
     - Soft deletes invoice items when updating

### Controller Layer - 1 file (Modified)
9. **InvoiceController.java** - REST API endpoints (~140 lines)
   - Location: src/main/java/com/itops/controller/
   - Complete rewrite with new endpoints
   
   - REST Endpoints:
     - GET /api/v1/invoices - List all invoices with filtering
       Query params: clientId, projectId, status, fromIssueDate, toIssueDate, overdueOnly, sortBy
     - GET /api/v1/invoices/{id} - Get single invoice
     - POST /api/v1/invoices - Create new invoice
     - PUT /api/v1/invoices/{id} - Update invoice
     - PATCH /api/v1/invoices/{id}/status - Update status only
     - DELETE /api/v1/invoices/{id} - Soft delete invoice
   
   - Security: Role-based access control
     - Blocks: CLIENT, USER roles (403 Forbidden)
     - Allows: TOP_USER, SUPER_USER, ADMIN roles
     - Extracts companyId from JWT token

## Technical Implementation Details

### Invoice Number Generation
- Format: INV-YYYYMMDD-XXXXXX
- Example: INV-20231215-543210
- Collision handling: Retry up to 10 times with new random 6-digit number
- Uniqueness: Per company (checked via existsByInvoiceNumberAndCompanyId)

### Tax & Total Calculations
Uses BigDecimal with scale 2 and RoundingMode.HALF_UP for all monetary calculations:
- **Subtotal** = Sum of (quantity  unitPrice) for all items
- **Tax Amount** = (subtotal  taxRate) / 100
- **Total** = subtotal + taxAmount
- Each line item amount = quantity  unitPrice (rounded to 2 decimals)

### Status Transition Rules
State machine enforced by alidateStatusTransition():
- **DRAFT**  SENT | CANCELLED
- **SENT**  PAID | OVERDUE | CANCELLED
- **OVERDUE**  PAID | CANCELLED
- **PAID**  No transitions allowed
- **CANCELLED**  No transitions allowed

### Filtering Capabilities
The getAllInvoices endpoint supports:
- **By Client**: Filter invoices for specific client
- **By Project**: Filter invoices for specific project  
- **By Status**: Filter by DRAFT, SENT, PAID, OVERDUE, CANCELLED
- **By Date Range**: Filter by issue date (fromIssueDate to toIssueDate)
- **Overdue Only**: Show only overdue invoices (dueDate < today && status == SENT)
- **Sorting**: issueDate_asc/desc, dueDate_asc/desc, total_asc/desc (default: issueDate_desc)

### Soft Delete Support
All deletes are soft deletes:
- Sets deletedAt timestamp instead of removing records
- Deleting invoice also soft deletes all associated items
- Queries filter out deleted records using deletedAt IS NULL

### Validation Rules
- Client must exist and belong to same company
- Project (if specified) must exist and belong to same company
- Cannot update invoices with PAID or CANCELLED status
- Cannot delete PAID invoices
- Status transitions follow state machine rules
- Items array must not be empty when creating invoice

## Compilation Status
 **Backend compiles successfully** - mvn clean compile -DskipTests passes with no errors

## Example API Request/Response

### Create Invoice
**Request:**
\\\json
POST /api/v1/invoices
Authorization: Bearer <JWT_TOKEN>

{
  "clientId": "123e4567-e89b-12d3-a456-426614174000",
  "projectId": "234e5678-e89b-12d3-a456-426614174111",
  "issueDate": "2024-01-15",
  "dueDate": "2024-02-15",
  "taxRate": 18.00,
  "notes": "Website development invoice",
  "items": [
    {
      "description": "Frontend Development",
      "quantity": 40,
      "unitPrice": 100.00
    },
    {
      "description": "Backend API Development",
      "quantity": 30,
      "unitPrice": 120.00
    }
  ]
}
\\\

**Response:**
\\\json
{
  "id": "345e6789-e89b-12d3-a456-426614174222",
  "companyId": "456e7890-e89b-12d3-a456-426614174333",
  "clientId": "123e4567-e89b-12d3-a456-426614174000",
  "projectId": "234e5678-e89b-12d3-a456-426614174111",
  "invoiceNumber": "INV-20240115-654321",
  "issueDate": "2024-01-15",
  "dueDate": "2024-02-15",
  "status": "DRAFT",
  "subtotal": 7600.00,
  "taxRate": 18.00,
  "taxAmount": 1368.00,
  "total": 8968.00,
  "notes": "Website development invoice",
  "isOverdue": false,
  "createdAt": "2024-01-15T10:30:00",
  "updatedAt": "2024-01-15T10:30:00",
  "items": [
    {
      "id": "567e8901-e89b-12d3-a456-426614174444",
      "description": "Frontend Development",
      "quantity": 40,
      "unitPrice": 100.00,
      "amount": 4000.00
    },
    {
      "id": "678e9012-e89b-12d3-a456-426614174555",
      "description": "Backend API Development",
      "quantity": 30,
      "unitPrice": 120.00,
      "amount": 3600.00
    }
  ]
}
\\\

### Update Invoice Status
**Request:**
\\\json
PATCH /api/v1/invoices/345e6789-e89b-12d3-a456-426614174222/status
Authorization: Bearer <JWT_TOKEN>

{
  "status": "SENT"
}
\\\

**Response:** Same as create response with updated status and updatedAt timestamp

### Get Filtered Invoices
**Request:**
\\\
GET /api/v1/invoices?clientId=123e4567-e89b-12d3-a456-426614174000&status=SENT&sortBy=dueDate_asc
Authorization: Bearer <JWT_TOKEN>
\\\

**Response:** Array of InvoiceResponse objects matching the filters

## Totals Calculation Verification
 **All calculations performed by backend**
- Subtotal: Sum of all item amounts (quantity  unitPrice)
- Tax Amount: (subtotal  taxRate) / 100
- Total: subtotal + taxAmount
- All BigDecimal operations use scale(2) and RoundingMode.HALF_UP
- Frontend does NOT need to calculate - all computed server-side

## Next Steps
1. Test endpoints using Postman/Insomnia
2. Implement frontend Invoice module
3. Add Invoice tab in Project Detail page
4. Create Invoice listing and detail pages
5. Implement invoice PDF generation (future enhancement)

