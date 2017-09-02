package org.tio.im.common.http;

import java.util.Objects;

/**
 * @author tanyaowu 
 * 2017年6月28日 下午2:23:16
 */
public enum Method {
	GET("GET"), POST("POST"),HEAD("HEAD"), PUT("PUT"), TRACE("TRACE"), OPTIONS("OPTIONS"), PATCH("PATCH");
	String value;

	private Method(String value) {
		this.value = value;
	}

	public static Method from(String method) {
		Method[] values = Method.values();
		for (Method v : values) {
			if (Objects.equals(v.value, method)){
				return v;
			}
		}
		return GET;
	}

	public String getValue() {
		return value;
	}
}