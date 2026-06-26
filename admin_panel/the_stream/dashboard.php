<?php include_once ('includes/header.php'); ?>

<?php

$categories = "SELECT COUNT(*) as num FROM tbl_category";
$totalCategories = $connect->query($categories);
$totalCategories = $totalCategories->fetch_array();
$totalCategories = $totalCategories['num'];

$videos = "SELECT COUNT(*) as num FROM tbl_channel";
$totalVideos = $connect->query($videos);
$totalVideos = $totalVideos->fetch_array();
$totalVideos = $totalVideos['num'];

$deviceCount = 0;
$playlistCount = 0;
$bannedDeviceCount = 0;
$premiumNoAdsCount = 0;
$startupPopupActive = 0;
$tableCheck = mysqli_query($connect, "SHOW TABLES LIKE 'tbl_device_playlist'");
if ($tableCheck && mysqli_num_rows($tableCheck) > 0) {
    $devices = "SELECT COUNT(DISTINCT device_id) as num FROM tbl_device_playlist";
    $totalDevices = $connect->query($devices);
    if ($totalDevices) {
        $totalDevices = $totalDevices->fetch_array();
        $deviceCount = $totalDevices['num'];
    }

    $playlists = "SELECT COUNT(*) as num FROM tbl_device_playlist";
    $totalPlaylists = $connect->query($playlists);
    if ($totalPlaylists) {
        $totalPlaylists = $totalPlaylists->fetch_array();
        $playlistCount = $totalPlaylists['num'];
    }

    $blockTableCheck = mysqli_query($connect, "SHOW TABLES LIKE 'tbl_device_blocklist'");
    if ($blockTableCheck && mysqli_num_rows($blockTableCheck) > 0) {
        $bannedDevices = "SELECT COUNT(*) as num FROM tbl_device_blocklist WHERE status = 1";
        $totalBannedDevices = $connect->query($bannedDevices);
        if ($totalBannedDevices) {
            $totalBannedDevices = $totalBannedDevices->fetch_array();
            $bannedDeviceCount = $totalBannedDevices['num'];
        }
    }

    $accessTableCheck = mysqli_query($connect, "SHOW TABLES LIKE 'tbl_device_access'");
    if ($accessTableCheck && mysqli_num_rows($accessTableCheck) > 0) {
        $premiumDevices = "SELECT COUNT(*) as num FROM tbl_device_access WHERE status = 1 AND no_ads = 1";
        $totalPremiumDevices = $connect->query($premiumDevices);
        if ($totalPremiumDevices) {
            $totalPremiumDevices = $totalPremiumDevices->fetch_array();
            $premiumNoAdsCount = $totalPremiumDevices['num'];
        }
    }
}

$popupTableCheck = mysqli_query($connect, "SHOW TABLES LIKE 'tbl_startup_popup'");
if ($popupTableCheck && mysqli_num_rows($popupTableCheck) > 0) {
    $popupState = "SELECT COUNT(*) as num FROM tbl_startup_popup WHERE status = 1";
    $popupResult = $connect->query($popupState);
    if ($popupResult) {
        $popupResult = $popupResult->fetch_array();
        $startupPopupActive = $popupResult['num'];
    }
}

?>

<section class="content">

    <ol class="breadcrumb">
        <li><a href="dashboard.php">Dashboard</a></li>
        <li class="active">Home</a></li>
    </ol>

    <div class="container-fluid">

       <div class="row">

        <a href="category.php">
            <div class="col-lg-3 col-md-3 col-sm-6 col-xs-12">
                <div class="card demo-color-box bg-blue waves-effect corner-radius col-lg-12 col-md-12 col-sm-12 col-xs-12">
                    <br>
                    <div class="color-name uppercase"><?php echo $menu_category; ?></div>
                    <div class="color-name"><i class="material-icons">view_list</i></div>
                    <div class="color-class-name">Total ( <?php echo $totalCategories; ?> ) Categories</div>
                    <br>
                </div>
            </div>
        </a>

        <a href="channel.php">
            <div class="col-lg-3 col-md-3 col-sm-6 col-xs-12">
                <div class="card demo-color-box bg-blue waves-effect corner-radius col-lg-12 col-md-12 col-sm-12 col-xs-12">
                    <br>
                    <div class="color-name uppercase"><?php echo $menu_channel; ?></div>
                    <div class="color-name"><i class="material-icons">live_tv</i></div>
                    <div class="color-class-name">Total ( <?php echo $totalVideos; ?> ) Videos</div>
                    <br>
                </div>
            </div>
        </a>

        <a href="ads.php">
            <div class="col-lg-3 col-md-3 col-sm-6 col-xs-12">
                <div class="card demo-color-box bg-blue waves-effect corner-radius col-lg-12 col-md-12 col-sm-12 col-xs-12">
                    <br>
                    <div class="color-name uppercase"><?php echo $menu_ads; ?></div>
                    <div class="color-name"><i class="material-icons">monetization_on</i></div>
                    <div class="color-class-name">App Monetization</div>
                    <br>
                </div>
            </div>
        </a>

        <a href="notification.php">
            <div class="col-lg-3 col-md-3 col-sm-6 col-xs-12">
                <div class="card demo-color-box bg-blue waves-effect corner-radius col-lg-12 col-md-12 col-sm-12 col-xs-12">
                    <br>
                    <div class="color-name uppercase"><?php echo $menu_notification ?></div>
                    <div class="color-name"><i class="material-icons">notifications</i></div>
                    <div class="color-class-name">Send notification to your users</div>
                    <br>
                </div>
            </div>
        </a>

        <a href="admin.php">
            <div class="col-lg-3 col-md-3 col-sm-3 col-xs-12">
                <div class="card demo-color-box bg-blue waves-effect corner-radius col-lg-12 col-md-12 col-sm-12 col-xs-12">
                    <br>
                    <div class="color-name uppercase"><?php echo $menu_administrator; ?></div>
                    <div class="color-name"><i class="material-icons">people</i></div>
                    <div class="color-class-name">Admin Panel Privileges</div>
                    <br>
                </div>
            </div>
        </a>

        <a href="settings.php">
            <div class="col-lg-3 col-md-3 col-sm-3 col-xs-12">
                <div class="card demo-color-box bg-blue waves-effect corner-radius col-lg-12 col-md-12 col-sm-12 col-xs-12">
                    <br>
                    <div class="color-name uppercase"><?php echo $menu_setting; ?></div>
                    <div class="color-name"><i class="material-icons">settings</i></div>
                    <div class="color-class-name">Key and Privacy Settings</div>
                    <br>
                </div>
            </div>
        </a>

        <a href="apps.php">
            <div class="col-lg-3 col-md-3 col-sm-3 col-xs-12">
                <div class="card demo-color-box bg-blue waves-effect corner-radius col-lg-12 col-md-12 col-sm-12 col-xs-12">
                    <br>
                    <div class="color-name uppercase"><?php echo $menu_app; ?></div>
                    <div class="color-name"><i class="material-icons">adb</i></div>
                    <div class="color-class-name">Apps and Redirect</div>
                    <br>
                </div>
            </div>
        </a>

        <a href="device-playlist.php">
            <div class="col-lg-3 col-md-3 col-sm-3 col-xs-12">
                <div class="card demo-color-box bg-blue waves-effect corner-radius col-lg-12 col-md-12 col-sm-12 col-xs-12">
                    <br>
                    <div class="color-name uppercase"><?php echo $menu_device_playlist; ?></div>
                    <div class="color-name"><i class="material-icons">devices</i></div>
                    <div class="color-class-name"><?php echo $deviceCount; ?> devices • <?php echo $playlistCount; ?> playlists • <?php echo $bannedDeviceCount; ?> banned • <?php echo $premiumNoAdsCount; ?> no-ads</div>
                    <br>
                </div>
            </div>
        </a>

        <a href="startup-popup.php">
            <div class="col-lg-3 col-md-3 col-sm-3 col-xs-12">
                <div class="card demo-color-box bg-blue waves-effect corner-radius col-lg-12 col-md-12 col-sm-12 col-xs-12">
                    <br>
                    <div class="color-name uppercase"><?php echo $menu_startup_popup; ?></div>
                    <div class="color-name"><i class="material-icons">announcement</i></div>
                    <div class="color-class-name"><?php echo $startupPopupActive > 0 ? 'Active' : 'Inactive'; ?></div>
                    <br>
                </div>
            </div>
        </a>

        <a href="license.php">
            <div class="col-lg-3 col-md-3 col-sm-3 col-xs-12">
                <div class="card demo-color-box bg-blue waves-effect corner-radius col-lg-12 col-md-12 col-sm-12 col-xs-12">
                    <br>
                    <div class="color-name uppercase"><?php echo $menu_license; ?></div>
                    <div class="color-name"><i class="material-icons">vpn_key</i></div>
                    <div class="color-class-name">Item purchase code</div>
                    <br>
                </div>
            </div>
        </a>

    </div>

</div>

</section>

<?php include_once('includes/footer.php'); ?>