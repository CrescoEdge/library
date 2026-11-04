package io.cresco.library.agent;

import io.cresco.library.data.DataPlaneService;
import io.cresco.library.messaging.MsgEvent;
import io.cresco.library.plugin.PluginBuilder;
import io.cresco.library.security.Caller;
import io.cresco.library.utilities.CLogger;

import java.util.Collections;
import java.util.Set;

public interface AgentService {
	AgentState getAgentState();
	DataPlaneService getDataPlaneService();
	CLogger getCLogger(PluginBuilder pluginBuilder, String baseClassName, String issuingClassName, CLogger.Level level);
	CLogger getCLogger(PluginBuilder pluginBuilder, String baseClassName, String issuingClassName);
	void msgOut(String id, MsgEvent msg);
	void setLogLevel(String logId, CLogger.Level level);
	String getAgentDataDirectory();

	/**
	 * Send {@code msg} on behalf of an API user the calling bundle has authenticated. The controller honours it only
	 * from a bundle listed in {@code on_behalf_bundles}; from any other bundle it is refused and nothing is sent.
	 *
	 * @throws SecurityException the calling bundle may not act on behalf of a user, or {@code caller} is not an API caller
	 */
	default void msgOutOnBehalfOf(String id, MsgEvent msg, Caller caller) {
		throw new SecurityException("this controller does not accept messages on behalf of a user");
	}

	/** This node's tenant, or null if it is not known yet (callers then fail closed). */
	default String getTenant() { return null; }

	/** The superuser tenants ({@code broker_superuser_tenants}). Never null. */
	default Set<String> getSuperuserTenants() { return Collections.emptySet(); }

	/**
	 * The trust store that plugin and repository jars arriving over the network must chain to
	 * ({@code plugin_signing_truststore}), or null if none is configured (signed jars are then refused).
	 */
	default java.security.KeyStore getPluginSigningTrust() { return null; }

	/**
	 * False only when {@code plugin_signing_required=false} is set explicitly (development only, logged as a WARNING on
	 * every boot).
	 */
	default boolean isPluginSigningRequired() { return true; }
}