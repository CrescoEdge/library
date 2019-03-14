package io.cresco.library.data;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.*;

public class FileUtils {

    private Path journalPath;

    public FileUtils(String journalDirPath) {
        try {
            //journalPath = FileSystems.getDefault().getPath("journal");
            journalPath = Paths.get(journalDirPath);
            Files.createDirectories(journalPath);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }

    public Path getJournalPath() { return journalPath; }

    public List<FileObject> createFileObjects(List<String> fileList) {
        List<FileObject> fileObjects = null;
        try {

            fileObjects = new ArrayList<>();

            for(String filePath : fileList) {
                fileObjects.add(createFileObject(filePath));
            }

        }catch (Exception ex) {
            ex.printStackTrace();
        }
        return fileObjects;
    }

    public FileObject createFileObject(String fileName) {
        FileObject fileObject = null;
        try {

            File inFile = new File(fileName);
            if(inFile.exists()) {
                String dataName = UUID.randomUUID().toString();
                String fileMD5Hash = getMD5(fileName);

                Map<String, String> dataMap = splitFile(dataName, fileName);
                fileObject = new FileObject(inFile.getName(),fileMD5Hash,dataMap,dataName);
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return fileObject;
    }

    public Map<String,String> splitFile(String dataName, String fileName)  {

        Map<String,String> filePartNames = null;
        try {

            File f = new File(fileName);

            //try-with-resources to ensure closing stream
            FileInputStream fis = new FileInputStream(f);

            filePartNames = streamToSplitFile(dataName, fis);

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return filePartNames;
    }

    public Map<String,String> streamToSplitFile(String dataName, InputStream is)  {

        Map<String,String> filePartNames = null;
        try {


            filePartNames = new HashMap<>();

            int partCounter = 0;//I like to name parts from 001, 002, 003, ...
            //you can change it to 0 if you want 000, 001, ...

            int sizeOfFiles = 1024 * 1024 * 5;// 1MB
            byte[] buffer = new byte[sizeOfFiles];


            //String fileName = UUID.randomUUID().toString();

            //try-with-resources to ensure closing stream
            try (BufferedInputStream bis = new BufferedInputStream(is)) {

                int bytesAmount = 0;
                while ((bytesAmount = bis.read(buffer)) > 0) {
                    //write each chunk of data into separate file with different number in name
                    //String filePartName = String.format("%s.%03d", fileName, partCounter++);

                    String filePartName = dataName + "." + partCounter;
                    //MessageDigest m= MessageDigest.getInstance("MD5");
                    //m.update(buffer);
                    //String md5Hash = new BigInteger(1,m.digest()).toString(16);

                    partCounter++;

                    File newFile = new File(journalPath.toAbsolutePath().toString(), filePartName);
                    try (FileOutputStream out = new FileOutputStream(newFile)) {
                        out.write(buffer, 0, bytesAmount);
                    }

                    String md5Hash = getMD5(newFile.getAbsolutePath());
                    filePartNames.put(filePartName, md5Hash);
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return filePartNames;
    }

    public String getMD5(String filePath) {
        String hashString = null;
        try {
            //Get file input stream for reading the file content
            FileInputStream fis = new FileInputStream(filePath);

            MessageDigest digest = MessageDigest.getInstance("MD5");

            //Create byte array to read data in chunks
            byte[] byteArray = new byte[1024];
            int bytesCount = 0;

            //Read file data and update in message digest
            while ((bytesCount = fis.read(byteArray)) != -1) {
                digest.update(byteArray, 0, bytesCount);
            }
            ;

            //close the stream; We don't need it now.
            fis.close();

            //Get the hash's bytes
            byte[] bytes = digest.digest();

            //This bytes[] has bytes in decimal format;
            //Convert it to hexadecimal format
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < bytes.length; i++) {
                sb.append(Integer.toString((bytes[i] & 0xff) + 0x100, 16).substring(1));
            }

            hashString = sb.toString();

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        //return complete hash
        return hashString;
    }

    public void mergeFiles(List<File> files, File into, boolean deleteParts) {

        try {

            try (FileOutputStream fos = new FileOutputStream(into);
                 BufferedOutputStream mergingStream = new BufferedOutputStream(fos)) {
                for (File f : files) {
                    Files.copy(f.toPath(), mergingStream);
                    if (deleteParts) {
                        f.delete();
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

}
