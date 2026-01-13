package com.itops.controller;

import com.itops.dto.*;
import com.itops.security.JwtUtil;
import com.itops.service.ReportService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Slf4j
public class ReportController {

    private final ReportService reportService;
    private final JwtUtil jwtUtil;

    private UUID getCompanyIdFromRequest(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
            return jwtUtil.extractCompanyId(token);
        }
        throw new RuntimeException("No valid token found");
    }

    private String getRoleFromRequest(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
            return jwtUtil.extractRole(token);
        }
        throw new RuntimeException("No valid token found");
    }

    @PostMapping("/revenue")
    public ResponseEntity<RevenueReportDTO> getRevenueReport(
            HttpServletRequest request,
            @RequestBody ReportFilterDTO filter) {
        UUID companyId = getCompanyIdFromRequest(request);
        String role = getRoleFromRequest(request);
        
        // Only TOP_USER, SUPER_USER, and ADMIN can access revenue reports
        if ("CLIENT".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        log.info("Getting revenue report for company: {} (role: {})", companyId, role);
        RevenueReportDTO report = reportService.getRevenueReport(companyId, filter);
        return ResponseEntity.ok(report);
    }

    @PostMapping("/projects")
    public ResponseEntity<ProjectReportDTO> getProjectReport(
            HttpServletRequest request,
            @RequestBody ReportFilterDTO filter) {
        UUID companyId = getCompanyIdFromRequest(request);
        String role = getRoleFromRequest(request);
        
        // Only TOP_USER, SUPER_USER, and ADMIN can access project reports
        if ("CLIENT".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        log.info("Getting project report for company: {} (role: {})", companyId, role);
        ProjectReportDTO report = reportService.getProjectReport(companyId, filter);
        return ResponseEntity.ok(report);
    }

    @PostMapping("/time")
    public ResponseEntity<TimeReportDTO> getTimeReport(
            HttpServletRequest request,
            @RequestBody ReportFilterDTO filter) {
        UUID companyId = getCompanyIdFromRequest(request);
        
        log.info("Getting time report for company: {}", companyId);
        TimeReportDTO report = reportService.getTimeReport(companyId, filter);
        return ResponseEntity.ok(report);
    }

    @PostMapping("/team")
    public ResponseEntity<TeamReportDTO> getTeamReport(
            HttpServletRequest request,
            @RequestBody ReportFilterDTO filter) {
        UUID companyId = getCompanyIdFromRequest(request);
        String role = getRoleFromRequest(request);
        
        // Only TOP_USER, SUPER_USER, and ADMIN can access team reports
        if ("CLIENT".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        log.info("Getting team report for company: {} (role: {})", companyId, role);
        TeamReportDTO report = reportService.getTeamReport(companyId, filter);
        return ResponseEntity.ok(report);
    }

    @PostMapping("/revenue/pdf")
    public ResponseEntity<byte[]> downloadRevenuePDF(
            HttpServletRequest request,
            @RequestBody ReportFilterDTO filter) {
        UUID companyId = getCompanyIdFromRequest(request);
        String role = getRoleFromRequest(request);
        
        // Only TOP_USER, SUPER_USER, and ADMIN can download revenue PDF
        if ("CLIENT".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        log.info("Generating revenue report PDF for company: {}", companyId);
        
        RevenueReportDTO report = reportService.getRevenueReport(companyId, filter);
        String period = filter.getPeriod() != null ? filter.getPeriod() : "month";
        byte[] pdfBytes = reportService.generateRevenuePDF(report, period);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "revenue-report.pdf");
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }

    @PostMapping("/projects/pdf")
    public ResponseEntity<byte[]> downloadProjectsPDF(
            HttpServletRequest request,
            @RequestBody ReportFilterDTO filter) {
        UUID companyId = getCompanyIdFromRequest(request);
        String role = getRoleFromRequest(request);
        
        // Only TOP_USER, SUPER_USER, and ADMIN can download project PDF
        if ("CLIENT".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        log.info("Generating projects report PDF for company: {}", companyId);
        
        ProjectReportDTO report = reportService.getProjectReport(companyId, filter);
        String period = filter.getPeriod() != null ? filter.getPeriod() : "month";
        byte[] pdfBytes = reportService.generateProjectPDF(report, period);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "projects-report.pdf");
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }

    @PostMapping("/time/pdf")
    public ResponseEntity<byte[]> downloadTimePDF(
            HttpServletRequest request,
            @RequestBody ReportFilterDTO filter) {
        UUID companyId = getCompanyIdFromRequest(request);
        
        log.info("Generating time report PDF for company: {}", companyId);
        
        TimeReportDTO report = reportService.getTimeReport(companyId, filter);
        String period = filter.getPeriod() != null ? filter.getPeriod() : "month";
        byte[] pdfBytes = reportService.generateTimePDF(report, period);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "time-report.pdf");
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }

    @PostMapping("/team/pdf")
    public ResponseEntity<byte[]> downloadTeamPDF(
            HttpServletRequest request,
            @RequestBody ReportFilterDTO filter) {
        UUID companyId = getCompanyIdFromRequest(request);
        String role = getRoleFromRequest(request);
        
        // Only TOP_USER, SUPER_USER, and ADMIN can download team PDF
        if ("CLIENT".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        log.info("Generating team report PDF for company: {}", companyId);
        
        TeamReportDTO report = reportService.getTeamReport(companyId, filter);
        String period = filter.getPeriod() != null ? filter.getPeriod() : "month";
        byte[] pdfBytes = reportService.generateTeamPDF(report, period);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "team-report.pdf");
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }
}
