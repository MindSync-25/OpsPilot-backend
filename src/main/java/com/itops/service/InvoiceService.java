package com.itops.service;

import com.itops.domain.Invoice;
import com.itops.domain.InvoiceItem;
import com.itops.dto.*;
import com.itops.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.itops.dto.NotificationType;

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
    public List<InvoiceResponse> getAllInvoices(UUID companyId, UUID clientId, UUID projectId,
                                                  String status, LocalDate fromIssueDate, LocalDate toIssueDate,
                                                  Boolean overdueOnly, String sortBy) {
        List<Invoice> invoices;

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

        invoice = invoiceRepository.save(invoice);

        List<InvoiceItem> items = createInvoiceItems(request.getItems(), invoice.getId(), companyId);
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

        if (request.getItems() != null && !request.getItems().isEmpty()) {
            List<InvoiceItem> oldItems = invoiceItemRepository.findByInvoiceId(invoice.getId()).stream().filter(item -> item.getDeletedAt() == null).collect(Collectors.toList());
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

            List<InvoiceItem> newItems = createInvoiceItems(request.getItems(), invoice.getId(), companyId);
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

        List<InvoiceItem> items = invoiceItemRepository.findByInvoiceId(invoice.getId()).stream().filter(item -> item.getDeletedAt() == null).collect(Collectors.toList());
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

    private CalculatedTotals calculateTotals(List<InvoiceItemRequest> items, BigDecimal taxRate) {
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

    private List<InvoiceItem> createInvoiceItems(List<InvoiceItemRequest> itemRequests, UUID invoiceId, UUID companyId) {
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

    private List<Invoice> sortInvoices(List<Invoice> invoices, String sortBy) {
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
        List<InvoiceItem> items = invoiceItemRepository.findByInvoiceId(invoice.getId()).stream().filter(item -> item.getDeletedAt() == null).collect(Collectors.toList());

        List<InvoiceItemResponse> itemResponses = items.stream()
                .map(item -> InvoiceItemResponse.builder()
                        .id(item.getId())
                        .description(item.getDescription())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .amount(item.getAmount())
                        .build())
                .collect(Collectors.toList());

        boolean isOverdue = invoice.getDueDate() != null &&
                invoice.getDueDate().isBefore(LocalDate.now()) &&
                "SENT".equals(invoice.getStatus());

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
        List<UUID> adminIds = userRepository.findByCompanyIdAndDeletedAtIsNull(companyId).stream()
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
}