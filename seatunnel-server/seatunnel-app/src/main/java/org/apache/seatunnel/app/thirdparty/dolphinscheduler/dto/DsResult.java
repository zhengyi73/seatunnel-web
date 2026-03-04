package org.apache.seatunnel.app.thirdparty.dolphinscheduler.dto;

import lombok.Data;

@Data
public class DsResult<T> {
    private Integer code;
    private String msg;
    private T data;
    private Boolean success;

    public boolean isSuccess() {
        return code != null && code == 0;
    }
}
