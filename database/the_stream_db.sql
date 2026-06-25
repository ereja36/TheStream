-- phpMyAdmin SQL Dump
-- version 5.2.1
-- https://www.phpmyadmin.net/
--
-- Host: 127.0.0.1
-- Generation Time: Jul 01, 2025 at 04:52 PM
-- Server version: 10.4.32-MariaDB
-- PHP Version: 8.1.25

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Database: `shif_shqip_db`
--

-- --------------------------------------------------------

--
-- Table structure for table `tbl_ads`
--

CREATE TABLE `tbl_ads` (
  `id` int(11) NOT NULL,
  `ad_status` varchar(5) NOT NULL DEFAULT 'on',
  `ad_type` varchar(45) NOT NULL DEFAULT 'admob',
  `backup_ads` varchar(45) NOT NULL DEFAULT 'none',
  `admob_publisher_id` varchar(45) NOT NULL DEFAULT '0',
  `admob_app_id` varchar(100) NOT NULL DEFAULT '0',
  `admob_banner_unit_id` varchar(100) NOT NULL DEFAULT '0',
  `admob_interstitial_unit_id` varchar(100) NOT NULL DEFAULT '0',
  `admob_native_unit_id` varchar(100) NOT NULL DEFAULT '0',
  `admob_app_open_ad_unit_id` varchar(45) NOT NULL DEFAULT '0',
  `ad_manager_banner_unit_id` varchar(255) NOT NULL DEFAULT '/6499/example/banner',
  `ad_manager_interstitial_unit_id` varchar(255) NOT NULL DEFAULT '/6499/example/interstitial',
  `ad_manager_native_unit_id` varchar(255) NOT NULL DEFAULT '/6499/example/native',
  `ad_manager_app_open_ad_unit_id` varchar(255) NOT NULL DEFAULT '/6499/example/app-open',
  `fan_banner_unit_id` varchar(100) NOT NULL DEFAULT '0',
  `fan_interstitial_unit_id` varchar(100) NOT NULL DEFAULT '0',
  `fan_native_unit_id` varchar(100) NOT NULL DEFAULT '0',
  `startapp_app_id` varchar(45) NOT NULL DEFAULT '0',
  `unity_game_id` varchar(45) NOT NULL DEFAULT '0',
  `unity_banner_placement_id` varchar(45) NOT NULL DEFAULT 'banner',
  `unity_interstitial_placement_id` varchar(45) NOT NULL DEFAULT 'video',
  `applovin_banner_ad_unit_id` varchar(45) NOT NULL DEFAULT '0',
  `applovin_interstitial_ad_unit_id` varchar(45) NOT NULL DEFAULT '0',
  `applovin_native_ad_manual_unit_id` varchar(45) NOT NULL DEFAULT '0',
  `applovin_app_open_ad_unit_id` varchar(255) NOT NULL DEFAULT '0',
  `applovin_banner_mrec_zone_id` varchar(255) NOT NULL DEFAULT '0',
  `applovin_banner_zone_id` varchar(45) NOT NULL DEFAULT '0',
  `applovin_interstitial_zone_id` varchar(45) NOT NULL DEFAULT '0',
  `ironsource_app_key` varchar(255) NOT NULL DEFAULT '85460dcd',
  `ironsource_banner_placement_name` varchar(255) NOT NULL DEFAULT 'DefaultBanner',
  `ironsource_interstitial_placement_name` varchar(255) NOT NULL DEFAULT 'DefaultInterstitial',
  `wortise_app_id` varchar(255) NOT NULL DEFAULT 'test-app-id',
  `wortise_banner_unit_id` varchar(255) NOT NULL DEFAULT 'test-banner',
  `wortise_interstitial_unit_id` varchar(255) NOT NULL DEFAULT 'test-interstitial',
  `wortise_native_unit_id` varchar(255) NOT NULL DEFAULT 'test-native',
  `wortise_app_open_unit_id` varchar(255) NOT NULL DEFAULT 'test-app-open',
  `mopub_banner_ad_unit_id` varchar(45) NOT NULL DEFAULT '0',
  `mopub_interstitial_ad_unit_id` varchar(45) NOT NULL DEFAULT '0',
  `interstitial_ad_interval` int(11) NOT NULL DEFAULT 3,
  `native_ad_interval` int(11) NOT NULL DEFAULT 20,
  `native_ad_index` int(11) NOT NULL DEFAULT 4,
  `date_time` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `test_mode` varchar(10) NOT NULL DEFAULT '1'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `tbl_ads`
--

INSERT INTO `tbl_ads` (`id`, `ad_status`, `ad_type`, `backup_ads`, `admob_publisher_id`, `admob_app_id`, `admob_banner_unit_id`, `admob_interstitial_unit_id`, `admob_native_unit_id`, `admob_app_open_ad_unit_id`, `ad_manager_banner_unit_id`, `ad_manager_interstitial_unit_id`, `ad_manager_native_unit_id`, `ad_manager_app_open_ad_unit_id`, `fan_banner_unit_id`, `fan_interstitial_unit_id`, `fan_native_unit_id`, `startapp_app_id`, `unity_game_id`, `unity_banner_placement_id`, `unity_interstitial_placement_id`, `applovin_banner_ad_unit_id`, `applovin_interstitial_ad_unit_id`, `applovin_native_ad_manual_unit_id`, `applovin_app_open_ad_unit_id`, `applovin_banner_mrec_zone_id`, `applovin_banner_zone_id`, `applovin_interstitial_zone_id`, `ironsource_app_key`, `ironsource_banner_placement_name`, `ironsource_interstitial_placement_name`, `wortise_app_id`, `wortise_banner_unit_id`, `wortise_interstitial_unit_id`, `wortise_native_unit_id`, `wortise_app_open_unit_id`, `mopub_banner_ad_unit_id`, `mopub_interstitial_ad_unit_id`, `interstitial_ad_interval`, `native_ad_interval`, `native_ad_index`, `date_time`, `test_mode`) VALUES
(1, 'on', 'admob', 'none', 'pub-3940256099942544', '0', 'ca-app-pub-3940256099942544/9214589741', 'ca-app-pub-3940256099942544/1033173712', 'ca-app-pub-3940256099942544/2247696110', 'ca-app-pub-3940256099942544/9257395921', '/6499/example/banner', '/6499/example/interstitial', '/6499/example/native', '/6499/example/app-open', 'YOUR_PLACEMENT_ID', 'YOUR_PLACEMENT_ID', 'YOUR_PLACEMENT_ID', '123456789', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '85460dcd', 'DefaultBanner', 'DefaultInterstitial', 'test-app-id', 'test-banner', 'test-interstitial', 'test-native', 'test-app-open', '0', '0', 3, 20, 4, '2025-07-01 09:41:44', '0');

-- --------------------------------------------------------

--
-- Table structure for table `tbl_app_config`
--

CREATE TABLE `tbl_app_config` (
  `id` int(11) NOT NULL,
  `package_name` varchar(255) NOT NULL,
  `status` varchar(5) NOT NULL,
  `redirect_url` varchar(500) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Table structure for table `tbl_category`
--

CREATE TABLE `tbl_category` (
  `cid` int(11) NOT NULL,
  `category_name` varchar(255) NOT NULL,
  `category_image` varchar(255) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `tbl_category`
--

INSERT INTO `tbl_category` (`cid`, `category_name`, `category_image`) VALUES
(1, 'Automotive', '1625134846_automotive.jpg'),
(2, 'Movies', '1625134869_movies.jpg'),
(3, 'Family', '1625134858_family.jpg'),
(4, 'Education', '1625135228_6094-2017-04-13.png'),
(5, 'Entertainment', '1625135250_3455-2017-04-13.png'),
(6, 'Music', '1625135259_7102-2017-04-13.png');

-- --------------------------------------------------------

--
-- Table structure for table `tbl_channel`
--

CREATE TABLE `tbl_channel` (
  `id` int(11) NOT NULL,
  `category_id` int(11) NOT NULL,
  `channel_name` varchar(255) NOT NULL,
  `channel_image` varchar(255) NOT NULL,
  `channel_url` varchar(1000) NOT NULL,
  `channel_description` text NOT NULL,
  `channel_type` varchar(45) NOT NULL DEFAULT 'URL',
  `channel_status` int(11) NOT NULL DEFAULT 1,
  `video_id` varchar(45) NOT NULL,
  `user_agent` varchar(500) NOT NULL DEFAULT 'default',
  `total_views` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `tbl_channel`
--

INSERT INTO `tbl_channel` (`id`, `category_id`, `channel_name`, `channel_image`, `channel_url`, `channel_description`, `channel_type`, `channel_status`, `video_id`, `user_agent`, `total_views`) VALUES
(1, 1, 'What care can you get for a grand?', '1625126143_WhatCarCanYouGetForAGrand.jpg', 'http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WhatCarCanYouGetForAGrand.mp4', '<p>The Smoking Tire meets up with Chris and Jorge from CarsForAGrand.com to see just how far $1,000 can go when looking for a car.The Smoking Tire meets up with Chris and Jorge from CarsForAGrand.com to see just how far $1,000 can go when looking for a car.</p>\r\n', 'URL', 1, '', 'default', 85437),
(2, 1, 'We Are Going On Bullrun', '1625126278_WeAreGoingOnBullrun.jpg', 'http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WeAreGoingOnBullrun.mp4', '<p>The Smoking Tire is going on the 2010 Bullrun Live Rally in a 2011 Shelby GT500, and posting a video from the road every single day! The only place to watch them is by subscribing to The Smoking Tire or watching at BlackMagicShine.com</p>\r\n', 'URL', 1, '', 'default', 83192),
(3, 1, 'Volkswagen GTI Review', '1625126489_VolkswagenGTIReview.jpg', 'http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/VolkswagenGTIReview.mp4', '<p>The Smoking Tire heads out to Adams Motorsports Park in Riverside, CA to test the most requested car of 2010, the Volkswagen GTI. Will it beat the Mazdaspeed3&#39;s standard-setting lap time? Watch and see...</p>\r\n', 'URL', 1, '', 'default', 81206),
(4, 2, 'Tears of Steel', '1625126603_TearsOfSteel.jpg', 'http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4', '<p>Tears of Steel was realized with crowd-funding by users of the open source 3D creation tool Blender. Target was to improve and test a complete open and free pipeline for visual effects in film - and to make a compelling sci-fi film in Amsterdam, the Netherlands. The film itself, and all raw material used for making it, have been released under the Creatieve Commons 3.0 Attribution license. Visit the tearsofsteel.org website to find out more about this, or to purchase the 4-DVD box with a lot of extras. (CC) Blender Foundation - http://www.tearsofsteel.org</p>\r\n', 'URL', 1, '', 'default', 79102),
(5, 1, 'Subaru Outback On Street And Dirt', '1625126668_SubaruOutbackOnStreetAndDirt.jpg', 'http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackOnStreetAndDirt.mp4', '<p>Smoking Tire takes the all-new Subaru Outback to the highest point we can find in hopes our customer-appreciation Balloon Launch will get some free T-shirts into the hands of our viewers.</p>\r\n', 'URL', 1, '', 'default', 78021),
(6, 2, 'Sintel', '1625126723_Sintel.jpg', 'http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4', '<p>Sintel is an independently produced short film, initiated by the Blender Foundation as a means to further improve and validate the free/open source 3D creation suite Blender. With initial funding provided by 1000s of donations via the internet community, it has again proven to be a viable development model for both open 3D technology as for independent animation film.\nThis 15 minute film has been realized in the studio of the Amsterdam Blender Institute, by an international team of artists and developers. In addition to that, several crucial technical and creative targets have been realized online, by developers and artists and teams all over the world.\nwww.sintel.org</p>\r\n', 'URL', 1, '', 'default', 76425),
(7, 5, 'For Bigger Meltdowns', '1625126955_ForBiggerMeltdowns.jpg', 'http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerMeltdowns.mp4', '<p>Introducing Chromecast. The easiest way to enjoy online video and music on your TV&mdash;for when you want to make Buster&#39;s big meltdowns even bigger. For $35. Learn how to use Chromecast with Netflix and more at google.com/chromecast.</p>\r\n', 'URL', 1, '', 'default', 72012),
(8, 3, 'For Bigger Joyrides', '1625127012_ForBiggerJoyrides.jpg', 'http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4', '<p>Introducing Chromecast. The easiest way to enjoy online video and music on your TV&mdash;for the times that call for bigger joyrides. For $35. Learn how to use Chromecast with YouTube and more at google.com/chromecast.</p>\r\n', 'URL', 1, '', 'default', 73386),
(9, 3, 'For Bigger Fun', '1625127080_ForBiggerFun.jpg', 'http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4', '<p>Introducing Chromecast. The easiest way to enjoy online video and music on your TV. For $35. Find out more at google.com/chromecast.</p>\r\n', 'URL', 1, '', 'default', 70383),
(10, 2, 'Elephant Dream', '1625127257_ElephantsDream.jpg', 'http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4', '<p>The first Blender Open Movie from 2006</p>\r\n', 'URL', 1, '', 'default', 68013),
(11, 2, 'Big Buck Bunny', '1625127305_BigBuckBunny.jpg', 'http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4', '<p>Big Buck Bunny tells the story of a giant rabbit with a heart bigger than himself. When one sunny day three rodents rudely harass him, something snaps... and the rabbit ain&#39;t no bunny anymore! In the typical cartoon tradition he prepares the nasty rodents a comical revenge. Licensed under the Creative Commons Attribution license http://www.bigbuckbunny.org</p>\r\n', 'URL', 1, '', 'default', 65834);

-- --------------------------------------------------------

--
-- Table structure for table `tbl_device_playlist`
--

CREATE TABLE `tbl_device_playlist` (
  `id` int(11) NOT NULL,
  `device_id` varchar(255) NOT NULL,
  `playlist_name` varchar(255) NOT NULL DEFAULT '',
  `playlist_type` varchar(30) NOT NULL DEFAULT 'm3u',
  `playlist_source` varchar(1000) NOT NULL,
  `channels_count` int(11) NOT NULL DEFAULT 0,
  `status` tinyint(1) NOT NULL DEFAULT 1,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `tbl_device_blocklist`
--

CREATE TABLE `tbl_device_blocklist` (
  `id` int(11) NOT NULL,
  `device_id` varchar(255) NOT NULL,
  `reason` varchar(500) NOT NULL DEFAULT '',
  `status` tinyint(1) NOT NULL DEFAULT 1,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `tbl_device_access`
--

CREATE TABLE `tbl_device_access` (
  `id` int(11) NOT NULL,
  `device_id` varchar(255) NOT NULL,
  `no_ads` tinyint(1) NOT NULL DEFAULT 0,
  `status` tinyint(1) NOT NULL DEFAULT 1,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `tbl_startup_popup`
--

CREATE TABLE `tbl_startup_popup` (
  `id` int(11) NOT NULL,
  `title` varchar(255) NOT NULL DEFAULT '',
  `message` text NOT NULL,
  `popup_type` varchar(20) NOT NULL DEFAULT 'text',
  `media_url` varchar(1000) NOT NULL DEFAULT '',
  `target_url` varchar(1000) NOT NULL DEFAULT '',
  `duration_seconds` int(11) NOT NULL DEFAULT 0,
  `allow_manual_close` tinyint(1) NOT NULL DEFAULT 1,
  `device_id` varchar(255) NOT NULL DEFAULT '',
  `status` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Dumping data for table `tbl_startup_popup`
--

INSERT INTO `tbl_startup_popup` (`id`, `title`, `message`, `popup_type`, `media_url`, `target_url`, `duration_seconds`, `allow_manual_close`, `device_id`, `status`) VALUES
(1, '', '', 'text', '', '', 0, 1, '', 0);

-- --------------------------------------------------------

--
-- Table structure for table `tbl_fcm_template`
--

CREATE TABLE `tbl_fcm_template` (
  `id` int(11) NOT NULL,
  `title` varchar(255) NOT NULL DEFAULT 'Notification',
  `message` text NOT NULL,
  `image` varchar(255) NOT NULL,
  `link` varchar(500) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `tbl_fcm_template`
--

INSERT INTO `tbl_fcm_template` (`id`, `title`, `message`, `image`, `link`) VALUES
(1, 'Shif Shqip', 'Hello World! this is Shif Shqip App.', '1633711641_notification.jpg', ''),
(2, 'Shif Shqip', 'A new updated version is available now!', '1633711688_notification.jpg', '');

-- --------------------------------------------------------

--
-- Table structure for table `tbl_license`
--

CREATE TABLE `tbl_license` (
  `id` int(11) NOT NULL,
  `purchase_code` varchar(255) NOT NULL,
  `item_id` int(11) NOT NULL,
  `item_name` varchar(255) NOT NULL,
  `buyer` varchar(255) NOT NULL,
  `license_type` varchar(45) NOT NULL,
  `purchase_date` varchar(255) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Table structure for table `tbl_settings`
--

CREATE TABLE `tbl_settings` (
  `id` int(11) NOT NULL,
  `app_fcm_key` text NOT NULL,
  `api_key` varchar(255) NOT NULL,
  `package_name` varchar(255) NOT NULL DEFAULT 'com.shif.shqip.android',
  `onesignal_app_id` varchar(500) NOT NULL DEFAULT '0',
  `onesignal_rest_api_key` varchar(500) NOT NULL DEFAULT '0',
  `privacy_policy` text NOT NULL,
  `providers` varchar(45) NOT NULL DEFAULT 'onesignal',
  `protocol_type` varchar(10) NOT NULL DEFAULT 'http',
  `fcm_notification_topic` varchar(255) NOT NULL DEFAULT 'shif_shqip_topic',
  `more_apps_url` text NOT NULL,
  `youtube_api_key` varchar(255) NOT NULL DEFAULT '0'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `tbl_settings`
--

INSERT INTO `tbl_settings` (`id`, `app_fcm_key`, `api_key`, `package_name`, `onesignal_app_id`, `onesignal_rest_api_key`, `privacy_policy`, `providers`, `protocol_type`, `fcm_notification_topic`, `more_apps_url`, `youtube_api_key`) VALUES
(1, '0', 'CHANGE_ME_GENERATE_FROM_PANEL', 'com.shif.shqip.android', '00000000-0000-0000-0000-000000000000', 'XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', '<p>Shif Shqip app is provided as is.</p>\r\n\r\n<p>This page is used to inform visitors regarding our policies with the collection, use, and disclosure of Personal Information if anyone decided to use our Service.</p>\r\n\r\n<p>If you choose to use our Service, then you agree to the collection and use of information in relation to this policy. The Personal Information that we collect is used for providing and improving the Service. We will not use or share your information with anyone except as described in this Privacy Policy.</p>\r\n\r\n<p>The terms used in this Privacy Policy have the same meanings as in our Terms and Conditions unless otherwise defined in this Privacy Policy.</p>\r\n\r\n<p><strong>Information Collection and Use</strong></p>\r\n\r\n<p>For a better experience, while using our Service, we may require you to provide us with certain personally identifiable information. The information that we request will be retained by us and used as described in this privacy policy.</p>\r\n\r\n<p>The app may use third party services that collect information used to identify you.</p>\r\n\r\n<p><strong>Changes to This Privacy Policy</strong></p>\r\n\r\n<p>We may update our Privacy Policy from time to time. Thus, you are advised to review this page periodically for any changes.</p>\r\n', 'firebase', 'http', 'shif_shqip_topic', 'https://play.google.com/store/apps/developer?id=Shif+Shqip', '0');

-- --------------------------------------------------------

--
-- Table structure for table `tbl_user`
--

CREATE TABLE `tbl_user` (
  `id` int(11) NOT NULL,
  `username` varchar(15) NOT NULL,
  `password` text NOT NULL,
  `email` varchar(100) NOT NULL,
  `user_role` enum('100','101','102') NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `tbl_user`
--

INSERT INTO `tbl_user` (`id`, `username`, `password`, `email`, `user_role`) VALUES
(1, 'admin', 'd82494f05d6917ba02f7aaa29689ccb444bb73f20380876cb05d1f37537b7892', 'youremail@gmail.com', '100');

--
-- Indexes for dumped tables
--

--
-- Indexes for table `tbl_ads`
--
ALTER TABLE `tbl_ads`
  ADD PRIMARY KEY (`id`);

--
-- Indexes for table `tbl_app_config`
--
ALTER TABLE `tbl_app_config`
  ADD PRIMARY KEY (`id`);

--
-- Indexes for table `tbl_category`
--
ALTER TABLE `tbl_category`
  ADD PRIMARY KEY (`cid`);

--
-- Indexes for table `tbl_channel`
--
ALTER TABLE `tbl_channel`
  ADD PRIMARY KEY (`id`);

--
-- Indexes for table `tbl_device_playlist`
--
ALTER TABLE `tbl_device_playlist`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_device_id` (`device_id`),
  ADD KEY `idx_updated_at` (`updated_at`);

--
-- Indexes for table `tbl_device_blocklist`
--
ALTER TABLE `tbl_device_blocklist`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uniq_device_id` (`device_id`),
  ADD KEY `idx_block_status` (`status`);

--
-- Indexes for table `tbl_device_access`
--
ALTER TABLE `tbl_device_access`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uniq_device_access_id` (`device_id`),
  ADD KEY `idx_no_ads_status` (`no_ads`,`status`);

--
-- Indexes for table `tbl_startup_popup`
--
ALTER TABLE `tbl_startup_popup`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_popup_status` (`status`),
  ADD KEY `idx_popup_device` (`device_id`);

--
-- Indexes for table `tbl_fcm_template`
--
ALTER TABLE `tbl_fcm_template`
  ADD PRIMARY KEY (`id`);

--
-- Indexes for table `tbl_license`
--
ALTER TABLE `tbl_license`
  ADD PRIMARY KEY (`id`);

--
-- Indexes for table `tbl_settings`
--
ALTER TABLE `tbl_settings`
  ADD PRIMARY KEY (`id`);

--
-- Indexes for table `tbl_user`
--
ALTER TABLE `tbl_user`
  ADD PRIMARY KEY (`id`);

--
-- AUTO_INCREMENT for dumped tables
--

--
-- AUTO_INCREMENT for table `tbl_ads`
--
ALTER TABLE `tbl_ads`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=2;

--
-- AUTO_INCREMENT for table `tbl_app_config`
--
ALTER TABLE `tbl_app_config`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `tbl_category`
--
ALTER TABLE `tbl_category`
  MODIFY `cid` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=7;

--
-- AUTO_INCREMENT for table `tbl_channel`
--
ALTER TABLE `tbl_channel`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=12;

--
-- AUTO_INCREMENT for table `tbl_device_playlist`
--
ALTER TABLE `tbl_device_playlist`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `tbl_device_blocklist`
--
ALTER TABLE `tbl_device_blocklist`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `tbl_device_access`
--
ALTER TABLE `tbl_device_access`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `tbl_startup_popup`
--
ALTER TABLE `tbl_startup_popup`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=2;

--
-- AUTO_INCREMENT for table `tbl_fcm_template`
--
ALTER TABLE `tbl_fcm_template`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=3;

--
-- AUTO_INCREMENT for table `tbl_license`
--
ALTER TABLE `tbl_license`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `tbl_settings`
--
ALTER TABLE `tbl_settings`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=2;

--
-- AUTO_INCREMENT for table `tbl_user`
--
ALTER TABLE `tbl_user`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=2;
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
