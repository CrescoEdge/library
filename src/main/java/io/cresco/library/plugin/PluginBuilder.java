package io.cresco.library.plugin;

import io.cresco.library.agent.AgentService;
import io.cresco.library.messaging.MsgEvent;
import io.cresco.library.messaging.RPC;
import io.cresco.library.metrics.CrescoMeterRegistry;
import io.cresco.library.security.ActionAuthorizer;
import io.cresco.library.security.Caller;
import io.cresco.library.security.JarSignatures;
import io.cresco.library.utilities.CLogger;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

public class PluginBuilder {

    private static final Logger log = LoggerFactory.getLogger(PluginBuilder.class);

    // When the plugin looks AgentService up from the registry it is tracked (not cached once),
    // so the plugin re-acquires the current AgentService if the controller restarts. When the
    // agent injects its own AgentService directly, the field below is used instead.
    private AgentService agentService;
    private ServiceTracker<AgentService, AgentService> agentServiceTracker;
    private BundleContext bundleContext;
    private static final long AGENT_SERVICE_WAIT_MS = 30000L;

    /**
     * The OSGi BundleContext of the bundle hosting this PluginBuilder (the controller or a plugin).
     * Used to register/track OSGi services such as {@code org.apache.felix.hc.api.HealthCheck}.
     * May be null if the builder was constructed with a directly-injected AgentService.
     */
    public BundleContext getBundleContext() { return bundleContext; }
    private Config config;
    private CrescoMeterRegistry crescoMeterRegistry;
    private String baseClassName;
    private Executor executor;
    private AtomicBoolean isActive = new AtomicBoolean(false);
    private RPC rpc;
    private ExecutorService msgInProcessQueue;
    /** Who may run which action (verified caller, roles, privileged floor): checked before the executor runs. */
    private final ActionAuthorizer authorizer;
    private final java.util.concurrent.atomic.AtomicLong denied = new java.util.concurrent.atomic.AtomicLong();
    private volatile long denyLogWindowStart;
    private final java.util.concurrent.atomic.AtomicInteger denyLogsInWindow = new java.util.concurrent.atomic.AtomicInteger();

    public PluginBuilder(String className, BundleContext context, Map<String,Object> configMap) {
        this(null,className,context,configMap);
    }

    public PluginBuilder(AgentService agentService, String className, BundleContext context, Map<String,Object> configMap) {

        //bounded pool: a cached (unbounded) pool here means thread-per-message under broker
        //churn, which exhausts threads/FDs on constrained edge hosts; CallerRuns pushes
        //backpressure onto the delivery thread instead of dropping or growing without limit
        int msgInMaxThreads = Math.max(4, Runtime.getRuntime().availableProcessors());
        ThreadPoolExecutor msgInPool = new ThreadPoolExecutor(msgInMaxThreads, msgInMaxThreads,
                60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(1024),
                new ThreadPoolExecutor.CallerRunsPolicy());
        msgInPool.allowCoreThreadTimeOut(true);
        this.msgInProcessQueue = msgInPool;

        // Always retain the BundleContext. Both construction paths (injected AgentService and the
        // tracked-AgentService path below) need it — e.g. the controller registers HealthCheck OSGi
        // services through it. Previously it was set only in the tracked path, so the controller's
        // getBundleContext() returned null and the health executor could not start.
        this.bundleContext = context;
        String bundleName = null;
        try {
            if (context != null && context.getBundle() != null) bundleName = context.getBundle().getSymbolicName();
        } catch (Exception ignore) {
            // a test or mock context: the bundle may be named later (ActionAuthorizer.setBundle)
        }
        this.authorizer = new ActionAuthorizer(bundleName);

        String identString = null;

        if(agentService == null) {
            // Track AgentService dynamically instead of a one-shot lookup + busy-wait cast loop.
            this.bundleContext = context;
            this.agentServiceTracker = new ServiceTracker<>(context, AgentService.class, null);
            this.agentServiceTracker.open();
            try {
                AgentService initial = agentServiceTracker.waitForService(AGENT_SERVICE_WAIT_MS);
                if (initial == null) {
                    log.error("PluginBuilder: AgentService not available after {}ms", AGENT_SERVICE_WAIT_MS);
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
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

    /**
     * Current AgentService: the tracked service if this PluginBuilder looked it up from the
     * registry (re-acquired on controller restart), otherwise the directly-injected one.
     * Waits briefly if the tracked service is momentarily unavailable.
     */
    private AgentService agentService() {
        ServiceTracker<AgentService, AgentService> tracker = agentServiceTracker;
        if (tracker != null) {
            AgentService svc = tracker.getService();
            if (svc == null) {
                // OUT-111: never wait while this bundle or the framework is stopping (the controller's AgentService is gone
                // for good then): a 30 s wait per call there made every JVM stop end in SIGKILL. Otherwise wait in short
                // slices, so a stop that begins during the wait ends it too.
                long deadline = System.currentTimeMillis() + AGENT_SERVICE_WAIT_MS;
                try {
                    while (svc == null && !stopping()) {
                        long left = deadline - System.currentTimeMillis();
                        if (left <= 0) break;
                        svc = tracker.waitForService(Math.min(left, AGENT_SERVICE_WAIT_SLICE_MS));
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                } catch (IllegalStateException closedTracker) {
                    return null;
                }
            }
            return svc;
        }
        return agentService;
    }

    private static final long AGENT_SERVICE_WAIT_SLICE_MS = 250L;
    private volatile boolean closed;

    /**
     * Is this bundle, or the framework, stopping (or this builder closed)? Then the AgentService will not come back and
     * nothing should wait for it.
     */
    boolean stopping() {
        if (closed) return true;
        BundleContext ctx = bundleContext;
        if (ctx == null) return false;
        try {
            org.osgi.framework.Bundle self = ctx.getBundle();
            if (self != null && (self.getState() & (org.osgi.framework.Bundle.STOPPING | org.osgi.framework.Bundle.RESOLVED
                    | org.osgi.framework.Bundle.UNINSTALLED)) != 0) {
                return true;
            }
            org.osgi.framework.Bundle system = ctx.getBundle(0);
            return system != null && (system.getState() & org.osgi.framework.Bundle.STOPPING) != 0;
        } catch (IllegalStateException invalidContext) {
            return true;                                   // the bundle has stopped: its context is no longer valid
        } catch (Exception other) {
            return false;
        }
    }

    /** Close the AgentService tracker; call from the plugin's stop path to avoid leaking trackers across reloads. */
    public void close() {
        closed = true;
        try {
            if (agentServiceTracker != null) {
                agentServiceTracker.close();
                agentServiceTracker = null;
            }
        } catch (Exception ex) {
            /* ignore */
        }
        try {
            msgInProcessQueue.shutdown();
            if (!msgInProcessQueue.awaitTermination(5, TimeUnit.SECONDS)) {
                msgInProcessQueue.shutdownNow();
            }
        } catch (InterruptedException ie) {
            msgInProcessQueue.shutdownNow();
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            /* ignore */
        }
    }

    public void setLogLevel(String logId, CLogger.Level level) {
        AgentService as = agentService();
        if(as != null) {
            as.setLogLevel(logId,level);
        }
    }

    public AgentService getAgentService() {
        return agentService();
    }

    public Config getConfig() { return config;}

    /**
     * The fabric-wide crypto policy this node holds (set at the global controller, carried down to every region
     * and agent), or null when it has not arrived yet. A plugin that chooses an algorithm for something new checks
     * it here, and refuses on null (fail closed); it never reads crypto policy keys from its own config.
     */
    public io.cresco.library.crypto.CryptoPolicy getCryptoPolicy() { return io.cresco.library.crypto.CryptoPolicy.current(); }
    public void setConfig(Map<String,Object> configMap) {
        this.config = new Config(configMap);
    }
    public String getAgent() { return agentService().getAgentState().getAgent(); }
    public String getRegion() { return agentService().getAgentState().getRegion(); }
    public String getPluginID() { return config.getStringParam("pluginID"); }

    public void msgIn(MsgEvent message) {
        if (message != null) {

            String callId = message.getParam(("callId-" + this.getRegion() + "-" +
                    this.getAgent() + "-" + this.getPluginID()));
            if (callId != null) {
                //don't return rpc directly back to caller if self-called
                int ttl;
                try {
                    ttl = Integer.parseInt(message.getParam("ttl"));
                } catch (RuntimeException badTtl) {
                    ttl = 0;                                    // no or malformed ttl: not a reply that travelled
                }
                if(ttl > 0) {
                    this.receiveRPC(callId, message);
                    return;
                }
            }

            if(executor != null) {
                    msgInProcessQueue.submit(new MessageProcessor(message));
            }

        }
    }

    public void receiveRPC(String callId, MsgEvent msg) {

        rpc.putReturnMessage(callId, msg);
    }

    public void msgOut(MsgEvent msg) {

        agentService().msgOut(getPluginID(), msg);

    }

    /**
     * Send {@code msg} on behalf of an API user this plugin has authenticated. The controller stamps {@code caller} as
     * the message's verified caller, but only for a bundle listed in {@code on_behalf_bundles} (default
     * {@code io.cresco.wsapi}); for any other bundle it refuses and nothing is sent. {@code caller} must be an API caller
     * ({@link Caller#api}).
     *
     * @throws SecurityException this bundle may not act on behalf of a user, or {@code caller} is not an API caller
     */
    public void msgOutOnBehalfOf(MsgEvent msg, Caller caller) {
        if (caller == null || caller.getKind() != Caller.Kind.API) {
            throw new SecurityException("msgOutOnBehalfOf needs an API caller, not " + caller);
        }
        AgentService as = agentService();
        if (as == null) {
            throw new IllegalStateException("AgentService is not available");
        }
        as.msgOutOnBehalfOf(getPluginID(), msg, caller);
    }

    /** {@link #sendRPC(MsgEvent)} on behalf of an API user; see {@link #msgOutOnBehalfOf}. */
    public MsgEvent sendRPCOnBehalfOf(MsgEvent msg, Caller caller) {
        msg.setParam("is_rpc",Boolean.TRUE.toString());
        return this.rpc.call(msg, caller);
    }

    /** {@link #sendRPC(MsgEvent, long)} on behalf of an API user; see {@link #msgOutOnBehalfOf}. */
    public MsgEvent sendRPCOnBehalfOf(MsgEvent msg, long timeout, Caller caller) {
        msg.setParam("is_rpc",Boolean.TRUE.toString());
        return this.rpc.call(msg, timeout, caller);
    }

    /**
     * Verify a jar that arrived over the network before it is installed or stored: it must be signed, every entry, by a
     * signer chaining to {@code plugin_signing_truststore} ({@link JarSignatures#verify}). Returns the jar's SHA-384
     * (lowercase hex), computed here; a hash a caller supplied is advisory only.
     *
     * <p>Only when {@code plugin_signing_required=false} is set explicitly (development) is an unsigned or untrusted jar
     * accepted, with a WARNING; its SHA-384 is still returned.
     *
     * @throws JarSignatures.Rejected the jar is not acceptable (including: no trust store configured)
     */
    public String verifyJarSignature(Path jar) throws java.io.IOException, java.security.GeneralSecurityException {
        AgentService as = agentService();
        if (as == null) {
            throw new JarSignatures.Rejected("AgentService is not available, so the signing policy is unknown");
        }
        try {
            return JarSignatures.verify(jar, as.getPluginSigningTrust());
        } catch (JarSignatures.Rejected rejected) {
            if (as.isPluginSigningRequired()) {
                throw rejected;
            }
            log.warn("plugin_signing_required=false: accepting {} although {}", jar, rejected.getMessage());
            return JarSignatures.sha384Hex(jar);
        }
    }
    public CrescoMeterRegistry getCrescoMeterRegistry() { return crescoMeterRegistry; }

    public CLogger getLogger(String issuingClassName, CLogger.Level level) {
        //return new CLogger(this,baseClassName,issuingClassName,level);
        return agentService().getCLogger(this,baseClassName,issuingClassName,level);
    }

    public String getPluginDataDirectory() {
        if(getPluginID() != null) {
            return agentService().getAgentDataDirectory() + "/plugin-data/" + getPluginID();
        } else {
            return agentService().getAgentDataDirectory();
        }
    }

    public boolean isIPv6() {
        boolean isv6 = false;
        if(config != null) {
            isv6 = config.getBooleanParam("ipv6", false);
        }
        return isv6;
    }

    public MsgEvent sendRPC(MsgEvent msg) {
        msg.setParam("is_rpc",Boolean.TRUE.toString());
        return this.rpc.call(msg);
    }

    public MsgEvent sendRPC(MsgEvent msg, long timeout) {
        msg.setParam("is_rpc",Boolean.TRUE.toString());
        return this.rpc.call(msg, timeout);
    }

    public MsgEvent getGlobalControllerMsgEvent(MsgEvent.Type type) {
        return getMsgEvent(type, getRegion(),getAgent(), null,true,true);
    }

    public MsgEvent getGlobalControllerMsgEvent(MsgEvent.Type type, String region, String agent) {
        return getMsgEvent(type, region, agent, null,true,true);
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

    public MsgEvent getRegionalControllerMsgEvent(MsgEvent.Type type, String region, String agent) {
        return getMsgEvent(type, region, agent, null,true,false);
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
            log.error("getMsgEvent", ex);
        }
        return msg;
    }

    public void setExecutor(Executor executor) {
        this.executor = executor;
        if (executor != null) authorizer.register(executor.getClass());
    }

    /** The authorization table of this bundle (its executors' {@code @CrescoAction} roles and the privileged floor). */
    public ActionAuthorizer getAuthorizer() { return authorizer; }

    /** Messages refused so far for want of an authorized caller. */
    public long deniedCount() { return denied.get(); }

    /**
     * May the verified caller stamped on {@code msg} run its action here? The executor never runs a message this refuses.
     * See {@link ActionAuthorizer}.
     */
    public ActionAuthorizer.Decision authorize(MsgEvent msg) {
        return authorize(msg, authorizer);
    }

    /** {@link #authorize(MsgEvent)} against another table (the controller's regional and global tiers). */
    public ActionAuthorizer.Decision authorize(MsgEvent msg, ActionAuthorizer table) {
        AgentService as = agentService();
        String tenant = null;
        java.util.Set<String> superuser = java.util.Collections.emptySet();
        if (as != null) {
            try {
                if (!as.isSecurityEnabled()) return ActionAuthorizer.insecure(msg);
                tenant = as.getTenant();
                java.util.Set<String> s = as.getSuperuserTenants();
                if (s != null) superuser = s;
            } catch (Exception ignore) {
                // unknown: fail closed (only what needs no tenant passes)
            }
        }
        return table.decide(msg, tenant, superuser);
    }

    /**
     * Authorize {@code msg} and, when it is refused: count it ({@code cresco.authz.denied}), log it (rate limited), and
     * answer an RPC request with {@code status_code=403}, {@code status_desc=unauthorized: <action> needs <roles>}.
     * Returns true when it may run.
     */
    public boolean admit(MsgEvent msg) {
        return admit(msg, authorizer);
    }

    /** {@link #admit(MsgEvent)} against another table (the controller's regional and global tiers). */
    public boolean admit(MsgEvent msg, ActionAuthorizer table) {
        ActionAuthorizer.Decision d = authorize(msg, table);
        if (d.allowed) return true;
        denied.incrementAndGet();
        String action = d.action == null ? "none" : d.action;
        try {
            if (crescoMeterRegistry != null) {
                // the tag is bounded: an action this table does not know is counted as "other"
                String tag = table.isKnown(action) ? action : "other";
                crescoMeterRegistry.counter("cresco.authz.denied", "action", tag).increment();
            }
        } catch (Exception ignore) { }
        long now = System.currentTimeMillis();
        if (now - denyLogWindowStart > 10_000L) { denyLogWindowStart = now; denyLogsInWindow.set(0); }
        if (denyLogsInWindow.incrementAndGet() <= 20) {
            log.warn("DENIED {} {} from {} at {} ({}): {}", msg.getMsgType(), action, d.caller == null ? "no verified caller" : d.caller,
                    getPluginID() == null ? "the controller" : getPluginID(), msg.printHeader(), d.statusDesc());
        }
        if (isRpcRequest(msg)) {
            try {
                msg.setParam("status_code", "403");
                msg.setParam("status_desc", d.statusDesc());
                msg.setReturn();
                String callId = msg.getParam("callId-" + getRegion() + "-" + getAgent() + "-" + getPluginID());
                if (callId != null) {
                    rpc.putReturnMessage(callId, msg, true);
                } else {
                    msgOut(msg);
                }
            } catch (Exception ex) {
                log.error("could not answer a refused call: {}", ex.getMessage());
            }
        }
        return false;
    }

    /** An RPC request (not a reply): its caller's call id is keyed by its source, which a reply has swapped away. */
    private static boolean isRpcRequest(MsgEvent m) {
        if (m.getParams() == null || m.getParam("is_rpc") == null) return false;
        return m.getParam("callId-" + m.getSrcRegion() + "-" + m.getSrcAgent() + "-" + m.getSrcPlugin()) != null;
    }

    public boolean isActive() { return this.isActive.get(); }
    public void setIsActive(boolean isActive) { this.isActive.set(isActive); }

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

                    // deny by default: no executor runs a message whose verified caller may not run its action
                    if (!admit(msg)) {
                        return;
                    }

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

                        if (callId != null) {
                            rpc.putReturnMessage(callId, retMsg, true);   // this executor answered its own bundle's call
                        } else {
                            msgOut(retMsg);
                        }


                    }
                } else {
                        log.error("Executor == null {} plugin: {}", msg.printHeader(), getPluginID());
                    }
                }

            } catch (Exception e) {
                log.error("MessageProcessor.run", e);
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
                log.error("getPluginName", ex);
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
            log.error("getPluginName", ex);
        }
        return version;
    }

    public String getPluginVersion(String jarFile) {
        String version = null;
        try{

            InputStream is = null;
            JarInputStream jarStream = null;

            
            try {

                if(jarFile.contains("!")) {
                    jarFile = jarFile.replace("\\","/");
                    jarFile = "jar:file:/" + jarFile;
                    URL inputURL = new URL(jarFile);
                    JarURLConnection conn = (JarURLConnection)inputURL.openConnection();
                    is = conn.getInputStream();

                } else {
                    Path path = Paths.get(jarFile);
                    is = new FileInputStream(path.toFile());
                }

                jarStream = new JarInputStream(is);
                Manifest mf = jarStream.getManifest();

                if (mf != null) {
                    Attributes mainAttribs = mf.getMainAttributes();
                    if (mainAttribs != null) {
                        version = mainAttribs.getValue("Bundle-Version");
                    }
                }
            } catch(Exception ex) {
                log.error("getPluginVersion", ex);
            } finally {

                if(jarStream != null) {
                    jarStream.close();
                }

                if(is != null) {
                    is.close();
                }
            }
        }
        catch(Exception ex)
        {
            log.error("getPluginVersion", ex);

        }
        return version;
    }


    public String getMD5(String jarLocation) {
        String hashString = null;
        try {

            if(jarLocation.startsWith("jar:")) {

                URL inputURL = null;
                InputStream in = null;


                inputURL = new URL(jarLocation);
                JarURLConnection conn = (JarURLConnection)inputURL.openConnection();
                in = conn.getInputStream();

                if(in != null) {
                    hashString = getMD5(in);
                    in.close();
                }


            } else {

                InputStream inputStream = null;

                if(jarLocation.contains("!")) {
                    URL fileURL = getClass().getClassLoader().getResource(jarLocation);
                    if (fileURL != null) {
                        inputStream = getClass().getClassLoader().getResourceAsStream(fileURL.getPath());
                    }

                } else {

                    if(jarLocation.startsWith("file:")) {
                        jarLocation = jarLocation.replace("file:","");
                    }
                    Path checkPath = Paths.get(jarLocation);

                    if (checkPath.toFile().exists()) {
                        inputStream = new FileInputStream(jarLocation);
                    }
                }

                if (inputStream != null) {
                    hashString = getMD5(inputStream);
                    inputStream.close();
                }

            }

        } catch (Exception ex) {
            log.error("getMD5", ex);
        }
        //return complete hash
        return hashString;
    }


    // Compatibility alias: the algorithm is now the FIPS-approved SHA-256 (CrescoCrypto), not MD5.
    // New code should call CrescoCrypto.contentHashHex directly. The wire param key "md5" still
    // carries this value (now SHA-256); a fabric-wide rename is tracked in the compliance POA&M.
    @Deprecated
    public String getMD5(InputStream inputStream) {
        String hashString = null;
        try {
            if (inputStream != null) {
                hashString = io.cresco.library.crypto.CrescoCrypto.contentHashHex(inputStream);
                inputStream.close();
            }
        } catch (Exception ex) {
            log.error("getMD5", ex);
        }
        return hashString;
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
                            String pluginMD5 = getMD5(jarPath);
                            String pluginVersion = getPluginVersion(jarPath);

                            Map<String,String> pluginMap = new HashMap<>();
                            pluginMap.put("pluginname",pluginName);
                            pluginMap.put("jarfile",jarFileName);
                            pluginMap.put("md5",pluginMD5);
                            pluginMap.put("version",pluginVersion);
                            pluginFiles.add(pluginMap);
                        } catch(Exception ex) {
                            log.error("getPluginInventory", ex);
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
