    <section>
        <!-- Left Sidebar -->
        <aside id="leftsidebar" class="sidebar">
            <!-- User Info -->
            <div class="user-info">
                
                <div class="info-container">
                    <div class="col-lg-3 col-md-3 col-sm-3 col-xs-3">
                        <img src="assets/images/ic_launcher.png" width="40px" height="40px" />
                    </div>
                    <div class="name" data-toggle="dropdown" aria-haspopup="true" aria-expanded="false">
                    <b><?php echo $data['username'] ?></b>
                    </div>
                    <div class="email"><b><?php echo $data['email'] ?></b></div>
                    <div class="btn-group user-helper-dropdown">
                        <i class="material-icons" data-toggle="dropdown" aria-haspopup="true" aria-expanded="true">keyboard_arrow_down</i>
                        <ul class="dropdown-menu pull-right">
                            <li><a href="admin-edit.php?id=<?php echo $data['id']; ?>"><i class="material-icons">person</i>Profile</a></li>
                            <li><a href="logout.php"><i class="material-icons">power_settings_new</i>Logout</a></li>
                        </ul>
                    </div>
                </div>
            </div>
            <!-- #User Info -->
            <!-- Menu -->
            <div class="menu">
                <ul class="list">
                    <?php $page = $_SERVER['REQUEST_URI']; ?>
                    <li class="header">MENU</li>
                    <li class="<?php if (strpos($page, 'dashboard') !== false) {echo 'active';} else { echo 'noactive'; }?>">
                        <a href="dashboard.php">
                            <i class="material-icons">dashboard</i>
                            <span><?php echo $menu_dashboard; ?></span>
                        </a>
                    </li>

                    <li class="<?php if (strpos($page, 'category') !== false) {echo 'active';} else { echo 'noactive'; }?>">
                        <a href="category.php">
                            <i class="material-icons">view_list</i>
                            <span><?php echo $menu_category; ?></span>
                        </a>
                    </li>

                    <li class="<?php if (strpos($page, 'channel') !== false || strpos($page, 'channel') !== false) {echo 'active';} else { echo 'noactive'; }?>">
                        <a href="channel.php">
                            <i class="material-icons">live_tv</i>
                            <span><?php echo $menu_channel; ?></span>
                        </a>
                    </li>

                    <li class="<?php if (strpos($page, 'ads') !== false) {echo 'active';} else { echo 'noactive'; }?>">
                        <a href="ads.php">
                            <i class="material-icons">monetization_on</i>
                            <span><?php echo $menu_ads; ?></span>
                        </a>
                    </li>

                    <li class="<?php if (strpos($page, 'notification') !== false) {echo 'active';} else { echo 'noactive'; }?>">
                        <a href="notification.php">
                            <i class="material-icons">notifications</i>
                            <span><?php echo $menu_notification; ?></span>
                        </a>
                    </li>

                    <li class="<?php if (strpos($page, 'admin') !== false) {echo 'active';} else { echo 'noactive'; }?>">
                        <a href="admin.php">
                            <i class="material-icons">people</i>
                            <span><?php echo $menu_administrator; ?></span>
                        </a>
                    </li>

                    <li class="<?php if (strpos($page, 'settings') !== false) {echo 'active';} else { echo 'noactive'; }?>">
                        <a href="settings.php">
                            <i class="material-icons">settings</i>
                            <span><?php echo $menu_setting; ?></span>
                        </a>
                    </li>

                    <li class="<?php if (strpos($page, 'apps.php') !== false || strpos($page, 'apps-') !== false) {echo 'active';} else { echo 'noactive'; }?>">
                        <a href="apps.php">
                            <i class="material-icons">adb</i>
                            <span><?php echo $menu_app; ?></span>
                        </a>
                    </li>

                    <li class="<?php if (strpos($page, 'device-playlist') !== false) {echo 'active';} else { echo 'noactive'; }?>">
                        <a href="device-playlist.php">
                            <i class="material-icons">devices</i>
                            <span><?php echo $menu_device_playlist; ?></span>
                        </a>
                    </li>

                    <li class="<?php if (strpos($page, 'startup-popup') !== false) {echo 'active';} else { echo 'noactive'; }?>">
                        <a href="startup-popup.php">
                            <i class="material-icons">announcement</i>
                            <span><?php echo $menu_startup_popup; ?></span>
                        </a>
                    </li>

                    <li class="<?php if (strpos($page, 'app-design') !== false) {echo 'active';} else { echo 'noactive'; }?>">
                        <a href="app-design.php">
                            <i class="material-icons">palette</i>
                            <span><?php echo $menu_app_design; ?></span>
                        </a>
                    </li>

                    <li class="<?php if (strpos($page, 'playlist-guard') !== false) {echo 'active';} else { echo 'noactive'; }?>">
                        <a href="playlist-guard.php">
                            <i class="material-icons">security</i>
                            <span><?php echo $menu_playlist_guard; ?></span>
                        </a>
                    </li>

                    <li class="<?php if (strpos($page, 'license') !== false) {echo 'active';} else { echo 'noactive'; }?>">
                        <a href="license.php">
                            <i class="material-icons">vpn_key</i>
                            <span><?php echo $menu_license; ?></span>
                        </a>
                    </li>

                    <li>
                        <a href="logout.php">
                            <i class="material-icons">power_settings_new</i>
                            <span><?php echo $menu_logout; ?></span>
                        </a>
                    </li>

                </ul>
            </div>
            <!-- #Menu -->
            <!-- Footer -->
            <div class="legal">
                <div class="copyright">
                    <?php echo $copyright; ?>
                </div>
                <div class="version">
                    <b>Version: </b> <?php echo $app_version; ?>
                </div>
            </div>
            <!-- #Footer -->
        </aside>
        <!-- #END# Left Sidebar -->
        
    </section>