const { renderTemplate } = require('../renderTemplate');

function renderCategoryPageContent(params = {}) {
  return renderTemplate('public/categoryPage.ejs', {
    basePath: params.basePath || '',
    backPath: params.backPath || '/',
    iconClass: params.iconClass || 'fa-folder-open',
    iconColor: params.iconColor || 'var(--accent)',
    logoUrl: params.logoUrl || '',
    title: params.title || '',
    badgeClass: params.badgeClass || '',
    badgeText: params.badgeText || '',
    count: params.count || 0,
    cards: params.cards || '',
    extraStatusHtml: params.extraStatusHtml || '',
    bottomAdHtml: params.bottomAdHtml || ''
  });
}

module.exports = {
  renderCategoryPageContent
};
