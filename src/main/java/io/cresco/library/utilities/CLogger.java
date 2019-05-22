package io.cresco.library.utilities;

import io.cresco.library.agent.AgentState;
import io.cresco.library.data.DataPlaneService;
import io.cresco.library.messaging.MsgEvent;

public interface CLogger {

	public enum Level {

		None(-1), Error(0), Warn(1), Info(2), Debug(4), Trace(8);

		private final int level;
		Level(int level) { this.level = level; }
		public int getValue() { return level; }
	}

	public void error(String logMessage);
	public void error(String logMessage, Object ... params);
	public void warn(String logMessage);
	public void warn(String logMessage, Object ... params);
	public void info(String logMessage);
	public void info(String logMessage, Object ... params);
	public void debug(String logMessage);
	public void debug(String logMessage, Object ... params);
	public void trace(String logMessage);
	public void trace(String logMessage, Object ... params);

}