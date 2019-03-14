package io.cresco.library.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FileObject {

    private Map<String,String> filePartMapSource;
    private Map<String,String> filePartMapDest;
    private String dataName;
    private String fileName;
    private String fileMD5Hash;

    private List<String> downloadList;


    public FileObject(String fileName, String fileMD5Hash, Map<String,String> filePartMap, String dataName) {

        this.filePartMapSource = filePartMap;
        this.dataName = dataName;
        this.fileName = fileName;
        this.fileMD5Hash = fileMD5Hash;
        this.filePartMapDest = new HashMap<>();

    }

    public String getDataName() { return dataName; }
    public String getFileName() { return fileName; }
    public String getFileMD5Hash() { return fileMD5Hash; }
    public boolean setDestFilePart(String filePartName, String filePartMD5Hash) {
        boolean isPartSet = false;
        try {

            if(filePartMapSource.containsKey(filePartName)) {
                String sourcePartHash = filePartMapSource.get(filePartName);
                if(sourcePartHash.equals(filePartMD5Hash)) {
                    filePartMapDest.put(filePartName,filePartMD5Hash);
                    isPartSet = true;
                }
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return isPartSet;
    }
    public boolean isFilePartComplete() {
        boolean isComplete = false;
        try {

            if(filePartMapSource.size() == filePartMapDest.size()) {

                boolean isFault = false;
                for (Map.Entry<String, String> entry : filePartMapSource.entrySet()) {
                    String filePartSourceName = entry.getKey();
                    String filePartSourceMD5Hash = entry.getValue();

                    if(filePartMapDest.containsKey(filePartSourceName)) {
                        if(!filePartMapDest.get(filePartSourceName).equals(filePartSourceMD5Hash)) {
                            isFault = true;
                        }
                    } else {
                        isFault = true;
                    }
                }

                if(!isFault) {
                    isComplete = true;
                }

            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return isComplete;
    }

    public List<String> getOrderedPartList() {
        List<String> orderedPartList = null;
        try {
            orderedPartList = new ArrayList<>();

            int partCount = filePartMapSource.size();
            for (int i = 0; i < partCount; i++) {
                orderedPartList.add(dataName + "." + String.valueOf(i));
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return orderedPartList;
    }

}
