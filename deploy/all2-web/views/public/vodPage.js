const { renderTemplate } = require('../renderTemplate');

function renderVodPageContent({
  title,
  description,
  filtersHtml,
  cardsHtml,
  paginationHtml
}) {
  return renderTemplate('public/vodPage.ejs', {
    title,
    description,
    filtersHtml,
    cardsHtml,
    paginationHtml
  });
}

module.exports = {
  renderVodPageContent
};
