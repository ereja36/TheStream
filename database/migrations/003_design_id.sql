-- Shto design_id për ndërrim dinamik të UI nga paneli (pa update APK)
ALTER TABLE `tbl_app_config`
  ADD COLUMN IF NOT EXISTS `design_id` varchar(10) NOT NULL DEFAULT 'v1' AFTER `redirect_url`;

UPDATE `tbl_app_config` SET `design_id` = 'v1' WHERE `design_id` IS NULL OR `design_id` = '';
