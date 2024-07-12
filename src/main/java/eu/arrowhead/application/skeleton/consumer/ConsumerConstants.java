package eu.arrowhead.application.skeleton.consumer;

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
	//=================================================================================================
	// assistant methods

	//-------------------------------------------------------------------------------------------------
	private ConsumerConstants() {
		throw new UnsupportedOperationException();
	}
}
