package io.cresco.library.messaging;


import javax.xml.bind.annotation.adapters.XmlAdapter;

class MsgEventTypesAdapter extends XmlAdapter<String, MsgEvent.Type> {
    @Override
    public MsgEvent.Type unmarshal(String v) throws IllegalArgumentException {
        return Enum.valueOf(MsgEvent.Type.class, v);
    }

    @Override
    public String marshal(MsgEvent.Type v) throws IllegalArgumentException {
        return v.toString();
    }
}