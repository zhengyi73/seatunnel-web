package org.apache.seatunnel.app.thirdparty.dolphinscheduler.dto;

import lombok.Data;

import java.util.Date;

@Data
public class Schedule {
    private Long id;
    private Long processDefinitionCode;
    private String crontab;
    private Date startTime;
    private Date endTime;
    private String timezoneId;
    private String failureStrategy;
    private String warningType;
    private int warningGroupId;
    private String processInstancePriority;
    private String workerGroup;
    private Long environmentCode;
    private String releaseState;
}
