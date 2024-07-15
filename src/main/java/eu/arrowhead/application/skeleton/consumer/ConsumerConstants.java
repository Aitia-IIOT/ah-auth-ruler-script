package eu.arrowhead.application.skeleton.consumer;

import java.util.Arrays;
import java.util.List;

public final class ConsumerConstants {

	//=================================================================================================
	// members
	public static final String AUTHORIZATION = "authorization";
	public static final String INTERFACE_SECURE = "HTTP-SECURE-JSON";
	public static final String INTERFACE_INSECURE = "HTTP-INSECURE-JSON";
	public static final String QUERY_GET_SYSTEMS = "/mgmt/systems";
	public static final String QUERY_GET_SERVICES = "/mgmt/services";
	public static final String QUERY_GET_INTERFACES = "/mgmt/interfaces";
	public static final String QUERY_AUTH_INTRA_CLOUD = "/mgmt/intracloud";
	public static final List<String> DEFAULT_INTERFACE_NAMES = (List<String>) Arrays.asList(INTERFACE_SECURE, INTERFACE_INSECURE);
	public static final String SCHEME = "scheme";
	public static final String HOST = "host";
	public static final String PORT = "port";
	public static final String PATH = "path";
	public static final String METADATA_SCHEME_STRING = "=";
	//=================================================================================================
	// assistant methods

	//-------------------------------------------------------------------------------------------------
	private ConsumerConstants() {
		throw new UnsupportedOperationException();
	}
}
