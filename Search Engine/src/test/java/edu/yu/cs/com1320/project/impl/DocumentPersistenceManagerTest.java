package edu.yu.cs.com1320.project.impl;

import com.google.gson.Gson;
import edu.yu.cs.com1320.project.stage6.Document;
import edu.yu.cs.com1320.project.stage6.impl.DocumentImpl;
import edu.yu.cs.com1320.project.stage6.impl.DocumentPersistenceManager;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DocumentPersistenceManagerTest {

    @Test
    public void start() throws URISyntaxException, IOException {

        Gson gson = new Gson();
        //don't do this
        //use the class from specs

        //5. DocumentPersistenceManager should use instances of
        //com.google.gson.JsonSerializer<Document> and
        //com.google.gson.JsonDeserializer<Document> to (de)serialize from/to disk.

        URI uri= new URI("foo://exale.com:8042/oer/tre?nm=fet#nos");
        HashMap<String, Integer> map = new HashMap<>();
        DocumentImpl doc = new DocumentImpl(uri,"Hello",map);

        //Convert to Json
        String myJson =  gson.toJson(doc);
        System.out.println(myJson);

        //Write to a file
        FileWriter writer = new FileWriter("doc.json");
        gson.toJson(doc,writer);
        writer.close(); //close file

        //Read from file
        FileReader reader = new FileReader("doc.json");
        DocumentImpl disk = gson.fromJson(reader, DocumentImpl.class);
        System.out.println("\n"+disk); //may not work b/c DocumentImpl needs a toString()

    }

    @Test
    public void printBaseDir() {

        System.out.println(System.getProperty("user.dir"));

    }

   /* @Test
    public void serializeTest() throws URISyntaxException, IOException {

        URI uri= new URI("yellow/src/main/java/edu/yu/cs/com1320/project/hello");
        String txt = "hello world generic text";
        HashMap<String, Integer> wordCountMap = new HashMap<>();

        DocumentImpl doc = new DocumentImpl(uri,txt,wordCountMap);
        HashMap<String, String> map = new HashMap<>();
        map.put("hello","sir");
        map.put("I'm", "Eitan");
        map.put("Good","day");
        doc.setMetadata(map);

        File file = new File("C:\\Users\\marko\\Desktop\\CompSci\\PersonalRepo\\Markovitz_Andrew_800759408\\DataStructures\\project\\stage6\\src\\");
        DocumentPersistenceManager<URI, DocumentImpl> documentDocumentPersistenceManager = new DocumentPersistenceManager<>(file);

        documentDocumentPersistenceManager.serialize(uri,doc);
        Document x = documentDocumentPersistenceManager.deserialize(uri);


        System.out.println(x);
        //DEBUGGING deserialized Document x:
        //      Doesn't have the desired contents, metadata, or wordcountmap
        //      -only uri
        //  Check DocSerizalize.deserialize() to fix

        System.out.println(doc);
        assertEquals(x.getWordMap(),doc.getWordMap());
        assertEquals(x.getMetadata(),doc.getMetadata());
        assertEquals(x.getKey(),doc.getKey());
        assertEquals(x.getDocumentTxt(),doc.getDocumentTxt());
        assertEquals(x,doc);

    }*/


    @Test
    void binaryDocSerialize() throws IOException, URISyntaxException {

        URI uri= new URI("yellow/src/main/java/edu/yu/cs/com1320/project/hello");
        //String txt = "hello world generic text";
        String doc1Words= "fred first";
        byte[] byties = doc1Words.getBytes();
        //InputStream one = new ByteArrayInputStream(byties);

        //HashMap<String, Integer> wordCountMap = new HashMap<>();

        DocumentImpl doc = new DocumentImpl(uri,byties);
        HashMap<String, String> map = new HashMap<>();
        map.put("hello","sir");
        map.put("I'm", "Eitan");
        map.put("Good","day");
        doc.setMetadata(map);

        File file = new File("C:\\Users\\marko\\Desktop\\CompSci\\PersonalRepo\\Markovitz_Andrew_800759408\\DataStructures\\project\\stage6\\src\\");
        DocumentPersistenceManager<URI, DocumentImpl> documentDocumentPersistenceManager = new DocumentPersistenceManager<>(file);

        documentDocumentPersistenceManager.serialize(uri,doc);
        Document x = documentDocumentPersistenceManager.deserialize(uri);


        //System.out.println(x);
        //DEBUGGING deserialized Document x:
        //      Doesn't have the desired contents, metadata, or wordcountmap
        //      -only uri
        //  Check DocSerizalize.deserialize() to fix

        System.out.println(doc);

        //assertEquals(x,doc);
        //System.out.println(documentDocumentPersistenceManager.serializedDocs);
        boolean b = documentDocumentPersistenceManager.delete(uri);

        //System.out.println(b);

    }

    @Test
    public void twoDocs() throws IOException, URISyntaxException {

        URI uri= new URI("yellow/src/main/java/edu/yu/cs/com1320/project/hello");
        //String txt = "hello world generic text";
        String doc1Words= "fred first";
        byte[] byties = doc1Words.getBytes();
        //InputStream one = new ByteArrayInputStream(byties);

        //HashMap<String, Integer> wordCountMap = new HashMap<>();

        DocumentImpl doc = new DocumentImpl(uri,byties);
        HashMap<String, String> map = new HashMap<>();
        map.put("hello","sir");
        map.put("I'm", "Eitan");
        map.put("Good","day");
        doc.setMetadata(map);

        File file = new File("C:\\Users\\marko\\Desktop\\CompSci\\PersonalRepo\\Markovitz_Andrew_800759408\\DataStructures\\project\\stage6\\src\\");
        DocumentPersistenceManager<URI, DocumentImpl> documentDocumentPersistenceManager = new DocumentPersistenceManager<>(file);

        documentDocumentPersistenceManager.serialize(uri,doc);
        Document x = documentDocumentPersistenceManager.deserialize(uri);


        //System.out.println(doc);

        //assertEquals(x,doc);
        //System.out.println(documentDocumentPersistenceManager.serializedDocs);


        URI uri2= new URI("yellow/sr/main/java/edu/yu/cs/com1320/project/yo");
        String txt = "hello bob generic text";
        HashMap<String, Integer> wordCountMap = new HashMap<>();

        DocumentImpl doc2 = new DocumentImpl(uri2,txt,wordCountMap);
        HashMap<String, String> map2 = new HashMap<>();
        map.put("yo","man");
        map.put("this", "Andrew");
        map.put("groovy","mornin");
        doc.setMetadata(map);

        documentDocumentPersistenceManager.serialize(uri2,doc2);
        Document y = documentDocumentPersistenceManager.deserialize(uri2);







 //       boolean b = documentDocumentPersistenceManager.delete(uri);
 //       boolean f = documentDocumentPersistenceManager.delete(uri2);

        //System.out.println(b);

    }

    @Test
    public void wordCountTest() throws URISyntaxException, IOException {
        //URI uri= new URI("http://yellow/src/main/java/edu/yu/cs/com1320/project/hello");
        URI uri= new URI("http://edu.yu.cs/com1320/project/doc1"); //"http://yellow/Eitan is in/ houston"
      //  URI uri= new URI("edu.yu.cs/com1320/project/doc1");
        String txt = "hello world generic text";
        DocumentImpl doc = new DocumentImpl(uri,txt,null);

        HashMap metadata = new HashMap<String,String>();
        metadata.put("first","word1");
        metadata.put("second","word2");
        doc.setMetadata(metadata);

        File file = new File("C:\\Users\\marko\\Desktop\\CompSci\\PersonalRepo\\Markovitz_Andrew_800759408\\DataStructures\\project\\stage6\\src\\");
        DocumentPersistenceManager<URI, DocumentImpl> documentDocumentPersistenceManager = new DocumentPersistenceManager<>(file);

        documentDocumentPersistenceManager.serialize(uri,doc);
        DocumentImpl nuDoc = documentDocumentPersistenceManager.deserialize(uri);
        assertEquals(doc.getWords(),nuDoc.getWords());
        assertEquals(doc.getKey(),nuDoc.getKey());
        assertEquals(doc.getDocumentTxt(),nuDoc.getDocumentTxt());
        assertEquals(doc.getMetadata(),nuDoc.getMetadata());


        documentDocumentPersistenceManager.delete(uri);

        URI uri2= new URI("edu.yu.cs/com1320");
        String txt2 = "hello world unique text";
        DocumentImpl doc2 = new DocumentImpl(uri2,txt2,null);
    //    documentDocumentPersistenceManager.serialize(uri2,doc2);


      //  documentDocumentPersistenceManager.delete(uri);
      //  documentDocumentPersistenceManager.delete(uri2);

        //HashMap<String, Integer> wordCountMap = new HashMap<>();

    }
/*
    @Test
    public void randomDir() throws URISyntaxException, IOException {

        //Should throw an exception:

        URI uri= new URI("yellow/src/main/java/edu/yu/cs/com1320/project/hello");
        String txt = "hello world generic text";
        DocumentImpl doc = new DocumentImpl(uri,txt,null);

        File file = new File("Hello\\World\\Eitan\\");
        DocumentPersistenceManager<URI, DocumentImpl> documentDocumentPersistenceManager = new DocumentPersistenceManager<>(file);

        //documentDocumentPersistenceManager.serialize(uri,doc);
        //DocumentImpl nuDoc = documentDocumentPersistenceManager.deserialize(uri);

        //documentDocumentPersistenceManager.delete(uri);
    }*/

    @Test
    public void deserializeNonexistentPath() throws URISyntaxException, IOException {

        URI uri= new URI("yellow/src/main/java/edu/yu/cs/com1320/project/hello");
        String txt = "hello world generic text";
        DocumentImpl doc = new DocumentImpl(uri,txt,null);

        File file = new File("C:\\Users\\marko\\Desktop\\CompSci\\PersonalRepo\\Markovitz_Andrew_800759408\\DataStructures\\project\\stage6\\src\\");
        DocumentPersistenceManager<URI, DocumentImpl> documentDocumentPersistenceManager = new DocumentPersistenceManager<>(file);

        //documentDocumentPersistenceManager.serialize(uri,doc);
        DocumentImpl nuDoc = documentDocumentPersistenceManager.deserialize(uri);
        System.out.println(nuDoc);

    }

    @Test
    public void printDefBaseDir() {

        String defaultDir = System.getProperty("user.dir");
        System.out.println(defaultDir);

    }

    @Test
    public void checkDirExists() {

        String directoryPath = System.getProperty("user.dir");

        File directory = new File(directoryPath);

        if (directory.exists() && directory.isDirectory()) {
            System.out.println("Directory exists.");
        } else {
            System.out.println("Directory does not exist or is not a directory.");
        }

    }

    @Test
    public void trimUri() throws URISyntaxException {

        URI uriVal= new URI("src/main/java/edu/yu/cs/com1320/project/world");
        String uri = uriVal.toString();

        int slash = uri.lastIndexOf("/");
        String checkUri = uri.substring(0,slash);
        System.out.println(checkUri);

    }

    @Test
    public void createDir() throws URISyntaxException {

        // Specify the path for the new folder
        URI uri= new URI("hello/big/world");
        String folderPath = System.getProperty("user.dir")+"/"+uri;

        // Create a File object representing the new folder
        File folder = new File(folderPath);

        // Use mkdir() to create the directory, returns true if successful, false otherwise
        boolean folderCreated = folder.mkdir();

        if (folderCreated) {
            System.out.println("Folder created successfully.");
        } else {
            System.out.println("Failed to create folder.");
        }

    }

    @Test
    public void serializeNullBaseDir() throws IOException, URISyntaxException {

        URI uri= new URI("src/main/java/edu/yu/cs/com1320/project/hi/my/name/is/slim/shady/yo");
        String txt = "hello world generic text";
        HashMap<String, Integer> wordCountMap = new HashMap<>();

        DocumentImpl doc = new DocumentImpl(uri,txt,wordCountMap);
        HashMap<String, String> map = new HashMap<>();
        map.put("hello","sir");
        map.put("I'm", "Eitan");
        map.put("Good","day");
        doc.setMetadata(map);

        File file = new File("C:\\Users\\marko\\Desktop\\CompSci\\PersonalRepo\\Markovitz_Andrew_800759408\\DataStructures\\");
        DocumentPersistenceManager<URI, DocumentImpl> documentDocumentPersistenceManager = new DocumentPersistenceManager<>(null);

        documentDocumentPersistenceManager.serialize(uri,doc);
        Document x = documentDocumentPersistenceManager.deserialize(uri);


        System.out.println(x);
        //DEBUGGING deserialized Document x:
        //      Doesn't have the desired contents, metadata, or wordcountmap
        //      -only uri
        //  Check DocSerizalize.deserialize() to fix

        System.out.println(doc);

        //assertEquals(x,doc);

    }

    //TEST FOR DELETE



    //PUTTING THIS HERE FOR NOW:


/*
    //LET'S ASSUME: key is the URI

    String baseDir;
        if (this.dir != null) {
        baseDir = this.dir.toString();
    } else {
        baseDir = System.getProperty("user.dir");
    }

    String uri = key.toString();

    //CHECK IF DIRECTORY EXISTS

    boolean existsSlash = true;
    String addToBaseDir=baseDir;
    String uriCopy = uri;
    int slash=0;
    String checkUri;
        while (existsSlash) { //while there's a level I haven't created

        if (uriCopy.contains("/")) {
            slash = uriCopy.indexOf("/");
            checkUri = uriCopy.substring(0, slash); //take first level
        } else {
            break;
            //checkUri = uriCopy;
            //existsSlash = false;
        }

        String fullPath = addToBaseDir + "/" + checkUri;

        //every run of the loop:
        //take a level of the path
        //It doesn't exist, so build it
        //add it to path we know variable


        File directory = new File(fullPath);

        if (!directory.exists() || !directory.isDirectory()) { //directory doesn't exist- must be created
            // Create a File object representing the new folder
            //File folder = new File(folderPath);

            // Use mkdir() to create the directory, returns true if successful, false otherwise
            boolean folderCreated = directory.mkdir();

                *//*if (folderCreated) {
                    System.out.println("Folder created successfully.");
                } else {
                    System.out.println("Failed to create folder.");
                }*//*

        }
        //update uri
        addToBaseDir = fullPath;
        try {
            uriCopy = uriCopy.substring(slash+1);
        } catch (StringIndexOutOfBoundsException e) {
            uriCopy="";
        }
        //check if there's another level of the directory- exists slash
    }

        */
}
