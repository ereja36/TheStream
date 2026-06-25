-- Rregulla për playlista të mbrojtura (Referer, Origin, Cookie, User-Agent)
CREATE TABLE IF NOT EXISTS `tbl_playlist_guard` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `rule_name` varchar(255) NOT NULL DEFAULT '',
  `url_pattern` varchar(500) NOT NULL,
  `referer` varchar(500) NOT NULL DEFAULT '',
  `origin` varchar(500) NOT NULL DEFAULT '',
  `user_agent` varchar(500) NOT NULL DEFAULT '',
  `cookie` text NOT NULL,
  `extra_headers` text NOT NULL,
  `status` tinyint(1) NOT NULL DEFAULT 1,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `idx_guard_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Logo custom për V2/V3 nga paneli
ALTER TABLE `tbl_app_config`
  ADD COLUMN IF NOT EXISTS `app_logo_url` varchar(500) NOT NULL DEFAULT '' AFTER `design_id`;
