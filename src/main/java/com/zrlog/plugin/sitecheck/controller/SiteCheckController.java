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

    private String indexHtml() {
        return "<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\"/>"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"/>"
                + "<title>站点检查</title>"
                + "<style>"
                + "body{margin:0;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;background:#fff;color:#1f2328;}"
                + ".wrap{padding:24px;max-width:980px;margin:0 auto;}"
                + ".head{display:flex;align-items:flex-start;justify-content:space-between;gap:16px;margin-bottom:18px;}"
                + "h1{font-size:22px;margin:0 0 8px;}.desc{color:#59636e;margin:0;line-height:1.7;}"
                + ".actions{display:flex;gap:10px;flex-wrap:wrap;justify-content:flex-end;}"
                + "button{border:1px solid #d0d7de;background:#f6f8fa;border-radius:6px;padding:8px 12px;cursor:pointer;color:#24292f;}"
                + "button.primary{background:#0969da;border-color:#0969da;color:#fff;}button:disabled{opacity:.55;cursor:not-allowed;}"
                + ".message{margin:0 0 14px;padding:10px 12px;border:1px solid #d0d7de;border-radius:6px;background:#f6f8fa;color:#57606a;}"
                + ".grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(120px,1fr));gap:10px;margin-bottom:18px;}"
                + ".metric,.section{border:1px solid #d0d7de;border-radius:6px;background:#fff;}"
                + ".metric{padding:12px;}.label{font-size:12px;color:#57606a;margin-bottom:6px;}.value{font-size:18px;font-weight:650;}"
                + ".sections{display:grid;grid-template-columns:minmax(0,1.4fr) minmax(260px,.8fr);gap:14px;}"
                + ".side{display:grid;gap:14px;align-content:start;}.section{padding:14px;}.section h2{font-size:15px;margin:0 0 12px;}"
                + ".item,.record{border-top:1px solid #d8dee4;padding:12px 0;}.item:first-of-type,.record:first-of-type{border-top:0;padding-top:0;}"
                + ".item-title{display:flex;align-items:center;justify-content:space-between;gap:10px;font-weight:650;margin-bottom:6px;}"
                + ".item-desc,.record-desc{color:#57606a;line-height:1.6;font-size:13px;}"
                + ".samples{margin:10px 0 0;padding-left:18px;color:#57606a;font-size:12px;line-height:1.6;}"
                + ".item-actions{display:flex;gap:8px;flex-wrap:wrap;margin-top:10px;}.item-actions button{padding:6px 10px;}"
                + ".status{font-size:12px;border-radius:999px;padding:2px 8px;background:#d8dee4;color:#24292f;white-space:nowrap;}"
                + ".status.warning{background:#fff8c5;color:#7d4e00;}.status.error{background:#ffebe9;color:#cf222e;}.status.normal{background:#dafbe1;color:#116329;}"
                + ".empty{color:#57606a;line-height:1.7;}.record strong{font-size:14px;}.record time{color:#57606a;font-size:12px;}"
                + ".form{display:grid;gap:10px;}.form-row{display:grid;gap:6px;}.form-row.inline{grid-template-columns:1fr 1fr;gap:10px;}"
                + ".field label,.check{font-size:12px;color:#57606a;}.field input,.field textarea{width:100%;box-sizing:border-box;border:1px solid #d0d7de;border-radius:6px;padding:8px;color:#24292f;background:#fff;}"
                + ".field textarea{min-height:72px;resize:vertical;}.checks{display:grid;gap:6px}.check{display:flex;gap:8px;align-items:center;}.check input{margin:0;}"
                + "@media(max-width:720px){.wrap{padding:18px}.head{display:block}.actions{justify-content:flex-start;margin-top:14px}.sections{grid-template-columns:1fr}}"
                + "</style></head><body><main class=\"wrap\">"
                + "<div class=\"head\"><div><h1>站点检查</h1>"
                + "<p class=\"desc\">检查不会在页面打开时自动执行。点击“立即检查”后，插件才会通过宿主抓取公开页面并分析基础 SEO 信息。</p>"
                + "</div><div class=\"actions\" id=\"actions\"></div></div>"
                + "<div class=\"message\" id=\"message\">正在读取最近一次检查结果...</div>"
                + "<div class=\"grid\" id=\"metrics\"></div>"
                + "<div class=\"sections\"><section class=\"section\"><h2>检查结果</h2><div id=\"items\"></div></section>"
                + "<div class=\"side\"><section class=\"section\"><h2>检查设置</h2>"
                + "<form class=\"form\" id=\"settings\"><div class=\"form-row inline\">"
                + "<div class=\"field\"><label for=\"maxPages\">页面上限</label><input id=\"maxPages\" name=\"maxPages\" type=\"number\" min=\"1\" max=\"50\"/></div>"
                + "<div class=\"field\"><label for=\"timeoutSeconds\">超时秒数</label><input id=\"timeoutSeconds\" name=\"timeoutSeconds\" type=\"number\" min=\"3\" max=\"30\"/></div>"
                + "</div><div class=\"field\"><label for=\"userAgent\">User-Agent</label><input id=\"userAgent\" name=\"userAgent\" maxlength=\"120\"/></div>"
                + "<div class=\"field\"><label for=\"extraPaths\">额外路径</label><textarea id=\"extraPaths\" name=\"extraPaths\" placeholder=\"/about&#10;/archive\"></textarea></div>"
                + "<div class=\"checks\">"
                + "<label class=\"check\"><input id=\"requireCanonical\" name=\"requireCanonical\" type=\"checkbox\"/>检查 canonical</label>"
                + "<label class=\"check\"><input id=\"requireH1\" name=\"requireH1\" type=\"checkbox\"/>检查 H1</label>"
                + "<label class=\"check\"><input id=\"checkDuplicateMeta\" name=\"checkDuplicateMeta\" type=\"checkbox\"/>检查重复 title/description</label>"
                + "<label class=\"check\"><input id=\"checkLengthGuidance\" name=\"checkLengthGuidance\" type=\"checkbox\"/>检查标题和描述长度</label>"
                + "</div><button class=\"primary\" type=\"submit\">保存设置</button></form></section>"
                + "<section class=\"section\"><h2>最近记录</h2><div id=\"records\"></div></section></div></div>"
                + "</main><script>"
                + "var busy=false;var currentSurface={};"
                + "function adminRoute(route){window.parent.postMessage({source:'zrlog-plugin',type:'zrlog-admin:navigate',route:route},'*');}"
                + "function esc(v){return String(v==null?'':v).replace(/[&<>\\\"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','\\\"':'&quot;',\"'\":'&#39;'}[c];});}"
                + "function fmtTime(v){if(!v){return '-';}var d=new Date(v);if(isNaN(d.getTime())){return '-';}return d.getFullYear()+'-'+pad(d.getMonth()+1)+'-'+pad(d.getDate())+' '+pad(d.getHours())+':'+pad(d.getMinutes());}"
                + "function pad(v){return String(v).padStart(2,'0');}"
                + "function setMessage(text){document.getElementById('message').textContent=text||'';}"
                + "function setBusy(next){busy=next;Array.prototype.forEach.call(document.querySelectorAll('button'),function(btn){btn.disabled=busy;});}"
                + "function renderActions(actions){var root=document.getElementById('actions');root.innerHTML='';(actions||[]).forEach(function(action){root.appendChild(actionButton(action));});}"
                + "function actionButton(action){var btn=document.createElement('button');btn.textContent=action.label||'操作';if(action.style==='primary'){btn.className='primary';}btn.onclick=function(){invokeAction(action);};return btn;}"
                + "function invokeAction(action){if(!action){return;}if(action.adminRoute){adminRoute(action.adminRoute);return;}if(action.actionRef){runAction(action.actionRef,{});}}"
                + "async function runAction(actionRef,values){if(busy){return;}setBusy(true);setMessage(actionRef==='siteCheck:optimize'?'数据库维护中...':(actionRef==='siteCheck:settings'?'保存设置中...':'检查中...'));var body=new URLSearchParams();body.set('actionRef',actionRef);body.set('values',JSON.stringify(values||{}));"
                + "try{var resp=await fetch('surfaceAction',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded;charset=UTF-8'},body:body});var json=await resp.json();if(!json.success){throw new Error(json.message||'操作失败');}var data=json.data||{};setMessage(data.message||'操作完成');renderSurface(data.surface||{});}catch(e){setMessage(e&&e.message?e.message:'操作失败');}finally{setBusy(false);}}"
                + "async function loadSurface(){try{var resp=await fetch('surface');var json=await resp.json();if(!json.success){throw new Error(json.message||'读取失败');}setMessage(json.data&&json.data.description?json.data.description:'已读取最近一次检查结果');renderSurface(json.data||{});}catch(e){setMessage(e&&e.message?e.message:'读取失败');}}"
                + "function renderSurface(surface){currentSurface=surface||{};renderActions(currentSurface.actions);renderMetrics(currentSurface.metrics);renderSettings(currentSurface.config||{});renderItems(currentSurface.items,issueMap(currentSurface.result));renderRecords(currentSurface.records);}"
                + "function renderSettings(config){setInput('maxPages',config.maxPages||12);setInput('timeoutSeconds',config.timeoutSeconds||12);setInput('userAgent',config.userAgent||'ZrLog SiteCheck/4.0');setInput('extraPaths',config.extraPaths||'');setCheck('requireCanonical',config.requireCanonical!==false);setCheck('requireH1',config.requireH1!==false);setCheck('checkDuplicateMeta',config.checkDuplicateMeta!==false);setCheck('checkLengthGuidance',config.checkLengthGuidance!==false);}"
                + "function setInput(id,value){var el=document.getElementById(id);if(el&&document.activeElement!==el){el.value=value==null?'':value;}}"
                + "function setCheck(id,value){var el=document.getElementById(id);if(el){el.checked=!!value;}}"
                + "function readSettings(){return {maxPages:num('maxPages'),timeoutSeconds:num('timeoutSeconds'),userAgent:val('userAgent'),extraPaths:val('extraPaths'),requireCanonical:checked('requireCanonical'),requireH1:checked('requireH1'),checkDuplicateMeta:checked('checkDuplicateMeta'),checkLengthGuidance:checked('checkLengthGuidance')};}"
                + "function num(id){var n=parseInt(val(id),10);return isNaN(n)?0:n;}function val(id){var el=document.getElementById(id);return el?el.value:'';}function checked(id){var el=document.getElementById(id);return !!(el&&el.checked);}"
                + "function issueMap(result){var map={};((result&&result.issues)||[]).forEach(function(issue){map[issue.key]=issue;});return map;}"
                + "function renderMetrics(metrics){var root=document.getElementById('metrics');root.innerHTML='';(metrics||[]).forEach(function(metric){var div=document.createElement('div');div.className='metric';div.innerHTML='<div class=\"label\">'+esc(metric.label)+'</div><div class=\"value\">'+esc(metric.value)+'</div>';root.appendChild(div);});}"
                + "function renderItems(items,issues){var root=document.getElementById('items');root.innerHTML='';if(!items||items.length===0){root.innerHTML='<div class=\"empty\">暂无检查结果。</div>';return;}items.forEach(function(item){var issue=(issues||{})[item.id]||{};var div=document.createElement('div');div.className='item';var actions=(item.actions||[]).map(function(action){return '<button data-action=\"'+esc(JSON.stringify(action))+'\">'+esc(action.label||'操作')+'</button>';}).join('');div.innerHTML='<div class=\"item-title\"><span>'+esc(item.title)+'</span><span class=\"status '+esc(item.status)+'\">'+esc(statusText(item.status))+'</span></div><div class=\"item-desc\">'+esc(item.description)+'</div>'+sampleHtml(issue)+(actions?'<div class=\"item-actions\">'+actions+'</div>':'');root.appendChild(div);Array.prototype.forEach.call(div.querySelectorAll('button[data-action]'),function(btn){btn.onclick=function(){invokeAction(JSON.parse(btn.getAttribute('data-action')));};});});}"
                + "function sampleHtml(issue){var rows=[];(issue.sampleDetails||[]).forEach(function(sample){var text=(sample.target?sample.target+' / ':'')+sampleLabel(sample.key);rows.push('<li>'+esc(text)+'</li>');});(issue.samples||[]).forEach(function(sample){rows.push('<li>'+esc(sample)+'</li>');});return rows.length?'<ul class=\"samples\">'+rows.slice(0,5).join('')+'</ul>':'';}"
                + "function sampleLabel(key){var map={websiteSeoTitleMissing:'站点标题缺失',websiteSeoDescriptionMissing:'站点描述缺失',websiteSeoKeywordsMissing:'站点关键词缺失',pageTitleMissing:'页面 title 缺失',pageTitleTooShort:'页面 title 过短',pageTitleTooLong:'页面 title 过长',pageDescriptionMissing:'meta description 缺失',pageDescriptionTooShort:'meta description 过短',pageDescriptionTooLong:'meta description 过长',pageCanonicalMissing:'canonical 缺失',pageCanonicalNotAbsolute:'canonical 不是完整 URL',pageH1Missing:'H1 缺失',pageH1Multiple:'H1 过多',pageNoIndex:'页面声明 noindex',pageViewportMissing:'viewport 缺失',pageHtmlLangMissing:'html lang 缺失',pageTitleDuplicate:'页面 title 重复',pageDescriptionDuplicate:'meta description 重复',articleSeoDigestMissing:'文章摘要缺失',articleSeoKeywordsMissing:'文章关键词缺失'};return map[key]||key;}"
                + "function statusText(status){return {normal:'正常',warning:'提醒',error:'异常'}[status]||status||'-';}"
                + "function renderRecords(records){var root=document.getElementById('records');root.innerHTML='';if(!records||records.length===0){root.innerHTML='<div class=\"empty\">暂无检查记录。</div>';return;}records.forEach(function(record){var div=document.createElement('div');div.className='record';div.innerHTML='<strong>'+esc(record.score)+'/100</strong> <span class=\"status '+esc(record.status)+'\">'+esc(statusText(record.status))+'</span><br/><time>'+esc(fmtTime(record.checkedAt))+'</time><div class=\"record-desc\">问题 '+esc(record.issueCount)+' 类，页面 '+esc(record.crawledPageCount)+'/'+esc((record.crawledPageCount||0)+(record.crawlFailedPageCount||0))+'，文章 '+esc(record.publishedArticleCount)+'/'+esc(record.articleCount)+'，SEO '+esc(record.seoIssueCount)+'，数据库 '+esc(record.databaseFragmentLabel)+'</div>';root.appendChild(div);});}"
                + "document.getElementById('settings').addEventListener('submit',function(event){event.preventDefault();runAction('siteCheck:settings',readSettings());});"
                + "loadSurface();"
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
