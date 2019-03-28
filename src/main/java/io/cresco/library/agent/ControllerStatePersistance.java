package io.cresco.library.agent;

public interface ControllerStatePersistance {

	public String getAgentId();
	void setControllerState(ControllerState.Mode currentMode, String currentDesc, String globalRegion, String globalAgent, String regionalRegion, String regionalAgent, String localRegion, String localAgent);
}