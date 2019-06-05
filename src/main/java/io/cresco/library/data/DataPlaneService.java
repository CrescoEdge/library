package io.cresco.library.data;

import javax.jms.*;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public interface DataPlaneService {

    //sending messages on DP
    String addMessageListener(TopicType topicType, MessageListener messageListener, String selectorString);
    void removeMessageListener(String listenerId);
    boolean sendMessage(TopicType topicType, Message message);
    BytesMessage createBytesMessage();
    MapMessage createMapMessage();
    Message createMessage();
    ObjectMessage createObjectMessage();
    StreamMessage createStreamMessage();
    TextMessage createTextMessage();

    //CEP services
    String createCEP(String inputRecordSchemaString, String inputStreamName, String outputStreamName, String outputStreamAttributesString,String queryString);
    void input(String cepId, String streamName, String jsonPayload);
    boolean removeCEP(String cepId);

    //sending file messages
    public List<FileObject> createFileObjects(List<String> fileList);
    public FileObject createFileObject(String fileName);
    public Map<String,String> splitFile(String dataName, String fileName);
    public Map<String,String> streamToSplitFile(String dataName, InputStream is);
    public void mergeFiles(List<File> files, File into, boolean deleteParts);
    public List<FileObject> getFileObjectsFromString(String fileObjectsString);
    public String generateFileObjectsString(List<FileObject> fileObjects);
    public Path getJournalPath();
    public Path downloadRemoteFile(String remoteRegion, String remoteAgent, String remoteFilePath, String localFilePath);
}
