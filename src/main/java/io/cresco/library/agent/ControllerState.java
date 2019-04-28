package io.cresco.library.agent;

import java.util.Map;
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


	//private Map<String, String> configParams;

	private ControllerStatePersistance controllerStatePersistance;

	public ControllerState(ControllerStatePersistance controllerStatePersistance) {
		this.controllerStatePersistance = controllerStatePersistance;
		//configParams = Collections.synchronizedMap(new HashMap<>());
		setPreInit();
	}

	/*
	public void setConfigParams(Map<String,String> configParams) {
		synchronized (lockMode) {
			this.configParams.putAll(configParams);
		}
	}

	public Map<String,String> getConfigParams() {
		synchronized (lockMode) {
			return configParams;
		}
	}
	*/

	public boolean isActive() {
		synchronized (lockMode) {
			return (currentMode == Mode.AGENT) || (currentMode == Mode.GLOBAL) || (currentMode == Mode.REGION_GLOBAL);
		}
	}

	public Mode getControllerState() {
		synchronized (lockMode) {
			return currentMode;
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

	public boolean setPreInit() {

		//pull last known agent and region name from the database
		Map<String, String> lastCState = controllerStatePersistance.getStateMap();

		synchronized (lockMode) {
			currentMode = Mode.PRE_INIT;
			currentDesc = "Initial State";
			if(lastCState != null) {
				localAgent = lastCState.get("local_agent");
				localRegion = lastCState.get("local_region");
			} else {
				localAgent = null;
				localRegion = null;
			}
			regionalRegion = null;
			regionalAgent = null;
			globalAgent = null;
			globalRegion = null;
		}
		if(controllerStatePersistance.setControllerState(currentMode, currentDesc, globalRegion, globalAgent, regionalRegion, regionalAgent, localRegion, localAgent)) {
			return true;
		} else {
			return false;
		}
	}

	public boolean setAgentShutdown(String desc) {

		if(controllerStatePersistance.setControllerState(Mode.AGENT_SHUTDOWN, desc, globalRegion, globalAgent, regionalRegion, regionalAgent, localRegion, localAgent)){

			synchronized (lockMode) {
				currentMode = Mode.AGENT_SHUTDOWN;
				currentDesc = desc;
			}

			return true;
		} else {
			return false;
		}
	}

	public boolean setAgentFailed(String regionalRegion, String regionalAgent, String desc) {
		synchronized (lockMode) {
			currentMode = Mode.AGENT_FAILED;
			currentDesc = desc;
			this.globalAgent = null;
			this.globalRegion = null;
			this.regionalRegion = regionalRegion;
			this.regionalAgent = regionalAgent;
		}
		if(controllerStatePersistance.setControllerState(currentMode, currentDesc, globalRegion, globalAgent, regionalRegion, regionalAgent, localRegion, localAgent)){
			return true;
		} else {
			return false;
		}
	}

	public boolean setAgentSuccess(String regionalRegion, String regionalAgent, String desc) {
		synchronized (lockMode) {
			currentMode = Mode.AGENT;
			currentDesc = desc;
			this.globalAgent = null;
			this.globalRegion = null;
			this.regionalRegion = regionalRegion;
			this.regionalAgent = regionalAgent;
		}
		if(controllerStatePersistance.setControllerState(currentMode, currentDesc, globalRegion, globalAgent, regionalRegion, regionalAgent, localRegion, localAgent)){
			return true;
		} else {
			return false;
		}
	}

	public boolean setAgentInit(String regionName, String agentName, String desc) {
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
		if(controllerStatePersistance.setControllerState(currentMode, currentDesc, globalRegion, globalAgent, regionalRegion, regionalAgent, localRegion, localAgent)) {
			return true;
		} else {
			return false;
		}

	}

	public boolean setRegionInit(String regionName, String agentName, String desc) {
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
		if(controllerStatePersistance.setControllerState(currentMode, currentDesc, globalRegion, globalAgent, regionalRegion, regionalAgent, localRegion, localAgent)) {
			return true;
		} else {
			return false;
		}
	}

	public boolean setRegionSuccess(String regionName, String agentName, String desc) {
		synchronized (lockMode) {
			currentMode = Mode.REGION;
			currentDesc = desc;
			localRegion = regionName;
			localAgent = agentName;
			regionalRegion = null;
			regionalAgent = null;
			globalAgent = null;
			globalRegion = null;
		}
		if(controllerStatePersistance.setControllerState(currentMode, currentDesc, globalRegion, globalAgent, regionalRegion, regionalAgent, localRegion, localAgent)) {
			return true;
		} else {
			return false;
		}
	}

	public boolean setRegionGlobalInit(String desc) {
		synchronized (lockMode) {
			currentMode = Mode.REGION_GLOBAL_INIT;
			currentDesc = desc;
			this.globalAgent = null;
			this.globalRegion = null;
			this.regionalAgent = localAgent;
			this.regionalRegion = localRegion;
		}
		if(controllerStatePersistance.setControllerState(currentMode, currentDesc, globalRegion, globalAgent, regionalRegion, regionalAgent, localRegion, localAgent)){
			return true;
		} else {
			return false;
		}
	}

	public boolean setRegionFailed(String desc) {
		synchronized (lockMode) {
			currentMode = Mode.REGION_FAILED;
			currentDesc = desc;
			this.globalAgent = null;
			this.globalRegion = null;
			this.regionalAgent = null;
			this.regionalRegion = null;
		}
		if(controllerStatePersistance.setControllerState(currentMode, currentDesc, globalRegion, globalAgent, regionalRegion, regionalAgent, localRegion, localAgent)) {
			return true;
		} else  {
			return false;
		}
	}

	public boolean setGlobalSuccess(String desc) {
		synchronized (lockMode) {
			currentMode = Mode.GLOBAL;
			currentDesc = desc;
			this.regionalAgent = localAgent;
			this.regionalRegion = localRegion;
			this.regionalAgent = localAgent;
			this.regionalRegion = localRegion;
		}
		if(controllerStatePersistance.setControllerState(currentMode, currentDesc, globalRegion, globalAgent, regionalRegion, regionalAgent, localRegion, localAgent)) {
			return true;
		} else {
			return false;
		}
	}

	public boolean setGlobalInit(String desc) {
		synchronized (lockMode) {
			currentMode = Mode.GLOBAL_INIT;
			currentDesc = desc;
			this.regionalAgent = localAgent;
			this.regionalRegion = localRegion;
			this.regionalAgent = localAgent;
			this.regionalRegion = localRegion;
		}
		if(controllerStatePersistance.setControllerState(currentMode, currentDesc, globalRegion, globalAgent, regionalRegion, regionalAgent, localRegion, localAgent)) {
			return true;
		} else {
			return false;
		}
	}

	public boolean setGlobalFailed(String desc) {
		synchronized (lockMode) {
			currentMode = Mode.GLOBAL_FAILED;
			currentDesc = desc;
			this.regionalAgent = localAgent;
			this.regionalRegion = localRegion;
			this.regionalAgent = localAgent;
			this.regionalRegion = localRegion;
		}
		if(controllerStatePersistance.setControllerState(currentMode, currentDesc, globalRegion, globalAgent, regionalRegion, regionalAgent, localRegion, localAgent)) {
			return true;
		} else {
			return false;
		}
	}

	public boolean setGlobalShutdown(String desc) {

		if(controllerStatePersistance.setControllerState(Mode.GLOBAL_SHUTDOWN, desc, globalRegion, globalAgent, regionalRegion, regionalAgent, localRegion, localAgent)) {
			synchronized (lockMode) {
				currentMode = Mode.GLOBAL_SHUTDOWN;
				currentDesc = desc;
			}
			return true;
		} else {
			return false;
		}
	}

	public boolean setRegionalGlobalSuccess(String globalRegion, String globalAgent, String desc) {
		synchronized (lockMode) {
			currentMode = Mode.REGION_GLOBAL;
			currentDesc = desc;
			this.globalRegion = globalRegion;
			this.globalAgent = globalAgent;
		}
		if(controllerStatePersistance.setControllerState(currentMode, currentDesc, globalRegion, globalAgent, regionalRegion, regionalAgent, localRegion, localAgent)) {
			return true;
		} else {
			return false;
		}
	}

	public boolean setRegionShutdown(String desc) {

		if(controllerStatePersistance.setControllerState(Mode.REGION_SHUTDOWN, desc, globalRegion, globalAgent, regionalRegion, regionalAgent, localRegion, localAgent)) {
			synchronized (lockMode) {
				currentMode = Mode.REGION_SHUTDOWN;
				currentDesc = desc;
			}
			return true;
		} else {
			return false;
		}
	}

	public boolean setRegionalGlobalFailed(String desc) {
		synchronized (lockMode) {
			currentMode = Mode.REGION_GLOBAL_FAILED;
			currentDesc = desc;
			globalAgent = null;
			globalRegion = null;
		}
		if(controllerStatePersistance.setControllerState(currentMode, currentDesc, globalRegion, globalAgent, regionalRegion, regionalAgent, localRegion, localAgent)) {
			return true;
		} else {
			return false;
		}
	}

	public boolean setStandaloneInit(String region, String agent, String desc) {
		synchronized (lockMode) {
			currentMode = Mode.STANDALONE_INIT;
			currentDesc = desc;
			localAgent = agent;
			localRegion = region;
			regionalRegion = null;
			regionalAgent = null;
			globalAgent = null;
			globalRegion = null;
		}
		if(controllerStatePersistance.setControllerState(currentMode, currentDesc, globalRegion, globalAgent, regionalRegion, regionalAgent, localRegion, localAgent)) {
			return true;
		} else {
			return false;
		}
	}

	public boolean setStandaloneSuccess(String region, String agent, String desc) {
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
		if(controllerStatePersistance.setControllerState(currentMode, currentDesc, globalRegion, globalAgent, regionalRegion, regionalAgent, localRegion, localAgent)) {
			return true;
		} else {
			return false;
		}
	}

	public boolean setStandaloneFailed(String region, String agent, String desc) {
		synchronized (lockMode) {
			currentMode = Mode.STANDALONE_FAILED;
			currentDesc = desc;
			localAgent = agent;
			localRegion = null;
			regionalRegion = null;
			regionalAgent = null;
			globalAgent = null;
			globalRegion = null;
		}
		if(controllerStatePersistance.setControllerState(currentMode, currentDesc, globalRegion, globalAgent, regionalRegion, regionalAgent, localRegion, localAgent)) {
			return true;
		} else {
			return false;
		}
	}

	public boolean setStandaloneShutdown(String desc) {
		synchronized (lockMode) {
			currentMode = Mode.STANDALONE_SHUTDOWN;
			currentDesc = desc;
		}
		if(controllerStatePersistance.setControllerState(currentMode, currentDesc, globalRegion, globalAgent, regionalRegion, regionalAgent, localRegion, localAgent)) {
			return true;
		} else {
			return false;
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