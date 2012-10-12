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
			if (((IRODSFile) file1).isDirectory()	&& !((IRODSFile) file2).isDirectory()) // Keep directories separate from files
				return -1 * (sortAscending ? 1 : -1);
			if (!((IRODSFile) file1).isDirectory() && ((IRODSFile) file2).isDirectory())
				return (sortAscending ? 1 : -1);
			return (((IRODSFile) file1).getName().toLowerCase().compareTo(((IRODSFile) file2).getName().toLowerCase()))	* (sortAscending ? 1 : -1);
		} else if (sortField.equals("size")) {
			if (((IRODSFile) file1).isDirectory()	&& !((IRODSFile) file2).isDirectory()) // Keep directories separate from files
				return -1 * (sortAscending ? 1 : -1);
			if (!((IRODSFile) file1).isDirectory() && ((IRODSFile) file2).isDirectory())
				return (sortAscending ? 1 : -1);
			return (new Long(((IRODSFile) file1).length()).compareTo(new Long(((IRODSFile) file2).length()))) * (sortAscending ? 1 : -1);
		} else if (sortField.equals("date")) {
			return (new Long(((IRODSFile) file1).lastModified()).compareTo(new Long(((IRODSFile) file2).lastModified()))) * (sortAscending ? 1 : -1);
		} else if (sortField.equals("sharing")) {
			return ((CachedFile)file1).getSharingValue().compareTo(((CachedFile)file2).getSharingValue())* (sortAscending ? 1 : -1);
		}

		return 0; 
	}
}
