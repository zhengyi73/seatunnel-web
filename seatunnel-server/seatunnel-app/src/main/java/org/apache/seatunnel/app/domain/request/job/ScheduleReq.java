package org.apache.seatunnel.app.domain.request.job;

import lombok.Data;

@Data
public class ScheduleReq {
    private Long jobDefineId;
    private String cronExpression;
    private String startTime;
    private String endTime;
    private String timezoneId = "Asia/Shanghai";
    private Integer retryTimes = 0;
    private Integer retryInterval = 1;
}
