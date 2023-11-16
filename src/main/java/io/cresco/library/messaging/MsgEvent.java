package io.cresco.library.messaging;

//import javax.xml.bind.DatatypeConverter;
//import javax.xml.bind.annotation.XmlRootElement;
//import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

//@XmlRootElement
public class MsgEvent {
    public enum Type {
        CONFIG, DISCOVER, ERROR, EXEC, GC, INFO, KPI, LOG, WATCHDOG
    }

    //headers
    private Type msgType;
    private String src_region = null;
    private String src_agent = null;
    private String src_plugin = null;
    private String dst_region = null;
    private String dst_agent = null;
    private String dst_plugin = null;
    private boolean isRegional = false;
    private boolean isGlobal = false;

    //msgparams constants
    private static final String HAS_OUTGOING_FILES = "HAS_OUTGOING_FILES";
    private static final String IS_PATH_TRACE = "IS_PATH_TRACE";



    //private boolean hasOutgoingFiles = false;

    private Map<String, String> msgparams;
    private Map<String, String> params;

    //for file list
    private List<String> fileList;

    //for path trace
    private List<Map<String,String>> pathList;

    public MsgEvent() {

    }

    public MsgEvent(Type msgType, String src_region, String src_agent, String src_plugin, String dst_region, String dst_agent, String dst_plugin, boolean isRegional, boolean isGlobal) {
        //create header
        this.msgType = msgType;
        this.src_region = src_region;
        this.src_agent = src_agent;
        this.src_plugin = src_plugin;

        this.dst_region = dst_region;
        this.dst_agent = dst_agent;
        this.dst_plugin = dst_plugin;

        this.isRegional = isRegional;
        this.isGlobal = isGlobal;


        //create payload params
        this.params = new HashMap<String, String>();

        //create message params
        msgparams = new HashMap<>();

    }


    public MsgEvent(Type msgType, String msgRegion, String msgAgent, String msgPlugin, String msgBody) {
        this.msgType = msgType;
        this.params = new HashMap<String, String>();
        msgparams = new HashMap<>();
    }

    /*

    public MsgEvent(Type msgType, String msgRegion, String msgAgent, String msgPlugin, Map<String, String> params) {
        this.msgType = msgType;
        //this.msgRegion = msgRegion;
        //this.msgAgent = msgAgent;
        //this.msgPlugin = msgPlugin;
        this.params = params;
        this.params = new HashMap<String, String>(params);
        msgparams = new HashMap<>();
    }
    */

    public boolean isGlobal() {
        return isGlobal;
    }

    public boolean isRegional() {
        return isRegional;
    }

    public String getSrcPlugin() {
        return src_plugin;
    }

    public String getSrcRegion() {
        return src_region;
    }

    public String getSrcAgent() {
        return src_agent;
    }

    public String getDstRegion() {
        return dst_region;
    }

    public String getDstAgent() {
        return dst_agent;
    }

    public String getDstPlugin() {
        return dst_plugin;
    }

    public boolean dstIsLocal(String localRegion, String localAgent, String localPlugin) {
        boolean isLocal = false;

            if(dst_region.equals(localRegion) && dst_agent.equals(localAgent)) {

                if((localPlugin == null) && (dst_plugin == null)) {
                    isLocal = true;
                } else if((localPlugin != null) && (dst_plugin != null)) {
                    if(localPlugin.equals(dst_plugin)) {
                        isLocal = true;
                    }
                }
            }

        return isLocal;
    }

    public void setSrc(String region, String agent, String plugin) {
        setParam("src_region", region);
        setParam("src_agent", agent);
        setParam("src_plugin", plugin);
    }

    public void setDst(String region, String agent, String plugin) {
        setParam("dst_region", region);
        setParam("dst_agent", agent);
        setParam("dst_plugin", plugin);
    }

    public void setForwardDst(String dstRegion, String dstAgent, String dstPlugin) {
        this.dst_region = dstRegion;
        this.dst_agent = dstAgent;
        this.dst_plugin = dstPlugin;

    }

    public String getForwardDst() {

        if ((dst_region != null) && (dst_agent != null)) {
            return dst_region + "_" + dst_agent;
        } else if (dst_region != null) {
            return dst_region;
        } else {
            return null;
        }
    }

    public void setReturn() {

        String src_region_tmp = src_region;
        String src_agent_tmp = src_agent;
        String src_plugin_tmp = src_plugin;

        src_region = null;
        src_agent = null;
        src_plugin = null;

        src_region = dst_region;
        src_agent = dst_agent;
        if(dst_plugin != null) {
            src_plugin = dst_plugin;
        }

        dst_region = src_region_tmp;
        dst_agent = src_agent_tmp;
        dst_plugin = src_plugin_tmp;

    }

    public String printHeader() {

        return "type:" + msgType.toString() + " src_region:" + src_region + " src_agent:" + src_agent +
                " src_plugin:" + src_plugin + " dst_region:" + dst_region + " dst_agent:" +dst_agent +
                " dst_plugin:" + dst_plugin;
    }


    /*
    public String getMsgBody() {
        return params.get("msg");
    }

    public void setMsgBody(String msgBody) {
        params.put("msg", msgBody);
    }
    */

    //@XmlJavaTypeAdapter(MsgEventTypesAdapter.class)
    public Type getMsgType() {
        return msgType;
    }

    public void setMsgType(Type msgType) {
        this.msgType = msgType;
    }


    //@XmlJavaTypeAdapter(MsgEventParamsAdapter.class)
    public Map<String, String> getParams() {
        return params;
    }

    public void setParams(Map<String, String> params) {
        this.params = params;
    }

    public String getParam(String key) {
        return params.get(key);
    }

    public void setParam(String key, String value) {

        /*
        try {
            if ((key.startsWith("src_")) || (key.startsWith("dst_"))) {
                throw new NullPointerException("Found SRC_ or DST_");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        */
        params.put(key, value);
    }

    public void removeParam(String key) {
        params.remove(key);
    }

    public void setCompressedParam(String key, String value) {
        //params.put(key, DatatypeConverter.printBase64Binary(stringCompress(value)));
        params.put(key,Base64.getEncoder().encodeToString(stringCompress(value)));
    }

    public void addFile(String filePath) {
        try {
            if(fileList == null) {
                fileList = new ArrayList<>();
                msgparams.put(HAS_OUTGOING_FILES,Boolean.TRUE.toString());
            }
            fileList.add(filePath);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void clearFileList() {
        if(fileList != null) {
            fileList.clear();
        }
        msgparams.put(HAS_OUTGOING_FILES,Boolean.FALSE.toString());
    }

    public void addFiles(List<String> files) {
        if(fileList == null) {
            fileList = new ArrayList<>();
            msgparams.put(HAS_OUTGOING_FILES,Boolean.TRUE.toString());
        }
        fileList.addAll(files);
    }

    public List<String> getFileList() {
        return fileList;
    }

    public boolean hasFiles() {

        if(msgparams.containsKey(HAS_OUTGOING_FILES)) {
            if(Boolean.parseBoolean(msgparams.get(HAS_OUTGOING_FILES))) {
                return true;
            }
        }

        return false;
    }

    public boolean isPathTrace() {

        if(msgparams.containsKey(IS_PATH_TRACE)) {
            if(Boolean.parseBoolean(msgparams.get(IS_PATH_TRACE))) {
                return true;
            }
        }

        return false;
    }

    public void setIsPathTrace(Boolean pathTrace) {

        msgparams.put(IS_PATH_TRACE,pathTrace.toString());

    }

    public void addPath(Map<String,String> pathMap) {
        try {
            if(pathList == null) {
                pathList = new ArrayList<>();
                msgparams.put(IS_PATH_TRACE,Boolean.TRUE.toString());
            }
            pathList.add(pathMap);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }



    public List<Map<String,String>> getPathList() {
        List<Map<String,String>> tmpPathList = new ArrayList<>();
        tmpPathList.addAll(pathList);
        return tmpPathList;
    }

    public void setDataParam(String key, byte[] value) {
        //params.put(key, DatatypeConverter.printBase64Binary(value));
        params.put(key,Base64.getEncoder().encodeToString(value));
    }

    public void setCompressedDataParam(String key, byte[] value) {
        //params.put(key, DatatypeConverter.printBase64Binary(value));
        params.put(key,Base64.getEncoder().encodeToString(dataCompress(value)));
    }

    public byte[] getDataParam(String key) {
        byte[] byteArray = null;
        try {
            String value = params.get(key);
            if (value != null) {
                //byteArray = DatatypeConverter.parseBase64Binary(value);
                byteArray = Base64.getDecoder().decode(value);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return byteArray;
    }

    public String getCompressedParam(String key) {
        String value = params.get(key);
        if (value == null)
            return null;
        try {
            //byte[] exportDataRawCompressed = DatatypeConverter.parseBase64Binary(value);
            byte[] exportDataRawCompressed = Base64.getDecoder().decode(value);
            try (InputStream iss = new ByteArrayInputStream(exportDataRawCompressed);
                    InputStream is = new GZIPInputStream(iss);) {
                return new Scanner(is,"UTF-8").useDelimiter("\\A").next();
            }
        } catch (IOException e) {
            return null;
        }
    }

    public byte[] dataCompress(byte[] dataToCompress) {

        byte[] compressedData;
        try {
            ByteArrayOutputStream byteStream =
                    new ByteArrayOutputStream(dataToCompress.length);
            try {
                GZIPOutputStream zipStream =
                        new GZIPOutputStream(byteStream);
                try {
                    zipStream.write(dataToCompress);
                }
                finally {
                    zipStream.close();
                }
            } finally {
                byteStream.close();
            }
            compressedData = byteStream.toByteArray();
        } catch(Exception e) {
            return null;
        }
        return compressedData;
    }

    
    public byte[] stringCompress(String str) {

        byte[] dataToCompress = str.getBytes(StandardCharsets.UTF_8);
        return dataCompress(dataToCompress);
    }

    public boolean paramsContains(String key) {
        return params.containsKey(key);
    }

}