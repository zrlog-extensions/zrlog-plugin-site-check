package com.zrlog.plugin.sitecheck;

import com.zrlog.plugin.RunConstants;
import com.zrlog.plugin.common.PluginNativeImageUtils;
import com.zrlog.plugin.sitecheck.controller.SiteCheckController;
import com.zrlog.plugin.sitecheck.service.SiteCheckService;
import com.zrlog.plugin.type.RunType;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

public class GraalvmAgentApplication {

    public static void main(String[] args) throws IOException {
        RunConstants.runType = RunType.AGENT;
        PluginNativeImageUtils.usedGsonObject();
        PluginNativeImageUtils.gsonNativeAgentByClazz(Arrays.asList(
                SiteCheckService.HealthCheckResult.class,
                SiteCheckService.HealthCheckRecord.class,
                SiteCheckService.HealthCheckIssue.class,
                SiteCheckService.HealthCheckSample.class,
                SiteCheckService.HealthCheckSuggestion.class
        ));
        String basePath = System.getProperty("user.dir").replace("\\target", "").replace("/target", "");
        File file = new File(basePath + "/src/main/resources");
        PluginNativeImageUtils.doLoopResourceLoad(file.listFiles(), file.getPath() + "/", "/");
        PluginNativeImageUtils.exposeController(Collections.singletonList(SiteCheckController.class));
        Application.main(args);
    }
}
