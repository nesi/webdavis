package webdavis;

import java.util.Comparator;
import edu.sdsc.grid.io.GeneralFile;

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
			if (((GeneralFile) file1).isDirectory()	&& !((GeneralFile) file2).isDirectory()) // Keep directories separate from files
				return -1 * (sortAscending ? 1 : -1);
			if (!((GeneralFile) file1).isDirectory() && ((GeneralFile) file2).isDirectory())
				return (sortAscending ? 1 : -1);
			return (((GeneralFile) file1).getName().toLowerCase().compareTo(((GeneralFile) file2).getName().toLowerCase()))	* (sortAscending ? 1 : -1);
		} else if (sortField.equals("size")) {
			if (((GeneralFile) file1).isDirectory()	&& !((GeneralFile) file2).isDirectory()) // Keep directories separate from files
				return -1 * (sortAscending ? 1 : -1);
			if (!((GeneralFile) file1).isDirectory() && ((GeneralFile) file2).isDirectory())
				return (sortAscending ? 1 : -1);
			return (new Long(((GeneralFile) file1).length()).compareTo(new Long(((GeneralFile) file2).length()))) * (sortAscending ? 1 : -1);
		} else if (sortField.equals("date")) {
			return (new Long(((GeneralFile) file1).lastModified()).compareTo(new Long(((GeneralFile) file2).lastModified()))) * (sortAscending ? 1 : -1);
		}

		return 0; // ###TBD comparator for metadata
	}
}
