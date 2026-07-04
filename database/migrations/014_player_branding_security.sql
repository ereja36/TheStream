ALTER TABLE `tbl_settings`
  ADD COLUMN `player_brand_text` VARCHAR(128) NOT NULL DEFAULT '' AFTER `player_engine`,
  ADD COLUMN `player_brand_logo_url` VARCHAR(512) NOT NULL DEFAULT '' AFTER `player_brand_text`,
  ADD COLUMN `player_brand_enabled` TINYINT(1) NOT NULL DEFAULT 1 AFTER `player_brand_logo_url`,
  ADD COLUMN `app_integrity_enabled` TINYINT(1) NOT NULL DEFAULT 1 AFTER `player_brand_enabled`,
  ADD COLUMN `app_release_cert_sha256` VARCHAR(256) NOT NULL DEFAULT '' AFTER `app_integrity_enabled`,
  ADD COLUMN `ads_antiblock_script` TEXT NULL AFTER `app_release_cert_sha256`;

UPDATE `tbl_settings`
SET
  `player_brand_text` = IF(`player_brand_text` = '', 'shifshqip.com', `player_brand_text`),
  `app_release_cert_sha256` = IF(
    `app_release_cert_sha256` = '',
    '11:B7:12:C1:FA:76:23:10:65:EC:82:1D:4B:35:2C:2E:71:1C:CA:71:5C:D8:E6:3D:78:97:43:2C:4F:82:49:D1',
    `app_release_cert_sha256`
  ),
  `ads_antiblock_script` = IF(
    `ads_antiblock_script` IS NULL OR `ads_antiblock_script` = '',
    '{\n  \"reload_on_fail\": true,\n  \"max_retries\": 3,\n  \"retry_delay_ms\": 4000,\n  \"reload_on_resume\": true\n}',
    `ads_antiblock_script`
  )
WHERE `id` = 1;
