<?php include_once('includes/header.php'); ?>

<?php

$tableExists = false;
$tableCheck = mysqli_query($connect, "SHOW TABLES LIKE 'tbl_device_playlist'");
if ($tableCheck && mysqli_num_rows($tableCheck) > 0) {
    $tableExists = true;
}

if (!$tableExists) {
    $_SESSION['msg'] = "Table tbl_device_playlist is missing. Please import latest SQL migration.";
    header("Location: device-playlist.php");
    exit;
}

if (isset($_POST['submit'])) {
    $status = ($_POST['status'] == 'active') ? '1' : '0';
    $device_id = clean($_POST['device_id']);
    $playlist_name = clean($_POST['playlist_name']);
    $playlist_type = clean($_POST['playlist_type']);
    $playlist_source = clean($_POST['playlist_source']);
    $channels_count = (int) clean($_POST['channels_count']);

    $sql = "SELECT id FROM tbl_device_playlist WHERE device_id = '$device_id' AND playlist_source = '$playlist_source' LIMIT 1";
    $result = mysqli_query($connect, $sql);
    if (mysqli_num_rows($result) > 0) {
        $row = mysqli_fetch_assoc($result);
        $data = array(
            'playlist_name' => $playlist_name,
            'playlist_type' => $playlist_type,
            'channels_count' => $channels_count,
            'status' => $status
        );
        update('tbl_device_playlist', $data, "WHERE id = '" . $row['id'] . "'");
        $_SESSION['msg'] = "Existing playlist updated for this device...";
    } else {
        $data = array(
            'device_id' => $device_id,
            'playlist_name' => $playlist_name,
            'playlist_type' => $playlist_type,
            'playlist_source' => $playlist_source,
            'channels_count' => $channels_count,
            'status' => $status
        );
        insert('tbl_device_playlist', $data);
        $_SESSION['msg'] = "Device playlist added successfully...";
    }
    header("Location: device-playlist.php");
    exit;
}

?>

<section class="content">

    <ol class="breadcrumb">
        <li><a href="dashboard.php">Dashboard</a></li>
        <li><a href="device-playlist.php">Device Playlists</a></li>
        <li class="active">Add Device Playlist</li>
    </ol>

    <div class="container-fluid">
        <div class="row clearfix">
            <div class="col-lg-12 col-md-12 col-sm-12 col-xs-12">
                <form id="form_validation" method="post">
                    <div class="card corner-radius">
                        <div class="header">
                            <h2>ADD DEVICE PLAYLIST</h2>
                        </div>
                        <div class="body">

                            <div class="row clearfix">
                                <div class="form-group col-sm-6">
                                    <div class="form-line">
                                        <div class="font-12">Device ID *</div>
                                        <input type="text" class="form-control" name="device_id" id="device_id" placeholder="ANDROID_ID" required>
                                    </div>
                                </div>

                                <div class="form-group col-sm-6">
                                    <div class="form-line">
                                        <div class="font-12">Playlist Name</div>
                                        <input type="text" class="form-control" name="playlist_name" id="playlist_name" placeholder="My IPTV">
                                    </div>
                                </div>

                                <div class="form-group col-sm-4">
                                    <div class="font-12">Playlist Type *</div>
                                    <select class="form-control show-tick" name="playlist_type" id="playlist_type">
                                        <option value="m3u">M3U</option>
                                        <option value="xtream">Xtream</option>
                                        <option value="website">Website</option>
                                        <option value="file">File</option>
                                        <option value="direct">Direct</option>
                                    </select>
                                </div>

                                <div class="form-group col-sm-4">
                                    <div class="form-line">
                                        <div class="font-12">Channels Count</div>
                                        <input type="number" class="form-control" name="channels_count" id="channels_count" min="0" value="0">
                                    </div>
                                </div>

                                <div class="form-group col-sm-4">
                                    <div class="font-12">Status *</div>
                                    <select class="form-control show-tick" name="status" id="status">
                                        <option value="active">Active</option>
                                        <option value="inactive">Inactive</option>
                                    </select>
                                </div>

                                <div class="form-group col-sm-12">
                                    <div class="form-line">
                                        <div class="font-12">Playlist Source URL *</div>
                                        <input type="text" class="form-control" name="playlist_source" id="playlist_source" placeholder="https://example.com/playlist.m3u" required>
                                    </div>
                                    <div class="help-info pull-left"><font color="#337ab7">Use URL, Xtream get.php URL, or portal source URL</font></div>
                                </div>

                                <div class="col-sm-12">
                                    <button class="button button-rounded waves-effect waves-float pull-right" type="submit" name="<?php if (validateResourceAccess($inputToken)) {
                                                                                                                                    echo 'submit';
                                                                                                                                } else {
                                                                                                                                    echo 'validate';
                                                                                                                                } ?>">SUBMIT</button>
                                </div>
                            </div>

                        </div>
                    </div>
                </form>
            </div>
        </div>
    </div>

</section>

<?php include_once('includes/footer.php'); ?>
