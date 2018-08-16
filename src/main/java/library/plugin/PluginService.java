package library.plugin;

import io.cresco.library.messaging.MsgEvent;

public interface PluginService {

	boolean inMsg(MsgEvent incoming);
	boolean isStarted();
	boolean isStopped();


}