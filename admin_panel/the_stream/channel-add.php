<?php include_once('includes/header.php'); ?>
<script src="assets/plugins/ckeditor/ckeditor.js"></script>

<?php 

if (isset($_POST['submit'])) {

    if ($_POST['upload_type'] == 'URL') {

        $video_id = '';
        $channel = clean($_POST['channel_url']);

        $channel_image = time().'_'.$_FILES['channel_image']['name'];
        $image = $_FILES['channel_image']['tmp_name'];
        $path = 'upload/'.$channel_image;
        copy($image, $path);

        if ($_POST['user_agent_type'] == 'CUSTOM') {
            $user_agent = clean($_POST['user_agent']);
        } else {
            $user_agent = clean('default');
        }

    } else if ($_POST['upload_type'] == 'EMBED') {

        $video_id = '';
        $channel = clean($_POST['embed_url']);

        $channel_image = time().'_'.$_FILES['embed_image']['name'];
        $image = $_FILES['embed_image']['tmp_name'];
        $path = 'upload/'.$channel_image;
        copy($image, $path);

        $user_agent = clean('default');

    } else {
        $channel = clean($_POST['youtube']);

        if ($_FILES['youtube_thumbnail']['name'] != '') {
            $channel_image = time().'_'.$_FILES['youtube_thumbnail']['name'];
            $image = $_FILES['youtube_thumbnail']['tmp_name'];
            $path = 'upload/'.$channel_image;
            copy($image, $path);
        } else {
            $channel_image = '';
        }

        $user_agent = clean('default');

        function youtube_id_from_url($url) {

            $pattern = 
            '%^# Match any youtube URL
            (?:https?://)?  # Optional scheme. Either http or https
            (?:www\.)?      # Optional www subdomain
            (?:             # Group host alternatives
                youtu\.be/    # Either youtu.be,
                | youtube\.com  # or youtube.com
                (?:           # Group path alternatives
                    /embed/     # Either /embed/
                    | /v/         # or /v/
                    | /watch\?v=  # or /watch\?v=
                )             # End path alternatives.
            )               # End host alternatives.
            ([\w-]{10,12})  # Allow 10-12 for 11 char youtube id.
            $%x'
            ;

            $result = preg_match($pattern, $url, $matches);

            if (false !== $result) {
                return $matches[1];
            }
            return false;

        }

        $video_id = youtube_id_from_url($_POST['youtube']);

    }

    $data = array(
        'category_id'           => clean($_POST['category_id']),         
        'channel_name'          => clean($_POST['channel_name']),
        'channel_url'           => $channel,                                    
        'video_id'              => $video_id,
        'channel_image'         => $channel_image,
        'channel_description'   => $_POST['channel_description'],
        'channel_type'          => clean($_POST['upload_type']),
        'user_agent'            => $user_agent
    );      

    $qry = insert('tbl_channel', $data);                                    
    
    $_SESSION['msg'] = 'Channel added successfully...';
    header( "Location:channel-add.php");
    exit;

}

$sql_category = "SELECT * FROM tbl_category";
$category_result = mysqli_query($connect, $sql_category);

?>

<script type="text/javascript">

    $(document).ready(function(e) {

        $("#upload_type").change(function() {
            var type = $("#upload_type").val();

            if (type == "YOUTUBE") {
                $("#direct_url").hide();
                $("#youtube").show();
                $("#embed").hide();
            }

            if (type == "URL") {
                $("#youtube").hide();
                $("#direct_url").show();
                $("#embed").hide();
            }

            if (type == "EMBED") {
                $("#youtube").hide();
                $("#direct_url").hide();
                $("#embed").show();
            }
            
        });

        $( window ).load(function() {
            var type=$("#upload_type").val();

            if (type == "YOUTUBE")  {
                $("#direct_url").hide();
                $("#youtube").show();
                $("#embed").hide();
            }

            if (type == "URL") {
                $("#youtube").hide();
                $("#direct_url").show();
                $("#embed").hide();
            }

            if (type == "EMBED") {
                $("#youtube").hide();
                $("#direct_url").hide();
                $("#embed").show();
            }

        });

    });

</script>

<script type="text/javascript">

    $(document).ready(function(e) {

        $("#user_agent_type").change(function() {
            var type = $("#user_agent_type").val();

            if (type == "DEFAULT") {
                $("#default").show();
                $("#custom").hide();
            }

            if (type == "CUSTOM") {
                $("#default").hide();
                $("#custom").show();
            }
            
        });

        $( window ).load(function() {
            var type=$("#user_agent_type").val();

            if (type == "DEFAULT")  {
                $("#default").show();
                $("#custom").hide();
            }

            if (type == "CUSTOM") {
                $("#default").hide();
                $("#custom").show();
            }

        });

    });

</script>

<section class="content">

	<ol class="breadcrumb">
		<li><a href="dashboard.php">Dashboard</a></li>
		<li><a href="channel.php">Manage Channel</a></li>
		<li class="active">Add Channel</a></li>
	</ol>

	<div class="container-fluid">

		<div class="row clearfix">
			<div class="col-lg-12 col-md-12 col-sm-12 col-xs-12">

				<form id="form_validation" method="post" enctype="multipart/form-data">
					<div class="card corner-radius">
						<div class="header">
							<h2>ADD CHANNEL</h2>
						</div>
						<div class="body">

							<?php if(isset($_SESSION['msg'])) { ?>
							<div class='alert alert-info alert-dismissible corner-radius' role='alert'>
								<button type="button" class="close" data-dismiss="alert" aria-label="Close"><span aria-hidden="true">&times;</span>&nbsp;&nbsp;</button>
								<?php echo $_SESSION['msg']; ?>
							</div>
							<?php unset($_SESSION['msg']); } ?>                            

                            <div class="row clearfix">
                                
                                <div class="col-sm-5">

                                    <div class="form-group">
                                        <div class="font-12">Channel Name *</div>
                                        <div class="form-line">
                                            <input type="text" class="form-control" name="channel_name" id="channel_name" placeholder="Channel Name" required>
                                        </div>
                                    </div>

                                    <div class="form-group">
                                        <div class="font-12">Category *</div>
                                        <select class="form-control show-tick" name="category_id" id="category_id">
                                            <?php while ($data = mysqli_fetch_array ($category_result)) { ?>
                                            <option value="<?php echo $data['cid'];?>"><?php echo $data['category_name'];?></option>
                                            <?php } ?>
                                        </select>
                                    </div>

                                    <div class="form-group">
                                        <div class="font-12">Channel Source *</div>
                                        <select class="form-control show-tick" name="upload_type" id="upload_type">
                                            <option value="URL">Streaming Url</option>
                                            <option value="YOUTUBE">YouTube</option>
                                            <option value="EMBED">Embed</option>
                                        </select>
                                    </div>

                                    <div id="youtube">
                                        <div class="font-12 ex1">Optional YouTube Thumbnail ( jpg / png )</div>
                                        <div class="form-group">
                                            <input type="file" name="youtube_thumbnail" id="youtube_thumbnail" class="dropify-image" data-max-file-size="3M" data-allowed-file-extensions="jpg jpeg png gif"/>
                                            <div class="help-info pull-left">If the thumbnail image is empty, it will take from the default thumbnail on YouTube</div><br>
                                        </div>

                                        <div class="form-group">
                                            <div class="font-12">Youtube URL</div>
                                            <div class="form-line">
                                                <input type="url" class="form-control" name="youtube" id="youtube" placeholder="https://www.youtube.com/watch?v=33F5DJw3aiU" required>
                                            </div>
                                            <div class="font-12"></div>
                                        </div>
                                    </div>

                                    <div id="direct_url">

                                        <div class="font-12 ex1">Channel Image ( jpg / png ) *</div>
                                        <div class="form-group">
                                            <input type="file" name="channel_image" id="channel_image" class="dropify-image" data-max-file-size="3M" data-allowed-file-extensions="jpg jpeg png gif webp bmp" required/>
                                        </div>

                                        <div class="form-group">
                                            <div class="font-12">Channel URL</div>
                                            <div class="form-line">
                                                <input type="text" class="form-control" name="channel_url" id="channel_url" placeholder="http://live.metube.id/tv/channel2000022/index512.m3u8" required/>
                                            </div>
                                        </div>

                                        <div class="form-group">
                                            <div class="font-12">User Agent</div>
                                            <select class="form-control show-tick" name="user_agent_type" id="user_agent_type">
                                                <option value="DEFAULT">Default</option>
                                                <option value="CUSTOM">Custom</option>
                                            </select>
                                        </div>

                                        <div id="default">
                                            <input type="hidden" class="form-control" name="user_agent" id="user_agent" value="default" required/>
                                        </div>

                                        <div id="custom">
                                            <div class="form-group">
                                                <div class="font-12">Custom User Agent</div>
                                                <div class="form-line">
                                                    <input type="text" class="form-control" name="user_agent" id="user_agent" placeholder="Mozilla/5.0 (Linux; Tizen 2.3) AppleWebKit/538.1 (KHTML, like Gecko)Version/2.3 TV Safari/538.1" required/>
                                                </div>
                                            </div>
                                        </div> 

                                    </div>

                                    <div id="embed">

                                        <div class="font-12 ex1">Channel Image ( jpg / png ) *</div>
                                        <div class="form-group">
                                            <input type="file" name="embed_image" id="embed_image" class="dropify-image" data-max-file-size="3M" data-allowed-file-extensions="jpg jpeg png gif webp bmp" required/>
                                        </div>

                                        <div class="form-group">
                                            <div class="font-12">Embed URL</div>
                                            <div class="form-line">
                                                <input type="text" class="form-control" name="embed_url" id="embed_url" placeholder="https://www.youtube.com/embed/sVPYIRF9RCQ" required/>
                                            </div>
                                        </div>

                                    </div>                           

                                </div>

                                <div class="col-sm-7">
                                    <div class="font-12 ex1">Description *</div>
                                    <div class="form-group">
                                        <textarea class="form-control" name="channel_description" id="channel_description" class="form-control" cols="60" rows="10" required></textarea>

                                        <?php if ($ENABLE_RTL_MODE == 'true') { ?>
                                        <script>                             
                                            CKEDITOR.replace( 'channel_description' );
                                            CKEDITOR.config.contentsLangDirection = 'rtl';
                                            CKEDITOR.config.height = 338;
                                        </script>
                                        <?php } else { ?>
                                        <script>                             
                                            CKEDITOR.replace( 'channel_description' );
                                            CKEDITOR.config.height = 338;
                                        </script>
                                        <?php } ?>
                                    </div>

                                    <button type="submit" name="<?php if (validateResourceAccess($inputToken)) { echo 'submit'; } else { echo 'validate'; } ?>" class="button button-rounded waves-effect waves-float pull-right">SUBMIT</button>
                                    
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