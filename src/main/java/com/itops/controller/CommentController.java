package com.itops.controller;

import com.itops.dto.CommentResponse;
import com.itops.dto.CreateCommentRequest;
import com.itops.model.Comment;
import com.itops.security.JwtUtil;
import com.itops.service.CommentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;
    private final JwtUtil jwtUtil;

    // Get comments for a phase
    @GetMapping("/phases/{phaseId}")
    public ResponseEntity<List<CommentResponse>> getPhaseComments(
            @PathVariable UUID phaseId,
            HttpServletRequest request
    ) {
        UUID companyId = getCompanyIdFromRequest(request);
        List<CommentResponse> comments = commentService.getComments(
                Comment.EntityType.PHASE,
                phaseId,
                companyId
        );
        return ResponseEntity.ok(comments);
    }

    // Create comment for a phase
    @PostMapping("/phases/{phaseId}")
    public ResponseEntity<CommentResponse> createPhaseComment(
            @PathVariable UUID phaseId,
            @RequestBody CreateCommentRequest request,
            HttpServletRequest httpRequest
    ) {
        UUID companyId = getCompanyIdFromRequest(httpRequest);
        UUID userId = getUserIdFromRequest(httpRequest);
        request.setEntityType(Comment.EntityType.PHASE);
        request.setEntityId(phaseId);
        CommentResponse comment = commentService.createComment(request, companyId, userId);
        return ResponseEntity.ok(comment);
    }

    // Get comments for a task
    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<List<CommentResponse>> getTaskComments(
            @PathVariable UUID taskId,
            HttpServletRequest request
    ) {
        UUID companyId = getCompanyIdFromRequest(request);
        List<CommentResponse> comments = commentService.getComments(
                Comment.EntityType.TASK,
                taskId,
                companyId
        );
        return ResponseEntity.ok(comments);
    }

    // Create comment for a task
    @PostMapping("/tasks/{taskId}")
    public ResponseEntity<CommentResponse> createTaskComment(
            @PathVariable UUID taskId,
            @RequestBody CreateCommentRequest request,
            HttpServletRequest httpRequest
    ) {
        UUID companyId = getCompanyIdFromRequest(httpRequest);
        UUID userId = getUserIdFromRequest(httpRequest);
        request.setEntityType(Comment.EntityType.TASK);
        request.setEntityId(taskId);
        CommentResponse comment = commentService.createComment(request, companyId, userId);
        return ResponseEntity.ok(comment);
    }

    // Get comments for a subtask
    @GetMapping("/subtasks/{subtaskId}")
    public ResponseEntity<List<CommentResponse>> getSubtaskComments(
            @PathVariable UUID subtaskId,
            HttpServletRequest request
    ) {
        UUID companyId = getCompanyIdFromRequest(request);
        List<CommentResponse> comments = commentService.getComments(
                Comment.EntityType.SUBTASK,
                subtaskId,
                companyId
        );
        return ResponseEntity.ok(comments);
    }

    // Create comment for a subtask
    @PostMapping("/subtasks/{subtaskId}")
    public ResponseEntity<CommentResponse> createSubtaskComment(
            @PathVariable UUID subtaskId,
            @RequestBody CreateCommentRequest request,
            HttpServletRequest httpRequest
    ) {
        UUID companyId = getCompanyIdFromRequest(httpRequest);
        UUID userId = getUserIdFromRequest(httpRequest);
        request.setEntityType(Comment.EntityType.SUBTASK);
        request.setEntityId(subtaskId);
        CommentResponse comment = commentService.createComment(request, companyId, userId);
        return ResponseEntity.ok(comment);
    }

    private UUID getCompanyIdFromRequest(HttpServletRequest request) {
        String token = request.getHeader("Authorization").substring(7);
        return jwtUtil.getCompanyIdFromToken(token);
    }

    private UUID getUserIdFromRequest(HttpServletRequest request) {
        String token = request.getHeader("Authorization").substring(7);
        return jwtUtil.getUserIdFromToken(token);
    }
}
