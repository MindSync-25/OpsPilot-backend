package com.itops.dto;

public class NotificationType {
    // Project notifications
    public static final String PROJECT_CREATED = "PROJECT_CREATED";
    public static final String PROJECT_UPDATED = "PROJECT_UPDATED";
    public static final String PROJECT_MEMBER_ADDED = "PROJECT_MEMBER_ADDED";
    public static final String PROJECT_MEMBER_REMOVED = "PROJECT_MEMBER_REMOVED";
    public static final String PROJECT_STATUS_CHANGED = "PROJECT_STATUS_CHANGED";
    
    // Task notifications
    public static final String TASK_ASSIGNED = "TASK_ASSIGNED";
    public static final String TASK_UNASSIGNED = "TASK_UNASSIGNED";
    public static final String TASK_STATUS_CHANGED = "TASK_STATUS_CHANGED";
    public static final String TASK_DUE_DATE_CHANGED = "TASK_DUE_DATE_CHANGED";
    public static final String TASK_COMMENTED = "TASK_COMMENTED";
    public static final String TASK_PRIORITY_CHANGED = "TASK_PRIORITY_CHANGED";
    
    // Subtask notifications
    public static final String SUBTASK_ASSIGNED = "SUBTASK_ASSIGNED";
    public static final String SUBTASK_UNASSIGNED = "SUBTASK_UNASSIGNED";
    public static final String SUBTASK_STATUS_CHANGED = "SUBTASK_STATUS_CHANGED";
    public static final String SUBTASK_COMPLETED = "SUBTASK_COMPLETED";
    public static final String SUBTASK_COMMENTED = "SUBTASK_COMMENTED";
    
    // Phase notifications
    public static final String PHASE_CREATED = "PHASE_CREATED";
    public static final String PHASE_STATUS_CHANGED = "PHASE_STATUS_CHANGED";
    
    // Client/CRM notifications
    public static final String CLIENT_ASSIGNED = "CLIENT_ASSIGNED";
    public static final String CRM_DEAL_CREATED = "CRM_DEAL_CREATED";
    public static final String CRM_DEAL_UPDATED = "CRM_DEAL_UPDATED";
    public static final String CRM_DEAL_WON = "CRM_DEAL_WON";
    public static final String CRM_DEAL_LOST = "CRM_DEAL_LOST";
    
    // Invoice notifications
    public static final String INVOICE_CREATED = "INVOICE_CREATED";
    public static final String INVOICE_APPROVED = "INVOICE_APPROVED";
    public static final String INVOICE_REJECTED = "INVOICE_REJECTED";
    public static final String INVOICE_PAID = "INVOICE_PAID";
    
    // Timesheet notifications
    public static final String TIMESHEET_SUBMITTED = "TIMESHEET_SUBMITTED";
    public static final String TIMESHEET_APPROVED = "TIMESHEET_APPROVED";
    public static final String TIMESHEET_REJECTED = "TIMESHEET_REJECTED";
    
    // Leave notifications
    public static final String LEAVE_REQUEST_CREATED = "LEAVE_REQUEST_CREATED";
    public static final String LEAVE_REQUEST_APPROVED = "LEAVE_REQUEST_APPROVED";
    public static final String LEAVE_REQUEST_REJECTED = "LEAVE_REQUEST_REJECTED";
    
    // Team notifications
    public static final String TEAM_MEMBER_ADDED = "TEAM_MEMBER_ADDED";
    public static final String TEAM_MEMBER_REMOVED = "TEAM_MEMBER_REMOVED";
    public static final String TEAM_CREATED = "TEAM_CREATED";
    public static final String TEAM_UPDATED = "TEAM_UPDATED";
    
    // Comment notifications
    public static final String COMMENT_ADDED = "COMMENT_ADDED";
    public static final String COMMENT_MENTION = "COMMENT_MENTION";
    
    // Attachment notifications
    public static final String ATTACHMENT_ADDED = "ATTACHMENT_ADDED";
    
    // Client notifications
    public static final String CLIENT_CREATED = "CLIENT_CREATED";
    public static final String CLIENT_UPDATED = "CLIENT_UPDATED";
}
