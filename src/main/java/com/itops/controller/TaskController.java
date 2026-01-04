package com.itops.controller;

import com.itops.dto.TaskRequest;
import com.itops.dto.TaskResponse;
import com.itops.security.JwtUtil;
import com.itops.service.TaskService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final JwtUtil jwtUtil;

    @GetMapping
    public ResponseEntity<List<TaskResponse>> getAllTasks(@RequestParam(required = false) UUID projectId,
                                                           @RequestParam(required = false) UUID phaseId,
                                                           @RequestParam(required = false) UUID teamId,
                                                           @RequestParam(required = false) UUID assignedTo,
                                                           HttpServletRequest request) {
        UUID companyId = getCompanyIdFromRequest(request);
        UUID userId = getUserIdFromRequest(request);
        String userRole = getUserRoleFromRequest(request);
        
        log.info("GET /tasks - userId: {}, role: {}, companyId: {}, projectId: {}, phaseId: {}, teamId: {}, assignedTo: {}", 
            userId, userRole, companyId, projectId, phaseId, teamId, assignedTo);

        // Use comprehensive filtering that handles all parameter combinations
        List<TaskResponse> tasks = taskService.getTasksWithFilters(companyId, userId, userRole, projectId, phaseId, teamId, assignedTo);
        log.info("Returning {} tasks", tasks.size());
        return ResponseEntity.ok(tasks);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> getTaskById(@PathVariable UUID id, HttpServletRequest request) {
        UUID companyId = getCompanyIdFromRequest(request);
        return ResponseEntity.ok(taskService.getTaskById(id, companyId));
    }

    @PostMapping
    public ResponseEntity<TaskResponse> createTask(@Valid @RequestBody TaskRequest taskRequest,
                                                    HttpServletRequest request) {
        UUID companyId = getCompanyIdFromRequest(request);
        UUID userId = getUserIdFromRequest(request);
        return ResponseEntity.ok(taskService.createTask(taskRequest, companyId, userId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TaskResponse> updateTask(@PathVariable UUID id,
                                                    @Valid @RequestBody TaskRequest taskRequest,
                                                    HttpServletRequest request) {
        UUID companyId = getCompanyIdFromRequest(request);
        UUID actorId = getUserIdFromRequest(request);
        return ResponseEntity.ok(taskService.updateTask(id, taskRequest, companyId, actorId));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<TaskResponse> patchTask(@PathVariable UUID id,
                                                  @RequestBody TaskRequest taskRequest,
                                                  HttpServletRequest request) {
        UUID companyId = getCompanyIdFromRequest(request);
        UUID actorId = getUserIdFromRequest(request);
        return ResponseEntity.ok(taskService.patchTask(id, taskRequest, companyId, actorId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable UUID id, HttpServletRequest request) {
        UUID companyId = getCompanyIdFromRequest(request);
        taskService.deleteTask(id, companyId);
        return ResponseEntity.noContent().build();
    }

    private UUID getCompanyIdFromRequest(HttpServletRequest request) {
        String token = request.getHeader("Authorization").substring(7);
        return jwtUtil.getCompanyIdFromToken(token);
    }

    private UUID getUserIdFromRequest(HttpServletRequest request) {
        String token = request.getHeader("Authorization").substring(7);
        return jwtUtil.getUserIdFromToken(token);
    }

    private String getUserRoleFromRequest(HttpServletRequest request) {
        String token = request.getHeader("Authorization").substring(7);
        return jwtUtil.getRoleFromToken(token);
    }
}
