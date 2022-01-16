package io.cresco.library.data;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import javax.jms.JMSException;

public interface DataMessage extends DMessage {

    InputStream getInputStream() throws IOException, JMSException;

    URL getURL() throws MalformedURLException, JMSException;

    String getMimeType();

    void setMimeType(String var1);

    String getName();

    void setName(String var1);
}