import {legacyLogicalPropertiesTransformer, StyleProvider} from "@ant-design/cssinjs";
import {App, ConfigProvider, Layout, theme} from "antd";
import zhCN from "antd/es/locale/zh_CN";
import axios from "axios";
import {useEffect, useState} from "react";
import {createRoot} from "react-dom/client";
import AppBase from "./AppBase";

const {darkAlgorithm, defaultAlgorithm} = theme;
const {Content} = Layout;

export interface Plugin {
    id: string;
    version: string;
    name: string;
    paths: string[];
    actions: string[];
    desc: string;
    author: string;
    shortName: string;
    indexPage: string;
    previewImageBase64: string;
    services: string[];
    dependentService: string[];
}

export interface SiteCheckConfig {
    maxPages: number;
    timeoutSeconds: number;
    userAgent: string;
    extraPaths: string;
    requireCanonical: boolean;
    requireH1: boolean;
    checkDuplicateMeta: boolean;
    checkLengthGuidance: boolean;
}

export interface HealthCheckSample {
    key: string;
    target?: string;
}

export interface HealthCheckIssue {
    key: string;
    severity: "error" | "warning" | "info" | string;
    impact?: string;
    count: number;
    samples: string[];
    sampleDetails: HealthCheckSample[];
    actionRoute?: string;
    title?: string;
    description?: string;
}

export interface HealthCheckSuggestion {
    key: string;
    actionRoute?: string;
    title?: string;
    description?: string;
}

export interface HealthCheckResult {
    checkedAt: number;
    score: number;
    articleCount: number;
    publishedArticleCount: number;
    crawledPageCount: number;
    crawlFailedPageCount: number;
    brokenLinkCount: number;
    seoIssueCount: number;
    routeIssueCount: number;
    siteConfigIssueCount: number;
    publicOutputIssueCount: number;
    databaseFragmentValue: number;
    databaseFragmentLabel: string;
    databaseEngine: string;
    databaseFragmentInspectable: boolean;
    canOptimizeDatabase: boolean;
    databaseOptimizeUnsupportedReason?: string;
    issues: HealthCheckIssue[];
    suggestions: HealthCheckSuggestion[];
}

export interface HealthCheckRecord {
    checkedAt: number;
    score: number;
    status: string;
    issueCount: number;
    articleCount: number;
    publishedArticleCount: number;
    crawledPageCount: number;
    crawlFailedPageCount: number;
    brokenLinkCount: number;
    seoIssueCount: number;
    routeIssueCount: number;
    siteConfigIssueCount: number;
    publicOutputIssueCount: number;
    databaseFragmentLabel: string;
}

export interface SurfaceAction {
    label: string;
    actionRef?: string;
    adminRoute?: string;
    style?: "primary" | "default" | string;
}

export interface SurfaceMetric {
    label: string;
    value: string | number;
}

export interface SurfaceItem {
    id: string;
    title: string;
    description: string;
    status: string;
    actions: SurfaceAction[];
}

export interface SiteCheckSurface {
    version: string;
    title: string;
    description: string;
    config: SiteCheckConfig;
    actions: SurfaceAction[];
    records: HealthCheckRecord[];
    result?: HealthCheckResult;
    metrics: SurfaceMetric[];
    items: SurfaceItem[];
}

export interface SiteCheckInfoResponse {
    dark: boolean;
    adminColorPrimary?: string;
    plugin: Plugin;
    surface: SiteCheckSurface;
}

export interface StandardResponse<T> {
    success: boolean;
    message?: string;
    data: T;
}

const loadFromDocument = () => {
    try {
        const node = document.getElementById("pluginInfo");
        if (node === null || node.innerText.length === 0) {
            return null;
        }
        return JSON.parse(node.innerText) as StandardResponse<SiteCheckInfoResponse>;
    } catch (e) {
        return null;
    }
}

const Index = () => {
    const [response, setResponse] = useState<StandardResponse<SiteCheckInfoResponse> | null>(loadFromDocument);

    useEffect(() => {
        if (response === null) {
            axios.get<StandardResponse<SiteCheckInfoResponse>>("json").then(({data}) => setResponse(data));
        }
    }, [response]);

    if (response === null || !response.success) {
        return <></>;
    }

    return (
        <ConfigProvider
            locale={zhCN}
            theme={{
                algorithm: response.data.dark ? darkAlgorithm : defaultAlgorithm,
                token: response.data.adminColorPrimary ? {colorPrimary: response.data.adminColorPrimary} : undefined,
            }}
        >
            <StyleProvider transformers={[legacyLogicalPropertiesTransformer]}>
                <Content style={{
                    minHeight: "100vh",
                    backgroundColor: response.data.dark ? "#141414" : undefined,
                    color: response.data.dark ? "#dfdfdf" : undefined,
                }}>
                    <App>
                        <AppBase pluginInfo={response.data}/>
                    </App>
                </Content>
            </StyleProvider>
        </ConfigProvider>
    );
};

const container = document.getElementById("app");
const root = createRoot(container!);
root.render(<Index/>);
