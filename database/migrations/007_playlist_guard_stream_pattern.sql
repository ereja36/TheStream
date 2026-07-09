ALTER TABLE `tbl_playlist_guard`
  ADD COLUMN IF NOT EXISTS `stream_url_pattern` varchar(500) NOT NULL DEFAULT '' AFTER `url_pattern`;
