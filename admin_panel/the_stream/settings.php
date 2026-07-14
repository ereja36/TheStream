<?php include_once('includes/header.php'); ?>
<script src="assets/plugins/ckeditor/ckeditor.js"></script>

<?php

    $ID =  clean("1");

    $sql = "SELECT * FROM tbl_settings WHERE id = '$ID'";
    $result = $connect->query($sql);
    $settings_row = $result->fetch_assoc();

    $server_url = (isset($_SERVER['HTTPS']) ? 'https' : 'http').'://'.$_SERVER['SERVER_NAME'].dirname($_SERVER['REQUEST_URI']);
    $applicationId = $settings_row['package_name'];
    $plain_text = $server_url.'_applicationId_'.$applicationId;
    $encode = encrypt($plain_text);

    if(isset($_POST['submit'])) {

        $data = array(
            'app_fcm_key' => $_POST['app_fcm_key'],
            'api_key' => $_POST['api_key'],
            'package_name' => $_POST['package_name'],
            'onesignal_app_id' => $_POST['onesignal_app_id'],
            'onesignal_rest_api_key' => $_POST['onesignal_rest_api_key'],
            'providers' => $_POST['providers'],
            'fcm_notification_topic' => $_POST['fcm_notification_topic'],
            'privacy_policy' => $_POST['privacy_policy'],
            'youtube_api_key' => $_POST['youtube_api_key'],
            'more_apps_url' => $_POST['more_apps_url'],
            'enable_player_epg' => isset($_POST['enable_player_epg']) ? (int) $_POST['enable_player_epg'] : 0,
            'privacy_public_url' => $_POST['privacy_public_url'],
            'player_brand_text' => isset($_POST['player_brand_text']) ? $_POST['player_brand_text'] : '',
            'player_brand_logo_url' => isset($_POST['player_brand_logo_url']) ? $_POST['player_brand_logo_url'] : '',
            'player_brand_enabled' => isset($_POST['player_brand_enabled']) ? (int) $_POST['player_brand_enabled'] : 0,
            'app_integrity_enabled' => isset($_POST['app_integrity_enabled']) ? (int) $_POST['app_integrity_enabled'] : 0,
            'app_release_cert_sha256' => isset($_POST['app_release_cert_sha256']) ? $_POST['app_release_cert_sha256'] : '',
            'ads_antiblock_script' => isset($_POST['ads_antiblock_script']) ? $_POST['ads_antiblock_script'] : ''
        );

        $update_setting = update('tbl_settings', $data, "WHERE id = '$ID'");

        if ($update_setting > 0) {
            $_SESSION['msg'] = "Changes Saved...";
            header( "Location:settings.php");
            exit;
        }
    }

?>


    <section class="content">

        <ol class="breadcrumb">
            <li><a href="dashboard.php">Dashboard</a></li>
            <li class="active">Settings</a></li>
        </ol>

       <div class="container-fluid">

            <div class="row clearfix">
                <div class="col-lg-12 col-md-12 col-sm-12 col-xs-12">

                    <form method="post" id="form_validation" enctype="multipart/form-data">
                    <div class="card corner-radius">
                        <div class="header">
                            <h2>SETTINGS</h2>
                            <div class="header-dropdown m-r--5">
                                <button type="submit" name="<?php if (validateResourceAccess($inputToken)) { echo 'submit'; } else { echo 'validate'; } ?>" class="button button-rounded btn-offset waves-effect waves-float">SAVE SETTINGS</button>
                            </div>
                        </div>
                        <div class="body">

                            <?php if(isset($_SESSION['msg'])) { ?>
                                <div class='alert alert-info alert-dismissible corner-radius' role='alert'>
                                    <button type="button" class="close" data-dismiss="alert" aria-label="Close"><span aria-hidden="true">&times;</span>&nbsp;&nbsp;</button>
                                    <?php echo $_SESSION['msg']; ?>
                                </div>
                            <?php unset($_SESSION['msg']); } ?>                            

                            <div class="row clearfix">
                            <div class="">

                                <!-- <div class="col-sm-12">
                                    <div class="alert alert-info alert-dismissible corner-radius bottom-offset">
                                        <p style="margin-top: -3px;">applicationId (Package Name) and Server Key configuration moved to Apps menu</p>
                                        <a href="apps.php"><button type="button" class="button-light button-rounded waves-effect" style="margin-top: 10px;">OPEN APPS MENU</button></a>
                                    </div>
                                </div> -->

                                <input type="hidden" class="form-control" name="package_name" id="package_name" value="<?php echo $settings_row['package_name'];?>" required>

                                <input type="hidden" class="form-control" value="<?php echo $encode; ?>" required>

                                <div class="col-sm-12">
                                    <div class="form-group">
                                        <div class="form-line">
                                            <div class="font-12"><b>Rest API Key</b></div>
                                            <input type="text" class="form-control" name="api_key" id="api_key" value="<?php echo $settings_row['api_key'];?>" required>
                                        </div>
                                        <div class="help-info pull-left"><a href="" data-toggle="modal" data-target="#modal-api-key">Where I have to put my API Key?</a> | <a href="api-key.php"><span class="label label-rounded bg-red">CHANGE API KEY</span></a></div>
                                    </div>
                                </div>

                                <input type="hidden" class="form-control" name="youtube_api_key" id="youtube_api_key" value="<?php echo $settings_row['youtube_api_key'];?>" required>

                                <div class="col-sm-12">
                                    <div class="form-group">
                                            <div class="font-12"><b>Push Notification Provider</b></div>
                                                <select class="form-control show-tick" name="providers" id="providers">
                                                        <?php if ($settings_row['providers'] == 'onesignal') { ?>
                                                            <option value="onesignal" selected="selected">OneSignal</option>
                                                            <option value="firebase">Firebase Cloud Messaging (FCM)</option>
                                                        <?php } else { ?>
                                                            <option value="onesignal">OneSignal</option>
                                                            <option value="firebase" selected="selected">Firebase Cloud Messaging (FCM)</option>
                                                        <?php } ?>
                                                </select>
                                        <div class="help-info pull-left"><font color="#337ab7">Choose your provider for sending push notification</font></div>
                                    </div>
                                </div>

                                <input type="hidden" class="form-control" name="app_fcm_key" id="app_fcm_key" value="<?php echo $settings_row['app_fcm_key'];?>" required>

                                <div class="col-sm-12">
                                    <div class="form-group">
                                        <div class="form-line">
                                            <div class="font-12"><b>FCM Notification Topic</b></div>
                                            <input type="text" class="form-control" name="fcm_notification_topic" id="fcm_notification_topic" value="<?php echo $settings_row['fcm_notification_topic'];?>" required>
                                        </div>
                                        <div class="help-info pull-left"><font color="#337ab7">FCM notification topic must be written in lowercase without space (use underscore)</font></div>
                                    </div>
                                </div>

                                <div class="col-sm-12">
                                    <div class="form-group">
                                        <div class="form-line">
                                            <div class="font-12"><b>OneSignal APP ID</b></div>
                                            <input type="text" class="form-control" name="onesignal_app_id" id="onesignal_app_id" value="<?php echo $settings_row['onesignal_app_id'];?>" required>
                                        </div>
                                        <div class="help-info pull-left"><a href="" data-toggle="modal" data-target="#modal-onesignal">Where do I get my OneSignal app id?</a></div>
                                    </div>
                                </div>

                                <div class="col-sm-12">
                                    <div class="form-group">
                                        <div class="form-line">
                                            <div class="font-12"><b>OneSignal Rest API Key</b></div>
                                            <input type="text" class="form-control" name="onesignal_rest_api_key" id="onesignal_rest_api_key" value="<?php echo $settings_row['onesignal_rest_api_key'];?>" required>
                                        </div>
                                        <div class="help-info pull-left"><a href="" data-toggle="modal" data-target="#modal-onesignal">Where do I get my OneSignal Rest API Key?</a></div>
                                    </div>
                                </div>

                                <div class="col-sm-12">
                                    <div class="form-group">
                                        <div class="form-line">
                                            <div class="font-12"><b>More Apps Url</b></div>
                                            <input type="text" class="form-control" name="more_apps_url" id="more_apps_url" value="<?php echo $settings_row['more_apps_url'];?>" required>
                                        </div>
                                        <div class="help-info pull-left"><font color="#337ab7">More apps url for other apps</font></div>
                                    </div>
                                </div>

                                <div class="col-sm-12">
                                    <div class="form-group">
                                        <div class="form-line">
                                            <div class="font-12"><b>Public Privacy Policy URL</b></div>
                                            <input type="text" class="form-control" name="privacy_public_url" id="privacy_public_url" value="<?php echo isset($settings_row['privacy_public_url']) ? $settings_row['privacy_public_url'] : 'https://shifshqip.com/privacy'; ?>" required>
                                        </div>
                                        <div class="help-info pull-left"><font color="#337ab7">Shown in the app (e.g. https://shifshqip.com/privacy)</font></div>
                                    </div>
                                </div>

                                <div class="col-sm-12">
                                    <div class="form-group">
                                        <div class="font-12"><b>Player EPG Button</b></div>
                                        <select class="form-control show-tick" name="enable_player_epg" id="enable_player_epg">
                                            <?php $epgEnabled = !isset($settings_row['enable_player_epg']) || (int) $settings_row['enable_player_epg'] === 1; ?>
                                            <option value="1" <?php echo $epgEnabled ? 'selected="selected"' : ''; ?>>Enabled</option>
                                            <option value="0" <?php echo !$epgEnabled ? 'selected="selected"' : ''; ?>>Disabled</option>
                                        </select>
                                        <div class="help-info pull-left"><font color="#337ab7">Show EPG button in video player when channel has EPG data</font></div>
                                    </div>
                                </div>

                                <div class="col-sm-12">
                                    <div class="form-group">
                                        <div class="font-12"><b>Player Branding</b></div>
                                        <select class="form-control show-tick" name="player_brand_enabled" id="player_brand_enabled">
                                            <?php $brandEnabled = !isset($settings_row['player_brand_enabled']) || (int) $settings_row['player_brand_enabled'] === 1; ?>
                                            <option value="1" <?php echo $brandEnabled ? 'selected="selected"' : ''; ?>>Enabled</option>
                                            <option value="0" <?php echo !$brandEnabled ? 'selected="selected"' : ''; ?>>Disabled</option>
                                        </select>
                                    </div>
                                </div>

                                <div class="col-sm-12">
                                    <div class="form-group">
                                        <div class="form-line">
                                            <div class="font-12"><b>Player Brand Text</b></div>
                                            <input type="text" class="form-control" name="player_brand_text" id="player_brand_text" value="<?php echo isset($settings_row['player_brand_text']) ? htmlspecialchars($settings_row['player_brand_text']) : 'shifshqip.com'; ?>">
                                        </div>
                                        <div class="help-info pull-left"><font color="#337ab7">Shown top-right in player if logo URL is empty (e.g. shifshqip.com)</font></div>
                                    </div>
                                </div>

                                <div class="col-sm-12">
                                    <div class="form-group">
                                        <div class="form-line">
                                            <div class="font-12"><b>Player Brand Logo URL</b></div>
                                            <input type="text" class="form-control" name="player_brand_logo_url" id="player_brand_logo_url" value="<?php echo isset($settings_row['player_brand_logo_url']) ? htmlspecialchars($settings_row['player_brand_logo_url']) : ''; ?>">
                                        </div>
                                        <div class="help-info pull-left"><font color="#337ab7">Optional image URL shown instead of brand text</font></div>
                                    </div>
                                </div>

                                <div class="col-sm-12">
                                    <div class="form-group">
                                        <div class="font-12"><b>APK Integrity Protection</b></div>
                                        <select class="form-control show-tick" name="app_integrity_enabled" id="app_integrity_enabled">
                                            <?php $integrityEnabled = !isset($settings_row['app_integrity_enabled']) || (int) $settings_row['app_integrity_enabled'] === 1; ?>
                                            <option value="1" <?php echo $integrityEnabled ? 'selected="selected"' : ''; ?>>Enabled</option>
                                            <option value="0" <?php echo !$integrityEnabled ? 'selected="selected"' : ''; ?>>Disabled</option>
                                        </select>
                                        <div class="help-info pull-left"><font color="#337ab7">Block modified/re-signed APK builds (mod apps)</font></div>
                                    </div>
                                </div>

                                <div class="col-sm-12">
                                    <div class="form-group">
                                        <div class="form-line">
                                            <div class="font-12"><b>Allowed Release Cert SHA-256</b></div>
                                            <input type="text" class="form-control" name="app_release_cert_sha256" id="app_release_cert_sha256" value="<?php echo isset($settings_row['app_release_cert_sha256']) ? htmlspecialchars($settings_row['app_release_cert_sha256']) : ''; ?>">
                                        </div>
                                        <div class="help-info pull-left"><font color="#337ab7">Comma-separated SHA-256 fingerprints of official APK signing cert. Empty = allow any signed build.</font></div>
                                    </div>
                                </div>

                                <div class="col-sm-12">
                                    <div class="form-group">
                                        <div class="form-line">
                                            <div class="font-12"><b>Ads Anti-block Script (JSON or key=value)</b></div>
                                            <textarea class="form-control" name="ads_antiblock_script" id="ads_antiblock_script" rows="5"><?php echo isset($settings_row['ads_antiblock_script']) ? htmlspecialchars($settings_row['ads_antiblock_script']) : "{\n  \"reload_on_fail\": true,\n  \"max_retries\": 3,\n  \"retry_delay_ms\": 4000,\n  \"reload_on_resume\": true\n}"; ?></textarea>
                                        </div>
                                        <div class="help-info pull-left"><font color="#337ab7">Controls ad reload when users block ads or ad load fails</font></div>
                                    </div>
                                </div>

                                <div class="col-sm-12">
                                    
                                    <div class="form-group">
                                        <div class="form-line">
                                            <div class="font-12 ex1"><b>Privacy Policy</b></div>
                                            <textarea class="form-control" name="privacy_policy" id="privacy_policy" class="form-control" cols="60" rows="10" required><?php echo $settings_row['privacy_policy'];?></textarea>

                                            <?php if ($ENABLE_RTL_MODE == 'true') { ?>
                                            <script>                             
                                                CKEDITOR.replace( 'privacy_policy' );
                                                CKEDITOR.config.contentsLangDirection = 'rtl';
                                            </script>
                                            <?php } else { ?>
                                            <script>                             
                                                CKEDITOR.replace( 'privacy_policy' );
                                                CKEDITOR.config.height = 400; 
                                            </script>
                                            <?php } ?>

                                        </div>
                                    </div>
                                </div>

                            </div>

                            </div>
                        </div>
                    </div>
                    </form>

                </div>
            </div>
            
        </div>

    </section>


<?php include_once('includes/footer.php'); ?>