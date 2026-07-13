const { renderTemplate } = require('../renderTemplate');

function renderAndroidAdMobAdminSection({ mobileAdMobSettings, basePath }) {
  return renderTemplate('admin/androidAdmobSection.ejs', {
    mobileAdMobSettings,
    basePath
  });
}

module.exports = {
  renderAndroidAdMobAdminSection
};
