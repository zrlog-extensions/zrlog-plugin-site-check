const fs = require("fs");
const path = require("path");

const buildPath = path.resolve(process.cwd(), process.env.BUILD_PATH || "build");
const manifestPath = path.join(buildPath, "asset-manifest.json");
const indexPath = path.join(buildPath, "index.html");

const manifest = JSON.parse(fs.readFileSync(manifestPath, "utf8"));
const mainJs = manifest.files && manifest.files["main.js"];

if (!mainJs) {
  throw new Error("main.js not found in asset-manifest.json");
}

const mainJsPath = path.join(buildPath, mainJs.replace(/^\.\//, ""));
const mainJsContent = fs.readFileSync(mainJsPath, "utf8");
const indexHtml = fs.readFileSync(indexPath, "utf8");
const scriptTagPattern = /<script defer="defer" src="[^"]*main\.[^"]*\.js"><\/script>/;

if (!scriptTagPattern.test(indexHtml)) {
  throw new Error("main script tag not found in index.html");
}

const inlinedHtml = indexHtml.replace(
  scriptTagPattern,
  `<script>${mainJsContent}</script>`
);

fs.writeFileSync(indexPath, inlinedHtml);
