<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:D="DAV:">
    <xsl:output method="html"/>
    <xsl:param name="dojoroot"/>
    <xsl:param name="servertype"/>
    <xsl:param name="href"/>
    <xsl:param name="url"/>
    <xsl:param name="unc"/>
    <xsl:param name="parent"/>
    <xsl:param name="trash"/>
    <xsl:param name="home"/>
 	
    <xsl:template match="/">
        <html>
            <head>
                <title>Davis - <xsl:value-of select="$url"/></title>
                <meta HTTP-EQUIV="Pragma" CONTENT="no-cache"/>
                <meta HTTP-EQUIV="Cache-Control" CONTENT="no-cache"/>
                <meta HTTP-EQUIV="Expires" CONTENT="0"/>
    			<style type="text/css">
    @import "<xsl:value-of select="$dojoroot"/>dojoroot/dijit/themes/tundra/tundra.css";
    @import "<xsl:value-of select="$dojoroot"/>dojoroot/dojox/grid/resources/Grid.css";
    @import "<xsl:value-of select="$dojoroot"/>dojoroot/dojox/grid/resources/tundraGrid.css";
    @import "<xsl:value-of select="$dojoroot"/>dojoroot/dojo/resources/dojo.css"
    			</style>
                <style>
    body {
        font-family: Verdana, Tahoma, Helvetica, Arial, sans-serif;
        background: white;
        font-size: 10pt;
    }
    p {
        font-size: 10pt;
    }
    td {
        font-family: Verdana, Tahoma, Helvetica, Arial, sans-serif;
        font-size: 10pt;
    }
    a {
        font-family: Verdana, Tahoma, Helvetica, Arial, sans-serif;
        color: black;
        text-decoration: none;
    }
    a:hover {
        color: green;
    }
    a.hidden {
        font-style: italic;
    }
    a.directory {
        font-weight: bold;
        color: green;
    }
    a.directory:hover {
        color: black;
    }
    a.hiddendirectory {
        font-weight: bold;
        color: #99aa88;
    }
    a.hiddendirectory:hover {
        color: #777777;
    }
    a.parent {
        font-weight: bold;
        color: green;
    }
    a.parent:hover {
        color: #bbccaa;
    }
    .properties {
        font-size: 8pt;
    }
    a.title {
        behavior: url(#default#AnchorClick);
        font-size: 16pt;
        font-weight: bold;
        color: green;
    }
    a.title:hover {
        color: #bbccaa;
    }
    a.unc {
        behavior: url(#default#AnchorClick);
        font-size: 10pt;
        font-weight: bold;
        color: black;
    }
    a.unc:hover {
        color: green;
    }
    
    .dojoxGrid-row-odd td {
        background:#e8f2ff;
	}
    #metadataGrid {
        border: 1px solid #333;
        width: 400px;
        height: 300px;
    }
	#permissionGrid {
        border: 1px solid #333;
        width: 600px;
        height: 200px;
    }
    table { 
    	border: none; 
    }
    
                </style>
    			<xsl:text disable-output-escaping="yes">&lt;script type="text/javascript" src="</xsl:text><xsl:value-of select="$dojoroot"/><xsl:text disable-output-escaping="yes">dojoroot/dojo/dojo.js" djConfig="isDebug: false, parseOnLoad: true">&lt;/script></xsl:text>
    			<script type="text/javascript">
    // Load Dojo's code relating to the Button widget
//    dojo.require("dojox.grid.compat.Grid");
    dojo.require("dojox.grid.DataGrid");
//	dojo.require("dojox.grid.compat._grid.edit");
    dojo.require("dojo.data.ItemFileWriteStore");
    dojo.require("dojo.data.ItemFileReadStore");
    dojo.require("dijit.form.Button");
    dojo.require("dijit.form.CheckBox");
    dojo.require("dijit.Dialog");
	dojo.require("dijit.form.TextBox");
	dojo.require("dojo.parser");
	dojo.require("dijit.form.FilteringSelect");
	dojo.require("dojo.io.iframe");
	dojo.require("dijit.ProgressBar");
	
    var metadataLayout= [{
			defaultCell: { editable: true, type: dojox.grid.cells._Widget, styles: 'text-align: left;'  },
			rows: [
        { field: "name", width: "150px", name: "Name", editable: true},
        { field: "value", width: "auto", name: "Value", editable: true}
        <xsl:if test="$servertype='irods'">
        ,{ field: "unit", width: "50px", name: "Unit", editable: true}
        </xsl:if>
        ]}
    ];

    var permissionsLayout= [
        { field: "username", width: "200px", name: "Username"},
        <xsl:if test="$servertype='srb'">
        { field: "domain", width: "200px", name: "Domain"},
        </xsl:if>
        { field: "permission", width: "auto", name: "Permission"}
    ];
    
    var layoutReplicas= [
    	{ field: "resource", width: "150px", name: "Resource"},
     	{ field: "number", width: "auto", name: "Replica Number"}
    ];
    
//	var model2 = new dojox.grid.data.Table(null, []);
	var metadataStore = new dojo.data.ItemFileWriteStore({data: {items:[]}});
	var permissionsStore = new dojo.data.ItemFileWriteStore({data: {items:[]}});
	var domainsStore = new dojo.data.ItemFileWriteStore({data: {items:[]}});
	var usersStore = new dojo.data.ItemFileWriteStore({data: {items:[]}});
	var replicasStore = new dojo.data.ItemFileWriteStore({data: {items:[]}});
	var server_url;
	var ori_url; // Used in getDomains for getting user list
	var uploadInProgress = false;
		
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
	
	var cursorCount = 0;

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
			if (cursorCount &lt; 0)
				cursorCount = 0;
			if (cursorCount == 0)
				setCursor(false, false);
		}
	}

	function getDomains(urlString){
	  	dojo.xhrPost({
    		url: urlString,
    		load: function(responseObject, ioArgs){
      
//				console.dir(formDomain);  // Dump it to the console
//         		console.dir(responseObject.items[0].username);  // Prints username     			
				domainsStore=new dojo.data.ItemFileWriteStore({data: responseObject});
				var formDomainObj = dijit.byId('formDomain');
				formDomainObj.store=domainsStore;
				formDomainObj.onChange=function(val){
//					alert(server_url);
					dijit.byId('formUsername').textbox.value = "";
					dijit.byId('formUsername').valueNode.value = "";
					getUsers(ori_url+"?method=userlist&amp;domain="+formDomainObj.item.name);
//					console.dir(formDomainObj.item);
				}
//				alert("xx");
//      			formDomain.setStore(domainsStore);
//       			return responseObject;
    		},
    		error: function(response, ioArgs){
      			alert("Error when loading domain list.");
      			return response;
    		},
    		handleAs: "json"
  		});
	}
	function getUsers(urlString){
	  	dojo.xhrPost({
    		url: urlString,
    		load: function(responseObject, ioArgs){
      
//				console.dir(formDomain);  // Dump it to the console
//         		console.dir(responseObject.items[0].username);  // Prints username     			
				usersStore=new dojo.data.ItemFileWriteStore({data: responseObject});
				var formUsernameObj = dijit.byId('formUsername');
				formUsernameObj.store=usersStore;
    		},
    		error: function(response, ioArgs){
      			alert("Error when loading domain list.");
      			return response;
    		},
    		handleAs: "json"
  		});
	}
	function getPermission(urlString, data){
		if (typeof data === "undefined")
			data = "";
	  	dojo.rawXhrPost({
    		url: urlString,
 		    headers: {
		        "content-length": data.length,
		        "content-type": "text/x-json"
		    },
 		    postData: data,
    		load: function(responseObject, ioArgs){
      
//				console.dir(responseObject);  // Dump it to the console
//         		console.dir(responseObject.items[0].username);  // Prints username     			
       			populatePermission(responseObject);
      			
//       			return responseObject;
    		},
    		error: function(response, ioArgs){
      			alert("Error when loading permissions.");
      			return response;
    		},
    		handleAs: "json"
  		});
	
	}
	function loadMetadataFromServer(urlString){
	  	dojo.xhrPost({
    		url: urlString,
    		load: function(responseObject, ioArgs){
      
//				console.dir(responseObject);  // Dump it to the console
//         		console.dir(responseObject.items[0].username);  // Prints username     			
				metadataStore=new dojo.data.ItemFileWriteStore({data: responseObject});
				metadataGrid.setStore(metadataStore);
      			
//       			return responseObject;
    		},
    		error: function(response, ioArgs){
      			alert("Error when loading metadata.");
      			return response;
    		},
    		handleAs: "json"
  		});
	}
	function populatePermission(perms){
		permissionsStore=new dojo.data.ItemFileWriteStore({data: perms});
		permissionGrid.setStore(permissionsStore);
//		alert(perms.sticky);
		if (perms.sticky!=null){
			if (perms.sticky=="true"){
				document.getElementById("stickyControl").innerHTML="This directory is sticky.&lt;button onclick=\"unsetSticky(dojo.byId('recursive').checked)\">Unset&lt;/button>";
			}else{
				document.getElementById("stickyControl").innerHTML="This directory is not sticky.&lt;button onclick=\"setSticky(dojo.byId('recursive').checked)\">Set&lt;/button>";
			}
		}else{
			document.getElementById("stickyControl").innerHTML="";
		}
	}
	function getMetadata(url, batch){
		ori_url=url;
		server_url=url+"?method=metadata";
		var metadata_url = "";
		if (batch) {
//			loadMetadataFromServer(server_url); // Default metadata is taken from current directory
			resetMetadataStore();

		} else {
			metadata_url = url+"/"+getFirstCheckedItem()+"?method=metadata";
			loadMetadataFromServer(metadata_url);
		}
		dijit.byId('dialogMetadata').attr("url", metadata_url);
		dojo.byId('metadataRefreshButton').disabled=batch;  // Don't want the refresh button active in batch mode
		dijit.byId('dialogMetadata').show();
	}
	function refreshMetadata(url){
		loadMetadataFromServer(url);
	}
//	function getFilePermission(url){
//		ori_url=url;
//		dojo.byId("recursive").disabled=true;
//		if (document.getElementById("servertype").value=="srb"){
//			server_url=url+"?method=domains";
//			getDomains(server_url);
//		}else{
//			server_url=url+"?method=userlist";
//			getUsers(server_url);
//		}
//		server_url=url+"?method=permission";
//		getPermission(server_url);
//		dijit.byId('dialogPermissions').show();
//	}
	function getPermissions(url, batch){
		ori_url=url;
		var sourceURL = url;
		if (!batch)
			sourceURL = url+"/"+getFirstCheckedItem(); 
		dojo.byId("recursive").disabled=!directoryIsChecked();	// Enable if any dirs in list
		if (document.getElementById("servertype").value=="srb"){
			server_url=sourceURL+"?method=domains";
			getDomains(server_url);
		}else{
			server_url=sourceURL+"?method=userlist";
			getUsers(server_url);
		}
		if (!batch)
			getPermission(sourceURL+"?method=permission");
		else 
			resetPermissionsStore();
		server_url=url+"?method=permission";
		dijit.byId('dialogPermissions').show();
	}
	function getReplicas(url, action){
		cursorBusy(true);	
		server_url = url+"/"+getFirstCheckedItem()+"?method=replicas"+action;
	  	dojo.xhrPost({
    		url: server_url,
    		load: function(responseObject, ioArgs){    
				replicasStore=new dojo.data.ItemFileWriteStore({data: responseObject});
				replicasGrid.setStore(replicasStore);  
				loadResourcesFromServer(url);
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
	function loadResourcesFromServer(url){
		var server_url = url+"/"+getFirstCheckedItem()+"?method=resources";
		cursorBusy(true);	
		dojo.byId('replicasStatusField').innerHTML='Fetching list of resources from server...';
	  	dojo.xhrPost({
    		url: server_url,
    		load: function(responseObject, ioArgs){    
				var resources = responseObject.items;
				replicasStore.fetch({
					onComplete: 
						function(items, request){
							for (var i = 0; i &lt; resources.length; i++) {
								var resourceName = resources[i].name;
								var found = false;
								for (var j = 0; j &lt; items.length; j++) {	
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
 				dojo.byId('replicasStatusField').innerHTML='';
      			return response;
    		},
//    		sync: true,
    		handleAs: "json"
  		});
	}
	
	function deleteReplica() {
		var resource = replicasStore.getValue(replicasGrid.selection.getFirstSelected(), "resource");
		getReplicas('<xsl:value-of select="$url"/>', "&amp;delete="+resource);
	}
	
	function replicate() {
		var resource = replicasStore.getValue(replicasGrid.selection.getFirstSelected(), "resource");
		getReplicas('<xsl:value-of select="$url"/>', "&amp;replicate="+resource);
	}
	
	function savePermission(){
		if (checkedItemsCount() == 0)
			return;
		var rowCount=dijit.byId('metadataGrid').rowCount;
		var recursiveValue="";
		var usernameValue=dojo.byId("formUsername").value;
		var domainValue;
		var permValue=dojo.byId("formPerm").options[dojo.byId("formPerm").selectedIndex].value;
		if (document.getElementById("servertype").value=="srb"){
			domainValue=dojo.byId("formDomain").value;
			if (!dijit.byId("formDomain").isValid()||domainValue==""){
				alert("Please enter a valid domain.");
				return;
			}
		}
		if (!dijit.byId("formUsername").isValid()||usernameValue==""){
			alert("Please enter a valid username.");
			return;
		}
		if (dojo.byId("recursive").disabled==false){
			recursiveValue="&amp;recursive="+(dojo.byId("recursive").checked);
		}
		var form_url;
		if (document.getElementById("servertype").value=="srb"){
			form_url=server_url+"&amp;username="+usernameValue+"&amp;domain="+domainValue+"&amp;permission="+permValue+recursiveValue;
		}else{
			form_url=server_url+"&amp;username="+usernameValue+"&amp;permission="+permValue+recursiveValue;
		}
//		alert(form_url);
		getPermission(form_url, listToJSON());
	}
	function setSticky(recursive){
		var form_url=server_url+"&amp;recursive="+recursive+"&amp;sticky=true";
		getPermission(form_url, listToJSON());
	}
	function unsetSticky(recursive){
		var form_url=server_url+"&amp;recursive="+recursive+"&amp;sticky=false";
		getPermission(form_url, listToJSON());
	}
	function getSelectedPermission(e){
		//console.dir(e.rowIndex);
		//console.dir(permissionGrid.getItem(e.rowIndex).username);
		//console.dir(permissionsStore.items[e.rowIndex]);
		//console.dir(dojo.byId("formUsername").value);
		dojo.byId("formUsername").value=permissionGrid.getItem(e.rowIndex).username;
		dojo.byId("formDomain").value=permissionGrid.getItem(e.rowIndex).domain;
		for (var i=0;i&lt;dojo.byId("formPerm").options.length;i++){
			if (dojo.byId("formPerm").options[i].text==permissionGrid.getItem(e.rowIndex).permission) 
				dojo.byId("formPerm").options[i].selected=true;
			else
				dojo.byId("formPerm").options[i].selected=false;
		}
	}
	function addMetadata(){
        // set the properties for the new item:
        <xsl:if test="$servertype='srb'">
        var myNewItem = {name: "name", value: "value"};
        </xsl:if>
        <xsl:if test="$servertype='irods'">
        var myNewItem = {name: "name", value: "value", unit: "unit"};
        </xsl:if>
        // Insert the new item into the store:
        // (we use domainsStore from the example above in this example)
        metadataStore.newItem(myNewItem);
	}
	function delMetadata(){
        // Get all selected items from the Grid:
        var items = metadataGrid.selection.getSelected();
        if(items.length){
            // Iterate through the list of selected items.
            // The current item is available in the variable
            // "selectedItem" within the following function:
            dojo.forEach(items, function(selectedItem) {
                if(selectedItem !== null) {
                    // Delete the item from the data store:
                    metadataStore.deleteItem(selectedItem);
                } // end if
            }); // end forEach
        } // end if
	}
	function saveMetadata(){
		if (checkedItemsCount() == 0)
			return;
		var rowCount=dijit.byId('metadataGrid').rowCount;

		//json format is: [{"files":["file1","file2"...],"metadata":[{"name":"foo","value":"bar"},{"name":"foo2","value":"bar2"}...]}]
		var data=listToJSON();
		data = data.substring(0, data.length-2);	// Trim '}]' off end 
		data+=",\"metadata\":[";
		for (var i=0;i&lt;rowCount;i++){
			if (i>0) data+=",";
	        <xsl:if test="$servertype='srb'">
			data+="{\"name\":\""+dijit.byId('metadataGrid').getItem(i).name+"\", \"value\":\""+dijit.byId('metadataGrid').getItem(i).value+"\"}";
	        </xsl:if>
	        <xsl:if test="$servertype='irods'">
			data+="{\"name\":\""+dijit.byId('metadataGrid').getItem(i).name+"\", \"value\":\""+dijit.byId('metadataGrid').getItem(i).value+"\", \"unit\":\""+dijit.byId('metadataGrid').getItem(i).unit+"\"}";
	        </xsl:if>
		}
		data+="]}]";
//		alert(dijit.byId('metadataGrid').getItem(0).name);
//		alert("data:"+data);
	  	dojo.rawXhrPost({
    		url: server_url,
		    headers: {
		        "content-length": data.length,
		        "content-type": "text/x-json"
		    },
		    postData: data,
    		load: function(responseObject, ioArgs){
      
//				console.dir(responseObject);  // Dump it to the console
//         		console.dir(responseObject.items[0].username);  // Prints username     			
				metadataStore=new dojo.data.ItemFileWriteStore({data: responseObject});
				metadataGrid.setStore(metadataStore);
      			
//       			return responseObject;
    		},
    		error: function(response, ioArgs){
      			alert("Error when loading metadata.");
      			return response;
    		},
    		handleAs: "json"
  		});
	}
		
	function createDirectory(url){
		dijit.byId('dialogCreateDir').hide();
		var dirName = dojo.byId('directoryInputBox').value;
		
		dojo.xhr("MKCOL", {
			url: url+"/"+dirName,
		    load: function(responseObject, ioArgs){
		      	window.location.reload();
		      	return responseObject;
		    },
    		error: function(response, ioArgs){
      			alert("Failed to create directory "+dirName+": "+errorMessage("mkcol", response));
      			return response;
    		}
		});
	}
		
 	function uploadFile(url){ 		
		server_url="?method=upload";
		dojo.byId('uploadStatusField').innerHTML = "Uploading...";
		uploadInProgress = true;
	
		dojo.io.iframe.send({
			url: server_url,
			method: "POST",
			form: "uploadForm",
			handleAs: "json",
			handle: function(response, ioArgs){
						dijit.byId('dialogUpload').hide();
						if (response.status == "success") {
							alert("Successfully transferred "+response.message+" bytes");
							window.location.reload();						
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
			window.location.reload();
		}
	}
	
	function listCheckedItems() {
		var list="";
		for (var i = 1; i &lt; document.childrenform.selections.length; i++) // Ignore dummy first element
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
		for (var i = 1; i &lt; document.childrenform.selections.length; i++) // Ignore dummy first element
			if (document.childrenform.selections[i].checked) 
			  return trimMode(document.childrenform.selections[i].value);
		return null;
	}
	
	function checkedItemsCount() {
		var count=0;
		for (var i = 1; i &lt; document.childrenform.selections.length; i++) // Ignore dummy first element
			if (document.childrenform.selections[i].checked) 
				count++;
		return count;
	}
	
	function directoryIsChecked() {
		for (var i = 1; i &lt; document.childrenform.selections.length; i++) // Ignore dummy first element
			if (document.childrenform.selections[i].checked) 
				if (document.childrenform.selections[i].value.indexOf(";directory") >= 0)
					return true;
		return false;
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
		      	window.location.reload();
		      	return responseObject;
		    },
    		error: function(response, ioArgs){
      			alert("Failed to delete one or more items: "+errorMessage("delete", response));
      			window.location.reload(); // Reload on error in case some files were deleted
      			return response;
    		}
		}, true);
		checkAll(false);
	}
	
	function rename(name, newName){
		dijit.byId('dialogRename').hide();
		var url = '<xsl:value-of select="$url"/>/'+name;
		var destination = '<xsl:value-of select="$href"/>/'+newName;
		renameFile(url, destination);
	}

	function renameFile(url, destination) {
		dojo.xhr("MOVE", {
			url: url,
			headers: {
		        "Destination": destination
		    },
		    load: function(responseObject, ioArgs){
		      	window.location.reload();
		      	return responseObject;
		    },
    		error: function(response, ioArgs){
      			alert("Failed to move items: "+errorMessage("move", response));
      			window.location.reload(); // Reload on error in case file was deleted
      			return response;
    		}
		}, true);
		checkAll(false);
	}
	
	function restoreFiles(url) {
		dijit.byId('dialogRestore').hide();
		var hrefBase = '<xsl:value-of select="$href"/>'.substr(0, '<xsl:value-of select="$href"/>'.length-url.length);
		var destination = hrefBase+'<xsl:value-of select="$home"/>'+url.substring('<xsl:value-of select="$trash"/>'.length, url.length);
		moveFiles(url, destination);
	}
	
	function moveFiles(url, destination) {
		if (checkedItemsCount() == 0)
			return;
		var data='[{"files":['+listCheckedItems()+']}]'; //json format is: [{"files":["file1","file2"...]}]		
		dojo.xhr("MOVE", {
			url: url,
			headers: {
		        "content-length": data.length,
		        "content-type": "text/x-json",
		        "Destination": destination
		    },
		    putData: data, // This can be specified as rawBody in dojo 1.4
		    load: function(responseObject, ioArgs){
		      	window.location.reload();
		      	return responseObject;
		    },
    		error: function(response, ioArgs){
      			alert("Failed to move one or more items: "+errorMessage("move", response));
      			window.location.reload(); // Reload on error in case some files were deleted
      			return response;
    		}
		}, true);
		checkAll(false);
	}
	
	function showMetadata(currentURL) {
		var checkedCount = checkedItemsCount();
		if (checkedCount == 0)
			return;
		getMetadata(currentURL, (checkedCount > 1));				
	}
	
	function showPermissions(currentURL) {
		var checkedCount = checkedItemsCount();
		if (checkedCount == 0)
			return;
		getPermissions(currentURL, (checkedCount > 1));				
	}
		
	function showReplicas(currentURL) {
		var checkedCount = checkedItemsCount();
		if (checkedCount != 1)
			return;
		resetReplicasStore();
		dojo.byId('replicasStatusField').innerHTML='Loading...';
		dijit.byId('dialogReplicas').show();
		getReplicas(currentURL, "");
	}
	
	function setToggleButtonState(state) {
		var button = dojo.byId('toggleAllButton');
		button.value = state ? "Select all" : "Select none";
		button.innerHTML = button.value;
	}
	
	function getToggleButtonState() {
		return dojo.byId('toggleAllButton').value == "Select all";
	}
	
	function refreshButtons() {
		dojo.byId('renameButton').disabled = checkedItemsCount() != 1;
		dojo.byId('replicasButton').disabled = checkedItemsCount() != 1;
		dojo.byId('deleteButton').disabled = checkedItemsCount() == 0;
		dojo.byId('metadataButton').disabled = checkedItemsCount() == 0;
		dojo.byId('accessControlButton').disabled = checkedItemsCount() == 0;
		if (replicasGrid.selection.getSelectedCount() == 0) {
			dojo.byId('replicasDeleteButton').disabled = true;
			dojo.byId('replicasReplicateButton').disabled = true;
		} else {
			var item = replicasGrid.selection.getFirstSelected();
			var isReplica = replicasStore.getValue(item, "number") != null;
			dojo.byId('replicasDeleteButton').disabled = !isReplica;
			dojo.byId('replicasReplicateButton').disabled = isReplica;			
		}
		if (dojo.byId('restoreButton') != null)
			dojo.byId('restoreButton').disabled = checkedItemsCount() == 0;
	}
		
	function toggleAll() {
		var allChecked = !getToggleButtonState();
		setToggleButtonState(allChecked);
		for (var i = 1; i &lt; document.childrenform.selections.length; i++) // Ignore dummy first element
			document.childrenform.selections[i].checked = !allChecked;
	}
	
	function checkAll(state) {
		for (var i = 1; i &lt; document.childrenform.selections.length; i++) // Ignore dummy first element
			document.childrenform.selections[i].checked = state;
	}
	
	function navigate(form){
		var URL = document.mainToolbar.location.options[document.mainToolbar.location.selectedIndex].value;
		window.location.href = URL;
	}
	
	function inTrash() {
		return '<xsl:value-of select="$url"/>'.indexOf('<xsl:value-of select="$trash"/>') == 0;
	}
	
	function errorMessage(method, messageObject) {
		var code = messageObject.status;
		var message = messageObject.message;
		if (code &lt; 0)
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

	function toBreadcrumb(path) {
		var result = "";
		var subpath = "";
		var dirs = path.split('/');
		for (var i = 1; i &lt; dirs.length; i++) {
			subpath = subpath+'/'+dirs[i];
			result = result+'<a class="title" href="'+subpath+'">/'+dirs[i]+'</a>';
		}
		return result;
	}
    			</script>		
            </head>
            <body class="tundra">
                <xsl:apply-templates select="D:multistatus"/>
                <input type="hidden" name="servertype" id="servertype">
	                <xsl:attribute name="value">
	                	<xsl:value-of select="$servertype"/>
	                </xsl:attribute>
                </input>
            <!-- Dialogs begin-->
            	<div dojoType="dijit.Dialog" id="dialogCreateDir" title="Create Directory">
            		New directory name:
 					<input name="directory" id="directoryInputBox" dojoType="dijit.form.TextBox" trim="true" value=""/>
					<br/><br/>
					<div style="text-align: right;">
						<button onclick="createDirectory('{$url}')">Create</button>
						<button onclick="dijit.byId('dialogCreateDir').hide()">Cancel</button>
					</div>
				</div>					
            	<div dojoType="dijit.Dialog" id="dialogRename" title="Rename">
            		New name:
 					<input name="newname" id="renameInputBox" dojoType="dijit.form.TextBox" trim="true"/>
					<br/><br/>
					<div style="text-align: right;">
						<button onclick="rename(getFirstCheckedItem(), dojo.byId('renameInputBox').value)">Rename</button>
						<button onclick="dijit.byId('dialogRename').hide()">Cancel</button>
					</div>
				</div>					
             	<div dojoType="dijit.Dialog" id="dialogDelete" title="Delete Items">
            		Are you sure you want to delete the selected items and their contents?
					<br/><br/>
					<div style="text-align: right;">
						<button onclick="deleteFiles('{$url}')">Delete</button>
						<button onclick="dijit.byId('dialogDelete').hide()">Cancel</button>
					</div>
				</div>					
              	<div dojoType="dijit.Dialog" id="dialogRestore" title="Restore Items">
            		Are you sure you want to restore the selected items and their contents?
					<br/><br/>
					<div style="text-align: right;">
						<button onclick="restoreFiles('{$url}')">Restore</button>
						<button onclick="dijit.byId('dialogRestore').hide()">Cancel</button>
					</div>
				</div>					
            	<div dojoType="dijit.Dialog" id="dialogUpload" title="Upload">
            		<form id="uploadForm" enctype="multipart/form-data" name="uploadTest" method="post">
            		File to upload:
            		<input type="file" name="uploadFileName" id="formUpload"/>
            		</form>
					<div id="uploadStatusField"></div>
					<br/>
					<div style="text-align: right;">
						<button id="uploadStartButton" onclick="uploadStartButton.disabled=true; uploadFile();">Upload</button> 
						<button id="uploadCancelButton" onclick="uploadCancelButton.disabled=true; uploadCancel();">Cancel</button>
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
        					<button onclick="dijit.byId('dialogMetadata').hide()">Cancel</button>
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
						<td width="600px">   <!-- dojoType="" structure="permissionsLayout" dojox.Grid store="permissionsStore"-->
							<div id="permissionGrid"  structure="permissionsLayout" dojoType="dojox.grid.DataGrid" jsId="permissionGrid" selectionMode="single" onRowClick="getSelectedPermission"></div>
						</td>
						<td valign="top">
							<table>
								<tr>
									<td colspan="2"><span id="stickyControl"></span></td>
								</tr>
								<xsl:if test="$servertype='srb'">
								<tr>
									<td>Domain</td>
									<td><input name="domain" id="formDomain" jsId="formDomain" dojoType="dijit.form.FilteringSelect" autocomplete="true" searchAttr="name" store="domainsStore"/></td>
								</tr>
								</xsl:if>
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
											<xsl:if test="$servertype='srb'">
												<option value="c">curate</option>
												<option value="t">annotate</option>
											</xsl:if>
										<!-- 	<option value="o">owner</option> -->
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
							<div id="replicasStatusField"></div>
						</td>
					</tr>
				</table>
				</div>
            <!-- Dialogs end -->
            </body>
        </html>
    </xsl:template>
    <xsl:template match="D:multistatus">
        <xsl:apply-templates select="D:response[D:href = $href]" mode="base"/>
        <xsl:choose>
            <xsl:when test="D:response[D:href != $href]">
                <p>
                    <xsl:text>Total </xsl:text>
                    <xsl:value-of select="format-number(sum(D:response[not(D:propstat/D:prop/D:resourcetype/D:collection)]/D:propstat/D:prop/D:getcontentlength), '#,##0 bytes')"/>
                    <xsl:text> (</xsl:text>
                    <xsl:value-of select="format-number(round(sum(D:response[not(D:propstat/D:prop/D:resourcetype/D:collection)]/D:propstat/D:prop/D:getcontentlength) div 1024), '#,##0 KB')"/>
                    <xsl:text>).</xsl:text><br/>
                    <xsl:value-of select="format-number(count(D:response[D:href != $href]), '#,##0')"/>
                    <xsl:text> objects (</xsl:text>
                    <xsl:value-of select="format-number(count(D:response[D:href != $href][D:propstat/D:prop/D:resourcetype/D:collection]), '#,##0')"/>
                    <xsl:text> directories, </xsl:text>
                    <xsl:value-of select="format-number(count(D:response[not(D:propstat/D:prop/D:resourcetype/D:collection)]), '#,##0')"/>
                    <xsl:text> files):</xsl:text>
                </p>
<!-- 
					<button onclick="alert('href={$href}\n trash={$trash}\n url={$url}\n unc={$unc}\n home={$home}\n parent={$parent}')">Debug</button>
 -->               
                <button id="toggleAllButton" onclick="toggleAll(); refreshButtons()" value="Select all">Select all</button>
                &#160;&#160;
                <xsl:choose>
                	<xsl:when test="starts-with($url, $trash)">
                		<button id="restoreButton" onclick="if (checkedItemsCount() > 0) dijit.byId('dialogRestore').show()" disabled="true">Restore</button>
  			   	 		<button onclick="checkAll(true); setToggleButtonState(!getToggleButtonState()); dijit.byId('dialogDelete').show()">Empty Trash</button>
                	</xsl:when>
                	<xsl:otherwise>	
 <!-- 			   	 		<button id="deleteButton" onclick="if (checkedItemsCount() > 0) dijit.byId('dialogDelete').show()" disabled="true">Delete</button>
 -->
 			   	 	</xsl:otherwise>                
                </xsl:choose> 
 <!-- -->               <button id="deleteButton" onclick="if (checkedItemsCount() > 0) dijit.byId('dialogDelete').show()" disabled="true">Delete</button>
 <!-- -->
			    <button id="renameButton" onclick="dojo.byId('renameInputBox').value=getFirstCheckedItem(); dijit.byId('dialogRename').show()" disabled="true">Rename</button>
			    <button id="metadataButton" onclick="showMetadata('{$url}')" disabled="true">Metadata</button>
			    <button id="accessControlButton" onclick="showPermissions('{$url}')" disabled="true">Access Control</button> 
			    <button id="replicasButton" onclick="showReplicas('{$url}')" disabled="true">Replicas</button><br/>
			    <form name="childrenform">  
					<input type="hidden" name="selections"/> <!-- Dummy first element for checkboxes array forces array when only one item --> 
                	<table border="0" cellpadding="0" cellspacing="0">
                   		<tr valign="top">
                       		<td>
                           		<table border="0" cellpadding="6" cellspacing="0">
                    				<xsl:if test="D:response[D:href != $href][D:propstat/D:prop/D:resourcetype/D:collection]">
                                    	<xsl:apply-templates select="D:response[D:href != $href][D:propstat/D:prop/D:resourcetype/D:collection]" mode="directory">
                                       	<xsl:sort select="D:propstat/D:prop/D:displayname"/>
                                    	</xsl:apply-templates>
                    				</xsl:if>
                    				<xsl:if test="D:response[not(D:propstat/D:prop/D:resourcetype/D:collection)]">
                                    	<xsl:apply-templates select="D:response[not(D:propstat/D:prop/D:resourcetype/D:collection)]" mode="file">
                                       	<xsl:sort select="D:propstat/D:prop/D:displayname"/>
                                    	</xsl:apply-templates>
                    				</xsl:if>
                            	</table>
                        	</td>
                		</tr>
                	</table>
      			</form>
            </xsl:when>
            <xsl:otherwise>
                <p>
                    <i>(Directory is empty)</i>
                </p>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>
    <xsl:template match="D:response" mode="base">
        <p>
        	<script type="text/javascript">
        		document.write(toBreadcrumb('<xsl:value-of select="$url"/>'));
        	</script><br/>
<!--            <a class="title" href="{$href}" folder="{$href}"><xsl:value-of select="$url"/></a><br/> -->
            <a class="unc" href="{$href}" folder="{$href}"><xsl:value-of select="$unc"/></a><br/>
            <xsl:text>Last modified on </xsl:text>
            <xsl:value-of select="D:propstat/D:prop/D:getlastmodified"/>
            <xsl:text>.</xsl:text>
            <!--<br/>
            <table>
            	<tr>-->
             		<br/>
             		<form name="mainToolbar">
             			<input type="button" value="Create Directory" onClick="dijit.byId('dialogCreateDir').show()"/>
              			<input type="button" value="Upload File" onClick="uploadCancelButton.disabled=false; uploadStartButton.disabled=false; dojo.byId('uploadStatusField').innerHTML=''; dijit.byId('dialogUpload').show()"/>
                 		&#160;&#160;&#160;&#160;
						<select name="location">
							<option value="{$home}">Home</option>
							<option value="{$trash}">Trash</option>
						</select>
						<input type="button" value="Go to" onClick="navigate(this)"/>
					</form>            		
            		<xsl:if test="$url != '/'">
                		<br/><a href="{$parent}" class="parent">Parent</a>
            		</xsl:if>
            	<!--</tr>
            </table>-->
        </p>
    </xsl:template>
    <xsl:template match="D:response" mode="directory">
        <tr valign="top">
    		<td><input type="checkbox" name="selections" value="{D:propstat/D:prop/D:displayname};directory" onClick="refreshButtons()"/></td>   
            <td nowrap="nowrap">
		        <a href="{D:href}" class="directory">
		            <xsl:if test="D:propstat/D:prop/D:ishidden = '1'">
		                <xsl:attribute name="class">hiddendirectory</xsl:attribute>
		            </xsl:if>
		            <xsl:value-of select="D:propstat/D:prop/D:displayname"/>
		        </a>
            </td>
            <td align="right">
            	<xsl:text>directory</xsl:text>
            </td>
            <td class="properties">
                <xsl:value-of select="D:propstat/D:prop/D:getlastmodified"/>
            </td>
            <!-- <td nowrap="nowrap">
                 <button onclick="getMetadata('{D:href}')">Metadata</button> 
                <button onclick="getDirPermission('{D:href}')">Access Control</button> 
            </td> -->
        </tr>
    </xsl:template>
    <xsl:template match="D:response" mode="file">
        <tr valign="top">
            <td><input type="checkbox" name="selections" value="{D:propstat/D:prop/D:displayname};file" onClick="refreshButtons()"/></td>   
            <td nowrap="nowrap">
                <xsl:if test="position() mod 2 = 1">
                    <xsl:attribute name="bgcolor">#eeffdd</xsl:attribute>
                </xsl:if>
                <a href="{D:href}">
                    <xsl:if test="D:propstat/D:prop/D:ishidden = '1'">
                        <xsl:attribute name="class">hidden</xsl:attribute>
                    </xsl:if>
                    <xsl:value-of select="D:propstat/D:prop/D:displayname"/>
                </a>
            </td>
            <td align="right">
                <xsl:if test="position() mod 2 = 1">
                    <xsl:attribute name="bgcolor">#eeffdd</xsl:attribute>
                </xsl:if>
                <xsl:choose>
                    <xsl:when test="number(D:propstat/D:prop/D:getcontentlength) > 1024">
                        <xsl:value-of select="format-number(round(number(D:propstat/D:prop/D:getcontentlength) div 1024), '#,##0 KB')"/>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:value-of select="format-number(D:propstat/D:prop/D:getcontentlength, '#,##0 bytes')"/>
                    </xsl:otherwise>
                </xsl:choose>
            </td>
            <td class="properties">
                <xsl:if test="position() mod 2 = 1">
                    <xsl:attribute name="bgcolor">#ddeecc</xsl:attribute>
                </xsl:if>
                <xsl:apply-templates select="D:propstat/D:prop" mode="properties"/>
            </td>
            <!-- <td nowrap="nowrap">
                <xsl:if test="position() mod 2 = 1">
                    <xsl:attribute name="bgcolor">#eeffdd</xsl:attribute>
                </xsl:if>
                 <button onclick="getMetadata('{D:href}')">Metadata</button>  
                <button onclick="getFilePermission('{D:href}')">Access Control</button> 
            </td> -->
        </tr>
    </xsl:template>
    <xsl:template match="D:prop" mode="properties">
        <xsl:value-of select="D:getlastmodified"/>
        <xsl:if test="D:getcontenttype != 'application/octet-stream'">
            <br/><xsl:value-of select="D:getcontenttype"/>
        </xsl:if>
        <xsl:if test="D:isreadonly = '1'">
            <br/><xsl:text>Read-Only</xsl:text>
        </xsl:if>
        <xsl:if test="D:ishidden = '1'">
            <br/><i><xsl:text>Hidden</xsl:text></i>
        </xsl:if>
    </xsl:template>
</xsl:stylesheet>
