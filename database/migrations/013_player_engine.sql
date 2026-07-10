ALTER TABLE `tbl_settings`
  ADD COLUMN `player_engine` VARCHAR(16) NOT NULL DEFAULT 'exoplayer' AFTER `enable_player_epg`;
