package org.eclipse.ceylon.cmr.repository.webdav;

import java.io.InputStream;

import org.eclipse.ceylon.aether.github.sardine.impl.io.ContentLengthInputStream;

public class WebDAVInputStream {

	private ContentLengthInputStream src;

	WebDAVInputStream(ContentLengthInputStream src) {
		this.src = src;
	}

	public Long getLength() {
		return src.getLength();
	}

	public InputStream getInputStream() {
		return src;
	}

}
