const { renderTemplate } = require('../renderTemplate');

function renderVodDetailPageContent({
  basePath,
  vod,
  relatedCardsHtml
}) {
  return renderTemplate('public/vodDetailPage.ejs', {
    basePath,
    vod,
    relatedCardsHtml
  });
}

module.exports = {
  renderVodDetailPageContent
};
