package com.itops.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttachmentResponse {
    private UUID id;
    private String entityType;
    private UUID entityId;
    private String filename;
    private String fileUrl;
    private Long fileSize;
    private String mimeType;
    private UUID uploadedBy;
    private LocalDateTime createdAt;
}
