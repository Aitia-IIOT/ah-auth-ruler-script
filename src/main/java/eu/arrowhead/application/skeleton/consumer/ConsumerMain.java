package eu.arrowhead.application.skeleton.consumer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.HttpMethod;
import org.springframework.util.Assert;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ai.aitia.arrowhead.application.library.ArrowheadService;
import eu.arrowhead.application.skeleton.consumer.data.AuthRule;
import eu.arrowhead.application.skeleton.consumer.data.AuthorizationIntraCloudListResponseDTO;
import eu.arrowhead.application.skeleton.consumer.data.AuthorizationIntraCloudRequestDTO;
import eu.arrowhead.application.skeleton.consumer.data.AuthorizationIntraCloudResponseDTO;
import eu.arrowhead.application.skeleton.consumer.data.ServiceDefinitionsListResponseDTO;
import eu.arrowhead.application.skeleton.consumer.data.ServiceInterfacesListResponseDTO;
import eu.arrowhead.application.skeleton.consumer.data.SystemListResponseDTO;
import eu.arrowhead.common.CommonConstants;
import eu.arrowhead.common.SSLProperties;
import eu.arrowhead.common.Utilities;
import eu.arrowhead.common.dto.shared.ServiceDefinitionResponseDTO;
import eu.arrowhead.common.dto.shared.ServiceInterfaceResponseDTO;
import eu.arrowhead.common.dto.shared.SystemResponseDTO;

@SpringBootApplication
@ComponentScan(basePackages = { CommonConstants.BASE_PACKAGE, "ai.aitia" })
public class ConsumerMain implements ApplicationRunner {

	// =================================================================================================
	// members

	@Autowired
	private ArrowheadService arrowheadService;

	@Autowired
	private SSLProperties sslProperties;

	@Value("#{${default_interface_name_list}}")
	private List<String> defaultInterfaceNames;

	private final Logger logger = LogManager.getLogger(ConsumerMain.class);

	private List<SystemResponseDTO> systems;

	private List<ServiceDefinitionResponseDTO> services;

	private List<ServiceInterfaceResponseDTO> interfaces;

	// =================================================================================================
	// methods

	// ------------------------------------------------------------------------------------------------
	public static void main(final String[] args) {
		if (args.length != 1) {
			System.out.println("You must specify exactly one path! (E.g.: ../example.json)");
		} else {
			SpringApplication.run(ConsumerMain.class, args);
		}
	}

	// -------------------------------------------------------------------------------------------------
	@Override
	public void run(final ApplicationArguments args) throws Exception {

		List<AuthRule> newRules = getRules(args.getSourceArgs()[0]);

		if (newRules == null) {
			System.out.println("Reading the file was unsuccessful!");
			return;
		}

		systems = getSystems();
		services = getServices();
		interfaces = getInterfaces();

		updateAuthRules(newRules);
	}

	// -------------------------------------------------------------------------------------------------
	private List<AuthRule> getRules(final String filename) {

		JsonNode newRulesJson;
		try {
			newRulesJson = new ObjectMapper().readTree(new File(filename));
		} catch (IOException e) {
			logger.error(e.getMessage());
			return null;
		}
		List<AuthRule> rules = new ArrayList<>();

		for (JsonNode rule : newRulesJson) {
			try {
				AuthRule newRule = Utilities.fromJson(rule.toString(), AuthRule.class);
				// Setting the defaults if no interfaces are given
				if (newRule.getInterfaces().size() == 0) {
					newRule.setInterfaces(new ArrayList<String>(defaultInterfaceNames));
				}
				rules.add(newRule);
			} catch (Exception e) {
				logger.error("Could not create authorization rule (invalid json format): " + e.getMessage());
			}
		}
		return rules;
	}

	// -------------------------------------------------------------------------------------------------
	private void updateAuthRules(final List<AuthRule> newRules) {
		deleteRules(newRules);
		addRules(newRules);
	}

	// -------------------------------------------------------------------------------------------------
	private void deleteRules(final List<AuthRule> rules) {
		List<Integer> ruleIdsToDelete = new ArrayList<Integer>();
		try {
			ruleIdsToDelete = getRuleIdsToDelete(getAuthorizationRules(), getSystemIdsToDelete(rules));
		} catch (Exception e) {
			logger.error(e.getMessage());
		}

		Map<String, String> authorizationUri = null;
		try {
			authorizationUri = getAuthorizationUri();
		} catch (Exception e) {
			logger.error(e.getMessage());
		}

		for (int id : ruleIdsToDelete) {
			logger.info("Removing authorization rule with id: " + id);
			String response = arrowheadService.consumeServiceHTTP(String.class, HttpMethod.DELETE,
					Utilities.createURI(authorizationUri.get("scheme"), authorizationUri.get("host"),
							Integer.parseInt(authorizationUri.get("port")), authorizationUri.get("path")
									+ ConsumerConstants.QUERY_AUTH_INTRA_CLOUD + "/" + Integer.toString(id)),
					null, null);
			logger.info("Http DELETE response: " + response);
		}
	}

	// -------------------------------------------------------------------------------------------------
	private void addRules(final List<AuthRule> rules) {

		List<AuthorizationIntraCloudRequestDTO> rulesToAdd = createDTOListFromAuthRules(rules);
		for (AuthorizationIntraCloudRequestDTO ruleToAdd : rulesToAdd) {
			addSingleRule(ruleToAdd);
		}
	}

	// =================================================================================================
	// assistant methods

	// -------------------------------------------------------------------------------------------------
	private List<AuthorizationIntraCloudRequestDTO> createDTOListFromAuthRules(final List<AuthRule> rules) {

		List<AuthorizationIntraCloudRequestDTO> dtoList = new ArrayList<>();
		Long consumerId = null;
		List<Long> providerId;
		List<Long> serviceDefinitionId;
		List<Long> interfaceIds;

		for (int i = 0; i < rules.size(); i++) {

			providerId = new ArrayList<>();
			serviceDefinitionId = new ArrayList<>();
			interfaceIds = new ArrayList<>();

			try {
				consumerId = getSystemIdByInfo(rules.get(i).getConsumer());

				providerId.add(getSystemIdByInfo(rules.get(i).getProvider()));

				serviceDefinitionId.add(serviceDefinitionToId(rules.get(i).getService()));

				for (String interfaceName : rules.get(i).getInterfaces()) {
					interfaceIds.add(interfaceNameToId(interfaceName));

				}

				dtoList.add(new AuthorizationIntraCloudRequestDTO(consumerId, providerId, serviceDefinitionId,
						interfaceIds));
			} catch (Exception e) {
				logger.error("Could not create authorization rule: " + rules.get(i).toString() + ", reason: "
						+ e.getMessage());
			}
		}
		return dtoList;

	}

	// -------------------------------------------------------------------------------------------------
	private void addSingleRule(final AuthorizationIntraCloudRequestDTO ruleToAdd) {
		Map<String, String> authorizationUri = null;
		try {
			authorizationUri = getAuthorizationUri();
		} catch (Exception e) {
			logger.error(e.getMessage());
		}

		logger.info("Sending the POST request for the following authorization rule: " + ruleToAdd.toString());
		AuthorizationIntraCloudListResponseDTO response = arrowheadService.consumeServiceHTTP(
				AuthorizationIntraCloudListResponseDTO.class, HttpMethod.POST,
				Utilities.createURI(authorizationUri.get("scheme"), authorizationUri.get("host"),
						Integer.parseInt(authorizationUri.get("port")),
						authorizationUri.get("path") + ConsumerConstants.QUERY_AUTH_INTRA_CLOUD),
				null, ruleToAdd);
		if (response == null) {
			logger.error("Could not apply the following authorization rule: " + ruleToAdd.toString());
		} else {
			logger.info("Successfully applied rule with id: " + response.getData().getFirst().getId());
		}
	}

	// -------------------------------------------------------------------------------------------------
	private List<Integer> getSystemIdsToDelete(final List<AuthRule> rules) {

		List<Integer> result = new ArrayList<>();

		for (AuthRule rule : rules) {
			try {
				int id = (int) getSystemIdByInfo(rule.getConsumer());
				if (!result.contains(id)) {
					result.add(id);
				}
			} catch (Exception e) {
				logger.error(e);
			}

		}

		return result;
	}

	// -------------------------------------------------------------------------------------------------
	// the system string can be systemname or metadata
	// (in case it is metadata, it must contain '=')
	private long getSystemIdByInfo(final String systemInfo) throws Exception {
		Assert.notNull(this.systems, "The ist of possible systems is empty!");
		for (SystemResponseDTO system : systems) {
			if (systemInfo.contains("=")) {
				String[] metaData = systemInfo.split("=");

				if (metaData.length != 2) {
					throw new IllegalArgumentException("System metadata is in invalid format!");
				}
				String key = metaData[0];
				String vaule = metaData[1];
				if (system.getMetadata() != null && system.getMetadata().containsKey(key)
						&& system.getMetadata().get(key).equals(vaule)) {
					return system.getId();
				}
			} else if (system.getSystemName().equals(systemInfo)) {
				return system.getId();
			}
		}
		throw new Exception("Could not find id for system with info: " + systemInfo);
	}

	// -------------------------------------------------------------------------------------------------
	private long interfaceNameToId(final String interfaceName) throws Exception {
		Assert.notNull(this.interfaces, "The list of possible interfaces is empty!");
		for (ServiceInterfaceResponseDTO interfaceElement : this.interfaces) {
			if (interfaceElement.getInterfaceName().equals(interfaceName)) {
				return interfaceElement.getId();
			}
		}
		throw new Exception("Could not find id for interface with name: " + interfaceName);
	}

	// -------------------------------------------------------------------------------------------------
	private long serviceDefinitionToId(final String serviceDefinition) throws Exception {
		Assert.notNull(this.services, "The ist of possible services is empty!");
		for (ServiceDefinitionResponseDTO service : this.services) {
			if (service.getServiceDefinition().equals(serviceDefinition)) {
				return service.getId();
			}
		}
		throw new Exception("Could not find id for service definition with name: " + serviceDefinition);
	}

	// -------------------------------------------------------------------------------------------------
	private List<Integer> getRuleIdsToDelete(final List<AuthorizationIntraCloudResponseDTO> rules, final List<Integer> systemIds) {
		List<Integer> result = new ArrayList<>();
		for (AuthorizationIntraCloudResponseDTO rule : rules) {
			for (int id : systemIds) {
				if (rule.getConsumerSystem().getId() == id) {
					result.add((int) rule.getId());
				}
			}
		}
		return result;
	}

	// -------------------------------------------------------------------------------------------------
	private List<SystemResponseDTO> getSystems() {
		logger.info("Get systems request started...");
		SystemListResponseDTO response = arrowheadService.consumeServiceHTTP(SystemListResponseDTO.class,
				HttpMethod.GET,
				Utilities.createURI(getScheme(),
						CommonConstants.$SERVICEREGISTRY_ADDRESS_WD.split(":")[1].replace("}", ""),
						Integer.parseInt(CommonConstants.$SERVICEREGISTRY_PORT_WD.split(":")[1].replace("}", "")),
						CommonConstants.SERVICEREGISTRY_URI + ConsumerConstants.QUERY_GET_SYSTEMS),
				null, null);
		return response.getData();
	}

	// -------------------------------------------------------------------------------------------------
	private List<ServiceDefinitionResponseDTO> getServices() {
		logger.info("Get services request started...");
		ServiceDefinitionsListResponseDTO response = arrowheadService.consumeServiceHTTP(
				ServiceDefinitionsListResponseDTO.class, HttpMethod.GET,
				Utilities.createURI(getScheme(),
						CommonConstants.$SERVICEREGISTRY_ADDRESS_WD.split(":")[1].replace("}", ""),
						Integer.parseInt(CommonConstants.$SERVICEREGISTRY_PORT_WD.split(":")[1].replace("}", "")),
						CommonConstants.SERVICEREGISTRY_URI + ConsumerConstants.QUERY_GET_SERVICES),
				null, null);
		return response.getData();
	}

	// -------------------------------------------------------------------------------------------------
	private List<ServiceInterfaceResponseDTO> getInterfaces() {
		logger.info("Get interfaces request started...");
		ServiceInterfacesListResponseDTO response = arrowheadService.consumeServiceHTTP(
				ServiceInterfacesListResponseDTO.class, HttpMethod.GET,
				Utilities.createURI(getScheme(),
						CommonConstants.$SERVICEREGISTRY_ADDRESS_WD.split(":")[1].replace("}", ""),
						Integer.parseInt(CommonConstants.$SERVICEREGISTRY_PORT_WD.split(":")[1].replace("}", "")),
						CommonConstants.SERVICEREGISTRY_URI + ConsumerConstants.QUERY_GET_INTERFACES),
				null, null);
		return response.getData();
	}

	// -------------------------------------------------------------------------------------------------
	private List<AuthorizationIntraCloudResponseDTO> getAuthorizationRules() {
		Map<String, String> authorizationUri = null;
		List<AuthorizationIntraCloudResponseDTO> rules = null;
		try {
			authorizationUri = getAuthorizationUri();
		} catch (Exception e) {
			logger.error(e.getMessage());
		}
		logger.info("Get authorization rules request started...");
		AuthorizationIntraCloudListResponseDTO response = arrowheadService.consumeServiceHTTP(
				AuthorizationIntraCloudListResponseDTO.class, HttpMethod.GET,
				Utilities.createURI(authorizationUri.get("scheme"), authorizationUri.get("host"),
						Integer.parseInt(authorizationUri.get("port")),
						authorizationUri.get("path") + ConsumerConstants.QUERY_AUTH_INTRA_CLOUD),
				null, null);

		if (response == null) {
			logger.error("Getting the authorization rules was unsuccessful!");
		} else if (response.getData().isEmpty()) {
			logger.error("No authorization rule found!");
		} else {
			rules = response.getData();
		}
		return rules;
	}

	// -------------------------------------------------------------------------------------------------
	private String getScheme() {
		if (sslProperties.isSslEnabled()) {
			return CommonConstants.HTTPS;
		}
		return CommonConstants.HTTP;
	}

	// -------------------------------------------------------------------------------------------------
	private Map<String, String> getAuthorizationUri() throws Exception {
		Map<String, String> result = new HashMap<>();
		result.put("scheme", getScheme());

		for (SystemResponseDTO system : systems) {
			if (system.getSystemName().equals(ConsumerConstants.AUTHORIZATION)) {
				result.put("host", system.getAddress());
				result.put("port", Integer.toString(system.getPort()));
				result.put("path", CommonConstants.AUTHORIZATION_URI);

				return result;
			}
		}
		throw new Exception("The authorization core system address cannot be found!");
	}
}
