ALTER TABLE `tbl_settings`
  ADD COLUMN IF NOT EXISTS `global_playlist_enabled` tinyint(1) NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS `global_playlist_url` varchar(500) NOT NULL DEFAULT '',
  ADD COLUMN IF NOT EXISTS `global_playlist_name` varchar(255) NOT NULL DEFAULT 'Shif Shqip';
