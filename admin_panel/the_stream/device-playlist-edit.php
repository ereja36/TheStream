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

if (isset($_GET['id'])) {
    $ID = (int) clean($_GET['id']);
    $sql = "SELECT * FROM tbl_device_playlist WHERE id = '$ID' LIMIT 1";
    $result = $connect->query($sql);
    $row = $result->fetch_assoc();
    if (!$row) {
        $_SESSION['msg'] = "Playlist not found";
        header("Location: device-playlist.php");
        exit;
    }
}

if (isset($_POST['submit'])) {
    $status = ($_POST['status'] == 'active') ? '1' : '0';
    $data = array(
        'device_id' => clean($_POST['device_id']),
        'playlist_name' => clean($_POST['playlist_name']),
        'playlist_type' => clean($_POST['playlist_type']),
        'playlist_source' => clean($_POST['playlist_source']),
        'channels_count' => (int) clean($_POST['channels_count']),
        'status' => $status
    );

    $update = update('tbl_device_playlist', $data, "WHERE id = '$ID'");
    if ($update > 0) {
        $_SESSION['msg'] = "Changes saved...";
        header("Location:device-playlist-edit.php?id=$ID");
        exit;
    }
}

?>

<section class="content">

    <ol class="breadcrumb">
        <li><a href="dashboard.php">Dashboard</a></li>
        <li><a href="device-playlist.php">Device Playlists</a></li>
        <li class="active">Edit Device Playlist</li>
    </ol>

    <div class="container-fluid">
        <div class="row clearfix">
            <div class="col-lg-12 col-md-12 col-sm-12 col-xs-12">
                <form id="form_validation" method="post">
                    <div class="card corner-radius">
                        <div class="header">
                            <h2>EDIT DEVICE PLAYLIST</h2>
                        </div>
                        <div class="body">

                            <?php if (isset($_SESSION['msg'])) { ?>
                                <div class='alert alert-info alert-dismissible corner-radius' role='alert'>
                                    <button type="button" class="close" data-dismiss="alert" aria-label="Close"><span aria-hidden="true">&times;</span>&nbsp;&nbsp;</button>
                                    <?php echo $_SESSION['msg']; ?>
                                </div>
                            <?php unset($_SESSION['msg']);
                            } ?>

                            <div class="row clearfix">
                                <div class="form-group col-sm-6">
                                    <div class="form-line">
                                        <div class="font-12">Device ID *</div>
                                        <input type="text" class="form-control" name="device_id" id="device_id" value="<?php echo $row['device_id']; ?>" required>
                                    </div>
                                </div>

                                <div class="form-group col-sm-6">
                                    <div class="form-line">
                                        <div class="font-12">Playlist Name</div>
                                        <input type="text" class="form-control" name="playlist_name" id="playlist_name" value="<?php echo $row['playlist_name']; ?>">
                                    </div>
                                </div>

                                <div class="form-group col-sm-4">
                                    <div class="font-12">Playlist Type *</div>
                                    <select class="form-control show-tick" name="playlist_type" id="playlist_type">
                                        <option value="m3u" <?php if ($row['playlist_type'] == 'm3u') echo 'selected="selected"'; ?>>M3U</option>
                                        <option value="xtream" <?php if ($row['playlist_type'] == 'xtream') echo 'selected="selected"'; ?>>Xtream</option>
                                        <option value="website" <?php if ($row['playlist_type'] == 'website') echo 'selected="selected"'; ?>>Website</option>
                                        <option value="file" <?php if ($row['playlist_type'] == 'file') echo 'selected="selected"'; ?>>File</option>
                                        <option value="direct" <?php if ($row['playlist_type'] == 'direct') echo 'selected="selected"'; ?>>Direct</option>
                                    </select>
                                </div>

                                <div class="form-group col-sm-4">
                                    <div class="form-line">
                                        <div class="font-12">Channels Count</div>
                                        <input type="number" class="form-control" name="channels_count" id="channels_count" min="0" value="<?php echo (int) $row['channels_count']; ?>">
                                    </div>
                                </div>

                                <div class="form-group col-sm-4">
                                    <div class="font-12">Status *</div>
                                    <select class="form-control show-tick" name="status" id="status">
                                        <option value="active" <?php if ($row['status'] == '1') echo 'selected="selected"'; ?>>Active</option>
                                        <option value="inactive" <?php if ($row['status'] == '0') echo 'selected="selected"'; ?>>Inactive</option>
                                    </select>
                                </div>

                                <div class="form-group col-sm-12">
                                    <div class="form-line">
                                        <div class="font-12">Playlist Source URL *</div>
                                        <input type="text" class="form-control" name="playlist_source" id="playlist_source" value="<?php echo htmlspecialchars($row['playlist_source']); ?>" required>
                                    </div>
                                </div>

                                <div class="form-group col-sm-12">
                                    <div class="help-info pull-left">
                                        Created: <?php echo $row['created_at']; ?> &nbsp;&nbsp; | &nbsp;&nbsp; Last Sync: <?php echo $row['updated_at']; ?>
                                    </div>
                                </div>

                                <div class="col-sm-12">
                                    <button class="button button-rounded waves-effect waves-float pull-right" type="submit" name="<?php if (validateResourceAccess($inputToken)) {
                                                                                                                                    echo 'submit';
                                                                                                                                } else {
                                                                                                                                    echo 'validate';
                                                                                                                                } ?>">UPDATE</button>
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
