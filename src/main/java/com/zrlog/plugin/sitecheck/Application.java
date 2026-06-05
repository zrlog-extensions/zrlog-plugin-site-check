package com.zrlog.plugin.sitecheck;

import com.zrlog.plugin.client.NioClient;
import com.zrlog.plugin.render.SimpleTemplateRender;
import com.zrlog.plugin.sitecheck.controller.SiteCheckController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Application {

    public static void main(String[] args) throws IOException {
        List<Class<?>> classList = new ArrayList<>();
        classList.add(SiteCheckController.class);
        new NioClient(null, new SimpleTemplateRender())
                .connectServer(args, classList, SiteCheckPluginAction.class);
    }
}
