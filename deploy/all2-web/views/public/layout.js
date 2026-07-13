const { renderTemplate } = require('../renderTemplate');

function renderPublicLayout({
  baseStyle,
  title,
  extraHeadHtml = '',
  navPublicHtml,
  playerModalHtml,
  pageContentHtml,
  playerScriptHtml
}) {
  return renderTemplate('public/layout.ejs', {
    baseStyle,
    title,
    extraHeadHtml,
    navPublicHtml,
    playerModalHtml,
    pageContentHtml,
    playerScriptHtml
  });
}

module.exports = {
  renderPublicLayout
};
