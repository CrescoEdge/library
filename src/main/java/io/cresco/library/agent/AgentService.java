package io.cresco.library.agent;


import io.cresco.library.messaging.MsgEvent;
import io.cresco.library.utilities.CLogger;

public interface AgentService {
	AgentState getAgentState();
	void msgOut(String id, MsgEvent msg);
	void setLogLevel(String logId, CLogger.Level level);
}