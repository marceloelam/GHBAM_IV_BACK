package br.com.iv.util;

import java.io.Serializable;
import java.text.SimpleDateFormat;

public class GerarIdUnico implements Serializable {

	private static final long serialVersionUID = 1L; // = 8024334484927119750L;

	public String gerarId() {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmssSSSS");
		String id = sdf.format(System.currentTimeMillis());
		System.out.println("id:" + id);
		return id;
	}
}
