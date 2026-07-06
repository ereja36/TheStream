ALTER TABLE `tbl_ads`
  ADD COLUMN `app_open_on_start` TINYINT(1) NOT NULL DEFAULT 1 AFTER `interstitial_ad_interval`,
  ADD COLUMN `app_open_on_resume` TINYINT(1) NOT NULL DEFAULT 1 AFTER `app_open_on_start`;

UPDATE `tbl_ads`
SET
  `app_open_on_start` = 1,
  `app_open_on_resume` = 1,
  `interstitial_ad_interval` = 3
WHERE `id` = 1;
