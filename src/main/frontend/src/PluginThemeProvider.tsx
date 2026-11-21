import {legacyLogicalPropertiesTransformer, StyleProvider} from "@ant-design/cssinjs";
import {ThemeStyles, useUiTheme} from "@zrlog/ui";
import type {UiThemeOptions} from "@zrlog/ui";
import {UiIconProvider} from "@zrlog/ui/icons";
import {DEFAULT_PRIMARY_COLOR} from "@zrlog/ui/themes";
import {App, ConfigProvider, Layout, theme} from "antd";
import zhCN from "antd/es/locale/zh_CN";
import type {PropsWithChildren} from "react";

export interface PluginAppearance {
    appearance?: Partial<UiThemeOptions> | null;
    dark?: boolean;
    colorPrimary?: string;
    adminColorPrimary?: string;
}

const PluginContent = ({children}: PropsWithChildren) => {
    const {token} = theme.useToken();
    return <Layout.Content style={{
        minHeight: "100vh", background: token.colorBgLayout, color: token.colorText, fontFamily: token.fontFamily,
    }}>
        <App>{children}</App>
    </Layout.Content>;
};

export const PluginThemeProvider = ({page, children}: PropsWithChildren<{page: PluginAppearance}>) => {
    const appearance = page.appearance;
    const themeName = appearance?.theme?.trim();
    const config = useUiTheme({
        theme: themeName,
        dark: appearance?.dark ?? page.dark ?? false,
        colorPrimary: appearance?.colorPrimary?.trim() || page.colorPrimary?.trim()
            || page.adminColorPrimary?.trim() || DEFAULT_PRIMARY_COLOR,
        compactMode: appearance?.compactMode ?? false,
    });

    return <UiIconProvider theme={themeName}>
        <ConfigProvider {...config} locale={zhCN} drawer={{closable: {placement: "end"}}}>
            <StyleProvider transformers={[legacyLogicalPropertiesTransformer]}>
                <ThemeStyles theme={themeName}/>
                <PluginContent>{children}</PluginContent>
            </StyleProvider>
        </ConfigProvider>
    </UiIconProvider>;
};
