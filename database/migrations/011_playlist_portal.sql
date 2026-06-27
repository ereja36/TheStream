CREATE TABLE IF NOT EXISTS `tbl_playlist_portal` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `portal_slug` varchar(100) NOT NULL DEFAULT 'shifshqip',
  `portal_name` varchar(255) NOT NULL DEFAULT 'Shif Shqip',
  `source_url` varchar(1000) NOT NULL DEFAULT '',
  `source_type` varchar(20) NOT NULL DEFAULT 'auto',
  `access_key` varchar(128) NOT NULL DEFAULT '',
  `required_user_agent` varchar(255) NOT NULL DEFAULT 'ShifShqip',
  `required_package` varchar(255) NOT NULL DEFAULT 'com.shif.shqip.android',
  `outbound_user_agent` varchar(500) NOT NULL DEFAULT '',
  `outbound_referer` varchar(500) NOT NULL DEFAULT '',
  `outbound_origin` varchar(500) NOT NULL DEFAULT '',
  `outbound_cookie` text NOT NULL,
  `outbound_extra_headers` text NOT NULL,
  `status` tinyint(1) NOT NULL DEFAULT 1,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_portal_slug` (`portal_slug`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `tbl_playlist_portal` (
  `portal_slug`, `portal_name`, `source_url`, `source_type`, `access_key`,
  `required_user_agent`, `required_package`,
  `outbound_referer`, `status`
)
SELECT 'shifshqip', 'Shif Shqip', '', 'auto',
  MD5(CONCAT('shifshqip-portal-', RAND(), '-', UNIX_TIMESTAMP())),
  'ShifShqip', 'com.shif.shqip.android',
  'https://shifher-15810060fed0.herokuapp.com/', 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `tbl_playlist_portal` WHERE `portal_slug` = 'shifshqip' LIMIT 1);
