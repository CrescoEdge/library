package io.cresco.library.agent;

import io.cresco.library.data.DataPlaneService;
import io.cresco.library.messaging.MsgEvent;
import io.cresco.library.plugin.PluginBuilder;
import io.cresco.library.utilities.CLogger;

public interface AgentService {
	AgentState getAgentState();
	DataPlaneService getDataPlaneService();
	CLogger getCLogger(PluginBuilder pluginBuilder, String baseClassName, String issuingClassName, CLogger.Level level);
	CLogger getCLogger(PluginBuilder pluginBuilder, String baseClassName, String issuingClassName);
	void msgOut(String id, MsgEvent msg);
	void setLogLevel(String logId, CLogger.Level level);
}