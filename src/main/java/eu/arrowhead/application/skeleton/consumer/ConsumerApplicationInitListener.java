package eu.arrowhead.application.skeleton.consumer;

import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import ai.aitia.arrowhead.application.library.config.ApplicationInitListener;
import eu.arrowhead.common.core.CoreSystem;

@Component
public class ConsumerApplicationInitListener extends ApplicationInitListener {

	//=================================================================================================
	// methods

	//-------------------------------------------------------------------------------------------------
	@Override
	protected void customInit(final ContextRefreshedEvent event) {
		//Checking the availability of necessary core systems
		checkCoreSystemReachability(CoreSystem.SERVICEREGISTRY);
		checkCoreSystemReachability(CoreSystem.AUTHORIZATION);
	}
}
