package com.zrlog.plugin.sitecheck.service;

import com.google.gson.Gson;
import com.hibegin.common.dao.DataSourceWrapperImpl;
import com.zrlog.plugin.IOSession;
import com.zrlog.plugin.client.HttpClientUtils;
import com.zrlog.plugin.common.PathKit;
import com.zrlog.plugin.common.SessionKvRepository;
import com.zrlog.plugin.common.model.BlogRunTime;
import com.zrlog.plugin.common.model.PublicInfo;
import com.zrlog.plugin.data.codec.ContentType;
import com.zrlog.plugin.type.ActionType;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SiteCheckService {

    private static final String LAST_RESULT_KEY = "siteCheckLastResult";
    private static final String RECORDS_KEY = "siteCheckRecords";
    private static final String CONFIG_KEY = "siteCheckConfig";
    private static final int SAMPLE_LIMIT = 5;
    private static final int RECORD_LIMIT = 10;
    private static final String WEBSITE_ROUTE = "/website";
    private static final String WEBSITE_BLOG_ROUTE = "/website/blog";
    private static final String WEBSITE_OTHER_ROUTE = "/website/other";
    private static final String ARTICLE_ROUTE = "/article";
    private static final String SYSTEM_ROUTE = "/system";
    private static final String PLUGIN_ROUTE = "/plugin";
    private static final String DATABASE_OPTIMIZE_UNSUPPORTED_WEBAPI = "remoteWebApi";
    private static final String DATABASE_OPTIMIZE_UNSUPPORTED_ENGINE = "unsupportedEngine";
    private static final String DATABASE_OPTIMIZE_UNAVAILABLE = "unavailable";
    private static final Pattern MARKDOWN_LINK_PATTERN = Pattern.compile("!?\\[[^\\]]*]\\(([^)\\s]+)(?:\\s+\"[^\"]*\")?\\)");
    private static final String DEFAULT_SITEMAP_URI_PATH = "/sitemap.xml";
    private static final String DEFAULT_RSS_URI_PATH = "/rss.xml";
    private static final Gson GSON = new Gson();

    private final IOSession session;
    private File cachedStaticRoot;

    public SiteCheckService(IOSession session) {
        this.session = session;
    }

    public HealthCheckResult check() throws SQLException {
        SiteCheckConfig config = readConfig();
        ArticleHealthScanResult articleHealthScanResult = scanArticleHealth(config);
        BrokenLinkResult brokenLinkResult = articleHealthScanResult.brokenLinks;
        PageCrawlResult pageCrawlResult = crawlSitePages(articleHealthScanResult.articleTargets, config);
        SeoResult seoResult = pageCrawlResult.seo.count > 0 || pageCrawlResult.crawledCount > 0
                ? pageCrawlResult.seo
                : articleHealthScanResult.seo;
        IssueScanResult routeIntegrityResult = articleHealthScanResult.routeIntegrity;
        IssueScanResult staticSiteConfigResult = inspectStaticSiteConfig();
        IssueScanResult publicOutputResult = inspectPublicOutput(articleHealthScanResult.publishedArticleCount);
        IssueScanResult robotPolicyResult = inspectRobotPolicy();
        DatabaseFragmentResult databaseFragmentResult = inspectDatabaseFragment();
        DirectoryWritableResult directoryWritableResult = inspectDirectoryWritable();

        List<HealthCheckIssue> issues = new ArrayList<>();
        if (brokenLinkResult.count > 0) {
            issues.add(issue("brokenLinks", "warning", brokenLinkResult.count, brokenLinkResult.samples,
                    Collections.emptyList(), "publish", ARTICLE_ROUTE,
                    "存在本地资源死链", "部分文章引用了已经不存在的本地文件或附件。"));
        }
        if (seoResult.count > 0) {
            issues.add(issue("seoMissing", "warning", seoResult.count,
                    Collections.emptyList(), seoResult.samples, "search",
                    seoResult.hasHomeIssue ? WEBSITE_ROUTE : ARTICLE_ROUTE,
                    "页面 SEO 基础信号异常", "公开页面的 title、meta description、canonical、H1、viewport、html lang、索引策略或重复元信息需要复查。"));
        }
        if (pageCrawlResult.failure.count > 0) {
            issues.add(issue(pageCrawlResult.crawledCount > 0 ? "pageCrawlFailed" : "pageCrawlUnavailable",
                    pageCrawlResult.crawledCount > 0 ? "info" : "warning", pageCrawlResult.failure.count,
                    pageCrawlResult.failure.samples, Collections.emptyList(), "search", WEBSITE_BLOG_ROUTE,
                    pageCrawlResult.crawledCount > 0 ? "部分页面抓取失败" : "公开页面无法抓取",
                    "页面级 SEO 检查需要抓取公开页面，失败时请确认主页地址和站点访问状态。"));
        }
        if (routeIntegrityResult.count > 0) {
            issues.add(issue("articleRoute", "warning", routeIntegrityResult.count, routeIntegrityResult.samples,
                    Collections.emptyList(), "publish", ARTICLE_ROUTE,
                    "文章访问地址异常", "部分文章别名为空、重复或与数字 ID 冲突，可能影响公开访问和跳转。"));
        }
        if (staticSiteConfigResult.count > 0) {
            issues.add(issue("staticSiteConfig", "warning", staticSiteConfigResult.count, staticSiteConfigResult.samples,
                    Collections.emptyList(), "availability", WEBSITE_BLOG_ROUTE,
                    "静态站点配置异常", "静态化开启时需要可公开访问的主页地址，地址格式异常会影响静态页面链接生成。"));
        }
        if (robotPolicyResult.count > 0) {
            issues.add(issue("robotsPolicy", "info", robotPolicyResult.count, robotPolicyResult.samples,
                    Collections.emptyList(), "search", WEBSITE_OTHER_ROUTE,
                    "robots.txt 建议复查", "robots.txt 未声明 Sitemap 或没有明确保护后台路径。"));
        }
        if (publicOutputResult.count > 0) {
            issues.add(issue("publicOutput", "info", publicOutputResult.count, publicOutputResult.samples,
                    Collections.emptyList(), "search", PLUGIN_ROUTE,
                    "公开分发文件建议复查", "已发布文章存在，但本地未发现常见 RSS 或 Sitemap 输出文件。"));
        }
        if (databaseFragmentResult.fragmentValue > 0) {
            issues.add(issue("databaseFragment", databaseFragmentResult.canOptimize ? "warning" : "info",
                    databaseFragmentResult.fragmentValue, databaseFragmentResult.samples,
                    Collections.emptyList(), "performance", null,
                    "建议执行数据库维护", "当前数据库存在可回收空间或统计信息维护需求。"));
        }
        if (directoryWritableResult.count > 0) {
            issues.add(issue("directoryWritable", "error", directoryWritableResult.count,
                    directoryWritableResult.samples, Collections.emptyList(), "availability", SYSTEM_ROUTE,
                    "目录写入权限异常", "静态目录或插件临时目录无法正常创建、写入或删除文件。"));
        }

        int score = buildScore(
                brokenLinkResult.count,
                seoResult.count,
                databaseFragmentResult.fragmentValue > 0,
                directoryWritableResult.count > 0,
                routeIntegrityResult.count,
                publicOutputResult.count + robotPolicyResult.count,
                staticSiteConfigResult.count,
                pageCrawlResult.crawledCount == 0 && pageCrawlResult.failure.count > 0
        );
        HealthCheckResult result = new HealthCheckResult();
        result.checkedAt = System.currentTimeMillis();
        result.score = score;
        result.articleCount = articleHealthScanResult.articleCount;
        result.publishedArticleCount = articleHealthScanResult.publishedArticleCount;
        result.crawledPageCount = pageCrawlResult.crawledCount;
        result.crawlFailedPageCount = pageCrawlResult.failure.count;
        result.brokenLinkCount = brokenLinkResult.count;
        result.seoIssueCount = seoResult.count;
        result.routeIssueCount = routeIntegrityResult.count;
        result.siteConfigIssueCount = staticSiteConfigResult.count;
        result.publicOutputIssueCount = publicOutputResult.count + robotPolicyResult.count;
        result.databaseFragmentValue = databaseFragmentResult.fragmentValue;
        result.databaseFragmentLabel = databaseFragmentResult.fragmentLabel;
        result.databaseEngine = databaseFragmentResult.engineLabel;
        result.databaseFragmentInspectable = databaseFragmentResult.fragmentInspectable;
        result.canOptimizeDatabase = databaseFragmentResult.canOptimize;
        result.databaseOptimizeUnsupportedReason = databaseFragmentResult.optimizeUnsupportedReason;
        result.issues = issues;
        result.suggestions = suggestions(issues);
        return result;
    }

    public SiteCheckConfig readConfig() {
        Optional<String> json = SessionKvRepository.of(session).get(CONFIG_KEY);
        if (json.isEmpty() || !notBlank(json.get())) {
            return SiteCheckConfig.defaults();
        }
        try {
            SiteCheckConfig config = GSON.fromJson(json.get(), SiteCheckConfig.class);
            return config == null ? SiteCheckConfig.defaults() : config.normalized();
        } catch (Exception ignored) {
            return SiteCheckConfig.defaults();
        }
    }

    public SiteCheckConfig saveConfig(Map<String, Object> values) {
        SiteCheckConfig config = SiteCheckConfig.fromValues(values);
        SessionKvRepository.of(session).put(CONFIG_KEY, GSON.toJson(config));
        return config;
    }

    public HealthCheckResult optimizeDatabase() throws SQLException {
        DatabaseFragmentResult databaseFragmentResult = inspectDatabaseFragment();
        if (databaseFragmentResult.canOptimize) {
            runDatabaseOptimize(databaseFragmentResult.engine);
        }
        return check();
    }

    public void saveLastResult(HealthCheckResult result) {
        SessionKvRepository repository = SessionKvRepository.of(session);
        repository.put(LAST_RESULT_KEY, GSON.toJson(result));
        List<HealthCheckRecord> records = readRecords();
        records.add(0, record(result));
        if (records.size() > RECORD_LIMIT) {
            records = new ArrayList<>(records.subList(0, RECORD_LIMIT));
        }
        repository.put(RECORDS_KEY, GSON.toJson(records));
    }

    public Optional<HealthCheckResult> readLastResult() {
        Optional<String> json = SessionKvRepository.of(session).get(LAST_RESULT_KEY);
        if (json.isEmpty() || !notBlank(json.get())) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(GSON.fromJson(json.get(), HealthCheckResult.class));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public List<HealthCheckRecord> readRecords() {
        Optional<String> json = SessionKvRepository.of(session).get(RECORDS_KEY);
        if (json.isEmpty() || !notBlank(json.get())) {
            return new ArrayList<>();
        }
        try {
            HealthCheckRecord[] records = GSON.fromJson(json.get(), HealthCheckRecord[].class);
            if (records == null) {
                return new ArrayList<>();
            }
            return new ArrayList<>(Arrays.asList(records));
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    private HealthCheckRecord record(HealthCheckResult result) {
        HealthCheckRecord record = new HealthCheckRecord();
        record.checkedAt = result.checkedAt;
        record.score = result.score;
        record.status = status(result);
        record.issueCount = safeIssues(result).size();
        record.articleCount = result.articleCount;
        record.publishedArticleCount = result.publishedArticleCount;
        record.crawledPageCount = result.crawledPageCount;
        record.crawlFailedPageCount = result.crawlFailedPageCount;
        record.brokenLinkCount = result.brokenLinkCount;
        record.seoIssueCount = result.seoIssueCount;
        record.routeIssueCount = result.routeIssueCount;
        record.siteConfigIssueCount = result.siteConfigIssueCount;
        record.publicOutputIssueCount = result.publicOutputIssueCount;
        record.databaseFragmentLabel = result.databaseFragmentLabel;
        return record;
    }

    public Map<String, Object> surfaceData() {
        return surfaceData(readLastResult().orElse(null));
    }

    public Map<String, Object> surfaceData(HealthCheckResult result) {
        Map<String, Object> surface = new LinkedHashMap<>();
        List<HealthCheckRecord> records = readRecords();
        surface.put("version", "1.0");
        surface.put("title", "站点检查");
        surface.put("view", view("打开插件", "index", "index"));
        surface.put("config", readConfig());
        surface.put("actions", surfaceActions(result));
        surface.put("records", records);
        if (result == null) {
            surface.put("description", "尚未执行检查。控制台加载时不会自动扫描。");
            surface.put("status", "normal");
            surface.put("metrics", Arrays.asList(
                    metric("状态", "未检查"),
                    metric("扫描", "手动触发"),
                    metric("记录", records.size()),
                    metric("范围", "页面抓取 / SEO / 文章资源 / 公开输出 / 数据库")
            ));
            surface.put("items", Collections.singletonList(item("idle", "等待手动检查",
                    "点击“立即检查”后才会抓取公开页面并分析基础 SEO 信息。", "normal", Collections.emptyList())));
            return surface;
        }
        List<HealthCheckIssue> issues = safeIssues(result);
        surface.put("result", result);
        surface.put("description", "上次检查 " + formatTime(result.checkedAt) + "，发现 " + issues.size() + " 类问题。");
        surface.put("status", status(result));
        surface.put("metrics", Arrays.asList(
                metric("得分", result.score + "/100"),
                metric("文章", result.publishedArticleCount + "/" + result.articleCount),
                metric("页面", result.crawledPageCount + "/" + (result.crawledPageCount + result.crawlFailedPageCount)),
                metric("问题", issues.size()),
                metric("死链", result.brokenLinkCount),
                metric("SEO 缺失", result.seoIssueCount),
                metric("站点配置", result.siteConfigIssueCount),
                metric("公开输出", result.publicOutputIssueCount),
                metric("记录", records.size()),
                metric("数据库", result.databaseFragmentLabel)
        ));
        surface.put("items", surfaceItems(result));
        return surface;
    }

    private List<Map<String, Object>> surfaceActions(HealthCheckResult result) {
        List<Map<String, Object>> actions = new ArrayList<>();
        actions.add(action("立即检查", "siteCheck:run", "primary"));
        if (result != null && result.canOptimizeDatabase) {
            actions.add(action("数据库维护", "siteCheck:optimize", "default"));
        }
        actions.add(routeAction("文章管理", ARTICLE_ROUTE));
        actions.add(routeAction("网站设置", WEBSITE_ROUTE));
        actions.add(routeAction("robots", WEBSITE_OTHER_ROUTE));
        return actions;
    }

    private List<Map<String, Object>> surfaceItems(HealthCheckResult result) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (HealthCheckIssue issue : safeIssues(result)) {
            List<Map<String, Object>> actions = new ArrayList<>();
            if ("databaseFragment".equals(issue.key) && result.canOptimizeDatabase) {
                actions.add(action("执行维护", "siteCheck:optimize", "primary"));
            }
            if (notBlank(issue.actionRoute)) {
                actions.add(routeAction("前往处理", issue.actionRoute));
            }
            items.add(item(issue.key, issue.title, issueDescription(issue), issueStatus(issue), actions));
            if (items.size() >= 5) {
                break;
            }
        }
        if (items.isEmpty()) {
            items.add(item("healthy", "当前未发现明显问题",
                    "建议在批量导入内容、切换主题或清理附件后再次检查。", "normal", Collections.emptyList()));
        }
        return items;
    }

    private String issueDescription(HealthCheckIssue issue) {
        String sample = firstSample(issue);
        if (notBlank(sample)) {
            return issue.detail + " 示例：" + sample;
        }
        return issue.detail;
    }

    private String firstSample(HealthCheckIssue issue) {
        if (issue.samples != null && !issue.samples.isEmpty()) {
            return issue.samples.get(0);
        }
        if (issue.sampleDetails != null && !issue.sampleDetails.isEmpty()) {
            HealthCheckSample sample = issue.sampleDetails.get(0);
            String text = sampleText(sample.key);
            return notBlank(sample.target) ? sample.target + " / " + text : text;
        }
        return "";
    }

    private String sampleText(String key) {
        switch (key) {
            case "websiteSeoTitleMissing":
                return "站点标题缺失";
            case "websiteSeoDescriptionMissing":
                return "站点描述缺失";
            case "websiteSeoKeywordsMissing":
                return "站点关键词缺失";
            case "pageTitleMissing":
                return "页面 title 缺失";
            case "pageTitleTooShort":
                return "页面 title 过短";
            case "pageTitleTooLong":
                return "页面 title 过长";
            case "pageDescriptionMissing":
                return "meta description 缺失";
            case "pageDescriptionTooShort":
                return "meta description 过短";
            case "pageDescriptionTooLong":
                return "meta description 过长";
            case "pageCanonicalMissing":
                return "canonical 缺失";
            case "pageCanonicalNotAbsolute":
                return "canonical 不是完整 URL";
            case "pageH1Missing":
                return "H1 缺失";
            case "pageH1Multiple":
                return "H1 过多";
            case "pageNoIndex":
                return "页面声明 noindex";
            case "pageViewportMissing":
                return "viewport 缺失";
            case "pageHtmlLangMissing":
                return "html lang 缺失";
            case "pageTitleDuplicate":
                return "页面 title 重复";
            case "pageDescriptionDuplicate":
                return "meta description 重复";
            case "articleSeoDigestMissing":
                return "文章摘要缺失";
            case "articleSeoKeywordsMissing":
                return "文章关键词缺失";
            case "articleAliasMissing":
                return "文章别名为空";
            case "articleAliasDuplicate":
                return "文章别名重复";
            case "articleAliasNumericCollision":
                return "文章别名与数字 ID 冲突";
            case "staticSiteHostMissing":
                return "静态化开启但主页地址为空";
            case "staticSiteHostInvalid":
                return "主页地址不是完整 HTTP/HTTPS 地址";
            case "robotsSitemapMissing":
                return "robots.txt 缺少 Sitemap";
            case "robotsAdminDisallowMissing":
                return "robots.txt 未保护后台路径";
            case "publicOutputMissing":
                return "公开输出文件缺失";
            default:
                return key;
        }
    }

    private List<HealthCheckIssue> safeIssues(HealthCheckResult result) {
        return result == null || result.issues == null ? Collections.emptyList() : result.issues;
    }

    private String status(HealthCheckResult result) {
        for (HealthCheckIssue issue : safeIssues(result)) {
            if ("error".equals(issue.severity)) {
                return "error";
            }
        }
        if (result.score < 60) {
            return "error";
        }
        if (result.score < 85 || !safeIssues(result).isEmpty()) {
            return "warning";
        }
        return "normal";
    }

    private String issueStatus(HealthCheckIssue issue) {
        if ("error".equals(issue.severity)) {
            return "error";
        }
        if ("warning".equals(issue.severity)) {
            return "warning";
        }
        return "normal";
    }

    private Map<String, Object> metric(String label, Object value) {
        Map<String, Object> metric = new LinkedHashMap<>();
        metric.put("label", label);
        metric.put("value", value);
        return metric;
    }

    private Map<String, Object> view(String label, String view, String url) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("label", label);
        map.put("view", view);
        map.put("url", url);
        return map;
    }

    private Map<String, Object> action(String label, String actionRef, String style) {
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("label", label);
        action.put("actionRef", actionRef);
        action.put("style", style);
        return action;
    }

    private Map<String, Object> routeAction(String label, String route) {
        Map<String, Object> action = action(label, "siteCheck:route:" + route, "default");
        action.put("adminRoute", route);
        return action;
    }

    private Map<String, Object> item(String id, String title, String description, String status,
                                     List<Map<String, Object>> actions) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("title", title);
        item.put("description", description);
        item.put("status", status);
        item.put("actions", actions);
        return item;
    }

    private HealthCheckIssue issue(String key, String severity, long count, List<String> samples,
                                   List<HealthCheckSample> sampleDetails, String impact, String actionRoute,
                                   String title, String detail) {
        HealthCheckIssue issue = new HealthCheckIssue();
        issue.key = key;
        issue.severity = severity;
        issue.count = count;
        issue.samples = samples;
        issue.sampleDetails = sampleDetails;
        issue.impact = impact;
        issue.actionRoute = actionRoute;
        issue.title = title;
        issue.detail = detail;
        return issue;
    }

    private List<HealthCheckSuggestion> suggestions(List<HealthCheckIssue> issues) {
        List<HealthCheckSuggestion> suggestions = new ArrayList<>();
        for (HealthCheckIssue issue : issues) {
            if ("brokenLinks".equals(issue.key)) {
                suggestions.add(suggestion("repairBrokenLinks", ARTICLE_ROUTE));
            } else if ("seoMissing".equals(issue.key)) {
                suggestions.add(suggestion("completeSeo", notBlank(issue.actionRoute) ? issue.actionRoute : ARTICLE_ROUTE));
            } else if ("pageCrawlFailed".equals(issue.key) || "pageCrawlUnavailable".equals(issue.key)) {
                suggestions.add(suggestion("reviewSiteHost", WEBSITE_BLOG_ROUTE));
            } else if ("articleRoute".equals(issue.key)) {
                suggestions.add(suggestion("repairArticleRoute", ARTICLE_ROUTE));
            } else if ("staticSiteConfig".equals(issue.key)) {
                suggestions.add(suggestion("reviewStaticSiteConfig", WEBSITE_BLOG_ROUTE));
            } else if ("robotsPolicy".equals(issue.key)) {
                suggestions.add(suggestion("reviewRobotsPolicy", WEBSITE_OTHER_ROUTE));
            } else if ("publicOutput".equals(issue.key)) {
                suggestions.add(suggestion("reviewPublicOutputPlugins", PLUGIN_ROUTE));
            } else if ("databaseFragment".equals(issue.key)) {
                suggestions.add(suggestion("databaseOptimize", null));
            } else if ("directoryWritable".equals(issue.key)) {
                suggestions.add(suggestion("repairDirectoryWritable", SYSTEM_ROUTE));
            }
        }
        if (suggestions.isEmpty()) {
            suggestions.add(suggestion("healthy", null));
        }
        return suggestions;
    }

    private HealthCheckSuggestion suggestion(String key, String actionRoute) {
        HealthCheckSuggestion suggestion = new HealthCheckSuggestion();
        suggestion.key = key;
        suggestion.actionRoute = actionRoute;
        return suggestion;
    }

    private ArticleHealthScanResult scanArticleHealth(SiteCheckConfig config) throws SQLException {
        Map<String, Object> websiteInfo = websiteInfo();
        long siteMissingCount = 0L;
        long articleMissingCount = 0L;
        List<HealthCheckSample> seoSamples = new ArrayList<>();
        if (!notBlank(text(websiteInfo.get("title")))) {
            siteMissingCount++;
            addSample(seoSamples, "websiteSeoTitleMissing", "");
        }
        if (!notBlank(text(websiteInfo.get("description")))) {
            siteMissingCount++;
            addSample(seoSamples, "websiteSeoDescriptionMissing", "");
        }
        if (!notBlank(text(websiteInfo.get("keywords")))) {
            siteMissingCount++;
            addSample(seoSamples, "websiteSeoKeywordsMissing", "");
        }

        boolean staticHtml = toBoolean(websiteInfo.get("generator_html_status"));
        List<Map<String, Object>> rows = SiteCheckDatabase.queryList(session,
                "select logId, title, alias, content, markdown, digest, keywords, privacy, thumbnail from log where rubbish = ?", false);
        long articleCount = rows.size();
        long publishedArticleCount = 0L;
        long brokenLinkCount = 0L;
        LinkedHashSet<String> brokenLinkSamples = new LinkedHashSet<>();
        long routeIssueCount = 0L;
        LinkedHashSet<String> routeIssueSamples = new LinkedHashSet<>();
        Map<String, List<String>> aliasTitles = new LinkedHashMap<>();
        int articleTargetLimit = Math.max(0, config.maxPages - 1);
        List<ArticlePageTarget> articleTargets = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String logId = text(row.get("logId"));
            String articleTitle = text(firstNotEmpty(row.get("title"), row.get("logId")));
            String alias = text(row.get("alias"));
            if (!notBlank(alias)) {
                routeIssueCount++;
                addSample(routeIssueSamples, articleTitle + " / " + sampleText("articleAliasMissing"));
            } else {
                aliasTitles.computeIfAbsent(alias, ignored -> new ArrayList<>()).add(articleTitle);
                if (!Objects.equals(alias, logId) && isPositiveInteger(alias)) {
                    routeIssueCount++;
                    addSample(routeIssueSamples, alias + " / " + sampleText("articleAliasNumericCollision"));
                }
            }

            String content = text(row.get("content"));
            if (notBlank(content)) {
                brokenLinkCount += collectHtmlBrokenLocalAssets(content, brokenLinkSamples);
            }
            String markdown = text(row.get("markdown"));
            if (notBlank(markdown) && !Objects.equals(markdown, content)) {
                brokenLinkCount += collectMarkdownBrokenLocalAssets(markdown, brokenLinkSamples);
            }
            String thumbnail = normalizeUrl(text(row.get("thumbnail")));
            if (isMissingLocalAsset(thumbnail)) {
                brokenLinkCount++;
                addSample(brokenLinkSamples, thumbnail);
            }
            if (toBoolean(row.get("privacy"))) {
                continue;
            }
            publishedArticleCount++;
            if (notBlank(alias) && articleTargets.size() < articleTargetLimit) {
                articleTargets.add(new ArticlePageTarget(articleTitle, articlePath(alias, staticHtml)));
            }
            boolean missingDigest = !notBlank(text(row.get("digest")));
            boolean missingKeywords = !notBlank(text(row.get("keywords")));
            if (missingDigest) {
                articleMissingCount++;
                addSample(seoSamples, "articleSeoDigestMissing", articleTitle);
            }
            if (missingKeywords) {
                articleMissingCount++;
                addSample(seoSamples, "articleSeoKeywordsMissing", articleTitle);
            }
        }
        for (Map.Entry<String, List<String>> entry : aliasTitles.entrySet()) {
            if (entry.getValue().size() <= 1) {
                continue;
            }
            routeIssueCount += entry.getValue().size();
            addSample(routeIssueSamples, entry.getKey() + " / " + sampleText("articleAliasDuplicate"));
        }
        return new ArticleHealthScanResult(
                new BrokenLinkResult(brokenLinkCount, new ArrayList<>(brokenLinkSamples)),
                new SeoResult(siteMissingCount + articleMissingCount, siteMissingCount > 0, seoSamples),
                new IssueScanResult(routeIssueCount, new ArrayList<>(routeIssueSamples)),
                articleTargets,
                articleCount,
                publishedArticleCount
        );
    }

    private Map<String, Object> websiteInfo() throws SQLException {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> rows = websiteRows("title", "description", "keywords", "generator_html_status");
        for (Map<String, Object> row : rows) {
            result.put(text(row.get("name")), row.get("value"));
        }
        return result;
    }

    private List<Map<String, Object>> websiteRows(String... keys) throws SQLException {
        if (keys == null || keys.length == 0) {
            return Collections.emptyList();
        }
        StringBuilder sql = new StringBuilder("select name, value from website where name in (");
        for (int i = 0; i < keys.length; i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("?");
        }
        sql.append(")");
        return SiteCheckDatabase.queryList(session, sql.toString(), (Object[]) keys);
    }

    private Map<String, Object> websiteInfo(String... keys) throws SQLException {
        Map<String, Object> result = new HashMap<>();
        for (Map<String, Object> row : websiteRows(keys)) {
            result.put(text(row.get("name")), row.get("value"));
        }
        return result;
    }

    private PageCrawlResult crawlSitePages(List<ArticlePageTarget> articleTargets, SiteCheckConfig config) {
        SiteCheckConfig normalizedConfig = config == null ? SiteCheckConfig.defaults() : config.normalized();
        String baseUrl = publicBaseUrl();
        LinkedHashMap<String, String> pages = new LinkedHashMap<>();
        if (notBlank(baseUrl)) {
            addCrawlPage(pages, baseUrl, "首页", normalizedConfig.maxPages);
            for (String path : normalizedConfig.extraPathList()) {
                String url = joinUrl(baseUrl, path);
                if (isSameSiteUrl(baseUrl, url)) {
                    addCrawlPage(pages, url, path, normalizedConfig.maxPages);
                }
            }
            for (ArticlePageTarget target : articleTargets) {
                String url = joinUrl(baseUrl, target.path);
                if (isSameSiteUrl(baseUrl, url)) {
                    addCrawlPage(pages, url, target.title, normalizedConfig.maxPages);
                }
            }
        }
        List<HealthCheckSample> seoSamples = new ArrayList<>();
        LinkedHashSet<String> failureSamples = new LinkedHashSet<>();
        Map<String, List<String>> titlePages = new LinkedHashMap<>();
        Map<String, List<String>> descriptionPages = new LinkedHashMap<>();
        int crawledCount = 0;
        long crawlFailureCount = 0L;
        long seoIssueCount = 0L;
        boolean homeIssue = false;
        if (pages.isEmpty()) {
            addSample(failureSamples, "未获取到站点主页地址");
            return new PageCrawlResult(new SeoResult(0L, true, seoSamples),
                    new IssueScanResult(1L, new ArrayList<>(failureSamples)), 0);
        }
        List<String> urls = new ArrayList<>(pages.keySet());
        for (int index = 0; index < urls.size() && index < normalizedConfig.maxPages; index++) {
            String url = urls.get(index);
            String label = pages.get(url);
            try {
                String html = fetchPage(url, normalizedConfig);
                crawledCount++;
                SiteSeoInspector.PageSeoInspection inspection = SiteSeoInspector.inspect(url, label, html, normalizedConfig);
                seoIssueCount += inspection.issueCount;
                homeIssue = homeIssue || (inspection.homeIssue && inspection.issueCount > 0);
                addSamples(seoSamples, inspection.samples);
                if (normalizedConfig.checkDuplicateMeta) {
                    if (notBlank(inspection.title)) {
                        addGroupedSample(titlePages, inspection.title, notBlank(label) ? label : url);
                    }
                    if (notBlank(inspection.description)) {
                        addGroupedSample(descriptionPages, inspection.description, notBlank(label) ? label : url);
                    }
                }
                if (index == 0 && pages.size() < normalizedConfig.maxPages) {
                    for (Map.Entry<String, String> discovered : discoverCrawlLinks(baseUrl, html).entrySet()) {
                        if (addCrawlPage(pages, discovered.getKey(), discovered.getValue(), normalizedConfig.maxPages)) {
                            urls.add(discovered.getKey());
                        }
                    }
                }
            } catch (Exception e) {
                crawlFailureCount++;
                addSample(failureSamples, url + " / " + shortError(e));
            }
        }
        if (normalizedConfig.checkDuplicateMeta) {
            seoIssueCount += addDuplicateSamples(titlePages, "pageTitleDuplicate", seoSamples);
            seoIssueCount += addDuplicateSamples(descriptionPages, "pageDescriptionDuplicate", seoSamples);
        }
        return new PageCrawlResult(new SeoResult(seoIssueCount, homeIssue || hasHomeIssue(seoSamples), seoSamples),
                new IssueScanResult(crawlFailureCount, new ArrayList<>(failureSamples)), crawledCount);
    }

    private String publicBaseUrl() {
        String homeUrl = "";
        try {
            PublicInfo publicInfo = session.getResponseSync(ContentType.JSON, new HashMap<>(),
                    ActionType.LOAD_PUBLIC_INFO, PublicInfo.class);
            if (publicInfo != null) {
                homeUrl = text(publicInfo.getHomeUrl());
            }
        } catch (Exception ignored) {
            homeUrl = "";
        }
        if (!isHttpUrl(homeUrl)) {
            try {
                Map<String, Object> info = websiteInfo("host");
                homeUrl = normalizeHost(text(info.get("host")));
            } catch (SQLException ignored) {
                homeUrl = "";
            }
        }
        return trimTrailingSlash(isHttpUrl(homeUrl) ? homeUrl : "");
    }

    private String normalizeHost(String host) {
        if (!notBlank(host)) {
            return "";
        }
        if (host.startsWith("http://") || host.startsWith("https://")) {
            return host;
        }
        return "http://" + host;
    }

    private String articlePath(String alias, boolean staticHtml) {
        String path = "/" + alias;
        return staticHtml ? path + ".html" : path;
    }

    private String fetchPage(String url, SiteCheckConfig config) {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", config.userAgent);
        return HttpClientUtils.sendGetRequest(url, String.class, headers, session,
                Duration.ofSeconds(config.timeoutSeconds));
    }

    private boolean addCrawlPage(LinkedHashMap<String, String> pages, String url, String label, int maxPages) {
        if (pages.size() >= maxPages || !isHttpUrl(url)) {
            return false;
        }
        String normalizedUrl = trimTrailingSlash(stripFragment(text(url)));
        if (!notBlank(normalizedUrl) || pages.containsKey(normalizedUrl)) {
            return false;
        }
        pages.put(normalizedUrl, notBlank(label) ? label : normalizedUrl);
        return true;
    }

    private LinkedHashMap<String, String> discoverCrawlLinks(String baseUrl, String html) {
        LinkedHashMap<String, String> links = new LinkedHashMap<>();
        try {
            URI baseUri = new URI(trimTrailingSlash(baseUrl) + "/");
            Document document = Jsoup.parse(html, baseUri.toString());
            for (Element element : document.select("a[href]")) {
                String href = normalizeUrl(text(element.attr("href")));
                if (!shouldDiscoverHref(href)) {
                    continue;
                }
                URI resolved = baseUri.resolve(href);
                if (!sameOrigin(baseUri, resolved) || shouldSkipCrawlPath(text(resolved.getPath()))) {
                    continue;
                }
                String url = trimTrailingSlash(stripFragment(resolved.toString()));
                if (!notBlank(url) || links.containsKey(url)) {
                    continue;
                }
                String label = text(element.text());
                links.put(url, notBlank(label) ? label : text(resolved.getPath()));
            }
        } catch (Exception ignored) {
            return links;
        }
        return links;
    }

    private boolean shouldDiscoverHref(String href) {
        if (!notBlank(href) || href.startsWith("#")) {
            return false;
        }
        String lowerCase = href.toLowerCase(Locale.ROOT);
        return !(lowerCase.startsWith("mailto:") || lowerCase.startsWith("tel:")
                || lowerCase.startsWith("javascript:") || lowerCase.startsWith("data:")
                || lowerCase.startsWith("//"));
    }

    private boolean sameOrigin(URI baseUri, URI resolved) {
        String baseScheme = text(baseUri.getScheme()).toLowerCase(Locale.ROOT);
        String resolvedScheme = text(resolved.getScheme()).toLowerCase(Locale.ROOT);
        return Objects.equals(baseScheme, resolvedScheme)
                && Objects.equals(text(baseUri.getHost()).toLowerCase(Locale.ROOT),
                text(resolved.getHost()).toLowerCase(Locale.ROOT))
                && effectivePort(baseUri) == effectivePort(resolved);
    }

    private boolean isSameSiteUrl(String baseUrl, String url) {
        try {
            return sameOrigin(new URI(trimTrailingSlash(baseUrl) + "/"), new URI(url));
        } catch (Exception ignored) {
            return false;
        }
    }

    private int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        String scheme = text(uri.getScheme()).toLowerCase(Locale.ROOT);
        if ("https".equals(scheme)) {
            return 443;
        }
        if ("http".equals(scheme)) {
            return 80;
        }
        return -1;
    }

    private boolean shouldSkipCrawlPath(String path) {
        String lowerCase = text(path).toLowerCase(Locale.ROOT);
        if (!notBlank(lowerCase) || "/".equals(lowerCase)) {
            return false;
        }
        if (lowerCase.startsWith("/admin") || lowerCase.startsWith("/api") || lowerCase.startsWith("/plugin")) {
            return true;
        }
        return lowerCase.matches(".*\\.(css|js|json|xml|txt|png|jpe?g|gif|webp|svg|ico|pdf|zip|gz|mp4|mp3|woff2?)$");
    }

    private String stripFragment(String url) {
        int hashIndex = text(url).indexOf('#');
        return hashIndex >= 0 ? text(url).substring(0, hashIndex) : text(url);
    }

    private void addGroupedSample(Map<String, List<String>> groupedSamples, String value, String target) {
        groupedSamples.computeIfAbsent(value, ignored -> new ArrayList<>()).add(target);
    }

    private long addDuplicateSamples(Map<String, List<String>> groupedSamples, String sampleKey,
                                     List<HealthCheckSample> samples) {
        long count = 0L;
        for (Map.Entry<String, List<String>> entry : groupedSamples.entrySet()) {
            if (entry.getValue().size() > 1) {
                count++;
                addSample(samples, sampleKey, String.join(", ", entry.getValue()));
            }
        }
        return count;
    }

    private boolean hasHomeIssue(List<HealthCheckSample> samples) {
        for (HealthCheckSample sample : samples) {
            if (!notBlank(sample.target)) {
                return true;
            }
            if ("首页".equals(sample.target) || sample.target.startsWith("首页,")) {
                return true;
            }
        }
        return false;
    }

    private String joinUrl(String baseUrl, String path) {
        if (!notBlank(path)) {
            return baseUrl;
        }
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return trimTrailingSlash(baseUrl) + normalizedPath;
    }

    private String trimTrailingSlash(String value) {
        String result = text(value);
        while (result.endsWith("/") && result.length() > 1) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String shortError(Exception e) {
        String message = text(e.getMessage());
        if (!notBlank(message)) {
            return e.getClass().getSimpleName();
        }
        return message.length() > 140 ? message.substring(0, 140) : message;
    }

    private long collectHtmlBrokenLocalAssets(String content, LinkedHashSet<String> samples) {
        long count = 0L;
        Document document = Jsoup.parseBodyFragment(content);
        for (Element element : document.select("[src], a[href]")) {
            String attr = element.hasAttr("src") ? "src" : "href";
            String url = normalizeUrl(element.attr(attr));
            if (isMissingLocalAsset(url)) {
                count++;
                addSample(samples, url);
            }
        }
        return count;
    }

    private long collectMarkdownBrokenLocalAssets(String markdown, LinkedHashSet<String> samples) {
        long count = 0L;
        Matcher matcher = MARKDOWN_LINK_PATTERN.matcher(markdown);
        while (matcher.find()) {
            String url = normalizeUrl(matcher.group(1));
            if (isMissingLocalAsset(url)) {
                count++;
                addSample(samples, url);
            }
        }
        return count;
    }

    private boolean isMissingLocalAsset(String url) {
        if (!shouldCheckAsLocalAsset(url)) {
            return false;
        }
        File file = staticFile(url);
        return file == null || !file.exists();
    }

    private IssueScanResult inspectPublicOutput(long publishedArticleCount) throws SQLException {
        if (publishedArticleCount <= 0) {
            return IssueScanResult.empty();
        }
        List<String> samples = new ArrayList<>();
        Map<String, String> outputPaths = publicOutputPaths();
        for (Map.Entry<String, String> entry : outputPaths.entrySet()) {
            File file = staticFile(entry.getValue());
            if (file == null || !file.exists() || !file.isFile() || file.length() <= 0) {
                samples.add(entry.getValue() + " / " + sampleText("publicOutputMissing"));
            }
        }
        return new IssueScanResult(samples.size(), samples);
    }

    private Map<String, String> publicOutputPaths() throws SQLException {
        Map<String, Object> info = websiteInfo("sitemap_uriPath", "rss_uriPath");
        Map<String, String> outputPaths = new LinkedHashMap<>();
        outputPaths.put("sitemap", configuredOutputPath(info.get("sitemap_uriPath"), DEFAULT_SITEMAP_URI_PATH));
        outputPaths.put("rss", configuredOutputPath(info.get("rss_uriPath"), DEFAULT_RSS_URI_PATH));
        return outputPaths;
    }

    private String configuredOutputPath(Object value, String defaultPath) {
        String uriPath = normalizeUrl(text(value));
        if (!notBlank(uriPath)) {
            return defaultPath;
        }
        if (uriPath.startsWith("http://") || uriPath.startsWith("https://") || uriPath.startsWith("//")) {
            return defaultPath;
        }
        return uriPath.startsWith("/") ? uriPath : "/" + uriPath;
    }

    private IssueScanResult inspectStaticSiteConfig() throws SQLException {
        Map<String, Object> info = websiteInfo("generator_html_status", "host");
        if (!toBoolean(info.get("generator_html_status"))) {
            return IssueScanResult.empty();
        }
        String host = text(info.get("host"));
        List<String> samples = new ArrayList<>();
        if (!notBlank(host)) {
            samples.add(sampleText("staticSiteHostMissing"));
        } else if (!isHttpUrl(host)) {
            samples.add(host + " / " + sampleText("staticSiteHostInvalid"));
        }
        return new IssueScanResult(samples.size(), samples);
    }

    private IssueScanResult inspectRobotPolicy() throws SQLException {
        Map<String, Object> info = websiteInfo("robotRuleContent");
        String robots = text(info.get("robotRuleContent"));
        if (!notBlank(robots)) {
            return IssueScanResult.empty();
        }
        List<String> samples = new ArrayList<>();
        String lowerCase = robots.toLowerCase(Locale.ROOT);
        if (!lowerCase.matches("(?ms).*^\\s*sitemap\\s*:.*")) {
            samples.add(sampleText("robotsSitemapMissing"));
        }
        if (!hasAdminDisallow(robots)) {
            samples.add(sampleText("robotsAdminDisallowMissing"));
        }
        return new IssueScanResult(samples.size(), samples);
    }

    private boolean hasAdminDisallow(String robots) {
        for (String line : robots.split("\\r?\\n")) {
            String normalized = line.trim().toLowerCase(Locale.ROOT);
            if (normalized.startsWith("disallow:")) {
                String value = normalized.substring("disallow:".length()).trim();
                if (value.equals("/admin") || value.startsWith("/admin/")) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isHttpUrl(String value) {
        try {
            URI uri = new URI(value);
            String scheme = text(uri.getScheme()).toLowerCase(Locale.ROOT);
            return ("http".equals(scheme) || "https".equals(scheme)) && notBlank(uri.getHost());
        } catch (Exception ignored) {
            return false;
        }
    }

    private File staticFile(String url) {
        try {
            String relativePath = url.startsWith("/") ? url.substring(1) : url;
            File root = staticRoot().getCanonicalFile();
            File file = new File(root, relativePath).getCanonicalFile();
            return isInsideRoot(file, root) ? file : null;
        } catch (IOException e) {
            return null;
        }
    }

    private File staticRoot() {
        if (cachedStaticRoot != null) {
            return cachedStaticRoot;
        }
        BlogRunTime blogRunTime = session.getResponseSync(ContentType.JSON, new HashMap<>(),
                ActionType.BLOG_RUN_TIME, BlogRunTime.class);
        String path = blogRunTime == null ? "" : text(blogRunTime.getPath());
        cachedStaticRoot = notBlank(path) ? new File(path) : new File(PathKit.getStaticPath());
        return cachedStaticRoot;
    }

    private boolean isInsideRoot(File file, File root) throws IOException {
        String rootPath = root.getCanonicalPath();
        String filePath = file.getCanonicalPath();
        return filePath.equals(rootPath) || filePath.startsWith(rootPath + File.separator);
    }

    private boolean shouldCheckAsLocalAsset(String url) {
        if (!notBlank(url)) {
            return false;
        }
        String lowerCase = url.toLowerCase(Locale.ROOT);
        if (lowerCase.startsWith("http://") || lowerCase.startsWith("https://") || lowerCase.startsWith("//")
                || lowerCase.startsWith("mailto:") || lowerCase.startsWith("tel:") || lowerCase.startsWith("javascript:")
                || lowerCase.startsWith("data:") || lowerCase.startsWith("#")) {
            return false;
        }
        if (lowerCase.startsWith("./") || lowerCase.startsWith("../")) {
            return false;
        }
        if (lowerCase.startsWith("/attached")) {
            return true;
        }
        return lowerCase.startsWith("/") && lowerCase.matches(".*\\.[a-z0-9]{2,8}$");
    }

    private String normalizeUrl(String url) {
        if (!notBlank(url)) {
            return "";
        }
        String normalized = url.trim();
        int queryIndex = normalized.indexOf('?');
        if (queryIndex >= 0) {
            normalized = normalized.substring(0, queryIndex);
        }
        int hashIndex = normalized.indexOf('#');
        if (hashIndex >= 0) {
            normalized = normalized.substring(0, hashIndex);
        }
        return normalized;
    }

    private void addSample(List<HealthCheckSample> samples, String key, String target) {
        if (samples.size() < SAMPLE_LIMIT) {
            HealthCheckSample sample = new HealthCheckSample();
            sample.key = key;
            sample.target = target;
            samples.add(sample);
        }
    }

    private void addSamples(List<HealthCheckSample> samples, List<HealthCheckSample> nextSamples) {
        if (nextSamples == null || nextSamples.isEmpty()) {
            return;
        }
        for (HealthCheckSample sample : nextSamples) {
            if (samples.size() >= SAMPLE_LIMIT) {
                return;
            }
            samples.add(sample);
        }
    }

    private void addSample(LinkedHashSet<String> samples, String sample) {
        if (samples.size() < SAMPLE_LIMIT && notBlank(sample)) {
            samples.add(sample);
        }
    }

    private boolean isPositiveInteger(String value) {
        if (!notBlank(value)) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private DatabaseFragmentResult inspectDatabaseFragment() throws SQLException {
        DatabaseEngine engine = detectDatabaseEngine();
        DataSourceWrapperImpl dataSourceWrapper = SiteCheckDatabase.dataSource(session);
        if (Objects.isNull(dataSourceWrapper)) {
            return new DatabaseFragmentResult(engine, "Unknown", 0L, "Unknown", Collections.emptyList(), false,
                    false, DATABASE_OPTIMIZE_UNAVAILABLE);
        }
        if (dataSourceWrapper.isWebApi()) {
            return new DatabaseFragmentResult(DatabaseEngine.WEBAPI, "webapi / D1", 0L, "N/A",
                    Collections.emptyList(), false, false, DATABASE_OPTIMIZE_UNSUPPORTED_WEBAPI);
        }
        switch (engine) {
            case SQLITE:
                long freelistCount = toLong(SiteCheckDatabase.queryFirstObj(session, "PRAGMA freelist_count"));
                long pageSize = toLong(SiteCheckDatabase.queryFirstObj(session, "PRAGMA page_size"));
                long reclaimableBytes = freelistCount * pageSize;
                return new DatabaseFragmentResult(engine, "SQLite", reclaimableBytes, formatFileSize(reclaimableBytes),
                        Collections.emptyList(), true, true, null);
            case MYSQL:
                long dataFree = toLong(SiteCheckDatabase.queryFirstObj(session,
                        "SELECT COALESCE(SUM(data_free), 0) FROM information_schema.tables WHERE table_schema = DATABASE()"
                ));
                return new DatabaseFragmentResult(engine, "MySQL/MariaDB", dataFree, formatFileSize(dataFree),
                        Collections.emptyList(), true, true, null);
            case POSTGRESQL:
                long deadTuples = toLong(SiteCheckDatabase.queryFirstObj(session,
                        "SELECT COALESCE(SUM(n_dead_tup), 0) FROM pg_stat_user_tables"));
                return new DatabaseFragmentResult(engine, "PostgreSQL", deadTuples, Long.toString(deadTuples),
                        Collections.emptyList(), true, true, null);
            case H2:
                return new DatabaseFragmentResult(engine, "H2", 0L, "N/A", Collections.emptyList(), false,
                        false, DATABASE_OPTIMIZE_UNSUPPORTED_ENGINE);
            default:
                return new DatabaseFragmentResult(engine, "Unknown", 0L, "Unknown", Collections.emptyList(), false,
                        false, DATABASE_OPTIMIZE_UNSUPPORTED_ENGINE);
        }
    }

    private void runDatabaseOptimize(DatabaseEngine engine) throws SQLException {
        DataSourceWrapperImpl dataSourceWrapper = SiteCheckDatabase.dataSource(session);
        if (Objects.isNull(dataSourceWrapper) || dataSourceWrapper.isWebApi()) {
            return;
        }
        switch (engine) {
            case SQLITE:
                SiteCheckDatabase.executeStatement(session, "VACUUM");
                return;
            case MYSQL:
                List<Map<String, Object>> tables = SiteCheckDatabase.queryList(session,
                        "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'");
                for (Map<String, Object> table : tables) {
                    String tableName = text(table.get("table_name"));
                    if (notBlank(tableName)) {
                        SiteCheckDatabase.executeStatement(session, "OPTIMIZE TABLE `" + tableName.replace("`", "") + "`");
                    }
                }
                return;
            case POSTGRESQL:
                SiteCheckDatabase.executeStatement(session, "VACUUM ANALYZE");
                return;
            default:
        }
    }

    private DatabaseEngine detectDatabaseEngine() {
        DataSourceWrapperImpl dataSourceWrapper = SiteCheckDatabase.dataSource(session);
        if (Objects.isNull(dataSourceWrapper)) {
            return DatabaseEngine.UNKNOWN;
        }
        if (dataSourceWrapper.isWebApi()) {
            return DatabaseEngine.WEBAPI;
        }
        Properties properties = SiteCheckDatabase.properties(session);
        String jdbcUrl = text(properties == null ? "" : properties.get("jdbcUrl")).toLowerCase(Locale.ROOT);
        if (jdbcUrl.contains(":sqlite:")) {
            return DatabaseEngine.SQLITE;
        }
        if (jdbcUrl.contains(":mysql:") || jdbcUrl.contains(":mariadb:")) {
            return DatabaseEngine.MYSQL;
        }
        if (jdbcUrl.contains(":postgresql:")) {
            return DatabaseEngine.POSTGRESQL;
        }
        if (jdbcUrl.contains(":h2:")) {
            return DatabaseEngine.H2;
        }
        String dbInfo = text(dataSourceWrapper.getDbInfo()).toLowerCase(Locale.ROOT);
        if (dbInfo.contains("sqlite")) {
            return DatabaseEngine.SQLITE;
        }
        if (dbInfo.contains("mysql") || dbInfo.contains("mariadb")) {
            return DatabaseEngine.MYSQL;
        }
        if (dbInfo.contains("postgresql")) {
            return DatabaseEngine.POSTGRESQL;
        }
        if (dbInfo.contains("h2")) {
            return DatabaseEngine.H2;
        }
        return DatabaseEngine.UNKNOWN;
    }

    private DirectoryWritableResult inspectDirectoryWritable() {
        List<String> samples = new ArrayList<>();
        checkDirectoryWritable("static", staticRoot(), samples);
        checkDirectoryWritable("tmp", new File(PathKit.getTmpPath()), samples);
        return new DirectoryWritableResult(samples.size(), samples);
    }

    private void checkDirectoryWritable(String key, File directory, List<String> samples) {
        if (!directory.exists() && !directory.mkdirs() && !directory.isDirectory()) {
            samples.add(key + ": " + directory + " (cannot create directory)");
            return;
        }
        if (!directory.isDirectory()) {
            samples.add(key + ": " + directory + " (not a directory)");
            return;
        }
        String failureReason = tryWriteProbe(directory);
        if (notBlank(failureReason)) {
            samples.add(key + ": " + directory + " (" + failureReason + ")");
        }
    }

    private String tryWriteProbe(File directory) {
        File probeFile = new File(directory, ".zrlog-site-check-" + UUID.randomUUID() + ".tmp");
        try {
            if (!probeFile.createNewFile()) {
                return "cannot create probe file";
            }
            java.nio.file.Files.write(probeFile.toPath(), Collections.singletonList("site-check"));
            if (!probeFile.delete()) {
                return "probe file cannot be deleted";
            }
            return "";
        } catch (IOException e) {
            if (probeFile.exists() && !probeFile.delete()) {
                probeFile.deleteOnExit();
            }
            return e.getClass().getSimpleName() + ": " + text(e.getMessage());
        }
    }

    private int buildScore(long brokenLinks, long seoIssues, boolean hasDatabaseFragment, boolean hasDirectoryWritableIssue,
                           long routeIssues, long publicOutputIssues, long siteConfigIssues, boolean crawlUnavailable) {
        int score = 100;
        score -= Math.min(30, (int) brokenLinks * 5);
        score -= Math.min(25, (int) seoIssues * 3);
        score -= Math.min(15, (int) routeIssues * 5);
        score -= Math.min(12, (int) siteConfigIssues * 6);
        score -= Math.min(8, (int) publicOutputIssues * 2);
        if (crawlUnavailable) {
            score -= 12;
        }
        if (hasDatabaseFragment) {
            score -= 10;
        }
        if (hasDirectoryWritableIssue) {
            score -= 20;
        }
        return Math.max(score, 0);
    }

    private String formatFileSize(long fileS) {
        DecimalFormat df = new DecimalFormat("#.00");
        if (fileS < 1024) {
            return df.format((double) fileS) + "B";
        } else if (fileS < 1048576) {
            return df.format((double) fileS / 1024) + "K";
        } else if (fileS < 1073741824) {
            return df.format((double) fileS / 1048576) + "M";
        } else if (fileS < 1099511627776L) {
            return df.format((double) fileS / 1073741824) + "G";
        }
        return df.format((double) fileS / 1099511627776L) + "T";
    }

    private String formatTime(long timestamp) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(timestamp));
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        String text = text(value).toLowerCase(Locale.ROOT);
        return "true".equals(text) || "1".equals(text) || "yes".equals(text);
    }

    private Object firstNotEmpty(Object first, Object second) {
        return notBlank(text(first)) ? first : second;
    }

    private long toLong(Object value) {
        if (Objects.isNull(value)) {
            return 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.parseLong(value.toString());
    }

    private String text(Object value) {
        return Objects.toString(value, "").trim();
    }

    private boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private enum DatabaseEngine {
        WEBAPI, SQLITE, MYSQL, POSTGRESQL, H2, UNKNOWN
    }

    private static class BrokenLinkResult {
        private final long count;
        private final List<String> samples;

        private BrokenLinkResult(long count, List<String> samples) {
            this.count = count;
            this.samples = samples;
        }
    }

    private static class ArticleHealthScanResult {
        private final BrokenLinkResult brokenLinks;
        private final SeoResult seo;
        private final IssueScanResult routeIntegrity;
        private final List<ArticlePageTarget> articleTargets;
        private final long articleCount;
        private final long publishedArticleCount;

        private ArticleHealthScanResult(BrokenLinkResult brokenLinks, SeoResult seo, IssueScanResult routeIntegrity,
                                        List<ArticlePageTarget> articleTargets, long articleCount,
                                        long publishedArticleCount) {
            this.brokenLinks = brokenLinks;
            this.seo = seo;
            this.routeIntegrity = routeIntegrity;
            this.articleTargets = articleTargets;
            this.articleCount = articleCount;
            this.publishedArticleCount = publishedArticleCount;
        }
    }

    private static class SeoResult {
        private final long count;
        private final boolean hasHomeIssue;
        private final List<HealthCheckSample> samples;

        private SeoResult(long count, boolean hasHomeIssue, List<HealthCheckSample> samples) {
            this.count = count;
            this.hasHomeIssue = hasHomeIssue;
            this.samples = samples;
        }
    }

    private static class ArticlePageTarget {
        private final String title;
        private final String path;

        private ArticlePageTarget(String title, String path) {
            this.title = title;
            this.path = path;
        }
    }

    private static class PageCrawlResult {
        private final SeoResult seo;
        private final IssueScanResult failure;
        private final int crawledCount;

        private PageCrawlResult(SeoResult seo, IssueScanResult failure, int crawledCount) {
            this.seo = seo;
            this.failure = failure;
            this.crawledCount = crawledCount;
        }
    }

    private static class IssueScanResult {
        private final long count;
        private final List<String> samples;

        private IssueScanResult(long count, List<String> samples) {
            this.count = count;
            this.samples = samples;
        }

        private static IssueScanResult empty() {
            return new IssueScanResult(0L, Collections.emptyList());
        }
    }

    private static class DatabaseFragmentResult {
        private final DatabaseEngine engine;
        private final String engineLabel;
        private final long fragmentValue;
        private final String fragmentLabel;
        private final List<String> samples;
        private final boolean fragmentInspectable;
        private final boolean canOptimize;
        private final String optimizeUnsupportedReason;

        private DatabaseFragmentResult(DatabaseEngine engine, String engineLabel, long fragmentValue, String fragmentLabel,
                                       List<String> samples, boolean fragmentInspectable, boolean canOptimize,
                                       String optimizeUnsupportedReason) {
            this.engine = engine;
            this.engineLabel = engineLabel;
            this.fragmentValue = fragmentValue;
            this.fragmentLabel = fragmentLabel;
            this.samples = samples;
            this.fragmentInspectable = fragmentInspectable;
            this.canOptimize = canOptimize;
            this.optimizeUnsupportedReason = optimizeUnsupportedReason;
        }
    }

    private static class DirectoryWritableResult {
        private final long count;
        private final List<String> samples;

        private DirectoryWritableResult(long count, List<String> samples) {
            this.count = count;
            this.samples = samples;
        }
    }

    public static class HealthCheckResult {
        public long checkedAt;
        public int score;
        public long articleCount;
        public long publishedArticleCount;
        public int crawledPageCount;
        public long crawlFailedPageCount;
        public long brokenLinkCount;
        public long seoIssueCount;
        public long routeIssueCount;
        public long siteConfigIssueCount;
        public long publicOutputIssueCount;
        public long databaseFragmentValue;
        public String databaseFragmentLabel;
        public String databaseEngine;
        public boolean databaseFragmentInspectable;
        public boolean canOptimizeDatabase;
        public String databaseOptimizeUnsupportedReason;
        public List<HealthCheckIssue> issues;
        public List<HealthCheckSuggestion> suggestions;
    }

    public static class HealthCheckRecord {
        public long checkedAt;
        public int score;
        public String status;
        public int issueCount;
        public long articleCount;
        public long publishedArticleCount;
        public int crawledPageCount;
        public long crawlFailedPageCount;
        public long brokenLinkCount;
        public long seoIssueCount;
        public long routeIssueCount;
        public long siteConfigIssueCount;
        public long publicOutputIssueCount;
        public String databaseFragmentLabel;
    }

    public static class HealthCheckIssue {
        public String key;
        public String severity;
        public String impact;
        public long count;
        public List<String> samples;
        public List<HealthCheckSample> sampleDetails;
        public String actionRoute;
        public String title;
        public String detail;
    }

    public static class HealthCheckSample {
        public String key;
        public String target;
    }

    public static class HealthCheckSuggestion {
        public String key;
        public String actionRoute;
    }
}
