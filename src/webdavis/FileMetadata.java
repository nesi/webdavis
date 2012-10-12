package webdavis;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

public class FileMetadata  {

	private HashMap<String, ArrayList<String>> metadata = new HashMap<String, ArrayList<String>>();
	
	
	
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
