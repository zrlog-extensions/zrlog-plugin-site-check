const fs = require("fs");
const path = require("path");

const buildPath = path.resolve(process.cwd(), process.env.BUILD_PATH || "build");
const indexPath = path.join(buildPath, "index.html");
const pluginStaticBase = "/admin/plugins/${_plugin.shortName}/static/";

const indexHtml = fs.readFileSync(indexPath, "utf8");
const rewrittenHtml = indexHtml.replace(/(src|href)="\.\/static\//g, `$1="${pluginStaticBase}`);

if (rewrittenHtml === indexHtml) {
  throw new Error("No static resource references were rewritten in index.html");
}

fs.writeFileSync(indexPath, rewrittenHtml);
