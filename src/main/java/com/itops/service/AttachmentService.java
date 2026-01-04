package com.itops.service;

import com.itops.domain.Attachment;
import com.itops.domain.Task;
import com.itops.domain.Subtask;
import com.itops.domain.ProjectPhase;
import com.itops.dto.AttachmentResponse;
import com.itops.dto.CreateAttachmentRequest;
import com.itops.dto.NotificationType;
import com.itops.repository.AttachmentRepository;
import com.itops.repository.TaskRepository;
import com.itops.repository.SubtaskRepository;
import com.itops.repository.ProjectPhaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttachmentService {
    
    private final AttachmentRepository attachmentRepository;
    private final NotificationService notificationService;
    private final TaskRepository taskRepository;
    private final SubtaskRepository subtaskRepository;
    private final ProjectPhaseRepository projectPhaseRepository;
    
    @Value("${file.upload-dir:uploads}")
    private String uploadDir;
    
    // Max file sizes
    private static final long MAX_IMAGE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final long MAX_DOCUMENT_SIZE = 25 * 1024 * 1024; // 25MB
    private static final long MAX_VIDEO_SIZE = 50 * 1024 * 1024; // 50MB
    
    // Allowed MIME types
    private static final List<String> ALLOWED_IMAGE_TYPES = Arrays.asList(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp"
    );
    
    private static final List<String> ALLOWED_DOCUMENT_TYPES = Arrays.asList(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain"
    );
    
    private static final List<String> ALLOWED_VIDEO_TYPES = Arrays.asList(
            "video/mp4", "video/mpeg", "video/quicktime", "video/x-msvideo", "video/webm"
    );
    
    @Transactional
    public AttachmentResponse uploadAttachment(MultipartFile file, String entityType, UUID entityId, UUID companyId, UUID userId) {
        // Validate file
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        
        String contentType = file.getContentType();
        long fileSize = file.getSize();
        
        // Validate file type and size
        validateFileTypeAndSize(contentType, fileSize);
        
        // Validate entity type
        if (!Arrays.asList("TASK", "SUBTASK", "PHASE").contains(entityType)) {
            throw new IllegalArgumentException("Invalid entity type. Must be TASK, SUBTASK, or PHASE");
        }
        
        try {
            // Create upload directory if it doesn't exist
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
            
            // Generate unique filename
            String originalFilename = file.getOriginalFilename();
            String fileExtension = originalFilename != null && originalFilename.contains(".") 
                    ? originalFilename.substring(originalFilename.lastIndexOf(".")) 
                    : "";
            String uniqueFilename = UUID.randomUUID().toString() + fileExtension;
            
            // Save file to disk
            Path filePath = uploadPath.resolve(uniqueFilename);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
            
            // Create attachment record
            Attachment attachment = Attachment.builder()
                    .entityType(entityType)
                    .entityId(entityId)
                    .filename(originalFilename)
                    .fileUrl("/uploads/" + uniqueFilename)
                    .fileSize(fileSize)
                    .mimeType(contentType)
                    .uploadedBy(userId)
                    .companyId(companyId)
                    .build();
            
            attachment = attachmentRepository.save(attachment);
            
            // Send notifications
            sendAttachmentNotification(entityType, entityId, originalFilename, companyId, userId);
            
            return toResponse(attachment);
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file: " + e.getMessage(), e);
        }
    }
    
    private void validateFileTypeAndSize(String contentType, long fileSize) {
        if (contentType == null) {
            throw new IllegalArgumentException("File type could not be determined");
        }
        
        boolean isValidType = false;
        long maxSize = 0;
        
        if (ALLOWED_IMAGE_TYPES.contains(contentType)) {
            isValidType = true;
            maxSize = MAX_IMAGE_SIZE;
        } else if (ALLOWED_DOCUMENT_TYPES.contains(contentType)) {
            isValidType = true;
            maxSize = MAX_DOCUMENT_SIZE;
        } else if (ALLOWED_VIDEO_TYPES.contains(contentType)) {
            isValidType = true;
            maxSize = MAX_VIDEO_SIZE;
        }
        
        if (!isValidType) {
            throw new IllegalArgumentException(
                    "File type not allowed. Allowed types: images (jpeg, png, gif, webp), " +
                    "documents (pdf, doc, docx, xls, xlsx, ppt, pptx, txt), " +
                    "videos (mp4, mpeg, mov, avi, webm)"
            );
        }
        
        if (fileSize > maxSize) {
            String maxSizeStr = maxSize / (1024 * 1024) + "MB";
            throw new IllegalArgumentException("File size exceeds maximum allowed size of " + maxSizeStr);
        }
    }
    
    private void sendAttachmentNotification(String entityType, UUID entityId, String filename, UUID companyId, UUID userId) {
        switch (entityType) {
            case "TASK":
                Task task = taskRepository.findById(entityId).orElse(null);
                if (task != null && task.getAssignedTo() != null && !task.getAssignedTo().equals(userId)) {
                    notificationService.createNotification(
                            task.getAssignedTo(),
                            companyId,
                            NotificationType.ATTACHMENT_ADDED,
                            "Attachment Added to Task",
                            "An attachment (" + filename + ") was added to task: " + task.getTitle(),
                            "TASK",
                            task.getId(),
                            userId
                    );
                }
                break;
                
            case "SUBTASK":
                Subtask subtask = subtaskRepository.findById(entityId).orElse(null);
                if (subtask != null && subtask.getAssignedTo() != null && !subtask.getAssignedTo().equals(userId)) {
                    notificationService.createNotification(
                            subtask.getAssignedTo(),
                            companyId,
                            NotificationType.ATTACHMENT_ADDED,
                            "Attachment Added to Subtask",
                            "An attachment (" + filename + ") was added to subtask: " + subtask.getTitle(),
                            "SUBTASK",
                            subtask.getId(),
                            userId
                    );
                }
                break;
                
            case "PHASE":
                ProjectPhase phase = projectPhaseRepository.findById(entityId).orElse(null);
                if (phase != null && phase.getTeamId() != null) {
                    // Could notify team members - for now just log
                    // Future enhancement: notify all team members
                }
                break;
        }
    }
    
    @Transactional(readOnly = true)
    public List<AttachmentResponse> getAttachmentsByEntity(String entityType, UUID entityId, UUID companyId) {
        List<Attachment> attachments = attachmentRepository.findByEntityTypeAndEntityIdAndCompanyId(
                entityType, entityId, companyId);
        return attachments.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public AttachmentResponse getAttachment(UUID id, UUID companyId) {
        Attachment attachment = attachmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Attachment not found"));
        
        if (!attachment.getCompanyId().equals(companyId)) {
            throw new RuntimeException("Access denied");
        }
        
        return toResponse(attachment);
    }
    
    @Transactional
    public void deleteAttachment(UUID id, UUID companyId) {
        Attachment attachment = attachmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Attachment not found"));
        
        if (!attachment.getCompanyId().equals(companyId)) {
            throw new RuntimeException("Access denied");
        }
        
        attachmentRepository.delete(attachment);
    }
    
    private AttachmentResponse toResponse(Attachment attachment) {
        return AttachmentResponse.builder()
                .id(attachment.getId())
                .entityType(attachment.getEntityType())
                .entityId(attachment.getEntityId())
                .filename(attachment.getFilename())
                .fileUrl(attachment.getFileUrl())
                .fileSize(attachment.getFileSize())
                .mimeType(attachment.getMimeType())
                .uploadedBy(attachment.getUploadedBy())
                .createdAt(attachment.getCreatedAt())
                .build();
    }
}
