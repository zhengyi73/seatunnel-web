package org.apache.seatunnel.app.thirdparty.dolphinscheduler.client;

import org.apache.seatunnel.app.thirdparty.dolphinscheduler.config.DolphinSchedulerProperties;
import org.apache.seatunnel.app.thirdparty.dolphinscheduler.dto.DsResult;
import org.apache.seatunnel.app.thirdparty.dolphinscheduler.dto.ProcessDefinition;
import org.apache.seatunnel.app.thirdparty.dolphinscheduler.dto.Project;
import org.apache.seatunnel.app.thirdparty.dolphinscheduler.dto.Schedule;
import org.apache.seatunnel.server.common.SeatunnelErrorEnum;
import org.apache.seatunnel.server.common.SeatunnelException;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Resource;

@Slf4j
@Component
public class DolphinSchedulerClient {

    @Resource private DolphinSchedulerProperties properties;

    @Resource private RestTemplate restTemplate;

    @Resource private ObjectMapper objectMapper;

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("token", properties.getToken());
        return headers;
    }

    public Project queryProjectByName(String projectName) {
        // Use the paged list API with searchVal to find the project
        String url =
                String.format(
                        "%s/projects?searchVal=%s&pageNo=1&pageSize=10",
                        properties.getUrl(), projectName);
        HttpEntity<String> entity = new HttpEntity<>(buildHeaders());

        try {
            ResponseEntity<String> response =
                    restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            log.info("Query project list response: {}", response.getBody());
            com.fasterxml.jackson.databind.JsonNode root =
                    objectMapper.readTree(response.getBody());
            if (root.path("code").asInt() == 0) {
                com.fasterxml.jackson.databind.JsonNode totalList =
                        root.path("data").path("totalList");
                if (totalList.isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode node : totalList) {
                        String name = node.path("name").asText();
                        if (projectName.equals(name)) {
                            return objectMapper.treeToValue(node, Project.class);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Query project by name failed, projectName: {}", projectName, e);
        }
        return null;
    }

    public Project getOrCreateProject() {
        Project project = queryProjectByName(properties.getProjectName());
        if (project != null) {
            return project;
        }

        String url = String.format("%s/projects", properties.getUrl());
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("projectName", properties.getProjectName());
        params.add("description", "Created by SeaTunnel Web");

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(params, buildHeaders());
        try {
            ResponseEntity<String> response =
                    restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            log.info("Create project response: {}", response.getBody());
            DsResult<Project> result =
                    objectMapper.readValue(
                            response.getBody(), new TypeReference<DsResult<Project>>() {});
            if (result.isSuccess()) {
                return result.getData();
            } else if (result.getCode() != null && result.getCode() == 10019) {
                // Project already exists — re-query to get the existing project info
                log.info("Project already exists, re-querying: {}", properties.getProjectName());
                Project existing = queryProjectByName(properties.getProjectName());
                if (existing != null) {
                    return existing;
                }
                // If still null, try to use the data from the create response
                if (result.getData() != null) {
                    return result.getData();
                }
                throw new SeatunnelException(
                        SeatunnelErrorEnum.UNEXPECTED_RETURN_CODE,
                        result.getCode().toString(),
                        result.getMsg());
            } else {
                throw new SeatunnelException(
                        SeatunnelErrorEnum.UNEXPECTED_RETURN_CODE,
                        result.getCode().toString(),
                        result.getMsg());
            }
        } catch (SeatunnelException e) {
            throw e;
        } catch (Exception e) {
            log.error("Create project failed, projectName: {}", properties.getProjectName(), e);
            throw new SeatunnelException(SeatunnelErrorEnum.HTTP_REQUEST_FAILED, url);
        }
    }

    public ProcessDefinition queryProcessDefinitionByName(Long projectCode, String processName) {
        String url =
                String.format(
                        "%s/projects/%d/process-definition?searchVal=%s&pageNo=1&pageSize=10",
                        properties.getUrl(), projectCode, processName);
        HttpEntity<String> entity = new HttpEntity<>(buildHeaders());
        try {
            ResponseEntity<String> response =
                    restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            com.fasterxml.jackson.databind.JsonNode root =
                    objectMapper.readTree(response.getBody());
            if (root.path("code").asInt() == 0) {
                com.fasterxml.jackson.databind.JsonNode totalList =
                        root.path("data").path("totalList");
                if (totalList.isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode node : totalList) {
                        String name = node.path("name").asText();
                        if (processName.equals(name)) {
                            return objectMapper.treeToValue(node, ProcessDefinition.class);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Query process definition by name failed, name: {}", processName, e);
        }
        return null;
    }

    /** Generate unique task codes from DolphinScheduler */
    public Long genTaskCode(Long projectCode) {
        String url =
                String.format(
                        "%s/projects/%d/task-definition/gen-task-codes?genNum=1",
                        properties.getUrl(), projectCode);
        HttpEntity<String> entity = new HttpEntity<>(buildHeaders());
        try {
            ResponseEntity<String> response =
                    restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            log.info("Gen task codes response: {}", response.getBody());
            com.fasterxml.jackson.databind.JsonNode root =
                    objectMapper.readTree(response.getBody());
            if (root.path("code").asInt() == 0) {
                com.fasterxml.jackson.databind.JsonNode data = root.path("data");
                if (data.isArray() && data.size() > 0) {
                    return data.get(0).asLong();
                }
            }
        } catch (Exception e) {
            log.error("Gen task codes failed", e);
        }
        // Fallback: generate a snowflake-like code
        return System.currentTimeMillis() * 10000 + (long) (Math.random() * 10000);
    }

    public ProcessDefinition createProcessDefinition(
            Long projectCode,
            String name,
            String taskDefinitionJson,
            String taskRelationJson,
            String locationsJson) {
        String url =
                String.format(
                        "%s/projects/%d/process-definition", properties.getUrl(), projectCode);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("name", name);
        params.add("taskDefinitionJson", taskDefinitionJson);
        params.add("taskRelationJson", taskRelationJson);
        params.add("tenantCode", properties.getTenant());
        params.add("description", "Generated by SeaTunnel Web");
        params.add("locations", locationsJson);
        params.add("executionType", "PARALLEL");
        params.add("globalParams", "[]");

        log.info(
                "Create process definition request - name: {}, taskDefinitionJson: {}, taskRelationJson: {}",
                name,
                taskDefinitionJson,
                taskRelationJson);

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(params, buildHeaders());
        try {
            ResponseEntity<String> response =
                    restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            log.info("Create process definition response: {}", response.getBody());
            DsResult<ProcessDefinition> result =
                    objectMapper.readValue(
                            response.getBody(),
                            new TypeReference<DsResult<ProcessDefinition>>() {});
            if (result.isSuccess()) {
                return result.getData();
            } else {
                throw new SeatunnelException(
                        SeatunnelErrorEnum.UNEXPECTED_RETURN_CODE,
                        result.getCode().toString(),
                        result.getMsg());
            }
        } catch (SeatunnelException e) {
            throw e;
        } catch (Exception e) {
            log.error("Create process definition failed, name: {}", name, e);
            throw new SeatunnelException(SeatunnelErrorEnum.HTTP_REQUEST_FAILED, url);
        }
    }

    public void updateProcessDefinition(
            Long projectCode,
            Long processDefinitionCode,
            String name,
            String taskDefinitionJson,
            String taskRelationJson,
            String locationsJson) {
        String url =
                String.format(
                        "%s/projects/%d/process-definition/%d",
                        properties.getUrl(), projectCode, processDefinitionCode);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("name", name);
        params.add("taskDefinitionJson", taskDefinitionJson);
        params.add("taskRelationJson", taskRelationJson);
        params.add("tenantCode", properties.getTenant());
        params.add("description", "Generated by SeaTunnel Web");
        params.add("locations", locationsJson);
        params.add("executionType", "PARALLEL");
        params.add("globalParams", "[]");
        params.add("releaseState", "OFFLINE");

        log.info(
                "Update process definition request - name: {}, code: {}",
                name,
                processDefinitionCode);

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(params, buildHeaders());
        try {
            ResponseEntity<String> response =
                    restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);
            log.info("Update process definition response: {}", response.getBody());
            DsResult<Void> result =
                    objectMapper.readValue(
                            response.getBody(), new TypeReference<DsResult<Void>>() {});
            if (!result.isSuccess()) {
                throw new SeatunnelException(
                        SeatunnelErrorEnum.UNEXPECTED_RETURN_CODE,
                        result.getCode().toString(),
                        result.getMsg());
            }
        } catch (SeatunnelException e) {
            throw e;
        } catch (Exception e) {
            log.error("Update process definition failed, name: {}", name, e);
            throw new SeatunnelException(SeatunnelErrorEnum.HTTP_REQUEST_FAILED, url);
        }
    }

    public void onlineProcessDefinition(Long projectCode, Long processDefinitionCode) {
        releaseProcessDefinition(projectCode, processDefinitionCode, "ONLINE");
    }

    public void offlineProcessDefinition(Long projectCode, Long processDefinitionCode) {
        releaseProcessDefinition(projectCode, processDefinitionCode, "OFFLINE");
    }

    private void releaseProcessDefinition(
            Long projectCode, Long processDefinitionCode, String state) {
        String url =
                String.format(
                        "%s/projects/%d/process-definition/%d/release",
                        properties.getUrl(), projectCode, processDefinitionCode);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("name", String.valueOf(processDefinitionCode));
        params.add("releaseState", state);

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(params, buildHeaders());
        try {
            restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        } catch (Exception e) {
            log.error(
                    "Release process definition failed, code: {}, state: {}",
                    processDefinitionCode,
                    state,
                    e);
            throw new SeatunnelException(SeatunnelErrorEnum.HTTP_REQUEST_FAILED, url);
        }
    }

    public Schedule createSchedule(
            Long projectCode, Long processDefinitionCode, String scheduleJson) {
        String url = String.format("%s/projects/%d/schedules", properties.getUrl(), projectCode);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("processDefinitionCode", String.valueOf(processDefinitionCode));
        params.add("schedule", scheduleJson);

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(params, buildHeaders());
        try {
            ResponseEntity<String> response =
                    restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            DsResult<Schedule> result =
                    objectMapper.readValue(
                            response.getBody(), new TypeReference<DsResult<Schedule>>() {});
            if (result.isSuccess()) {
                return result.getData();
            } else {
                throw new SeatunnelException(
                        SeatunnelErrorEnum.UNEXPECTED_RETURN_CODE,
                        result.getCode().toString(),
                        result.getMsg());
            }
        } catch (Exception e) {
            log.error("Create schedule failed, code: {}", processDefinitionCode, e);
            throw new SeatunnelException(SeatunnelErrorEnum.HTTP_REQUEST_FAILED, url);
        }
    }

    public void updateSchedule(Long projectCode, Integer scheduleId, String scheduleJson) {
        String url =
                String.format(
                        "%s/projects/%d/schedules/%d",
                        properties.getUrl(), projectCode, scheduleId);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("schedule", scheduleJson);

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(params, buildHeaders());
        try {
            ResponseEntity<String> response =
                    restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);
            DsResult<Void> result =
                    objectMapper.readValue(
                            response.getBody(), new TypeReference<DsResult<Void>>() {});
            if (!result.isSuccess()) {
                throw new SeatunnelException(
                        SeatunnelErrorEnum.UNEXPECTED_RETURN_CODE,
                        result.getCode().toString(),
                        result.getMsg());
            }
        } catch (Exception e) {
            log.error("Update schedule failed, id: {}", scheduleId, e);
            throw new SeatunnelException(SeatunnelErrorEnum.HTTP_REQUEST_FAILED, url);
        }
    }

    public void deleteSchedule(Long projectCode, Integer scheduleId) {
        String url =
                String.format(
                        "%s/projects/%d/schedules/%d",
                        properties.getUrl(), projectCode, scheduleId);

        HttpEntity<String> entity = new HttpEntity<>(buildHeaders());
        try {
            restTemplate.exchange(url, HttpMethod.DELETE, entity, String.class);
        } catch (Exception e) {
            log.error("Delete schedule failed, id: {}", scheduleId, e);
        }
    }

    public void updateScheduleState(Long projectCode, Integer scheduleId, String releaseState) {
        String url =
                String.format(
                        "%s/projects/%d/schedules/%d/online",
                        properties.getUrl(), projectCode, scheduleId);
        if ("OFFLINE".equalsIgnoreCase(releaseState)) {
            url =
                    String.format(
                            "%s/projects/%d/schedules/%d/offline",
                            properties.getUrl(), projectCode, scheduleId);
        }

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(params, buildHeaders());
        try {
            restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        } catch (Exception e) {
            log.error("Update schedule state failed, id: {}", scheduleId, e);
            throw new SeatunnelException(SeatunnelErrorEnum.HTTP_REQUEST_FAILED, url);
        }
    }

    public Schedule queryScheduleByProcessCode(Long projectCode, Long processDefinitionCode) {
        String url =
                String.format(
                        "%s/projects/%d/schedules?processDefinitionCode=%d&pageNo=1&pageSize=1",
                        properties.getUrl(), projectCode, processDefinitionCode);
        HttpEntity<String> entity = new HttpEntity<>(buildHeaders());
        try {
            ResponseEntity<String> response =
                    restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            log.info("Query schedule response: {}", response.getBody());
            // Response is paged, data contains totalList
            com.fasterxml.jackson.databind.JsonNode root =
                    objectMapper.readTree(response.getBody());
            if (root.path("code").asInt() == 0) {
                com.fasterxml.jackson.databind.JsonNode totalList =
                        root.path("data").path("totalList");
                if (totalList.isArray() && totalList.size() > 0) {
                    return objectMapper.treeToValue(totalList.get(0), Schedule.class);
                }
            }
        } catch (Exception e) {
            log.error("Query schedule failed, processDefinitionCode: {}", processDefinitionCode, e);
        }
        return null;
    }
}
