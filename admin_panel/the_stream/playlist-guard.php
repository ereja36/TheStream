<?php
header('Location: playlist-portal.php');
exit;
include_once('includes/header.php'); ?>

<?php

$tableExists = false;
$tableCheck = mysqli_query($connect, "SHOW TABLES LIKE 'tbl_playlist_guard'");
if ($tableCheck && mysqli_num_rows($tableCheck) > 0) {
    $tableExists = true;
}

if ($tableExists) {
    $streamColumnCheck = mysqli_query($connect, "SHOW COLUMNS FROM tbl_playlist_guard LIKE 'stream_url_pattern'");
    if (!$streamColumnCheck || mysqli_num_rows($streamColumnCheck) === 0) {
        mysqli_query($connect, "ALTER TABLE tbl_playlist_guard ADD COLUMN stream_url_pattern varchar(500) NOT NULL DEFAULT '' AFTER url_pattern");
    }
}

if (!$tableExists) {
    mysqli_query($connect, "CREATE TABLE IF NOT EXISTS `tbl_playlist_guard` (
      `id` int(11) NOT NULL AUTO_INCREMENT,
      `rule_name` varchar(255) NOT NULL DEFAULT '',
      `url_pattern` varchar(500) NOT NULL,
      `stream_url_pattern` varchar(500) NOT NULL DEFAULT '',
      `referer` varchar(500) NOT NULL DEFAULT '',
      `origin` varchar(500) NOT NULL DEFAULT '',
      `user_agent` varchar(500) NOT NULL DEFAULT '',
      `cookie` text NOT NULL,
      `extra_headers` text NOT NULL,
      `status` tinyint(1) NOT NULL DEFAULT 1,
      `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
      `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
      PRIMARY KEY (`id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
    $tableExists = true;
}

if ($tableExists && isset($_POST['submit'])) {
    if (validateResourceAccess($inputToken)) {
        $data = array(
            'rule_name' => clean($_POST['rule_name']),
            'url_pattern' => clean($_POST['url_pattern']),
            'stream_url_pattern' => clean($_POST['stream_url_pattern']),
            'referer' => clean($_POST['referer']),
            'origin' => clean($_POST['origin']),
            'user_agent' => clean($_POST['user_agent']),
            'cookie' => clean($_POST['cookie']),
            'extra_headers' => clean($_POST['extra_headers']),
            'status' => isset($_POST['status']) ? 1 : 0
        );

        if (!empty($_POST['id'])) {
            update('tbl_playlist_guard', $data, "WHERE id = '" . clean($_POST['id']) . "'");
            $_SESSION['msg'] = 'Playlist guard rule updated.';
        } else {
            insert('tbl_playlist_guard', $data);
            $_SESSION['msg'] = 'Playlist guard rule added.';
        }
        header('Location:playlist-guard.php');
        exit;
    } else {
        systemUpdateFlag($invalidResponse);
    }
}

if ($tableExists && isset($_GET['delete'])) {
    if (validateResourceAccess($inputToken)) {
        $id = clean($_GET['delete']);
        mysqli_query($connect, "DELETE FROM tbl_playlist_guard WHERE id = '$id'");
        $_SESSION['msg'] = 'Rule deleted.';
        header('Location:playlist-guard.php');
        exit;
    }
}

$editRow = null;
if ($tableExists && isset($_GET['edit'])) {
    $id = clean($_GET['edit']);
    $editResult = mysqli_query($connect, "SELECT * FROM tbl_playlist_guard WHERE id = '$id' LIMIT 1");
    if ($editResult) {
        $editRow = mysqli_fetch_assoc($editResult);
    }
}

$rules = array();
if ($tableExists) {
    $rulesResult = mysqli_query($connect, "SELECT * FROM tbl_playlist_guard ORDER BY id DESC");
    if ($rulesResult) {
        while ($row = mysqli_fetch_assoc($rulesResult)) {
            $rules[] = $row;
        }
    }
}

?>

<section class="content">
    <ol class="breadcrumb">
        <li><a href="dashboard.php">Dashboard</a></li>
        <li class="active">Playlist Guard</li>
    </ol>

    <div class="container-fluid">
        <div class="row clearfix">
            <div class="col-lg-5 col-md-5 col-sm-12 col-xs-12">
                <div class="card corner-radius">
                    <div class="header"><h2><?php echo $editRow ? 'EDIT RULE' : 'ADD RULE'; ?></h2></div>
                    <div class="body">
                        <?php if (isset($_SESSION['msg'])) { ?>
                        <div class='alert alert-success alert-dismissible corner-radius'><?php echo $_SESSION['msg']; ?></div>
                        <?php unset($_SESSION['msg']); } ?>

                        <form method="post">
                            <input type="hidden" name="id" value="<?php echo $editRow ? $editRow['id'] : ''; ?>">

                            <div class="form-group">
                                <label>Rule name</label>
                                <input type="text" class="form-control" name="rule_name" value="<?php echo $editRow ? $editRow['rule_name'] : 'Lista Time'; ?>" required>
                            </div>

                            <div class="form-group">
                                <label>URL pattern (pjesa unike e linkut të playlistës)</label>
                                <input type="text" class="form-control" name="url_pattern" value="<?php echo $editRow ? $editRow['url_pattern'] : 'herokuapp.com/k1/playlist'; ?>" required>
                                <small>P.sh. <code>herokuapp.com/k1/playlist</code> — pa http/https. Kur përputhet playlista, header-at aplikohen edhe për kanalet.</small>
                            </div>

                            <div class="form-group">
                                <label>Stream URL pattern (opsionale)</label>
                                <input type="text" class="form-control" name="stream_url_pattern" value="<?php echo $editRow ? (isset($editRow['stream_url_pattern']) ? $editRow['stream_url_pattern'] : '') : ''; ?>">
                                <small>P.sh. <code>cdn.example.com/live</code> — vetëm nëse stream-et janë në domain tjetër nga playlista.</small>
                            </div>

                            <div class="form-group">
                                <label>Referer</label>
                                <input type="text" class="form-control" name="referer" value="<?php echo $editRow ? $editRow['referer'] : ''; ?>">
                            </div>

                            <div class="form-group">
                                <label>Origin</label>
                                <input type="text" class="form-control" name="origin" value="<?php echo $editRow ? $editRow['origin'] : ''; ?>">
                            </div>

                            <div class="form-group">
                                <label>User-Agent</label>
                                <input type="text" class="form-control" name="user_agent" value="<?php echo $editRow ? $editRow['user_agent'] : ''; ?>">
                            </div>

                            <div class="form-group">
                                <label>Cookie</label>
                                <textarea class="form-control" name="cookie" rows="2"><?php echo $editRow ? $editRow['cookie'] : ''; ?></textarea>
                            </div>

                            <div class="form-group">
                                <label>Extra headers (JSON)</label>
                                <textarea class="form-control" name="extra_headers" rows="2" placeholder='{"X-Custom":"value"}'><?php echo $editRow ? $editRow['extra_headers'] : ''; ?></textarea>
                            </div>

                            <div class="form-group">
                                <input type="checkbox" name="status" id="status" <?php echo (!$editRow || (int)$editRow['status'] === 1) ? 'checked' : ''; ?>>
                                <label for="status">Active</label>
                            </div>

                            <button type="submit" name="submit" class="button button-rounded waves-effect">SAVE</button>
                            <?php if ($editRow) { ?><a href="playlist-guard.php" class="btn btn-default">Cancel</a><?php } ?>
                        </form>

                        <div class="alert alert-info corner-radius" style="margin-top:16px;">
                            <b>Si funksionon:</b> Kur playlista përputhet me pattern, header-at aplikohen edhe për <b>të gjitha kanalet</b> (stream playback) në app. Listat personale pa rregull hapen normalisht.
                        </div>
                    </div>
                </div>
            </div>

            <div class="col-lg-7 col-md-7 col-sm-12 col-xs-12">
                <div class="card corner-radius">
                    <div class="header"><h2>ACTIVE RULES</h2></div>
                    <div class="body table-responsive">
                        <table class="table">
                            <thead>
                                <tr>
                                    <th>Name</th>
                                    <th>Pattern</th>
                                    <th>Status</th>
                                    <th>Action</th>
                                </tr>
                            </thead>
                            <tbody>
                            <?php if (empty($rules)) { ?>
                                <tr><td colspan="4">No rules yet.</td></tr>
                            <?php } else { foreach ($rules as $rule) { ?>
                                <tr>
                                    <td><?php echo $rule['rule_name']; ?></td>
                                    <td><code><?php echo $rule['url_pattern']; ?></code></td>
                                    <td><?php echo ((int)$rule['status'] === 1) ? '<span class="label bg-green">ON</span>' : '<span class="label bg-red">OFF</span>'; ?></td>
                                    <td>
                                        <a href="playlist-guard.php?edit=<?php echo $rule['id']; ?>">Edit</a> |
                                        <a href="playlist-guard.php?delete=<?php echo $rule['id']; ?>" onclick="return confirm('Delete this rule?')">Delete</a>
                                    </td>
                                </tr>
                            <?php }} ?>
                            </tbody>
                        </table>
                    </div>
                </div>
            </div>
        </div>
    </div>
</section>

<?php include_once('includes/footer.php'); ?>
