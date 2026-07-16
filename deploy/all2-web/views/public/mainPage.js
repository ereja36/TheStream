const { renderTemplate } = require('../renderTemplate');

function renderMainPageContent(params = {}) {
  return renderTemplate('public/mainPage.ejs', {
    basePath: params.basePath,
    customCards: params.customCards || '',
    visibleMainCount: params.visibleMainCount || 0,
    manualMainEnabled: !!params.manualMainEnabled,
    cards: params.cards || '',
    theme: params.theme || 'classic',
    categoryRowsHtml: params.categoryRowsHtml || '',
    channelCount: params.channelCount || params.visibleMainCount || 0,
    categoryCount: params.categoryCount || 0,
    telegramUrl: params.telegramUrl || 'https://t.me/albview',
    playerStatusText: params.playerStatusText || ''
  });
}

module.exports = {
  renderMainPageContent
};
