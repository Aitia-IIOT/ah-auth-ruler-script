package eu.arrowhead.application.skeleton.consumer.data;

import java.util.List;

public class AuthRule {

	//=================================================================================================
	// members

	private String consumer;
	private String provider;
	private String service;
	private List<String> interfaces;

	//=================================================================================================
	// methods

	//-------------------------------------------------------------------------------------------------
	public AuthRule() {
	}

	//-------------------------------------------------------------------------------------------------
	public AuthRule(final String consumer, final String provider, final String service, final List<String> interfaces) {
		this.consumer = consumer;
		this.provider = provider;
		this.service = service;
		this.interfaces = interfaces;
	}

	//-------------------------------------------------------------------------------------------------
	public String getConsumer() {
		return this.consumer;
	}

	//-------------------------------------------------------------------------------------------------
	public String getProvider() {
		return this.provider;
	}

	//-------------------------------------------------------------------------------------------------
	public String getService() {
		return this.service;
	}

	//-------------------------------------------------------------------------------------------------
	public List<String> getInterfaces() {
		return this.interfaces;
	}

	//-------------------------------------------------------------------------------------------------
	public void setConsumer(final String consumer) {
		this.consumer = consumer;
	}

	//-------------------------------------------------------------------------------------------------
	public void setProvider(final String provider) {
		this.provider = provider;
	}

	//-------------------------------------------------------------------------------------------------
	public void setService(final String service) {
		this.service = service;
	}

	//-------------------------------------------------------------------------------------------------
	public void setInterfaces(final List<String> interfaces) {
		this.interfaces = interfaces;
	}

	//-------------------------------------------------------------------------------------------------
	public String toString() {
		return new String("consumer: " + consumer + ", provider: " + provider + ", service: " + service + ", interfaces: " + interfaces.toString());
	}
}
