package com.itops.service;

import com.itops.domain.TimeEntry;
import com.itops.dto.CreateManualTimeEntryRequest;
import com.itops.dto.StartTimerRequest;
import com.itops.dto.TimeEntryResponse;
import com.itops.dto.UpdateTimeEntryRequest;
import com.itops.repository.ProjectRepository;
import com.itops.repository.TaskRepository;
import com.itops.repository.TimeEntryRepository;
import com.itops.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TimeEntryService {

    private final TimeEntryRepository timeEntryRepository;
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    public List<TimeEntryResponse> getAllTimeEntries(UUID companyId, UUID projectId, UUID userId, LocalDate fromDate, LocalDate toDate, Boolean billable) {
        log.info("Getting time entries for companyId: {}, projectId: {}, userId: {}", companyId, projectId, userId);
        List<TimeEntry> entries = timeEntryRepository.findByCompanyId(companyId);
        return entries.stream()
                .filter(e -> e.getDeletedAt() == null)
                .filter(e -> projectId == null || e.getProjectId().equals(projectId))
                .filter(e -> userId == null || e.getUserId().equals(userId))
                .filter(e -> fromDate == null || !e.getDate().isBefore(fromDate))
                .filter(e -> toDate == null || !e.getDate().isAfter(toDate))
                .filter(e -> billable == null || e.getIsBillable().equals(billable))
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public Optional<TimeEntryResponse> getActiveTimer(UUID userId) {
        return timeEntryRepository.findByUserIdAndIsActiveTrue(userId)
                .filter(e -> e.getDeletedAt() == null)
                .map(this::toResponse);
    }

    @Transactional
    public TimeEntryResponse startTimer(StartTimerRequest request, UUID userId, UUID companyId) {
        Optional<TimeEntry> existingTimer = timeEntryRepository.findByUserIdAndIsActiveTrue(userId)
                .filter(e -> e.getDeletedAt() == null);
        if (existingTimer.isPresent()) {
            throw new RuntimeException("You already have an active timer running");
        }
        validateProjectBelongsToCompany(request.getProjectId(), companyId);
        if (request.getTaskId() != null) {
            validateTaskBelongsToProject(request.getTaskId(), request.getProjectId(), companyId);
        }
        TimeEntry entry = TimeEntry.builder()
                .userId(userId)
                .projectId(request.getProjectId())
                .taskId(request.getTaskId())
                .date(LocalDate.now())
                .hours(0)
                .description(request.getNotes())
                .isBillable(request.getIsBillable() != null ? request.getIsBillable() : true)
                .startTime(LocalDateTime.now())
                .isActive(true)
                .build();
        entry.setCompanyId(companyId);
        return toResponse(timeEntryRepository.save(entry));
    }

    @Transactional
    public TimeEntryResponse stopTimer(UUID userId, UUID companyId) {
        TimeEntry entry = timeEntryRepository.findByUserIdAndIsActiveTrue(userId)
                .filter(e -> e.getDeletedAt() == null && e.getCompanyId().equals(companyId))
                .orElseThrow(() -> new RuntimeException("No active timer found"));
        entry.setEndTime(LocalDateTime.now());
        entry.setIsActive(false);
        if (entry.getStartTime() != null && entry.getEndTime() != null) {
            long minutes = Duration.between(entry.getStartTime(), entry.getEndTime()).toMinutes();
            entry.setHours(minutes > 0 ? Math.max(1, (int) Math.round(minutes / 60.0)) : 0);
        }
        return toResponse(timeEntryRepository.save(entry));
    }

    @Transactional
    public TimeEntryResponse createManualEntry(CreateManualTimeEntryRequest request, UUID userId, UUID companyId) {
        validateProjectBelongsToCompany(request.getProjectId(), companyId);
        if (request.getTaskId() != null) {
            validateTaskBelongsToProject(request.getTaskId(), request.getProjectId(), companyId);
        }
        TimeEntry entry = TimeEntry.builder()
                .userId(userId)
                .projectId(request.getProjectId())
                .taskId(request.getTaskId())
                .date(request.getDate())
                .hours(request.getHours())
                .description(request.getNotes())
                .isBillable(request.getIsBillable() != null ? request.getIsBillable() : true)
                .isActive(false)
                .build();
        entry.setCompanyId(companyId);
        return toResponse(timeEntryRepository.save(entry));
    }

    @Transactional
    public TimeEntryResponse updateEntry(UUID id, UpdateTimeEntryRequest request, UUID userId, UUID companyId, boolean isAdmin) {
        TimeEntry entry = timeEntryRepository.findById(id)
                .filter(e -> e.getDeletedAt() == null && e.getCompanyId().equals(companyId))
                .orElseThrow(() -> new RuntimeException("Time entry not found"));
        if (!isAdmin && !entry.getUserId().equals(userId)) {
            throw new RuntimeException("You can only edit your own time entries");
        }
        if (entry.getIsActive()) {
            throw new RuntimeException("Cannot edit an active timer");
        }
        if (request.getProjectId() != null) {
            validateProjectBelongsToCompany(request.getProjectId(), companyId);
            entry.setProjectId(request.getProjectId());
        }
        if (request.getTaskId() != null) {
            UUID projectId = request.getProjectId() != null ? request.getProjectId() : entry.getProjectId();
            validateTaskBelongsToProject(request.getTaskId(), projectId, companyId);
            entry.setTaskId(request.getTaskId());
        }
        if (request.getDate() != null) entry.setDate(request.getDate());
        if (request.getHours() != null) entry.setHours(request.getHours());
        if (request.getIsBillable() != null) entry.setIsBillable(request.getIsBillable());
        if (request.getNotes() != null) entry.setDescription(request.getNotes());
        return toResponse(timeEntryRepository.save(entry));
    }

    @Transactional
    public void deleteEntry(UUID id, UUID userId, UUID companyId, boolean isAdmin) {
        TimeEntry entry = timeEntryRepository.findById(id)
                .filter(e -> e.getDeletedAt() == null && e.getCompanyId().equals(companyId))
                .orElseThrow(() -> new RuntimeException("Time entry not found"));
        if (!isAdmin && !entry.getUserId().equals(userId)) {
            throw new RuntimeException("You can only delete your own time entries");
        }
        if (entry.getIsActive()) {
            throw new RuntimeException("Cannot delete an active timer");
        }
        entry.setDeletedAt(LocalDateTime.now());
        timeEntryRepository.save(entry);
    }

    private TimeEntryResponse toResponse(TimeEntry entry) {
        // Fetch user name
        String userName = userRepository.findById(entry.getUserId())
                .map(u -> u.getName())
                .orElse(null);
        
        // Fetch project name
        String projectName = projectRepository.findById(entry.getProjectId())
                .map(p -> p.getName())
                .orElse(null);
        
        // Fetch task name
        String taskName = entry.getTaskId() != null 
                ? taskRepository.findById(entry.getTaskId())
                        .map(t -> t.getTitle())
                        .orElse(null)
                : null;
        
        TimeEntryResponse.TimeEntryResponseBuilder builder = TimeEntryResponse.builder()
                .id(entry.getId())
                .userId(entry.getUserId())
                .userName(userName)
                .projectId(entry.getProjectId())
                .projectName(projectName)
                .taskId(entry.getTaskId())
                .taskName(taskName)
                .date(entry.getDate() != null ? entry.getDate().toString() : null)
                .hours(entry.getHours())
                .description(entry.getDescription())
                .isBillable(entry.getIsBillable())
                .createdAt(entry.getCreatedAt())
                .startTime(entry.getStartTime())
                .endTime(entry.getEndTime())
                .isActive(entry.getIsActive());
        if (entry.getStartTime() != null) {
            LocalDateTime endTime = entry.getEndTime() != null ? entry.getEndTime() : LocalDateTime.now();
            long minutes = Duration.between(entry.getStartTime(), endTime).toMinutes();
            builder.durationMinutes((int) minutes);
        }
        return builder.build();
    }

    private void validateProjectBelongsToCompany(UUID projectId, UUID companyId) {
        projectRepository.findById(projectId)
                .filter(p -> p.getDeletedAt() == null && p.getCompanyId().equals(companyId))
                .orElseThrow(() -> new RuntimeException("Project not found"));
    }

    private void validateTaskBelongsToProject(UUID taskId, UUID projectId, UUID companyId) {
        taskRepository.findById(taskId)
                .filter(t -> t.getDeletedAt() == null && t.getProjectId().equals(projectId))
                .orElseThrow(() -> new RuntimeException("Task not found"));
    }
}
