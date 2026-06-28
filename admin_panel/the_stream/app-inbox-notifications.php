<?php include_once('includes/header.php'); ?>

<?php

$tableExists = false;
$tableCheck = mysqli_query($connect, "SHOW TABLES LIKE 'tbl_app_inbox_notifications'");
if ($tableCheck && mysqli_num_rows($tableCheck) > 0) {
    $tableExists = true;
}

if ($tableExists && isset($_POST['send_notification'])) {
    if (validateResourceAccess($inputToken)) {
        $data = array(
            'title' => clean($_POST['title']),
            'message' => clean($_POST['message']),
            'target_url' => clean($_POST['target_url']),
            'status' => 1
        );
        insert('tbl_app_inbox_notifications', $data);
        $_SESSION['msg'] = "In-app notification sent. Devices will receive it on next app open.";
        header("Location:app-inbox-notifications.php");
        exit;
    } else {
        systemUpdateFlag($invalidResponse);
    }
}

if ($tableExists && isset($_GET['toggle_id'])) {
    $toggleId = (int) clean($_GET['toggle_id']);
    if ($toggleId > 0) {
        $rowResult = mysqli_query($connect, "SELECT status FROM tbl_app_inbox_notifications WHERE id = '$toggleId' LIMIT 1");
        if ($rowResult && mysqli_num_rows($rowResult) > 0) {
            $row = mysqli_fetch_assoc($rowResult);
            $newStatus = ((int) $row['status'] === 1) ? 0 : 1;
            update('tbl_app_inbox_notifications', array('status' => $newStatus), "WHERE id = '$toggleId'");
            $_SESSION['msg'] = "Notification status updated.";
        }
    }
    header("Location:app-inbox-notifications.php");
    exit;
}

if ($tableExists && isset($_GET['delete_id'])) {
    $deleteId = (int) clean($_GET['delete_id']);
    if ($deleteId > 0) {
        delete('tbl_app_inbox_notifications', "id = '$deleteId'");
        $deliveryCheck = mysqli_query($connect, "SHOW TABLES LIKE 'tbl_app_inbox_delivery'");
        if ($deliveryCheck && mysqli_num_rows($deliveryCheck) > 0) {
            delete('tbl_app_inbox_delivery', "notification_id = '$deleteId'");
        }
        $_SESSION['msg'] = "Notification deleted.";
    }
    header("Location:app-inbox-notifications.php");
    exit;
}

$notifications = array();
if ($tableExists) {
    $sql = "SELECT n.*,
            (SELECT COUNT(*) FROM tbl_app_inbox_delivery d WHERE d.notification_id = n.id) AS delivered_count,
            (SELECT COUNT(*) FROM tbl_app_inbox_delivery d WHERE d.notification_id = n.id AND d.opened_at IS NOT NULL) AS opened_count
            FROM tbl_app_inbox_notifications n
            ORDER BY n.id DESC";
    $result = mysqli_query($connect, $sql);
    if ($result) {
        while ($row = mysqli_fetch_assoc($result)) {
            $notifications[] = $row;
        }
    }
}

?>

<section class="content">

    <ol class="breadcrumb">
        <li><a href="dashboard.php">Dashboard</a></li>
        <li class="active">In-App Notifications</li>
    </ol>

    <div class="container-fluid">
        <div class="row clearfix">
            <div class="col-lg-12 col-md-12 col-sm-12 col-xs-12">
                <div class="card corner-radius">
                    <div class="header">
                        <h2>IN-APP NOTIFICATIONS (NO FIREBASE)</h2>
                    </div>
                    <div class="body">
                        <?php if (isset($_SESSION['msg'])) { ?>
                            <div class='alert alert-info alert-dismissible corner-radius bottom-offset' role='alert'>
                                <button type="button" class="close" data-dismiss="alert" aria-label="Close"><span aria-hidden="true">&times;</span>&nbsp;&nbsp;</button>
                                <?php echo $_SESSION['msg']; ?>
                            </div>
                        <?php unset($_SESSION['msg']); } ?>

                        <?php if (!$tableExists) { ?>
                            <div class="alert alert-warning corner-radius">
                                Run database migration <code>009_play_store_panel_features.sql</code> first.
                            </div>
                        <?php } else { ?>

                        <p>Notifications are delivered when users open the app. Delivery count shows how many devices received the notification.</p>

                        <form method="post" class="bottom-offset">
                            <div class="row clearfix">
                                <div class="col-sm-12">
                                    <div class="form-group">
                                        <div class="form-line">
                                            <div class="font-12"><b>Title</b></div>
                                            <input type="text" class="form-control" name="title" required>
                                        </div>
                                    </div>
                                </div>
                                <div class="col-sm-12">
                                    <div class="form-group">
                                        <div class="form-line">
                                            <div class="font-12"><b>Message</b></div>
                                            <textarea class="form-control" name="message" rows="3" required></textarea>
                                        </div>
                                    </div>
                                </div>
                                <div class="col-sm-12">
                                    <div class="form-group">
                                        <div class="form-line">
                                            <div class="font-12"><b>Target URL (optional)</b></div>
                                            <input type="text" class="form-control" name="target_url" placeholder="https://shifshqip.com">
                                        </div>
                                    </div>
                                </div>
                                <div class="col-sm-12">
                                    <button type="submit" name="<?php if (validateResourceAccess($inputToken)) { echo 'send_notification'; } else { echo 'validate'; } ?>" class="button button-rounded waves-effect waves-float">SEND TO ALL DEVICES</button>
                                </div>
                            </div>
                        </form>

                        <div class="table-responsive">
                            <table class="table table-hover table-striped table-offset">
                                <thead>
                                    <tr>
                                        <th>ID</th>
                                        <th>Title</th>
                                        <th>Message</th>
                                        <th>Delivered</th>
                                        <th>Opened</th>
                                        <th>Status</th>
                                        <th>Created</th>
                                        <th width="12%">Action</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <?php if (count($notifications) === 0) { ?>
                                        <tr><td colspan="8" align="center">No notifications sent yet.</td></tr>
                                    <?php } else { ?>
                                        <?php foreach ($notifications as $item) { ?>
                                            <tr>
                                                <td><?php echo (int) $item['id']; ?></td>
                                                <td><?php echo htmlspecialchars($item['title']); ?></td>
                                                <td><?php echo htmlspecialchars($item['message']); ?></td>
                                                <td><span class="label label-rounded bg-green"><?php echo (int) $item['delivered_count']; ?> devices</span></td>
                                                <td><?php echo (int) $item['opened_count']; ?></td>
                                                <td><?php echo ((int) $item['status'] === 1) ? 'Active' : 'Disabled'; ?></td>
                                                <td><?php echo htmlspecialchars($item['created_at']); ?></td>
                                                <td>
                                                    <a href="app-inbox-notifications.php?toggle_id=<?php echo (int) $item['id']; ?>" class="btn bg-blue btn-circle waves-effect waves-circle waves-float"><i class="material-icons">swap_horiz</i></a>
                                                    <a href="app-inbox-notifications.php?delete_id=<?php echo (int) $item['id']; ?>" class="btn bg-red btn-circle waves-effect waves-circle waves-float" onclick="return confirm('Delete this notification?');"><i class="material-icons">delete</i></a>
                                                </td>
                                            </tr>
                                        <?php } ?>
                                    <?php } ?>
                                </tbody>
                            </table>
                        </div>

                        <?php } ?>
                    </div>
                </div>
            </div>
        </div>
    </div>

</section>

<?php include_once('includes/footer.php'); ?>
