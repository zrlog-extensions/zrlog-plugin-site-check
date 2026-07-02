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
        data.put("data", gson.toJson(SiteCheckApiResponse.success(pageData())));
        session.responseHtml("/templates/index", data, requestPacket.getMethodStr(), requestPacket.getMsgId());
    }

    public void json() {
        response(SiteCheckApiResponse.success(pageData()));
    }

    public void surface() {
        response(SiteCheckApiResponse.success(new SiteCheckService(session).surfaceData()));
    }

    public void surfaceAction() {
        SiteCheckActionRequest params = params();
        String actionRef = stringValue(params.getActionRef());
        SiteCheckService service = new SiteCheckService(session);
        try {
            if (ACTION_RUN.equals(actionRef)) {
                HealthCheckResult result = service.check();
                service.saveLastResult(result);
                response(SiteCheckApiResponse.success(new SiteCheckActionResponse("检查完成", service.surfaceData(result))));
                return;
            }
            if (ACTION_OPTIMIZE.equals(actionRef)) {
                HealthCheckResult result = service.optimizeDatabase();
                service.saveLastResult(result);
                response(SiteCheckApiResponse.success(new SiteCheckActionResponse(
                        result.canOptimizeDatabase ? "数据库维护已执行，检查结果已刷新" : "当前数据库不支持插件维护，检查结果已刷新",
                        service.surfaceData(result))));
                return;
            }
            if (ACTION_SETTINGS.equals(actionRef)) {
                service.saveConfig(params.getValues());
                response(SiteCheckApiResponse.success(new SiteCheckActionResponse("检查设置已保存", service.surfaceData())));
                return;
            }
            response(SiteCheckApiResponse.error("未知操作"));
        } catch (Exception e) {
            response(SiteCheckApiResponse.error(actionErrorPrefix(actionRef) + e.getMessage()));
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

    private SiteCheckActionRequest params() {
        if (requestInfo.getRequestBody() != null && requestInfo.getRequestBody().length > 0) {
            String body = new String(requestInfo.getRequestBody(), StandardCharsets.UTF_8);
            if (body.trim().startsWith("{")) {
                SiteCheckActionRequest request = gson.fromJson(body, SiteCheckActionRequest.class);
                return request == null ? new SiteCheckActionRequest() : request;
            }
        }
        return SiteCheckActionRequest.fromParams(this::paramObject, gson);
    }

    private Object paramObject(String key) {
        if (requestInfo.getParam() == null || requestInfo.getParam().get(key) == null || requestInfo.getParam().get(key).length == 0) {
            return null;
        }
        String[] values = requestInfo.getParam().get(key);
        return values.length == 1 ? values[0] : values;
    }

    private SiteCheckPageData pageData() {
        SiteCheckPageData data = new SiteCheckPageData();
        data.setDark(requestInfo.isDarkMode());
        data.setAdminColorPrimary(requestInfo.getAdminColorPrimary());
        data.setPlugin(session.getPlugin());
        data.setSurface(new SiteCheckService(session).surfaceData());
        return data;
    }

    private void response(SiteCheckApiResponse<?> response) {
        session.sendMsg(ContentType.JSON, response, requestPacket.getMethodStr(), requestPacket.getMsgId(),
                MsgPacketStatus.RESPONSE_SUCCESS);
    }

    private String stringValue(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value).trim();
    }

    private boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
