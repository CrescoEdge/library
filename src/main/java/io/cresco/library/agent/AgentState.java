package io.cresco.library.agent;

import io.cresco.library.messaging.MsgEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;

public class AgentState implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(AgentState.class);

    /**
     * Only needed for ECF generic transport
     */
    private static final long serialVersionUID = 5117254163782139591L;

    public String getRegion() { return controllerState.getRegion();}
    public String getAgent() { return controllerState.getAgent();}

    public String getRegionalRegion() { return controllerState.getRegionalRegion();}
    public String getRegionalAgent() { return controllerState.getRegionalAgent();}

    public String getGlobalRegion() { return controllerState.getGlobalRegion();}
    public String getGlobalAgent() { return controllerState.getGlobalAgent();}

    public boolean isActive() { return controllerState.isActive(); }

    public ControllerMode getControllerState() {return controllerState.getControllerState(); }

    private ControllerState controllerState;

    public AgentState(ControllerState controllerState) {
        this.controllerState = controllerState;
    }

    public void sendMessage(String message) {
        log.debug("Message From Agent: remote_id:{}", message);
    }

    public void msgIn(String msg) {

        //    logger.trace("msgIn : " + msg.getParams().toString());
        //    msgInProcessQueue.submit(new MsgRoute(this, msg));
    }
    public void msgIn(MsgEvent msg) {
        //    logger.trace("msgIn : " + msg.getParams().toString());
        //    msgInProcessQueue.submit(new MsgRoute(this, msg));
        log.debug("MESSAGE IN AGENT!!! {}", msg.getParams().toString());

    }

}