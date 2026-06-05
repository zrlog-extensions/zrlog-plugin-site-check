package com.zrlog.plugin.sitecheck.service;

import com.zrlog.plugin.sitecheck.service.SiteCheckService.HealthCheckSample;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

final class SiteSeoInspector {

    private static final int MIN_TITLE_LENGTH = 8;
    private static final int MAX_TITLE_LENGTH = 90;
    private static final int MIN_DESCRIPTION_LENGTH = 30;
    private static final int MAX_DESCRIPTION_LENGTH = 220;

    private SiteSeoInspector() {
    }

    static PageSeoInspection inspect(String url, String label, String html, SiteCheckConfig config) {
        SiteCheckConfig normalizedConfig = config == null ? SiteCheckConfig.defaults() : config.normalized();
        Document document = Jsoup.parse(Objects.toString(html, ""), url);
        String target = notBlank(label) ? label : url;
        List<HealthCheckSample> samples = new ArrayList<>();
        String title = text(document.title());
        if (!notBlank(title)) {
            addSample(samples, "pageTitleMissing", target);
        } else if (normalizedConfig.checkLengthGuidance) {
            if (title.length() < MIN_TITLE_LENGTH) {
                addSample(samples, "pageTitleTooShort", target);
            } else if (title.length() > MAX_TITLE_LENGTH) {
                addSample(samples, "pageTitleTooLong", target);
            }
        }

        Element descriptionMeta = document.selectFirst("meta[name=description]");
        String description = text(descriptionMeta == null ? "" : descriptionMeta.attr("content"));
        if (!notBlank(description)) {
            addSample(samples, "pageDescriptionMissing", target);
        } else if (normalizedConfig.checkLengthGuidance) {
            if (description.length() < MIN_DESCRIPTION_LENGTH) {
                addSample(samples, "pageDescriptionTooShort", target);
            } else if (description.length() > MAX_DESCRIPTION_LENGTH) {
                addSample(samples, "pageDescriptionTooLong", target);
            }
        }

        if (normalizedConfig.requireCanonical) {
            Element canonical = document.selectFirst("link[rel=canonical]");
            String canonicalHref = canonical == null ? "" : text(canonical.attr("href"));
            if (!notBlank(canonicalHref)) {
                addSample(samples, "pageCanonicalMissing", target);
            } else if (!isHttpUrl(canonicalHref)) {
                addSample(samples, "pageCanonicalNotAbsolute", target);
            }
        }

        if (normalizedConfig.requireH1) {
            int h1Count = document.select("h1").size();
            if (h1Count == 0) {
                addSample(samples, "pageH1Missing", target);
            } else if (h1Count > 1) {
                addSample(samples, "pageH1Multiple", target);
            }
        }

        Element robots = document.selectFirst("meta[name=robots], meta[name=googlebot]");
        if (robots != null && robots.attr("content").toLowerCase(Locale.ROOT).contains("noindex")) {
            addSample(samples, "pageNoIndex", target);
        }
        if (document.selectFirst("meta[name=viewport]") == null) {
            addSample(samples, "pageViewportMissing", target);
        }
        if (!notBlank(document.selectFirst("html") == null ? "" : document.selectFirst("html").attr("lang"))) {
            addSample(samples, "pageHtmlLangMissing", target);
        }
        return new PageSeoInspection(samples.size(), isHomePage(url) || "首页".equals(target), title, description, samples);
    }

    private static void addSample(List<HealthCheckSample> samples, String key, String target) {
        HealthCheckSample sample = new HealthCheckSample();
        sample.key = key;
        sample.target = target;
        samples.add(sample);
    }

    private static boolean isHttpUrl(String value) {
        try {
            URI uri = new URI(value);
            String scheme = text(uri.getScheme()).toLowerCase(Locale.ROOT);
            return ("http".equals(scheme) || "https".equals(scheme)) && notBlank(uri.getHost());
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isHomePage(String url) {
        try {
            URI uri = new URI(url);
            String path = text(uri.getPath());
            return !notBlank(path) || "/".equals(path);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String text(Object value) {
        return Objects.toString(value, "").trim();
    }

    private static boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    static final class PageSeoInspection {
        final long issueCount;
        final boolean homeIssue;
        final String title;
        final String description;
        final List<HealthCheckSample> samples;

        private PageSeoInspection(long issueCount, boolean homeIssue, String title, String description,
                                  List<HealthCheckSample> samples) {
            this.issueCount = issueCount;
            this.homeIssue = homeIssue;
            this.title = title;
            this.description = description;
            this.samples = samples;
        }
    }
}
