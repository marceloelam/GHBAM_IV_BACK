package br.com.iv.util;

import java.io.Serializable;

import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

public class FormData implements Serializable {
	private static final long serialVersionUID = 1L;

	@RestForm("file")
	private FileUpload file;

	public FileUpload getFile() {
		return file;
	}

}
