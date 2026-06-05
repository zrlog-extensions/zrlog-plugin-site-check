package com.zrlog.plugin.sitecheck;

import com.zrlog.plugin.IOSession;
import com.zrlog.plugin.api.IPluginAction;
import com.zrlog.plugin.data.codec.HttpRequestInfo;
import com.zrlog.plugin.data.codec.MsgPacket;
import com.zrlog.plugin.sitecheck.controller.SiteCheckController;

public class SiteCheckPluginAction implements IPluginAction {

    @Override
    public void start(IOSession ioSession, MsgPacket msgPacket) {
    }

    @Override
    public void stop(IOSession ioSession, MsgPacket msgPacket) {
    }

    @Override
    public void install(IOSession ioSession, MsgPacket msgPacket, HttpRequestInfo httpRequestInfo) {
        new SiteCheckController(ioSession, msgPacket, httpRequestInfo).index();
    }

    @Override
    public void uninstall(IOSession ioSession, MsgPacket msgPacket) {
    }
}
