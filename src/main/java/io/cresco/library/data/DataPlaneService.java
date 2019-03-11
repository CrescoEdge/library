package io.cresco.library.data;

import org.apache.activemq.BlobMessage;

import javax.jms.*;

public interface DataPlaneService {

    String addMessageListener(TopicType topicType, MessageListener messageListener, String selectorString);
    boolean sendMessage(TopicType topicType, Message message);
    BytesMessage createBytesMessage();
    MapMessage createMapMessage();
    Message createMessage();
    ObjectMessage createObjectMessage();
    StreamMessage createStreamMessage();
    TextMessage createTextMessage();
    BlobMessage createBlobMessage();
    String createCEP(String inputRecordSchemaString, String inputStreamName, String outputStreamName, String outputStreamAttributesString,String queryString);
    void input(String cepId, String streamName, String jsonPayload);
    boolean removeCEP(String cepId);
}
