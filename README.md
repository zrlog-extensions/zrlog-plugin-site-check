# ZrLog Site Check Plugin

站点检查标准插件。

- `GET surface` 只读取最近一次检查结果，不自动扫描。
- `POST surfaceAction` 的 `siteCheck:run` 才执行检查。
- `POST surfaceAction` 的 `siteCheck:optimize` 才执行数据库维护。
- Surface 和 iframe 页面都通过宿主后台路由能力打开文章、网站设置等页面。
