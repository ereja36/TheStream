<?php 

if (isset($_GET['get_php_info'])) {
	phpinfo();
} else {
	header('location: ../dashboard.php');
    exit;
}

?>