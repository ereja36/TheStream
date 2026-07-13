const { renderTemplate } = require('../renderTemplate');

function renderMainPageContent({
  basePath,
  customCards,
  visibleMainCount,
  manualMainEnabled,
  cards
}) {
  return renderTemplate('public/mainPage.ejs', {
    basePath,
    customCards,
    visibleMainCount,
    manualMainEnabled,
    cards
  });
}

module.exports = {
  renderMainPageContent
};
