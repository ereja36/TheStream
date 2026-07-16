#!/usr/bin/env python3
from pathlib import Path

ejs = Path("/root/all/all2/views/public/categoryPage.ejs")
text = ejs.read_text(encoding="utf-8")
if "category-bottom-ad" not in text:
    text = text.replace(
        '<div class="channels-grid"><%- cards %></div>\n</div>',
        '<div class="channels-grid"><%- cards %></div>\n'
        '<% if (typeof bottomAdHtml !== "undefined" && bottomAdHtml) { %>\n'
        '<div class="category-bottom-ad"><%- bottomAdHtml %></div>\n'
        '<% } %>\n'
        '</div>',
    )
    ejs.write_text(text, encoding="utf-8")
    print("category ejs ok")

Path("/root/all/all2/views/public/categoryPage.js").write_text(
    """const { renderTemplate } = require('../renderTemplate');

function renderCategoryPageContent(params = {}) {
  return renderTemplate('public/categoryPage.ejs', {
    ...params,
    bottomAdHtml: params.bottomAdHtml || ''
  });
}

module.exports = {
  renderCategoryPageContent
};
""",
    encoding="utf-8",
)
print("category js ok")

server = Path("/root/all/all2/server.js")
t = server.read_text(encoding="utf-8")
needle = "const pageContentHtml = renderCategoryPageContent({"
idx = 0
count = 0
while True:
    i = t.find(needle, idx)
    if i < 0:
        break
    end = t.find("});", i)
    block = t[i:end]
    if "bottomAdHtml" not in block:
        t = (
            t[:end]
            + "    bottomAdHtml: buildNativeAdSlotHtml(normalizeAdSnippetConfig(appConfig.ad_snippets), 0),\n  "
            + t[end:]
        )
        count += 1
        idx = end + 120
    else:
        idx = i + 10
server.write_text(t, encoding="utf-8")
print("patched category renders", count)
