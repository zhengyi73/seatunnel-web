package org.apache.seatunnel.app.controller;

import org.apache.seatunnel.app.common.Result;
import org.apache.seatunnel.app.domain.request.job.ScheduleReq;
import org.apache.seatunnel.app.service.ISchedulerService;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

import javax.annotation.Resource;
import javax.validation.constraints.NotNull;

@RequestMapping("/seatunnel/api/v1/job/schedule")
@RestController
public class SchedulerController {

    @Resource private ISchedulerService schedulerService;

    @GetMapping("/{jobDefineId}")
    @ApiOperation(value = "Get schedule for a job", httpMethod = "GET")
    public Result<ScheduleReq> getSchedule(
            @ApiParam(value = "Job define id", required = true) @PathVariable(value = "jobDefineId")
                    Long jobDefineId) {
        ScheduleReq schedule = schedulerService.getSchedule(jobDefineId);
        return Result.success(schedule);
    }

    @PostMapping
    @ApiOperation(value = "Create schedule for a job", httpMethod = "POST")
    public Result<Void> createSchedule(@RequestBody @NotNull ScheduleReq scheduleReq) {
        schedulerService.createSchedule(scheduleReq);
        return Result.success();
    }

    @PutMapping
    @ApiOperation(value = "Update schedule for a job", httpMethod = "PUT")
    public Result<Void> updateSchedule(@RequestBody @NotNull ScheduleReq scheduleReq) {
        schedulerService.updateSchedule(scheduleReq);
        return Result.success();
    }

    @DeleteMapping("/{jobDefineId}")
    @ApiOperation(value = "Delete schedule", httpMethod = "DELETE")
    public Result<Void> deleteSchedule(
            @ApiParam(value = "Job define id", required = true) @PathVariable(value = "jobDefineId")
                    Long jobDefineId) {
        schedulerService.deleteSchedule(jobDefineId);
        return Result.success();
    }

    @PostMapping("/{jobDefineId}/online")
    @ApiOperation(value = "Online/Activate schedule", httpMethod = "POST")
    public Result<Void> onlineSchedule(
            @ApiParam(value = "Job define id", required = true) @PathVariable(value = "jobDefineId")
                    Long jobDefineId) {
        schedulerService.onlineSchedule(jobDefineId);
        return Result.success();
    }

    @PostMapping("/{jobDefineId}/offline")
    @ApiOperation(value = "Offline/Pause schedule", httpMethod = "POST")
    public Result<Void> offlineSchedule(
            @ApiParam(value = "Job define id", required = true) @PathVariable(value = "jobDefineId")
                    Long jobDefineId) {
        schedulerService.offlineSchedule(jobDefineId);
        return Result.success();
    }
}
