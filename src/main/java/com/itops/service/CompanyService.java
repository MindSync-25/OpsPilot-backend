package com.itops.service;

import com.itops.domain.Company;
import com.itops.exception.ResourceNotFoundException;
import com.itops.model.Comment;
import com.itops.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final ProjectRepository projectRepository;
    private final ProjectPhaseRepository projectPhaseRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final TaskRepository taskRepository;
    private final SubtaskRepository subtaskRepository;
    private final TimeEntryRepository timeEntryRepository;
    private final TimesheetRepository timesheetRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final ClientRepository clientRepository;
    private final ClientCrmRepository clientCrmRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final BillingEventRepository billingEventRepository;
    private final AttachmentRepository attachmentRepository;
    private final CommentRepository commentRepository;
    private final NotificationRepository notificationRepository;

    @Transactional
    public void deleteCompany(UUID companyId, UUID requestingUserId) {
        log.info("Starting company deletion for companyId: {}, requested by userId: {}", companyId, requestingUserId);
        
        // Verify company exists
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found"));
        
        // Verify requesting user has permission (must be TOP_USER of this company)
        var requestingUser = userRepository.findById(requestingUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        if (!requestingUser.getCompanyId().equals(companyId)) {
            throw new IllegalArgumentException("User does not belong to this company");
        }
        
        if (requestingUser.getRole() != com.itops.domain.User.UserRole.TOP_USER) {
            throw new IllegalArgumentException("Only company owner (TOP_USER) can delete the company");
        }
        
        log.info("Permission verified. Starting cascade deletion for company: {}", company.getName());
        
        // Delete in reverse dependency order to avoid foreign key constraints
        
        // 1. Delete notifications
        log.info("Deleting notifications for company: {}", companyId);
        notificationRepository.deleteAll(notificationRepository.findByCompanyId(companyId));
        
        // 2. Delete comments (tasks only - Comment.EntityType only has PHASE, TASK, SUBTASK)
        log.info("Deleting comments for company: {}", companyId);
        var tasks = taskRepository.findByCompanyId(companyId);
        tasks.forEach(task -> {
            commentRepository.deleteAll(
                commentRepository.findByEntityTypeAndEntityId(Comment.EntityType.TASK, task.getId())
            );
        });
        
        var phases = projectPhaseRepository.findActivePhasesByCompany(companyId);
        phases.forEach(phase -> {
            commentRepository.deleteAll(
                commentRepository.findByEntityTypeAndEntityId(Comment.EntityType.PHASE, phase.getId())
            );
        });
        
        var projects = projectRepository.findByCompanyId(companyId);
        
        // 3. Delete attachments
        log.info("Deleting attachments for company: {}", companyId);
        tasks.forEach(task -> attachmentRepository.deleteAll(
            attachmentRepository.findByEntityTypeAndEntityId("TASK", task.getId())
        ));
        projects.forEach(project -> attachmentRepository.deleteAll(
            attachmentRepository.findByEntityTypeAndEntityId("PROJECT", project.getId())
        ));
        
        // 4. Delete subtasks
        log.info("Deleting subtasks for company: {}", companyId);
        tasks.forEach(task -> subtaskRepository.deleteAll(subtaskRepository.findByTaskId(task.getId())));
        
        // 5. Delete tasks
        log.info("Deleting tasks for company: {}", companyId);
        taskRepository.deleteAll(tasks);
        
        // 6. Delete project members
        log.info("Deleting project members for company: {}", companyId);
        projectMemberRepository.deleteAll(projectMemberRepository.findByCompanyId(companyId));
        
        // 7. Delete project phases
        log.info("Deleting project phases for company: {}", companyId);
        projectPhaseRepository.deleteAll(projectPhaseRepository.findActivePhasesByCompany(companyId));
        
        // 8. Delete projects
        log.info("Deleting projects for company: {}", companyId);
        projectRepository.deleteAll(projects);
        
        // 9. Delete time entries and timesheets
        log.info("Deleting time entries and timesheets for company: {}", companyId);
        timeEntryRepository.deleteAll(timeEntryRepository.findByCompanyId(companyId));
        timesheetRepository.deleteAll(timesheetRepository.findByCompanyIdAndDeletedAtIsNull(companyId));
        
        // 10. Delete leave requests
        log.info("Deleting leave requests for company: {}", companyId);
        leaveRequestRepository.deleteAll(leaveRequestRepository.findByCompanyIdAndDeletedAtIsNull(companyId));
        
        // 11. Delete invoice items and invoices
        log.info("Deleting invoices for company: {}", companyId);
        var invoices = invoiceRepository.findByCompanyId(companyId);
        invoices.forEach(invoice -> invoiceItemRepository.deleteAll(invoiceItemRepository.findByInvoiceId(invoice.getId())));
        invoiceRepository.deleteAll(invoices);
        
        // 12. Delete CRM clients and regular clients
        log.info("Deleting clients for company: {}", companyId);
        clientCrmRepository.deleteAll(clientCrmRepository.findAllByCompanyId(companyId));
        clientRepository.deleteAll(clientRepository.findByCompanyId(companyId));
        
        // 13. Delete billing events
        log.info("Deleting billing events for company: {}", companyId);
        billingEventRepository.deleteAll(billingEventRepository.findByCompanyId(companyId));
        
        // 14. Delete subscription
        log.info("Deleting subscription for company: {}", companyId);
        subscriptionRepository.findByCompanyIdAndDeletedAtIsNull(companyId).ifPresent(subscriptionRepository::delete);
        
        // 15. Delete teams
        log.info("Deleting teams for company: {}", companyId);
        teamRepository.deleteAll(teamRepository.findByCompanyId(companyId));
        
        // 16. Delete users
        log.info("Deleting users for company: {}", companyId);
        userRepository.deleteAll(userRepository.findByCompanyId(companyId));
        
        // 17. Finally, delete the company
        log.info("Deleting company: {}", company.getName());
        companyRepository.delete(company);
        
        log.info("Company deletion completed successfully for companyId: {}", companyId);
    }

    @Transactional(readOnly = true)
    public com.itops.dto.CompanyResponse getCompany(UUID companyId, UUID requestingUserId) {
        // Verify company exists
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found"));
        
        // Verify requesting user belongs to this company
        var requestingUser = userRepository.findById(requestingUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        if (!requestingUser.getCompanyId().equals(companyId)) {
            throw new IllegalArgumentException("User does not belong to this company");
        }
        
        return mapToResponse(company);
    }

    @Transactional
    public com.itops.dto.CompanyResponse updateCompany(UUID companyId, UUID requestingUserId, com.itops.dto.UpdateCompanyRequest request) {
        // Verify company exists
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found"));
        
        // Verify requesting user belongs to this company
        var requestingUser = userRepository.findById(requestingUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        if (!requestingUser.getCompanyId().equals(companyId)) {
            throw new IllegalArgumentException("User does not belong to this company");
        }
        
        // Update fields
        if (request.getName() != null && !request.getName().isEmpty()) {
            company.setName(request.getName());
        }
        
        if (request.getAddress() != null) {
            company.setAddress(request.getAddress());
        }
        
        if (request.getCity() != null) {
            company.setCity(request.getCity());
        }
        
        if (request.getState() != null) {
            company.setState(request.getState());
        }
        
        if (request.getZipCode() != null) {
            company.setZipCode(request.getZipCode());
        }
        
        if (request.getPhone() != null) {
            company.setPhone(request.getPhone());
        }
        
        Company updated = companyRepository.save(company);
        return mapToResponse(updated);
    }

    private com.itops.dto.CompanyResponse mapToResponse(Company company) {
        return com.itops.dto.CompanyResponse.builder()
                .id(company.getId())
                .name(company.getName())
                .address(company.getAddress())
                .city(company.getCity())
                .state(company.getState())
                .zipCode(company.getZipCode())
                .phone(company.getPhone())
                .subscriptionStatus(company.getSubscriptionStatus())
                .createdAt(company.getCreatedAt())
                .updatedAt(company.getUpdatedAt())
                .build();
    }
}
