package com.zrlog.plugin.sitecheck.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class SiteCheckConfig {

    public static final int DEFAULT_MAX_PAGES = 12;
    public static final int DEFAULT_TIMEOUT_SECONDS = 12;
    public static final String DEFAULT_USER_AGENT = "ZrLog SiteCheck/4.0";

    public int maxPages = DEFAULT_MAX_PAGES;
    public int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
    public boolean requireCanonical = true;
    public boolean requireH1 = true;
    public boolean checkDuplicateMeta = true;
    public boolean checkLengthGuidance = true;
    public String extraPaths = "";
    public String userAgent = DEFAULT_USER_AGENT;

    public static SiteCheckConfig defaults() {
        return new SiteCheckConfig().normalized();
    }

    public SiteCheckConfig normalized() {
        maxPages = clamp(maxPages, 1, 50);
        timeoutSeconds = clamp(timeoutSeconds, 3, 30);
        extraPaths = limit(text(extraPaths), 2000);
        userAgent = limit(text(userAgent), 120);
        if (!notBlank(userAgent)) {
            userAgent = DEFAULT_USER_AGENT;
        }
        return this;
    }

    public List<String> extraPathList() {
        List<String> paths = new ArrayList<>();
        String normalized = text(extraPaths).replace('\r', '\n').replace(',', '\n');
        if (!notBlank(normalized)) {
            return paths;
        }
        for (String raw : normalized.split("\\n")) {
            String path = text(raw);
            if (!notBlank(path) || path.startsWith("#")) {
                continue;
            }
            String lower = path.toLowerCase(Locale.ROOT);
            if (lower.startsWith("mailto:") || lower.startsWith("tel:") || lower.startsWith("javascript:")
                    || lower.startsWith("data:") || lower.startsWith("//")) {
                continue;
            }
            if (!lower.startsWith("http://") && !lower.startsWith("https://") && !path.startsWith("/")) {
                path = "/" + path;
            }
            if (!paths.contains(path)) {
                paths.add(path);
            }
        }
        return paths;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String limit(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private static String text(Object value) {
        return Objects.toString(value, "").trim();
    }

    private static boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
