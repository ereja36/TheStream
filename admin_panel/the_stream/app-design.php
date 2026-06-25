<?php include_once('includes/header.php'); ?>

<?php

$packageName = 'com.shif.shqip.android';
$columnExists = false;
$columnCheck = mysqli_query($connect, "SHOW COLUMNS FROM tbl_app_config LIKE 'design_id'");
if ($columnCheck && mysqli_num_rows($columnCheck) > 0) {
    $columnExists = true;
}

if (!$columnExists) {
    mysqli_query($connect, "ALTER TABLE tbl_app_config ADD COLUMN design_id varchar(10) NOT NULL DEFAULT 'v1' AFTER redirect_url");
    $columnExists = true;
}

$currentDesign = 'v1';
$appRow = null;
$sqlApp = "SELECT * FROM tbl_app_config WHERE package_name = '$packageName' LIMIT 1";
$resultApp = mysqli_query($connect, $sqlApp);
if ($resultApp && mysqli_num_rows($resultApp) > 0) {
    $appRow = mysqli_fetch_assoc($resultApp);
    if (!empty($appRow['design_id'])) {
        $currentDesign = strtolower($appRow['design_id']);
    }
}

if (isset($_POST['submit'])) {
    if (validateResourceAccess($inputToken)) {
        $designId = isset($_POST['design_id']) ? strtolower(clean($_POST['design_id'])) : 'v1';
        if ($designId !== 'v2' && $designId !== 'v3') {
            $designId = 'v1';
        }

        if ($appRow) {
            $data = array('design_id' => $designId);
            update('tbl_app_config', $data, "WHERE package_name = '$packageName'");
        } else {
            $data = array(
                'package_name' => $packageName,
                'status' => '1',
                'redirect_url' => 'https://play.google.com/store/apps/details?id=' . $packageName,
                'design_id' => $designId
            );
            insert('tbl_app_config', $data);
        }

        $_SESSION['msg'] = "App design switched to " . strtoupper($designId) . ". Users will see it on next app open (no APK update needed).";
        header("Location:app-design.php");
        exit;
    } else {
        systemUpdateFlag($invalidResponse);
    }
}

$designs = array(
    'v1' => array(
        'title' => 'Design V1 — Classic',
        'desc' => 'Dizajni aktual me toolbar, bottom navigation dhe listë/grid kanalesh.',
        'color' => '#AFB42B'
    ),
    'v2' => array(
        'title' => 'Design V2 — IPTV Modern',
        'desc' => 'Hero LIVE, kategori horizontale, karta kanalesh dhe nav pill si në foto.',
        'color' => '#8DB04B'
    ),
    'v3' => array(
        'title' => 'Design V3 — Premium Glass',
        'desc' => 'Gradient purple/cyan, grid 3 kolona, featured row dhe stil premium.',
        'color' => '#00E5FF'
    )
);

?>

<section class="content">

    <ol class="breadcrumb">
        <li><a href="dashboard.php">Dashboard</a></li>
        <li class="active">App Design</li>
    </ol>

    <div class="container-fluid">
        <div class="row clearfix">
            <div class="col-lg-12 col-md-12 col-sm-12 col-xs-12">
                <form method="post">
                    <div class="card corner-radius">
                        <div class="header">
                            <h2>APP DESIGN SWITCHER</h2>
                            <p style="margin:0;color:#888;">Ndrysho dizajnin për <b><?php echo $packageName; ?></b> — përdoruesit e marrin automatikisht pa update APK.</p>
                        </div>

                        <div class="body">
                            <?php if (isset($_SESSION['msg'])) { ?>
                            <div class='alert alert-success alert-dismissible corner-radius' role='alert'>
                                <button type="button" class="close" data-dismiss="alert" aria-label="Close"><span aria-hidden="true">&times;</span></button>
                                <?php echo $_SESSION['msg']; ?>
                            </div>
                            <?php unset($_SESSION['msg']); } ?>

                            <div class="row">
                                <?php foreach ($designs as $id => $meta) { ?>
                                <div class="col-lg-4 col-md-4 col-sm-12 col-xs-12">
                                    <label style="display:block;cursor:pointer;">
                                        <div class="card corner-radius" style="border:3px solid <?php echo ($currentDesign === $id) ? $meta['color'] : '#ddd'; ?>;min-height:220px;">
                                            <div class="body">
                                                <div style="display:flex;align-items:center;gap:10px;margin-bottom:12px;">
                                                    <input type="radio" name="design_id" value="<?php echo $id; ?>" <?php echo ($currentDesign === $id) ? 'checked' : ''; ?> />
                                                    <h3 style="margin:0;"><?php echo $meta['title']; ?></h3>
                                                </div>
                                                <div style="height:8px;border-radius:4px;background:<?php echo $meta['color']; ?>;margin-bottom:12px;"></div>
                                                <p><?php echo $meta['desc']; ?></p>
                                                <?php if ($currentDesign === $id) { ?>
                                                <span class="label bg-green">ACTIVE</span>
                                                <?php } ?>
                                            </div>
                                        </div>
                                    </label>
                                </div>
                                <?php } ?>
                            </div>

                            <button type="submit" name="submit" class="button button-rounded waves-effect waves-float btn-block">SAVE DESIGN</button>

                            <div class="alert alert-info corner-radius" style="margin-top:20px;">
                                <b>Si funksionon:</b> Aplikacioni lexon <code>design_id</code> nga API në çdo hapje. Përdoruesi mbyll dhe rihap app-in për të parë dizajnin e ri.
                            </div>
                        </div>
                    </div>
                </form>
            </div>
        </div>
    </div>
</section>

<?php include_once('includes/footer.php'); ?>
