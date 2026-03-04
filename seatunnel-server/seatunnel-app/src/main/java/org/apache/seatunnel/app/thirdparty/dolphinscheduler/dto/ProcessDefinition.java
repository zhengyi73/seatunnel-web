package org.apache.seatunnel.app.thirdparty.dolphinscheduler.dto;

import lombok.Data;

@Data
public class ProcessDefinition {
    private Long id;
    private Long code;
    private String name;
    private String description;
    private String projectCode;
    private int tenantId;
}
