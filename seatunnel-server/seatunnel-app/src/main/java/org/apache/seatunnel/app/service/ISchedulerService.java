package org.apache.seatunnel.app.service;

import org.apache.seatunnel.app.domain.request.job.ScheduleReq;

public interface ISchedulerService {

    void createSchedule(ScheduleReq scheduleReq);

    ScheduleReq getSchedule(Long jobDefineId);

    void updateSchedule(ScheduleReq scheduleReq);

    void deleteSchedule(Long jobDefineId);

    void onlineSchedule(Long jobDefineId);

    void offlineSchedule(Long jobDefineId);
}
