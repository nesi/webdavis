package webdavis;

import java.io.File;
import java.util.Comparator;
import org.irods.jargon.core.pub.io.IRODSFile;

public class ListingComparator implements Comparator<Object> {
			
	private boolean sortAscending = true;
	private String sortField = "name";
	
	public void setSortAscending(boolean value) {			
		sortAscending = value;
	}
	
	public void setSortField(String value) {			
		sortField = value;
	}
	
	public boolean getSortAscending() {
		return sortAscending;
	}
	
	public String getSortField() {
		return sortField;
	}

	public int compare(Object file1, Object file2) {
		if (sortField.equals("name")) { // File name column
			if (((CachedFile) file1).isDirectory()	&& !((CachedFile) file2).isDirectory()) // Keep directories separate from files
				return -1 * (sortAscending ? 1 : -1);
			if (!((CachedFile) file1).isDirectory() && ((CachedFile) file2).isDirectory())
				return (sortAscending ? 1 : -1);
			return (((CachedFile) file1).getName().toLowerCase().compareTo(((CachedFile) file2).getName().toLowerCase()))	* (sortAscending ? 1 : -1);
		} else if (sortField.equals("size")) {
			if (((CachedFile) file1).isDirectory()	&& !((CachedFile) file2).isDirectory()) // Keep directories separate from files
				return -1 * (sortAscending ? 1 : -1);
			if (!((CachedFile) file1).isDirectory() && ((CachedFile) file2).isDirectory())
				return (sortAscending ? 1 : -1);
			return (new Long(((CachedFile) file1).length()).compareTo(new Long(((CachedFile) file2).length()))) * (sortAscending ? 1 : -1);
		} else if (sortField.equals("date")) {
			return (new Long(((CachedFile) file1).lastModified()).compareTo(new Long(((CachedFile) file2).lastModified()))) * (sortAscending ? 1 : -1);
		} else if (sortField.equals("sharing")) {
			return ((CachedFile)file1).getSharingValue().compareTo(((CachedFile)file2).getSharingValue())* (sortAscending ? 1 : -1);
		}

		return 0; 
	}
}
