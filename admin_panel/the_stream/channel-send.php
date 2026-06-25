<?php include_once('includes/header.php'); ?>

<?php

	if (isset($_GET['id'])) {
		$ID = clean($_GET['id']);
	} else {
		$ID = clean("");
	}
			
	$sql_query = "SELECT g.*, c.category_name FROM tbl_channel g, tbl_category c WHERE g.category_id = c.cid AND g.id = '$ID'";
    $channel_result = mysqli_query($connect, $sql_query);
    $data   = mysqli_fetch_assoc($channel_result);

    $setting_qry    = "SELECT * FROM tbl_settings WHERE id = '1'";
    $setting_result = mysqli_query($connect, $setting_qry);
    $settings_row   = mysqli_fetch_assoc($setting_result);

    $provider = $settings_row["providers"];

    $oneSignalAppId = $settings_row['onesignal_app_id'];
    $oneSignalRestApiKey = $settings_row['onesignal_rest_api_key'];
    $fcmNotificationTopic = $settings_row['fcm_notification_topic'];

    $redirect = 'Location:channel.php';

    if (isset($_POST['submit'])) {
        $title = $_POST["title"];
        $message = $_POST["message"];

        if ($_POST["post_id"] == "") {
            $postId = "0";
        } else {
            $postId = $_POST["post_id"];
        }

        $link = $_POST['link'];

        if ($data['channel_type'] == 'YOUTUBE') {
            $bigImage = 'https://img.youtube.com/vi/'.$data['video_id'].'/mqdefault.jpg';
        } else {
            $actualLink = (isset($_SERVER['HTTPS']) ? 'https' : 'http').'://'.$_SERVER['SERVER_NAME'].dirname($_SERVER['REQUEST_URI']);
            $bigImage = $actualLink.'/upload/'.$data['channel_image'];
        }

        $generateRandomId = rand(1000, 9999);
        $uniqueId = "$generateRandomId";

        if ($provider == 'onesignal') {
            ONESIGNAL($uniqueId,  $title, $message, $bigImage, $link, $postId, $oneSignalAppId, $oneSignalRestApiKey, $redirect);
        } else if ($provider == 'firebase') {
            $fcmOAuthToken = getFirebaseOAuthToken();
            FCM_V1($uniqueId, $title, $message, $bigImage, $link, $postId, $fcmOAuthToken, $fcmNotificationTopic, $redirect);
        }

    }

?>

    <section class="content">

        <ol class="breadcrumb">
            <li><a href="dashboard.php">Dashboard</a></li>
            <li><a href="channel.php">Manage Channel</a></li>
            <li class="active">Send Channel Notification</a></li>
        </ol>

        <div class="container-fluid">

            <div class="row clearfix">
                <div class="col-lg-12 col-md-12 col-sm-12 col-xs-12">
                    <form method="post" id="form_validation" enctype="multipart/form-data">
                        <div class="card corner-radius">
                            <div class="header">
                                <h2>SEND NOTIFICATION</h2>
                            </div>
                            <div class="body">

                                <?php if(isset($_SESSION['msg'])) { ?>
                                <div class='alert alert-info alert-dismissible corner-radius' role='alert'>
                                    <button type="button" class="close" data-dismiss="alert" aria-label="Close"><span aria-hidden="true">&times;</span>&nbsp;&nbsp;</button>
                                    <?php echo $_SESSION['msg']; ?>
                                </div>
                                <?php unset($_SESSION['msg']); } ?>

                                <div class="row clearfix">

                                    <?php if ($provider == 'onesignal') { ?>

                                        <?php if ($oneSignalAppId == '0' || $oneSignalRestApiKey == '0') { ?>
                                            <div class="col-sm-12">
                                                <p><b>OneSignal App ID</b> or <b>OneSignal Rest API Key</b> have not been configured, put your OneSignal App ID or OneSignal Rest API Key in the <a href="settings.php"><b>Settings menu</b></a> in the admin panel.</p>
                                            </div>
                                        <?php } else { ?>

                                            <input type="hidden" name="post_id" id="post_id" value="<?php echo $data['id']; ?>" required>
                                            <input type="hidden" name="link" id="link" value="" />

                                            <div class="form-group col-sm-12">
                                                <div class="font-12">Title</div>
                                                <div class="form-line">
                                                    <input type="text" class="form-control" name="title" id="title" placeholder="Title" value="<?php echo $data['category_name']; ?>" required/>
                                                </div>
                                            </div>

                                            <div class="form-group col-sm-12">
                                                <div class="font-12">Message</div>
                                                <div class="form-line">
                                                    <input type="text" class="form-control" name="message" id="message" placeholder="Message" value="<?php echo $data['channel_name']; ?>" required/>
                                                </div>
                                            </div>

                                            <div class="col-sm-6">
                                                <div class="font-12 ex1">Image</div>
                                                <div class="form-group">
                                                    <?php if ($data['channel_type'] == 'YOUTUBE') { ?>
                                                        <input type="file" class="dropify-image" data-max-file-size="3M" data-allowed-file-extensions="jpg jpeg png gif" data-default-file="https://img.youtube.com/vi/<?php echo $data['video_id'];?>/mqdefault.jpg" data-show-remove="false" disabled/>
                                                    <?php } else { ?>
                                                        <input type="file" class="dropify-image" data-max-file-size="3M" data-allowed-file-extensions="jpg jpeg png gif" data-default-file="upload/<?php echo $data['channel_image']; ?>" data-show-remove="false" disabled/>
                                                    <?php } ?>
                                                </div>
                                            </div>

                                            <div class="col-sm-12">
                                                <button class="button button-rounded waves-effect waves-float pull-right" type="submit" name="<?php if (validateResourceAccess($inputToken)) { echo 'submit'; } else { echo 'validate'; } ?>">SEND NOW</button>
                                            </div>

                                        <?php } ?>

                                    <?php } else { ?>

                                        <?php if (file_exists("service-account.json")) { ?>

                                            <?php if (getFirebaseProjectId() == 'invalid') { ?>

                                                <div class="col-sm-12">
                                                    <p>Invalid Project Id in the service-account.json</p>
                                                </div>

                                            <?php } else { ?>

                                                <input type="hidden" name="post_id" id="post_id" value="<?php echo $data['id']; ?>" required>
                                                <input type="hidden" name="link" id="link" value="" />

                                                <div class="form-group col-sm-12">
                                                    <div class="font-12">Title</div>
                                                    <div class="form-line">
                                                        <input type="text" class="form-control" name="title" id="title" placeholder="Title" value="<?php echo $data['category_name']; ?>" required/>
                                                    </div>
                                                </div>

                                                <div class="form-group col-sm-12">
                                                    <div class="font-12">Message</div>
                                                    <div class="form-line">
                                                        <input type="text" class="form-control" name="message" id="message" placeholder="Message" value="<?php echo $data['channel_name']; ?>" required/>
                                                    </div>
                                                </div>

                                                <div class="col-sm-6">
                                                    <div class="font-12 ex1">Image</div>
                                                    <div class="form-group">
                                                        <?php if ($data['channel_type'] == 'YOUTUBE') { ?>
                                                            <input type="file" class="dropify-image" data-max-file-size="3M" data-allowed-file-extensions="jpg jpeg png gif" data-default-file="https://img.youtube.com/vi/<?php echo $data['video_id'];?>/mqdefault.jpg" data-show-remove="false" disabled/>
                                                        <?php } else { ?>
                                                            <input type="file" class="dropify-image" data-max-file-size="3M" data-allowed-file-extensions="jpg jpeg png gif" data-default-file="upload/<?php echo $data['channel_image']; ?>" data-show-remove="false" disabled/>
                                                        <?php } ?>
                                                    </div>
                                                </div>

                                                <div class="col-sm-12">
                                                    <button class="button button-rounded waves-effect waves-float pull-right" type="submit" name="<?php if (validateResourceAccess($inputToken)) { echo 'submit'; } else { echo 'validate'; } ?>">SEND NOW</button>
                                                </div>

                                            <?php } ?>

                                        <?php } else { ?>

                                            <div class="col-sm-12">
                                                <p>Missing <b>service-account.json</b> file, please complete the Firebase Cloud Messaging setup in order to send push notification using Firebase</p>
                                            </div>

                                        <?php } ?>

                                    <?php } ?>
                                        
                                </div>
                            </div>
                        </div>
                    </form>
                </div>
            </div>
        </div>

    </section>

<?php include_once('includes/footer.php'); ?>