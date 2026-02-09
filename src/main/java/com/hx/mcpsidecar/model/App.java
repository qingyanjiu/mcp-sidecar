package com.hx.mcpsidecar.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * dify app bean
 */
@Data
@TableName("apps")
public class App {

    @TableId
    private UUID id;

    private UUID tenantId;

    private String name;

    private String mode;

    private String icon;

    private String iconBackground;

    private UUID appModelConfigId;

    private String status;

    private Boolean enableSite;

    private Boolean enableApi;

    private Integer apiRpm;

    private Integer apiRph;

    private Boolean isDemo;

    private Boolean isPublic;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Boolean isUniversal;

    private UUID workflowId;

    private String description;

    private String tracing;

    private Integer maxActiveRequests;

    private String iconType;

    private UUID createdBy;

    private UUID updatedBy;

    private Boolean useIconAsAnswerIcon;
}