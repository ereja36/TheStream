-- Rregullim për ekranin e bardhë në faqen Device Playlists
-- Ekzekuto nëse shfaqet gabimi: Illegal mix of collations

ALTER TABLE `tbl_device_playlist` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE `tbl_device_blocklist` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE `tbl_device_access` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE `tbl_startup_popup` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
