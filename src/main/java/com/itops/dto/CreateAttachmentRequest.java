package com.itops.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateAttachmentRequest {
    private String entityType; // TASK or SUBTASK
    private UUID entityId;
    private String filename;
    private String fileUrl;
    private Long fileSize;
    private String mimeType;
}
