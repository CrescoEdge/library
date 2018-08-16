package library.plugin;

import io.cresco.library.agent.AgentService;
import io.cresco.library.messaging.MsgEvent;
import io.cresco.library.messaging.RPC;
import io.cresco.library.metrics.CrescoMeterRegistry;
import io.cresco.library.utilities.CLogger;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

import java.io.File;
import java.io.FileInputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

public class PluginBuilder {

    private AgentService agentService;
    //private LogService logService;
    private Config config;
    private CrescoMeterRegistry crescoMeterRegistry;
    private String baseClassName;
    private Executor executor;
    private boolean isActive;
    private RPC rpc;
    private ExecutorService msgInProcessQueue;

    public PluginBuilder(String className, BundleContext context, Map<String,Object> configMap) {
        this(null,className,context,configMap);
    }

    public PluginBuilder(AgentService agentService,String className, BundleContext context, Map<String,Object> configMap) {

        this.msgInProcessQueue = Executors.newCachedThreadPool();
        //this.msgInProcessQueue = Executors.newFixedThreadPool(100);


        String identString = null;

        if(agentService == null) {
            //init agent services
            ServiceReference sr = context.getServiceReference(AgentService.class.getName());
            if (sr != null) {
                boolean assign = sr.isAssignableTo(context.getBundle(), AgentService.class.getName());

                if (assign) {
                    boolean isAssigned = false;
                    while(!isAssigned) {
                        try {
                            this.agentService = (AgentService) context.getService(sr);
                            isAssigned = true;
                        } catch (Exception ex) {
                            System.out.println("Failed AgentService Assignment");
                        }
                    }
                } else {
                    System.out.println("Could not assign AgentService!");
                }
            } else {
                System.out.println("Can't Find :" + AgentService.class.getName());
            }
        } else {
            identString = "agent";
            this.agentService = agentService;
        }

        this.baseClassName = className.substring(0,className.lastIndexOf("."));

        //create config
        config = new Config(configMap);

        //metric registery
        if(identString == null) {
            identString = getPluginID();
        }

        this.crescoMeterRegistry = new CrescoMeterRegistry(this,identString);

        this.rpc = new RPC(this);

    }

    public void setLogLevel(String logId, CLogger.Level level) {
        if(agentService != null) {
            agentService.setLogLevel(logId,level);
        }
    }

    public AgentService getAgentService() {
        return agentService;
    }

    public Config getConfig() { return config;}
    public void setConfig(Map<String,Object> configMap) {
        this.config = new Config(configMap);
    }
    public String getAgent() { return agentService.getAgentState().getAgent(); }
    public String getRegion() { return agentService.getAgentState().getRegion(); }
    public String getPluginID() { return config.getStringParam("pluginID"); }

    public void msgIn(MsgEvent message) {
        if (message != null) {

            String callId = message.getParam(("callId-" + this.getRegion() + "-" +
                    this.getAgent() + "-" + this.getPluginID()));
            if (callId != null) {
                //don't return rpc directly back to caller if self-called
                int ttl = Integer.parseInt(message.getParam("ttl"));
                if(ttl > 0) {
                    this.receiveRPC(callId, message);
                    return;
                }
            }

            if(executor != null) {
                    //new Thread(new MessageProcessor(message)).start();
                    msgInProcessQueue.submit(new MessageProcessor(message));
            }

        }
    }

    public void receiveRPC(String callId, MsgEvent msg) {
        rpc.putReturnMessage(callId, msg);
    }

    public void msgOut(MsgEvent msg) {

        agentService.msgOut(getPluginID(), msg);

    }
    public CrescoMeterRegistry getCrescoMeterRegistry() { return crescoMeterRegistry; }

    public CLogger getLogger(String issuingClassName, CLogger.Level level) {
        return new CLogger(this,baseClassName,issuingClassName,level);
    }
    public boolean isIPv6() { return false; }

    public MsgEvent sendRPC(MsgEvent msg) {
        msg.setParam("is_rpc",Boolean.TRUE.toString());
        return this.rpc.call(msg);
    }

    public MsgEvent getGlobalControllerMsgEvent(MsgEvent.Type type) {
        return getMsgEvent(type, getRegion(),getAgent(), null,true,true);
    }

    public MsgEvent getGlobalAgentMsgEvent(MsgEvent.Type type, String dstRegion, String dstAgent) {
        return getMsgEvent(type, dstRegion,dstAgent, null,false,false);
    }

    public MsgEvent getGlobalPluginMsgEvent(MsgEvent.Type type, String dstRegion, String dstAgent, String dstPlugin) {
        return getMsgEvent(type, dstRegion,dstAgent, dstPlugin,false,false);
    }

    public MsgEvent getKPIMsgEvent() {
        return getMsgEvent(MsgEvent.Type.KPI,getRegion(), getAgent(), null,true,false);
    }

    public MsgEvent getRegionalControllerMsgEvent(MsgEvent.Type type) {
        return getMsgEvent(type,getRegion(), getAgent(), null,true,false);
    }

    public MsgEvent getRegionalAgentMsgEvent(MsgEvent.Type type, String dstAgent) {
        return getMsgEvent(type,getRegion(), dstAgent, null,false,false);
    }

    public MsgEvent getRegionalPluginMsgEvent(MsgEvent.Type type, String dstAgent, String dstPlugin) {
        return getMsgEvent(type,getRegion(), dstAgent, dstPlugin,false,false);
    }

    public MsgEvent getAgentMsgEvent(MsgEvent.Type type) {
        return getMsgEvent(type,getRegion(), getAgent(), null,false,false);
    }

    public MsgEvent getPluginMsgEvent(MsgEvent.Type type, String dstPlugin) {
        return getMsgEvent(type,getRegion(),getAgent(),dstPlugin,false,false);
    }

    private MsgEvent getMsgEvent(MsgEvent.Type type, String dstRegion, String dstAgent, String dstPlugin, boolean isRegional, boolean isGlobal) {

        MsgEvent msg = null;
        try {
            msg = new MsgEvent(type, getRegion(),getAgent(),getPluginID(),dstRegion,dstAgent,dstPlugin,isRegional ,isGlobal);
        } catch(Exception ex) {
            ex.printStackTrace();
        }
        return msg;
    }

    public void setExecutor(Executor executor) {
        this.executor = executor;
    }

    public boolean isActive() { return this.isActive; }
    public void setIsActive(boolean isActive) { this.isActive = isActive; }

    protected class MessageProcessor implements Runnable {
        /** Incoming MsgEvent object */
        private MsgEvent msg;
        /** MessageProcessor Cresco Logger instance */
        private final CLogger logger;

        /**
         * Constructor
         * @param msg   MsgEvent to process
         */
        MessageProcessor(MsgEvent msg) {
            this.msg = msg;
            logger = getLogger(MessageProcessor.class.getName(), CLogger.Level.Info);
        }

        /**
         * Processing method
         */
        @Override
        public void run() {
            try {

                if(msg.dstIsLocal(getRegion(),getAgent(),getPluginID())) {

                    MsgEvent retMsg = null;

                    if (executor != null) {

                        switch (msg.getMsgType().toString().toUpperCase()) {
                            case "CONFIG":
                                retMsg = executor.executeCONFIG(msg);
                                break;
                            case "DISCOVER":
                                retMsg = executor.executeDISCOVER(msg);
                                break;
                            case "ERROR":
                                retMsg = executor.executeERROR(msg);
                                break;
                            case "EXEC":
                                retMsg = executor.executeEXEC(msg);
                                break;
                            case "INFO":
                                retMsg = executor.executeINFO(msg);
                                break;
                            case "WATCHDOG":
                                retMsg = executor.executeWATCHDOG(msg);
                                break;
                            case "KPI":
                                retMsg = executor.executeKPI(msg);
                                break;

                            default:
                                logger.error("UNKNOWN MESSAGE TYPE! " + msg.getParams());
                                break;
                        }


                    if ((retMsg != null) && (retMsg.getParams().keySet().contains("is_rpc"))) {
                        retMsg.setReturn();
                        //pick up self-rpc, unless ttl == 0
                        String callId = retMsg.getParam(("callId-" + getRegion() + "-" +
                                getAgent() + "-" + getPluginID()));

                        //if ((callId != null) && (ttl > 0)) {
                        if (callId != null) {
                            receiveRPC(callId, retMsg);
                        } else {
                            msgOut(retMsg);
                        }


                    }
                } else {
                        System.out.println("Executor == null " + msg.printHeader() + " plugin: " + getPluginID());
                    }
                }

            } catch (Exception e) {
                //logger.error("Message Execution Exception: {}", e.getMessage());
                //System.out.println("MessageProcessor ERROR : " + msg.getParams());
                e.printStackTrace();
            }
        }
    }

    public String getPluginName(String jarFile) {
        String version = null;

        try{
            File file = new File(jarFile);

            boolean calcHash = true;
            BasicFileAttributes attr = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
            long fileTime = attr.creationTime().toMillis();

            FileInputStream fis = null;
            JarInputStream jarStream = null;

            try {

                fis = new FileInputStream(file);
                jarStream = new JarInputStream(fis);
                Manifest mf = jarStream.getManifest();
                if (mf != null) {
                    Attributes mainAttribs = mf.getMainAttributes();
                    if (mainAttribs != null) {
                        version = mainAttribs.getValue("Bundle-SymbolicName");
                    }
                }
            } catch(Exception ex) {
                ex.printStackTrace();
            } finally {

                if(jarStream != null) {
                    jarStream.close();
                }

                if(fis != null) {
                    fis.close();
                }
            }
        }
        catch(Exception ex)
        {
            ex.printStackTrace();
        }
        return version;
    }

    public String getPluginVersion(String jarFile) {
        String version = null;
        try{
            File file = new File(jarFile);

            boolean calcHash = true;
            BasicFileAttributes attr = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
            long fileTime = attr.creationTime().toMillis();

            FileInputStream fis = null;
            JarInputStream jarStream = null;

            try {
                fis = new FileInputStream(file);
                jarStream = new JarInputStream(fis);
                Manifest mf = jarStream.getManifest();

                if (mf != null) {
                    Attributes mainAttribs = mf.getMainAttributes();
                    if (mainAttribs != null) {
                        version = mainAttribs.getValue("Bundle-Version");
                    }
                }
            } catch(Exception ex) {
                ex.printStackTrace();
            } finally {

                if(jarStream != null) {
                    jarStream.close();
                }

                if(fis != null) {
                    fis.close();
                }
            }
        }
        catch(Exception ex)
        {
            ex.printStackTrace();

        }
        return version;
    }

    public String getJarMD5(String pluginFile) {
        String jarString = null;
        try
        {
            Path path = Paths.get(pluginFile);
            byte[] data = Files.readAllBytes(path);

            MessageDigest m= MessageDigest.getInstance("MD5");
            m.update(data);
            jarString = new BigInteger(1,m.digest()).toString(16);
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
        return jarString;
    }

    public List<Map<String,String>> getPluginInventory(String repoPath) {
        List<Map<String,String>> pluginFiles = null;
        try
        {
            File folder = new File(repoPath);
            if(folder.exists())
            {
                pluginFiles = new ArrayList<>();
                File[] listOfFiles = folder.listFiles();

                for (int i = 0; i < listOfFiles.length; i++)
                {
                    if (listOfFiles[i].isFile())
                    {
                        try{
                            String jarPath = listOfFiles[i].getAbsolutePath();
                            String jarFileName = listOfFiles[i].getName();
                            String pluginName = getPluginName(jarPath);
                            String pluginMD5 = getJarMD5(jarPath);
                            String pluginVersion = getPluginVersion(jarPath);
                            //System.out.println(pluginName + " " + jarFileName + " " + pluginVersion + " " + pluginMD5);
                            //pluginFiles.add(listOfFiles[i].getAbsolutePath());
                            Map<String,String> pluginMap = new HashMap<>();
                            pluginMap.put("pluginname",pluginName);
                            pluginMap.put("jarfile",jarFileName);
                            pluginMap.put("md5",pluginMD5);
                            pluginMap.put("version",pluginVersion);
                            pluginFiles.add(pluginMap);
                        } catch(Exception ex) {

                        }

                    }

                }
                if(pluginFiles.isEmpty())
                {
                    pluginFiles = null;
                }
            }
        }
        catch(Exception ex)
        {
            pluginFiles = null;
        }
        return pluginFiles;
    }

}
