# ZrLog Site Check Plugin

ZrLog 站点检查插件。检查公开页面的 SEO、静态资源、公开接口、输出文件和运行状态，并保留最近结果。

## 功能

- 打开页面时只读取最近一次检查结果，不自动扫描站点
- 可手动执行站点检查，或手动触发数据库维护
- 检查首页、配置的额外路径、文章页面和同站链接
- 分析 title、meta description、canonical、H1、robots、viewport、html lang 等 SEO 信息
- 检查文章本地资源、Markdown 链接、缩略图、文章别名路由、静态站点配置、robots.txt、RSS/Sitemap 输出文件、数据库维护状态和目录写入权限
- 可配置页面上限、请求超时、User-Agent、额外检查路径和 SEO 规则开关
- 保留最近一次检查结果和最近 10 次摘要记录

## 构建

工程包含 jar/native 发布 workflow，推送 `master` 后按其他插件工程的方式构建并上传部署产物。
