package io.cresco.library.agent;


import io.cresco.library.data.TopicType;
import io.cresco.library.messaging.MsgEvent;
import io.cresco.library.utilities.CLogger;

import javax.jms.MessageListener;
import javax.jms.TextMessage;

public interface AgentService {
	AgentState getAgentState();
	void msgOut(String id, MsgEvent msg);
	void setLogLevel(String logId, CLogger.Level level);
	String addMessageListener(TopicType topicType, MessageListener messageListener, String selectorString);
	boolean sendMessage(TopicType topicType, TextMessage textMessage);

}