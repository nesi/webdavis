package webdavis;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.JSONParser;

import edu.sdsc.grid.io.GeneralMetaData;
import edu.sdsc.grid.io.MetaDataCondition;
import edu.sdsc.grid.io.MetaDataRecordList;
import edu.sdsc.grid.io.MetaDataSelect;
import edu.sdsc.grid.io.MetaDataSet;
import edu.sdsc.grid.io.MetaDataTable;
import edu.sdsc.grid.io.RemoteFile;
import edu.sdsc.grid.io.RemoteFileSystem;
import edu.sdsc.grid.io.UserMetaData;
import edu.sdsc.grid.io.irods.IRODSFile;
import edu.sdsc.grid.io.irods.IRODSFileInputStream;
import edu.sdsc.grid.io.irods.IRODSFileSystem;
import edu.sdsc.grid.io.srb.SRBFile;
import edu.sdsc.grid.io.srb.SRBFileInputStream;
import edu.sdsc.grid.io.srb.SRBFileSystem;
import edu.sdsc.grid.io.srb.SRBMetaDataRecordList;
import edu.sdsc.grid.io.srb.SRBMetaDataSet;
import edu.sdsc.grid.io.MetaDataField;

/**
 * Default implementation of a handler for requests using the HTTP POST method.
 * 
 * @author Eric Glass
 */
public class DefaultPostHandler extends AbstractHandler {

	/**
	 * Services requests which use the HTTP POST method. This may, at some
	 * point, implement some sort of useful behavior. Right now it doesn't do
	 * anything.
	 * 
	 * @param request
	 *            The request being serviced.
	 * @param response
	 *            The servlet response.
	 * @param auth
	 *            The user's authentication information.
	 * @throws SerlvetException
	 *             If an application error occurs.
	 * @throws IOException
	 *             If an IO error occurs while handling the request.
	 */
	public void service(HttpServletRequest request,
			HttpServletResponse response, DavisSession davisSession)
			throws ServletException, IOException {
		String method = request.getParameter("method");
		String url = getRemoteURL(request, getRequestURL(request),
				getRequestURICharset());
		Log.log(Log.DEBUG, "url:" + url + " method:" + method);
		RemoteFile file = getRemoteFile(request, davisSession);
		Log.log(Log.DEBUG, "GET Request for resource \"{0}\".", file);
		if (!file.exists()) {
			Log.log(Log.DEBUG, "File does not exist.");
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		String requestUrl = getRequestURL(request);
		Log.log(Log.DEBUG, "Request URL: {0}", requestUrl);
		StringBuffer str = new StringBuffer();
		if (method.equalsIgnoreCase("permission")) {
			String username = request.getParameter("username");
			if (username != null) {
				String domain = request.getParameter("domain");
				String permission = request.getParameter("permission");
				boolean recursive = false;
				try {
					recursive = Boolean.getBoolean(request
							.getParameter("recursive"));
				} catch (Exception _e) {
				}
				if (file.getFileSystem() instanceof SRBFileSystem) {
					((SRBFile) file).changePermissions(permission, username,
							domain, recursive);
				} else if (file.getFileSystem() instanceof IRODSFileSystem) {
					// permissions=((IRODSFile)file).getPermissions(true);
				}

			}

			MetaDataRecordList[] permissions = null;
			if (file.getFileSystem() instanceof SRBFileSystem) {
				permissions = ((SRBFile) file).getPermissions(true);
			} else if (file.getFileSystem() instanceof IRODSFileSystem) {
				// permissions=((IRODSFile)file).getPermissions(true);
			}
			str.append("{\nitems:[");
			if (permissions != null) {
				for (int i = 0; i < permissions.length; i++) {
					// for (MetaDataField f:p.getFields()){
					// Log.log(Log.DEBUG, f.getName()+" "+p.getValue(f));
					// }
					if (i > 0)
						str.append(",\n");
					else
						str.append("\n");
					// "user name"
					str.append("{username:'").append(
							permissions[i].getValue(SRBMetaDataSet.USER_NAME))
							.append("', ");
					// "user domain"
					str
							.append("domain:'")
							.append(
									permissions[i]
											.getValue(SRBMetaDataSet.USER_DOMAIN))
							.append("', ");
					// "file access constraint"
					str
							.append("permission:'")
							.append(
									permissions[i]
											.getValue(SRBMetaDataSet.ACCESS_CONSTRAINT))
							.append("'}");
				}
			}
			str.append("\n");
			str.append("]}");

		} else if (method.equalsIgnoreCase("metadata")) {

			if (request.getContentLength() > 0) {
		        InputStream input = request.getInputStream();
		        byte[] buf = new byte[request.getContentLength()];
		        int count=input.read(buf);
		        Log.log(Log.DEBUG, "read:"+count);
		        Log.log(Log.DEBUG, "received metadata: " + new String(buf));

				// for testing
//				String line = null;
//				StringBuffer buffer = new StringBuffer();
//				while ((line = reader.readLine()) != null) {
//					buffer.append(line);
//				}
//				Log.log(Log.DEBUG, "received metadata: " + buffer);

				Object obj=JSONValue.parse(new String(buf));
				JSONArray array=(JSONArray)obj;

				if (array != null) {
					String[][] definableMetaDataValues = new String[array
							.size()][2];

					for (int i = 0; i < array.size(); i++) {
						definableMetaDataValues[i][0] = (String) ((JSONObject) array
								.get(i)).get("name");
						definableMetaDataValues[i][1] = (String) ((JSONObject) array
								.get(i)).get("value");

					}

					int[] operators = new int[definableMetaDataValues.length];
					MetaDataTable metaDataTable = null;

					if (file.getFileSystem() instanceof SRBFileSystem) {
						MetaDataRecordList rl;
						MetaDataField mdf=null;
						if (!file.isDirectory()){
							mdf=SRBMetaDataSet.getField(SRBMetaDataSet.DEFINABLE_METADATA_FOR_FILES);
						}else{
							mdf=SRBMetaDataSet.getField(SRBMetaDataSet.DEFINABLE_METADATA_FOR_DIRECTORIES);
						}
						if (mdf!=null){
							rl = new SRBMetaDataRecordList(mdf,(MetaDataTable) null);
							file.modifyMetaData(rl);
							metaDataTable = new MetaDataTable(operators,
									definableMetaDataValues);
							rl = new SRBMetaDataRecordList(mdf,metaDataTable);
							file.modifyMetaData(rl);
						}

					}

				}
			}

			MetaDataCondition[] conditions;
			MetaDataTable metaDataTable = null;
			MetaDataSelect[] selects=null;
			MetaDataRecordList[] rl = null;
			if (file.getFileSystem() instanceof SRBFileSystem) {
				// conditions = new MetaDataCondition[0];
				// conditions[0] = MetaDataSet.newCondition(
				// SRBMetaDataSet.DEFINABLE_METADATA_FOR_FILES, metaDataTable );

				if (!file.isDirectory()){
					selects = new MetaDataSelect[1];
					// "definable metadata for files"
					selects[0] = MetaDataSet
							.newSelection(SRBMetaDataSet.DEFINABLE_METADATA_FOR_FILES);
				}else{
					selects = new MetaDataSelect[1];
					// "definable metadata for files"
					selects[0] = MetaDataSet
							.newSelection(SRBMetaDataSet.DEFINABLE_METADATA_FOR_DIRECTORIES);
				}
				if (selects!=null){
					rl = file.query(selects);
				}

			}
			str.append("{\nitems:[");
			boolean b = false;
			if (rl != null) { // Nothing in the database matched the query
				for (int i = 0; i < rl.length; i++) {
//					if (i > 0)
//						str.append(",\n");
//					else
//						str.append("\n");

					int metaDataIndex;
					if (file.isDirectory())
						metaDataIndex = rl[i]
								.getFieldIndex(SRBMetaDataSet.DEFINABLE_METADATA_FOR_DIRECTORIES);
					else
						metaDataIndex = rl[i]
								.getFieldIndex(SRBMetaDataSet.DEFINABLE_METADATA_FOR_FILES);
					if (metaDataIndex > -1) {
						MetaDataTable t = rl[i].getTableValue(metaDataIndex);
						for (int j = 0; j < t.getRowCount(); j++) {
							if (b)
								str.append(",\n");
							else
								str.append("\n");
							str.append("{name:'");
							str.append(t.getStringValue(j, 0)).append("', ");
							str.append("value:'")
									.append(t.getStringValue(j, 1)).append("'}");
							b = true;
						}
					}

					// "definable metadata for files"
					// String[]
					// lines=rl[i].getValue(SRBMetaDataSet.DEFINABLE_METADATA_FOR_FILES).toString().split("\n");
					// boolean b=false;
					// for (int j=0;j<lines.length;j++){
					// if (b)
					// str.append(",\n");
					// else
					// str.append("\n");
					// if (lines[j].length()>0){
					// str.append("{name:'");
					// str.append(lines[j].replaceAll(" = ",
					// "', value:'").trim());
					// str.append("'}");
					// b=true;
					// }
					// }
					// str.append("{name:'").append(rl[i].).append("', ");
					// str.append("value:'").append(permissions[i].getValue("file access constraint")).append("'}");
					// for (int j=0;j<rl[i].getFieldCount();j++){
					// System.out.println("field name: "+rl[i].getFieldName(j));
					// System.out.println("value: "+rl[i].getValue(j));
					// }
				}
			}
			str.append("\n");
			str.append("]}");

		}

		ServletOutputStream op = response.getOutputStream();
		byte[] buf = str.toString().getBytes();
		Log.log(Log.DEBUG, "output(" + buf.length + "):\n" + str);
		op.write(buf);
		op.flush();
		op.close();

	}

}
