package com.zrlog.plugin.sitecheck.controller;

public class SiteCheckApiResponse<T> {

    private boolean success;
    private String message;
    private T data;

    public SiteCheckApiResponse() {
    }

    private SiteCheckApiResponse(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    public static <T> SiteCheckApiResponse<T> success(T data) {
        return new SiteCheckApiResponse<T>(true, null, data);
    }

    public static SiteCheckApiResponse<Void> error(String message) {
        return new SiteCheckApiResponse<Void>(false, message, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
