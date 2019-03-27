package io.cresco.library.agent;


import java.util.concurrent.atomic.AtomicBoolean;

public class ControllerState {

	private Mode currentMode  = Mode.PRE_INIT;
	private String localRegion;
	private String localAgent;
	private String currentDesc;
	private String globalAgent;
	private String globalRegion;
	private String regionalAgent;
	private String regionalRegion;
	private AtomicBoolean lockMode = new AtomicBoolean();


	public ControllerState() {
		setPreInit();
	}

	public boolean isActive() {
		synchronized (lockMode) {
			return (currentMode == Mode.AGENT) || (currentMode == Mode.GLOBAL) || (currentMode == Mode.REGION_GLOBAL);
		}
	}

	public String getControllerState() {
		synchronized (lockMode) {
			return currentMode.toString();
		}
	}

	public String getCurrentDesc() {
		return  currentDesc;
	}

	public boolean isRegionalController() {
		boolean isRC = false;

		synchronized (lockMode) {
			if ((currentMode.toString().startsWith("REGION")) || isGlobalController()) {
				isRC = true;
			}
		}
		return isRC;
	}

	public boolean isGlobalController() {
		boolean isGC = false;

		synchronized (lockMode) {
			if (currentMode.toString().startsWith("GLOBAL")) {
				isGC = true;
			}
		}
		return isGC;
	}

	public String getRegion() { return localRegion; }

	public String getAgent() { return localAgent; }

	public String getGlobalAgent() {
		return globalAgent;
	}

	public String getGlobalRegion() {
		return globalRegion;
	}

	public String getRegionalAgent() {
		return regionalAgent;
	}

	public String getRegionalRegion() {
		return regionalRegion;
	}

	public String getGlobalControllerPath() {
		if(isRegionalController()) {
			return globalRegion + "_" + globalAgent;
		} else {
			return null;
		}
	}

	public String getRegionalControllerPath() {
		if(isRegionalController()) {
			return regionalRegion + "_" + regionalAgent;
		} else {
			return null;
		}
	}

	public String getAgentPath() {
		return localRegion + "_" + localAgent;
	}

	public void setPreInit() {
		synchronized (lockMode) {
			currentMode = Mode.PRE_INIT;
			currentDesc = null;
			localAgent = null;
			localRegion = null;
			regionalRegion = null;
			regionalAgent = null;
			globalAgent = null;
			globalRegion = null;
		}
	}

	public void setAgentSuccess(String regionalRegion, String regionalAgent, String desc) {
		synchronized (lockMode) {
			currentMode = Mode.AGENT;
			currentDesc = desc;
			this.globalAgent = null;
			this.globalRegion = null;
			this.regionalRegion = regionalRegion;
			this.regionalAgent = regionalAgent;
		}
	}

	public void setAgentInit(String regionName, String agentName, String desc) {
		synchronized (lockMode) {
			currentMode = Mode.AGENT_INIT;
			currentDesc = desc;
			this.localAgent = agentName;
			this.localRegion = regionName;
			regionalRegion = null;
			regionalAgent = null;
			globalAgent = null;
			globalRegion = null;
		}

	}

	public void setRegionInit(String regionName, String agentName, String desc) {
		synchronized (lockMode) {
			currentMode = Mode.REGION_INIT;
			currentDesc = desc;
			localRegion = regionName;
			localAgent = agentName;
			regionalRegion = null;
			regionalAgent = null;
			globalAgent = null;
			globalRegion = null;
		}
	}

	public void setRegionGlobalInit(String desc) {
		synchronized (lockMode) {
			currentMode = Mode.REGION_GLOBAL_INIT;
			currentDesc = desc;
			this.globalAgent = null;
			this.globalRegion = null;
			this.regionalAgent = localAgent;
			this.regionalRegion = localRegion;
		}
	}

	public void setRegionFailed(String desc) {
		synchronized (lockMode) {
			currentMode = Mode.REGION_FAILED;
			currentDesc = desc;
			this.globalAgent = null;
			this.globalRegion = null;
			this.regionalAgent = null;
			this.regionalRegion = null;
		}
	}

	public void setGlobalSuccess(String desc) {
		synchronized (lockMode) {
			currentMode = Mode.GLOBAL;
			currentDesc = desc;
			this.regionalAgent = localAgent;
			this.regionalRegion = localRegion;
			this.regionalAgent = localAgent;
			this.regionalRegion = localRegion;
		}
	}

	public void setRegionalGlobalSuccess(String globalRegion, String globalAgent, String desc) {
		synchronized (lockMode) {
			currentMode = Mode.REGION_GLOBAL;
			currentDesc = desc;
			this.globalRegion = globalRegion;
			this.globalAgent = globalAgent;
		}
	}

	public void setRegionalGlobalFailed(String desc) {
		synchronized (lockMode) {
			currentMode = Mode.REGION_GLOBAL_FAILED;
			currentDesc = desc;
			globalAgent = null;
			globalRegion = null;
		}
	}

	public void setStandaloneInit(String agent, String desc) {
		synchronized (lockMode) {
			currentMode = Mode.STANDALONE_INIT;
			currentDesc = desc;
			localAgent = agent;
			localRegion = null;
			regionalRegion = null;
			regionalAgent = null;
			globalAgent = null;
			globalRegion = null;
		}
	}

	public void setStandalone(String agent, String desc) {
		synchronized (lockMode) {
			currentMode = Mode.STANDALONE;
			currentDesc = desc;
			localAgent = agent;
			localRegion = null;
			regionalRegion = null;
			regionalAgent = null;
			globalAgent = null;
			globalRegion = null;
		}
	}

	public enum Mode {
		PRE_INIT,

		STANDALONE_INIT,
		STANDALONE,
		STANDALONE_FAILED,
		STANDALONE_SHUTDOWN,

		AGENT_INIT,
		AGENT,
		AGENT_FAILED,
		AGENT_SHUTDOWN,

		REGION_INIT,
		REGION_FAILED,
		REGION,
		REGION_GLOBAL_INIT,
		REGION_GLOBAL_FAILED,
		REGION_GLOBAL,
		REGION_SHUTDOWN,

		GLOBAL_INIT,
		GLOBAL,
		GLOBAL_FAILED,
		GLOBAL_SHUTDOWN;

		Mode() {

		}
	}

}
