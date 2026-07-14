<?php include_once('includes/header.php'); ?>

<?php

error_reporting(0);

$tableExists = false;
$tableCheck = mysqli_query($connect, "SHOW TABLES LIKE 'tbl_device_playlist'");
if ($tableCheck && mysqli_num_rows($tableCheck) > 0) {
    $tableExists = true;
}

$blockTableExists = false;
$blockTableCheck = mysqli_query($connect, "SHOW TABLES LIKE 'tbl_device_blocklist'");
if ($blockTableCheck && mysqli_num_rows($blockTableCheck) > 0) {
    $blockTableExists = true;
}

$accessTableExists = false;
$accessTableCheck = mysqli_query($connect, "SHOW TABLES LIKE 'tbl_device_access'");
if ($accessTableCheck && mysqli_num_rows($accessTableCheck) > 0) {
    $accessTableExists = true;
}

if (!$tableExists) {
    $_SESSION['msg'] = "Table tbl_device_playlist is missing. Please import latest SQL migration.";
} else if (!$blockTableExists) {
    $_SESSION['msg'] = "Table tbl_device_blocklist is missing. Ban/Unban features are disabled until migration is applied.";
} else if (!$accessTableExists) {
    $_SESSION['msg'] = "Table tbl_device_access is missing. No-Ads premium controls are disabled until migration is applied.";
}

if ($tableExists && isset($_POST['submit'])) {
    $arr = $_POST['chk_id'];
    $count = count((array) $arr);
    if ($count > 0) {
        foreach ($arr as $nid) {
            $nid = (int) $nid;
            $sql_delete = "DELETE FROM tbl_device_playlist WHERE id = $nid";
            $connect->query($sql_delete);
        }
        $_SESSION['msg'] = "$count selected playlists deleted";
    } else {
        $_SESSION['msg'] = "Whoops! no playlists selected to delete";
    }
    header("Location:device-playlist.php");
    exit;
}

if ($tableExists && isset($_GET['page']) && isset($_GET['disable'])) {
    if (validateResourceAccess($inputToken)) {
        $id = (int) $_GET['disable'];
        $data = array('status' => '0');
        $update = update('tbl_device_playlist', $data, "WHERE id = '$id'");
        if ($update > 0) {
            $_SESSION['msg'] = "Playlist successfully disabled";
            header('Location:device-playlist.php?page=' . $_GET['page']);
            exit;
        }
    } else {
        systemUpdateFlag($invalidResponse);
    }
}

if ($tableExists && isset($_GET['page']) && isset($_GET['enable'])) {
    if (validateResourceAccess($inputToken)) {
        $id = (int) $_GET['enable'];
        $data = array('status' => '1');
        $update = update('tbl_device_playlist', $data, "WHERE id = '$id'");
        if ($update > 0) {
            $_SESSION['msg'] = "Playlist successfully enabled";
            header("Location:device-playlist.php?page=" . $_GET['page']);
            exit;
        }
    } else {
        systemUpdateFlag($invalidResponse);
    }
}

if ($tableExists && $blockTableExists && isset($_GET['ban_device'])) {
    if (validateResourceAccess($inputToken)) {
        $deviceId = clean($_GET['ban_device']);
        $reason = clean($_GET['reason']);
        if ($reason == '') {
            $reason = 'Abuse detected';
        }

        $sqlCheck = "SELECT id FROM tbl_device_blocklist WHERE device_id = '$deviceId' LIMIT 1";
        $resultCheck = mysqli_query($connect, $sqlCheck);
        if ($resultCheck && mysqli_num_rows($resultCheck) > 0) {
            $row = mysqli_fetch_assoc($resultCheck);
            $data = array(
                'status' => '1',
                'reason' => $reason
            );
            update('tbl_device_blocklist', $data, "WHERE id = '" . $row['id'] . "'");
        } else {
            $data = array(
                'device_id' => $deviceId,
                'reason' => $reason,
                'status' => '1'
            );
            insert('tbl_device_blocklist', $data);
        }
        $_SESSION['msg'] = "Device successfully banned: " . $deviceId;
        header("Location:device-playlist.php");
        exit;
    } else {
        systemUpdateFlag($invalidResponse);
    }
}

if ($tableExists && $blockTableExists && isset($_GET['unban_device'])) {
    if (validateResourceAccess($inputToken)) {
        $deviceId = clean($_GET['unban_device']);
        $sqlCheck = "SELECT id FROM tbl_device_blocklist WHERE device_id = '$deviceId' LIMIT 1";
        $resultCheck = mysqli_query($connect, $sqlCheck);
        if ($resultCheck && mysqli_num_rows($resultCheck) > 0) {
            $row = mysqli_fetch_assoc($resultCheck);
            $data = array(
                'status' => '0'
            );
            update('tbl_device_blocklist', $data, "WHERE id = '" . $row['id'] . "'");
        }
        $_SESSION['msg'] = "Device successfully unbanned: " . $deviceId;
        header("Location:device-playlist.php");
        exit;
    } else {
        systemUpdateFlag($invalidResponse);
    }
}

if ($tableExists && $accessTableExists && isset($_GET['enable_no_ads'])) {
    if (validateResourceAccess($inputToken)) {
        $deviceId = clean($_GET['enable_no_ads']);
        $sqlCheck = "SELECT id FROM tbl_device_access WHERE device_id = '$deviceId' LIMIT 1";
        $resultCheck = mysqli_query($connect, $sqlCheck);
        if ($resultCheck && mysqli_num_rows($resultCheck) > 0) {
            $row = mysqli_fetch_assoc($resultCheck);
            $data = array(
                'no_ads' => '1',
                'status' => '1'
            );
            update('tbl_device_access', $data, "WHERE id = '" . $row['id'] . "'");
        } else {
            $data = array(
                'device_id' => $deviceId,
                'no_ads' => '1',
                'status' => '1'
            );
            insert('tbl_device_access', $data);
        }
        $_SESSION['msg'] = "No-ads premium enabled for device: " . $deviceId;
        header("Location:device-playlist.php");
        exit;
    } else {
        systemUpdateFlag($invalidResponse);
    }
}

if ($tableExists && $accessTableExists && isset($_GET['disable_no_ads'])) {
    if (validateResourceAccess($inputToken)) {
        $deviceId = clean($_GET['disable_no_ads']);
        $sqlCheck = "SELECT id FROM tbl_device_access WHERE device_id = '$deviceId' LIMIT 1";
        $resultCheck = mysqli_query($connect, $sqlCheck);
        if ($resultCheck && mysqli_num_rows($resultCheck) > 0) {
            $row = mysqli_fetch_assoc($resultCheck);
            $data = array(
                'no_ads' => '0',
                'status' => '1'
            );
            update('tbl_device_access', $data, "WHERE id = '" . $row['id'] . "'");
        }
        $_SESSION['msg'] = "No-ads premium disabled for device: " . $deviceId;
        header("Location:device-playlist.php");
        exit;
    } else {
        systemUpdateFlag($invalidResponse);
    }
}

$keyword = '';
$reload = "device-playlist.php";

if ($tableExists && isset($_REQUEST['keyword']) && $_REQUEST['keyword'] <> "") {
    $keyword = clean($_REQUEST['keyword']);
    if ($blockTableExists && $accessTableExists) {
        $sql = "SELECT p.*, IFNULL(b.status, 0) AS device_blocked, IFNULL(b.reason, '') AS block_reason, IFNULL(a.no_ads, 0) AS no_ads
                FROM tbl_device_playlist p
                LEFT JOIN tbl_device_blocklist b ON p.device_id COLLATE utf8mb4_unicode_ci = b.device_id COLLATE utf8mb4_unicode_ci
                LEFT JOIN tbl_device_access a ON p.device_id COLLATE utf8mb4_unicode_ci = a.device_id COLLATE utf8mb4_unicode_ci
                WHERE p.device_id LIKE '%$keyword%' 
                   OR p.playlist_name LIKE '%$keyword%'
                   OR p.playlist_source LIKE '%$keyword%'
                   OR b.reason LIKE '%$keyword%'
                ORDER BY p.updated_at DESC";
    } else if ($blockTableExists) {
        $sql = "SELECT p.*, IFNULL(b.status, 0) AS device_blocked, IFNULL(b.reason, '') AS block_reason, 0 AS no_ads
                FROM tbl_device_playlist p
                LEFT JOIN tbl_device_blocklist b ON p.device_id COLLATE utf8mb4_unicode_ci = b.device_id COLLATE utf8mb4_unicode_ci
                WHERE p.device_id LIKE '%$keyword%' 
                   OR p.playlist_name LIKE '%$keyword%'
                   OR p.playlist_source LIKE '%$keyword%'
                   OR b.reason LIKE '%$keyword%'
                ORDER BY p.updated_at DESC";
    } else if ($accessTableExists) {
        $sql = "SELECT p.*, 0 AS device_blocked, '' AS block_reason, IFNULL(a.no_ads, 0) AS no_ads
                FROM tbl_device_playlist p
                LEFT JOIN tbl_device_access a ON p.device_id COLLATE utf8mb4_unicode_ci = a.device_id COLLATE utf8mb4_unicode_ci
                WHERE p.device_id LIKE '%$keyword%' 
                   OR p.playlist_name LIKE '%$keyword%'
                   OR p.playlist_source LIKE '%$keyword%'
                ORDER BY p.updated_at DESC";
    } else {
        $sql = "SELECT p.*, 0 AS device_blocked, '' AS block_reason, 0 AS no_ads
                FROM tbl_device_playlist p
                WHERE p.device_id LIKE '%$keyword%' 
                   OR p.playlist_name LIKE '%$keyword%'
                   OR p.playlist_source LIKE '%$keyword%'
                ORDER BY p.updated_at DESC";
    }
    $result = $connect->query($sql);
} else if ($tableExists) {
    if ($blockTableExists && $accessTableExists) {
        $sql = "SELECT p.*, IFNULL(b.status, 0) AS device_blocked, IFNULL(b.reason, '') AS block_reason, IFNULL(a.no_ads, 0) AS no_ads
                FROM tbl_device_playlist p
                LEFT JOIN tbl_device_blocklist b ON p.device_id COLLATE utf8mb4_unicode_ci = b.device_id COLLATE utf8mb4_unicode_ci
                LEFT JOIN tbl_device_access a ON p.device_id COLLATE utf8mb4_unicode_ci = a.device_id COLLATE utf8mb4_unicode_ci
                ORDER BY p.updated_at DESC";
    } else if ($blockTableExists) {
        $sql = "SELECT p.*, IFNULL(b.status, 0) AS device_blocked, IFNULL(b.reason, '') AS block_reason, 0 AS no_ads
                FROM tbl_device_playlist p
                LEFT JOIN tbl_device_blocklist b ON p.device_id COLLATE utf8mb4_unicode_ci = b.device_id COLLATE utf8mb4_unicode_ci
                ORDER BY p.updated_at DESC";
    } else if ($accessTableExists) {
        $sql = "SELECT p.*, 0 AS device_blocked, '' AS block_reason, IFNULL(a.no_ads, 0) AS no_ads
                FROM tbl_device_playlist p
                LEFT JOIN tbl_device_access a ON p.device_id COLLATE utf8mb4_unicode_ci = a.device_id COLLATE utf8mb4_unicode_ci
                ORDER BY p.updated_at DESC";
    } else {
        $sql = "SELECT p.*, 0 AS device_blocked, '' AS block_reason, 0 AS no_ads
                FROM tbl_device_playlist p
                ORDER BY p.updated_at DESC";
    }
    $result = $connect->query($sql);
} else {
    $result = false;
}

$rpp = $postPerPage;
$page = intval($_GET["page"]);
if ($page <= 0) $page = 1;
$tcount = $result ? mysqli_num_rows($result) : 0;
$tpages = ($tcount) ? ceil($tcount / $rpp) : 1;
$count = 0;
$i = ($page - 1) * $rpp;

$totalDevices = 0;
$totalPlaylists = 0;
$activePlaylists = 0;
$blockedDevices = 0;
$premiumNoAdsDevices = 0;
if ($tableExists) {
    $queryDevices = $connect->query("SELECT COUNT(DISTINCT device_id) AS total FROM tbl_device_playlist");
    $queryPlaylists = $connect->query("SELECT COUNT(*) AS total FROM tbl_device_playlist");
    $queryActive = $connect->query("SELECT COUNT(*) AS total FROM tbl_device_playlist WHERE status = 1");
    if ($queryDevices) {
        $rowDevices = $queryDevices->fetch_assoc();
        $totalDevices = $rowDevices['total'];
    }
    if ($queryPlaylists) {
        $rowPlaylists = $queryPlaylists->fetch_assoc();
        $totalPlaylists = $rowPlaylists['total'];
    }
    if ($queryActive) {
        $rowActive = $queryActive->fetch_assoc();
        $activePlaylists = $rowActive['total'];
    }
    if ($blockTableExists) {
        $queryBlocked = $connect->query("SELECT COUNT(*) AS total FROM tbl_device_blocklist WHERE status = 1");
        if ($queryBlocked) {
            $rowBlocked = $queryBlocked->fetch_assoc();
            $blockedDevices = $rowBlocked['total'];
        }
    }
    if ($accessTableExists) {
        $queryPremium = $connect->query("SELECT COUNT(*) AS total FROM tbl_device_access WHERE status = 1 AND no_ads = 1");
        if ($queryPremium) {
            $rowPremium = $queryPremium->fetch_assoc();
            $premiumNoAdsDevices = $rowPremium['total'];
        }
    }
}

if (isset($_POST['jump_to_page'])) {
    $pageNumber = clean($_POST['page_number']);
    header('Location:device-playlist.php?page=' . $pageNumber);
    exit;
}

function shortText($value, $max = 90) {
    if (strlen($value) <= $max) {
        return $value;
    }
    return substr($value, 0, $max) . '...';
}

?>

<section class="content">

    <ol class="breadcrumb">
        <li><a href="dashboard.php">Dashboard</a></li>
        <li class="active">Device Playlists</li>
    </ol>

    <div class="container-fluid">

        <div class="row clearfix">
            <div class="col-lg-3 col-md-3 col-sm-6 col-xs-12">
                <div class="card demo-color-box bg-blue corner-radius">
                    <div class="body">
                        <div class="color-name uppercase">Total Devices</div>
                        <div class="color-class-name"><?php echo $totalDevices; ?></div>
                    </div>
                </div>
            </div>
            <div class="col-lg-3 col-md-3 col-sm-6 col-xs-12">
                <div class="card demo-color-box bg-blue corner-radius">
                    <div class="body">
                        <div class="color-name uppercase">Total Playlists</div>
                        <div class="color-class-name"><?php echo $totalPlaylists; ?></div>
                    </div>
                </div>
            </div>
            <div class="col-lg-3 col-md-3 col-sm-6 col-xs-12">
                <div class="card demo-color-box bg-blue corner-radius">
                    <div class="body">
                        <div class="color-name uppercase">Active Playlists</div>
                        <div class="color-class-name"><?php echo $activePlaylists; ?></div>
                    </div>
                </div>
            </div>
            <div class="col-lg-3 col-md-3 col-sm-6 col-xs-12">
                <div class="card demo-color-box bg-red corner-radius">
                    <div class="body">
                        <div class="color-name uppercase">Banned Devices</div>
                        <div class="color-class-name"><?php echo $blockedDevices; ?></div>
                    </div>
                </div>
            </div>
            <div class="col-lg-3 col-md-3 col-sm-6 col-xs-12">
                <div class="card demo-color-box bg-green corner-radius">
                    <div class="body">
                        <div class="color-name uppercase">No-Ads Premium</div>
                        <div class="color-class-name"><?php echo $premiumNoAdsDevices; ?></div>
                    </div>
                </div>
            </div>
        </div>

        <div class="row clearfix">
            <div class="col-lg-12 col-md-12 col-sm-12 col-xs-12">
                <div class="card corner-radius">
                    <div class="header">
                        <h2>DEVICE PLAYLISTS</h2>
                        <div class="header-dropdown m-r--5">
                            <a href="device-playlist-add.php"><button type="button" class="button button-rounded btn-offset waves-effect waves-float">ADD NEW PLAYLIST</button></a>
                        </div>
                    </div>

                    <div style="margin-top: -10px;" class="body table-responsive">

                        <?php if (isset($_SESSION['msg'])) { ?>
                            <div class='alert alert-info alert-dismissible corner-radius bottom-offset' role='alert'>
                                <button type="button" class="close" data-dismiss="alert" aria-label="Close"><span aria-hidden="true">&times;</span>&nbsp;&nbsp;</button>
                                <?php echo $_SESSION['msg']; ?>
                            </div>
                            <?php unset($_SESSION['msg']);
                        } ?>

                        <form method="get" id="form_validation">
                            <table class='table'>
                                <tr>
                                    <td>
                                        <div class="form-group form-float">
                                            <div class="form-line">
                                                <input type="text" class="form-control" name="keyword" placeholder="Search by device, playlist name, source..." required>
                                            </div>
                                        </div>
                                    </td>
                                    <td width="1%"><a href="device-playlist.php"><button type="button" class="button button-rounded waves-effect waves-float">RESET</button></a></td>
                                    <td width="1%"><button type="submit" class="btn bg-blue btn-circle waves-effect waves-circle waves-float"><i class="material-icons">search</i></button></td>
                                </tr>
                            </table>
                        </form>

                        <?php if (!$tableExists) { ?>
                            <p align="center" style="font-size: 110%;">Database table is not available yet. Please import latest SQL and refresh this page.</p>
                        <?php } else if ($tcount == 0) { ?>
                            <p align="center" style="font-size: 110%;">There are no device playlists.</p>
                        <?php } else { ?>

                            <form method="post" action="">

                                <div style="margin-left: 8px; margin-top: -36px; margin-bottom: 10px;">
                                    <button type="submit" name="<?php if (validateResourceAccess($inputToken)) {
                                                                        echo 'submit';
                                                                    } else {
                                                                        echo 'validate';
                                                                    } ?>" id="submit" class="button button-rounded waves-effect waves-float" onclick="return confirm('Are you sure want to delete selected playlists?')">Delete selected item(s)</button>&nbsp;&nbsp;
                                    <a href="" data-toggle="modal" data-target="#modal-jump-to-page"><button type="button" class="button button-rounded waves-effect waves-float">Jump to Page</button></a>
                                </div>

                                <table class='table table-hover table-striped'>
                                    <thead>
                                        <tr>
                                            <th width="1%">
                                                <div class="demo-checkbox" style="margin-bottom: -15px">
                                                    <input id="chk_all" name="chk_all" type="checkbox" class="filled-in chk-col-blue" />
                                                    <label for="chk_all"></label>
                                                </div>
                                            </th>
                                            <th width="18%">Device ID</th>
                                            <th width="14%">Playlist Name</th>
                                            <th width="8%">Type</th>
                                            <th width="30%">Playlist Source</th>
                                            <th width="6%"><center>Channels</center></th>
                                            <th width="11%">Last Sync</th>
                                            <th width="15%"><center>Action</center></th>
                                        </tr>
                                    </thead>

                                    <?php
                                    while (($count < $rpp) && ($i < $tcount)) {
                                        mysqli_data_seek($result, $i);
                                        $data = mysqli_fetch_array($result);
                                    ?>
                                        <tr>
                                            <td style="vertical-align: middle;" width="1%">
                                                <div class="demo-checkbox" style="margin-top: 13px;">
                                                    <input type="checkbox" name="chk_id[]" id="playlist_<?php echo $data['id']; ?>" class="chkbox filled-in chk-col-blue" value="<?php echo $data['id']; ?>" />
                                                    <label for="playlist_<?php echo $data['id']; ?>"></label>
                                                </div>
                                            </td>
                                            <td style="vertical-align: middle;">
                                                <?php if ($data['status'] == '1') {
                                                    echo $data['device_id'];
                                                } else {
                                                    echo '<strike>' . $data['device_id'] . '</strike>';
                                                } ?>
                                                <?php if ((int)$data['device_blocked'] == 1) { ?>
                                                    <br><span class="label label-rounded bg-red">BANNED</span>
                                                <?php } ?>
                                                <?php if ((int)$data['no_ads'] == 1) { ?>
                                                    <br><span class="label label-rounded bg-green">NO ADS</span>
                                                <?php } ?>
                                            </td>
                                            <td style="vertical-align: middle;">
                                                <?php if ($data['status'] == '1') {
                                                    echo $data['playlist_name'] == '' ? '-' : $data['playlist_name'];
                                                } else {
                                                    echo '<strike>' . ($data['playlist_name'] == '' ? '-' : $data['playlist_name']) . '</strike>';
                                                } ?>
                                            </td>
                                            <td style="vertical-align: middle;">
                                                <span class="label label-rounded bg-green"><?php echo strtoupper($data['playlist_type']); ?></span>
                                            </td>
                                            <td style="vertical-align: middle;" title="<?php echo htmlspecialchars($data['playlist_source']); ?>">
                                                <?php if ($data['status'] == '1') {
                                                    echo htmlspecialchars(shortText($data['playlist_source']));
                                                } else {
                                                    echo '<strike>' . htmlspecialchars(shortText($data['playlist_source'])) . '</strike>';
                                                } ?>
                                            </td>
                                            <td style="vertical-align: middle;">
                                                <center><?php echo (int) $data['channels_count']; ?></center>
                                            </td>
                                            <td style="vertical-align: middle;">
                                                <?php echo $data['updated_at']; ?>
                                            </td>
                                            <td style="vertical-align: middle;">
                                                <center>
                                                    <?php if ($data['status'] == '1') { ?>
                                                        <a href="device-playlist.php?page=<?php echo $page; ?>&disable=<?php echo $data['id']; ?>" onclick="return confirm('Are you sure want to disable this playlist?')">
                                                            <i class="material-icons">visibility</i>
                                                        </a>
                                                    <?php } else { ?>
                                                        <a href="device-playlist.php?page=<?php echo $page; ?>&enable=<?php echo $data['id']; ?>" onclick="return confirm('Are you sure want to enable this playlist?')">
                                                            <i class="material-icons">visibility_off</i>
                                                        </a>
                                                    <?php } ?>
                                                    <a href="device-playlist-edit.php?id=<?php echo $data['id']; ?>">
                                                        <i class="material-icons">mode_edit</i>
                                                    </a>
                                                    <?php if ($blockTableExists) { ?>
                                                        <?php if ((int)$data['device_blocked'] == 1) { ?>
                                                            <a href="device-playlist.php?unban_device=<?php echo urlencode($data['device_id']); ?>" onclick="return confirm('Unban this device ID?')">
                                                                <i class="material-icons" title="Unban device">verified_user</i>
                                                            </a>
                                                        <?php } else { ?>
                                                            <a href="device-playlist.php?ban_device=<?php echo urlencode($data['device_id']); ?>&reason=<?php echo urlencode('Abuse detected'); ?>" onclick="return confirm('Ban this device ID?')">
                                                                <i class="material-icons" title="Ban device">block</i>
                                                            </a>
                                                        <?php } ?>
                                                    <?php } ?>
                                                    <?php if ($accessTableExists) { ?>
                                                        <?php if ((int)$data['no_ads'] == 1) { ?>
                                                            <a href="device-playlist.php?disable_no_ads=<?php echo urlencode($data['device_id']); ?>" onclick="return confirm('Disable no-ads premium for this device?')">
                                                                <i class="material-icons" title="Disable no ads">money_off</i>
                                                            </a>
                                                        <?php } else { ?>
                                                            <a href="device-playlist.php?enable_no_ads=<?php echo urlencode($data['device_id']); ?>" onclick="return confirm('Enable no-ads premium for this device?')">
                                                                <i class="material-icons" title="Enable no ads">workspace_premium</i>
                                                            </a>
                                                        <?php } ?>
                                                    <?php } ?>
                                                    <a href="device-playlist-delete.php?id=<?php echo $data['id']; ?>" onclick="return confirm('Are you sure want to delete this playlist?')">
                                                        <i class="material-icons">delete</i>
                                                    </a>
                                                </center>
                                            </td>
                                        </tr>
                                    <?php
                                        $i++;
                                        $count++;
                                    }
                                    ?>
                                </table>
                            </form>
                        <?php } ?>

                        <?php if ($tableExists && $tcount > $postPerPage) {
                            echo pagination($reload, $page, $keyword, $tpages);
                        } ?>
                    </div>

                </div>
            </div>
        </div>
    </div>
</section>

<div style="margin-top: 20%;" class="modal fade" id="modal-jump-to-page" tabindex="-1" role="dialog">
    <div class="modal-dialog modal-md" role="document">
        <form method="post" id="form_validation">
            <div class="modal-content">
                <div class="modal-header">
                    <h4 class="modal-title" id="largeModalLabel">Jump to Page</h4>
                </div>
                <div class="modal-body">
                    <div class="form-group">
                        <div class="form-line">
                            <div class="font-12"><b>Input Page Number</b></div>
                            <input type="number" class="form-control" name="page_number" id="page_number" min="1" max="<?php echo $tpages; ?>" required>
                        </div>
                        <div class="help-info pull-left">Page number between ( 1 - <?php echo $tpages; ?> )</div>
                    </div>
                </div>
                <div class="modal-footer">
                    <button type="submit" name="jump_to_page" class="btn btn-link waves-effect">GO</button>
                </div>
            </div>
        </form>
    </div>
</div>

<?php include_once('includes/footer.php'); ?>
