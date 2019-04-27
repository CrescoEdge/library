package io.cresco.library.agent;

import java.util.Map;

public interface ControllerStatePersistance {

	Map<String,String> getStateMap();
	boolean setControllerState(ControllerState.Mode currentMode, String currentDesc, String globalRegion, String globalAgent, String regionalRegion, String regionalAgent, String localRegion, String localAgent);
}