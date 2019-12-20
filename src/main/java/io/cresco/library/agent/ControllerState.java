package io.cresco.library.agent;

public interface ControllerState {

	public boolean isActive();
	public ControllerMode getControllerState();
	public String getCurrentDesc();
	public boolean isRegionalController();
	public boolean isGlobalController();
	public String getRegion();
	public String getAgent();
	public String getGlobalAgent();
	public String getGlobalRegion();
	public String getRegionalAgent();
	public String getRegionalRegion();
	public String getGlobalControllerPath();
	public String getRegionalControllerPath();
	public String getAgentPath();

}