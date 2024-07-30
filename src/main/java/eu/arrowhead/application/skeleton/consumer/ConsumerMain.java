package eu.arrowhead.application.skeleton.consumer;

import java.io.File;
import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.springframework.http.HttpStatus;
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
import eu.arrowhead.common.exception.ArrowheadException;

@SpringBootApplication
@ComponentScan(basePackages = { CommonConstants.BASE_PACKAGE, "ai.aitia" })
public class ConsumerMain implements ApplicationRunner {

	// =================================================================================================
	// members

	@Autowired
	private ArrowheadService arrowheadService;

	@Autowired
	private SSLProperties sslProperties;

	private final Logger logger = LogManager.getLogger(ConsumerMain.class);

	private List<SystemResponseDTO> systems;

	private List<ServiceDefinitionResponseDTO> services;

	private List<ServiceInterfaceResponseDTO> interfaces;

	private Map<String, String> authorizationUri;

	@Value(CommonConstants.$SERVICEREGISTRY_ADDRESS_WD)
	private String serviceRegistryAddress;

	@Value(CommonConstants.$SERVICEREGISTRY_PORT_WD)
	private int serviceRegistryPort;

	// =================================================================================================
	// methods

	// ------------------------------------------------------------------------------------------------
	public static void main(final String[] args) {
		SpringApplication.run(ConsumerMain.class, args);
	}

	// -------------------------------------------------------------------------------------------------
	@Override
	public void run(final ApplicationArguments args) throws Exception {

		if (args.getSourceArgs().length != 1) {
			logger.error("You must specify exactly one path! (E.g.: ../example.json)");
			return;
		}

		final List<AuthRule> newRules = getRules(args.getSourceArgs()[0]);

		if (newRules == null) {
			logger.error("Reading the file was unsuccessful!");
			return;
		}

		try {
			systems = getSystems();
			services = getServices();
			interfaces = getInterfaces();
			authorizationUri = getAuthorizationUri();
		} catch (final Exception e) {
			logger.error("Updating the authorization rules was unsuccessful, reason: " + e.getMessage());
			return;
		}

		updateAuthRules(newRules);
	}

	// =================================================================================================
	// assistant methods

	// -------------------------------------------------------------------------------------------------
	private List<AuthRule> getRules(final String filename) {

		JsonNode newRulesJson;
		try {
			newRulesJson = new ObjectMapper().readTree(new File(filename));
		} catch (final IOException e) {
			logger.error(e.getMessage());
			return null;
		}
		final List<AuthRule> rules = new ArrayList<>();

		for (final JsonNode rule : newRulesJson) {
			try {
				final AuthRule newRule = Utilities.fromJson(rule.toString(), AuthRule.class);
				// Setting the defaults if no interfaces are given
				if (newRule.getInterfaces().size() == 0) {
					newRule.setInterfaces(new ArrayList<String>(ConsumerConstants.DEFAULT_INTERFACE_NAMES));
				}
				rules.add(newRule);
			} catch (final Exception e) {
				logger.error("Could not create authorization rule (invalid json format): " + rule + ", reason: " + e.getMessage());
				return null;
			}
		}
		return rules;
	}

	// -------------------------------------------------------------------------------------------------
	private void updateAuthRules(final List<AuthRule> newRules) {
		if (deleteRules(newRules)) {
			try {
				addRules(newRules);
			} catch (final Exception e) {
				return;
			}
		}
	}

	// -------------------------------------------------------------------------------------------------
	//returns true if the rules were successfully deleted, else returns false
	private boolean deleteRules(final List<AuthRule> rules) {
		List<Long> ruleIdsToDelete = new ArrayList<Long>();
		try {
			ruleIdsToDelete = getRuleIdsToDelete(getAuthorizationRules(), getSystemIdsToDelete(rules));
		} catch (final Exception e) {
			logger.error("Finding the authorization rules to delete was unsuccessful. Reason: " + e.getMessage());
			return false;
		}

		for (final Long id : ruleIdsToDelete) {
			logger.debug("Removing authorization rule with id: " + id);
			final String response = arrowheadService.consumeServiceHTTP(String.class, HttpMethod.DELETE,
					Utilities.createURI(authorizationUri.get(ConsumerConstants.SCHEME), authorizationUri.get(ConsumerConstants.HOST),
							Integer.parseInt(authorizationUri.get(ConsumerConstants.PORT)), authorizationUri.get(ConsumerConstants.PATH)
									+ ConsumerConstants.OP_AUTH_INTRA_CLOUD + "/" + Long.toString(id)),
					null, null);
			logger.debug("Http DELETE response: " + response);
		}
		return true;
	}

	// -------------------------------------------------------------------------------------------------
	private void addRules(final List<AuthRule> rules) {

		final List<AuthorizationIntraCloudRequestDTO> rulesToAdd = createDTOListFromAuthRules(rules);
		for (final AuthorizationIntraCloudRequestDTO ruleToAdd : rulesToAdd) {
			addSingleRule(ruleToAdd);
		}
	}

	// -------------------------------------------------------------------------------------------------
	private List<AuthorizationIntraCloudRequestDTO> createDTOListFromAuthRules(final List<AuthRule> rules) {

		final List<AuthorizationIntraCloudRequestDTO> dtoList = new ArrayList<>();

		List<Long> consumerIdList; //multiple consumerid can match one auth rule's systeminfo
		List<Long> prodiverIdList; //multiple providerid can match one auth rule's systeminfo

		List<Long> serviceDefinitionId; //contains one element, but must be a list because of the DTO
		List<Long> interfaceIds;

		for (int i = 0; i < rules.size(); i++) {

			consumerIdList = new ArrayList<>();
			prodiverIdList = new ArrayList<>();
			serviceDefinitionId = new ArrayList<>(1);
			interfaceIds = new ArrayList<>();

			try {
				consumerIdList = getSystemIdsByInfo(rules.get(i).getConsumer());
				prodiverIdList = getSystemIdsByInfo(rules.get(i).getProvider());

				serviceDefinitionId.add(serviceDefinitionToId(rules.get(i).getService()));

				for (final String interfaceName : rules.get(i).getInterfaces()) {
					interfaceIds.add(interfaceNameToId(interfaceName));

				}

				for (final Long consumerId : consumerIdList) {
					for (final Long providerId : prodiverIdList) {
						dtoList.add(new AuthorizationIntraCloudRequestDTO(consumerId, Arrays.asList(providerId), serviceDefinitionId,
								interfaceIds));
					}
				}
			} catch (final Exception e) {
				logger.error("Could not create authorization rule: " + rules.get(i).toString() + ", reason: "
						+ e.getMessage());
			}
		}
		return dtoList;

	}

	// -------------------------------------------------------------------------------------------------
	private void addSingleRule(final AuthorizationIntraCloudRequestDTO ruleToAdd) {

		logger.debug("Sending the POST request for the following authorization rule: " + ruleToAdd.toString());
		AuthorizationIntraCloudListResponseDTO response = null;
		try {
			response = arrowheadService.consumeServiceHTTP(
					AuthorizationIntraCloudListResponseDTO.class, HttpMethod.POST,
					Utilities.createURI(authorizationUri.get(ConsumerConstants.SCHEME), authorizationUri.get(ConsumerConstants.HOST),
							Integer.parseInt(authorizationUri.get(ConsumerConstants.PORT)),
							authorizationUri.get(ConsumerConstants.PATH) + ConsumerConstants.OP_AUTH_INTRA_CLOUD),
					null, ruleToAdd);
		} catch (final ArrowheadException ae) {
			if (ae.getErrorCode() == HttpStatus.BAD_REQUEST.value()) {
				logger.error("Error 400 occured while applying authorization rule: " + ruleToAdd.toString() + ", reason: " + ae.getMessage());
				return;
			}
			if (ae.getErrorCode() == HttpStatus.INTERNAL_SERVER_ERROR.value() || ae.getErrorCode() == HttpStatus.UNAUTHORIZED.value()) {
				logger.error("Error " + ae.getErrorCode() + " occured while applying authorization rule: " + ruleToAdd.toString() + ", reason: " + ae.getMessage());
				throw ae;
			}
		}
		if (response == null) {
			logger.error("Could not apply the following authorization rule: " + ruleToAdd.toString());
		} else {
			logger.debug("Successfully applied rule with id: " + response.getData().getFirst().getId());
		}
	}

	// -------------------------------------------------------------------------------------------------
	private List<Long> getSystemIdsToDelete(final List<AuthRule> rules) {

		final List<Long> result = new ArrayList<>();

		for (final AuthRule rule : rules) {
			try {
				final List<Long> ids = getSystemIdsByInfo(rule.getConsumer());
				for (final Long id : ids) {
					if (!result.contains(id)) {
						result.add(id);
					}
				}

			} catch (final Exception e) {
				logger.error(e);
			}

		}

		return result;
	}

	// -------------------------------------------------------------------------------------------------
	// the system string can be systemname or metadata
	// (in case it is metadata, it must contain '=')
	private List<Long> getSystemIdsByInfo(final String systemInfo) throws Exception {

		Assert.notNull(this.systems, "The list of possible systems is empty!");

		final String systemInfoFormatted = systemInfo.trim();

		final List<Long> result = new ArrayList<>();

		//case: systeminfo is metadata
		if (systemInfoFormatted.contains("=")) {
			final String[] metadata = systemInfoFormatted.split(ConsumerConstants.METADATA_SCHEME_STRING);

			if (metadata.length != 2) {
				throw new IllegalArgumentException("System metadata is in invalid format!");
			}
			final String key = metadata[0].trim();
			final String value = metadata[1].trim();
			for (final SystemResponseDTO system : systems) {
				if (system.getMetadata() != null && system.getMetadata().containsKey(key)
						&& system.getMetadata().get(key).equals(value)) {
					result.add(system.getId());
				}
			}
			//case: systeminfo is systemname
		} else {
			for (final SystemResponseDTO system : systems) {
				if (system.getSystemName().equals(systemInfoFormatted)) {
					result.add(system.getId());
				}
			}
		}

		if (result.size() == 0) {
			throw new Exception("Could not find id for system with info: " + systemInfoFormatted);
		}
		return result;
	}

	// -------------------------------------------------------------------------------------------------
	private long interfaceNameToId(final String interfaceName) throws Exception {

		Assert.notNull(this.interfaces, "The list of possible interfaces is empty!");

		final String interfaceNameFormatted = interfaceName.trim();

		for (final ServiceInterfaceResponseDTO interfaceElement : this.interfaces) {
			if (interfaceElement.getInterfaceName().equals(interfaceNameFormatted)) {
				return interfaceElement.getId();
			}
		}
		throw new Exception("Could not find id for interface with name: " + interfaceNameFormatted);
	}

	// -------------------------------------------------------------------------------------------------
	private long serviceDefinitionToId(final String serviceDefinition) throws Exception {

		Assert.notNull(this.services, "The list of possible services is empty!");

		final String serviceDefinitionFormatted = serviceDefinition.trim();

		for (final ServiceDefinitionResponseDTO service : this.services) {
			if (service.getServiceDefinition().equals(serviceDefinitionFormatted)) {
				return service.getId();
			}
		}
		throw new Exception("Could not find id for service definition with name: " + serviceDefinitionFormatted);
	}

	// -------------------------------------------------------------------------------------------------
	private List<Long> getRuleIdsToDelete(final List<AuthorizationIntraCloudResponseDTO> rules, final List<Long> systemIds) {

		final List<Long> result = new ArrayList<>();

		for (final AuthorizationIntraCloudResponseDTO rule : rules) {
			for (final Long id : systemIds) {
				if (rule.getConsumerSystem().getId() == id) {
					result.add((rule.getId()));
				}
			}
		}
		return result;
	}

	// -------------------------------------------------------------------------------------------------
	private List<SystemResponseDTO> getSystems() {
		logger.debug("Get systems request started...");
		final SystemListResponseDTO response = arrowheadService.consumeServiceHTTP(SystemListResponseDTO.class,
				HttpMethod.GET,
				Utilities.createURI(getScheme(),
						serviceRegistryAddress,
						serviceRegistryPort,
						CommonConstants.SERVICEREGISTRY_URI + ConsumerConstants.QUERY_GET_SYSTEMS),
				null, null);
		return response.getData();
	}

	// -------------------------------------------------------------------------------------------------
	private List<ServiceDefinitionResponseDTO> getServices() {
		logger.debug("Get services request started...");
		final ServiceDefinitionsListResponseDTO response = arrowheadService.consumeServiceHTTP(
				ServiceDefinitionsListResponseDTO.class, HttpMethod.GET,
				Utilities.createURI(getScheme(),
						serviceRegistryAddress,
						serviceRegistryPort,
						CommonConstants.SERVICEREGISTRY_URI + ConsumerConstants.QUERY_GET_SERVICES),
				null, null);
		return response.getData();
	}

	// -------------------------------------------------------------------------------------------------
	private List<ServiceInterfaceResponseDTO> getInterfaces() {
		logger.debug("Get interfaces request started...");
		final ServiceInterfacesListResponseDTO response = arrowheadService.consumeServiceHTTP(
				ServiceInterfacesListResponseDTO.class, HttpMethod.GET,
				Utilities.createURI(getScheme(),
						serviceRegistryAddress,
						serviceRegistryPort,
						CommonConstants.SERVICEREGISTRY_URI + ConsumerConstants.QUERY_GET_INTERFACES),
				null, null);
		return response.getData();
	}

	// -------------------------------------------------------------------------------------------------
	private List<AuthorizationIntraCloudResponseDTO> getAuthorizationRules() throws Exception {

		List<AuthorizationIntraCloudResponseDTO> rules = new ArrayList<>();

		logger.debug("Get authorization rules request started...");
		final AuthorizationIntraCloudListResponseDTO response = arrowheadService.consumeServiceHTTP(
				AuthorizationIntraCloudListResponseDTO.class, HttpMethod.GET,
				Utilities.createURI(authorizationUri.get(ConsumerConstants.SCHEME), authorizationUri.get(ConsumerConstants.HOST),
						Integer.parseInt(authorizationUri.get(ConsumerConstants.PORT)),
						authorizationUri.get(ConsumerConstants.PATH) + ConsumerConstants.OP_AUTH_INTRA_CLOUD),
				null,
				null);

		if (response == null) {
			logger.error("Getting the authorization rules was unsuccessful!");
			throw new Exception("Existing authorization rules cannot be fetched.");
		} else if (response.getData().isEmpty()) {
			logger.debug("No current authorization were found.");
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
		final Map<String, String> result = new HashMap<>();
		result.put(ConsumerConstants.SCHEME, getScheme());

		for (final SystemResponseDTO system : systems) {
			if (system.getSystemName().equals(ConsumerConstants.AUTHORIZATION)) {
				result.put(ConsumerConstants.HOST, system.getAddress());
				result.put(ConsumerConstants.PORT, Integer.toString(system.getPort()));
				result.put(ConsumerConstants.PATH, CommonConstants.AUTHORIZATION_URI);

				return result;
			}
		}
		throw new Exception("The authorization core system address cannot be found!");
	}
}
