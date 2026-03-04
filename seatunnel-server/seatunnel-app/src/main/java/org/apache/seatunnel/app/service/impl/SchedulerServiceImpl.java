package org.apache.seatunnel.app.service.impl;

import org.apache.seatunnel.app.dal.dao.IJobDefinitionDao;
import org.apache.seatunnel.app.dal.dao.IScriptJobApplyDao;
import org.apache.seatunnel.app.dal.entity.JobDefinition;
import org.apache.seatunnel.app.domain.request.job.ScheduleReq;
import org.apache.seatunnel.app.service.ISchedulerService;
import org.apache.seatunnel.app.thirdparty.dolphinscheduler.client.DolphinSchedulerClient;
import org.apache.seatunnel.app.thirdparty.dolphinscheduler.config.DolphinSchedulerProperties;
import org.apache.seatunnel.app.thirdparty.dolphinscheduler.dto.ProcessDefinition;
import org.apache.seatunnel.app.thirdparty.dolphinscheduler.dto.Project;
import org.apache.seatunnel.app.thirdparty.dolphinscheduler.dto.Schedule;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Resource;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class SchedulerServiceImpl implements ISchedulerService {

    @Resource private IJobDefinitionDao jobDefinitionDao;

    @Resource private IScriptJobApplyDao scriptJobApplyDao;

    @Resource private DolphinSchedulerClient dolphinSchedulerClient;

    @Resource private DolphinSchedulerProperties properties;

    @Resource private ObjectMapper objectMapper;

    @Override
    public void createSchedule(ScheduleReq req) {
        JobDefinition job = jobDefinitionDao.getJob(req.getJobDefineId());

        // 1. Ensure project exists
        Project project = dolphinSchedulerClient.getOrCreateProject();

        // 2. Generate task code from DolphinScheduler
        Long taskCode = dolphinSchedulerClient.genTaskCode(project.getCode());

        // 3. Build SHELL Task JSON for DolphinScheduler
        // Uses curl to call the SeaTunnel Web execute API
        String taskName = "sync_task_" + job.getId();
        String curlScript =
                "curl -X POST '"
                        + properties.getCallbackUrl()
                        + "/seatunnel/api/v1/job/executor/execute?jobDefineId="
                        + req.getJobDefineId()
                        + "' -H 'Content-Type: application/json'";
        String escapedScript = curlScript.replace("\"", "\\\"");
        String taskDefinitionJson =
                "[{\"code\":"
                        + taskCode
                        + ",\"name\":\""
                        + taskName
                        + "\",\"version\":1,\"taskType\":\"SHELL\",\"taskParams\":{\"rawScript\":\""
                        + escapedScript
                        + "\",\"localParams\":[],\"resourceList\":[]},\"flag\":\"YES\",\"isCache\":\"NO\",\"taskPriority\":\"MEDIUM\",\"workerGroup\":\"default\",\"environmentCode\":-1,\"failRetryTimes\":"
                        + req.getRetryTimes()
                        + ",\"failRetryInterval\":"
                        + req.getRetryInterval()
                        + ",\"timeoutFlag\":\"CLOSE\",\"timeoutNotifyStrategy\":\"WARN\",\"timeout\":0,\"delayTime\":0,\"taskExecuteType\":\"BATCH\"}]";
        String taskRelationJson =
                "[{\"name\":\"\",\"preTaskCode\":0,\"preTaskVersion\":0,\"postTaskCode\":"
                        + taskCode
                        + ",\"postTaskVersion\":1,\"conditionType\":\"NONE\",\"conditionParams\":{}}]";
        String locationsJson = "[{\"taskCode\":" + taskCode + ",\"x\":100,\"y\":100}]";

        // 4. Create or Update Process Definition
        String processName = "SeaTunnel_Job_" + job.getId();
        ProcessDefinition processDef =
                dolphinSchedulerClient.queryProcessDefinitionByName(project.getCode(), processName);
        if (processDef != null) {
            dolphinSchedulerClient.offlineProcessDefinition(
                    project.getCode(), processDef.getCode());
            dolphinSchedulerClient.updateProcessDefinition(
                    project.getCode(),
                    processDef.getCode(),
                    processName,
                    taskDefinitionJson,
                    taskRelationJson,
                    locationsJson);
        } else {
            processDef =
                    dolphinSchedulerClient.createProcessDefinition(
                            project.getCode(),
                            processName,
                            taskDefinitionJson,
                            taskRelationJson,
                            locationsJson);
        }

        // Online Process Definition
        dolphinSchedulerClient.onlineProcessDefinition(project.getCode(), processDef.getCode());

        // 5. Build Schedule JSON
        try {
            String startTime = req.getStartTime();
            String endTime = req.getEndTime();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

            if (startTime == null || startTime.trim().isEmpty()) {
                startTime = sdf.format(new java.util.Date());
            }
            if (endTime == null || endTime.trim().isEmpty()) {
                // Default to 100 years later
                java.util.Calendar calendar = java.util.Calendar.getInstance();
                calendar.add(java.util.Calendar.YEAR, 100);
                endTime = sdf.format(calendar.getTime());
            }

            Map<String, Object> scheduleMap = new HashMap<>();
            scheduleMap.put("startTime", startTime);
            scheduleMap.put("endTime", endTime);
            scheduleMap.put("crontab", normalizeCrontab(req.getCronExpression()));
            scheduleMap.put("timezoneId", req.getTimezoneId());
            String scheduleJson = objectMapper.writeValueAsString(scheduleMap);

            log.info(
                    "Create or Update schedule request - processDefinitionCode: {}, scheduleJson: {}",
                    processDef.getCode(),
                    scheduleJson);

            // 6. Create or Update Schedule
            Schedule dsSchedule =
                    dolphinSchedulerClient.queryScheduleByProcessCode(
                            project.getCode(), processDef.getCode());
            if (dsSchedule != null) {
                dolphinSchedulerClient.updateScheduleState(
                        project.getCode(), dsSchedule.getId().intValue(), "OFFLINE");
                dolphinSchedulerClient.updateSchedule(
                        project.getCode(), dsSchedule.getId().intValue(), scheduleJson);
            } else {
                dsSchedule =
                        dolphinSchedulerClient.createSchedule(
                                project.getCode(), processDef.getCode(), scheduleJson);
            }

            // 7. Online Schedule (Activate)
            dolphinSchedulerClient.updateScheduleState(
                    project.getCode(), dsSchedule.getId().intValue(), "ONLINE");

            // Optional: Save mapping in local database here if needed.
            // (e.g., Update ScriptJobApply or SchedulerConfig)
        } catch (Exception e) {
            throw new RuntimeException("Failed to create/update schedule in DolphinScheduler", e);
        }
    }

    @Override
    public ScheduleReq getSchedule(Long jobDefineId) {
        try {
            Project project = dolphinSchedulerClient.getOrCreateProject();
            String processName = "SeaTunnel_Job_" + jobDefineId;
            // Query the process definition by searching in DS
            ProcessDefinition processDef =
                    dolphinSchedulerClient.queryProcessDefinitionByName(
                            project.getCode(), processName);
            if (processDef == null) {
                return null;
            }
            // Query the schedule for this process definition
            Schedule schedule =
                    dolphinSchedulerClient.queryScheduleByProcessCode(
                            project.getCode(), processDef.getCode());
            if (schedule == null) {
                return null;
            }
            ScheduleReq result = new ScheduleReq();
            result.setJobDefineId(jobDefineId);
            result.setCronExpression(schedule.getCrontab());
            if (schedule.getStartTime() != null) {
                result.setStartTime(
                        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                                .format(schedule.getStartTime()));
            }
            if (schedule.getEndTime() != null) {
                result.setEndTime(
                        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(schedule.getEndTime()));
            }
            result.setTimezoneId(schedule.getTimezoneId());
            return result;
        } catch (Exception e) {
            log.warn("Failed to get schedule for jobDefineId: {}", jobDefineId, e);
            return null;
        }
    }

    @Override
    public void updateSchedule(ScheduleReq req) {
        // Implementation for updating an existing schedule
    }

    @Override
    public void deleteSchedule(Long jobDefineId) {
        // Implementation for deleting an existing schedule
    }

    @Override
    public void onlineSchedule(Long jobDefineId) {
        // Implementation for putting schedule ONLINE
    }

    @Override
    public void offlineSchedule(Long jobDefineId) {
        // Implementation for putting schedule OFFLINE
    }

    private String normalizeCrontab(String crontab) {
        if (crontab == null || crontab.trim().isEmpty()) {
            return crontab;
        }
        String[] parts = crontab.trim().split("\\s+");
        if (parts.length == 5) {
            // Standard Unix Cron: min hour dom month dow
            // Convert to Quartz: 0 min hour dom month dow
            String dom = parts[2];
            String dow = parts[4];
            // In Quartz, only one of Day-of-Month or Day-of-Week can be '*'
            // The other must be '?'
            if ("*".equals(dom) && "*".equals(dow)) {
                dow = "?";
            } else if ("*".equals(dom)) {
                // If DOW is specified, DOM must be '?'
                dom = "?";
            } else if ("*".equals(dow)) {
                // If DOM is specified, DOW must be '?'
                dow = "?";
            }
            return String.format("0 %s %s %s %s %s", parts[0], parts[1], dom, parts[3], dow);
        }
        return crontab;
    }
}
