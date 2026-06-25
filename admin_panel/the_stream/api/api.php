<?php

error_reporting(0);

require_once("Rest.inc.php");

	class API extends REST {

		public $data = "";
		const demo_version = false;

		private $db 	= NULL;
		private $mysqli = NULL;
		public function __construct() {
			// Init parent contructor
			parent::__construct();
			// Initiate Database connection
			$this->dbConnect();
		}

		/*
		 *  Connect to Database
		*/
		private function dbConnect() {
			require_once ("../includes/config.php");
			$this->mysqli = new mysqli($host, $user, $pass, $database);
			$this->mysqli->query('SET CHARACTER SET utf8mb4');
		}

		/*
		 * Dynmically call the method based on the query string
		 */
		public function processApi() {
			$func = strtolower(trim(str_replace("/","",$_REQUEST['x'])));
			if((int)method_exists($this,$func) > 0)
				$this->$func();
			else
				$this->response('Ooops, no method found!',404); // If the method not exist with in this class "Page not found".
		}

		public function check_connection() {
			if (mysqli_ping($this->mysqli)) {
                $respon = array('status' => 'ok', 'database' => "connected");
                $this->response($this->json($respon), 200);
			} else {
                $respon = array('status' => 'failed', 'database' => 'not connected');
                $this->response($this->json($respon), 404);
			}
		}

		public function get_posts() {

			include "../includes/config.php";
		    $setting_qry    = "SELECT * FROM tbl_settings WHERE id = '1'";
		    $setting_result = mysqli_query($connect, $setting_qry);
		    $settings_row   = mysqli_fetch_assoc($setting_result);
		    $api_key    = $settings_row['api_key'];

		    if ($this->validateResourceAccess($this->retrieveCurrentToken())) {

				if (isset($_GET['api_key'])) {

					$access_key_received = $_GET['api_key'];

					if ($access_key_received == $api_key) {

						if($this->get_request_method() != "GET") $this->response('',406);
						$limit = isset($this->_request['count']) ? ((int)$this->_request['count']) : 10;
						$page = isset($this->_request['page']) ? ((int)$this->_request['page']) : 1;

						$offset = ($page * $limit) - $limit;
						$count_total = $this->get_count_result("SELECT COUNT(DISTINCT n.id) FROM tbl_channel n WHERE n.channel_status = '1'");

						$query = "SELECT DISTINCT
									n.id AS 'channel_id',
									n.category_id,
									n.channel_name, 
									n.channel_image, 
									n.channel_url,
									n.channel_description,
									n.channel_type,
									n.video_id,
									c.category_name,
									n.user_agent,
									n.total_views
									
								FROM 
									tbl_channel n, 
									tbl_category c 
									
								WHERE
									n.channel_status = '1' AND 
									n.category_id = c.cid ORDER BY n.id DESC LIMIT $limit OFFSET $offset";

						$post = $this->get_list_result($query);
						$count = count($post);
						$respon = array(
							'status' => 'ok', 'count' => $count, 'count_total' => $count_total, 'pages' => $page, 'posts' => $post
						);
						$this->response($this->json($respon), 200);

					} else {
						$this->responseInvalidApiKey();
					}

				} else {
					$this->responseRequiredApiKey();
				}
			} else {
				$this->responseInvalidParam();
			}

		}

		public function get_post_detail() {

	    	$id = $_GET['id'];

	    	include "../includes/config.php";
	    	$query_category    = "SELECT category_id FROM tbl_channel WHERE id = $id";
			$category_result = mysqli_query($connect, $query_category);
			$category_row   = mysqli_fetch_assoc($category_result);
			$category_id    = $category_row['category_id'];

			if($this->get_request_method() != "GET") $this->response('', 406);

			$query_post = "SELECT n.id AS 'channel_id', n.category_id, n.channel_name, n.channel_image, n.channel_url, n.channel_description, n.channel_type, n.video_id, c.category_name, n.user_agent, n.total_views FROM tbl_channel n, tbl_category c WHERE n.category_id = c.cid AND n.channel_status = '1' AND n.id = $id LIMIT 1";

			$query_suggested = "SELECT n.id AS 'channel_id', n.category_id, n.channel_name, n.channel_image, n.channel_url, n.channel_type, n.video_id, c.category_name, n.user_agent, n.total_views FROM tbl_channel n, tbl_category c WHERE n.id != $id AND n.category_id = c.cid AND n.channel_status = '1' AND n.category_id = $category_id LIMIT 5";

			$post = $this->get_one_result($query_post);
			$suggested = $this->get_list_result($query_suggested);
			$count = count($post);
			$respon = array(
				'status' => 'ok', 'post' => $post, 'suggested' => $suggested
			);
			$this->response($this->json($respon), 200);
		}

		public function get_category_index() {

			include "../includes/config.php";
		    $setting_qry    = "SELECT * FROM tbl_settings WHERE id = '1'";
		    $setting_result = mysqli_query($connect, $setting_qry);
		    $settings_row   = mysqli_fetch_assoc($setting_result);
		    $api_key    = $settings_row['api_key'];

		    if ($this->validateResourceAccess($this->retrieveCurrentToken())) {

				if (isset($_GET['api_key'])) {

					$access_key_received = $_GET['api_key'];

					if ($access_key_received == $api_key) {

						if($this->get_request_method() != "GET") $this->response('',406);
						$count_total = $this->get_count_result("SELECT COUNT(DISTINCT cid) FROM tbl_category");

						$query = "SELECT DISTINCT 
									cid,
									category_name,
									category_image
									
								FROM
									tbl_category ORDER BY cid DESC";

						$news = $this->get_list_result($query);
						$count = count($news);
						$respon = array(
							'status' => 'ok', 'count' => $count, 'categories' => $news
						);
						$this->response($this->json($respon), 200);

					} else {
						$this->responseInvalidApiKey();
					}
				} else {
					$this->responseRequiredApiKey();
				}

			} else {
				$this->responseInvalidParam();
			}

		}

		public function get_category_posts() {

			include "../includes/config.php";
		    $setting_qry    = "SELECT * FROM tbl_settings WHERE id = '1'";
		    $setting_result = mysqli_query($connect, $setting_qry);
		    $settings_row   = mysqli_fetch_assoc($setting_result);
		    $api_key    = $settings_row['api_key'];

			if (isset($_GET['api_key'])) {

				$access_key_received = $_GET['api_key'];

				if ($access_key_received == $api_key) {

					$id = $_GET['id'];

					if($this->get_request_method() != "GET") $this->response('',406);
					$limit = isset($this->_request['count']) ? ((int)$this->_request['count']) : 10;
					$page = isset($this->_request['page']) ? ((int)$this->_request['page']) : 1;

					$offset = ($page * $limit) - $limit;
					$count_total = $this->get_count_result("SELECT COUNT(DISTINCT id) FROM tbl_channel WHERE channel_status = '1' AND category_id = '$id'");

					$query = "SELECT DISTINCT 
								cid,
								category_name,
								category_image
								
							FROM
								tbl_category 

							WHERE 
								cid = '$id'

							ORDER BY cid DESC";

					$query2 = "SELECT DISTINCT 
								n.id AS 'channel_id',
								n.category_id,
								n.channel_name, 
								n.channel_image, 
								n.channel_url,
								n.channel_description,
								n.channel_type,
								n.video_id,
								c.category_name,
								n.user_agent,
								n.total_views
								
							FROM 
								tbl_channel n, 
								tbl_category c 
								
							WHERE
								n.category_id = c.cid AND 
								n.channel_status = '1' AND
								c.cid = '$id' ORDER BY n.id DESC LIMIT $limit OFFSET $offset";

					$category = $this->get_category_result($query);
					$post = $this->get_list_result($query2);
					$count = count($post);
					$respon = array(
						'status' => 'ok', 'count' => $count, 'count_total' => $count_total, 'pages' => $page, 'category' => $category, 'posts' => $post
					);
					$this->response($this->json($respon), 200);

				} else {
					$this->responseInvalidApiKey();
				}
			} else {
				$this->responseRequiredApiKey();
			}

		}

		public function get_search_results() {

			include "../includes/config.php";
		    $setting_qry    = "SELECT * FROM tbl_settings WHERE id = '1'";
		    $setting_result = mysqli_query($connect, $setting_qry);
		    $settings_row   = mysqli_fetch_assoc($setting_result);
		    $api_key    = $settings_row['api_key'];

		    if ($this->validateResourceAccess($this->retrieveCurrentToken())) {

				if (isset($_GET['api_key'])) {

					$access_key_received = $_GET['api_key'];

					if ($access_key_received == $api_key) {

						$search = $_GET['search'];

						if($this->get_request_method() != "GET") $this->response('',406);
						$limit = isset($this->_request['count']) ? ((int)$this->_request['count']) : 10;
						$page = isset($this->_request['page']) ? ((int)$this->_request['page']) : 1;

						$offset = ($page * $limit) - $limit;
						$count_total = $this->get_count_result("SELECT COUNT(DISTINCT n.id) FROM tbl_channel n, tbl_category c WHERE n.category_id = c.cid AND n.channel_status = '1' AND (n.channel_name LIKE '%$search%' OR n.channel_description LIKE '%$search%')");

						$query = "SELECT distinct 
									n.id AS 'channel_id',
									n.category_id,
									n.channel_name, 
									n.channel_image, 
									n.channel_url,
									n.channel_description,
									n.channel_type,
									n.video_id,
									c.category_name,
									n.user_agent,
									n.total_views
									
								FROM 
									tbl_channel n, 
									tbl_category c 
									
								WHERE n.category_id = c.cid AND n.channel_status = '1' AND (n.channel_name LIKE '%$search%' OR n.channel_description LIKE '%$search%') 

								LIMIT $limit OFFSET $offset";

						$post = $this->get_list_result($query);
						$count = count($post);
						$respon = array(
							'status' => 'ok', 'count' => $count, 'count_total' => $count_total, 'pages' => $page, 'posts' => $post
						);
						$this->response($this->json($respon), 200);

					} else {
						$this->responseInvalidApiKey();
					}
				} else {
					$this->responseRequiredApiKey();
				}

			} else {
				$this->responseInvalidParam();
			}

		}

		public function get_search_results_rtl() {

			include "../includes/config.php";
		    $setting_qry    = "SELECT * FROM tbl_settings WHERE id = '1'";
		    $setting_result = mysqli_query($connect, $setting_qry);
		    $settings_row   = mysqli_fetch_assoc($setting_result);
		    $api_key    = $settings_row['api_key'];

		    if ($this->validateResourceAccess($this->retrieveCurrentToken())) {

				if (isset($_GET['api_key'])) {

					$access_key_received = $_GET['api_key'];

					if ($access_key_received == $api_key) {

						$search = $_GET['search'];

						if($this->get_request_method() != "GET") $this->response('',406);
						$limit = isset($this->_request['count']) ? ((int)$this->_request['count']) : 10;
						$page = isset($this->_request['page']) ? ((int)$this->_request['page']) : 1;

						$offset = ($page * $limit) - $limit;
						$count_total = $this->get_count_result("SELECT COUNT(DISTINCT n.id) FROM tbl_channel n, tbl_category c WHERE n.category_id = c.cid AND n.channel_status = '1' AND (n.channel_name LIKE N'%$search%' OR n.channel_description LIKE N'%$search%')");

						$query = "SELECT DISTINCT 
									n.id AS 'channel_id',
									n.category_id,
									n.channel_name, 
									n.channel_image, 
									n.channel_url,
									n.channel_description,
									n.channel_type,
									n.video_id,
									c.category_name,
									n.user_agent,
									n.total_views
									
								FROM 
									tbl_channel n, 
									tbl_category c 
									
								WHERE n.category_id = c.cid AND n.channel_status = '1' AND (n.channel_name LIKE N'%$search%' OR n.channel_description LIKE N'%$search%') 

								LIMIT $limit OFFSET $offset";

						$post = $this->get_list_result($query);
						$count = count($post);
						$respon = array(
							'status' => 'ok', 'count' => $count, 'count_total' => $count_total, 'pages' => $page, 'posts' => $post
						);
						$this->response($this->json($respon), 200);

					} else {
						$this->responseInvalidApiKey();
					}
				} else {
					$this->responseRequiredApiKey();
				}

			} else {
				$this->responseInvalidParam();
			}

		}

		public function get_settings() {

			include "../includes/config.php";
		    $setting_qry    = "SELECT * FROM tbl_settings WHERE id = '1'";
		    $setting_result = mysqli_query($connect, $setting_qry);
		    $settings_row   = mysqli_fetch_assoc($setting_result);
		    $api_key    = $settings_row['api_key'];

		    if ($this->validateResourceAccess($this->retrieveCurrentToken())) {

				if (isset($_GET['api_key'])) {

					$access_key_received = $_GET['api_key'];

					if ($access_key_received == $api_key) {

						if($this->get_request_method() != "GET") $this->response('',406);
								
						$query = "SELECT * FROM tbl_settings WHERE id = 1";

						$result = $this->get_one_result($query);
						$respon = array(
							'status' => 'ok', 'post' => $result
						);
						$this->response($this->json($respon), 200);

					} else {
						$this->responseInvalidApiKey();
					}
				} else {
					$this->responseRequiredApiKey();
				}			

			} else {
				$this->responseInvalidParam();
			}

		}

		public function get_ads() {

			include "../includes/config.php";
		    $setting_qry    = "SELECT * FROM tbl_settings WHERE id = '1'";
		    $setting_result = mysqli_query($connect, $setting_qry);
		    $settings_row   = mysqli_fetch_assoc($setting_result);
		    $api_key    = $settings_row['api_key'];

		    if ($this->validateResourceAccess($this->retrieveCurrentToken())) {

				if (isset($_GET['api_key'])) {

					$access_key_received = $_GET['api_key'];

					if ($access_key_received == $api_key) {			

						if($this->get_request_method() != "GET") $this->response('',406);
								
						$query = "SELECT a.*, s.youtube_api_key, s.youtube_api_key, s.fcm_notification_topic, s.onesignal_app_id, s.more_apps_url FROM tbl_ads a, tbl_settings s WHERE a.id = 1 AND s.id = 1";

						$result = $this->get_one_result($query);
						$respon = array(
							'status' => 'ok', 'ads' => $result
						);
						$this->response($this->json($respon), 200);

					} else {
						$this->responseInvalidApiKey();
					}
				} else {
					$this->responseRequiredApiKey();
				}

			} else {
				$this->responseInvalidParam();
			}

		}	

		public function get_config() {

			include "../includes/config.php";
			include "../functions.php";

			$setting_qry    = "SELECT * FROM tbl_settings WHERE id = '1'";
			$setting_result = mysqli_query($connect, $setting_qry);
			$settings_row   = mysqli_fetch_assoc($setting_result);
			$api_key    = $settings_row['api_key'];

			if ($this->validateResourceAccess($this->retrieveCurrentToken())) {

				if (isset($_GET['api_key'])) {

					$access_key_received = $_GET['api_key'];
					$package_name = $_GET['package_name'];

					if ($access_key_received == $api_key) {			

						if($this->get_request_method() != "GET") $this->response('',406);

						$sql_api_key = "SELECT * FROM tbl_settings WHERE id = 1";
						$key_result = $connect->query($sql_api_key);
						$key_row = $key_result->fetch_assoc();

						$sql_settings = "SELECT * FROM tbl_settings WHERE id = 1";
						$sql_ads = "SELECT * FROM tbl_ads WHERE id = 1";
						$sqlApp = "SELECT package_name, status, redirect_url, design_id FROM tbl_app_config WHERE package_name = '$package_name' LIMIT 1 ";
						$sqlLicense = "SELECT item_id, item_name, buyer, license_type, purchase_date FROM tbl_license LIMIT 1";

						$settings = $this->get_one_result($sql_settings);
						$ads = $this->get_one_result($sql_ads);

						$appCount = count($this->get_list_result($sqlApp));
						$app = $this->get_one_result($sqlApp);
						$license = $this->get_one_result($sqlLicense);

						$AppArray = array( 'package_name' => '', 'status' => '', 'redirect_url' => '', 'design_id' => 'v1');

						if ($appCount > 0) {
							$respon = array('status' => 'ok', 'app' => $app, 'settings' => $settings, 'ads' => $ads, 'license' => $license);
						} else {
							$respon = array('status' => 'ok', 'app' => $AppArray, 'settings' => $settings, 'ads' => $ads, 'license' => $license);
						}


						$this->response($this->json($respon), 200);

					} else {
						$this->responseInvalidApiKey();
					}
				} else {
					$this->responseRequiredApiKey();
				}

			} else {
				$this->responseInvalidParam();
			}

		}
		
		public function update_view() {

			include "../includes/config.php";

			if ($_SERVER['REQUEST_METHOD'] == 'POST') {

				$response = array();

				$id = $_POST['id'];

				$sql = "UPDATE tbl_channel SET total_views = total_views + 1 WHERE id = '$id'";

				if(mysqli_query($connect, $sql)) {
					$response["value"] = 1;
					$response["message"] = "success update views";

					header( 'Content-Type: application/json; charset=utf-8' );
					echo json_encode($response);
				} else {
					$response["value"] = 0;
					$response["message"] = "failed update vieww!";

					header( 'Content-Type: application/json; charset=utf-8' );
					echo json_encode($response);
				}

				mysqli_close($connect);

			}

		}

		public function register_device_playlist() {

			include "../includes/config.php";
			$setting_qry = "SELECT * FROM tbl_settings WHERE id = '1'";
			$setting_result = mysqli_query($connect, $setting_qry);
			$settings_row = mysqli_fetch_assoc($setting_result);
			$api_key = $settings_row['api_key'];

			if ($this->get_request_method() != "POST") $this->response('', 406);

			if ($this->validateResourceAccess($this->retrieveCurrentToken())) {
				$access_key_received = isset($_POST['api_key']) ? $_POST['api_key'] : '';
				$device_id = isset($_POST['device_id']) ? trim($_POST['device_id']) : '';
				$playlist_name = isset($_POST['playlist_name']) ? trim($_POST['playlist_name']) : '';
				$playlist_type = isset($_POST['playlist_type']) ? trim($_POST['playlist_type']) : 'm3u';
				$playlist_source = isset($_POST['playlist_source']) ? trim($_POST['playlist_source']) : '';
				$channels_count = isset($_POST['channels_count']) ? intval($_POST['channels_count']) : 0;
				$status = isset($_POST['status']) ? intval($_POST['status']) : 1;

				if ($access_key_received != $api_key) {
					$this->responseInvalidApiKey();
					return;
				}

				if ($device_id == '' || $playlist_source == '') {
					$resp = array("status" => 'failed', "msg" => 'device_id and playlist_source are required');
					$this->response($this->json($resp), 200);
					return;
				}

				$blockReason = '';
				if ($this->isDeviceBlocked($device_id, $blockReason)) {
					$resp = array("status" => 'failed', "msg" => 'device blocked', "device_id" => $device_id, "blocked" => 1, "reason" => $blockReason);
					$this->response($this->json($resp), 200);
					return;
				}

				$device_id = mysqli_real_escape_string($connect, $device_id);
				$playlist_name = mysqli_real_escape_string($connect, $playlist_name);
				$playlist_type = mysqli_real_escape_string($connect, $playlist_type);
				$playlist_source = mysqli_real_escape_string($connect, $playlist_source);

				$exists = mysqli_query($connect, "SELECT id FROM tbl_device_playlist WHERE device_id = '$device_id' AND playlist_source = '$playlist_source' LIMIT 1");
				if ($exists && mysqli_num_rows($exists) > 0) {
					$row = mysqli_fetch_assoc($exists);
					$id = $row['id'];
					$sql = "UPDATE tbl_device_playlist 
							SET playlist_name = '$playlist_name',
								playlist_type = '$playlist_type',
								channels_count = '$channels_count',
								status = '$status',
								updated_at = NOW()
							WHERE id = '$id'";
					mysqli_query($connect, $sql);
				} else {
					$sql = "INSERT INTO tbl_device_playlist (device_id, playlist_name, playlist_type, playlist_source, channels_count, status, created_at, updated_at) 
							VALUES ('$device_id', '$playlist_name', '$playlist_type', '$playlist_source', '$channels_count', '$status', NOW(), NOW())";
					mysqli_query($connect, $sql);
				}

				$resp = array("status" => 'ok', "msg" => 'device playlist synced');
				$this->response($this->json($resp), 200);
			} else {
				$this->responseInvalidParam();
			}
		}

		public function get_device_playlists() {

			include "../includes/config.php";
			$setting_qry = "SELECT * FROM tbl_settings WHERE id = '1'";
			$setting_result = mysqli_query($connect, $setting_qry);
			$settings_row = mysqli_fetch_assoc($setting_result);
			$api_key = $settings_row['api_key'];

			if ($this->get_request_method() != "GET") $this->response('', 406);

			if ($this->validateResourceAccess($this->retrieveCurrentToken())) {
				$access_key_received = isset($_GET['api_key']) ? $_GET['api_key'] : '';
				$device_id = isset($_GET['device_id']) ? trim($_GET['device_id']) : '';

				if ($access_key_received != $api_key) {
					$this->responseInvalidApiKey();
					return;
				}

				if ($device_id == '') {
					$resp = array("status" => 'failed', "msg" => 'device_id is required');
					$this->response($this->json($resp), 200);
					return;
				}

				$blockReason = '';
				if ($this->isDeviceBlocked($device_id, $blockReason)) {
					$resp = array("status" => 'failed', "msg" => 'device blocked', "device_id" => $device_id, "blocked" => 1, "reason" => $blockReason, "count" => 0, "playlists" => array());
					$this->response($this->json($resp), 200);
					return;
				}

				$device_id = mysqli_real_escape_string($connect, $device_id);
				$query = "SELECT id, device_id, playlist_name, playlist_type, playlist_source, channels_count, status, updated_at 
						  FROM tbl_device_playlist 
						  WHERE device_id = '$device_id' AND status = 1
						  ORDER BY status DESC, updated_at DESC";
				$playlists = $this->get_list_result($query);
				$respon = array('status' => 'ok', 'count' => count($playlists), 'playlists' => $playlists);
				$this->response($this->json($respon), 200);
			} else {
				$this->responseInvalidParam();
			}
		}

		public function check_device_status() {

			include "../includes/config.php";
			$setting_qry = "SELECT * FROM tbl_settings WHERE id = '1'";
			$setting_result = mysqli_query($connect, $setting_qry);
			$settings_row = mysqli_fetch_assoc($setting_result);
			$api_key = $settings_row['api_key'];

			if ($this->get_request_method() != "GET") $this->response('', 406);

			if ($this->validateResourceAccess($this->retrieveCurrentToken())) {
				$access_key_received = isset($_GET['api_key']) ? $_GET['api_key'] : '';
				$device_id = isset($_GET['device_id']) ? trim($_GET['device_id']) : '';

				if ($access_key_received != $api_key) {
					$this->responseInvalidApiKey();
					return;
				}

				if ($device_id == '') {
					$resp = array("status" => 'failed', "msg" => 'device_id is required');
					$this->response($this->json($resp), 200);
					return;
				}

				$blockReason = '';
				$blocked = $this->isDeviceBlocked($device_id, $blockReason);
				$respon = array(
					'status' => 'ok',
					'device_id' => $device_id,
					'blocked' => $blocked ? 1 : 0,
					'reason' => $blockReason
				);
				$this->response($this->json($respon), 200);
			} else {
				$this->responseInvalidParam();
			}
		}

		public function get_device_profile() {

			include "../includes/config.php";
			$setting_qry = "SELECT * FROM tbl_settings WHERE id = '1'";
			$setting_result = mysqli_query($connect, $setting_qry);
			$settings_row = mysqli_fetch_assoc($setting_result);
			$api_key = $settings_row['api_key'];

			if ($this->get_request_method() != "GET") $this->response('', 406);

			if ($this->validateResourceAccess($this->retrieveCurrentToken())) {
				$access_key_received = isset($_GET['api_key']) ? $_GET['api_key'] : '';
				$device_id = isset($_GET['device_id']) ? trim($_GET['device_id']) : '';

				if ($access_key_received != $api_key) {
					$this->responseInvalidApiKey();
					return;
				}

				if ($device_id == '') {
					$resp = array("status" => 'failed', "msg" => 'device_id is required');
					$this->response($this->json($resp), 200);
					return;
				}

				$blockReason = '';
				$blocked = $this->isDeviceBlocked($device_id, $blockReason);
				$noAds = $this->isDeviceNoAds($device_id);

				$respon = array(
					'status' => 'ok',
					'device_id' => $device_id,
					'blocked' => $blocked ? 1 : 0,
					'reason' => $blockReason,
					'no_ads' => $noAds ? 1 : 0
				);
				$this->response($this->json($respon), 200);
			} else {
				$this->responseInvalidParam();
			}
		}

		public function get_startup_popup() {

			include "../includes/config.php";
			$setting_qry = "SELECT * FROM tbl_settings WHERE id = '1'";
			$setting_result = mysqli_query($connect, $setting_qry);
			$settings_row = mysqli_fetch_assoc($setting_result);
			$api_key = $settings_row['api_key'];

			if ($this->get_request_method() != "GET") $this->response('', 406);

			if ($this->validateResourceAccess($this->retrieveCurrentToken())) {
				$access_key_received = isset($_GET['api_key']) ? $_GET['api_key'] : '';
				$device_id = isset($_GET['device_id']) ? trim($_GET['device_id']) : '';

				if ($access_key_received != $api_key) {
					$this->responseInvalidApiKey();
					return;
				}

				$blockReason = '';
				$blocked = $this->isDeviceBlocked($device_id, $blockReason);
				if ($blocked) {
					$respon = array(
						'status' => 'failed',
						'msg' => 'device blocked',
						'blocked' => 1,
						'reason' => $blockReason
					);
					$this->response($this->json($respon), 200);
					return;
				}

				$popup = array();
				$tableCheck = $this->mysqli->query("SHOW TABLES LIKE 'tbl_startup_popup'");
				if ($tableCheck && $tableCheck->num_rows > 0) {
					$deviceIdEscaped = $this->real_escape($device_id);
					$query = "SELECT id, title, message, popup_type, media_url, target_url, duration_seconds, allow_manual_close, device_id, status
					          FROM tbl_startup_popup
					          WHERE status = 1 AND (device_id = '' OR device_id = '$deviceIdEscaped')
					          ORDER BY CASE WHEN device_id = '$deviceIdEscaped' THEN 0 ELSE 1 END, updated_at DESC
					          LIMIT 1";
					$popup = $this->get_one_result($query);
				}

				if (count($popup) == 0) {
					$popup = array(
						'status' => 0,
						'title' => '',
						'message' => '',
						'popup_type' => 'text',
						'media_url' => '',
						'target_url' => '',
						'duration_seconds' => 0,
						'allow_manual_close' => 1,
						'device_id' => ''
					);
				}

				$respon = array(
					'status' => 'ok',
					'popup' => $popup
				);
				$this->response($this->json($respon), 200);
			} else {
				$this->responseInvalidParam();
			}
		}

		//deprecated
		public function get_user_token() {

	    	$user_unique_id = $_GET['user_unique_id'];

			if($this->get_request_method() != "GET") $this->response('', 406);

			$query_post = "SELECT * FROM tbl_fcm_token WHERE user_unique_id = $user_unique_id ";

			$post = $this->get_one_result($query_post);
			$count = count($post);
			$respon = array(
				'status' => 'ok', 'response' => $post
			);
			$this->response($this->json($respon), 200);
		}

		//deprecated
		public function post_user_token() {

			include "../includes/config.php";

			if (!$_POST['user_android_token'] || $_POST['user_android_token'] == '') {
			    die("Error: token required");
			}

			$token = $_POST['user_android_token'];
			$unique_id = $_POST['user_unique_id'];
			$app_version = $_POST['user_app_version'];
			$os_version = $_POST['user_os_version'];
			$device_model = $_POST['user_device_model'];
			$device_manufacturer = $_POST['user_device_manufacturer'];

			$sql = "INSERT INTO tbl_fcm_token (user_android_token, user_unique_id, app_version, os_version, device_model, device_manufacturer) VALUES ('$token', '$unique_id', '$app_version', '$os_version', '$device_model', '$device_manufacturer')";

			if (mysqli_query($connect, $sql)) {
			    echo "New record created successfully";
			} else {
			    echo "Error: " . $sql . "<br>" . mysqli_error($connect);
			}

			$connect->close();	

		}		

		//deprecated
		public function update_user_token() {

			include "../includes/config.php";

			if (!$_POST['user_android_token'] || $_POST['user_android_token'] == '') {
			    die("Error: token required");
			}

			$user_android_token = $_POST['user_android_token'];
			$user_unique_id = $_POST['user_unique_id'];

			$sql = " UPDATE tbl_fcm_token SET user_android_token = '$user_android_token' WHERE user_unique_id = '$user_unique_id' ";
			
			if (mysqli_query($connect, $sql)) {
			    echo "Record updated successfully";
			} else {
			    echo "Error: " . $sql . "<br>" . mysqli_error($connect);
			}

			$connect->close();
			
		}


		//don't edit all the code below
		private function get_list($query) {
			$r = $this->mysqli->query($query) or die($this->mysqli->error.__LINE__);
			if($r->num_rows > 0) {
				$result = array();
				while($row = $r->fetch_assoc()) {
					$result[] = $row;
				}
				$this->response($this->json($result), 200); // send user details
			}
			$this->response('',204);	// If no records "No Content" status
		}

		private function get_list_result($query) {
			$result = array();
			$r = $this->mysqli->query($query) or die($this->mysqli->error.__LINE__);
			if($r->num_rows > 0) {
				while($row = $r->fetch_assoc()) {
					$result[] = $row;
				}
			}
			return $result;
		}

		private function get_object_result($query) {
			$result = array();
			$r = $this->mysqli->query($query) or die($this->mysqli->error.__LINE__);
			if($r->num_rows > 0) {
				while($row = $r->fetch_assoc()) {
					$result = $row;
				}
			}
			return $result;
		}

		private function get_category_result($query) {
			$result = array();
			$r = $this->mysqli->query($query) or die($this->mysqli->error.__LINE__);
			if($r->num_rows > 0) {
				while($row = $r->fetch_assoc()) {
					$result = $row;
				}
			}
			return $result;
		}

		private function get_one_result($query) {
				$result = array();
				$r = $this->mysqli->query($query) or die($this->mysqli->error.__LINE__);
				if($r->num_rows > 0) $result = $r->fetch_assoc();
				return $result;
		}

		private function get_one($query) {
			$r = $this->mysqli->query($query) or die($this->mysqli->error.__LINE__);
			if($r->num_rows > 0) {
				$result = $r->fetch_assoc();
				$this->response($this->json($result), 200); // send user details
			}
			$this->response('',204);	// If no records "No Content" status
		}

		private function get_count($query) {
			$r = $this->mysqli->query($query) or die($this->mysqli->error.__LINE__);
			if($r->num_rows > 0) {
				$result = $r->fetch_row();
				$this->response($result[0], 200);
			}
			$this->response('',204);	// If no records "No Content" status
		}

		private function get_count_result($query) {
			$r = $this->mysqli->query($query) or die($this->mysqli->error.__LINE__);
			if($r->num_rows > 0) {
				$result = $r->fetch_row();
				return $result[0];
			}
			return 0;
		}

		private function post_one($obj, $column_names, $table_name) {
			$keys 		= array_keys($obj);
			$columns 	= '';
			$values 	= '';
			foreach($column_names as $desired_key) { // Check the recipe received. If blank insert blank into the array.
			  if(!in_array($desired_key, $keys)) {
			   	$$desired_key = '';
				} else {
					$$desired_key = $obj[$desired_key];
				}
				$columns 	= $columns.$desired_key.',';
				$values 	= $values."'".$this->real_escape($$desired_key)."',";
			}
			$query = "INSERT INTO ".$table_name."(".trim($columns,',').") VALUES(".trim($values,',').")";
			//echo "QUERY : ".$query;
			if(!empty($obj)) {
				//$r = $this->mysqli->query($query) or trigger_error($this->mysqli->error.__LINE__);
				if ($this->mysqli->query($query)) {
					$status = "success";
			    $msg 		= $table_name." created successfully";
				} else {
					$status = "failed";
			    $msg 		= $this->mysqli->error.__LINE__;
				}
				$resp = array('status' => $status, "msg" => $msg, "data" => $obj);
				$this->response($this->json($resp),200);
			} else {
				$this->response('',204);	//"No Content" status
			}
		}

		private function post_update($id, $obj, $column_names, $table_name) {
			$keys = array_keys($obj[$table_name]);
			$columns = '';
			$values = '';
			foreach($column_names as $desired_key){ // Check the recipe received. If key does not exist, insert blank into the array.
			  if(!in_array($desired_key, $keys)) {
			   	$$desired_key = '';
				} else {
					$$desired_key = $obj[$table_name][$desired_key];
				}
				$columns = $columns.$desired_key."='".$this->real_escape($$desired_key)."',";
			}

			$query = "UPDATE ".$table_name." SET ".trim($columns,',')." WHERE id=$id";
			if(!empty($obj)) {
				// $r = $this->mysqli->query($query) or die($this->mysqli->error.__LINE__);
				if ($this->mysqli->query($query)) {
					$status = "success";
					$msg 	= $table_name." update successfully";
				} else {
					$status = "failed";
					$msg 	= $this->mysqli->error.__LINE__;
				}
				$resp = array('status' => $status, "msg" => $msg, "data" => $obj);
				$this->response($this->json($resp),200);
			} else {
				$this->response('',204);	// "No Content" status
			}
		}

		private function delete_one($id, $table_name) {
			if($id > 0) {
				$query="DELETE FROM ".$table_name." WHERE id = $id";
				if ($this->mysqli->query($query)) {
					$status = "success";
			    $msg 		= "One record " .$table_name." successfully deleted";
				} else {
					$status = "failed";
			    $msg 		= $this->mysqli->error.__LINE__;
				}
				$resp = array('status' => $status, "msg" => $msg);
				$this->response($this->json($resp),200);
			} else {
				$this->response('',204);	// If no records "No Content" status
			}
		}

		private function responseInvalidParam() {
			$resp = array("status" => 'Failed', "msg" => 'Invalid Parameter' );
			$this->response($this->json($resp), 200);
		}

		private function responseRequiredApiKey() {
			$resp = array("status" => 'Failed', "msg" => 'Forbidden, API Key is Required!' );
			$this->response($this->json($resp), 200);
		}

		private function responseInvalidApiKey() {
			$resp = array("status" => 'Failed', "msg" => 'Whoops! API Key is invalid!' );
			$this->response($this->json($resp), 200);
		}

		private function isDeviceBlocked(string $deviceId, string &$reason = ''): bool {
			$reason = '';
			if ($deviceId == '') {
				return false;
			}
			$deviceIdEscaped = $this->real_escape($deviceId);
			$query = "SELECT status, reason FROM tbl_device_blocklist WHERE device_id = '$deviceIdEscaped' ORDER BY id DESC LIMIT 1";
			$result = $this->mysqli->query($query);
			if ($result && $result->num_rows > 0) {
				$row = $result->fetch_assoc();
				$reason = isset($row['reason']) ? $row['reason'] : '';
				return ((int) $row['status']) === 1;
			}
			return false;
		}

		private function isDeviceNoAds(string $deviceId): bool {
			if ($deviceId == '') {
				return false;
			}
			$tableCheck = $this->mysqli->query("SHOW TABLES LIKE 'tbl_device_access'");
			if (!$tableCheck || $tableCheck->num_rows == 0) {
				return false;
			}
			$deviceIdEscaped = $this->real_escape($deviceId);
			$query = "SELECT no_ads, status FROM tbl_device_access WHERE device_id = '$deviceIdEscaped' LIMIT 1";
			$result = $this->mysqli->query($query);
			if ($result && $result->num_rows > 0) {
				$row = $result->fetch_assoc();
				return ((int) $row['status']) === 1 && ((int) $row['no_ads']) === 1;
			}
			return false;
		}

		private function retrieveCurrentToken(): string {
			include "../includes/config.php";
			$sqlLicense = "SELECT * FROM tbl_license ORDER BY id DESC LIMIT 1";
		    $licenseResult = $connect->query($sqlLicense);
		    $row = $licenseResult->fetch_assoc();
		    $inputToken = $row['purchase_code'];
		    return $inputToken;
		}

		private function getObfuscatedTarget(string $segment): int {
		    $char_val = 0;
		    for ($i = 0; $i < strlen($segment); $i++) {
		        $char_val += ord($segment[$i]);
		    }
		    return $char_val;
		}

		private function validateResourceAccess(string $data): bool {
		    $char_P = ord('P');
		    $char_T = ord('T');
		    $targetA = ($char_T - $char_P) + ($this->getObfuscatedTarget('L'));
		    $val_a = 10;
		    $val_b = 4;
		    $val_c = 2;
		    $val_d = 5;
		    $targetA = ($val_a * $val_b) - $val_b;
		    $targetB = ($val_c * $val_a * $val_c) + $val_b;
		    $count = mb_strlen($data, 'UTF-8');
		    if ($count === $targetA || $count === $targetB) {
		        return true;
		    } else {
		        return false;
		    }
		}

		/* ==================================== End of API utilities ==========================================
		 * ====================================================================================================
		 */

		/* Encode array into JSON */
		private function json($data) {
			if(is_array($data)) {
				return json_encode($data, JSON_NUMERIC_CHECK);
			}
		}

		/* String mysqli_real_escape_string */
		private function real_escape($s) {
			return mysqli_real_escape_string($this->mysqli, $s);
		}
	}

	// Initiate Library
	$api = new API;
	if (isset($_GET['check_connection'])) {
		$api->check_connection();
	} else if (isset($_GET['get_posts'])) {
		$api->get_posts();
	} else if (isset($_GET['get_post_detail'])) {
		$api->get_post_detail();
	} else if (isset($_GET['get_category_index'])) {
		$api->get_category_index();
	} else if (isset($_GET['get_category_posts'])) {
		$api->get_category_posts();
	} else if (isset($_GET['get_search_results'])) {
		$api->get_search_results();
	} else if (isset($_GET['get_search_results_rtl'])) {
		$api->get_search_results_rtl();
	} else if (isset($_GET['get_settings'])) {
		$api->get_settings();
	} else if (isset($_GET['get_ads'])) {
		$api->get_ads();
	} else if (isset($_GET['get_config'])) {
		$api->get_config();
	} else if (isset($_GET['update_view'])) {
		$api->update_view();
	} else if (isset($_GET['register_device_playlist'])) {
		$api->register_device_playlist();
	} else if (isset($_GET['get_device_playlists'])) {
		$api->get_device_playlists();
	} else if (isset($_GET['check_device_status'])) {
		$api->check_device_status();
	} else if (isset($_GET['get_device_profile'])) {
		$api->get_device_profile();
	} else if (isset($_GET['get_startup_popup'])) {
		$api->get_startup_popup();
	} else {
		$api->processApi();
	}
?>
