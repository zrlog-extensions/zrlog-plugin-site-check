package com.zrlog.plugin.sitecheck.service;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SiteSeoInspectorTest {

    @Test
    public void acceptsCompleteSeoPage() {
        String html = "<!doctype html><html lang=\"zh-CN\"><head>"
                + "<title>商业可用的站点检查页面</title>"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"/>"
                + "<meta name=\"description\" content=\"这是一段足够清晰的页面描述，用于说明站点检查插件的公开页面 SEO 质量。\"/>"
                + "<link rel=\"canonical\" href=\"https://example.com/about\"/>"
                + "</head><body><h1>站点检查</h1></body></html>";

        SiteSeoInspector.PageSeoInspection inspection = SiteSeoInspector.inspect(
                "https://example.com/about", "关于", html, SiteCheckConfig.defaults());

        assertEquals(0L, inspection.issueCount);
        assertEquals("商业可用的站点检查页面", inspection.title);
        assertTrue(inspection.samples.isEmpty());
    }

    @Test
    public void reportsMissingCoreSeoSignals() {
        String html = "<html><head><title>x</title></head><body><h1>A</h1><h1>B</h1></body></html>";

        SiteSeoInspector.PageSeoInspection inspection = SiteSeoInspector.inspect(
                "https://example.com/", "首页", html, SiteCheckConfig.defaults());

        assertTrue(inspection.homeIssue);
        assertEquals(6L, inspection.issueCount);
        assertEquals("pageTitleTooShort", inspection.samples.get(0).key);
        assertEquals("pageDescriptionMissing", inspection.samples.get(1).key);
        assertEquals("pageCanonicalMissing", inspection.samples.get(2).key);
        assertEquals("pageH1Multiple", inspection.samples.get(3).key);
        assertEquals("pageViewportMissing", inspection.samples.get(4).key);
        assertEquals("pageHtmlLangMissing", inspection.samples.get(5).key);
    }
}
