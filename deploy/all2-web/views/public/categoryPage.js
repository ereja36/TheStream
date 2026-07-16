const { renderTemplate } = require('../renderTemplate');

function renderCategoryPageContent(params = {}) {
  return renderTemplate('public/categoryPage.ejs', {
    ...params,
    bottomAdHtml: params.bottomAdHtml || ''
  });
}

module.exports = {
  renderCategoryPageContent
};
