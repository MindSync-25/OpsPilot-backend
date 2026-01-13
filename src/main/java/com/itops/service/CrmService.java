package com.itops.service;

import com.itops.domain.Client;
import com.itops.domain.ClientCrm;
import com.itops.domain.User;
import com.itops.dto.ClientResponse;
import com.itops.dto.CreateClientRequest;
import com.itops.dto.NotificationType;
import com.itops.exception.ResourceNotFoundException;
import com.itops.repository.ClientCrmRepository;
import com.itops.repository.ClientRepository;
import com.itops.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CrmService {
    private final ClientRepository clientRepository;
    private final ClientCrmRepository clientCrmRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public List<com.itops.dto.ClientCrmResponse> listAllCrmClients(UUID companyId) {
        List<ClientCrm> crmList = clientCrmRepository.findAllByCompanyId(companyId);
        return crmList.stream().map(crm -> {
            Client client = crm.getClient();
            
            // Get owner name
            String ownerName = null;
            if (crm.getOwnerId() != null) {
                ownerName = userRepository.findById(crm.getOwnerId())
                    .map(User::getName)
                    .orElse(null);
            }
            
            return com.itops.dto.ClientCrmResponse.builder()
                .id(crm.getId())
                .clientId(client != null ? client.getId() : null)
                .name(client != null ? client.getName() : null)
                .contactName(client != null ? client.getContactName() : null)
                .email(client != null ? client.getEmail() : null)
                .phone(client != null ? client.getPhone() : null)
                .address(client != null ? client.getAddress() : null)
                .status(client != null ? client.getStatus() : null)
                .leadStage(crm.getLeadStage())
                .notes(crm.getNotes())
                .ownerId(crm.getOwnerId())
                .ownerName(ownerName)
                .nextFollowUp(crm.getNextFollowUp())
                .createdAt(crm.getCreatedAt())
                .updatedAt(crm.getUpdatedAt())
                .build();
        }).collect(Collectors.toList());
    }

    @Transactional
    public ClientCrm createCrmClient(UUID companyId, UUID userId, CreateClientRequest request) {
        // Create client
        Client client = new Client();
        client.setCompanyId(companyId);
        client.setName(request.getName());
        client.setContactName(request.getContactName());
        client.setEmail(request.getEmail());
        client.setPhone(request.getPhone());
        client.setAddress(request.getAddress());
        client.setStatus(request.getStatus() != null ? request.getStatus() : "ACTIVE");
        Client savedClient = clientRepository.save(client);
        
        // Create CRM record with current user as owner
        ClientCrm crm = new ClientCrm();
        crm.setClient(savedClient);
        crm.setLeadStage("PROSPECT");
        crm.setOwnerId(userId); // Set creator as owner
        ClientCrm savedCrm = clientCrmRepository.save(crm);
        
        // Notify owner (creator) that they own this deal
        notificationService.createNotification(
                userId,
                companyId,
                NotificationType.CRM_DEAL_CREATED,
                "New CRM Deal Created",
                "You created a new CRM deal for: " + savedClient.getName(),
                "CRM",
                savedClient.getId(),
                userId
        );
        
        return savedCrm;
    }

    @Transactional
    public com.itops.dto.ClientCrmResponse updateCrmClient(UUID clientId, com.itops.dto.UpdateCrmClientRequest request) {
        ClientCrm crm = clientCrmRepository.findByClientId(clientId)
            .orElseThrow(() -> new ResourceNotFoundException("CRM record not found for client: " + clientId));
        Client client = crm.getClient();
        
        UUID oldOwnerId = crm.getOwnerId();
        String oldLeadStage = crm.getLeadStage();
        
        if (request.getName() != null) client.setName(request.getName());
        if (request.getContactName() != null) client.setContactName(request.getContactName());
        if (request.getEmail() != null) client.setEmail(request.getEmail());
        if (request.getPhone() != null) client.setPhone(request.getPhone());
        if (request.getAddress() != null) client.setAddress(request.getAddress());
        if (request.getStatus() != null) client.setStatus(request.getStatus());
        clientRepository.save(client);
        
        if (request.getLeadStage() != null) crm.setLeadStage(request.getLeadStage());
        if (request.getNotes() != null) crm.setNotes(request.getNotes());
        if (request.getOwnerId() != null) crm.setOwnerId(request.getOwnerId());
        if (request.getNextFollowUp() != null && !request.getNextFollowUp().trim().isEmpty()) {
            try {
                // Try parsing as full LocalDateTime first (e.g., "2026-01-08T10:30:00")
                crm.setNextFollowUp(LocalDateTime.parse(request.getNextFollowUp()));
            } catch (Exception e) {
                // If that fails, parse as LocalDate and convert to LocalDateTime at start of day
                LocalDate date = LocalDate.parse(request.getNextFollowUp());
                crm.setNextFollowUp(date.atStartOfDay());
            }
        }
        crm.setUpdatedAt(LocalDateTime.now());
        clientCrmRepository.save(crm);
        
        // Send notifications for stage or owner changes
        if (request.getOwnerId() != null && !request.getOwnerId().equals(oldOwnerId)) {
            // Notify new owner
            notificationService.createNotification(
                    request.getOwnerId(),
                    client.getCompanyId(),
                    NotificationType.CLIENT_ASSIGNED,
                    "CRM Deal Assigned",
                    "You have been assigned as owner of CRM deal: " + client.getName(),
                    "CRM",
                    client.getId(),
                    null
            );
        }
        
        if (request.getLeadStage() != null && !request.getLeadStage().equals(oldLeadStage)) {
            String notifType = NotificationType.CRM_DEAL_UPDATED;
            String title = "CRM Deal Stage Updated";
            
            if ("WON".equals(request.getLeadStage())) {
                notifType = NotificationType.CRM_DEAL_WON;
                title = "CRM Deal Won";
            } else if ("LOST".equals(request.getLeadStage())) {
                notifType = NotificationType.CRM_DEAL_LOST;
                title = "CRM Deal Lost";
            }
            
            // Notify owner of stage change
            if (crm.getOwnerId() != null) {
                notificationService.createNotification(
                        crm.getOwnerId(),
                        client.getCompanyId(),
                        notifType,
                        title,
                        "CRM deal " + client.getName() + " stage changed to: " + request.getLeadStage(),
                        "CRM",
                        client.getId(),
                        null
                );
            }
        }
        
        // Get owner name
        String ownerName = null;
        if (crm.getOwnerId() != null) {
            ownerName = userRepository.findById(crm.getOwnerId())
                .map(User::getName)
                .orElse(null);
        }
        
        return com.itops.dto.ClientCrmResponse.builder()
            .id(crm.getId())
            .clientId(client.getId())
            .name(client.getName())
            .contactName(client.getContactName())
            .email(client.getEmail())
            .phone(client.getPhone())
            .address(client.getAddress())
            .status(client.getStatus())
            .leadStage(crm.getLeadStage())
            .notes(crm.getNotes())
            .ownerId(crm.getOwnerId())
            .ownerName(ownerName)
            .nextFollowUp(crm.getNextFollowUp())
            .createdAt(crm.getCreatedAt())
            .updatedAt(crm.getUpdatedAt())
            .build();
    }

    @Transactional
    public com.itops.dto.ClientCrmResponse updateCrmById(UUID crmId, com.itops.dto.UpdateCrmClientRequest request) {
        ClientCrm crm = clientCrmRepository.findById(crmId)
            .orElseThrow(() -> new ResourceNotFoundException("CRM record not found for id: " + crmId));
        Client client = crm.getClient();
        
        if (request.getName() != null) client.setName(request.getName());
        if (request.getContactName() != null) client.setContactName(request.getContactName());
        if (request.getEmail() != null) client.setEmail(request.getEmail());
        if (request.getPhone() != null) client.setPhone(request.getPhone());
        if (request.getAddress() != null) client.setAddress(request.getAddress());
        if (request.getStatus() != null) client.setStatus(request.getStatus());
        clientRepository.save(client);
        
        if (request.getLeadStage() != null) crm.setLeadStage(request.getLeadStage());
        if (request.getNotes() != null) crm.setNotes(request.getNotes());
        if (request.getOwnerId() != null) crm.setOwnerId(request.getOwnerId());
        if (request.getNextFollowUp() != null && !request.getNextFollowUp().trim().isEmpty()) {
            try {
                // Try parsing as full LocalDateTime first (e.g., "2026-01-08T10:30:00")
                crm.setNextFollowUp(LocalDateTime.parse(request.getNextFollowUp()));
            } catch (Exception e) {
                // If that fails, parse as LocalDate and convert to LocalDateTime at start of day
                LocalDate date = LocalDate.parse(request.getNextFollowUp());
                crm.setNextFollowUp(date.atStartOfDay());
            }
        }
        crm.setUpdatedAt(LocalDateTime.now());
        clientCrmRepository.save(crm);
        
        // Get owner name
        String ownerName = null;
        if (crm.getOwnerId() != null) {
            ownerName = userRepository.findById(crm.getOwnerId())
                .map(User::getName)
                .orElse(null);
        }
        
        return com.itops.dto.ClientCrmResponse.builder()
            .id(crm.getId())
            .clientId(client.getId())
            .name(client.getName())
            .contactName(client.getContactName())
            .email(client.getEmail())
            .phone(client.getPhone())
            .address(client.getAddress())
            .status(client.getStatus())
            .leadStage(crm.getLeadStage())
            .notes(crm.getNotes())
            .ownerId(crm.getOwnerId())
            .ownerName(ownerName)
            .nextFollowUp(crm.getNextFollowUp())
            .createdAt(crm.getCreatedAt())
            .updatedAt(crm.getUpdatedAt())
            .build();
    }

    @Transactional
    public ClientCrm updateLeadStage(UUID clientId, String leadStage) {
        ClientCrm crm = clientCrmRepository.findByClientId(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("CRM record not found for client: " + clientId));
        crm.setLeadStage(leadStage);
        return clientCrmRepository.save(crm);
    }
}
