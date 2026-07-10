<?php ob_start(); ?>
<?php include_once('includes/header.php'); ?>

<?php

if (isset($_GET['id'])) {
    $ID = (int) clean($_GET['id']);
} else {
    $ID = 0;
}

if (validateResourceAccess($inputToken)) {
    $sql_delete = "DELETE FROM tbl_device_playlist WHERE id = '$ID'";
    $delete = $connect->query($sql_delete);

    if ($delete) {
        $_SESSION['msg'] = "Device playlist deleted successfully...";
        header("Location: device-playlist.php");
        exit;
    } else {
        $_SESSION['msg'] = "Failed to delete device playlist...";
        header("Location: device-playlist.php");
        exit;
    }
} else {
    systemUpdateFlag($invalidResponse);
}

?>

<?php include_once('includes/footer.php'); ?>
