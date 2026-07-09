<?php include_once('includes/header.php'); ?>

<?php

	error_reporting(0);

    // delete selected records
    if(isset($_POST['submit'])) {

        $arr = $_POST['chk_id'];
        $count = count((array)$arr);
        if ($count > 0) {
            foreach ($arr as $nid) {

                $sql_image = "SELECT channel_type, channel_image, channel_url FROM tbl_channel WHERE id = $nid";
                $img_results = $connect->query($sql_image);

                $sql_delete = "DELETE FROM tbl_channel WHERE id = $nid";
                $delete = $connect->query($sql_delete);

                if ($delete) {
                    while ($row = mysqli_fetch_assoc($img_results)) {
                    	if ($row['channel_type'] == 'URL') {
                    		unlink('upload/' . $row['channel_image']);
                    	} else {
							if ($row['channel_image'] != '') {
								unlink('upload/' . $row['channel_image']);
							} else {
								//do nothing
							}
						}
                    }
                    $_SESSION['msg'] = "$count Selected channels deleted";
                } else {
                    $_SESSION['msg'] = "Error deleting record";
                }

            }
        } else {
            $_SESSION['msg'] = "Whoops! no channels selected to delete";
        }
        header("Location:channel.php");
        exit;
    }

	if (isset($_REQUEST['keyword']) && $_REQUEST['keyword']<>"") {
		$keyword = $_REQUEST['keyword'];
		$reload = "channel.php";
		$sql =  "SELECT w.*, c.category_name FROM tbl_channel w, tbl_category c WHERE w.category_id = c.cid AND w.channel_name LIKE '%$keyword%'";
		$result = $connect->query($sql);
	} else {
		$reload = "channel.php";
		$sql =  "SELECT w.*, c.category_name FROM tbl_channel w, tbl_category c WHERE w.category_id = c.cid ORDER BY w.id DESC";
		$result = $connect->query($sql);
	}

	$rpp = $postPerPage;
	$page = intval($_GET["page"]);
	if($page <= 0) $page = 1;  
	$tcount = mysqli_num_rows($result);
	$tpages = ($tcount) ? ceil($tcount / $rpp) : 1;
	$count = 0;
	$i = ($page-1) * $rpp;
	$no_urut = ($page-1) * $rpp;

	//if (isset($_SERVER['HTTPS']) && $_SERVER['HTTPS'] === 'on') { $url = "https://"; } else { $url = "http://"; }  
    //$url.= $_SERVER['HTTP_HOST'];  
    $url = $_SERVER['REQUEST_URI'];
    $exploded = explode('/', $url);


    if (isset($_GET['page']) && isset($_GET['disable'])) {
    	if (validateResourceAccess($inputToken)) {
			$data = array('channel_status' => '0');	
			$update = update('tbl_channel', $data, "WHERE id = '".$_GET['disable']."'");
			if ($update > 0) {
		        $_SESSION['msg'] = "Channel successfully disabled";
		        header('Location:channel.php?page='.$_GET['page']);
				exit;
			}
		} else {
			systemUpdateFlag($invalidResponse);
		}
    }

    if (isset($_GET['page']) && isset($_GET['enable'])) {
    	if (validateResourceAccess($inputToken)) {
			$data = array('channel_status' => '1');	
			$update = update('tbl_channel', $data, "WHERE id = '".$_GET['enable']."'");
			if ($update > 0) {
		        $_SESSION['msg'] = "Channel successfully enabled";
		        header("Location:channel.php?page=".$_GET['page']);
				exit;
			}
		} else {
			systemUpdateFlag($invalidResponse);
		}
    }

    if(isset($_POST['jump_to_page'])) {
    	$pageNumber = clean($_POST['page_number']); 
		header('Location:channel.php?page='.$pageNumber);
		exit;
    }

?>  

<section class="content">

	<ol class="breadcrumb">
		<li><a href="dashboard.php">Dashboard</a></li>
		<li class="active">Manage Channel</a></li>
	</ol>

	<div class="container-fluid">

		<div class="row clearfix">
			<div class="col-lg-12 col-md-12 col-sm-12 col-xs-12">
				<div class="card corner-radius">
					<div class="header">
						<h2>MANAGE CHANNEL</h2>
						<div class="header-dropdown m-r--5">
							<a href="channel-add.php"><button type="button" class="button button-rounded btn-offset waves-effect waves-float">ADD NEW CHANNEL</button></a>
						</div>
					</div>

					<div style="margin-top: -10px;" class="body table-responsive">

						<?php if(isset($_SESSION['msg'])) { ?>
						<div class='alert alert-info alert-dismissible corner-radius bottom-offset' role='alert'>
							<button type="button" class="close" data-dismiss="alert" aria-label="Close"><span aria-hidden="true">&times;</span>&nbsp;&nbsp;</button>
							<?php echo $_SESSION['msg']; ?>
						</div>
						<?php unset($_SESSION['msg']); } ?>

						<form method="get" id="form_validation">
							<table class='table'>
								<tr>
									<td>
										<div class="form-group form-float">
											<div class="form-line">
												<input type="text" class="form-control" name="keyword" placeholder="Search..." required>
											</div>
										</div>
									</td>
									<td width="1%"><a href="channel.php"><button type="button" class="button button-rounded waves-effect waves-float">RESET</button></a></td>
									<td width="1%"><button type="submit" class="btn bg-blue btn-circle waves-effect waves-circle waves-float"><i class="material-icons">search</i></button></td>
								</tr>
							</table>
						</form>

						<?php if ($tcount == 0) { ?>
							<p align="center" style="font-size: 110%;">There are no channels.</p>
						<?php } else { ?>

						<form method="post" action="">

							<div style="margin-left: 8px; margin-top: -36px; margin-bottom: 10px;">
								<button type="submit" name="<?php if (validateResourceAccess($inputToken)) { echo 'submit'; } else { echo 'validate'; } ?>" id="submit" class="button button-rounded waves-effect waves-float" onclick="return confirm('Are you sure want to delete all selected channels?')">Delete selected items(s)</button>&nbsp;&nbsp;
								<a href="" data-toggle="modal" data-target="#modal-jump-to-page"><button type="button" class="button button-rounded waves-effect waves-float">Jump to Page</button></a>
							</div>				

							<table class='table table-hover table-striped'>
								<thead>
									<tr>
										<th width="1%">
											<div class="demo-checkbox" style="margin-bottom: -15px">
												<input id="chk_all" name="chk_all" type="checkbox" class="filled-in chk-col-blue" />
												<label for="chk_all"></label>
											</div>
										</th>
										<th width="39%">Channel Name</th>
										<th width="5%">Image</th>
										<th width="15%"><center>Category</center></th>
										<th width="10%"><center>Type</center></th>
										<th width="5%"><center>Views</center></th>
										<th width="20%"><center>Action</center></th>
									</tr>
								</thead>
								<?php
								while(($count < $rpp) && ($i < $tcount)) {
									mysqli_data_seek($result, $i);
									$data = mysqli_fetch_array($result);
									?>
									<tr>

										<td style="vertical-align: middle;" width="1%">
											<div class="demo-checkbox" style="margin-top: 13px;">
												<input type="checkbox" name="chk_id[]" id="<?php echo $data['id'];?>" class="chkbox filled-in chk-col-blue" value="<?php echo $data['id'];?>"/>
					                            <label for="<?php echo $data['id'];?>"></label>
											</div>
										</td>

										<td style="vertical-align: middle;">
											<?php if ($data['channel_status'] == '1') { echo $data['channel_name']; } else { echo '<strike>'.$data['channel_name'].'</strike>'; } ?>
										</td>

										<td>
											<?php
											if ($data['channel_type'] == 'YOUTUBE') { ?>
												<?php if ($data['channel_image'] != '') { ?>
													<img class="<?php if ($data['channel_status'] == '1') { echo 'img-rounded';} else {echo 'img-rounded img-grayscale';} ?>" style="object-fit:cover;" src="upload/<?php echo $data['channel_image'];?>" height="60px" width="60px"/>
												<?php } else { ?>
													<img class="<?php if ($data['channel_status'] == '1') { echo 'img-rounded';} else {echo 'img-rounded img-grayscale';} ?>" style="object-fit:cover;" src="https://img.youtube.com/vi/<?php echo $data['video_id'];?>/mqdefault.jpg" height="60px" width="60px"/>
												<?php } ?>
											<?php } else { ?>
												<img class="<?php if ($data['channel_status'] == '1') { echo 'img-rounded';} else {echo 'img-rounded img-grayscale';} ?>" style="object-fit:cover;" src="upload/<?php echo $data['channel_image'];?>" height="60px" width="60px"/>
											<?php } ?>
										</td>

										<td style="vertical-align: middle;">
											<center>
												<?php if ($data['channel_status'] == '1') { echo $data['category_name']; } else { echo '<strike>'.$data['category_name'].'</strike>'; } ?>
											</center>
										</td>
										<td style="vertical-align: middle;">
											<center>
												<?php if ($data['channel_type'] == 'YOUTUBE') { ?>
												<span class="label label-rounded bg-red">YOUTUBE</span>
												<?php } else if ($data['channel_type'] == 'URL') { ?>
												<span class="label label-rounded bg-green">URL</span>
												<?php } else if ($data['channel_type'] == 'EMBED') { ?>
												<span class="label label-rounded bg-orange">EMBED</span>
												<?php } else { ?>
												<span class="label label-rounded bg-black">UNKNOWN</span>
												<?php } ?>
											</center>
										</td>

										<td style="vertical-align: middle;">
											<center>
												<?php if ($data['channel_status'] == '1') { echo numberFormatter($data['total_views']); } else { echo numberFormatterDisabled($data['total_views']); } ?>
											</center>
										</td>

										<td style="vertical-align: middle;"><center>

											<?php if ($data['channel_status'] == '1') { ?>
											<a href="channel.php?page=<?php echo $page; ?>&disable=<?php echo $data['id'];?>" onclick="return confirm('Are you sure want to disable this channel?')">
												<i class="material-icons">visibility</i>
											</a>
											<?php } else { ?>
											<a href="channel.php?page=<?php echo $page; ?>&enable=<?php echo $data['id'];?>" onclick="return confirm('Are you sure want to enable this channel?')">
												<i class="material-icons">visibility_off</i>
											</a>
											<?php } ?>

											<a href="channel-send.php?id=<?php echo $data['id'];?>">
												<i class="material-icons">notifications_active</i>
											</a>

											<a href="channel-edit.php?id=<?php echo $data['id'];?>">
												<i class="material-icons">mode_edit</i>
											</a>

											<a href="channel-delete.php?id=<?php echo $data['id'];?>" onclick="return confirm('Are you sure want to delete this channel?')" >
												<i class="material-icons">delete</i>
											</a></center>
										</td>
									</tr>
									<?php
									$i++; 
									$count++;
								}
								?>
							</table>

						</form>

						<?php } ?>

						<?php if ($tcount > $postPerPage) { echo pagination($reload, $page, $keyword, $tpages); } ?>
					</div>

				</div>
			</div>
		</div>
	</div>
</section>

<div style="margin-top: 20%;" class="modal fade" id="modal-jump-to-page" tabindex="-1" role="dialog">
    <div class="modal-dialog modal-md" role="document">
        <form method="post" id="form_validation">
	        <div class="modal-content">
	            <div class="modal-header">
	                <h4 class="modal-title" id="largeModalLabel">Jump to Page</h4>
	            </div>
	            <div class="modal-body">
	            	<div class="form-group">
	            		<div class="form-line">
	            			<div class="font-12"><b>Input Page Number</b></div>
	            			<input type="number" class="form-control" name="page_number" id="page_number" min="1" max="<?php echo $tpages; ?>" required>
	            		</div>
	            		<div class="help-info pull-left">Page number between ( 1 - <?php echo $tpages; ?> )</div>
	            	</div>
	            </div>
	            <div class="modal-footer">
	                <button type="submit" name="jump_to_page" class="btn btn-link waves-effect">GO</button>
	            </div>
	        </div>
	    </form>
    </div>
</div>

<?php include_once('includes/footer.php'); ?>