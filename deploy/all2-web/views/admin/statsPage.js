const { renderTemplate } = require('../renderTemplate');

function renderAdminStatsPage({ baseStyle, navAdminHtml, basePath, adminToken }) {
  return renderTemplate('admin/statsPage.ejs', {
    baseStyle,
    navAdminHtml,
    basePath,
    adminToken
  });
}

module.exports = {
  renderAdminStatsPage
};
