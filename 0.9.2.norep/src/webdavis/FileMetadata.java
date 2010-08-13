package webdavis;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import edu.sdsc.grid.io.RemoteFile;
import edu.sdsc.grid.io.RemoteFileSystem;

public class FileMetadata extends RemoteFile {

	private HashMap<String, ArrayList<String>> metadata = new HashMap<String, ArrayList<String>>();
	
	
	public FileMetadata(RemoteFileSystem rfs, String path, String filename)	throws NullPointerException {

		super(rfs, path, filename);
	}
	
	public void addItem(String name, String value) {
		
		ArrayList<String> list = metadata.get(name);
		if (list == null) {
			list = new ArrayList<String>();
			metadata.put(name, list);
		}
		list.add(value);
	}
	
	public void replicate(String arg0) throws IOException {}

	public String getResource() throws IOException {
		
		return null;
	}
	
	public HashMap<String, ArrayList<String>> getMetadata() {
	
		return metadata;
	}
	
	public String toString() {
		
		return "file="+super.toString()+" metadata="+metadata;
	}
}
