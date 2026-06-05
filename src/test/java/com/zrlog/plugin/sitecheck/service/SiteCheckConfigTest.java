package com.zrlog.plugin.sitecheck.service;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class SiteCheckConfigTest {

    @Test
    public void normalizesOutOfRangeValues() {
        Map<String, Object> values = new HashMap<>();
        values.put("maxPages", 500);
        values.put("timeoutSeconds", 1);
        values.put("requireCanonical", "false");
        values.put("userAgent", "");
        values.put("extraPaths", "about, /archive\nmailto:test@example.com\n#comment");

        SiteCheckConfig config = SiteCheckConfig.fromValues(values);

        assertEquals(50, config.maxPages);
        assertEquals(3, config.timeoutSeconds);
        assertFalse(config.requireCanonical);
        assertEquals(SiteCheckConfig.DEFAULT_USER_AGENT, config.userAgent);
        assertEquals(2, config.extraPathList().size());
        assertEquals("/about", config.extraPathList().get(0));
        assertEquals("/archive", config.extraPathList().get(1));
    }
}
