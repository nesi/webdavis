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
		.hoverBorderSide {margin: 2px; border: 1px solid #CCC;}
		.link{text-decoration:none; color:#04E;}
		.darklink{text-decoration:none; color:black;}
		.linkOver{text-decoration:underline;}
		.link:hover{text-decoration:underline; color:#04E;}
		.darklink:hover{text-decoration:underline; color:black;}
		.activeItem{background:#AFCAAF; color:black; font-family:Arial, Helvetica, sans-serif; font-weight:bold;}
		.activeItem:hover{background:#509C52; color:#FFF;}
		.inactiveItem{background:#AFCAAF; color:#AFAFAF; font-family:Arial, Helvetica, sans-serif; font-weight:bold;}
		.invisible{background:white; color:white; border: 0px;}
		.linkImage{text-decoration:underline; color:#04E;}
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
			setToggleButtonState(true);
			refreshButtons();
			initConnects();
		});

		var currentDirectory = "";
	    var metadataLayout= [{
				defaultCell: {editable: true, type: dojox.grid.cells._Widget, styles: 'text-align: left;'},
				rows: [
        			{field: "name", width: "150px", name: "Name", editable: true},
        			{field: "value", width: "auto", name: "Value", editable: true}
        			<%if (request.getAttribute("servertype").equals("irods")) {%>
        				,{field: "unit", width: "50px", name: "Unit", editable: true}
        			<%}%>
        		]}];

    	var permissionsLayout= [
        		{field: "username", width: "200px", name: "Username"},
        		<%if (request.getAttribute("servertype").equals("srb")) {%>
        			{field: "domain", width: "200px", name: "Domain"},
       			<%}%>
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
		
		function resetPermissionsStore() {
			permissionsStore=new dojo.data.ItemFileWriteStore({data: {items:[]}});	
			permissionGrid.setStore(permissionsStore);	
		}
		
		function resetMetadataStore() {
			metadataStore=new dojo.data.ItemFileWriteStore({data: {items:[]}}); 
			metadataGrid.setStore(metadataStore);
		}
		
		function resetReplicasStore() { 
			replicasStore=new dojo.data.ItemFileWriteStore({data: {items:[]}});
			replicasGrid.setStore(replicasStore);  
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

		function initConnects() {
			dojo.connect(dojo.byId("directoryInputBox"), 'onkeypress', function(e) {
				if (e.keyCode == dojo.keys.ENTER) {
					createDirectory();
				}
			});
			dojo.connect(dojo.byId("renameInputBox"), 'onkeypress', function(e) {
				if (e.keyCode == dojo.keys.ENTER) {
					rename(getFirstCheckedItem(), dojo.byId('renameInputBox').value);
				}
			});
		}

		function isEnterKey(event) {
			return event && event.which == 13
		}

		function listCheckedItems() {
			var list="";
			for (var i = 1; i < document.childrenform.selections.length; i++) // Ignore dummy first element
				if (document.childrenform.selections[i].checked) 
				  list=list+"\""+trimMode(document.childrenform.selections[i].value)+"\",";
			if (list.length > 0)
				list = list.substring(0, list.length-1);  // remove trailing ','
			return list;
		}
		
		function listToJSON() {
			//json format is: [{"files":["file1","file2"...]}]
			return "[{\"files\":["+listCheckedItems()+"]}]";
		}

		function trimMode(value) {
			var i = value.indexOf(";");
			if (i >= 0)
				return value.substring(0,i);
			return value;
		}
		
		function getFirstCheckedItem() {
			for (var i = 1; i < document.childrenform.selections.length; i++) // Ignore dummy first element
				if (document.childrenform.selections[i].checked) 
				  return trimMode(document.childrenform.selections[i].value);
			return null;
		}
		
		function checkedItemsCount() {
			var count=0;
			for (var i = 1; i < document.childrenform.selections.length; i++) // Ignore dummy first element
				if (document.childrenform.selections[i].checked) 
					count++;
			return count;
		}
		
		function directoryIsChecked() {
			for (var i = 1; i < document.childrenform.selections.length; i++) // Ignore dummy first element
				if (document.childrenform.selections[i].checked) 
					if (document.childrenform.selections[i].value.indexOf(";directory") >= 0)
						return true;
			return false;
		}
		
		function toggleAll() {
			var allChecked = !getToggleButtonState();
			setToggleButtonState(allChecked);
			for (var i = 1; i < document.childrenform.selections.length; i++) // Ignore dummy first element
				document.childrenform.selections[i].checked = !allChecked;
		}
		
		function checkAll(state) {
			for (var i = 1; i < document.childrenform.selections.length; i++) // Ignore dummy first element
				document.childrenform.selections[i].checked = state;
		}

		function setToggleButtonState(state) {
			var s = state ? "Select all" : "Select none";
			dojo.byId("toggleAllButton").title = s;
			dojo.html.set(dojo.byId("toggleAllButton"), s);
		}
	
		function getToggleButtonState() {
			return dojo.byId('toggleAllButton').title == "Select all";
		}
	
		function enableActiveItem(item, enable) {
			dojo.byId(item).setAttribute('class', enable ? 'activeItem' : 'inactiveItem');
		}

		function activeItemIsEnabled(item) {
			return dojo.byId(item).getAttribute('class') == 'activeItem';
		}
		
		function refreshButtons() {
			enableActiveItem('renameButton', checkedItemsCount() == 1);
			enableActiveItem('replicasButton', checkedItemsCount() == 1);
			enableActiveItem('deleteButton', checkedItemsCount() != 0);
			enableActiveItem('metadataButton', checkedItemsCount() != 0);
			enableActiveItem('accessControlButton', checkedItemsCount() != 0);
			if (replicasGrid.selection.getSelectedCount() == 0) {
				dojo.byId('replicasDeleteButton').disabled = true;
				dojo.byId('replicasReplicateButton').disabled = true;
			} else {
				var item = replicasGrid.selection.getFirstSelected();
				var isReplica = replicasStore.getValue(item, "number") != null;
				dojo.byId('replicasDeleteButton').disabled = !isReplica;
				dojo.byId('replicasReplicateButton').disabled = isReplica;			
			}
			if (getCurrentDirectory().indexOf('<%=request.getAttribute("trash")%>') >= 0) {
				dojo.html.set(dojo.byId("trashEmpty"), 
          			'<table class="activeItem" border="0" cellspacing="0" cellpadding="6">'+
            		'	<tr>'+
              		'		<td onclick="checkAll(true); setToggleButtonState(!getToggleButtonState()); dijit.byId(\'dialogDelete\').show()"><strong><img src="/images/delete.png" alt="" width="16" height="16" />&nbsp;Empty Trash</strong></td>'+
            		'	</tr>'+
          			'</table>'
					);
				dojo.html.set(dojo.byId("trashRestoreRow"), 
	            	'<td class="activeItem" id="trashRestoreButton" onclick="if (!activeItemIsEnabled(\'trashRestoreButton\')) return; if (checkedItemsCount() > 0) dijit.byId(\'dialogRestore\').show()"/>Restore</strong></td>');
				dojo.byId("trashRestoreTable").setAttribute('class', 'hoverBorderSide');
			} else {
				dojo.html.set(dojo.byId("trashEmpty"), "");
				dojo.html.set(dojo.byId("trashRestoreRow"), "");
				dojo.byId("trashRestoreTable").setAttribute('class', 'invisible');
			}
			if (dojo.byId('trashRestoreButton') != null) 
				enableActiveItem('trashRestoreButton', checkedItemsCount() != 0);
				
			setToggleButtonState(getToggleButtonState());
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
    						'<td align="right" bgcolor="#FFFFFF"><input type="checkbox" name="selections" value="'+files[i].name+';directory" onClick="refreshButtons()"/></td>'+
  						'</tr>';
					}
      		}
      		dojo.html.set(dojo.byId("directoryTable"), listing);
			dojo.html.set(dojo.byId("breadcrumb"), toBreadcrumb(getCurrentDirectory()));
		}
		
		function fetchDirectory(url) {
			displayDirectory(null);
			cursorBusy(true);
			dojo.xhr("GET", {
				url: url+"?format=json",
				preventCache: true,
	    		handleAs: "json",
			    load: function(responseObject, ioArgs){
			    		displayDirectory(responseObject.items);
			    		cursorBusy(false);
			    		refreshButtons();
			      		return responseObject;
			    	},
	    		error: function(response, ioArgs){
	      				alert("Failed to fetch directory details for "+url+": "+errorMessage("get", response));
	      				cursorBusy(false);
	      				return response;
	    			}
			});
		}

		function gotoDirectory(url) {
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

		function uploadFile(){ 		
			dojo.byId('uploadStatusField').innerHTML = "Uploading...";
			uploadInProgress = true;
			dojo.io.iframe.send({
				url: getCurrentDirectory()+"?method=upload",
				method: "POST",
				form: "uploadForm",
				handleAs: "json",
				handle: function(response, ioArgs){
							dijit.byId('dialogUpload').hide();
							if (response.status == "success") {
								//alert("Successfully transferred "+response.message+" bytes");
								window.status="Successfully transferred "+response.message+" bytes";
								refreshCurrentDirectory();						
							}else 
								alert("Failed to upload file: "+response.message);
							uploadInProgress = false;
							return response;
						},
				error: function(response, ioArgs) {
							dijit.byId('dialogUpload').hide();
							alert("Failed to upload file: "+response);
							uploadInProgress = false;
							return response;
						}
			});
		}

		function uploadCancel() {
			if (!uploadInProgress)  
				dijit.byId('dialogUpload').hide();
			else {
				uploadInProgress = false;
				dojo.byId('uploadStatusField').innerHTML = "Canceling...";
				window.location.reload();	// Is this the only way to kill a transfer?
			}
		}

		function deleteFiles(url) {
			dijit.byId('dialogDelete').hide();
			if (checkedItemsCount() == 0)
				return;
			var data='[{"files":['+listCheckedItems()+']}]'; //json format is: [{"files":["file1","file2"...]}]		
			dojo.xhr("DELETE", {
				url: url,
				headers: {
			        "content-length": data.length,
			        "content-type": "text/x-json"
			    },
			    putData: data, // This can be specified as rawBody in dojo 1.4
			    load: function(responseObject, ioArgs){
			      		refreshCurrentDirectory();
			      		return responseObject;
			   		},
	    		error: function(response, ioArgs){
	      				alert("Failed to delete one or more items: "+errorMessage("delete", response));
	      				refreshCurrentDirectory(); // Reload on error in case some files were deleted
	      				return response;
	    			}
			}, true);
			checkAll(false);
		}	
			
		function rename(name, newName){
			dijit.byId('dialogRename').hide();
			var url = getCurrentDirectory()+'/'+name;
			var destination = getCurrentDirectory()+'/'+newName;
			renameFile(url, destination);
		}

		function renameFile(url, destination) {
			dojo.xhr("MOVE", {
				url: url,
				headers: {
			        "Destination": destination
			    },
			    load: function(responseObject, ioArgs){
			       		refreshCurrentDirectory();
			      		return responseObject;
			    	},
	    		error: function(response, ioArgs){
	      				alert("Failed to move items: "+errorMessage("move", response));
	      				refreshCurrentDirectory() // Reload on error in case file was deleted
	      				return response;
	    			}
			}, true);
			checkAll(false);
		}

		function restoreFiles() {
			dijit.byId('dialogRestore').hide();
			var destination = "<%=request.getAttribute("home")%>"+getCurrentDirectory().substr("<%=request.getAttribute("trash")%>".length, getCurrentDirectory().length);
			moveFiles(url, destination);
		}
		
		function moveFiles(url, destination) {
			if (checkedItemsCount() == 0)
				return;
			var data = '[{"files":['+listCheckedItems()+']}]'; //json format is: [{"files":["file1","file2"...]}]		
			dojo.xhr("MOVE", {
				url: url+"?destination="+destination,
				headers: {
			        "content-length": data.length,
			        "content-type": "text/x-json",
			        "Destination": destination
			    },
			    putData: data, // This can be specified as rawBody in dojo 1.4
			    load: function(responseObject, ioArgs){
			      	refreshCurrentDirectory();
			      	return responseObject;
			    },
	    		error: function(response, ioArgs){
	      			alert("Failed to move one or more items: "+errorMessage("move", response));
	      			refreshCurrentDirectory(); // Reload on error in case some files were deleted
	      			return response;
	    		}
			}, true);
			checkAll(false);
		}
		
		function loadMetadataFromServer(url){
		  	dojo.xhrPost({
	    		url: url,
	    		load: function(responseObject, ioArgs){	      
						metadataStore=new dojo.data.ItemFileWriteStore({data: responseObject});
						metadataGrid.setStore(metadataStore);	      			
	       				return responseObject;
	    			},
	    		error: function(response, ioArgs){
	      				alert("Error when loading metadata.");
	      				return response;
	    			},
	    		handleAs: "json"
	  		});
		}

		function getMetadata(batch){
			var metadataURL = "";
			if (batch) {
//				loadMetadataFromServer(getCurrentDirectory()+"?method=metadata");	// Default metadata is taken from current directory
				resetMetadataStore();
			} else {
				metadataURL = getCurrentDirectory()+"/"+getFirstCheckedItem()+"?method=metadata";
				loadMetadataFromServer(metadataURL);
			}
			dijit.byId('dialogMetadata').attr("url", metadataURL);
			dojo.byId('metadataRefreshButton').disabled = batch;	// Don't want the refresh button active in batch mode
			dijit.byId('dialogMetadata').show();
		}

		function refreshMetadata(url){
			loadMetadataFromServer(url);
		}

		function addMetadata(){
	        // set the properties for the new item:
	        <%if (request.getAttribute("servertype").equals("srb")) {%>
	        	var myNewItem = {name: "name", value: "value"};
	        <%} else {%>
	        	var myNewItem = {name: "name", value: "value", unit: "unit"};
			<%}%>
	        // Insert the new item into the store:
	        metadataStore.newItem(myNewItem);
		}
		
		function delMetadata(){
	        // Get all selected items from the Grid:
	        var items = metadataGrid.selection.getSelected();
	        if (items.length){
	            // Iterate through the list of selected items.
	            // The current item is available in the variable "selectedItem" within the following function:
	            dojo.forEach(items, function(selectedItem) {
	                if (selectedItem !== null) {
	                    // Delete the item from the data store:
	                    metadataStore.deleteItem(selectedItem);
	                }
	            }); 
	        } 
		}
		
		function saveMetadata(){
			if (checkedItemsCount() == 0)
				return;
			var rowCount = dijit.byId('metadataGrid').rowCount;

			//json format is: [{"files":["file1","file2"...],"metadata":[{"name":"foo","value":"bar"},{"name":"foo2","value":"bar2"}...]}]
			var data=listToJSON();
			data = data.substring(0, data.length-2);	// Trim '}]' off end 
			data += ",\"metadata\":[";
			for (var i = 0; i < rowCount; i++){
				if (i > 0) 
					data += ",";
		        <%if (request.getAttribute("servertype").equals("srb")) {%>
					data += "{\"name\":\""+dijit.byId('metadataGrid').getItem(i).name+"\", \"value\":\""+dijit.byId('metadataGrid').getItem(i).value+"\"}";
			    <%} else {%>
					data += "{\"name\":\""+dijit.byId('metadataGrid').getItem(i).name+"\", \"value\":\""+dijit.byId('metadataGrid').getItem(i).value+"\", \"unit\":\""+dijit.byId('metadataGrid').getItem(i).unit+"\"}";
				<%}%>
			}
			data += "]}]";
//			alert(dijit.byId('metadataGrid').getItem(0).name);
//			alert("data:"+data);
		  	dojo.rawXhrPost({
	    		url: getCurrentDirectory()+"?method=metadata",
			    headers: {
			        "content-length": data.length,
			        "content-type": "text/x-json"
			    },
			    postData: data,
	    		load: function(responseObject, ioArgs){
						metadataStore=new dojo.data.ItemFileWriteStore({data: responseObject});
						metadataGrid.setStore(metadataStore);	      			
	       				return responseObject;
	    			},
	    		error: function(response, ioArgs){
	      				alert("Error when loading metadata.");
	      				return response;
	    			},
	    		handleAs: "json"
	  		});
		}

		function showMetadata() {
			var checkedCount = checkedItemsCount();
			if (checkedCount == 0)
				return;
			getMetadata(checkedCount > 1);				
		}

		function getDomains(url){
		  	dojo.xhrPost({
	    		url: url,
	    		load: function(responseObject, ioArgs){
					domainsStore = new dojo.data.ItemFileWriteStore({data: responseObject});
					var formDomainObj = dijit.byId('formDomain');
					formDomainObj.store = domainsStore;
					formDomainObj.onChange = function(val){
						dijit.byId('formUsername').textbox.value = "";
						dijit.byId('formUsername').valueNode.value = "";
						getUsers(getCurrentDirectory()+"?method=userlist&domain="+formDomainObj.item.name);
					}
	       			return responseObject;
	    		},
	    		error: function(response, ioArgs){
	      			alert("Error when loading domain list.");
	      			return response;
	    		},
	    		handleAs: "json"
	  		});
		}

		function getUsers(url){
		  	dojo.xhrPost({
	    		url: url,
	    		load: function(responseObject, ioArgs){
						usersStore = new dojo.data.ItemFileWriteStore({data: responseObject});
						var formUsernameObj = dijit.byId('formUsername');
						formUsernameObj.store = usersStore;
	    			},
	    		error: function(response, ioArgs){
	      				alert("Error when loading domain list.");
	      				return response;
	    			},
	    		handleAs: "json"
	  		});
		}

		function getPermission(url, data){
			if (typeof data === "undefined")
				data = "";
		  	dojo.rawXhrPost({
	    		url: url,
	 		    headers: {
			        "content-length": data.length,
			        "content-type": "text/x-json"
			    },
	 		    postData: data,
	    		load: function(responseObject, ioArgs){
	       				populatePermission(responseObject);	      			
	       				return responseObject;
	    			},
	    		error: function(response, ioArgs){
	      				alert("Error when loading permissions.");
	      				return response;
	    			},
	    		handleAs: "json"
	  		});		
		}

		function populatePermission(permissions){
			permissionsStore = new dojo.data.ItemFileWriteStore({data: permissions});
			permissionGrid.setStore(permissionsStore);
			if (permissions.sticky != null){
				if (permissions.sticky == "true")
					document.getElementById("stickyControl").innerHTML = "This directory is sticky <button onclick=\"unsetSticky(dojo.byId('recursive').checked)\">Unset</button>";
				else
					document.getElementById("stickyControl").innerHTML = "This directory is not sticky <button onclick=\"setSticky(dojo.byId('recursive').checked)\">Set</button>";
			} else
				document.getElementById("stickyControl").innerHTML = "";
		}

		function getPermissions(batch){
			var sourceURL = getCurrentDirectory();
			if (!batch)
				sourceURL = getCurrentDirectory()+"/"+getFirstCheckedItem(); 
			dojo.byId("recursive").disabled = !directoryIsChecked();	// Enable if any dirs in list
	        <%if (request.getAttribute("servertype").equals("srb")) {%>
				getDomains(sourceURL+"?method=domains");
		    <%} else {%>
				getUsers(sourceURL+"?method=userlist");
			<%}%>
			if (!batch)
				getPermission(sourceURL+"?method=permission");
			else 
				resetPermissionsStore();
			dijit.byId('dialogPermissions').show();
		}

		function savePermission(){
			if (checkedItemsCount() == 0)
				return;
			var rowCount = dijit.byId('metadataGrid').rowCount;
			var recursiveValue = "";
			var usernameValue = dojo.byId("formUsername").value;
			var domainValue;
			var permValue = dojo.byId("formPerm").options[dojo.byId("formPerm").selectedIndex].value;
	        <%if (request.getAttribute("servertype").equals("srb")) {%>
				domainValue = dojo.byId("formDomain").value;
				if (!dijit.byId("formDomain").isValid() || domainValue == ""){
					alert("Please enter a valid domain.");
					return;
				}
			<%}%>
			if (!dijit.byId("formUsername").isValid() || usernameValue == ""){
				alert("Please enter a valid username.");
				return;
			}
			if (dojo.byId("recursive").disabled == false)
				recursiveValue = "&recursive="+(dojo.byId("recursive").checked);			
	        <%if (request.getAttribute("servertype").equals("srb")) {%>
				var form_url = getCurrentDirectory()+"?method=permission"+"&username="+usernameValue+"&domain="+domainValue+"&permission="+permValue+recursiveValue;
			<%} else {%>
				var form_url = getCurrentDirectory()+"?method=permission"+"&username="+usernameValue+"&permission="+permValue+recursiveValue;
			<%}%>
			getPermission(form_url, listToJSON());
		}

		function setSticky(recursive){
			var form_url = getCurrentDirectory()+"?method=permission"+"&recursive="+recursive+"&sticky=true";
			getPermission(form_url, listToJSON());
		}

		function unsetSticky(recursive){
			var form_url = getCurrentDirectory()+"?method=permission"+"&recursive="+recursive+"&sticky=false";
			getPermission(form_url, listToJSON());
		}

		function getSelectedPermission(e){
			dojo.byId("formUsername").value = permissionGrid.getItem(e.rowIndex).username;
			dojo.byId("formDomain").value = permissionGrid.getItem(e.rowIndex).domain;
			for (var i = 0; i < dojo.byId("formPerm").options.length; i++){
				if (dojo.byId("formPerm").options[i].text == permissionGrid.getItem(e.rowIndex).permission) 
					dojo.byId("formPerm").options[i].selected = true;
				else
					dojo.byId("formPerm").options[i].selected = false;
			}
		}

		function showPermissions() {
			var checkedCount = checkedItemsCount();
			if (checkedCount == 0)
				return;
			getPermissions(checkedCount > 1);				
		}

		function getReplicas(action){
			cursorBusy(true);	
		  	dojo.xhrPost({
	    		url: getCurrentDirectory()+"/"+getFirstCheckedItem()+"?method=replicas"+action,
	    		load: function(responseObject, ioArgs){    
						replicasStore = new dojo.data.ItemFileWriteStore({data: responseObject});
						replicasGrid.setStore(replicasStore);  
						loadResourcesFromServer();
	 					cursorBusy(false);
	       				return responseObject;
	    			},
	    		error: function(response, ioArgs){
	      				alert("Error while fetching replicas: "+errorMessage("replicas", response));
	       				cursorBusy(false);
	      				return response;
	    			},
	 //   		sync: true,
	    		handleAs: "json"
	  		});
		}
		
		function loadResourcesFromServer(){
			var server_url = getCurrentDirectory()+"/"+getFirstCheckedItem()+"?method=resources";
			cursorBusy(true);	
			dojo.byId('replicasStatusField').innerHTML = 'Fetching list of resources from server...';
		  	dojo.xhrPost({
	    		url: getCurrentDirectory()+"/"+getFirstCheckedItem()+"?method=resources",
	    		load: function(responseObject, ioArgs){    
						var resources = responseObject.items;
						replicasStore.fetch({
							onComplete: 
								function(items, request){
									for (var i = 0; i < resources.length; i++) {
										var resourceName = resources[i].name;
										var found = false;
										for (var j = 0; j < items.length; j++) {	
											var replicaName = replicasStore.getValue(items[j], "resource");
											if (replicaName == resourceName) {
												found = true;
												break;
											}
										}
										if (!found) {
											var newItem = {resource: resourceName, number: null};
											replicasStore.newItem(newItem);
										}
									}
								}
						});
						cursorBusy(false);
						dojo.byId('replicasStatusField').innerHTML='';
	       				return responseObject;
	    			},
	    		error: function(response, ioArgs){
	      				alert("Error while fetching resources: "+errorMessage("resources", response));
	      				cursorBusy(false);
	 					dojo.byId('replicasStatusField').innerHTML = '';
	      				return response;
	    			},
//	    		sync: true,
	    		handleAs: "json"
	  		});
		}
		
		function deleteReplica() {
			var resource = replicasStore.getValue(replicasGrid.selection.getFirstSelected(), "resource");
			getReplicas("&delete="+resource);
		}
		
		function replicate() {
			var resource = replicasStore.getValue(replicasGrid.selection.getFirstSelected(), "resource");
			getReplicas("&replicate="+resource);
		}		

		function showReplicas() {
			var checkedCount = checkedItemsCount();
			if (checkedCount != 1)
				return;
			resetReplicasStore();
			dojo.byId('replicasStatusField').innerHTML='Loading...';
			dijit.byId('dialogReplicas').show();
			getReplicas("");
		}
	</script>
</head>

<body class="tundra">


<!-- -------------- Dialogs -------------- -->
<div dojoType="dijit.Dialog" id="dialogCreateDir" title="Create Directory">
	New directory name:
	<input name="directory" id="directoryInputBox" dojoType="dijit.form.TextBox" trim="true" value=""/>
	<br/><br/>
	<div style="text-align: right;">
		<button onclick="createDirectory()">Create</button>
		<button onclick="dijit.byId('dialogCreateDir').hide()">Cancel</button>
	</div>
</div>		
<div dojoType="dijit.Dialog" id="dialogUpload" title="Upload">
 	<form id="uploadForm" enctype="multipart/form-data" name="uploadTest" method="post">
   		File to upload:
   		<input type="file" name="uploadFileName" id="formUpload" onKeyPress="if (isEnterKey(event)) {dojo.byId('uploadStartButton').disabled=true; uploadFile();}"/>
 	</form>
	<div id="uploadStatusField"></div>
	<br/>
	<div style="text-align: right;">
		<button id="uploadStartButton" onclick="dojo.byId('uploadStartButton').disabled=true; uploadFile();">Upload</button> 
		<button id="uploadCancelButton" onclick="dojo.byId('uploadCancelButton').disabled=true; uploadCancel();">Cancel</button>
	</div>
</div>					
<div dojoType="dijit.Dialog" id="dialogDelete" title="Delete Items">
    Are you sure you want to delete the selected items and their contents?
	<br/><br/>
	<div style="text-align: right;">
		<button onclick="deleteFiles(getCurrentDirectory()); checkAll(false); refreshButtons();">Delete</button>
		<button onclick="dijit.byId('dialogDelete').hide()">Cancel</button>
	</div>
</div>					
<div dojoType="dijit.Dialog" id="dialogRename" title="Rename">
    New name:
	<input name="newname" id="renameInputBox" dojoType="dijit.form.TextBox" trim="true" value=""/>
	<br/><br/>
	<div style="text-align: right;">
		<button onclick="rename(getFirstCheckedItem(), dojo.byId('renameInputBox').value)">Rename</button>
		<button onclick="dijit.byId('dialogRename').hide()">Cancel</button>
	</div>
</div>					
<div dojoType="dijit.Dialog" id="dialogRestore" title="Restore Items">
    Are you sure you want to restore the selected items and their contents?
	<br/><br/>
	<div style="text-align: right;">
		<button onclick="restoreFiles()">Restore</button>
		<button onclick="dijit.byId('dialogRestore').hide()">Cancel</button>
	</div>
</div>					
<div dojoType="dijit.Dialog" id="dialogMetadata" title="Metadata">
	<table>
		<tr>
			<td>
	        	<button id="metadataRefreshButton" onclick="refreshMetadata(dijit.byId('dialogMetadata').attr('url'))">Refresh</button>
	    		<button onclick="addMetadata()">Add Metadata</button>
	    		<button onclick="dijit.byId('metadataGrid').removeSelectedRows()">Remove Metadata</button>
	    		<button onclick="saveMetadata()">Save</button>
	    		<button onclick="dijit.byId('dialogMetadata').hide()">Close</button>
			</td>
		</tr>
		<tr>
			<td>
				<div id="metadataGrid" jsId="metadataGrid" dojoType="dojox.grid.DataGrid" structure="metadataLayout"></div>
			</td>
		</tr>
	</table>
</div>
<div dojoType="dijit.Dialog" id="dialogPermissions" title="Permissions">
	<table>
		<tr>
			<td>
				<div style="width: 450px;" id="permissionGrid" structure="permissionsLayout" dojoType="dojox.grid.DataGrid" jsId="permissionGrid" selectionMode="single" onRowClick="getSelectedPermission"></div>
			</td>
			<td valign="top">
				<table>
					<tr>
						<td colspan="2"><span id="stickyControl"></span></td>
					</tr>
	        		<%if (request.getAttribute("servertype").equals("srb")) {%>
					  <tr>
						  <td>Domain</td>
						  <td><input name="domain" id="formDomain" jsId="formDomain" dojoType="dijit.form.FilteringSelect" autocomplete="true" searchAttr="name" store="domainsStore"/></td>
					  </tr>
					<%}%>
					<tr>
						<td>Username</td>
						<td><input name="username" id="formUsername" jsId="formUsername" dojoType="dijit.form.FilteringSelect" autocomplete="true" searchAttr="name" store="usersStore"/></td>
					</tr>
					<tr>
						<td>Permission</td>
						<td>
							<select id="formPerm" typdojoType="dijit.form.Select" name="permission">
								<option value="all">all</option>
								<option value="w">write</option>
								<option value="r">read</option>
								<option value="n">null</option>
	        					<%if (request.getAttribute("servertype").equals("srb")) {%>
									<option value="c">curate</option>
									<option value="t">annotate</option>
								<%}%>
								<!-- <option value="o">owner</option> -->
							</select>
						</td>
					</tr>
					<tr>
						<td>Recursive</td>
						<td><input type="checkbox" name="recursive" id="recursive" dojoType="dijit.form.CheckBox"/></td>
					</tr>
					<tr>
						<td rowspan="2" align="center">
							<button onclick="savePermission()">Apply</button>
							<button onclick="dijit.byId('dialogPermissions').hide()">Cancel</button>
						</td>
					</tr>
				</table>
			</td>
		</tr>
	</table>
</div>
<div dojoType="dijit.Dialog" id="dialogReplicas" title="Replicas" onCancel="setCursor(false, true)">
	<table>
		<tr>
			<td width="300px">
				<div id="replicasGrid" structure="layoutReplicas" dojoType="dojox.grid.DataGrid" jsId="replicasGrid" selectionMode="single" loadingMessage="loading" autoHeight="true" onClick="refreshButtons()"></div>
			</td>
			<td valign="top">
				<button id="replicasDeleteButton" style="width:100%" onclick="deleteReplica()">Delete</button><br/>
				<button id="replicasReplicateButton" style="width:100%" onclick="replicate()">Replicate</button><br/>
				<button style="width:100%" onclick="dijit.byId('dialogReplicas').hide()">Cancel</button><br/>	
			</td>
		</tr>
		<tr>
			<td>
				<div id="replicasStatusField" style="font-size:small"></div>
			</td>
		</tr>
	</table>
</div>



<!-- ---------Main body------------ -->			
<table id="header" width="100%" border="0" cellspacing="0" cellpadding="12">
	<tr>
    	<td valign="bottom">
			<h1 class="text"><img src="/images/logo.jpg" width="28" height="30" />&nbsp;<%=request.getAttribute("authenticationrealm")%></h1>
		</td>
    	<!-- <td align="right" valign="bottom"> -->
    	<td align="right" valign="top">
			<table border="0" cellspacing="0" cellpadding="3">
      			<tr class="text">
        			<td align="right">You are logged in as &lt;<a title="Go to my home folder" class="link" onclick="gotoDirectory('<%=request.getAttribute("home")%>'); return false" href="<%=request.getAttribute("home")%>"><%=request.getAttribute("account")%></a>&gt;<!-- | <a class="link" href="">logout</a>--></td>
        		</tr>
        		<tr>
        			
        			<td align="right">
        				<a title="Go to my home folder" onclick="gotoDirectory('<%=request.getAttribute("home")%>'); return false" href="<%=request.getAttribute("home")%>"><img border="0" alt="home" src="/images/home.png" width="40" height="40"/></a>
        				<a title="Go to my trash folder" onclick="gotoDirectory('<%=request.getAttribute("trash")%>'); return false" href="<%=request.getAttribute("trash")%>"><img border="0" alt="trash" src="/images/trash.png" width="36" height="36"/></a>
       				</td>
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
          			<td>
						<table id='uploadButton' class="activeItem" border="0" cellspacing="0" cellpadding="6">
             				<tr>
                				<td onclick="dojo.byId('uploadCancelButton').disabled=false; dojo.byId('uploadStartButton').disabled=false; dojo.byId('uploadStatusField').innerHTML=''; dijit.byId('dialogUpload').show()"><img src="/images/arrow_up.gif" alt="" width="16" height="16" />&nbsp;Upload File</td>
              				</tr>
          				</table>
					</td>
          			<td>
						<table class="activeItem" border="0" cellspacing="0" cellpadding="6">
            				<tr>
              					<td onclick="dijit.byId('dialogCreateDir').show()"><img src="/images/folder_new.gif" alt="" width="16" height="16" />&nbsp;Create Directory</td>
            				</tr>
          				</table>
					</td>
					<td id="trashEmpty">
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
<form id="childrenform" name="childrenform">
	<input type="hidden" name="selections"/> <!-- Dummy first element for checkboxes array forces array when only one item --> 
	<table width="100%" border="0" cellspacing="0" cellpadding="12">
  		<tr>
    		<td width="80%" valign="top">
				<table id="directoryTable" width="100%" border="0" cellpadding="6" cellspacing="2"></table>
			</td>
    		<td valign="top">
				<table width="100%" class="hoverBorderSide" border="0" cellspacing="0" cellpadding="6">
      				<tr>
      					<td class="activeItem" id="toggleAllButton" onclick="toggleAll(); refreshButtons()" title=""></td>
      				</tr>
    			</table>
      			<table width="100%" border="0" cellspacing="2" cellpadding="6">
        			<tr>
          				<td class="text">&nbsp;</td>
          			</tr>
      			</table>
      			<table width="100%" class="hoverBorderSide" border="0" cellspacing="0" cellpadding="6">
        			<tr>
          				<td class="activeItem" id="accessControlButton" onclick="if (!activeItemIsEnabled('accessControlButton')) return; showPermissions()">Access&nbsp;Control</td>       				
        			</tr>
      			</table>
      			<table width="100%" class="hoverBorderSide" border="0" cellspacing="0" cellpadding="6">
       				<tr>
          				<td class="activeItem" id="metadataButton" onclick="if (!activeItemIsEnabled('metadataButton')) return; showMetadata()">Metadata</td>
        			</tr>
    			</table>
      			<table width="100%" class="hoverBorderSide" border="0" cellspacing="0" cellpadding="6">
        			<tr>
          				<td class="activeItem" id="replicasButton" onclick="if (!activeItemIsEnabled('replicasButton')) return; showReplicas()">Replicas</td>
        			</tr>
    			</table>    			
      			<table width="100%" class="hoverBorderSide" border="0" cellspacing="0" cellpadding="6">
       				<tr>
          				<td class="activeItem" id="renameButton" onclick="if (!activeItemIsEnabled('renameButton')) return; dojo.byId('renameInputBox').value=getFirstCheckedItem(); dijit.byId('dialogRename').show()">Rename</td>         				
        			</tr>
    			</table>
      			<table width="100%" class="hoverBorderSide" border="0" cellspacing="0" cellpadding="6">
        			<tr>
          				<td class="activeItem" id="deleteButton" onclick="if (!activeItemIsEnabled('deleteButton')) return; dijit.byId('dialogDelete').show()" >Delete</td>
        			</tr>
    			</table>
      			<table width="100%" class="invisible" id="trashRestoreTable" border="0" cellspacing="0" cellpadding="6">
        			<tr id="trashRestoreRow">
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