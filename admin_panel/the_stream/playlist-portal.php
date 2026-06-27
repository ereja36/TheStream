<?php include_once('includes/header.php'); ?>

<?php

$tableCheck = mysqli_query($connect, "SHOW TABLES LIKE 'tbl_playlist_portal'");
if (!$tableCheck || mysqli_num_rows($tableCheck) === 0) {
    mysqli_query($connect, "CREATE TABLE IF NOT EXISTS `tbl_playlist_portal` (
      `id` int(11) NOT NULL AUTO_INCREMENT,
      `portal_slug` varchar(100) NOT NULL DEFAULT 'shifshqip',
      `portal_name` varchar(255) NOT NULL DEFAULT 'Shif Shqip',
      `source_url` varchar(1000) NOT NULL DEFAULT '',
      `source_type` varchar(20) NOT NULL DEFAULT 'auto',
      `access_key` varchar(128) NOT NULL DEFAULT '',
      `required_user_agent` varchar(255) NOT NULL DEFAULT 'ShifShqip',
      `required_package` varchar(255) NOT NULL DEFAULT 'com.shif.shqip.android',
      `outbound_user_agent` varchar(500) NOT NULL DEFAULT '',
      `outbound_referer` varchar(500) NOT NULL DEFAULT '',
      `outbound_origin` varchar(500) NOT NULL DEFAULT '',
      `outbound_cookie` text NOT NULL,
      `outbound_extra_headers` text NOT NULL,
      `status` tinyint(1) NOT NULL DEFAULT 1,
      `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
      `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
      PRIMARY KEY (`id`),
      UNIQUE KEY `uniq_portal_slug` (`portal_slug`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
}

function generatePortalAccessKey() {
    return bin2hex(random_bytes(16));
}

function cleanupPortalGuardRules($connect) {
    mysqli_query($connect, "DELETE FROM tbl_playlist_guard WHERE rule_name LIKE 'Playlist Portal%' OR url_pattern LIKE '%playlist.shifshqip.com%'");
}

$portal = null;
$portalResult = mysqli_query($connect, "SELECT * FROM tbl_playlist_portal ORDER BY id ASC LIMIT 1");
if ($portalResult && mysqli_num_rows($portalResult) > 0) {
    $portal = mysqli_fetch_assoc($portalResult);
}

if ($portal === null) {
    $defaultKey = generatePortalAccessKey();
    insert('tbl_playlist_portal', array(
        'portal_slug' => 'shifshqip',
        'portal_name' => 'Shif Shqip',
        'source_url' => '',
        'source_type' => 'auto',
        'access_key' => $defaultKey,
        'required_user_agent' => 'ShifShqip',
        'required_package' => 'com.shif.shqip.android',
        'status' => 0
    ));
    $portalResult = mysqli_query($connect, "SELECT * FROM tbl_playlist_portal ORDER BY id ASC LIMIT 1");
    $portal = mysqli_fetch_assoc($portalResult);
}

if (isset($_POST['submit'])) {
    if (validateResourceAccess($inputToken)) {
        $accessKey = clean($_POST['access_key']);
        if ($accessKey === '') {
            $accessKey = generatePortalAccessKey();
        }
        $data = array(
            'portal_slug' => clean($_POST['portal_slug']),
            'portal_name' => clean($_POST['portal_name']),
            'source_url' => clean($_POST['source_url']),
            'source_type' => clean($_POST['source_type']),
            'access_key' => $accessKey,
            'required_user_agent' => clean($_POST['required_user_agent']),
            'required_package' => clean($_POST['required_package']),
            'outbound_user_agent' => clean($_POST['outbound_user_agent']),
            'outbound_referer' => clean($_POST['outbound_referer']),
            'outbound_origin' => clean($_POST['outbound_origin']),
            'outbound_cookie' => clean($_POST['outbound_cookie']),
            'outbound_extra_headers' => clean($_POST['outbound_extra_headers']),
            'status' => isset($_POST['status']) ? 1 : 0
        );
        update('tbl_playlist_portal', $data, "WHERE id = '" . clean($portal['id']) . "'");
        cleanupPortalGuardRules($connect);

        $settingsData = array(
            'global_playlist_url' => 'https://playlist.shifshqip.com/'
        );
        if (isset($_POST['enable_global']) && (int) $_POST['enable_global'] === 1) {
            $settingsData['global_playlist_enabled'] = 1;
            $settingsData['global_playlist_name'] = $data['portal_name'];
        } else {
            $settingsData['global_playlist_enabled'] = 0;
        }
        update('tbl_settings', $settingsData, "WHERE id = '1'");

        $_SESSION['msg'] = 'Playlist portal updated. App users only see the public URL.';
        header('Location:playlist-portal.php');
        exit;
    } else {
        systemUpdateFlag($invalidResponse);
    }
}

if (isset($_POST['regenerate_key']) && validateResourceAccess($inputToken)) {
    $newKey = generatePortalAccessKey();
    update('tbl_playlist_portal', array('access_key' => $newKey), "WHERE id = '" . clean($portal['id']) . "'");
    $_SESSION['msg'] = 'Access key regenerated. Update app on next open.';
    header('Location:playlist-portal.php');
    exit;
}

$portalResult = mysqli_query($connect, "SELECT * FROM tbl_playlist_portal ORDER BY id ASC LIMIT 1");
$portal = mysqli_fetch_assoc($portalResult);
$publicUrl = 'https://playlist.shifshqip.com/';

$settings = array('global_playlist_enabled' => 0);
$settingsResult = mysqli_query($connect, "SELECT global_playlist_enabled FROM tbl_settings WHERE id = '1' LIMIT 1");
if ($settingsResult) {
    $settings = mysqli_fetch_assoc($settingsResult);
}

?>

<section class="content">
    <ol class="breadcrumb">
        <li><a href="dashboard.php">Dashboard</a></li>
        <li class="active">Playlist Portal</li>
    </ol>

    <div class="container-fluid">
        <div class="row clearfix">
            <div class="col-lg-12 col-md-12 col-sm-12 col-xs-12">
                <form method="post">
                    <div class="card corner-radius">
                        <div class="header">
                            <h2>PLAYLIST.SHIFSHQIP.COM</h2>
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

                            <p>URL që përdorin përdoruesit në app (burimi real mbetet i fshehur):</p>
                            <p><code><?php echo htmlspecialchars($publicUrl); ?></code></p>
                            <p>Kur ndryshon source në panel, përdoruesit vazhdojnë me të njëjtën URL — app-i merr automatikisht kanalet e reja.</p>

                            <div class="form-group">
                                <input type="checkbox" name="status" id="status" <?php echo ((int) $portal['status'] === 1) ? 'checked' : ''; ?>>
                                <label for="status">Portal enabled</label>
                            </div>

                            <div class="form-group">
                                <input type="checkbox" name="enable_global" id="enable_global" value="1" <?php echo ((int) $settings['global_playlist_enabled'] === 1) ? 'checked' : ''; ?>>
                                <label for="enable_global">Push this portal playlist to all app users (Global Playlist)</label>
                            </div>

                            <input type="hidden" name="portal_slug" value="<?php echo htmlspecialchars($portal['portal_slug']); ?>">

                            <div class="form-group form-line">
                                <label><b>Playlist name (shown in app)</b></label>
                                <input type="text" class="form-control" name="portal_name" value="<?php echo htmlspecialchars($portal['portal_name']); ?>" required>
                            </div>

                            <div class="form-group form-line">
                                <label><b>Private source URL (hidden from users)</b></label>
                                <input type="text" class="form-control" name="source_url" value="<?php echo htmlspecialchars($portal['source_url']); ?>" placeholder="https://example.com/secret-playlist.m3u">
                            </div>

                            <div class="form-group form-line">
                                <label><b>Source type</b></label>
                                <select class="form-control" name="source_type">
                                    <option value="auto" <?php echo $portal['source_type'] === 'auto' ? 'selected' : ''; ?>>Auto detect (M3U / JSON)</option>
                                    <option value="m3u" <?php echo $portal['source_type'] === 'm3u' ? 'selected' : ''; ?>>M3U</option>
                                    <option value="json" <?php echo $portal['source_type'] === 'json' ? 'selected' : ''; ?>>JSON collect</option>
                                </select>
                            </div>

                            <h4>App access (required from Shif Shqip)</h4>
                            <div class="form-group form-line">
                                <label><b>Access key (X-Shif-Portal-Key)</b></label>
                                <input type="text" class="form-control" name="access_key" value="<?php echo htmlspecialchars($portal['access_key']); ?>" readonly>
                                <button type="submit" name="regenerate_key" class="btn bg-orange waves-effect m-t-10">Regenerate access key</button>
                            </div>
                            <div class="form-group form-line">
                                <label><b>Required User-Agent contains</b></label>
                                <input type="text" class="form-control" name="required_user_agent" value="<?php echo htmlspecialchars($portal['required_user_agent']); ?>">
                            </div>
                            <div class="form-group form-line">
                                <label><b>Required Android package (X-Requested-With)</b></label>
                                <input type="text" class="form-control" name="required_package" value="<?php echo htmlspecialchars($portal['required_package']); ?>">
                            </div>

                            <h4>Parametrat për source private (serveri i përdor kur merr playlistën)</h4>
                            <p class="help-info">Këto header-a përdoren vetëm nga playlist.shifshqip.com për të lexuar burimin tënd private. Përdoruesit nuk i shohin kurrë.</p>
                            <div class="form-group form-line">
                                <label><b>User-Agent</b></label>
                                <input type="text" class="form-control" name="outbound_user_agent" value="<?php echo htmlspecialchars($portal['outbound_user_agent']); ?>">
                            </div>
                            <div class="form-group form-line">
                                <label><b>Referer</b></label>
                                <input type="text" class="form-control" name="outbound_referer" value="<?php echo htmlspecialchars($portal['outbound_referer']); ?>">
                            </div>
                            <div class="form-group form-line">
                                <label><b>Origin</b></label>
                                <input type="text" class="form-control" name="outbound_origin" value="<?php echo htmlspecialchars($portal['outbound_origin']); ?>">
                            </div>
                            <div class="form-group form-line">
                                <label><b>Cookie</b></label>
                                <textarea class="form-control" name="outbound_cookie" rows="2"><?php echo htmlspecialchars($portal['outbound_cookie']); ?></textarea>
                            </div>
                            <div class="form-group form-line">
                                <label><b>Extra headers (JSON)</b></label>
                                <textarea class="form-control" name="outbound_extra_headers" rows="3" placeholder='{"X-Custom":"value"}'><?php echo htmlspecialchars($portal['outbound_extra_headers']); ?></textarea>
                            </div>
                        </div>
                    </div>
                </form>
            </div>
        </div>
    </div>
</section>

<?php include_once('includes/footer.php'); ?>
