const { renderTemplate } = require('../renderTemplate');

function renderCategoryPageContent({
  basePath,
  backPath = '/',
  iconClass = 'fa-tv',
  iconColor = 'var(--accent)',
  logoUrl = '',
  title = 'Kategori',
  badgeClass = '',
  badgeText = '',
  count = 0,
  cards = '',
  extraStatusHtml = ''
}) {
  return renderTemplate('public/categoryPage.ejs', {
    basePath,
    backPath,
    iconClass,
    iconColor,
    logoUrl,
    title,
    badgeClass,
    badgeText,
    count,
    cards,
    extraStatusHtml
  });
}

module.exports = {
  renderCategoryPageContent
};
