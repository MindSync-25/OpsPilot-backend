package com.itops.service;

import com.itops.domain.*;
import com.itops.dto.*;
import com.itops.exception.ResourceNotFoundException;
import com.itops.exception.BusinessException;
import com.itops.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceGenerationService {

    private final TimeEntryRepository timeEntryRepository;
    private final ProjectRepository projectRepository;
    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final InvoiceService invoiceService;
    
    private static final BigDecimal DEFAULT_TAX_RATE = new BigDecimal("18.00");
    private static final int MINUTES_PER_HOUR = 60;
    private static final int DEFAULT_DUE_DAYS = 15;

    /**
     * Preview invoice generation from unbilled time entries
     */
    public InvoiceGenerationPreviewResponse previewFromTime(
            UUID companyId,
            UUID requesterUserId,
            InvoiceGenerationPreviewRequest request) {
        
        log.info("Generating invoice preview for client: {}, from: {}, to: {}", 
                 request.getClientId(), request.getFromDate(), request.getToDate());
        
        // Validate request
        validateRequest(request);
        
        // Verify client exists and belongs to company
        Client client = clientRepository.findByIdAndCompanyIdAndDeletedAtIsNull(request.getClientId(), companyId)
            .orElseThrow(() -> new ResourceNotFoundException("Client not found"));
        
        // Get projects for client
        List<UUID> projectIds = getProjectIdsForClient(companyId, request.getClientId(), request.getProjectId());
        
        if (projectIds.isEmpty()) {
            return buildEmptyPreview(request, client, "No projects found for this client");
        }
        
        // Get project name if specific project requested
        String projectName = null;
        if (request.getProjectId() != null) {
            Project project = projectRepository.findById(request.getProjectId())
                .orElse(null);
            if (project != null) {
                projectName = project.getName();
            }
        }
        
        // Fetch unbilled time entries
        List<TimeEntry> entries = timeEntryRepository.findUnbilledEntriesForProjects(
            companyId,
            projectIds,
            request.getFromDate(),
            request.getToDate(),
            request.getBillableOnly()
        );
        
        if (entries.isEmpty()) {
            return buildEmptyPreview(request, client, "No unbilled time entries found for the specified period");
        }
        
        log.info("Found {} unbilled entries", entries.size());
        
        // Get user information for all entries
        Set<UUID> userIds = entries.stream()
            .map(TimeEntry::getUserId)
            .collect(Collectors.toSet());
        
        Map<UUID, User> usersMap = userRepository.findAllById(userIds).stream()
            .collect(Collectors.toMap(User::getId, u -> u));
        
        // Check for missing hourly rates
        List<MissingRateUser> missingRates = checkMissingRates(entries, usersMap);
        
        // Build line items based on grouping strategy
        List<PreviewLineItem> lineItems;
        if (request.getGroupBy() == InvoiceGenerationPreviewRequest.GroupBy.USER) {
            lineItems = buildLineItemsByUser(entries, usersMap, request.getIncludeDescriptions());
        } else {
            lineItems = buildLineItemsByTask(entries, usersMap, request.getIncludeDescriptions());
        }
        
        // Calculate totals
        BigDecimal subtotal = lineItems.stream()
            .map(PreviewLineItem::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal taxRate = DEFAULT_TAX_RATE;
        BigDecimal taxAmount = subtotal.multiply(taxRate).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.add(taxAmount);
        
        int totalMinutes = entries.stream()
            .mapToInt(e -> e.getHours() * MINUTES_PER_HOUR)
            .sum();
        
        BigDecimal totalHours = new BigDecimal(totalMinutes)
            .divide(new BigDecimal(MINUTES_PER_HOUR), 2, RoundingMode.HALF_UP);
        
        boolean canGenerate = missingRates.isEmpty();
        String message = canGenerate ? 
            "Ready to generate invoice" : 
            "Cannot generate: " + missingRates.size() + " user(s) missing hourly rate";
        
        return InvoiceGenerationPreviewResponse.builder()
            .clientId(client.getId())
            .clientName(client.getName())
            .projectId(request.getProjectId())
            .projectName(projectName)
            .fromDate(request.getFromDate())
            .toDate(request.getToDate())
            .totalMinutes(totalMinutes)
            .totalHours(totalHours)
            .subtotal(subtotal)
            .taxRate(taxRate)
            .taxAmount(taxAmount)
            .total(total)
            .lineItems(lineItems)
            .missingRateUsers(missingRates)
            .entriesCount(entries.size())
            .canGenerate(canGenerate)
            .message(message)
            .build();
    }

    /**
     * Generate a DRAFT invoice from unbilled time entries
     */
    @Transactional
    public InvoiceGenerateResponse generateDraftInvoiceFromTime(
            UUID companyId,
            UUID requesterUserId,
            InvoiceGenerateRequest request) {
        
        log.info("Generating invoice for client: {}, from: {}, to: {}", 
                 request.getClientId(), request.getFromDate(), request.getToDate());
        
        // First get preview to validate
        InvoiceGenerationPreviewResponse preview = previewFromTime(companyId, requesterUserId, request);
        
        if (!preview.getCanGenerate()) {
            throw new BusinessException(preview.getMessage());
        }
        
        if (preview.getEntriesCount() == 0) {
            throw new BusinessException("No unbilled entries found");
        }
        
        if (!Boolean.TRUE.equals(request.getConfirmed())) {
            throw new BusinessException("Invoice generation must be confirmed");
        }
        
        // Get projects for client to fetch entry IDs
        List<UUID> projectIds = getProjectIdsForClient(companyId, request.getClientId(), request.getProjectId());
        
        List<TimeEntry> entries = timeEntryRepository.findUnbilledEntriesForProjects(
            companyId,
            projectIds,
            request.getFromDate(),
            request.getToDate(),
            request.getBillableOnly()
        );
        
        // Use provided tax rate or default
        BigDecimal taxRate = request.getTaxRate() != null ? request.getTaxRate() : preview.getTaxRate();
        
        // Recalculate with override tax rate if different
        BigDecimal subtotal = preview.getSubtotal();
        BigDecimal taxAmount = subtotal.multiply(taxRate).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.add(taxAmount);
        
        // Create invoice
        Invoice invoice = new Invoice();
        invoice.setCompanyId(companyId);
        invoice.setClientId(request.getClientId());
        invoice.setProjectId(request.getProjectId());
        invoice.setIssueDate(LocalDate.now());
        invoice.setDueDate(LocalDate.now().plusDays(DEFAULT_DUE_DAYS));
        invoice.setStatus("DRAFT");
        invoice.setSubtotal(subtotal);
        invoice.setTaxRate(taxRate);
        invoice.setTaxAmount(taxAmount);
        invoice.setTotal(total);
        invoice.setNotes(request.getNotes());
        invoice.setCreatedBy(requesterUserId);
        
        // Generate invoice number
        String invoiceNumber = invoiceService.generateInvoiceNumber(companyId);
        invoice.setInvoiceNumber(invoiceNumber);
        
        invoice = invoiceRepository.save(invoice);
        // Flush to ensure invoice is persisted before updating time entries
        invoiceRepository.flush();
        log.info("Created invoice: {}", invoice.getInvoiceNumber());
        
        // Create invoice items from preview
        for (PreviewLineItem lineItem : preview.getLineItems()) {
            InvoiceItem item = new InvoiceItem();
            item.setCompanyId(companyId);
            item.setInvoiceId(invoice.getId());
            item.setDescription(lineItem.getDescription());
            item.setQuantity(lineItem.getQuantityHours().intValue()); // Store as hours
            item.setUnitPrice(lineItem.getUnitPrice());
            item.setAmount(lineItem.getAmount());
            invoiceItemRepository.save(item);
        }
        
        // Link time entries to invoice
        List<UUID> entryIds = entries.stream()
            .map(TimeEntry::getId)
            .collect(Collectors.toList());
        
        LocalDateTime billedAt = LocalDateTime.now();
        int updatedCount = timeEntryRepository.updateInvoiceIdForEntries(entryIds, invoice.getId(), billedAt);
        
        if (updatedCount != entryIds.size()) {
            throw new BusinessException(
                "Billing conflict: Some entries were already billed. Expected " + 
                entryIds.size() + " but updated " + updatedCount
            );
        }
        
        log.info("Linked {} time entries to invoice {}", updatedCount, invoice.getInvoiceNumber());
        
        return InvoiceGenerateResponse.builder()
            .invoiceId(invoice.getId())
            .invoiceNumber(invoice.getInvoiceNumber())
            .total(invoice.getTotal())
            .billedEntriesCount(updatedCount)
            .message("Invoice generated successfully in DRAFT status")
            .build();
    }

    // Helper methods
    
    private void validateRequest(InvoiceGenerationPreviewRequest request) {
        if (request.getFromDate().isAfter(request.getToDate())) {
            throw new IllegalArgumentException("From date must be before or equal to to date");
        }
    }
    
    private List<UUID> getProjectIdsForClient(UUID companyId, UUID clientId, UUID specificProjectId) {
        if (specificProjectId != null) {
            // Verify project belongs to client
            Project project = projectRepository.findById(specificProjectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
            
            if (!project.getClientId().equals(clientId)) {
                throw new IllegalArgumentException("Project does not belong to specified client");
            }
            
            return List.of(specificProjectId);
        }
        
        // Get all projects for client
        List<Project> projects = projectRepository.findByClientIdAndCompanyId(clientId, companyId);
        return projects.stream()
            .map(Project::getId)
            .collect(Collectors.toList());
    }
    
    private InvoiceGenerationPreviewResponse buildEmptyPreview(
            InvoiceGenerationPreviewRequest request,
            Client client,
            String message) {
        
        return InvoiceGenerationPreviewResponse.builder()
            .clientId(client.getId())
            .clientName(client.getName())
            .projectId(request.getProjectId())
            .fromDate(request.getFromDate())
            .toDate(request.getToDate())
            .totalMinutes(0)
            .totalHours(BigDecimal.ZERO)
            .subtotal(BigDecimal.ZERO)
            .taxRate(DEFAULT_TAX_RATE)
            .taxAmount(BigDecimal.ZERO)
            .total(BigDecimal.ZERO)
            .lineItems(new ArrayList<>())
            .missingRateUsers(new ArrayList<>())
            .entriesCount(0)
            .canGenerate(false)
            .message(message)
            .build();
    }
    
    private List<MissingRateUser> checkMissingRates(List<TimeEntry> entries, Map<UUID, User> usersMap) {
        return entries.stream()
            .map(TimeEntry::getUserId)
            .distinct()
            .map(userId -> usersMap.get(userId))
            .filter(user -> user.getHourlyRate() == null || user.getHourlyRate().compareTo(BigDecimal.ZERO) == 0)
            .map(user -> MissingRateUser.builder()
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .message("Hourly rate not configured")
                .build())
            .collect(Collectors.toList());
    }
    
    private List<PreviewLineItem> buildLineItemsByUser(
            List<TimeEntry> entries,
            Map<UUID, User> usersMap,
            Boolean includeDescriptions) {
        
        Map<UUID, List<TimeEntry>> entriesByUser = entries.stream()
            .collect(Collectors.groupingBy(TimeEntry::getUserId));
        
        return entriesByUser.entrySet().stream()
            .map(entry -> {
                UUID userId = entry.getKey();
                List<TimeEntry> userEntries = entry.getValue();
                User user = usersMap.get(userId);
                
                int totalMinutes = userEntries.stream()
                    .mapToInt(e -> e.getHours() * MINUTES_PER_HOUR)
                    .sum();
                
                BigDecimal hours = new BigDecimal(totalMinutes)
                    .divide(new BigDecimal(MINUTES_PER_HOUR), 2, RoundingMode.HALF_UP);
                
                BigDecimal rate = user.getHourlyRate() != null ? user.getHourlyRate() : BigDecimal.ZERO;
                BigDecimal amount = hours.multiply(rate).setScale(2, RoundingMode.HALF_UP);
                
                String description = "Services - " + user.getName();
                String notes = null;
                
                if (Boolean.TRUE.equals(includeDescriptions)) {
                    notes = userEntries.stream()
                        .map(TimeEntry::getDescription)
                        .filter(Objects::nonNull)
                        .filter(d -> !d.isBlank())
                        .distinct()
                        .collect(Collectors.joining("; "));
                }
                
                return PreviewLineItem.builder()
                    .description(description)
                    .quantityMinutes(totalMinutes)
                    .quantityHours(hours)
                    .unitPrice(rate)
                    .amount(amount)
                    .userId(userId)
                    .userName(user.getName())
                    .aggregatedNotes(notes)
                    .build();
            })
            .sorted(Comparator.comparing(PreviewLineItem::getUserName))
            .collect(Collectors.toList());
    }
    
    private List<PreviewLineItem> buildLineItemsByTask(
            List<TimeEntry> entries,
            Map<UUID, User> usersMap,
            Boolean includeDescriptions) {
        
        // Group by task (null tasks go together)
        Map<UUID, List<TimeEntry>> entriesByTask = entries.stream()
            .collect(Collectors.groupingBy(
                e -> e.getTaskId() != null ? e.getTaskId() : UUID.fromString("00000000-0000-0000-0000-000000000000"),
                LinkedHashMap::new,
                Collectors.toList()
            ));
        
        // Get task details
        Set<UUID> taskIds = entries.stream()
            .map(TimeEntry::getTaskId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        
        Map<UUID, Task> tasksMap = new HashMap<>();
        if (!taskIds.isEmpty()) {
            tasksMap = taskRepository.findAllById(taskIds).stream()
                .collect(Collectors.toMap(Task::getId, t -> t));
        }
        
        final Map<UUID, Task> finalTasksMap = tasksMap;
        
        return entriesByTask.entrySet().stream()
            .map(entry -> {
                UUID taskId = entry.getKey();
                boolean isNullTask = taskId.equals(UUID.fromString("00000000-0000-0000-0000-000000000000"));
                List<TimeEntry> taskEntries = entry.getValue();
                
                // Calculate total minutes
                int totalMinutes = taskEntries.stream()
                    .mapToInt(e -> e.getHours() * MINUTES_PER_HOUR)
                    .sum();
                
                BigDecimal hours = new BigDecimal(totalMinutes)
                    .divide(new BigDecimal(MINUTES_PER_HOUR), 2, RoundingMode.HALF_UP);
                
                // Use blended rate (weighted average)
                BigDecimal totalAmount = taskEntries.stream()
                    .map(e -> {
                        User user = usersMap.get(e.getUserId());
                        BigDecimal rate = user.getHourlyRate() != null ? user.getHourlyRate() : BigDecimal.ZERO;
                        BigDecimal entryHours = new BigDecimal(e.getHours());
                        return entryHours.multiply(rate);
                    })
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                
                BigDecimal blendedRate = hours.compareTo(BigDecimal.ZERO) > 0 ?
                    totalAmount.divide(hours, 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
                
                String description;
                String taskTitle = null;
                
                if (isNullTask) {
                    description = "General Services (No Task)";
                } else {
                    Task task = finalTasksMap.get(taskId);
                    taskTitle = task != null ? task.getTitle() : "Task #" + taskId.toString().substring(0, 8);
                    description = "Task: " + taskTitle;
                }
                
                String notes = null;
                if (Boolean.TRUE.equals(includeDescriptions)) {
                    notes = taskEntries.stream()
                        .map(TimeEntry::getDescription)
                        .filter(Objects::nonNull)
                        .filter(d -> !d.isBlank())
                        .distinct()
                        .collect(Collectors.joining("; "));
                }
                
                return PreviewLineItem.builder()
                    .description(description)
                    .quantityMinutes(totalMinutes)
                    .quantityHours(hours)
                    .unitPrice(blendedRate)
                    .amount(totalAmount.setScale(2, RoundingMode.HALF_UP))
                    .taskId(isNullTask ? null : taskId)
                    .taskTitle(taskTitle)
                    .aggregatedNotes(notes)
                    .build();
            })
            .collect(Collectors.toList());
    }
}
