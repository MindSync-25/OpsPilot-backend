package com.itops.service;

import com.itops.domain.Client;
import com.itops.dto.ClientResponse;
import com.itops.dto.CreateClientRequest;
import com.itops.dto.UpdateClientRequest;
import com.itops.exception.ResourceNotFoundException;
import com.itops.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClientService {
    
    private final ClientRepository clientRepository;
    
    /**
     * List all clients for a company with optional filters
     */
    /**
     * List all business clients (lead_stage = 'WON') for Clients module
     */
    public List<ClientResponse> listClients(UUID companyId, String status, String search) {
        // Ignore status/search for now, only show business clients (lead_stage = 'WON')
        List<Client> clients = clientRepository.findAllActiveBusinessClients(companyId);
        return clients.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * Get a single client by ID
     */
    public ClientResponse getClient(UUID companyId, UUID id) {
        Client client = clientRepository.findByIdAndCompanyIdAndDeletedAtIsNull(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found with id: " + id));
        return toResponse(client);
    }
    
    /**
     * Create a new client
     */
    @Transactional
    public ClientResponse createClient(UUID companyId, CreateClientRequest request) {
        Client client = new Client();
        client.setCompanyId(companyId);
        client.setName(request.getName());
        client.setContactName(request.getContactName());
        client.setEmail(request.getEmail());
        client.setPhone(request.getPhone());
        client.setAddress(request.getAddress());
        client.setStatus(request.getStatus() != null ? request.getStatus() : "ACTIVE");

        Client savedClient = clientRepository.save(client);
        return toResponse(savedClient);
    }
    
    /**
     * Update an existing client
     */
    @Transactional
    public ClientResponse updateClient(UUID companyId, UUID id, UpdateClientRequest request) {
        Client client = clientRepository.findByIdAndCompanyIdAndDeletedAtIsNull(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found with id: " + id));

        if (StringUtils.hasText(request.getName())) {
            client.setName(request.getName());
        }
        if (request.getContactName() != null) {
            client.setContactName(request.getContactName());
        }
        if (request.getEmail() != null) {
            client.setEmail(request.getEmail());
        }
        if (request.getPhone() != null) {
            client.setPhone(request.getPhone());
        }
        if (request.getAddress() != null) {
            client.setAddress(request.getAddress());
        }
        if (StringUtils.hasText(request.getStatus())) {
            client.setStatus(request.getStatus());
        }

        Client updatedClient = clientRepository.save(client);
        return toResponse(updatedClient);
    }
    
    /**
     * Soft delete a client
     */
    @Transactional
    public void deleteClient(UUID companyId, UUID id) {
        Client client = clientRepository.findByIdAndCompanyIdAndDeletedAtIsNull(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found with id: " + id));

        client.setDeletedAt(LocalDateTime.now());
        clientRepository.save(client);
    }
    
    /**
     * Convert entity to response DTO
     */
    private ClientResponse toResponse(Client client) {
        return ClientResponse.builder()
                .id(client.getId())
                .name(client.getName())
                .contactName(client.getContactName())
                .email(client.getEmail())
                .phone(client.getPhone())
                .address(client.getAddress())
                .status(client.getStatus())
                .companyId(client.getCompanyId())
                .createdAt(client.getCreatedAt())
                .updatedAt(client.getUpdatedAt())
                .build();
    }
}
