import {
    Alert,
    App,
    Button,
    Card,
    Col,
    Empty,
    Form,
    Grid,
    Input,
    InputNumber,
    List,
    Row,
    Space,
    Statistic,
    Switch,
    Tag,
    Typography,
    theme,
} from "antd";
import {
    HeartOutlined,
    LinkOutlined,
    ReloadOutlined,
    SearchOutlined,
    SettingOutlined,
    ToolOutlined,
} from "@ant-design/icons";
import axios from "axios";
import {FunctionComponent, useState} from "react";
import styled from "styled-components";
import {
    HealthCheckIssue,
    HealthCheckSample,
    SiteCheckConfig,
    SiteCheckInfoResponse,
    SiteCheckSurface,
    StandardResponse,
} from "../index";

type SiteCheckIndexProps = {
    data: SiteCheckInfoResponse;
}

const Shell = styled.div`
  width: 100%;
  max-width: 1180px;
  margin: 0 auto;
  padding: 20px;
  box-sizing: border-box;

  @media (max-width: 575px) {
    padding: 12px;
  }
`;

const TopBar = styled.div`
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;

  @media (max-width: 720px) {
    display: block;

    .ant-space {
      margin-top: 12px;
      width: 100%;
    }

    .ant-btn {
      flex: 1;
    }
  }
`;

const Title = styled.h1`
  margin: 0;
  font-size: 24px;
  line-height: 32px;
  font-weight: 650;
`;

const SubTitle = styled.div<{ $token: any }>`
  margin-top: 6px;
  color: ${props => props.$token.colorTextDescription};
  font-size: 14px;
`;

const ContentGrid = styled.div`
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(320px, .8fr);
  gap: 14px;

  @media (max-width: 960px) {
    grid-template-columns: 1fr;
  }
`;

const SideStack = styled.div`
  display: grid;
  gap: 14px;
  align-content: start;
`;

const severityColor = (severity: HealthCheckIssue["severity"]) => {
    if (severity === "error") {
        return "error";
    }
    if (severity === "warning") {
        return "warning";
    }
    return "processing";
};

const scoreStatus = (score: number) => {
    if (score >= 85) {
        return "success";
    }
    if (score >= 60) {
        return "warning";
    }
    return "error";
};

const formatTime = (timestamp?: number) => {
    if (!timestamp) {
        return "-";
    }
    return new Intl.DateTimeFormat(undefined, {
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
    }).format(timestamp);
};

const issueImpactLabel = (impact?: string) => {
    switch (impact) {
        case "availability":
            return "可用性";
        case "publish":
            return "发布";
        case "search":
            return "搜索";
        case "performance":
            return "性能";
        case "general":
            return "常规";
        default:
            return impact;
    }
};

const sampleMeta = (key: string) => {
    const map: Record<string, string> = {
        websiteSeoTitleMissing: "站点标题缺失",
        websiteSeoDescriptionMissing: "站点描述缺失",
        websiteSeoKeywordsMissing: "站点关键词缺失",
        articleSeoDigestMissing: "文章摘要缺失",
        articleSeoKeywordsMissing: "文章关键词缺失",
        pageTitleMissing: "页面 title 缺失",
        pageTitleTooShort: "页面 title 过短",
        pageTitleTooLong: "页面 title 过长",
        pageDescriptionMissing: "meta description 缺失",
        pageDescriptionTooShort: "meta description 过短",
        pageDescriptionTooLong: "meta description 过长",
        pageCanonicalMissing: "canonical 缺失",
        pageCanonicalNotAbsolute: "canonical 不是完整 URL",
        pageH1Missing: "H1 缺失",
        pageH1Multiple: "H1 过多",
        pageNoIndex: "页面声明 noindex",
        pageViewportMissing: "viewport 缺失",
        pageHtmlLangMissing: "html lang 缺失",
        pageTitleDuplicate: "页面 title 重复",
        pageDescriptionDuplicate: "meta description 重复",
    };
    return map[key] || key;
};

const sampleText = (sample: HealthCheckSample) => {
    const text = sampleMeta(sample.key);
    return sample.target ? `${sample.target} · ${text}` : text;
};

const databaseOptimizeUnsupportedReason = (reason?: string) => {
    switch (reason) {
        case "remoteWebApi":
            return "WebAPI / D1 数据源不支持插件直接维护数据库。";
        case "unsupportedEngine":
            return "当前数据库类型不支持插件自动维护。";
        case "unavailable":
            return "当前无法读取数据库连接信息。";
        default:
            return undefined;
    }
};

const request = async <T, >(url: string, params?: Record<string, string>) => {
    const {data} = await axios.post<StandardResponse<{ message?: string; surface: T }>>(url, new URLSearchParams(params), {
        headers: {"Content-Type": "application/x-www-form-urlencoded;charset=UTF-8"},
    });
    if (!data.success) {
        throw new Error(data.message || "操作失败");
    }
    return data.data;
};

const navigateAdmin = (route?: string) => {
    if (!route) {
        return;
    }
    window.parent.postMessage({source: "zrlog-plugin", type: "zrlog-admin:navigate", route}, "*");
};

const SiteCheckIndex: FunctionComponent<SiteCheckIndexProps> = ({data}) => {
    const {token} = theme.useToken();
    const screens = Grid.useBreakpoint();
    const {message} = App.useApp();
    const [surface, setSurface] = useState<SiteCheckSurface>(data.surface);
    const [loading, setLoading] = useState(false);
    const [optimizing, setOptimizing] = useState(false);
    const [saving, setSaving] = useState(false);
    const [form] = Form.useForm<SiteCheckConfig>();
    const state = surface.result;

    const load = async () => {
        setLoading(true);
        try {
            const {data} = await axios.get<StandardResponse<SiteCheckSurface>>("surface");
            if (!data.success) {
                throw new Error(data.message || "加载失败");
            }
            setSurface(data.data);
        } catch (e) {
            message.error(e instanceof Error ? e.message : "加载失败");
        } finally {
            setLoading(false);
        }
    };

    const runCheck = async () => {
        setLoading(true);
        try {
            const result = await request<SiteCheckSurface>("surfaceAction", {actionRef: "siteCheck:run"});
            setSurface(result.surface);
            message.success(result.message || "检查完成");
        } catch (e) {
            message.error(e instanceof Error ? e.message : "检查失败");
        } finally {
            setLoading(false);
        }
    };

    const optimizeDatabase = async () => {
        setOptimizing(true);
        try {
            const result = await request<SiteCheckSurface>("surfaceAction", {actionRef: "siteCheck:optimize"});
            setSurface(result.surface);
            message.success(result.message || "数据库维护完成");
        } catch (e) {
            message.error(e instanceof Error ? e.message : "数据库维护失败");
        } finally {
            setOptimizing(false);
        }
    };

    const saveSettings = async () => {
        const values = await form.validateFields();
        setSaving(true);
        try {
            const result = await request<SiteCheckSurface>("surfaceAction", {
                actionRef: "siteCheck:settings",
                values: JSON.stringify(values),
            });
            setSurface(result.surface);
            message.success(result.message || "已保存");
        } catch (e) {
            message.error(e instanceof Error ? e.message : "保存失败");
        } finally {
            setSaving(false);
        }
    };

    const optimizeUnsupportedReason = databaseOptimizeUnsupportedReason(state?.databaseOptimizeUnsupportedReason);

    return (
        <Shell>
            <TopBar>
                <div>
                    <Title>站点检查</Title>
                    <SubTitle $token={token}>检查公开页面、文章资源、SEO、生成文件、数据库和目录权限。</SubTitle>
                </div>
                <Space size={8} wrap>
                    <Button icon={<ReloadOutlined/>} onClick={load} loading={loading}>刷新</Button>
                    <Button type="primary" icon={<HeartOutlined/>} onClick={runCheck} loading={loading}>立即检查</Button>
                    {state?.canOptimizeDatabase && (
                        <Button icon={<ToolOutlined/>} onClick={optimizeDatabase} loading={optimizing}>数据库维护</Button>
                    )}
                </Space>
            </TopBar>

            <Space direction="vertical" size={16} style={{width: "100%"}}>
                <Row gutter={[12, 12]}>
                    <Col xs={24} sm={12} lg={6}>
                        <Card size="small" styles={{body: {padding: 16}}}>
                            <Statistic title="得分" value={state?.score ?? 100} suffix="/ 100"/>
                        </Card>
                    </Col>
                    <Col xs={24} sm={12} lg={6}>
                        <Card size="small" styles={{body: {padding: 16}}}>
                            <Statistic title="失效资源" value={state?.brokenLinkCount ?? 0} prefix={<LinkOutlined/>}/>
                        </Card>
                    </Col>
                    <Col xs={24} sm={12} lg={6}>
                        <Card size="small" styles={{body: {padding: 16}}}>
                            <Statistic title="SEO 问题" value={state?.seoIssueCount ?? 0} prefix={<SearchOutlined/>}/>
                        </Card>
                    </Col>
                    <Col xs={24} sm={12} lg={6}>
                        <Card size="small" styles={{body: {padding: 16}}}>
                            <Statistic title="已抓取页面" value={state?.crawledPageCount ?? 0}/>
                        </Card>
                    </Col>
                </Row>

                {state ? (
                    <Alert
                        type={scoreStatus(state.score)}
                        showIcon
                        message={`数据库: ${state.databaseEngine || "-"}`}
                        description={
                            <Space direction="vertical" size={2}>
                                <span>{`最近检查: ${formatTime(state.checkedAt)}`}</span>
                                {optimizeUnsupportedReason ? (
                                    <Typography.Text type="secondary">{optimizeUnsupportedReason}</Typography.Text>
                                ) : null}
                            </Space>
                        }
                    />
                ) : (
                    <Alert type="info" showIcon message="尚未执行检查" description="点击“立即检查”后才会抓取公开页面并生成站点健康报告。"/>
                )}

                <ContentGrid>
                    <Card
                        title={<Space size={8}><HeartOutlined/><span>健康检查</span></Space>}
                        loading={loading && !state}
                    >
                        {state && state.issues.length > 0 ? (
                            <List
                                dataSource={state.issues}
                                renderItem={(item) => (
                                    <List.Item>
                                        <div style={{
                                            width: "100%",
                                            display: "flex",
                                            flexDirection: screens.sm ? "row" : "column",
                                            alignItems: screens.sm ? "center" : "stretch",
                                            justifyContent: "space-between",
                                            gap: 12,
                                        }}>
                                            <div style={{flex: 1, minWidth: 0}}>
                                                <List.Item.Meta
                                                    title={
                                                        <Space size={8} wrap>
                                                            <span>{item.title || item.key}</span>
                                                            <Tag color={severityColor(item.severity)}>
                                                                {item.key === "databaseFragment" ? state.databaseFragmentLabel : item.count}
                                                            </Tag>
                                                        </Space>
                                                    }
                                                    description={
                                                        <Space direction="vertical" size={4} style={{width: "100%"}}>
                                                            <Typography.Text type="secondary">{item.description || item.key}</Typography.Text>
                                                            {item.impact ? (
                                                                <Typography.Text type="secondary">{`影响: ${issueImpactLabel(item.impact)}`}</Typography.Text>
                                                            ) : null}
                                                            {(item.sampleDetails || []).map(sample => (
                                                                <Typography.Text key={`${sample.key}-${sample.target || ""}`} ellipsis>
                                                                    {sampleText(sample)}
                                                                </Typography.Text>
                                                            ))}
                                                            {(item.samples || []).map(sample => (
                                                                <Typography.Text key={sample} ellipsis>{sample}</Typography.Text>
                                                            ))}
                                                        </Space>
                                                    }
                                                />
                                            </div>
                                            {item.actionRoute ? (
                                                <Button type="link" block={!screens.sm} onClick={() => navigateAdmin(item.actionRoute)}>
                                                    前往处理
                                                </Button>
                                            ) : null}
                                        </div>
                                    </List.Item>
                                )}
                            />
                        ) : (
                            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={state ? "当前未发现明显问题" : "暂无检查结果"}/>
                        )}
                    </Card>

                    <SideStack>
                        <Card title={<Space size={8}><SettingOutlined/><span>检查设置</span></Space>}>
                            <Form form={form} layout="vertical" initialValues={surface.config}>
                                <Row gutter={12}>
                                    <Col span={12}>
                                        <Form.Item label="页面上限" name="maxPages">
                                            <InputNumber min={1} max={50} style={{width: "100%"}}/>
                                        </Form.Item>
                                    </Col>
                                    <Col span={12}>
                                        <Form.Item label="超时秒数" name="timeoutSeconds">
                                            <InputNumber min={3} max={30} style={{width: "100%"}}/>
                                        </Form.Item>
                                    </Col>
                                </Row>
                                <Form.Item label="User-Agent" name="userAgent">
                                    <Input maxLength={120}/>
                                </Form.Item>
                                <Form.Item label="额外路径" name="extraPaths">
                                    <Input.TextArea rows={3} placeholder="/about&#10;/archive"/>
                                </Form.Item>
                                <Form.Item label="检查 canonical" name="requireCanonical" valuePropName="checked">
                                    <Switch/>
                                </Form.Item>
                                <Form.Item label="检查 H1" name="requireH1" valuePropName="checked">
                                    <Switch/>
                                </Form.Item>
                                <Form.Item label="检查重复 title/description" name="checkDuplicateMeta" valuePropName="checked">
                                    <Switch/>
                                </Form.Item>
                                <Form.Item label="检查标题和描述长度" name="checkLengthGuidance" valuePropName="checked">
                                    <Switch/>
                                </Form.Item>
                                <Button type="primary" onClick={saveSettings} loading={saving} block>保存设置</Button>
                            </Form>
                        </Card>

                        <Card title="建议">
                            {state ? (
                                <List
                                    dataSource={state.suggestions}
                                    renderItem={item => (
                                        <List.Item>
                                            <List.Item.Meta
                                                title={item.title || item.key}
                                                description={item.description || item.key}
                                            />
                                            {item.actionRoute ? (
                                                <Button type="link" onClick={() => navigateAdmin(item.actionRoute)}>前往</Button>
                                            ) : null}
                                        </List.Item>
                                    )}
                                />
                            ) : (
                                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无建议"/>
                            )}
                        </Card>

                        <Card title="最近记录">
                            <List
                                dataSource={surface.records || []}
                                locale={{emptyText: "暂无检查记录"}}
                                renderItem={record => (
                                    <List.Item>
                                        <List.Item.Meta
                                            title={<Space><span>{record.score}/100</span><Tag>{record.issueCount} 类问题</Tag></Space>}
                                            description={`页面 ${record.crawledPageCount}/${record.crawledPageCount + record.crawlFailedPageCount} · 文章 ${record.publishedArticleCount}/${record.articleCount} · ${formatTime(record.checkedAt)}`}
                                        />
                                    </List.Item>
                                )}
                            />
                        </Card>
                    </SideStack>
                </ContentGrid>
            </Space>
        </Shell>
    );
};

export default SiteCheckIndex;
