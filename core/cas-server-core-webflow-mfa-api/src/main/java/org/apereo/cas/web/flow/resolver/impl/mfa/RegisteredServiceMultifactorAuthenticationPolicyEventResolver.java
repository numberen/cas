package org.apereo.cas.web.flow.resolver.impl.mfa;

import org.apereo.cas.CentralAuthenticationService;
import org.apereo.cas.authentication.AuthenticationServiceSelectionPlan;
import org.apereo.cas.authentication.AuthenticationSystemSupport;
import org.apereo.cas.authentication.MultifactorAuthenticationProviderSelector;
import org.apereo.cas.authentication.principal.Principal;
import org.apereo.cas.services.RegisteredService;
import org.apereo.cas.services.ServicesManager;
import org.apereo.cas.ticket.registry.TicketRegistrySupport;
import org.apereo.cas.util.CollectionUtils;
import org.apereo.cas.web.flow.authentication.BaseMultifactorAuthenticationProviderEventResolver;
import org.apereo.cas.web.support.WebUtils;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.apereo.inspektr.audit.annotation.Audit;
import org.springframework.web.util.CookieGenerator;
import org.springframework.webflow.execution.Event;
import org.springframework.webflow.execution.RequestContext;

import java.util.Optional;
import java.util.Set;

/**
 * This is {@link RegisteredServiceMultifactorAuthenticationPolicyEventResolver}
 * that attempts to resolve the next event based on the authentication providers of this service.
 *
 * @author Misagh Moayyed
 * @since 5.0.0
 */
@Slf4j
public class RegisteredServiceMultifactorAuthenticationPolicyEventResolver extends BaseMultifactorAuthenticationProviderEventResolver {


    public RegisteredServiceMultifactorAuthenticationPolicyEventResolver(final AuthenticationSystemSupport authenticationSystemSupport,
                                                                         final CentralAuthenticationService centralAuthenticationService,
                                                                         final ServicesManager servicesManager,
                                                                         final TicketRegistrySupport ticketRegistrySupport,
                                                                         final CookieGenerator warnCookieGenerator,
                                                                         final AuthenticationServiceSelectionPlan authSelectionStrategies,
                                                                         final MultifactorAuthenticationProviderSelector selector) {
        super(authenticationSystemSupport, centralAuthenticationService, servicesManager,
            ticketRegistrySupport, warnCookieGenerator, authSelectionStrategies, selector);
    }

    @Override
    public Set<Event> resolveInternal(final RequestContext context) {
        val service = resolveRegisteredServiceInRequestContext(context);
        val authentication = WebUtils.getAuthentication(context);

        if (service == null || authentication == null) {
            LOGGER.debug("No service or authentication is available to determine event for principal");
            return null;
        }

        val policy = service.getMultifactorPolicy();
        if (policy == null || policy.getMultifactorAuthenticationProviders().isEmpty()) {
            LOGGER.debug("Authentication policy does not contain any multifactor authentication providers");
            return null;
        }

        if (StringUtils.isNotBlank(policy.getPrincipalAttributeNameTrigger()) || StringUtils.isNotBlank(policy.getPrincipalAttributeValueToMatch())) {
            LOGGER.debug("Authentication policy for [{}] has defined principal attribute triggers. Skipping...", service.getServiceId());
            return null;
        }

        return resolveEventPerAuthenticationProvider(authentication.getPrincipal(), context, service);
    }


    /**
     * Resolve event per authentication provider event.
     *
     * @param principal the principal
     * @param context   the context
     * @param service   the service
     * @return the event
     */
    @SneakyThrows
    protected Set<Event> resolveEventPerAuthenticationProvider(final Principal principal,
                                                               final RequestContext context,
                                                               final RegisteredService service) {
        val providers = getAuthenticationProviderForService(service);
        if (providers != null && !providers.isEmpty()) {
            val provider = this.multifactorAuthenticationProviderSelector.resolve(providers, service, principal);
            LOGGER.debug("Selected multifactor authentication provider for this transaction is [{}]", provider);

            val identifier = provider.getId();
            LOGGER.debug("Attempting to build an event based on the authentication provider [{}] and service [{}]", provider, service.getName());

            val event = validateEventIdForMatchingTransitionInContext(identifier, context, buildEventAttributeMap(principal, Optional.of(service), provider));
            return CollectionUtils.wrapSet(event);
        }

        LOGGER.debug("No multifactor authentication providers could be located for [{}]", service);
        return null;
    }

    @Audit(action = "AUTHENTICATION_EVENT",
        actionResolverName = "AUTHENTICATION_EVENT_ACTION_RESOLVER",
        resourceResolverName = "AUTHENTICATION_EVENT_RESOURCE_RESOLVER")
    @Override
    public Event resolveSingle(final RequestContext context) {
        return super.resolveSingle(context);
    }
}
