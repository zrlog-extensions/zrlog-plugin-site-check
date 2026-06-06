package com.zrlog.plugin.sitecheck.controller;

import com.google.gson.Gson;
import com.zrlog.plugin.IOSession;
import com.zrlog.plugin.data.codec.ContentType;
import com.zrlog.plugin.data.codec.HttpRequestInfo;
import com.zrlog.plugin.data.codec.MsgPacket;
import com.zrlog.plugin.data.codec.MsgPacketStatus;
import com.zrlog.plugin.sitecheck.service.SiteCheckService;
import com.zrlog.plugin.sitecheck.service.SiteCheckService.HealthCheckResult;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class SiteCheckController {

    private static final String ACTION_RUN = "siteCheck:run";
    private static final String ACTION_OPTIMIZE = "siteCheck:optimize";
    private static final String ACTION_SETTINGS = "siteCheck:settings";

    private final IOSession session;
    private final MsgPacket requestPacket;
    private final HttpRequestInfo requestInfo;
    private final Gson gson = new Gson();

    public SiteCheckController(IOSession session, MsgPacket requestPacket, HttpRequestInfo requestInfo) {
        this.session = session;
        this.requestPacket = requestPacket;
        this.requestInfo = requestInfo;
    }

    public void index() {
        Map<String, Object> data = new HashMap<>();
        data.put("theme", requestInfo.isDarkMode() ? "dark" : "light");
        data.put("data", gson.toJson(pageData()));
        session.responseHtml("/templates/index", data, requestPacket.getMethodStr(), requestPacket.getMsgId());
    }

    public void json() {
        response(successMap(pageData()));
    }

    public void surface() {
        response(successMap(new SiteCheckService(session).surfaceData()));
    }

    public void surfaceAction() {
        String actionRef = stringValue(params().get("actionRef"));
        SiteCheckService service = new SiteCheckService(session);
        try {
            if (ACTION_RUN.equals(actionRef)) {
                HealthCheckResult result = service.check();
                service.saveLastResult(result);
                response(successMap(actionResult("检查完成", service.surfaceData(result))));
                return;
            }
            if (ACTION_OPTIMIZE.equals(actionRef)) {
                HealthCheckResult result = service.optimizeDatabase();
                service.saveLastResult(result);
                response(successMap(actionResult(
                        result.canOptimizeDatabase ? "数据库维护已执行，检查结果已刷新" : "当前数据库不支持插件维护，检查结果已刷新",
                        service.surfaceData(result))));
                return;
            }
            if (ACTION_SETTINGS.equals(actionRef)) {
                service.saveConfig(values(params().get("values")));
                response(successMap(actionResult("检查设置已保存", service.surfaceData())));
                return;
            }
            response(errorMap("未知操作"));
        } catch (Exception e) {
            response(errorMap(actionErrorPrefix(actionRef) + e.getMessage()));
        }
    }

    private String actionErrorPrefix(String actionRef) {
        if (ACTION_OPTIMIZE.equals(actionRef)) {
            return "数据库维护失败: ";
        }
        if (ACTION_SETTINGS.equals(actionRef)) {
            return "保存设置失败: ";
        }
        return "检查失败: ";
    }

    private Map<String, Object> actionResult(String message, Map<String, Object> surface) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", message);
        result.put("surface", surface);
        return result;
    }

    private Map<String, Object> params() {
        if (requestInfo.getRequestBody() != null && requestInfo.getRequestBody().length > 0) {
            String body = new String(requestInfo.getRequestBody(), StandardCharsets.UTF_8);
            if (body.trim().startsWith("{")) {
                return gson.fromJson(body, Map.class);
            }
        }
        if (requestInfo.getParam() == null) {
            return new HashMap<>();
        }
        return requestInfo.simpleParam();
    }

    private Map<String, Object> pageData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dark", requestInfo.isDarkMode());
        data.put("adminColorPrimary", requestInfo.getAdminColorPrimary());
        data.put("plugin", session.getPlugin());
        data.put("surface", new SiteCheckService(session).surfaceData());
        return data;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<String, Object> values(Object raw) {
        Object value = firstValue(raw);
        if (value instanceof Map) {
            return new LinkedHashMap<>((Map) value);
        }
        String text = stringValue(value);
        if (text.startsWith("{")) {
            Map parsed = gson.fromJson(text, Map.class);
            return parsed == null ? new LinkedHashMap<>() : new LinkedHashMap<>(parsed);
        }
        return new LinkedHashMap<>();
    }

    private Object firstValue(Object value) {
        if (value instanceof Iterable) {
            for (Object item : (Iterable<?>) value) {
                return item;
            }
            return "";
        }
        return value;
    }

    private Map<String, Object> successMap(Object data) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("success", true);
        map.put("data", data);
        return map;
    }

    private Map<String, Object> errorMap(String message) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("success", false);
        map.put("message", notBlank(message) ? message : "操作失败");
        return map;
    }

    private void response(Map<String, Object> map) {
        session.sendMsg(ContentType.JSON, map, requestPacket.getMethodStr(), requestPacket.getMsgId(),
                MsgPacketStatus.RESPONSE_SUCCESS);
    }

    private String stringValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Iterable) {
            for (Object item : (Iterable<?>) value) {
                return stringValue(item);
            }
            return "";
        }
        return String.valueOf(value).trim();
    }

    private boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
