UPDATE `tbl_startup_popup`
SET
  `title` = 'Support Shif Shqip',
  `message` = 'Make a donation to help us keep the app operational.\nEvery penny counts.',
  `popup_type` = 'support',
  `target_url` = 'https://shifshqip.com',
  `duration_seconds` = 0,
  `allow_manual_close` = 1,
  `status` = 1
WHERE `id` = 1;
