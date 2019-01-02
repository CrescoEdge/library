package io.cresco.library.data;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.io.*;
import org.apache.avro.reflect.ReflectData;
import org.apache.avro.reflect.ReflectDatumWriter;
import org.apache.avro.specific.SpecificDatumWriter;

import java.io.ByteArrayOutputStream;

public class CDPUtils {

    public static GenericData.Record stringToGenericDataRecord(Schema schema, String jsonInputPayload) {

        GenericData.Record rec = null;

        try{


            Decoder decoder = new DecoderFactory().jsonDecoder(schema, jsonInputPayload);
            DatumReader<GenericData.Record> reader = new GenericDatumReader<>(schema);
            rec = reader.read(null, decoder);


        } catch(Exception ex) {
            ex.printStackTrace();
        }

        return rec;
    }
    public static GenericData.Record stringToGenericDataRecord(String schemaString, String jsonInputPayload) {

        GenericData.Record rec = null;

        try{

            Schema.Parser parser = new Schema.Parser();
            Schema schema = parser.parse(schemaString);

            Decoder decoder = new DecoderFactory().jsonDecoder(schema, jsonInputPayload);
            DatumReader<GenericData.Record> reader = new GenericDatumReader<>(schema);
            rec = reader.read(null, decoder);


        } catch(Exception ex) {
            ex.printStackTrace();
        }

        return rec;
    }

    public static byte[] stringToGenericDataRecordByte(String schemaString, String jsonInputPayload) {

        byte[] bytes = null;
        try{

            Schema.Parser parser = new Schema.Parser();
            Schema schema = parser.parse(schemaString);

            Decoder decoder = new DecoderFactory().jsonDecoder(schema, jsonInputPayload);
            DatumReader<GenericData.Record> reader = new GenericDatumReader<>(schema);
            GenericData.Record rec = reader.read(null, decoder);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
            DatumWriter<GenericData.Record> writer = new SpecificDatumWriter<>(schema);

            writer.write(rec, encoder);
            encoder.flush();
            out.close();
            bytes = out.toByteArray();

        } catch(Exception ex) {
            ex.printStackTrace();
        }

        return bytes;
    }
    public static byte[] stringToGenericDataRecordByte(Schema schema, String jsonInputPayload) {

        byte[] bytes = null;
        try{

            Decoder decoder = new DecoderFactory().jsonDecoder(schema, jsonInputPayload);
            DatumReader<GenericData.Record> reader = new GenericDatumReader<>(schema);
            GenericData.Record rec = reader.read(null, decoder);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
            DatumWriter<GenericData.Record> writer = new SpecificDatumWriter<>(schema);

            writer.write(rec, encoder);
            encoder.flush();
            out.close();
            bytes = out.toByteArray();

        } catch(Exception ex) {
            ex.printStackTrace();
        }

        return bytes;
    }

    public static String objectToString(Object o) {

        String returnString = null;
        try {

            Schema schema = ReflectData.get().getSchema(o.getClass());
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Encoder encoder = new EncoderFactory().jsonEncoder(schema, outputStream);
            DatumWriter<Object> writer = new ReflectDatumWriter<>(schema);
            writer.write(o, encoder);
            encoder.flush();

            returnString  = new String(outputStream.toByteArray());
        } catch(Exception ex){
            ex.printStackTrace();
        }
        return returnString;
    }

    public static Schema objectToSchema(Object o) {

        Schema schema = null;
        try {

            schema = ReflectData.get().getSchema(o.getClass());

        } catch(Exception ex){
            ex.printStackTrace();
        }
        return schema;
    }
    public static String objectToSchemaString(Object o) {

        String schemaString = null;
        try {

            Schema schema = ReflectData.get().getSchema(o.getClass());
            schemaString = schema.toString();

        } catch(Exception ex){
            ex.printStackTrace();
        }
        return schemaString;
    }




}
