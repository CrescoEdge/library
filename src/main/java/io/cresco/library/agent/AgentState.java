package io.cresco.library.agent;

import io.cresco.library.messaging.MsgEvent;

import java.io.Serializable;

public class AgentState implements Serializable {
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
        System.out.println("Message From Agent: remote_id:" + message);
    }

    public void msgIn(String msg) {

        //    logger.trace("msgIn : " + msg.getParams().toString());
        //    msgInProcessQueue.submit(new MsgRoute(this, msg));
    }
    public void msgIn(MsgEvent msg) {
        //    logger.trace("msgIn : " + msg.getParams().toString());
        //    msgInProcessQueue.submit(new MsgRoute(this, msg));
        System.out.println("MESSAGE IN AGENT!!! " + msg.getParams().toString());

    }

}