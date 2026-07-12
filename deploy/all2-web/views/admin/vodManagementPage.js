const { renderTemplate } = require('../renderTemplate');

function renderVodManagementPage({
  baseStyle,
  navAdminHtml,
  basePath,
  adminToken,
  initialQuery,
  initialGenre,
  initialYear,
  initialStatus,
  initialVisibility,
  page,
  pageSize
}) {
  return renderTemplate('admin/vodManagementPage.ejs', {
    baseStyle,
    navAdminHtml,
    basePath,
    adminToken,
    initialQuery,
    initialGenre,
    initialYear,
    initialStatus,
    initialVisibility,
    page,
    pageSize
  });
}

module.exports = {
  renderVodManagementPage
};
