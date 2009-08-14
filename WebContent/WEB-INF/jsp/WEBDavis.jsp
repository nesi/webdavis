<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
	<meta http-equiv="Content-Type" content="text/html; charset=utf-8">
	<title><%=request.getAttribute("authenticationrealm")%> - Start Page - <%=request.getAttribute("organisationname")%></title>
	<link href="http://www.arcs.org.au/favicon.ico" type="image/x-icon" rel="shortcut icon"/>
    <meta HTTP-EQUIV="Pragma" CONTENT="no-cache">
    <meta HTTP-EQUIV="Cache-Control" CONTENT="no-cache">
    <meta HTTP-EQUIV="Expires" CONTENT="0">
    <style type="text/css">
		@import "<%=request.getAttribute("dojoroot")%>dojoroot/dijit/themes/tundra/tundra.css";
		@import "<%=request.getAttribute("dojoroot")%>dojoroot/dojox/grid/resources/Grid.css";
		@import "<%=request.getAttribute("dojoroot")%>dojoroot/dojox/grid/resources/tundraGrid.css";
		@import "<%=request.getAttribute("dojoroot")%>dojoroot/dojo/resources/dojo.css"
   	</style>
	<style>
		.text{font-family:Arial, Helvetica, sans-serif;}
		.text_small{font-family:Arial, Helvetica, sans-serif; font-size:small;}
		h1{margin:0px;}
		#button_table{margin: 0px 0px 0px -4px;}
		#mode_buttons{margin: -2px 2px 0px 0px;}
		#header{margin: 0px 0px -12px 0px;}
		#hor_rule{margin: 0px -12px -12px 0px;}
		#table.HoverGreen {background:#509C52; color:#FFF;}
		#table.HoverGrey {background:#CCC; color:#FFF; border:1px solid #CCC;}
<!--		.hoverBorderSide {margin: 2px; border: 1px solid #CCC;}-->
		.link{text-decoration:none; color:#04E;}
		.darklink{text-decoration:none; color:black;}
<!--		.linkOver{text-decoration:underline;}-->
		.link:hover{text-decoration:underline; color:#04E;}
		.darklink:hover{text-decoration:underline; color:black;}
		.activeItem{background:#CCC; color:black;}
		.activeItem:hover{background:#509C52; color:#FFF;}
   		.dojoxGrid-row-odd td {background:#e8f2ff;}
    	#metadataGrid {border: 1px solid #333; width: 400px; height: 300px;}
		#permissionGrid {border: 1px solid #333; width: 600px; height: 200px;}
	</style>	
	<script type="text/javascript" src="<%=request.getAttribute("dojoroot")%>dojoroot/dojo/dojo.js" djConfig="isDebug: false, parseOnLoad: true"></script>
	<script type="text/javascript">
    	dojo.require("dojox.grid.compat.Grid");
    	dojo.require("dojox.grid.DataGrid");
		dojo.require("dojox.grid.compat._grid.edit");
    	dojo.require("dojo.data.ItemFileWriteStore");
    	dojo.require("dojo.data.ItemFileReadStore");
    	dojo.require("dijit.form.Button");
    	dojo.require("dijit.Dialog");
		dojo.require("dijit.form.TextBox");
		dojo.require("dojo.parser");
		dojo.require("dijit.form.FilteringSelect");
		dojo.require("dojo.io.iframe");
		dojo.require("dijit.ProgressBar");
		dojo.addOnLoad(function() {
			setCurrentDirectory("<%=request.getAttribute("url")%>");
			fetchDirectory(getCurrentDirectory());
		});

		var currentDirectory = "";
	    var metadataLayout= [{
				defaultCell: {editable: true, type: dojox.grid.cells._Widget, styles: 'text-align: left;'},
				rows: [
        			{field: "name", width: "150px", name: "Name", editable: true},
        			{field: "value", width: "auto", name: "Value", editable: true}
        			<% if (request.getAttribute("servertype").equals("irods")) { %>
        				,{field: "unit", width: "50px", name: "Unit", editable: true}
        			<% } %>
        		]}];

    	var permissionsLayout= [
        		{field: "username", width: "200px", name: "Username"},
        		<% if (request.getAttribute("servertype").equals("srb")) { %>
        			{field: "domain", width: "200px", name: "Domain"},
       			<% } %>
        		{field: "permission", width: "auto", name: "Permission"}
    		];
    
    	var layoutReplicas= [
    			{field: "resource", width: "150px", name: "Resource"},
     			{field: "number", width: "auto", name: "Replica Number"}
    		];
    
		var metadataStore = new dojo.data.ItemFileWriteStore({data: {items:[]}});
		var permissionsStore = new dojo.data.ItemFileWriteStore({data: {items:[]}});
		var domainsStore = new dojo.data.ItemFileWriteStore({data: {items:[]}});
		var usersStore = new dojo.data.ItemFileWriteStore({data: {items:[]}});
		var replicasStore = new dojo.data.ItemFileWriteStore({data: {items:[]}});
		var server_url;
		var ori_url; // Used in getDomains for getting user list
		var uploadInProgress = false;

		function errorMessage(method, messageObject) {			
			var code = messageObject.status;
			var message = messageObject.message;
			if (code < 0)
				return message;
			switch(code) {
				case 0:		switch(method) {
								case "replicas": return "replicate failed";
								default: return message;
							}
				case 403:	switch(method) {
								//case "move": 
								//case "copy": return "source and destination URI are the same";
								default: return "refused";
						  	}
				case 405:	return "path already exists";
				case 412: 	return "destination already exists";
				default: 	return message;
			}
		}
		
		var cursorCount = 0;	// Depth of cursor busy requests

		// Set cursor to given style. Force will cause change regardless of requests depth
		function setCursor(busy, force) {
			document.body.style.cursor = busy ? 'wait' : 'default';
			if (force)
				cursorCount = 0;
		}
		
		function cursorBusy(busy) {
			if (busy) {
				cursorCount++
				setCursor(true, false);
			} else {
				cursorCount--
				if (cursorCount < 0)
					cursorCount = 0;
				if (cursorCount == 0)
					setCursor(false, false);
			}
		}
		
		multipliers = [' bytes', 'K', 'M', 'G'];
   
     	function humanReadable(n) { 	
    		var multiplier = 0;
    		while (true) {
    			if (Math.round(n) < 1000)
    				break;
    			n = n/1024;
    			multiplier++;
    			if (multiplier >= 3)
    				break;
    		}
    		if ((multiplier == 0) && n < 1000)
    			return ""+Math.floor(n)+multipliers[0];
    		if (n < 10) {
				var m = multipliers[multiplier];
     			return Math.round(n*10)/10.0+m;
			}
	   		return Math.round(n)+multipliers[multiplier];
    	}

		function toBreadcrumb(path) {
			var result = "";
			var subpath = "";
			var dirs = path.split('/');
			for (var i = 1; i < dirs.length; i++) {
				subpath = subpath+'/'+dirs[i];
				if (i > 1)
					result = result + " > ";
				result = result+'<a class="link" onclick="gotoDirectory(\''+subpath+'\'); return false" href="'+subpath+'">'+dirs[i]+'</a>';
			}
			return result;
		}

		function setCurrentDirectory(dir) {
			currentDirectory = dir;
		}

		function getCurrentDirectory() {
			return currentDirectory;
		}
		
		function displayDirectory(files) {
			var listing =	
						'<tr class="text">'+
        					'<td width="50%" bgcolor="#E8E8E8"><strong>File/Directory Name</strong></td>'+
        					'<td align="center" bgcolor="#E8E8E8"><strong>Last Modified</strong></td>'+
        					'<td align="center" bgcolor="#E8E8E8"><strong>Size</strong></td>'+
        					'<td width="12%" align="right" bgcolor="#E8E8E8"><strong>Select</strong></td>'+
      					'</tr>'+
      					'<tr>'+
        					'<td bgcolor="#FFFFFF" class="text"><a class="link" onclick="gotoParentDirectory(); return false" href="'+getCurrentDirectory()+'/..">... Parent Directory</td>'+
        					'<td bgcolor="#FFFFFF">&nbsp;</td>'+
        					'<td align="right" bgcolor="#FFFFFF">&nbsp;</td>'+
      					'</tr>';
      		if (files != null) {
          		if (files.length == 0) {
              			listing = listing +
              			'<tr><td class="text"><small><i>(Directory is empty)</i></small></td></tr>';
          		} else 
					for (var i = 0; i < files.length; i++) {
						listing = listing +
						'<tr>'+
						'<td bgcolor="#FFFFFF">'
						var string = "";
						if (files[i].type == 'd') 
							listing = listing +
								'<img src="/images/folder.gif" width="16" height="16"/><a class="darklink" onclick="gotoDirectory(getCurrentDirectory()+\'/'+files[i].name+'\'); return false" href="'+getCurrentDirectory()+'/'+files[i].name+'">&nbsp;'+files[i].name;
						else 
							listing = listing +
								'<img src="/images/page.gif" width="16" height="16"/><a class="darklink" href="'+getCurrentDirectory()+'/'+files[i].name+'">&nbsp;'+files[i].name;
						listing = listing +
							'</td>'+
    						'<td align="right" bgcolor="#FFFFFF" class="text"><small><small>'+files[i].date+'</small></small></td>'+
   							'<td align="right" bgcolor="#FFFFFF" class="text"><small>';
 						if (files[i].type != 'd') 
							listing = listing +
   								humanReadable(files[i].size);
						listing = listing +
								'</small></td>'+
    						'<td align="right" bgcolor="#FFFFFF"><input type="checkbox" name="checkbox" id="checkbox'+i+'" /></td>'+
  						'</tr>';
					}
      		}
      		dojo.html.set(dojo.byId("directoryTable"), listing);
			dojo.html.set(dojo.byId("breadcrumb"), toBreadcrumb(getCurrentDirectory()));
		}

		var browserBusy = false; // Indicates browser is busy 
		
		function fetchDirectory(url) {
			//alert("fetchdirectory "+url);			
			displayDirectory(null);
			broswerBusy = true;
			cursorBusy(true);
			dojo.xhr("GET", {
				url: url+"?format=json",
				preventCache: true,
	    		handleAs: "json",
			    load: function(responseObject, ioArgs){
			    	displayDirectory(responseObject.items);
			    	cursorBusy(false);
			    	browserBusy = false;
			      	return responseObject;
			    },
	    		error: function(response, ioArgs){
	      			alert("Failed to fetch directory details for "+url+": "+errorMessage("get", response));
	      			cursorBusy(false);
	      			browserBusy = false;
	      			return response;
	    		}
			});
		}

		function gotoDirectory(url) {
			if (browserBusy)
				return;
			setCurrentDirectory(url);
			fetchDirectory(getCurrentDirectory());
		}

		function refreshCurrentDirectory() {
			fetchDirectory(getCurrentDirectory());
		}

		function gotoParentDirectory() {
			var parent = getCurrentDirectory();
			var i = parent.lastIndexOf("/");
			if (i >= 0)
				parent = parent.substring(0, i);
			gotoDirectory(parent);
		}

		function createDirectory(){
			dijit.byId('dialogCreateDir').hide();
			var dirName = dojo.byId('directoryInputBox').value;
			
			dojo.xhr("MKCOL", {
				url: getCurrentDirectory()+"/"+dirName,
			    load: function(responseObject, ioArgs){
			      	refreshCurrentDirectory();
			      	return responseObject;
			    },
	    		error: function(response, ioArgs){
	      			alert("Failed to create directory "+dirName+": "+errorMessage("mkcol", response));
	      			return response;
	    		}
			});
		}
	</script>
</head>

<body class="tundra">
<!-- Dialogs-->
<div dojoType="dijit.Dialog" id="dialogCreateDir" title="Create Directory">
	New directory name:
	<input name="directory" id="directoryInputBox" dojoType="dijit.form.TextBox" trim="true" value=""/>
	<br/><br/>
	<div style="text-align: right;">
		<button onclick="createDirectory()">Create</button>
		<button onclick="dijit.byId('dialogCreateDir').hide()">Cancel</button>
	</div>
</div>		

<!-- Main body -->			
<table id="header" width="100%" border="0" cellspacing="0" cellpadding="12">
	<tr>
    	<td valign="bottom">
			<h1 class="text"><img src="/images/logo.jpg" width="28" height="30" />&nbsp;<%=request.getAttribute("authenticationrealm")%></h1>
		</td>
    	<!-- <td align="right" valign="bottom"> -->
    	<td align="right" valign="top">
			<table border="0" cellspacing="0" cellpadding="6">
      			<tr class="text">
        			<td>You are logged in as &lt;<a class="link" onclick="gotoDirectory('<%=request.getAttribute("home")%>'); return false" href="<%=request.getAttribute("home")%>"><%=request.getAttribute("account")%></a>&gt;<!-- | <a class="link" href="">logout</a>--></td>
      			</tr>
    		</table>
      		<!-- <table id="mode_buttons" border="0" cellspacing="4" cellpadding="0">
        		<tr class="text">
          			<td>Choose mode&nbsp;</td>
          			<td bgcolor="#CCCCCC">
						<table class="hoverBorder" border="0" cellspacing="0" cellpadding="6">
            				<tr>
              					<td> Basic</td>
            				</tr>
      	    			</table>
					</td>
          			<td bgcolor="#EEEEEE">
						<table class="hoverBorder" border="0" cellspacing="0" cellpadding="6">
            				<tr>
              					<td> Advanced</td>
    	        			</tr>
        	  			</table>
					</td>
 		       	</tr>
    		</table> -->
		</td>
  	</tr>
</table>
<table id="hor_rule" width="100%" border="0" cellpadding="12" cellspacing="0">
	<tr>
    	<td>
			<table width="100%" border="0" cellpadding="0" cellspacing="0">
      			<tr>
        			<td bgcolor="#000000"><img src="/images/1px.png" alt="" width="1" height="1" /></td>
      			</tr>
    		</table>
		</td>
  	</tr>
</table>
<table width="80%" border="0" cellpadding="6" cellspacing="4">
	<tr>
    	<td valign="bottom">
			<p class="text">You are browsing:</p>
      		<p class="text" id="breadcrumb">
			</p>
      		<table id="button_table" border="0" cellspacing="4" cellpadding="0">
        		<tr class="text">
          			<td bgcolor="#CCCCCC">
						<table class="activeItem" border="0" cellspacing="0" cellpadding="6">
             				<tr>
                				<td onclick='alert("not implemented yet")'><strong><img src="/images/arrow_up.gif" alt="" width="16" height="16" />&nbsp;Upload File</strong></td>
              				</tr>
          				</table>
					</td>
          			<td bgcolor="#CCCCCC">
						<table class="activeItem" border="0" cellspacing="0" cellpadding="6">
            				<tr>
              					<td onclick="dijit.byId('dialogCreateDir').show()"><strong><img src="/images/folder_new.gif" alt="" width="16" height="16" />&nbsp;Create Directory</strong></td>
            				</tr>
          				</table>
					</td>
        		</tr>
      		</table>
		</td>
    	<td align="right" valign="bottom">
			<p>&nbsp;</p>
      		<p>&nbsp;</p>
      		<form id="form1" name="form1" method="post" action="">
        		<table border="0" cellspacing="2" cellpadding="6">
          			<tr>
            			<td>
							<!-- <input name="search" type="text" id="search" size="30" />
            				<input type="submit" name="search_btn" id="search_btn" value="Search" />-->
						</td>
          			</tr>
        		</table>
    		</form>
		</td>
  	</tr>
</table>
<form id="form2" name="form2" method="post" action="">
	<table width="100%" border="0" cellspacing="0" cellpadding="12">
  		<tr>
    		<td width="80%" valign="top">
				<table id="directoryTable" width="100%" border="0" cellpadding="6" cellspacing="2">
	   			</table>
			</td>
    		<td valign="top">
				<table border="0" cellspacing="2" cellpadding="6">
      				<tr>
        				<td class="text">&nbsp;</td>
      				</tr>
    			</table>
      			<table border="0" cellspacing="2" cellpadding="6">
        			<tr>
          				<td class="text">&nbsp;</td>
          			</tr>
      			</table>
      			<table class="hoverBorderSide" border="0" cellspacing="0" cellpadding="6">
        			<tr>
          				<td class="text">Access Control</td>
        			</tr>
      			</table>
      			<table class="hoverBorderSide" border="0" cellspacing="0" cellpadding="6">
       				<tr>
          				<td class="text">Meta Data</td>
        			</tr>
    			</table>
      			<table class="hoverBorderSide" border="0" cellspacing="0" cellpadding="6">
       				<tr>
          				<td class="text">Rename</td>
        			</tr>
    			</table>
      			<table class="hoverBorderSide" border="0" cellspacing="0" cellpadding="6">
        			<tr>
          				<td class="text">Delete</td>
        			</tr>
    			</table>
			</td>
  		</tr>
	</table>
</form>
<table id="hor_rule2" width="80%" border="0" cellpadding="12" cellspacing="0">
  	<tr>
    	<td>
			<table width="100%" border="0" cellpadding="0" cellspacing="0">
      			<tr>
        			<td bgcolor="#999999"><img src="/images/1px.png" alt="" width="1" height="1" /></td>
      			</tr>
    		</table>
		</td>
  	</tr>
</table>
<p>&nbsp;</p>
</body>
</html>
