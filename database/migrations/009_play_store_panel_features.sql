ALTER TABLE `tbl_settings`
  ADD COLUMN IF NOT EXISTS `enable_player_epg` tinyint(1) NOT NULL DEFAULT 1 AFTER `providers`,
  ADD COLUMN IF NOT EXISTS `privacy_public_url` varchar(500) NOT NULL DEFAULT 'https://shifshqip.com/privacy' AFTER `enable_player_epg`;

CREATE TABLE IF NOT EXISTS `tbl_device_heartbeat` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT,
  `device_id` varchar(128) NOT NULL,
  `app_version` varchar(32) NOT NULL DEFAULT '',
  `event_type` varchar(32) NOT NULL DEFAULT 'open',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_device_heartbeat_device` (`device_id`),
  KEY `idx_device_heartbeat_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `tbl_app_inbox_notifications` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT,
  `title` varchar(255) NOT NULL,
  `message` text NOT NULL,
  `target_url` varchar(500) NOT NULL DEFAULT '',
  `status` tinyint(1) NOT NULL DEFAULT 1,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `tbl_app_inbox_delivery` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT,
  `notification_id` bigint(20) UNSIGNED NOT NULL,
  `device_id` varchar(128) NOT NULL,
  `delivered_at` datetime DEFAULT NULL,
  `opened_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_notification_device` (`notification_id`,`device_id`),
  KEY `idx_inbox_delivery_notification` (`notification_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

UPDATE `tbl_settings`
SET `privacy_public_url` = 'https://shifshqip.com/privacy'
WHERE `id` = 1 AND (`privacy_public_url` IS NULL OR `privacy_public_url` = '' OR `privacy_public_url` LIKE '%api.shifshqip.com%');
