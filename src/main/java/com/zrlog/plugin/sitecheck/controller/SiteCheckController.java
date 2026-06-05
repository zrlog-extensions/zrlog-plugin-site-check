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
        session.responseHtmlStr(indexHtml(), requestPacket.getMethodStr(), requestPacket.getMsgId());
    }

    public void json() {
        surface();
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
            response(errorMap("未知操作"));
        } catch (Exception e) {
            response(errorMap((ACTION_OPTIMIZE.equals(actionRef) ? "数据库维护失败: " : "检查失败: ") + e.getMessage()));
        }
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

    private String indexHtml() {
        return "<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\"/>"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"/>"
                + "<title>站点检查</title>"
                + "<style>"
                + "body{margin:0;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;background:#fff;color:#1f2328;}"
                + ".wrap{padding:24px;max-width:760px;margin:0 auto;}"
                + "h1{font-size:22px;margin:0 0 8px;}.desc{color:#59636e;margin:0 0 20px;line-height:1.7;}"
                + ".actions{display:flex;gap:10px;flex-wrap:wrap;margin-bottom:18px;}"
                + "button{border:1px solid #d0d7de;background:#f6f8fa;border-radius:6px;padding:8px 12px;cursor:pointer;}"
                + "button.primary{background:#0969da;border-color:#0969da;color:#fff;}"
                + "pre{white-space:pre-wrap;background:#f6f8fa;border:1px solid #d0d7de;border-radius:6px;padding:12px;min-height:80px;}"
                + "</style></head><body><main class=\"wrap\">"
                + "<h1>站点检查</h1>"
                + "<p class=\"desc\">检查不会在页面打开时自动执行。点击“立即检查”后，插件才会扫描文章、SEO、数据库和目录写入状态。</p>"
                + "<div class=\"actions\">"
                + "<button class=\"primary\" onclick=\"runCheck()\">立即检查</button>"
                + "<button onclick=\"adminRoute('/article')\">文章管理</button>"
                + "<button onclick=\"adminRoute('/website')\">网站设置</button>"
                + "<button onclick=\"adminRoute('/index')\">控制台</button>"
                + "</div><pre id=\"result\">等待操作</pre></main>"
                + "<script>"
                + "function adminRoute(route){window.parent.postMessage({source:'zrlog-plugin',type:'zrlog-admin:navigate',route:route},'*');}"
                + "async function runCheck(){var el=document.getElementById('result');el.textContent='检查中...';"
                + "var body=new URLSearchParams();body.set('actionRef','siteCheck:run');body.set('values','{}');"
                + "try{var resp=await fetch('surfaceAction',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded;charset=UTF-8'},body:body});"
                + "var json=await resp.json();el.textContent=json.success?(json.data&&json.data.message?json.data.message:'检查完成'):(json.message||'检查失败');}"
                + "catch(e){el.textContent=e&&e.message?e.message:'检查失败';}}"
                + "</script></body></html>";
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
