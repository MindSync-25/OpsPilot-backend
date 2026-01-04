package com.itops.service;

import com.itops.domain.Invoice;
import com.itops.domain.InvoiceItem;
import com.itops.dto.*;
import com.itops.repository.*;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.itops.dto.NotificationType;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final ClientRepository clientRepository;
    private final ProjectRepository projectRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    private static final BigDecimal DEFAULT_TAX_RATE = new BigDecimal("18.00");
    private static final BigDecimal HUNDRED = new BigDecimal("100.00");

    @Transactional(readOnly = true)
    public java.util.List<InvoiceResponse> getAllInvoices(UUID companyId, UUID clientId, UUID projectId,
                                                  String status, LocalDate fromIssueDate, LocalDate toIssueDate,
                                                  Boolean overdueOnly, String sortBy) {
        java.util.List<Invoice> invoices;

        if (clientId != null || projectId != null || status != null || fromIssueDate != null || toIssueDate != null) {
            invoices = invoiceRepository.findFilteredInvoices(companyId, clientId, projectId, status, fromIssueDate, toIssueDate);
        } else {
            invoices = invoiceRepository.findAllByCompanyIdAndDeletedAtIsNull(companyId);
        }

        if (Boolean.TRUE.equals(overdueOnly)) {
            LocalDate today = LocalDate.now();
            invoices = invoices.stream()
                    .filter(invoice -> invoice.getDueDate() != null &&
                            invoice.getDueDate().isBefore(today) &&
                            "SENT".equals(invoice.getStatus()))
                    .collect(Collectors.toList());
        }

        invoices = sortInvoices(invoices, sortBy);

        return invoices.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public InvoiceResponse getInvoice(UUID id, UUID companyId) {
        Invoice invoice = invoiceRepository.findByIdAndCompanyIdAndDeletedAtIsNull(id, companyId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));
        return toResponse(invoice);
    }

    @Transactional
    public InvoiceResponse createInvoice(CreateInvoiceRequest request, UUID companyId, UUID userId) {
        if (!clientRepository.findById(request.getClientId()).filter(c -> c.getCompanyId().equals(companyId) && c.getDeletedAt() == null).isPresent()) {
            throw new RuntimeException("Client not found");
        }

        if (request.getProjectId() != null && !projectRepository.findById(request.getProjectId()).filter(p -> p.getCompanyId().equals(companyId) && p.getDeletedAt() == null).isPresent()) {
            throw new RuntimeException("Project not found");
        }

        String invoiceNumber = generateInvoiceNumber(companyId);

        BigDecimal taxRate = request.getTaxRate() != null ? request.getTaxRate() : DEFAULT_TAX_RATE;
        CalculatedTotals totals = calculateTotals(request.getItems(), taxRate);

        Invoice invoice = new Invoice();
        invoice.setCompanyId(companyId);
        invoice.setClientId(request.getClientId());
        invoice.setProjectId(request.getProjectId());
        invoice.setInvoiceNumber(invoiceNumber);
        invoice.setIssueDate(request.getIssueDate());
        invoice.setDueDate(request.getDueDate());
        invoice.setStatus("DRAFT");
        invoice.setSubtotal(totals.getSubtotal());
        invoice.setTaxRate(taxRate);
        invoice.setTaxAmount(totals.getTaxAmount());
        invoice.setTotal(totals.getTotal());
        invoice.setNotes(request.getNotes());
        invoice.setCreatedBy(userId);
        invoice.setBillingPeriodStart(request.getBillingPeriodStart());
        invoice.setBillingPeriodEnd(request.getBillingPeriodEnd());
        invoice.setCurrencyCode(request.getCurrencyCode() != null ? request.getCurrencyCode() : "USD");
        invoice.setPaymentTerms(request.getPaymentTerms());

        invoice = invoiceRepository.save(invoice);

        java.util.List<InvoiceItem> items = createInvoiceItems(request.getItems(), invoice.getId(), companyId);
        invoiceItemRepository.saveAll(items);

        // Notify admins/managers about new invoice
        notifyAdminsAboutInvoice(invoice, companyId, userId);

        log.info("Created invoice {} for company {}", invoiceNumber, companyId);
        return toResponse(invoice);
    }

    @Transactional
    public InvoiceResponse updateInvoice(UUID id, UpdateInvoiceRequest request, UUID companyId) {
        Invoice invoice = invoiceRepository.findByIdAndCompanyIdAndDeletedAtIsNull(id, companyId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        if ("PAID".equals(invoice.getStatus()) || "CANCELLED".equals(invoice.getStatus())) {
            throw new RuntimeException("Cannot update invoice with status " + invoice.getStatus());
        }

        if (request.getClientId() != null && !clientRepository.findById(request.getClientId()).filter(c -> c.getCompanyId().equals(companyId) && c.getDeletedAt() == null).isPresent()) {
            throw new RuntimeException("Client not found");
        }

        if (request.getProjectId() != null && !projectRepository.findById(request.getProjectId()).filter(p -> p.getCompanyId().equals(companyId) && p.getDeletedAt() == null).isPresent()) {
            throw new RuntimeException("Project not found");
        }

        if (request.getClientId() != null) invoice.setClientId(request.getClientId());
        if (request.getProjectId() != null) invoice.setProjectId(request.getProjectId());
        if (request.getIssueDate() != null) invoice.setIssueDate(request.getIssueDate());
        if (request.getDueDate() != null) invoice.setDueDate(request.getDueDate());
        if (request.getNotes() != null) invoice.setNotes(request.getNotes());
        if (request.getBillingPeriodStart() != null) invoice.setBillingPeriodStart(request.getBillingPeriodStart());
        if (request.getBillingPeriodEnd() != null) invoice.setBillingPeriodEnd(request.getBillingPeriodEnd());
        if (request.getCurrencyCode() != null) invoice.setCurrencyCode(request.getCurrencyCode());
        if (request.getPaymentTerms() != null) invoice.setPaymentTerms(request.getPaymentTerms());

        if (request.getItems() != null && !request.getItems().isEmpty()) {
            java.util.List<InvoiceItem> oldItems = invoiceItemRepository.findByInvoiceId(invoice.getId()).stream().filter(item -> item.getDeletedAt() == null).collect(Collectors.toList());
            oldItems.forEach(item -> {
                item.setDeletedAt(LocalDateTime.now());
                invoiceItemRepository.save(item);
            });

            BigDecimal taxRate = request.getTaxRate() != null ? request.getTaxRate() : invoice.getTaxRate();
            CalculatedTotals totals = calculateTotals(request.getItems(), taxRate);

            invoice.setSubtotal(totals.getSubtotal());
            invoice.setTaxRate(taxRate);
            invoice.setTaxAmount(totals.getTaxAmount());
            invoice.setTotal(totals.getTotal());

            java.util.List<InvoiceItem> newItems = createInvoiceItems(request.getItems(), invoice.getId(), companyId);
            invoiceItemRepository.saveAll(newItems);
        }

        invoice = invoiceRepository.save(invoice);
        log.info("Updated invoice {} for company {}", invoice.getInvoiceNumber(), companyId);
        return toResponse(invoice);
    }

    @Transactional
    public InvoiceResponse updateInvoiceStatus(UUID id, String newStatus, UUID companyId, UUID actorId) {
        Invoice invoice = invoiceRepository.findByIdAndCompanyIdAndDeletedAtIsNull(id, companyId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        validateStatusTransition(invoice.getStatus(), newStatus);
        String oldStatus = invoice.getStatus();

        invoice.setStatus(newStatus);
        
        // Update timestamps based on status
        LocalDateTime now = LocalDateTime.now();
        switch (newStatus) {
            case "SENT":
                if (invoice.getSentAt() == null) {
                    invoice.setSentAt(now);
                }
                break;
            case "PAID":
                if (invoice.getPaidAt() == null) {
                    invoice.setPaidAt(now);
                }
                break;
            case "CANCELLED":
                if (invoice.getCancelledAt() == null) {
                    invoice.setCancelledAt(now);
                }
                break;
        }
        
        invoice = invoiceRepository.save(invoice);

        // Send notifications based on status change
        notifyInvoiceStatusChange(invoice, oldStatus, newStatus, companyId, actorId);

        log.info("Updated invoice {} status to {} for company {}", invoice.getInvoiceNumber(), newStatus, companyId);
        return toResponse(invoice);
    }

    @Transactional
    public void deleteInvoice(UUID id, UUID companyId) {
        Invoice invoice = invoiceRepository.findByIdAndCompanyIdAndDeletedAtIsNull(id, companyId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        if ("PAID".equals(invoice.getStatus())) {
            throw new RuntimeException("Cannot delete paid invoices");
        }

        invoice.setDeletedAt(LocalDateTime.now());
        invoiceRepository.save(invoice);

        java.util.List<InvoiceItem> items = invoiceItemRepository.findByInvoiceId(invoice.getId()).stream().filter(item -> item.getDeletedAt() == null).collect(Collectors.toList());
        items.forEach(item -> {
            item.setDeletedAt(LocalDateTime.now());
            invoiceItemRepository.save(item);
        });

        log.info("Soft deleted invoice {} for company {}", invoice.getInvoiceNumber(), companyId);
    }

    public String generateInvoiceNumber(UUID companyId) {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        Random random = new Random();
        int maxAttempts = 10;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int randomNum = 100000 + random.nextInt(900000);
            String invoiceNumber = "INV-" + today + "-" + randomNum;

            if (!invoiceRepository.existsByInvoiceNumberAndCompanyId(invoiceNumber, companyId)) {
                return invoiceNumber;
            }
        }

        throw new RuntimeException("Failed to generate unique invoice number after " + maxAttempts + " attempts");
    }

    private CalculatedTotals calculateTotals(java.util.List<InvoiceItemRequest> items, BigDecimal taxRate) {
        BigDecimal subtotal = items.stream()
                .map(item -> {
                    BigDecimal quantity = new BigDecimal(item.getQuantity());
                    return item.getUnitPrice().multiply(quantity).setScale(2, RoundingMode.HALF_UP);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal taxAmount = subtotal.multiply(taxRate)
                .divide(HUNDRED, 2, RoundingMode.HALF_UP);

        BigDecimal total = subtotal.add(taxAmount).setScale(2, RoundingMode.HALF_UP);

        return new CalculatedTotals(subtotal, taxAmount, total);
    }

    private void validateStatusTransition(String currentStatus, String newStatus) {
        if (currentStatus.equals(newStatus)) {
            return;
        }

        switch (currentStatus) {
            case "DRAFT":
                if (!newStatus.equals("SENT") && !newStatus.equals("CANCELLED")) {
                    throw new RuntimeException("DRAFT invoices can only transition to SENT or CANCELLED");
                }
                break;
            case "SENT":
                if (!newStatus.equals("PAID") && !newStatus.equals("OVERDUE") && !newStatus.equals("CANCELLED")) {
                    throw new RuntimeException("SENT invoices can only transition to PAID, OVERDUE, or CANCELLED");
                }
                break;
            case "OVERDUE":
                if (!newStatus.equals("PAID") && !newStatus.equals("CANCELLED")) {
                    throw new RuntimeException("OVERDUE invoices can only transition to PAID or CANCELLED");
                }
                break;
            case "PAID":
            case "CANCELLED":
                throw new RuntimeException("Cannot change status of " + currentStatus + " invoices");
            default:
                throw new RuntimeException("Invalid current status: " + currentStatus);
        }
    }

    private java.util.List<InvoiceItem> createInvoiceItems(java.util.List<InvoiceItemRequest> itemRequests, UUID invoiceId, UUID companyId) {
        return itemRequests.stream()
                .map(req -> {
                    BigDecimal quantity = new BigDecimal(req.getQuantity());
                    BigDecimal amount = req.getUnitPrice().multiply(quantity).setScale(2, RoundingMode.HALF_UP);

                    InvoiceItem item = new InvoiceItem();
                    item.setCompanyId(companyId);
                    item.setInvoiceId(invoiceId);
                    item.setDescription(req.getDescription());
                    item.setQuantity(req.getQuantity());
                    item.setUnitPrice(req.getUnitPrice());
                    item.setAmount(amount);

                    return item;
                })
                .collect(Collectors.toList());
    }

    private java.util.List<Invoice> sortInvoices(java.util.List<Invoice> invoices, String sortBy) {
        if (sortBy == null || sortBy.isEmpty()) {
            sortBy = "issueDate_desc";
        }

        Comparator<Invoice> comparator;
        switch (sortBy.toLowerCase()) {
            case "issuedate_asc":
                comparator = Comparator.comparing(Invoice::getIssueDate, Comparator.nullsLast(Comparator.naturalOrder()));
                break;
            case "issuedate_desc":
                comparator = Comparator.comparing(Invoice::getIssueDate, Comparator.nullsLast(Comparator.reverseOrder()));
                break;
            case "duedate_asc":
                comparator = Comparator.comparing(Invoice::getDueDate, Comparator.nullsLast(Comparator.naturalOrder()));
                break;
            case "duedate_desc":
                comparator = Comparator.comparing(Invoice::getDueDate, Comparator.nullsLast(Comparator.reverseOrder()));
                break;
            case "total_asc":
                comparator = Comparator.comparing(Invoice::getTotal, Comparator.nullsLast(Comparator.naturalOrder()));
                break;
            case "total_desc":
                comparator = Comparator.comparing(Invoice::getTotal, Comparator.nullsLast(Comparator.reverseOrder()));
                break;
            default:
                comparator = Comparator.comparing(Invoice::getIssueDate, Comparator.nullsLast(Comparator.reverseOrder()));
        }

        return invoices.stream().sorted(comparator).collect(Collectors.toList());
    }

    private InvoiceResponse toResponse(Invoice invoice) {
        java.util.List<InvoiceItem> items = invoiceItemRepository.findByInvoiceId(invoice.getId()).stream().filter(item -> item.getDeletedAt() == null).collect(Collectors.toList());

        java.util.List<InvoiceItemResponse> itemResponses = items.stream()
                .map(this::toItemResponse)
                .collect(Collectors.toList());

        boolean isOverdue = invoice.getDueDate() != null &&
                invoice.getDueDate().isBefore(LocalDate.now()) &&
                "SENT".equals(invoice.getStatus());
        
        // Build enriched client info
        InvoiceClientInfo clientInfo = clientRepository.findById(invoice.getClientId())
                .map(client -> InvoiceClientInfo.builder()
                        .id(client.getId())
                        .name(client.getName())
                        .contactName(client.getContactName())
                        .email(client.getEmail())
                        .phone(client.getPhone())
                        .address(client.getAddress())
                        .build())
                .orElse(null);
        
        // Build enriched project info
        InvoiceProjectInfo projectInfo = null;
        if (invoice.getProjectId() != null) {
            projectInfo = projectRepository.findById(invoice.getProjectId())
                    .map(project -> InvoiceProjectInfo.builder()
                            .id(project.getId())
                            .name(project.getName())
                            .status(project.getStatus())
                            .build())
                    .orElse(null);
        }
        
        // Build enriched creator info
        InvoiceUserInfo createdByInfo = null;
        if (invoice.getCreatedBy() != null) {
            createdByInfo = userRepository.findById(invoice.getCreatedBy())
                    .map(user -> InvoiceUserInfo.builder()
                            .id(user.getId())
                            .name(user.getName())
                            .email(user.getEmail())
                            .build())
                    .orElse(null);
        }
        
        // Compute summary
        InvoiceSummary summary = computeSummary(items);

        return InvoiceResponse.builder()
                .id(invoice.getId())
                .companyId(invoice.getCompanyId())
                .clientId(invoice.getClientId())
                .projectId(invoice.getProjectId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .issueDate(invoice.getIssueDate())
                .dueDate(invoice.getDueDate())
                .status(invoice.getStatus())
                .subtotal(invoice.getSubtotal())
                .taxRate(invoice.getTaxRate())
                .taxAmount(invoice.getTaxAmount())
                .total(invoice.getTotal())
                .notes(invoice.getNotes())
                .createdAt(invoice.getCreatedAt())
                .updatedAt(invoice.getUpdatedAt())
                .isOverdue(isOverdue)
                .items(itemResponses)
                .client(clientInfo)
                .project(projectInfo)
                .createdBy(createdByInfo)
                .billingPeriodStart(invoice.getBillingPeriodStart())
                .billingPeriodEnd(invoice.getBillingPeriodEnd())
                .currencyCode(invoice.getCurrencyCode())
                .paymentTerms(invoice.getPaymentTerms())
                .sentAt(invoice.getSentAt())
                .paidAt(invoice.getPaidAt())
                .cancelledAt(invoice.getCancelledAt())
                .summary(summary)
                .build();
    }
    
    private InvoiceItemResponse toItemResponse(InvoiceItem item) {
        // Build user info if userId exists
        InvoiceUserInfo userInfo = null;
        if (item.getUserId() != null) {
            userInfo = userRepository.findById(item.getUserId())
                    .map(user -> InvoiceUserInfo.builder()
                            .id(user.getId())
                            .name(user.getName())
                            .email(user.getEmail())
                            .build())
                    .orElse(null);
        }
        
        // Build task info if taskId exists (skipped for now, can add TaskRepository injection later)
        InvoiceTaskInfo taskInfo = null;
        
        // Parse source time entry IDs
        java.util.List<UUID> sourceIds = new ArrayList<>();
        if (item.getSourceTimeEntryIds() != null && !item.getSourceTimeEntryIds().isEmpty()) {
            String[] ids = item.getSourceTimeEntryIds().split(",");
            for (String id : ids) {
                try {
                    sourceIds.add(UUID.fromString(id.trim()));
                } catch (IllegalArgumentException e) {
                    // Skip invalid UUIDs
                }
            }
        }
        
        return InvoiceItemResponse.builder()
                .id(item.getId())
                .description(item.getDescription())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .amount(item.getAmount())
                .itemType(item.getItemType())
                .user(userInfo)
                .task(taskInfo)
                .rate(item.getRate())
                .minutes(item.getMinutes())
                .sourceTimeEntryIds(sourceIds)
                .build();
    }
    
    private InvoiceSummary computeSummary(java.util.List<InvoiceItem> items) {
        int totalMinutes = items.stream()
                .filter(item -> "TIME".equals(item.getItemType()) && item.getMinutes() != null)
                .mapToInt(InvoiceItem::getMinutes)
                .sum();
        
        Set<UUID> uniqueUsers = items.stream()
                .filter(item -> item.getUserId() != null)
                .map(InvoiceItem::getUserId)
                .collect(Collectors.toSet());
        
        Set<UUID> uniqueTasks = items.stream()
                .filter(item -> item.getTaskId() != null)
                .map(InvoiceItem::getTaskId)
                .collect(Collectors.toSet());
        
        int entryCount = items.stream()
                .filter(item -> item.getSourceTimeEntryIds() != null && !item.getSourceTimeEntryIds().isEmpty())
                .mapToInt(item -> item.getSourceTimeEntryIds().split(",").length)
                .sum();
        
        return InvoiceSummary.builder()
                .totalMinutes(totalMinutes > 0 ? totalMinutes : null)
                .totalBillableMinutes(totalMinutes > 0 ? totalMinutes : null) // Assuming all are billable
                .entryCount(entryCount > 0 ? entryCount : null)
                .contributorsCount(uniqueUsers.size() > 0 ? uniqueUsers.size() : null)
                .tasksCount(uniqueTasks.size() > 0 ? uniqueTasks.size() : null)
                .build();
    }

    private static class CalculatedTotals {
        private final BigDecimal subtotal;
        private final BigDecimal taxAmount;
        private final BigDecimal total;

        public CalculatedTotals(BigDecimal subtotal, BigDecimal taxAmount, BigDecimal total) {
            this.subtotal = subtotal;
            this.taxAmount = taxAmount;
            this.total = total;
        }

        public BigDecimal getSubtotal() { return subtotal; }
        public BigDecimal getTaxAmount() { return taxAmount; }
        public BigDecimal getTotal() { return total; }
    }

    private void notifyAdminsAboutInvoice(Invoice invoice, UUID companyId, UUID creatorId) {
        java.util.List<UUID> adminIds = userRepository.findByCompanyIdAndDeletedAtIsNull(companyId).stream()
                .filter(user -> "ADMIN".equals(user.getRole()) || "SUPER_USER".equals(user.getRole()) || "TOP_USER".equals(user.getRole()))
                .map(user -> user.getId())
                .collect(Collectors.toList());

        notificationService.createNotifications(
                adminIds,
                companyId,
                NotificationType.INVOICE_CREATED,
                "New Invoice Created",
                "Invoice " + invoice.getInvoiceNumber() + " has been created",
                "INVOICE",
                invoice.getId(),
                creatorId
        );
    }

    private void notifyInvoiceStatusChange(Invoice invoice, String oldStatus, String newStatus, UUID companyId, UUID actorId) {
        if (invoice.getCreatedBy() != null) {
            String notificationType;
            String title;
            String message;

            switch (newStatus) {
                case "APPROVED":
                    notificationType = NotificationType.INVOICE_APPROVED;
                    title = "Invoice Approved";
                    message = "Invoice " + invoice.getInvoiceNumber() + " has been approved";
                    break;
                case "REJECTED":
                    notificationType = NotificationType.INVOICE_REJECTED;
                    title = "Invoice Rejected";
                    message = "Invoice " + invoice.getInvoiceNumber() + " has been rejected";
                    break;
                case "PAID":
                    notificationType = NotificationType.INVOICE_PAID;
                    title = "Invoice Paid";
                    message = "Invoice " + invoice.getInvoiceNumber() + " has been marked as paid";
                    break;
                default:
                    return; // Don't send notification for other status changes
            }

            notificationService.createNotification(
                    invoice.getCreatedBy(),
                    companyId,
                    notificationType,
                    title,
                    message,
                    "INVOICE",
                    invoice.getId(),
                    actorId
            );
        }
    }
    
    public byte[] generateInvoicePDF(UUID invoiceId, UUID companyId) {
        Invoice invoice = invoiceRepository.findByIdAndCompanyIdAndDeletedAtIsNull(invoiceId, companyId)
            .orElseThrow(() -> new RuntimeException("Invoice not found"));
        
        java.util.List<InvoiceItem> items = invoiceItemRepository.findByInvoiceId(invoiceId);
        
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 50, 50, 50, 50);
            PdfWriter.getInstance(document, baos);
            document.open();
            
            // Fonts
            Font titleFont = new Font(Font.HELVETICA, 22, Font.BOLD);
            Font sectionFont = new Font(Font.HELVETICA, 14, Font.BOLD);
            Font labelFont = new Font(Font.HELVETICA, 10, Font.BOLD);
            Font normalFont = new Font(Font.HELVETICA, 10, Font.NORMAL);
            Font smallFont = new Font(Font.HELVETICA, 9, Font.NORMAL);
            Font largeFont = new Font(Font.HELVETICA, 18, Font.BOLD);
            
            // === INVOICE HEADER ===
            Paragraph title = new Paragraph("INVOICE #" + invoice.getInvoiceNumber(), titleFont);
            title.setAlignment(Element.ALIGN_LEFT);
            document.add(title);
            
            // Status badge
            Paragraph status = new Paragraph("Status: " + invoice.getStatus(), normalFont);
            document.add(status);
            
            // Created date
            if (invoice.getCreatedAt() != null) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy");
                Paragraph created = new Paragraph("Created: " + invoice.getCreatedAt().format(formatter), smallFont);
                document.add(created);
            }
            
            document.add(new Paragraph(" "));
            document.add(new Paragraph("_________________________________________________________________"));
            document.add(new Paragraph(" "));
            
            // === CLIENT & PROJECT SECTION ===
            PdfPTable infoTable = new PdfPTable(2);
            infoTable.setWidthPercentage(100);
            infoTable.setWidths(new float[]{1, 1});
            
            // Client Information
            PdfPCell clientCell = new PdfPCell();
            clientCell.setBorder(Rectangle.BOX);
            clientCell.setPadding(10);
            clientCell.addElement(new Paragraph("CLIENT", labelFont));
            
            if (invoice.getClientId() != null) {
                var client = clientRepository.findById(invoice.getClientId());
                if (client.isPresent()) {
                    clientCell.addElement(new Paragraph(client.get().getName(), sectionFont));
                    if (client.get().getContactName() != null) {
                        clientCell.addElement(new Paragraph("Contact: " + client.get().getContactName(), normalFont));
                    }
                    if (client.get().getEmail() != null) {
                        clientCell.addElement(new Paragraph("Email: " + client.get().getEmail(), normalFont));
                    }
                    if (client.get().getPhone() != null) {
                        clientCell.addElement(new Paragraph("Phone: " + client.get().getPhone(), normalFont));
                    }
                    if (client.get().getAddress() != null) {
                        clientCell.addElement(new Paragraph("Address: " + client.get().getAddress(), smallFont));
                    }
                }
            }
            infoTable.addCell(clientCell);
            
            // Project Information
            PdfPCell projectCell = new PdfPCell();
            projectCell.setBorder(Rectangle.BOX);
            projectCell.setPadding(10);
            projectCell.addElement(new Paragraph("PROJECT", labelFont));
            
            if (invoice.getProjectId() != null) {
                var project = projectRepository.findById(invoice.getProjectId());
                if (project.isPresent()) {
                    projectCell.addElement(new Paragraph(project.get().getName(), sectionFont));
                    if (project.get().getDescription() != null) {
                        projectCell.addElement(new Paragraph(project.get().getDescription(), smallFont));
                    }
                }
            } else {
                projectCell.addElement(new Paragraph("No project linked", normalFont));
            }
            infoTable.addCell(projectCell);
            
            document.add(infoTable);
            document.add(new Paragraph(" "));
            
            // === DATES & TERMS SECTION ===
            PdfPTable datesTable = new PdfPTable(4);
            datesTable.setWidthPercentage(100);
            datesTable.setWidths(new float[]{1, 1, 1, 1});
            
            // Issue Date
            PdfPCell issueCell = new PdfPCell();
            issueCell.setBorder(Rectangle.BOX);
            issueCell.setPadding(8);
            issueCell.addElement(new Paragraph("Issue Date", labelFont));
            issueCell.addElement(new Paragraph(invoice.getIssueDate().toString(), normalFont));
            datesTable.addCell(issueCell);
            
            // Due Date
            PdfPCell dueCell = new PdfPCell();
            dueCell.setBorder(Rectangle.BOX);
            dueCell.setPadding(8);
            dueCell.addElement(new Paragraph("Due Date", labelFont));
            dueCell.addElement(new Paragraph(invoice.getDueDate() != null ? invoice.getDueDate().toString() : "N/A", normalFont));
            datesTable.addCell(dueCell);
            
            // Payment Terms
            PdfPCell termsCell = new PdfPCell();
            termsCell.setBorder(Rectangle.BOX);
            termsCell.setPadding(8);
            termsCell.addElement(new Paragraph("Payment Terms", labelFont));
            termsCell.addElement(new Paragraph(invoice.getPaymentTerms() != null ? invoice.getPaymentTerms() : "N/A", normalFont));
            datesTable.addCell(termsCell);
            
            // Currency & Tax
            PdfPCell currencyCell = new PdfPCell();
            currencyCell.setBorder(Rectangle.BOX);
            currencyCell.setPadding(8);
            currencyCell.addElement(new Paragraph("Currency & Tax", labelFont));
            currencyCell.addElement(new Paragraph(invoice.getCurrencyCode() + " • " + invoice.getTaxRate() + "%", normalFont));
            datesTable.addCell(currencyCell);
            
            document.add(datesTable);
            document.add(new Paragraph(" "));
            
            // === BILLING CONTEXT (if available) ===
            if (invoice.getBillingPeriodStart() != null && invoice.getBillingPeriodEnd() != null) {
                PdfPTable billingTable = new PdfPTable(1);
                billingTable.setWidthPercentage(100);
                
                PdfPCell billingCell = new PdfPCell();
                billingCell.setBorder(Rectangle.BOX);
                billingCell.setPadding(10);
                billingCell.setBackgroundColor(new Color(249, 250, 251));
                
                billingCell.addElement(new Paragraph("BILLING CONTEXT", labelFont));
                billingCell.addElement(new Paragraph("Period: " + invoice.getBillingPeriodStart() + " to " + invoice.getBillingPeriodEnd(), normalFont));
                
                billingTable.addCell(billingCell);
                document.add(billingTable);
                document.add(new Paragraph(" "));
            }
            
            // === LINE ITEMS TABLE ===
            document.add(new Paragraph("LINE ITEMS", sectionFont));
            document.add(new Paragraph(" "));
            
            PdfPTable itemsTable = new PdfPTable(4);
            itemsTable.setWidthPercentage(100);
            itemsTable.setWidths(new float[]{3, 1, 1.5f, 1.5f});
            
            // Table Header
            PdfPCell headerCell;
            String[] headers = {"Description", "Qty", "Rate", "Amount"};
            for (String header : headers) {
                headerCell = new PdfPCell(new Phrase(header, labelFont));
                headerCell.setBackgroundColor(new Color(240, 240, 240));
                headerCell.setPadding(8);
                headerCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                itemsTable.addCell(headerCell);
            }
            
            // Table Rows
            for (InvoiceItem item : items) {
                PdfPCell descCell = new PdfPCell(new Phrase(item.getDescription(), normalFont));
                descCell.setPadding(6);
                itemsTable.addCell(descCell);
                
                String qty = item.getMinutes() != null 
                    ? formatMinutes(item.getMinutes()) 
                    : String.valueOf(item.getQuantity());
                PdfPCell qtyCell = new PdfPCell(new Phrase(qty, normalFont));
                qtyCell.setPadding(6);
                qtyCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                itemsTable.addCell(qtyCell);
                
                BigDecimal rate = item.getRate() != null ? item.getRate() : item.getUnitPrice();
                PdfPCell rateCell = new PdfPCell(new Phrase("$" + rate.toString(), normalFont));
                rateCell.setPadding(6);
                rateCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                itemsTable.addCell(rateCell);
                
                PdfPCell amountCell = new PdfPCell(new Phrase("$" + item.getAmount().toString(), normalFont));
                amountCell.setPadding(6);
                amountCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                itemsTable.addCell(amountCell);
            }
            
            // Footer - Subtotal
            PdfPCell emptyCell1 = new PdfPCell(new Phrase("", normalFont));
            emptyCell1.setBorder(Rectangle.NO_BORDER);
            itemsTable.addCell(emptyCell1);
            itemsTable.addCell(emptyCell1);
            
            PdfPCell subtotalLabel = new PdfPCell(new Phrase("Subtotal", labelFont));
            subtotalLabel.setPadding(6);
            subtotalLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
            subtotalLabel.setBorder(Rectangle.TOP);
            itemsTable.addCell(subtotalLabel);
            
            PdfPCell subtotalValue = new PdfPCell(new Phrase("$" + invoice.getSubtotal().toString(), normalFont));
            subtotalValue.setPadding(6);
            subtotalValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
            subtotalValue.setBorder(Rectangle.TOP);
            itemsTable.addCell(subtotalValue);
            
            // Footer - Tax
            PdfPCell emptyCell2 = new PdfPCell(new Phrase("", normalFont));
            emptyCell2.setBorder(Rectangle.NO_BORDER);
            itemsTable.addCell(emptyCell2);
            itemsTable.addCell(emptyCell2);
            
            PdfPCell taxLabel = new PdfPCell(new Phrase("Tax (" + invoice.getTaxRate() + "%)", labelFont));
            taxLabel.setPadding(6);
            taxLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
            taxLabel.setBorder(Rectangle.NO_BORDER);
            itemsTable.addCell(taxLabel);
            
            PdfPCell taxValue = new PdfPCell(new Phrase("$" + invoice.getTaxAmount().toString(), normalFont));
            taxValue.setPadding(6);
            taxValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
            taxValue.setBorder(Rectangle.NO_BORDER);
            itemsTable.addCell(taxValue);
            
            // Footer - Total
            PdfPCell emptyCell3 = new PdfPCell(new Phrase("", normalFont));
            emptyCell3.setBorder(Rectangle.NO_BORDER);
            itemsTable.addCell(emptyCell3);
            itemsTable.addCell(emptyCell3);
            
            PdfPCell totalLabel = new PdfPCell(new Phrase("Total", largeFont));
            totalLabel.setPadding(8);
            totalLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalLabel.setBorder(Rectangle.TOP);
            totalLabel.setBackgroundColor(new Color(249, 250, 251));
            itemsTable.addCell(totalLabel);
            
            PdfPCell totalValue = new PdfPCell(new Phrase("$" + invoice.getTotal().toString(), largeFont));
            totalValue.setPadding(8);
            totalValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalValue.setBorder(Rectangle.TOP);
            totalValue.setBackgroundColor(new Color(249, 250, 251));
            itemsTable.addCell(totalValue);
            
            document.add(itemsTable);
            document.add(new Paragraph(" "));
            
            // === NOTES ===
            if (invoice.getNotes() != null && !invoice.getNotes().isEmpty()) {
                document.add(new Paragraph(" "));
                document.add(new Paragraph("NOTES", sectionFont));
                document.add(new Paragraph(" "));
                
                PdfPTable notesTable = new PdfPTable(1);
                notesTable.setWidthPercentage(100);
                
                PdfPCell notesCell = new PdfPCell();
                notesCell.setBorder(Rectangle.BOX);
                notesCell.setPadding(10);
                notesCell.setBackgroundColor(new Color(249, 250, 251));
                notesCell.addElement(new Paragraph(invoice.getNotes(), normalFont));
                
                notesTable.addCell(notesCell);
                document.add(notesTable);
            }
            
            document.close();
            return baos.toByteArray();
            
        } catch (Exception e) {
            log.error("Error generating PDF for invoice {}", invoiceId, e);
            throw new RuntimeException("Failed to generate PDF: " + e.getMessage());
        }
    }
    
    private String formatMinutes(int minutes) {
        int hours = minutes / 60;
        int mins = minutes % 60;
        if (mins == 0) {
            return hours + "h";
        }
        return hours + "h " + mins + "m";
    }
    
    private PdfPCell createTotalCell(String text, Font font, boolean alignRight) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(3);
        if (alignRight) {
            cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        }
        return cell;
    }
    
    private String buildSimplePDFContent(Invoice invoice) {
        // This method is no longer used but kept for reference
        StringBuilder sb = new StringBuilder();
        sb.append("%PDF-1.4\n");
        sb.append("1 0 obj\n");
        sb.append("<< /Type /Catalog /Pages 2 0 R >>\n");
        sb.append("endobj\n");
        sb.append("2 0 obj\n");
        sb.append("<< /Type /Pages /Kids [3 0 R] /Count 1 >>\n");
        sb.append("endobj\n");
        sb.append("3 0 obj\n");
        sb.append("<< /Type /Page /Parent 2 0 R /Resources 4 0 R /MediaBox [0 0 612 792] /Contents 5 0 R >>\n");
        sb.append("endobj\n");
        sb.append("4 0 obj\n");
        sb.append("<< /Font << /F1 << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> >> >>\n");
        sb.append("endobj\n");
        sb.append("5 0 obj\n");
        String content = String.format(
            "BT /F1 18 Tf 50 750 Td (INVOICE: %s) Tj ET " +
            "BT /F1 12 Tf 50 720 Td (Invoice Number: %s) Tj ET " +
            "BT /F1 12 Tf 50 700 Td (Issue Date: %s) Tj ET " +
            "BT /F1 12 Tf 50 680 Td (Due Date: %s) Tj ET " +
            "BT /F1 12 Tf 50 660 Td (Status: %s) Tj ET " +
            "BT /F1 14 Tf 50 630 Td (Total: $%.2f) Tj ET",
            invoice.getInvoiceNumber(),
            invoice.getInvoiceNumber(),
            invoice.getIssueDate(),
            invoice.getDueDate() != null ? invoice.getDueDate() : "N/A",
            invoice.getStatus(),
            invoice.getTotal()
        );
        sb.append("<< /Length ").append(content.length()).append(" >>\n");
        sb.append("stream\n");
        sb.append(content);
        sb.append("\nendstream\n");
        sb.append("endobj\n");
        sb.append("xref\n");
        sb.append("0 6\n");
        sb.append("0000000000 65535 f\n");
        sb.append("0000000009 00000 n\n");
        sb.append("0000000058 00000 n\n");
        sb.append("0000000115 00000 n\n");
        sb.append("0000000214 00000 n\n");
        sb.append("0000000308 00000 n\n");
        sb.append("trailer\n");
        sb.append("<< /Size 6 /Root 1 0 R >>\n");
        sb.append("startxref\n");
        sb.append("500\n");
        sb.append("%%EOF\n");
        return sb.toString();
    }
}