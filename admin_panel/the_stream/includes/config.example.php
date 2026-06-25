<?php

    // Kopjoje këtë skedar si config.php dhe plotëso vlerat e tua.
    // cp config.example.php config.php

    $host       = 'localhost';
    $user       = 'shif_user';          // përdoruesi i databazës
    $pass       = 'FJALEKALIMI_YT';     // fjalëkalimi i databazës
    $database   = 'shif_shqip_db';

    $connect = new mysqli($host, $user, $pass, $database);

    if (mysqli_connect_errno()) {
        die('Gabim lidhje me databazën: ' . mysqli_connect_error());
    } else {
        $connect->set_charset('utf8mb4');
    }

    $ENABLE_RTL_MODE = 'false';

?>
