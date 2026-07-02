package com.zrlog.plugin.sitecheck.service;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class SiteCheckConfigTest {

    @Test
    public void normalizesOutOfRangeValues() {
        SiteCheckConfig values = new SiteCheckConfig();
        values.maxPages = 500;
        values.timeoutSeconds = 1;
        values.requireCanonical = false;
        values.userAgent = "";
        values.extraPaths = "about, /archive\nmailto:test@example.com\n#comment";

        SiteCheckConfig config = values.normalized();

        assertEquals(50, config.maxPages);
        assertEquals(3, config.timeoutSeconds);
        assertFalse(config.requireCanonical);
        assertEquals(SiteCheckConfig.DEFAULT_USER_AGENT, config.userAgent);
        assertEquals(2, config.extraPathList().size());
        assertEquals("/about", config.extraPathList().get(0));
        assertEquals("/archive", config.extraPathList().get(1));
    }
}
