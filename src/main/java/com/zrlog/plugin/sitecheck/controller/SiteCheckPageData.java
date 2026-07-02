package com.zrlog.plugin.sitecheck.controller;

import com.zrlog.plugin.message.Plugin;

public class SiteCheckPageData {

    private boolean dark;
    private String adminColorPrimary;
    private Plugin plugin;
    private Object surface;

    public boolean isDark() {
        return dark;
    }

    public void setDark(boolean dark) {
        this.dark = dark;
    }

    public String getAdminColorPrimary() {
        return adminColorPrimary;
    }

    public void setAdminColorPrimary(String adminColorPrimary) {
        this.adminColorPrimary = adminColorPrimary;
    }

    public Plugin getPlugin() {
        return plugin;
    }

    public void setPlugin(Plugin plugin) {
        this.plugin = plugin;
    }

    public Object getSurface() {
        return surface;
    }

    public void setSurface(Object surface) {
        this.surface = surface;
    }
}
