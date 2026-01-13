package com.itops.service;

import com.itops.dto.DashboardResponse;
import com.itops.domain.*;
import com.itops.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final TimeEntryRepository timeEntryRepository;
    private final TimesheetRepository timesheetRepository;
    private final UserRepository userRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final OrgScopeService orgScopeService;
    
    public DashboardResponse getDashboardStats(UUID userId, UUID companyId, String role) {
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDate today = LocalDate.now();
            
            // Calculate week boundaries
            LocalDate startOfThisWeek = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
            LocalDate startOfLastWeek = startOfThisWeek.minusWeeks(1);
            LocalDate endOfLastWeek = startOfThisWeek.minusDays(1);
            
            // Determine if user should see all data or only their own
            boolean isRestrictedUser = "USER".equals(role) || "ADMIN".equals(role);
            
            // Project stats - filter by company first, then by role
            List<Project> allProjects = projectRepository.findByCompanyId(companyId);
            if (allProjects == null) {
                allProjects = new ArrayList<>();
            }
            
            // For restricted users, only show projects they're a member of
            if (isRestrictedUser) {
                Set<UUID> userProjectIds = projectMemberRepository.findAll().stream()
                        .filter(pm -> pm.getUser() != null && userId.equals(pm.getUser().getId()))
                        .filter(pm -> pm.getProject() != null)
                        .map(pm -> pm.getProject().getId())
                        .collect(Collectors.toSet());
                
                allProjects = allProjects.stream()
                        .filter(p -> userProjectIds.contains(p.getId()))
                        .collect(Collectors.toList());
            }
            
            List<Project> activeProjects = allProjects.stream()
                    .filter(p -> p.getDeletedAt() == null)
                    .filter(p -> "ACTIVE".equals(p.getStatus()))
                    .collect(Collectors.toList());
            
            int totalProjects = (int) allProjects.stream()
                    .filter(p -> p.getDeletedAt() == null)
                    .count();
        
        Map<String, Long> projectsByStatus = allProjects.stream()
                .filter(p -> p.getDeletedAt() == null)
                .collect(Collectors.groupingBy(
                        p -> p.getStatus() != null ? p.getStatus() : "PLANNING",
                        Collectors.counting()
                ));
        
        // Task stats - filter by company first, then by role
        List<Task> allTasks = taskRepository.findByCompanyId(companyId);
        
        // For restricted users, only show tasks assigned to them
        if (isRestrictedUser) {
            allTasks = allTasks.stream()
                    .filter(t -> userId.equals(t.getAssignedTo()))
                    .collect(Collectors.toList());
        }
        
        Map<String, Long> tasksByStatus = allTasks.stream()
                .filter(t -> t.getDeletedAt() == null)
                .collect(Collectors.groupingBy(
                        t -> t.getStatus() != null ? t.getStatus() : "TODO",
                        Collectors.counting()
                ));
        
        // Time tracking stats - filter by company first, then by role
        List<TimeEntry> allTimeEntries = timeEntryRepository.findByCompanyId(companyId);
        
        // For restricted users, only show their own time entries
        if (isRestrictedUser) {
            allTimeEntries = allTimeEntries.stream()
                    .filter(te -> userId.equals(te.getUserId()))
                    .collect(Collectors.toList());
        }
        
        List<TimeEntry> thisWeekEntries = allTimeEntries.stream()
                .filter(te -> te.getDeletedAt() == null)
                .filter(te -> {
                    LocalDate entryDate = te.getDate();
                    return !entryDate.isBefore(startOfThisWeek) && !entryDate.isAfter(today);
                })
                .collect(Collectors.toList());
        
        List<TimeEntry> lastWeekEntries = allTimeEntries.stream()
                .filter(te -> te.getDeletedAt() == null)
                .filter(te -> {
                    LocalDate entryDate = te.getDate();
                    return !entryDate.isBefore(startOfLastWeek) && !entryDate.isAfter(endOfLastWeek);
                })
                .collect(Collectors.toList());
        
        double hoursThisWeek = thisWeekEntries.stream()
                .mapToDouble(TimeEntry::getHours)
                .sum();
        
        double hoursLastWeek = lastWeekEntries.stream()
                .mapToDouble(TimeEntry::getHours)
                .sum();
        
        double totalHoursLogged = allTimeEntries.stream()
                .filter(te -> te.getDeletedAt() == null)
                .mapToDouble(TimeEntry::getHours)
                .sum();
        
        double billableHours = allTimeEntries.stream()
                .filter(te -> te.getDeletedAt() == null)
                .filter(te -> Boolean.TRUE.equals(te.getIsBillable()))
                .mapToDouble(TimeEntry::getHours)
                .sum();
        
        // Pending approvals stats - filter based on role (timesheets + leave requests)
        // For ADMIN/USER: show their own pending requests
        // For SUPER_USER/TOP_USER: show requests they need to approve
        int pendingApprovalsCount = 0;
        
        if (isRestrictedUser) {
            // ADMIN and USER see their own pending requests (submitted timesheets + pending leave requests)
            long myPendingTimesheets = timesheetRepository.findByCompanyIdAndDeletedAtIsNull(companyId).stream()
                    .filter(ts -> "SUBMITTED".equals(ts.getStatus()))
                    .filter(ts -> userId.equals(ts.getUserId()))
                    .count();
            
            long myPendingLeaveRequests = leaveRequestRepository.findByCompanyIdAndDeletedAtIsNull(companyId).stream()
                    .filter(lr -> "PENDING".equals(lr.getStatus()))
                    .filter(lr -> userId.equals(lr.getUserId()))
                    .count();
            
            pendingApprovalsCount = (int)(myPendingTimesheets + myPendingLeaveRequests);
        } else {
            // Get user IDs that this user can approve based on their role
            // Use orgScopeService to match actual approval permissions
            final Set<UUID> subordinateUserIds = orgScopeService.getAllowedUserIds(userId, role, companyId);
            
            // Remove self from the set to avoid counting own requests
            subordinateUserIds.remove(userId);
            
            // Count pending timesheets from subordinates
            List<Timesheet> pendingTimesheets = timesheetRepository.findByCompanyIdAndDeletedAtIsNull(companyId).stream()
                    .filter(ts -> "SUBMITTED".equals(ts.getStatus()))
                    .filter(ts -> subordinateUserIds.contains(ts.getUserId()))
                    .collect(Collectors.toList());
            
            // Count pending leave requests from subordinates
            List<LeaveRequest> pendingLeaveRequests = leaveRequestRepository.findByCompanyIdAndDeletedAtIsNull(companyId).stream()
                    .filter(lr -> "PENDING".equals(lr.getStatus()))
                    .filter(lr -> subordinateUserIds.contains(lr.getUserId()))
                    .collect(Collectors.toList());
            
            pendingApprovalsCount = pendingTimesheets.size() + pendingLeaveRequests.size();
        }
        
        // User stats - filter by company
        List<User> allUsers = userRepository.findByCompanyId(companyId);
        int totalTeamMembers = (int) allUsers.stream()
                .filter(u -> u.getDeletedAt() == null)
                .count();
        
        // Active users (users who logged time this week)
        Set<UUID> activeUserIds = thisWeekEntries.stream()
                .map(TimeEntry::getUserId)
                .collect(Collectors.toSet());
        int activeTeamMembers = isRestrictedUser ? (activeUserIds.contains(userId) ? 1 : 0) : activeUserIds.size();
        
        // Recent activities - filtered by company and role
        List<DashboardResponse.ActivityItem> recentActivities = getRecentActivities(userId, companyId, isRestrictedUser);
        
        // Upcoming deadlines - filtered by company and role
        List<DashboardResponse.DeadlineItem> upcomingDeadlines = getUpcomingDeadlines(userId, companyId, isRestrictedUser);
        
        // Top projects by hours - filtered by company and role
        List<DashboardResponse.ProjectHoursItem> topProjectsByHours = getTopProjectsByHours(userId, companyId, isRestrictedUser);
        
        return DashboardResponse.builder()
                .activeProjects(activeProjects.size())
                .totalProjects(totalProjects)
                .hoursThisWeek(Math.round(hoursThisWeek * 100.0) / 100.0)
                .hoursLastWeek(Math.round(hoursLastWeek * 100.0) / 100.0)
                .pendingTimesheets(pendingApprovalsCount)
                .teamMembers(totalTeamMembers)
                .activeTeamMembers(activeTeamMembers)
                .planningProjects(projectsByStatus.getOrDefault("PLANNING", 0L).intValue())
                .onHoldProjects(projectsByStatus.getOrDefault("ON_HOLD", 0L).intValue())
                .completedProjects(projectsByStatus.getOrDefault("COMPLETED", 0L).intValue())
                .cancelledProjects(projectsByStatus.getOrDefault("CANCELLED", 0L).intValue())
                .totalTasks(allTasks.stream().filter(t -> t.getDeletedAt() == null).collect(Collectors.toList()).size())
                .completedTasks(tasksByStatus.getOrDefault("DONE", 0L).intValue())
                .inProgressTasks(tasksByStatus.getOrDefault("IN_PROGRESS", 0L).intValue())
                .todoTasks(tasksByStatus.getOrDefault("TODO", 0L).intValue())
                .totalHoursLogged(Math.round(totalHoursLogged * 100.0) / 100.0)
                .billableHours(Math.round(billableHours * 100.0) / 100.0)
                .nonBillableHours(Math.round((totalHoursLogged - billableHours) * 100.0) / 100.0)
                .recentActivities(recentActivities)
                .upcomingDeadlines(upcomingDeadlines)
                .topProjectsByHours(topProjectsByHours)
                .build();
        } catch (Exception e) {
            // Log the error and return empty dashboard
            e.printStackTrace();
            return DashboardResponse.builder()
                    .activeProjects(0)
                    .totalProjects(0)
                    .hoursThisWeek(0.0)
                    .hoursLastWeek(0.0)
                    .pendingTimesheets(0)
                    .teamMembers(0)
                    .activeTeamMembers(0)
                    .planningProjects(0)
                    .onHoldProjects(0)
                    .completedProjects(0)
                    .cancelledProjects(0)
                    .totalTasks(0)
                    .completedTasks(0)
                    .inProgressTasks(0)
                    .todoTasks(0)
                    .totalHoursLogged(0.0)
                    .billableHours(0.0)
                    .nonBillableHours(0.0)
                    .recentActivities(new ArrayList<>())
                    .upcomingDeadlines(new ArrayList<>())
                    .topProjectsByHours(new ArrayList<>())
                    .build();
        }
    }
    
    private List<DashboardResponse.ActivityItem> getRecentActivities(UUID userId, UUID companyId, boolean isRestrictedUser) {
        try {
            List<DashboardResponse.ActivityItem> activities = new ArrayList<>();
            
            // Get recent projects - filtered by company and role
            List<Project> recentProjects = projectRepository.findByCompanyId(companyId).stream()
                    .filter(p -> p.getDeletedAt() == null)
                    .filter(p -> p.getCreatedAt() != null)
                    .filter(p -> {
                        if (!isRestrictedUser) return true;
                        // For restricted users, only show their projects
                        return projectMemberRepository.findAll().stream()
                                .anyMatch(pm -> pm.getProject() != null && pm.getProject().getId().equals(p.getId()) && 
                                                pm.getUser() != null && pm.getUser().getId().equals(userId));
                    })
                    .sorted(Comparator.comparing(Project::getCreatedAt).reversed())
                    .limit(3)
                    .collect(Collectors.toList());
        
        for (Project project : recentProjects) {
            String userName = "System";
            if (project.getProjectOwnerId() != null) {
                userName = userRepository.findById(project.getProjectOwnerId())
                        .map(User::getName)
                        .orElse("Unknown");
            }
            
            activities.add(DashboardResponse.ActivityItem.builder()
                    .id(project.getId().toString())
                    .type("PROJECT")
                    .title("New Project Created")
                    .description(project.getName())
                    .time(getRelativeTime(project.getCreatedAt()))
                    .userName(userName)
                    .build());
        }
        
        // Get recent tasks - filtered by company and role
        List<Task> recentTasks = taskRepository.findByCompanyId(companyId).stream()
                .filter(t -> t.getDeletedAt() == null)
                .filter(t -> t.getCreatedAt() != null)
                .filter(t -> {
                    if (!isRestrictedUser) return true;
                    // For restricted users, only show their tasks
                    return userId.equals(t.getAssignedTo());
                })
                .sorted(Comparator.comparing(Task::getCreatedAt).reversed())
                .limit(3)
                .collect(Collectors.toList());
        
        for (Task task : recentTasks) {
            String userName = "System";
            if (task.getAssignedTo() != null) {
                userName = userRepository.findById(task.getAssignedTo())
                        .map(User::getName)
                        .orElse("Unknown");
            }
            
            activities.add(DashboardResponse.ActivityItem.builder()
                    .id(task.getId().toString())
                    .type("TASK")
                    .title("New Task Created")
                    .description(task.getTitle())
                    .time(getRelativeTime(task.getCreatedAt()))
                    .userName(userName)
                    .build());
        }
        
        // Get recent timesheets - filtered by company and role
        List<Timesheet> recentTimesheets = timesheetRepository.findByCompanyIdAndDeletedAtIsNull(companyId).stream()
                .filter(ts -> ts.getCreatedAt() != null)
                .filter(ts -> {
                    if (!isRestrictedUser) return true;
                    // For restricted users, only show their timesheets
                    return userId.equals(ts.getUserId());
                })
                .sorted(Comparator.comparing(Timesheet::getCreatedAt).reversed())
                .limit(2)
                .collect(Collectors.toList());
        
        for (Timesheet timesheet : recentTimesheets) {
            String userName = userRepository.findById(timesheet.getUserId())
                    .map(User::getName)
                    .orElse("Unknown");
            
            String statusText = timesheet.getStatus() != null ? timesheet.getStatus() : "DRAFT";
            String action = switch (statusText) {
                case "SUBMITTED" -> "submitted";
                case "APPROVED" -> "approved";
                case "REJECTED" -> "rejected";
                default -> "created";
            };
            
            activities.add(DashboardResponse.ActivityItem.builder()
                    .id(timesheet.getId().toString())
                    .type("TIMESHEET")
                    .title("Timesheet " + action)
                    .description("Timesheet for week starting " + timesheet.getWeekStart() + " " + action)
                    .time(getRelativeTime(timesheet.getCreatedAt()))
                    .userName(userName)
                    .build());
        }
        
        // Sort all activities by time and return top 10
        return activities.stream()
                .sorted(Comparator.comparing(DashboardResponse.ActivityItem::getTime))
                .limit(10)
                .collect(Collectors.toList());
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
    
    private List<DashboardResponse.DeadlineItem> getUpcomingDeadlines(UUID userId, UUID companyId, boolean isRestrictedUser) {
        try {
            List<DashboardResponse.DeadlineItem> deadlines = new ArrayList<>();
            LocalDate today = LocalDate.now();
        
        // Get projects with end dates - filtered by company and role
        List<Project> projectsWithDeadlines = projectRepository.findByCompanyId(companyId).stream()
                .filter(p -> p.getDeletedAt() == null)
                .filter(p -> p.getEndDate() != null)
                .filter(p -> !p.getEndDate().isBefore(today))
                .filter(p -> {
                    if (!isRestrictedUser) return true;
                    // For restricted users, only show their projects
                    return projectMemberRepository.findAll().stream()
                            .anyMatch(pm -> pm.getProject() != null && pm.getProject().getId().equals(p.getId()) && 
                                            pm.getUser() != null && pm.getUser().getId().equals(userId));
                })
                .sorted(Comparator.comparing(Project::getEndDate))
                .limit(5)
                .collect(Collectors.toList());
        
        for (Project project : projectsWithDeadlines) {
            long daysRemaining = ChronoUnit.DAYS.between(today, project.getEndDate());
            deadlines.add(DashboardResponse.DeadlineItem.builder()
                    .id(project.getId().toString())
                    .type("PROJECT")
                    .name(project.getName())
                    .dueDate(project.getEndDate().toString())
                    .daysRemaining((int) daysRemaining)
                    .status(project.getStatus() != null ? project.getStatus() : "IN_PROGRESS")
                    .build());
        }
        
        // Get tasks with due dates - filtered by company and role
        List<Task> tasksWithDeadlines = taskRepository.findByCompanyId(companyId).stream()
                .filter(t -> t.getDeletedAt() == null)
                .filter(t -> t.getDueDate() != null)
                .filter(t -> !t.getDueDate().isBefore(today))
                .filter(t -> {
                    if (!isRestrictedUser) return true;
                    // For restricted users, only show their tasks
                    return userId.equals(t.getAssignedTo());
                })
                .sorted(Comparator.comparing(Task::getDueDate))
                .limit(5)
                .collect(Collectors.toList());
        
        for (Task task : tasksWithDeadlines) {
            long daysRemaining = ChronoUnit.DAYS.between(today, task.getDueDate());
            deadlines.add(DashboardResponse.DeadlineItem.builder()
                    .id(task.getId().toString())
                    .type("TASK")
                    .name(task.getTitle())
                    .dueDate(task.getDueDate().toString())
                    .daysRemaining((int) daysRemaining)
                    .status(task.getStatus() != null ? task.getStatus() : "TODO")
                    .build());
        }
        
        // Sort by days remaining and return top 10
        return deadlines.stream()
                .sorted(Comparator.comparing(DashboardResponse.DeadlineItem::getDaysRemaining))
                .limit(10)
                .collect(Collectors.toList());
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
    
    private List<DashboardResponse.ProjectHoursItem> getTopProjectsByHours(UUID userId, UUID companyId, boolean isRestrictedUser) {
        try {
            List<Project> projects = projectRepository.findByCompanyId(companyId).stream()
                    .filter(p -> p.getDeletedAt() == null)
                    .filter(p -> {
                        if (!isRestrictedUser) return true;
                        // For restricted users, only show their projects
                        return projectMemberRepository.findAll().stream()
                                .anyMatch(pm -> pm.getProject() != null && pm.getProject().getId().equals(p.getId()) && 
                                                pm.getUser() != null && pm.getUser().getId().equals(userId));
                    })
                    .collect(Collectors.toList());
            
            List<DashboardResponse.ProjectHoursItem> projectHours = new ArrayList<>();
        
        for (Project project : projects) {
            List<TimeEntry> entries = timeEntryRepository.findByProjectId(project.getId());
            
            double totalHours = entries.stream()
                    .mapToDouble(TimeEntry::getHours)
                    .sum();
            
            double billableHours = entries.stream()
                    .filter(te -> Boolean.TRUE.equals(te.getIsBillable()))
                    .mapToDouble(TimeEntry::getHours)
                    .sum();
            
            if (totalHours > 0) {
                projectHours.add(DashboardResponse.ProjectHoursItem.builder()
                        .id(project.getId().toString())
                        .name(project.getName())
                        .hours(Math.round(totalHours * 100.0) / 100.0)
                        .billableHours(Math.round(billableHours * 100.0) / 100.0)
                        .status(project.getStatus() != null ? project.getStatus() : "IN_PROGRESS")
                        .build());
            }
        }
        
        // Sort by total hours and return top 5
        return projectHours.stream()
                .sorted(Comparator.comparing(DashboardResponse.ProjectHoursItem::getHours).reversed())
                .limit(5)
                .collect(Collectors.toList());
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
    
    private String getRelativeTime(LocalDateTime dateTime) {
        if (dateTime == null) return "Unknown";
        
        LocalDateTime now = LocalDateTime.now();
        long minutes = ChronoUnit.MINUTES.between(dateTime, now);
        long hours = ChronoUnit.HOURS.between(dateTime, now);
        long days = ChronoUnit.DAYS.between(dateTime, now);
        
        if (minutes < 1) return "Just now";
        if (minutes < 60) return minutes + " minutes ago";
        if (hours < 24) return hours + " hour" + (hours > 1 ? "s" : "") + " ago";
        if (days < 7) return days + " day" + (days > 1 ? "s" : "") + " ago";
        if (days < 30) return (days / 7) + " week" + (days / 7 > 1 ? "s" : "") + " ago";
        return (days / 30) + " month" + (days / 30 > 1 ? "s" : "") + " ago";
    }
}
