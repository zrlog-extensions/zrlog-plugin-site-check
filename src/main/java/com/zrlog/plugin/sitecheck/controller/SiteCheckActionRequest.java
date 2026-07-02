package com.zrlog.plugin.sitecheck.controller;

import com.google.gson.Gson;
import com.zrlog.plugin.sitecheck.service.SiteCheckConfig;

import java.util.function.Function;

public class SiteCheckActionRequest {

    private String actionRef;
    private SiteCheckConfig values;

    public static SiteCheckActionRequest fromParams(Function<String, Object> paramValue, Gson gson) {
        SiteCheckActionRequest request = new SiteCheckActionRequest();
        request.setActionRef(stringValue(paramValue.apply("actionRef")));
        request.setValues(configValue(paramValue.apply("values"), gson));
        return request;
    }

    private static SiteCheckConfig configValue(Object raw, Gson gson) {
        Object value = firstValue(raw);
        if (value instanceof SiteCheckConfig) {
            return ((SiteCheckConfig) value).normalized();
        }
        String text = stringValue(value);
        if (text.startsWith("{")) {
            SiteCheckConfig config = gson.fromJson(text, SiteCheckConfig.class);
            return config == null ? null : config.normalized();
        }
        if (value != null) {
            SiteCheckConfig config = gson.fromJson(gson.toJson(value), SiteCheckConfig.class);
            return config == null ? null : config.normalized();
        }
        return null;
    }

    private static Object firstValue(Object value) {
        if (value instanceof String[]) {
            String[] values = (String[]) value;
            return values.length == 0 ? "" : values[0];
        }
        if (value instanceof Object[]) {
            Object[] values = (Object[]) value;
            return values.length == 0 ? "" : values[0];
        }
        if (value instanceof Iterable) {
            for (Object item : (Iterable<?>) value) {
                return item;
            }
            return "";
        }
        return value;
    }

    private static String stringValue(Object value) {
        Object first = firstValue(value);
        return first == null ? "" : String.valueOf(first).trim();
    }

    public String getActionRef() {
        return actionRef;
    }

    public void setActionRef(String actionRef) {
        this.actionRef = actionRef;
    }

    public SiteCheckConfig getValues() {
        return values;
    }

    public void setValues(SiteCheckConfig values) {
        this.values = values;
    }
}
