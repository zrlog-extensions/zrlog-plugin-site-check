# ZrLog Site Check Plugin

站点检查标准插件。

- `GET surface` 只读取最近一次检查结果，不自动扫描。
- `POST surfaceAction` 的 `siteCheck:run` 才执行检查。
- `POST surfaceAction` 的 `siteCheck:optimize` 才执行数据库维护。
- Surface 和 iframe 页面都通过宿主后台路由能力打开文章、网站设置等页面。
- SEO 检查通过 `IOSession` 走宿主 HTTP 能力抓取公开页面，再分析 title、meta description、canonical、H1、robots noindex 和重复页面元信息。
- 检查范围包括页面抓取、文章本地资源、Markdown 链接、缩略图、文章别名路由、静态站点配置、robots.txt、RSS/Sitemap 输出文件、数据库维护状态和目录写入权限。
- 最近一次检查结果和最近 10 次摘要记录会写入插件 KV，Surface 和插件 iframe 页面都会展示基础数据。
- 工程包含 jar/native 发布 workflow，推送 `master` 后按其他插件工程的方式构建并上传部署产物。
