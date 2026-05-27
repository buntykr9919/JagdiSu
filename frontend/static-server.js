const http = require("http");
const fs = require("fs");
const path = require("path");

const root = path.join(__dirname, "out");
const port = 1999;
const host = "127.0.0.1";

const types = {
  ".css": "text/css; charset=utf-8",
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".png": "image/png",
  ".svg": "image/svg+xml",
  ".webp": "image/webp",
  ".woff": "font/woff",
  ".woff2": "font/woff2"
};

function resolveFile(urlPath) {
  const cleanPath = decodeURIComponent(urlPath.split("?")[0]);
  const requested = cleanPath === "/" ? "/index.html" : cleanPath;
  const filePath = path.normalize(path.join(root, requested));

  if (!filePath.startsWith(root)) {
    return path.join(root, "index.html");
  }

  if (fs.existsSync(filePath) && fs.statSync(filePath).isFile()) {
    return filePath;
  }

  return path.join(root, "index.html");
}

http
  .createServer((request, response) => {
    const filePath = resolveFile(request.url || "/");
    const extension = path.extname(filePath);
    response.setHeader("Content-Type", types[extension] || "application/octet-stream");
    fs.createReadStream(filePath)
      .on("error", () => {
        response.statusCode = 404;
        response.end("Not found");
      })
      .pipe(response);
  })
  .listen(port, host, () => {
    console.log(`JagdiSu frontend running at http://${host}:${port}`);
  });
