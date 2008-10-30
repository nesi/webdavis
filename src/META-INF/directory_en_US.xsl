<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:D="DAV:">
    <xsl:output method="html"/>
    <xsl:param name="href"/>
    <xsl:param name="url"/>
    <xsl:param name="unc"/>
    <xsl:template match="/">
        <html>
            <head>
                <title>Davis - <xsl:value-of select="$url"/></title>
                <meta HTTP-EQUIV="Pragma" CONTENT="no-cache"/>
                <meta HTTP-EQUIV="Cache-Control" CONTENT="no-cache"/>
                <meta HTTP-EQUIV="Expires" CONTENT="0"/>
    			<style type="text/css">
    @import "dojoroot/dijit/themes/tundra/tundra.css";
    @import "dojoroot/dojox/grid/resources/Grid.css";
    @import "dojoroot/dojox/grid/resources/tundraGrid.css";
    @import "dojoroot/dojo/resources/dojo.css"
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
        width: 500px;
        height: 200px;
    }
    table { 
    	border: none; 
    }
    
                </style>
    			<script type="text/javascript" src="dojoroot/dojo/dojo.js" djConfig="isDebug:true, parseOnLoad: true"></script>
    			<script type="text/javascript">
    // Load Dojo's code relating to the Button widget
    dojo.require("dojox.grid.compat.Grid");
    dojo.require("dojox.grid.DataGrid");
	dojo.require("dojox.grid.compat._grid.edit");
    dojo.require("dojo.data.ItemFileWriteStore");
    dojo.require("dojo.data.ItemFileReadStore");
    dojo.require("dijit.form.Button");
    dojo.require("dijit.Dialog");
	dojo.require("dijit.form.TextBox");
	dojo.require("dojo.parser");
    var layout1= [
        { field: "name", width: "200px", name: "Name"},
        { field: "value", width: "200px", name: "Value"}
    ];

    var layout2= [
        { field: "username", width: "200px", name: "Username"},
        { field: "domain", width: "200px", name: "Domain"},
        { field: "permission", width: "100px", name: "Permission"}
    ];
	var model2 = new dojox.grid.data.Table(null, []);
	var store2=new dojo.data.ItemFileWriteStore({data: []});
	var handle = dojo.connect(store2, "onSet", onPackageEditChange); 
	function onPackageEditChange(item, attribute, oldValue, newValue)
	{
   // If the 2 values are the same then they really did not change.
   if (oldValue == newValue)
   {
      console.log("Attribute: " + attribute + " on package: " + item.name + "
did not change.");
   }
   else
   {  
      // TODO: Save the updated data.
      console.log("Attribute: " + attribute + " on package: " + item.name + "
was changed from: " + oldValue + " to: " + newValue);  
   
      console.dir({"The updated data store":testdata});
   }
	} 

	function getPermission(urlString){
	  	dojo.xhrPost({
    		url: urlString+"?method=permission",
    		load: function(responseObject, ioArgs){
      
      			var textBuffer = ["The data returned is:"];
				console.dir(responseObject);  // Dump it to the console
          		console.dir(responseObject.items[0].username);  // Prints username     			
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
	function populatePermission(perms){
		console.dir(perms);
//		model2 = new dojox.grid.data.Table(null, perms);
		store2=new dojo.data.ItemFileWriteStore({data: perms});
		console.dir(store2);
//		model = new dojox.grid.data.DojoData();
//		alert("after new model");
//		model.jsId="permGrid";
//		model.store=perms;
//		model.query="{ name : '*' }";
//		alert("model:"+model);
/*		var grid = new dojox.grid.DataGrid({
					"id": "permGrid",
					"model": model,
					"structure": layout2
		});*/
		permissionGrid.setStore(store2);
//		dojo.byId("permissionGrid").appendChild(grid.domNode);
//		alert("b4 start grid.");
//		grid.startup();
//		grid.render();	
//		permissionGrid.refresh();		
//		dijit.byId('dialog2').show();
	}
	function getMetadata(url){
		dijit.byId('dialog1').show();
	}
	function getFilePermission(url){
		dojo.byId("recursive").disabled=true;
		dijit.byId('dialog2').show();
		getPermission(url);
	}
	function getDirPermission(url){
		dojo.byId("recursive").disabled=false;
		dijit.byId('dialog2').show();
	}

    			</script>		
            </head>
            <body class="tundra">
            <!-- Dialogs begin-->
            	<div dojoType="dijit.Dialog" id="dialog1" title="Metadata" execute="checkPw(arguments[0]);">
				<table>
					<tr>
						<td>
        					<button onclick="dijit.byId('metadataGrid').refresh()">Refresh</button>
        					<button onclick="addRow()">Add Metadata</button>
        					<button onclick="dijit.byId('metadataGrid').removeSelectedRows()">Remove Metadata</button>
        					<button onclick="dijit.byId('metadataGrid').edit.apply()">Save</button>
        					<button onclick="dijit.byId('metadataGrid').edit.cancel()">Cancel</button>
						</td>
					</tr>
					<tr>
						<td>
							<div id="metadataGrid" dojoType="dojox.grid.DataGrid" structure="layout1"></div>
						</td>
					</tr>
				</table>
				</div>
				<div dojoType="dijit.Dialog" id="dialog2" title="Permissions" execute="checkPw(arguments[0]);">
				<table>
					<tr>
						<td width="500px">   <!-- dojoType="" structure="layout2" dojox.Grid store="store2"-->
							<div id="permissionGrid"  structure="layout2" dojoType="dojox.grid.DataGrid" jsId="permissionGrid"></div>
						</td>
						<td valign="top">
							<table>
								<tr>
									<td>Username</td>
									<td><input type="text" name="username" value="" dojoType="dijit.form.TextBox"/></td>
								</tr>
								<tr>
									<td>Domain</td>
									<td><input type="text" name="domain" value="" dojoType="dijit.form.TextBox"/></td>
								</tr>
								<tr>
									<td>Permission</td>
									<td>
										<select typdojoType="dijit.form.Select" name="permission">
											<option value="a">all</option>
											<option value="w">write</option>
											<option value="r">read</option>
											<option value="c">curate</option>
											<option value="n">null</option>
											<option value="t">annotate</option>
											<option value="o">owner</option>
										</select>
									</td>
								</tr>
								<tr>
									<td>Recursive</td>
									<td><input type="checkbox" name="recursive" id="recursive" dojoType="dijit.form.CheckBox"/></td>
								</tr>
								<tr>
									<td rowspan="2" align="center">
										<button onclick="dijit.byId('gridNode').edit.apply()">Apply</button>
										<button onclick="dijit.byId('gridNode').edit.cancel()">Cancel</button>
									</td>
								</tr>
							</table>
						</td>
					</tr>
				</table>
				</div>
            <!-- Dialogs end -->
                <xsl:apply-templates select="D:multistatus"/>
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
                <table border="0" cellpadding="0" cellspacing="0">
                    <tr valign="top">
                        <xsl:if test="D:response[D:href != $href][D:propstat/D:prop/D:resourcetype/D:collection]">
                            <td>
                                <table border="0" cellpadding="6" cellspacing="0">
                                    <tr valign="top">
                                        <td nowrap="nowrap">
                                            <xsl:apply-templates select="D:response[D:href != $href][D:propstat/D:prop/D:resourcetype/D:collection]" mode="directory">
                                                <xsl:sort select="D:propstat/D:prop/D:displayname"/>
                                            </xsl:apply-templates>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </xsl:if>
                        <xsl:if test="D:response[not(D:propstat/D:prop/D:resourcetype/D:collection)]">
                            <td>
                                <table border="0" cellpadding="6" cellspacing="0">
                                    <xsl:apply-templates select="D:response[not(D:propstat/D:prop/D:resourcetype/D:collection)]" mode="file">
                                        <xsl:sort select="D:propstat/D:prop/D:displayname"/>
                                    </xsl:apply-templates>
                                </table>
                            </td>
                        </xsl:if>
                    </tr>
                </table>
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
            <a class="title" href="{$href}" folder="{$href}"><xsl:value-of select="$url"/></a><br/>
            <a class="unc" href="{$href}" folder="{$href}"><xsl:value-of select="$unc"/></a><br/>
            <xsl:text>Last modified on </xsl:text>
            <xsl:value-of select="D:propstat/D:prop/D:getlastmodified"/>
            <xsl:text>.</xsl:text>
            <xsl:if test="$url != 'smb://'">
                <br/><a href="." class="parent">Parent</a>
            </xsl:if>
        </p>
    </xsl:template>
    <xsl:template match="D:response" mode="directory">
        <xsl:if test="position() != 1"><br/></xsl:if>
        <a href="{D:href}" class="directory">
            <xsl:if test="D:propstat/D:prop/D:ishidden = '1'">
                <xsl:attribute name="class">hiddendirectory</xsl:attribute>
            </xsl:if>
            <xsl:value-of select="D:propstat/D:prop/D:displayname"/>
        </a>
    </xsl:template>
    <xsl:template match="D:response" mode="file">
        <tr valign="top">
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
            <td nowrap="nowrap">
                <xsl:if test="position() mod 2 = 1">
                    <xsl:attribute name="bgcolor">#eeffdd</xsl:attribute>
                </xsl:if>
                <button onclick="getMetadata('{D:href}')">M</button>
                <button onclick="getFilePermission('{D:href}')">P</button>
            </td>
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
