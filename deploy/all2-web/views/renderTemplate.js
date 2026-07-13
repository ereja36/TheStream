const fs = require('fs');
const path = require('path');
const ejs = require('ejs');

const templateCache = new Map();

function getTemplateSource(relativePath) {
  const fullPath = path.join(__dirname, relativePath);
  if (!templateCache.has(fullPath)) {
    templateCache.set(fullPath, fs.readFileSync(fullPath, 'utf8'));
  }
  return templateCache.get(fullPath);
}

function renderTemplate(relativePath, data = {}) {
  const source = getTemplateSource(relativePath);
  return ejs.render(source, data, { rmWhitespace: false });
}

module.exports = {
  renderTemplate
};
