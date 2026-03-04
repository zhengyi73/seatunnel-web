package org.apache.seatunnel.app.thirdparty.dolphinscheduler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "dolphinscheduler")
public class DolphinSchedulerProperties {

    /** DolphinScheduler API URL */
    private String url;

    /** DolphinScheduler API Token */
    private String token;

    /** DolphinScheduler Tenant */
    private String tenant = "default";

    /** DolphinScheduler Project Name */
    private String projectName = "seatunnel";

    /** SeaTunnel Web callback URL (used by DS SHELL tasks to call back) */
    private String callbackUrl = "http://127.0.0.1:8801";
}
