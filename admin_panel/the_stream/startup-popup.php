<?php include_once('includes/header.php'); ?>

<?php

$tableExists = false;
$tableCheck = mysqli_query($connect, "SHOW TABLES LIKE 'tbl_startup_popup'");
if ($tableCheck && mysqli_num_rows($tableCheck) > 0) {
    $tableExists = true;
}

$defaults = array(
    'id' => 1,
    'title' => '',
    'message' => '',
    'popup_type' => 'text',
    'media_url' => '',
    'target_url' => '',
    'duration_seconds' => 0,
    'allow_manual_close' => 1,
    'device_id' => '',
    'status' => 0
);

if ($tableExists && isset($_POST['submit'])) {
    if (validateResourceAccess($inputToken)) {
        $data = array(
            'title' => clean($_POST['title']),
            'message' => clean($_POST['message']),
            'popup_type' => clean($_POST['popup_type']),
            'media_url' => clean($_POST['media_url']),
            'target_url' => clean($_POST['target_url']),
            'duration_seconds' => (int) $_POST['duration_seconds'],
            'allow_manual_close' => isset($_POST['allow_manual_close']) ? (int) $_POST['allow_manual_close'] : 1,
            'device_id' => clean($_POST['device_id']),
            'status' => isset($_POST['status']) ? (int) $_POST['status'] : 0
        );

        $checkExisting = mysqli_query($connect, "SELECT id FROM tbl_startup_popup WHERE id = 1 LIMIT 1");
        if ($checkExisting && mysqli_num_rows($checkExisting) > 0) {
            update('tbl_startup_popup', $data, "WHERE id = '1'");
        } else {
            $data['id'] = 1;
            insert('tbl_startup_popup', $data);
        }
        $_SESSION['msg'] = "Startup popup settings updated successfully.";
        header("Location:startup-popup.php");
        exit;
    } else {
        systemUpdateFlag($invalidResponse);
    }
}

$popup = $defaults;
if ($tableExists) {
    $result = mysqli_query($connect, "SELECT * FROM tbl_startup_popup WHERE id = 1 LIMIT 1");
    if ($result && mysqli_num_rows($result) > 0) {
        $popup = mysqli_fetch_assoc($result);
    }
}

?>

<section class="content">

    <ol class="breadcrumb">
        <li><a href="dashboard.php">Dashboard</a></li>
        <li class="active">Startup Popup</li>
    </ol>

    <div class="container-fluid">
        <div class="row clearfix">
            <div class="col-lg-12 col-md-12 col-sm-12 col-xs-12">
                <div class="card corner-radius">
                    <div class="header">
                        <h2>STARTUP POPUP CONFIGURATION</h2>
                    </div>
                    <div class="body">
                        <?php if (isset($_SESSION['msg'])) { ?>
                            <div class='alert alert-info alert-dismissible corner-radius bottom-offset' role='alert'>
                                <button type="button" class="close" data-dismiss="alert" aria-label="Close"><span aria-hidden="true">&times;</span>&nbsp;&nbsp;</button>
                                <?php echo $_SESSION['msg']; ?>
                            </div>
                            <?php unset($_SESSION['msg']);
                        } ?>

                        <?php if (!$tableExists) { ?>
                            <div class='alert alert-warning'>Table <b>tbl_startup_popup</b> not found. Import latest SQL migration and refresh.</div>
                        <?php } ?>

                        <form method="post">
                            <div class="row clearfix">
                                <div class="col-sm-6">
                                    <div class="form-group form-float">
                                        <div class="font-12"><b>Status</b></div>
                                        <select name="status" class="form-control">
                                            <option value="1" <?php if ((int) $popup['status'] === 1) echo "selected"; ?>>Active</option>
                                            <option value="0" <?php if ((int) $popup['status'] === 0) echo "selected"; ?>>Inactive</option>
                                        </select>
                                    </div>
                                </div>
                                <div class="col-sm-6">
                                    <div class="form-group form-float">
                                        <div class="font-12"><b>Popup Type</b></div>
                                        <select name="popup_type" class="form-control">
                                            <option value="text" <?php if ($popup['popup_type'] == 'text') echo "selected"; ?>>Text</option>
                                            <option value="image" <?php if ($popup['popup_type'] == 'image') echo "selected"; ?>>Image</option>
                                            <option value="video" <?php if ($popup['popup_type'] == 'video') echo "selected"; ?>>Video URL</option>
                                            <option value="link" <?php if ($popup['popup_type'] == 'link') echo "selected"; ?>>Link Only</option>
                                        </select>
                                    </div>
                                </div>
                            </div>

                            <div class="form-group form-float">
                                <div class="font-12"><b>Title</b></div>
                                <div class="form-line">
                                    <input type="text" class="form-control" name="title" value="<?php echo htmlspecialchars($popup['title']); ?>" maxlength="255">
                                </div>
                            </div>

                            <div class="form-group form-float">
                                <div class="font-12"><b>Message</b></div>
                                <div class="form-line">
                                    <textarea rows="4" class="form-control no-resize" name="message"><?php echo htmlspecialchars($popup['message']); ?></textarea>
                                </div>
                            </div>

                            <div class="form-group form-float">
                                <div class="font-12"><b>Media URL (image/video)</b></div>
                                <div class="form-line">
                                    <input type="text" class="form-control" name="media_url" value="<?php echo htmlspecialchars($popup['media_url']); ?>">
                                </div>
                            </div>

                            <div class="form-group form-float">
                                <div class="font-12"><b>Target Link URL</b></div>
                                <div class="form-line">
                                    <input type="text" class="form-control" name="target_url" value="<?php echo htmlspecialchars($popup['target_url']); ?>">
                                </div>
                            </div>

                            <div class="row clearfix">
                                <div class="col-sm-4">
                                    <div class="form-group form-float">
                                        <div class="font-12"><b>Duration (seconds, 0 = no timer)</b></div>
                                        <div class="form-line">
                                            <input type="number" class="form-control" min="0" name="duration_seconds" value="<?php echo (int) $popup['duration_seconds']; ?>">
                                        </div>
                                    </div>
                                </div>
                                <div class="col-sm-4">
                                    <div class="form-group form-float">
                                        <div class="font-12"><b>Manual Close Button</b></div>
                                        <select name="allow_manual_close" class="form-control">
                                            <option value="1" <?php if ((int) $popup['allow_manual_close'] === 1) echo "selected"; ?>>Enabled</option>
                                            <option value="0" <?php if ((int) $popup['allow_manual_close'] === 0) echo "selected"; ?>>Disabled</option>
                                        </select>
                                    </div>
                                </div>
                                <div class="col-sm-4">
                                    <div class="form-group form-float">
                                        <div class="font-12"><b>Device ID Scope (optional)</b></div>
                                        <div class="form-line">
                                            <input type="text" class="form-control" name="device_id" placeholder="Leave empty for all devices" value="<?php echo htmlspecialchars($popup['device_id']); ?>">
                                        </div>
                                    </div>
                                </div>
                            </div>

                            <button type="submit" name="<?php if (validateResourceAccess($inputToken)) {
                                                            echo 'submit';
                                                        } else {
                                                            echo 'validate';
                                                        } ?>" class="btn bg-blue waves-effect">SAVE CHANGES</button>
                        </form>
                    </div>
                </div>
            </div>
        </div>
    </div>
</section>

<?php include_once('includes/footer.php'); ?>
