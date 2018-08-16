package library.plugin;


import io.cresco.library.messaging.MsgEvent;

public interface Executor {

	MsgEvent executeCONFIG(MsgEvent incoming);
	MsgEvent executeDISCOVER(MsgEvent incoming);
	MsgEvent executeERROR(MsgEvent incoming);
	MsgEvent executeINFO(MsgEvent incoming);
	MsgEvent executeEXEC(MsgEvent incoming);
	MsgEvent executeWATCHDOG(MsgEvent incoming);
	MsgEvent executeKPI(MsgEvent incoming);

}
