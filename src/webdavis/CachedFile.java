package webdavis;

import java.io.File;
import java.io.IOException;

import edu.sdsc.grid.io.RemoteFile;
import edu.sdsc.grid.io.RemoteFileSystem;

public class CachedFile extends RemoteFile {
	private long length;
	private boolean isDir;
	private long lastModified;
	private boolean canWrite;
	private String canonicalPath;
	
	public CachedFile(RemoteFileSystem rfs, String path, String filename)
			throws NullPointerException {
		super(rfs, path, filename);
		// TODO Auto-generated constructor stub
	}

	@Override
	public String getResource() throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void replicate(String arg0) throws IOException {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean canWrite() {
		// TODO Auto-generated method stub
		return this.canWrite;
	}

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return super.getName();
	}

	@Override
	public boolean isDirectory() {
		// TODO Auto-generated method stub
		return isDir;
	}

	@Override
	public boolean isFile() {
		// TODO Auto-generated method stub
		return !isDir;
	}

	@Override
	public boolean isHidden() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public long lastModified() {
		// TODO Auto-generated method stub
		return this.lastModified;
	}

	@Override
	public long length() {
		// TODO Auto-generated method stub
		return length;
	}
	public void setLength(long length){
		this.length=length;
	}

	@Override
	public boolean setLastModified(long arg0) {
		// TODO Auto-generated method stub
		this.lastModified=arg0;
		return true;
	}
	
	public void setDirFlag(boolean isDir) {
		this.isDir=isDir;
	}
	public void setCanWriteFlag(boolean canWrite){
		this.canWrite=canWrite;
	}

	public String getCanonicalPath() {
		return getPath()+File.separator+getName();
	}

	public void setCanonicalPath(String canonicalPath) {
		this.canonicalPath = canonicalPath;
	}
}
