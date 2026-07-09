CREATE TABLE IF NOT EXISTS `tbl_portal_sessions` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `device_id` varchar(128) NOT NULL,
  `session_token` varchar(128) NOT NULL,
  `expires_at` datetime NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `device_id` (`device_id`),
  KEY `session_token` (`session_token`),
  KEY `expires_at` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
