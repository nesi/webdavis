package webdavis;

import java.util.Hashtable;

import webdavis.DefaultPostHandler.Tracker;

public class ClientInstance {
	
	private CachedFile[] fileListCache; // File listings cache from last server query
	private Tracker tracker;

	public void setFileListCache(CachedFile[] list) {
		fileListCache = list;
	}
	
	public void setTracker(Tracker tracker) {
		this.tracker = tracker;
	}

	public Tracker getTracker() {
		return tracker;
	}
	
	public CachedFile[] getFileListCache() {
		return fileListCache;
	}
}
