<?php include_once('includes/header.php'); ?>

<?php

$sql = "SELECT * FROM tbl_settings WHERE id = '1' LIMIT 1";
$result = $connect->query($sql);
$settings = $result ? $result->fetch_assoc() : array();

$defaults = array(
    'global_playlist_enabled' => 0,
    'global_playlist_url' => '',
    'global_playlist_name' => 'Shif Shqip'
);

foreach ($defaults as $key => $value) {
    if (!isset($settings[$key])) {
        $settings[$key] = $value;
    }
}

if (isset($_POST['submit'])) {
    if (validateResourceAccess($inputToken)) {
        $data = array(
            'global_playlist_enabled' => isset($_POST['global_playlist_enabled']) ? (int) $_POST['global_playlist_enabled'] : 0,
            'global_playlist_url' => clean($_POST['global_playlist_url']),
            'global_playlist_name' => clean($_POST['global_playlist_name'])
        );
        update('tbl_settings', $data, "WHERE id = '1'");
        $_SESSION['msg'] = "Global playlist settings updated.";
        header("Location:global-playlist.php");
        exit;
    } else {
        systemUpdateFlag($invalidResponse);
    }
}

?>

<section class="content">

    <ol class="breadcrumb">
        <li><a href="dashboard.php">Dashboard</a></li>
        <li class="active">Global Playlist</li>
    </ol>

    <div class="container-fluid">
        <div class="row clearfix">
            <div class="col-lg-12 col-md-12 col-sm-12 col-xs-12">
                <form method="post">
                    <div class="card corner-radius">
                        <div class="header">
                            <h2>GLOBAL PLAYLIST FOR ALL USERS</h2>
                            <div class="header-dropdown m-r--5">
                                <button type="submit" name="<?php if (validateResourceAccess($inputToken)) { echo 'submit'; } else { echo 'validate'; } ?>" class="button button-rounded btn-offset waves-effect waves-float">SAVE</button>
                            </div>
                        </div>
                        <div class="body">
                            <?php if (isset($_SESSION['msg'])) { ?>
                                <div class='alert alert-info alert-dismissible corner-radius bottom-offset' role='alert'>
                                    <button type="button" class="close" data-dismiss="alert" aria-label="Close"><span aria-hidden="true">&times;</span>&nbsp;&nbsp;</button>
                                    <?php echo $_SESSION['msg']; ?>
                                </div>
                            <?php unset($_SESSION['msg']); } ?>

                            <p>When enabled, every device receives this playlist on next app open. Users will not see the playlist URL in the app. When disabled, each user gets back their own saved playlist.</p>

                            <div class="form-group">
                                <div class="font-12"><b>Status</b></div>
                                <select class="form-control show-tick" name="global_playlist_enabled">
                                    <option value="1" <?php echo ((int) $settings['global_playlist_enabled'] === 1) ? 'selected' : ''; ?>>Enabled for all users</option>
                                    <option value="0" <?php echo ((int) $settings['global_playlist_enabled'] !== 1) ? 'selected' : ''; ?>>Disabled (users keep their own playlist)</option>
                                </select>
                            </div>

                            <div class="form-group">
                                <div class="form-line">
                                    <div class="font-12"><b>Playlist Name (shown in app)</b></div>
                                    <input type="text" class="form-control" name="global_playlist_name" value="<?php echo htmlspecialchars($settings['global_playlist_name']); ?>" required>
                                </div>
                            </div>

                            <div class="form-group">
                                <div class="form-line">
                                    <div class="font-12"><b>Playlist URL</b></div>
                                    <input type="text" class="form-control" name="global_playlist_url" value="<?php echo htmlspecialchars($settings['global_playlist_url']); ?>" placeholder="https://shifshqip.com/k1/app-measurement/collect/shifshqip">
                                </div>
                                <div class="help-info pull-left">Supports M3U, Xtream, and JSON collect URLs.</div>
                            </div>
                        </div>
                    </div>
                </form>
            </div>
        </div>
    </div>

</section>

<?php include_once('includes/footer.php'); ?>
