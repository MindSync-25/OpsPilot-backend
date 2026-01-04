package com.itops.service;

import com.itops.domain.*;
import com.itops.dto.*;
import com.itops.repository.*;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final InvoiceRepository invoiceRepository;
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final TimeEntryRepository timeEntryRepository;
    private final UserRepository userRepository;
    private final ClientRepository clientRepository;

    @Transactional(readOnly = true)
    public RevenueReportDTO getRevenueReport(UUID companyId, ReportFilterDTO filter) {
        LocalDate[] dates = calculateDateRange(filter);
        LocalDate startDate = dates[0];
        LocalDate endDate = dates[1];

        java.util.List<Invoice> invoices = invoiceRepository.findFilteredInvoices(
            companyId, filter.getClientId(), filter.getProjectId(), 
            null, startDate, endDate
        );

        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal paidAmount = BigDecimal.ZERO;
        BigDecimal pendingAmount = BigDecimal.ZERO;
        BigDecimal overdueAmount = BigDecimal.ZERO;
        int totalInvoices = invoices.size();
        int paidInvoices = 0;
        int pendingInvoices = 0;
        int overdueInvoices = 0;

        for (Invoice inv : invoices) {
            BigDecimal total = inv.getTotal();
            totalRevenue = totalRevenue.add(total);

            if ("PAID".equals(inv.getStatus())) {
                paidAmount = paidAmount.add(total);
                paidInvoices++;
            } else if ("OVERDUE".equals(inv.getStatus())) {
                overdueAmount = overdueAmount.add(total);
                overdueInvoices++;
            } else {
                pendingAmount = pendingAmount.add(total);
                pendingInvoices++;
            }
        }

        java.util.List<RevenueReportDTO.DailyRevenue> dailyRevenue = new ArrayList<>();
        Map<LocalDate, BigDecimal> revenueByDate = new HashMap<>();
        Map<LocalDate, Integer> countByDate = new HashMap<>();
        
        for (Invoice inv : invoices) {
            LocalDate date = inv.getIssueDate();
            revenueByDate.merge(date, inv.getTotal(), BigDecimal::add);
            countByDate.merge(date, 1, Integer::sum);
        }
        
        for (Map.Entry<LocalDate, BigDecimal> entry : revenueByDate.entrySet()) {
            dailyRevenue.add(RevenueReportDTO.DailyRevenue.builder()
                .date(entry.getKey())
                .amount(entry.getValue())
                .invoiceCount(countByDate.get(entry.getKey()))
                .build());
        }
        dailyRevenue.sort(Comparator.comparing(RevenueReportDTO.DailyRevenue::getDate));

        java.util.List<RevenueReportDTO.ClientRevenue> revenueByClient = new ArrayList<>();
        Map<UUID, BigDecimal> clientRevenueMap = new HashMap<>();
        Map<UUID, Integer> clientInvoiceCount = new HashMap<>();
        
        for (Invoice inv : invoices) {
            if (inv.getClientId() != null) {
                clientRevenueMap.merge(inv.getClientId(), inv.getTotal(), BigDecimal::add);
                clientInvoiceCount.merge(inv.getClientId(), 1, Integer::sum);
            }
        }
        
        for (Map.Entry<UUID, BigDecimal> entry : clientRevenueMap.entrySet()) {
            Optional<Client> clientOpt = clientRepository.findById(entry.getKey());
            if (clientOpt.isPresent()) {
                revenueByClient.add(RevenueReportDTO.ClientRevenue.builder()
                    .clientId(entry.getKey().toString())
                    .clientName(clientOpt.get().getName())
                    .revenue(entry.getValue())
                    .invoiceCount(clientInvoiceCount.get(entry.getKey()))
                    .build());
            }
        }
        revenueByClient.sort(Comparator.comparing(RevenueReportDTO.ClientRevenue::getRevenue).reversed());

        java.util.List<RevenueReportDTO.ProjectRevenue> revenueByProject = new ArrayList<>();
        Map<UUID, BigDecimal> projectRevenueMap = new HashMap<>();
        Map<UUID, Integer> projectInvoiceCount = new HashMap<>();
        
        for (Invoice inv : invoices) {
            if (inv.getProjectId() != null) {
                projectRevenueMap.merge(inv.getProjectId(), inv.getTotal(), BigDecimal::add);
                projectInvoiceCount.merge(inv.getProjectId(), 1, Integer::sum);
            }
        }
        
        for (Map.Entry<UUID, BigDecimal> entry : projectRevenueMap.entrySet()) {
            Optional<Project> projectOpt = projectRepository.findById(entry.getKey());
            if (projectOpt.isPresent()) {
                revenueByProject.add(RevenueReportDTO.ProjectRevenue.builder()
                    .projectId(entry.getKey().toString())
                    .projectName(projectOpt.get().getName())
                    .revenue(entry.getValue())
                    .invoiceCount(projectInvoiceCount.get(entry.getKey()))
                    .build());
            }
        }
        revenueByProject.sort(Comparator.comparing(RevenueReportDTO.ProjectRevenue::getRevenue).reversed());

        Map<String, BigDecimal> revenueByStatus = new HashMap<>();
        revenueByStatus.put("PAID", paidAmount);
        revenueByStatus.put("PENDING", pendingAmount);
        revenueByStatus.put("OVERDUE", overdueAmount);

        return RevenueReportDTO.builder()
            .totalRevenue(totalRevenue)
            .paidAmount(paidAmount)
            .pendingAmount(pendingAmount)
            .overdueAmount(overdueAmount)
            .totalInvoices(totalInvoices)
            .paidInvoices(paidInvoices)
            .pendingInvoices(pendingInvoices)
            .overdueInvoices(overdueInvoices)
            .dailyRevenue(dailyRevenue)
            .revenueByClient(revenueByClient)
            .revenueByProject(revenueByProject)
            .revenueByStatus(revenueByStatus)
            .build();
    }

    @Transactional(readOnly = true)
    public ProjectReportDTO getProjectReport(UUID companyId, ReportFilterDTO filter) {
        LocalDate[] dates = calculateDateRange(filter);
        LocalDate startDate = dates[0];
        LocalDate endDate = dates[1];

        java.util.List<Project> projects;
        if (filter.getClientId() != null) {
            projects = projectRepository.findByClientIdAndCompanyIdAndDeletedAtIsNull(
                filter.getClientId(), companyId);
        } else {
            projects = projectRepository.findByCompanyIdAndDeletedAtIsNull(companyId);
        }

        projects = projects.stream()
            .filter(p -> !p.getCreatedAt().toLocalDate().isBefore(startDate) 
                      && !p.getCreatedAt().toLocalDate().isAfter(endDate))
            .collect(Collectors.toList());

        int totalProjects = projects.size();
        int activeProjects = 0;
        int completedProjects = 0;
        int onHoldProjects = 0;
        double totalCompletionRate = 0.0;

        java.util.List<ProjectReportDTO.ProjectStats> projectStats = new ArrayList<>();
        
        for (Project project : projects) {
            if ("ACTIVE".equals(project.getStatus())) activeProjects++;
            else if ("COMPLETED".equals(project.getStatus())) completedProjects++;
            else if ("ON_HOLD".equals(project.getStatus())) onHoldProjects++;

            java.util.List<Task> tasks = taskRepository.findByProjectIdAndDeletedAtIsNull(project.getId());
            int totalTasks = tasks.size();
            long completedTasks = tasks.stream()
                .filter(t -> "DONE".equals(t.getStatus()))
                .count();
            
            double completion = totalTasks > 0 ? (completedTasks * 100.0 / totalTasks) : 0.0;
            totalCompletionRate += completion;

            int totalHours = timeEntryRepository.findByProjectId(project.getId()).stream()
                .mapToInt(TimeEntry::getHours)
                .sum();

            projectStats.add(ProjectReportDTO.ProjectStats.builder()
                .projectId(project.getId().toString())
                .projectName(project.getName())
                .status(project.getStatus())
                .totalTasks(totalTasks)
                .completedTasks((int) completedTasks)
                .completionRate(completion)
                .totalHours(totalHours)
                .revenue(BigDecimal.ZERO) // Can be enhanced
                .build());
        }

        Double avgCompletionRate = totalProjects > 0 ? totalCompletionRate / totalProjects : 0.0;

        java.util.List<Task> allTasks = new ArrayList<>();
        for (Project project : projects) {
            allTasks.addAll(taskRepository.findByProjectIdAndDeletedAtIsNull(project.getId()));
        }

        int totalTasks = allTasks.size();
        int completedTasks = (int) allTasks.stream().filter(t -> "DONE".equals(t.getStatus())).count();
        int inProgressTasks = (int) allTasks.stream().filter(t -> "IN_PROGRESS".equals(t.getStatus())).count();
        int todoTasks = (int) allTasks.stream().filter(t -> "TODO".equals(t.getStatus())).count();

        Map<String, Integer> taskStatusCount = new HashMap<>();
        for (Task task : allTasks) {
            String status = task.getStatus();
            taskStatusCount.merge(status, 1, Integer::sum);
        }

        java.util.List<ProjectReportDTO.TaskStatusDistribution> taskDistribution = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : taskStatusCount.entrySet()) {
            double percentage = totalTasks > 0 ? (entry.getValue() * 100.0 / totalTasks) : 0.0;
            taskDistribution.add(ProjectReportDTO.TaskStatusDistribution.builder()
                .status(entry.getKey())
                .count(entry.getValue())
                .percentage(percentage)
                .build());
        }

        return ProjectReportDTO.builder()
            .totalProjects(totalProjects)
            .activeProjects(activeProjects)
            .completedProjects(completedProjects)
            .onHoldProjects(onHoldProjects)
            .totalTasks(totalTasks)
            .completedTasks(completedTasks)
            .inProgressTasks(inProgressTasks)
            .todoTasks(todoTasks)
            .avgCompletionRate(avgCompletionRate)
            .projectStats(projectStats)
            .taskDistribution(taskDistribution)
            .build();
    }

    @Transactional(readOnly = true)
    public TimeReportDTO getTimeReport(UUID companyId, ReportFilterDTO filter) {
        LocalDate[] dates = calculateDateRange(filter);
        LocalDate startDate = dates[0];
        LocalDate endDate = dates[1];

        java.util.List<TimeEntry> timeEntries = timeEntryRepository.findByCompanyIdAndDeletedAtIsNull(companyId);
        
        timeEntries = timeEntries.stream()
            .filter(te -> !te.getDate().isBefore(startDate) && !te.getDate().isAfter(endDate))
            .filter(te -> filter.getProjectId() == null || te.getProjectId().equals(filter.getProjectId()))
            .filter(te -> filter.getUserId() == null || te.getUserId().equals(filter.getUserId()))
            .collect(Collectors.toList());

        int totalMinutes = timeEntries.stream()
            .mapToInt(te -> te.getHours() * 60)
            .sum();
        
        int billableMinutes = timeEntries.stream()
            .filter(te -> Boolean.TRUE.equals(te.getIsBillable()))
            .mapToInt(te -> te.getHours() * 60)
            .sum();
        
        int nonBillableMinutes = totalMinutes - billableMinutes;
        int totalEntries = timeEntries.size();

        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1;
        Double avgHoursPerDay = daysBetween > 0 ? totalMinutes / 60.0 / daysBetween : 0.0;

        java.util.List<TimeReportDTO.DailyTime> dailyTime = new ArrayList<>();
        Map<LocalDate, Integer> totalByDate = new HashMap<>();
        Map<LocalDate, Integer> billableByDate = new HashMap<>();
        Map<LocalDate, Integer> countByDate = new HashMap<>();
        
        for (TimeEntry te : timeEntries) {
            int minutes = te.getHours() * 60;
            totalByDate.merge(te.getDate(), minutes, Integer::sum);
            countByDate.merge(te.getDate(), 1, Integer::sum);
            if (Boolean.TRUE.equals(te.getIsBillable())) {
                billableByDate.merge(te.getDate(), minutes, Integer::sum);
            }
        }
        
        for (Map.Entry<LocalDate, Integer> entry : totalByDate.entrySet()) {
            dailyTime.add(TimeReportDTO.DailyTime.builder()
                .date(entry.getKey())
                .minutes(entry.getValue())
                .billableMinutes(billableByDate.getOrDefault(entry.getKey(), 0))
                .entryCount(countByDate.get(entry.getKey()))
                .build());
        }
        dailyTime.sort(Comparator.comparing(TimeReportDTO.DailyTime::getDate));

        java.util.List<TimeReportDTO.UserTime> timeByUser = new ArrayList<>();
        Map<UUID, Integer> userTotalMap = new HashMap<>();
        Map<UUID, Integer> userBillableMap = new HashMap<>();
        Map<UUID, Integer> userEntryCount = new HashMap<>();
        
        for (TimeEntry te : timeEntries) {
            int minutes = te.getHours() * 60;
            userTotalMap.merge(te.getUserId(), minutes, Integer::sum);
            userEntryCount.merge(te.getUserId(), 1, Integer::sum);
            if (Boolean.TRUE.equals(te.getIsBillable())) {
                userBillableMap.merge(te.getUserId(), minutes, Integer::sum);
            }
        }
        
        for (Map.Entry<UUID, Integer> entry : userTotalMap.entrySet()) {
            Optional<User> userOpt = userRepository.findById(entry.getKey());
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                timeByUser.add(TimeReportDTO.UserTime.builder()
                    .userId(entry.getKey().toString())
                    .userName(user.getName())
                    .totalMinutes(entry.getValue())
                    .billableMinutes(userBillableMap.getOrDefault(entry.getKey(), 0))
                    .entryCount(userEntryCount.get(entry.getKey()))
                    .build());
            }
        }
        timeByUser.sort(Comparator.comparing(TimeReportDTO.UserTime::getTotalMinutes).reversed());

        java.util.List<TimeReportDTO.ProjectTime> timeByProject = new ArrayList<>();
        Map<UUID, Integer> projectTotalMap = new HashMap<>();
        Map<UUID, Integer> projectBillableMap = new HashMap<>();
        Map<UUID, Integer> projectEntryCount = new HashMap<>();
        
        for (TimeEntry te : timeEntries) {
            int minutes = te.getHours() * 60;
            projectTotalMap.merge(te.getProjectId(), minutes, Integer::sum);
            projectEntryCount.merge(te.getProjectId(), 1, Integer::sum);
            if (Boolean.TRUE.equals(te.getIsBillable())) {
                projectBillableMap.merge(te.getProjectId(), minutes, Integer::sum);
            }
        }
        
        for (Map.Entry<UUID, Integer> entry : projectTotalMap.entrySet()) {
            Optional<Project> projectOpt = projectRepository.findById(entry.getKey());
            if (projectOpt.isPresent()) {
                timeByProject.add(TimeReportDTO.ProjectTime.builder()
                    .projectId(entry.getKey().toString())
                    .projectName(projectOpt.get().getName())
                    .totalMinutes(entry.getValue())
                    .billableMinutes(projectBillableMap.getOrDefault(entry.getKey(), 0))
                    .entryCount(projectEntryCount.get(entry.getKey()))
                    .build());
            }
        }
        timeByProject.sort(Comparator.comparing(TimeReportDTO.ProjectTime::getTotalMinutes).reversed());

        java.util.List<TimeReportDTO.TaskTime> timeByTask = new ArrayList<>();
        Map<UUID, Integer> taskTimeMap = new HashMap<>();
        Map<UUID, Integer> taskEntryCount = new HashMap<>();
        
        for (TimeEntry te : timeEntries) {
            if (te.getTaskId() != null) {
                int minutes = te.getHours() * 60;
                taskTimeMap.merge(te.getTaskId(), minutes, Integer::sum);
                taskEntryCount.merge(te.getTaskId(), 1, Integer::sum);
            }
        }
        
        for (Map.Entry<UUID, Integer> entry : taskTimeMap.entrySet()) {
            Optional<Task> taskOpt = taskRepository.findById(entry.getKey());
            if (taskOpt.isPresent()) {
                timeByTask.add(TimeReportDTO.TaskTime.builder()
                    .taskId(entry.getKey().toString())
                    .taskTitle(taskOpt.get().getTitle())
                    .totalMinutes(entry.getValue())
                    .entryCount(taskEntryCount.get(entry.getKey()))
                    .build());
            }
        }
        timeByTask.sort(Comparator.comparing(TimeReportDTO.TaskTime::getTotalMinutes).reversed());

        return TimeReportDTO.builder()
            .totalMinutes(totalMinutes)
            .billableMinutes(billableMinutes)
            .nonBillableMinutes(nonBillableMinutes)
            .totalEntries(totalEntries)
            .avgHoursPerDay(avgHoursPerDay)
            .dailyTime(dailyTime)
            .timeByUser(timeByUser)
            .timeByProject(timeByProject)
            .timeByTask(timeByTask)
            .build();
    }

    @Transactional(readOnly = true)
    public TeamReportDTO getTeamReport(UUID companyId, ReportFilterDTO filter) {
        LocalDate[] dates = calculateDateRange(filter);
        LocalDate startDate = dates[0];
        LocalDate endDate = dates[1];

        java.util.List<User> users = userRepository.findByCompanyId(companyId);
        
        if (filter.getTeamId() != null) {
            users = users.stream()
                .filter(u -> filter.getTeamId().equals(u.getTeamId()))
                .collect(Collectors.toList());
        }

        int totalMembers = users.size();
        int activeMembers = (int) users.stream()
            .filter(u -> Boolean.TRUE.equals(u.getIsActive()))
            .count();

        java.util.List<Task> allTasks = taskRepository.findByCompanyId(companyId);
        java.util.List<TimeEntry> allTimeEntries = timeEntryRepository.findByCompanyIdAndDeletedAtIsNull(companyId);
        
        allTimeEntries = allTimeEntries.stream()
            .filter(te -> !te.getDate().isBefore(startDate) && !te.getDate().isAfter(endDate))
            .collect(Collectors.toList());

        java.util.List<TeamReportDTO.MemberStats> memberStats = new ArrayList<>();
        int totalTasksAssigned = 0;
        int totalTasksCompleted = 0;
        int totalHoursLogged = 0;
        
        for (User user : users) {
            long tasksAssigned = allTasks.stream()
                .filter(t -> user.getId().equals(t.getAssignedTo()))
                .count();
            
            long tasksCompleted = allTasks.stream()
                .filter(t -> user.getId().equals(t.getAssignedTo()))
                .filter(t -> "DONE".equals(t.getStatus()))
                .count();
            
            int hoursLogged = allTimeEntries.stream()
                .filter(te -> user.getId().equals(te.getUserId()))
                .mapToInt(TimeEntry::getHours)
                .sum();
            
            double completionRate = tasksAssigned > 0 ? (tasksCompleted * 100.0 / tasksAssigned) : 0.0;
            double productivityScore = calculateProductivityScore(tasksCompleted, hoursLogged);

            totalTasksAssigned += tasksAssigned;
            totalTasksCompleted += tasksCompleted;
            totalHoursLogged += hoursLogged;

            memberStats.add(TeamReportDTO.MemberStats.builder()
                .userId(user.getId().toString())
                .userName(user.getName())
                .email(user.getEmail())
                .tasksAssigned((int) tasksAssigned)
                .tasksCompleted((int) tasksCompleted)
                .hoursLogged(hoursLogged)
                .completionRate(completionRate)
                .productivityScore(productivityScore)
                .build());
        }
        
        memberStats.sort(Comparator.comparing(TeamReportDTO.MemberStats::getProductivityScore).reversed());

        java.util.List<TeamReportDTO.TeamWorkload> workloadDistribution = new ArrayList<>();
        for (TeamReportDTO.MemberStats member : memberStats) {
            String status;
            if (member.getTasksAssigned() > 15) status = "Overloaded";
            else if (member.getTasksAssigned() >= 5) status = "Balanced";
            else status = "Underutilized";
            
            workloadDistribution.add(TeamReportDTO.TeamWorkload.builder()
                .userId(member.getUserId())
                .userName(member.getUserName())
                .currentWorkload(member.getTasksAssigned())
                .status(status)
                .build());
        }

        Double avgProductivityScore = !memberStats.isEmpty()
            ? memberStats.stream().mapToDouble(TeamReportDTO.MemberStats::getProductivityScore).average().orElse(0.0)
            : 0.0;

        return TeamReportDTO.builder()
            .totalMembers(totalMembers)
            .activeMembers(activeMembers)
            .totalTasksAssigned(totalTasksAssigned)
            .totalTasksCompleted(totalTasksCompleted)
            .totalHoursLogged(totalHoursLogged)
            .avgProductivityScore(avgProductivityScore)
            .memberStats(memberStats)
            .workloadDistribution(workloadDistribution)
            .build();
    }

    // PDF Generation Methods
    
    public byte[] generateRevenuePDF(RevenueReportDTO report, String period) throws DocumentException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4);
        PdfWriter.getInstance(document, baos);
        document.open();

        Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD);
        Paragraph title = new Paragraph("Revenue Report", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(20);
        document.add(title);

        Paragraph periodPara = new Paragraph("Period: " + period, new Font(Font.HELVETICA, 12));
        periodPara.setAlignment(Element.ALIGN_CENTER);
        periodPara.setSpacingAfter(30);
        document.add(periodPara);

        PdfPTable summaryTable = new PdfPTable(4);
        summaryTable.setWidthPercentage(100);
        summaryTable.setSpacingAfter(20);
        
        addSummaryCard(summaryTable, "Total Revenue", formatCurrency(report.getTotalRevenue()));
        addSummaryCard(summaryTable, "Paid Amount", formatCurrency(report.getPaidAmount()));
        addSummaryCard(summaryTable, "Pending Amount", formatCurrency(report.getPendingAmount()));
        addSummaryCard(summaryTable, "Overdue Amount", formatCurrency(report.getOverdueAmount()));
        
        document.add(summaryTable);

        if (!report.getRevenueByClient().isEmpty()) {
            Paragraph clientTitle = new Paragraph("Revenue by Client", new Font(Font.HELVETICA, 14, Font.BOLD));
            clientTitle.setSpacingBefore(20);
            clientTitle.setSpacingAfter(10);
            document.add(clientTitle);

            PdfPTable clientTable = new PdfPTable(3);
            clientTable.setWidthPercentage(100);
            clientTable.setWidths(new float[]{3, 2, 2});
            
            addTableHeader(clientTable, new String[]{"Client", "Revenue", "Invoices"});
            
            for (RevenueReportDTO.ClientRevenue cr : report.getRevenueByClient()) {
                addTableRow(clientTable, new String[]{
                    cr.getClientName(),
                    formatCurrency(cr.getRevenue()),
                    String.valueOf(cr.getInvoiceCount())
                });
            }
            
            document.add(clientTable);
        }

        document.close();
        return baos.toByteArray();
    }

    public byte[] generateProjectPDF(ProjectReportDTO report, String period) throws DocumentException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4);
        PdfWriter.getInstance(document, baos);
        document.open();

        Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD);
        Paragraph title = new Paragraph("Project Report", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(20);
        document.add(title);

        Paragraph periodPara = new Paragraph("Period: " + period, new Font(Font.HELVETICA, 12));
        periodPara.setAlignment(Element.ALIGN_CENTER);
        periodPara.setSpacingAfter(30);
        document.add(periodPara);

        PdfPTable summaryTable = new PdfPTable(4);
        summaryTable.setWidthPercentage(100);
        summaryTable.setSpacingAfter(20);
        
        addSummaryCard(summaryTable, "Total Projects", String.valueOf(report.getTotalProjects()));
        addSummaryCard(summaryTable, "Active", String.valueOf(report.getActiveProjects()));
        addSummaryCard(summaryTable, "Completed", String.valueOf(report.getCompletedProjects()));
        addSummaryCard(summaryTable, "Avg Completion", String.format("%.1f%%", report.getAvgCompletionRate()));
        
        document.add(summaryTable);

        if (!report.getProjectStats().isEmpty()) {
            Paragraph statsTitle = new Paragraph("Project Statistics", new Font(Font.HELVETICA, 14, Font.BOLD));
            statsTitle.setSpacingBefore(20);
            statsTitle.setSpacingAfter(10);
            document.add(statsTitle);

            PdfPTable statsTable = new PdfPTable(5);
            statsTable.setWidthPercentage(100);
            statsTable.setWidths(new float[]{3, 2, 2, 2, 2});
            
            addTableHeader(statsTable, new String[]{"Project", "Status", "Total Tasks", "Completed", "Completion %"});
            
            for (ProjectReportDTO.ProjectStats ps : report.getProjectStats()) {
                addTableRow(statsTable, new String[]{
                    ps.getProjectName(),
                    ps.getStatus(),
                    String.valueOf(ps.getTotalTasks()),
                    String.valueOf(ps.getCompletedTasks()),
                    String.format("%.1f%%", ps.getCompletionRate())
                });
            }
            
            document.add(statsTable);
        }

        document.close();
        return baos.toByteArray();
    }

    public byte[] generateTimePDF(TimeReportDTO report, String period) throws DocumentException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4);
        PdfWriter.getInstance(document, baos);
        document.open();

        Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD);
        Paragraph title = new Paragraph("Time Tracking Report", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(20);
        document.add(title);

        Paragraph periodPara = new Paragraph("Period: " + period, new Font(Font.HELVETICA, 12));
        periodPara.setAlignment(Element.ALIGN_CENTER);
        periodPara.setSpacingAfter(30);
        document.add(periodPara);

        PdfPTable summaryTable = new PdfPTable(3);
        summaryTable.setWidthPercentage(100);
        summaryTable.setSpacingAfter(20);
        
        addSummaryCard(summaryTable, "Total Hours", formatHours(report.getTotalMinutes()));
        addSummaryCard(summaryTable, "Billable Hours", formatHours(report.getBillableMinutes()));
        addSummaryCard(summaryTable, "Avg Hours/Day", String.format("%.2f", report.getAvgHoursPerDay()));
        
        document.add(summaryTable);

        if (!report.getTimeByUser().isEmpty()) {
            Paragraph userTitle = new Paragraph("Time by User", new Font(Font.HELVETICA, 14, Font.BOLD));
            userTitle.setSpacingBefore(20);
            userTitle.setSpacingAfter(10);
            document.add(userTitle);

            PdfPTable userTable = new PdfPTable(3);
            userTable.setWidthPercentage(100);
            userTable.setWidths(new float[]{3, 2, 2});
            
            addTableHeader(userTable, new String[]{"User", "Hours", "Entries"});
            
            for (TimeReportDTO.UserTime ut : report.getTimeByUser()) {
                addTableRow(userTable, new String[]{
                    ut.getUserName(),
                    formatHours(ut.getTotalMinutes()),
                    String.valueOf(ut.getEntryCount())
                });
            }
            
            document.add(userTable);
        }

        document.close();
        return baos.toByteArray();
    }

    public byte[] generateTeamPDF(TeamReportDTO report, String period) throws DocumentException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4);
        PdfWriter.getInstance(document, baos);
        document.open();

        Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD);
        Paragraph title = new Paragraph("Team Productivity Report", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(20);
        document.add(title);

        Paragraph periodPara = new Paragraph("Period: " + period, new Font(Font.HELVETICA, 12));
        periodPara.setAlignment(Element.ALIGN_CENTER);
        periodPara.setSpacingAfter(30);
        document.add(periodPara);

        PdfPTable summaryTable = new PdfPTable(3);
        summaryTable.setWidthPercentage(100);
        summaryTable.setSpacingAfter(20);
        
        addSummaryCard(summaryTable, "Team Members", String.valueOf(report.getTotalMembers()));
        addSummaryCard(summaryTable, "Tasks Completed", String.valueOf(report.getTotalTasksCompleted()));
        addSummaryCard(summaryTable, "Hours Logged", String.valueOf(report.getTotalHoursLogged()));
        
        document.add(summaryTable);

        if (!report.getMemberStats().isEmpty()) {
            Paragraph memberTitle = new Paragraph("Member Performance", new Font(Font.HELVETICA, 14, Font.BOLD));
            memberTitle.setSpacingBefore(20);
            memberTitle.setSpacingAfter(10);
            document.add(memberTitle);

            PdfPTable memberTable = new PdfPTable(5);
            memberTable.setWidthPercentage(100);
            memberTable.setWidths(new float[]{3, 2, 2, 2, 2});
            
            addTableHeader(memberTable, new String[]{"Member", "Assigned", "Completed", "Hours", "Productivity"});
            
            for (TeamReportDTO.MemberStats ms : report.getMemberStats()) {
                addTableRow(memberTable, new String[]{
                    ms.getUserName(),
                    String.valueOf(ms.getTasksAssigned()),
                    String.valueOf(ms.getTasksCompleted()),
                    String.valueOf(ms.getHoursLogged()),
                    String.format("%.1f", ms.getProductivityScore())
                });
            }
            
            document.add(memberTable);
        }

        document.close();
        return baos.toByteArray();
    }

    // Helper Methods
    
    private LocalDate[] calculateDateRange(ReportFilterDTO filter) {
        if (filter.getStartDate() != null && filter.getEndDate() != null) {
            return new LocalDate[]{filter.getStartDate(), filter.getEndDate()};
        }

        LocalDate endDate = LocalDate.now();
        LocalDate startDate;

        switch (filter.getPeriod() != null ? filter.getPeriod() : "month") {
            case "7days":
                startDate = endDate.minusDays(6);
                break;
            case "month":
                startDate = endDate.minusMonths(1);
                break;
            case "quarter":
                startDate = endDate.minusMonths(3);
                break;
            case "6months":
                startDate = endDate.minusMonths(6);
                break;
            case "year":
                startDate = endDate.minusYears(1);
                break;
            case "overall":
                startDate = LocalDate.of(2000, 1, 1);
                break;
            default:
                startDate = endDate.minusMonths(1);
        }

        return new LocalDate[]{startDate, endDate};
    }

    private double calculateProductivityScore(long tasksCompleted, int hoursLogged) {
        if (hoursLogged == 0) return 0.0;
        return (tasksCompleted * 10.0) / (hoursLogged / 8.0);
    }

    private void addSummaryCard(PdfPTable table, String label, String value) {
        PdfPCell cell = new PdfPCell();
        cell.setPadding(10);
        cell.setBackgroundColor(new Color(240, 240, 240));
        
        Paragraph labelPara = new Paragraph(label, new Font(Font.HELVETICA, 10, Font.NORMAL, Color.GRAY));
        Paragraph valuePara = new Paragraph(value, new Font(Font.HELVETICA, 14, Font.BOLD));
        
        cell.addElement(labelPara);
        cell.addElement(valuePara);
        table.addCell(cell);
    }

    private void addTableHeader(PdfPTable table, String[] headers) {
        Font headerFont = new Font(Font.HELVETICA, 10, Font.BOLD, Color.WHITE);
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, headerFont));
            cell.setBackgroundColor(new Color(70, 130, 180));
            cell.setPadding(8);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }
    }

    private void addTableRow(PdfPTable table, String[] values) {
        Font cellFont = new Font(Font.HELVETICA, 9);
        for (String value : values) {
            PdfPCell cell = new PdfPCell(new Phrase(value, cellFont));
            cell.setPadding(6);
            table.addCell(cell);
        }
    }

    private String formatCurrency(BigDecimal amount) {
        return "$" + amount.setScale(2, RoundingMode.HALF_UP).toString();
    }

    private String formatHours(int minutes) {
        double hours = minutes / 60.0;
        return String.format("%.2f hrs", hours);
    }
}
