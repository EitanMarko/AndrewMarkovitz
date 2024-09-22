package edu.yu.cs.com1320.project.stage6.impl;
import com.google.gson.*;
import edu.yu.cs.com1320.project.stage6.*;
import edu.yu.cs.com1320.project.stage6.PersistenceManager;
import javax.xml.bind.DatatypeConverter;

import javax.print.Doc;
import java.io.*;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class DocumentPersistenceManager<Key,Value> implements
        PersistenceManager<Key,Value>{

    private File dir;
    private HashMap<Key, Value> serializedDocs;
    public DocumentPersistenceManager(
            File dir){

    this.dir= dir;
    this.serializedDocs = new HashMap<>();

    }

    private class DocSerializer implements com.google.gson.JsonSerializer<Document>{

        private String txt; //elements???

        @Override
        public JsonElement serialize(Document document, Type type, JsonSerializationContext jsonSerializationContext) {

            JsonObject jsonObject = new JsonObject();

            //ADD CONTENTS
            if(document.getDocumentBinaryData()!=null){ //binary doc
                //jsonObject.addProperty("contents",document.getDocumentBinaryData().toString());
                //String byteContents = Base64.getEncoder().encodeToString(document.getDocumentBinaryData());
                String byteContents = DatatypeConverter.printBase64Binary(document.getDocumentBinaryData());
                //System.out.println(byteContents);


                //JsonArray contentBytes = new JsonArray();
                //contentBytes.add(byteContents);
                jsonObject.addProperty("binaryContents",byteContents);
            }
            else{ //txt doc
                jsonObject.addProperty("contents",document.getDocumentTxt());
            }

            //ADD METADATA
            //jsonObject.addProperty("metadata",document.getMetadata().toString());
            JsonObject metadata = new JsonObject();
            //System.out.println(document.getMetadata().entrySet());
            for(Map.Entry<String,String> entry : document.getMetadata().entrySet()){
                metadata.addProperty(entry.getKey(),entry.getValue());
                //System.out.println(entry.getKey());
                //System.out.println(entry.getValue()+"\n");
            }
            jsonObject.add("metadata",metadata);
//            System.out.println(jsonObject);
            //ADD URI
            jsonObject.addProperty("uri",document.getKey().toString());

            //ADD WORD COUNT- ONLY IF ITS A TXT DOC???
            //jsonObject.addProperty("wordCountMap",document.getWordMap().toString());
            JsonObject wordCount = new JsonObject();
            if (document.getWordMap()!=null) { //TXT doc
                for(Map.Entry<String,Integer> entry : document.getWordMap().entrySet()){
                    wordCount.addProperty(entry.getKey(),entry.getValue());
                }
            }
            jsonObject.add("wordCount",wordCount);

            //get specific things from the document, and only use those elements
            //JsonPrimitive only serializes an element at a time
            //We want 4 elements
            //How can this method return 4 elements?
            //Should I be able to call this method specific to the element I want

            //System.out.println(jsonObject);
            return jsonObject;
        }
    }

    private class DocDeserializer implements com.google.gson.JsonDeserializer<Document>{

        @Override
        public Document deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
            boolean binaryDoc= true;
            JsonObject jsonObject = jsonElement.getAsJsonObject();
            //System.out.println(jsonObject);
            //deserialize contents
            //System.out.println(jsonObject.get("contents"));
            JsonElement contentElement = jsonObject.get("contents");
            //byte[] v= Base64.getDecoder().decode(String.valueOf(contentElement));
            //byte[] v = DatatypeConverter.parseBase64Binary(jsonObject.get("contents").toString());
            //System.out.println(v);
            byte[] contentsBytes = null;
            String contentsString = "";

            if(jsonObject.get("binaryContents")!=null){ //binary doc
                contentsBytes = DatatypeConverter.parseBase64Binary(jsonObject.get("binaryContents").toString());
            }
            else{ //TXT doc
                contentsString = jsonObject.get("contents").getAsString();
                binaryDoc = false;
            }

            //deserialize metadata
            HashMap<String,String> metadata = new HashMap<>();
            JsonObject metadataJson = jsonObject.getAsJsonObject("metadata");
            try {
                for(Map.Entry<String,JsonElement> entry : metadataJson.entrySet()){
                    metadata.put(entry.getKey(), entry.getValue().getAsString());
                }
            } catch (NullPointerException e) {
                System.out.println("NO METADATA!!!");
            }

            //deserialize uri
            String uriString = jsonObject.get("uri").getAsString();
            URI uri;
            try {
                uri = new URI(uriString);
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }

            //deserialize wordCount - ONLY IF ITS A TXT DOC???
            HashMap<String,Integer> wordCount = new HashMap<>();
            JsonObject wordCountJson = jsonObject.getAsJsonObject("wordCount");
            if (wordCountJson!=null) {
                for(Map.Entry<String,JsonElement> entry : wordCountJson.entrySet()){
                    wordCount.put(entry.getKey(), entry.getValue().getAsInt());
                }
            }

            // if binaryDoc/ else...
            if(binaryDoc){
                DocumentImpl doc = new DocumentImpl(uri,contentsBytes);
                System.out.println(contentsBytes);
                doc.setMetadata(metadata);
                return doc;
            }
            //else, TXT doc...
            DocumentImpl doc = new DocumentImpl(uri,contentsString,wordCount);
            doc.setMetadata(metadata);
            //System.out.println("Hitting here?");
            return doc;


        }
    }

    //Gson gson = new Gson();
    //don't do this^^^
    //use the class from specs

    /*public void hello() throws URISyntaxException, IOException {
        URI uri= new URI("foo://exale.com:8042/oer/tre?nm=fet#nos");
        HashMap<String, Integer> map = new HashMap<>();
        DocumentImpl doc = new DocumentImpl(uri,"Hello",map);

        //Convert to Json
        String myJson =  gson.toJson(doc);
        //System.out.println(myJson);

        //Write to a file
        FileWriter writer = new FileWriter("doc.json");
        gson.toJson(doc,writer);
        writer.close(); //close file

        //Read from file
        FileReader reader = new FileReader("doc.json");
        DocumentImpl disk = gson.fromJson(reader, DocumentImpl.class);
        System.out.println(disk); //may not work b/c DocumentImpl needs a toString() method
    }*/

    //You must serialize/deserialize:
    //1. the contents of the document (String or binary)
    //2. any metadata key-value pairs
    //3. the URI
    //4. the wordcount map. You may not recalculate the word map when bring a document into memory from disk; it must be
    //stored on disk and loaded from disk.

    private String separator(String path){
        String pathToReturn=path;
        for(int i=0; i<path.length();i++){
            char targetChar='\\';
            char targetChar2 ='/';
            if(path.charAt(i)==targetChar || path.charAt(i)==targetChar2){
                pathToReturn=pathToReturn.substring(0,i)+File.separator+path.substring(i+1);
            }
        }
        return pathToReturn;
    }
    @Override
    public void serialize(Key key, Value val) throws IOException {

        //create new serializer object with that Document
        //call its serialize() method

        Gson gson = new GsonBuilder().registerTypeAdapter(DocumentImpl.class, new DocSerializer()).create();
        /*GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(Document.class, new DocDeserializer());*/


        String jsonElement = gson.toJson(val); //IS THIS RIGHT??? (val)
        //check it^^^
        //OR

      /*  Gson gson2 = gson.create();
        String jsonString = gson2.toJson(val);*/


        //LET'S ASSUME: key is the URI

        String baseDir;
        if (this.dir != null) {
            baseDir = this.dir.toString();
        } else {
            baseDir = System.getProperty("user.dir");
        }

        String uri = key.toString();

        String uriCopy = uri;
        char targetChar ='/';
        for (int i = 0; i < uriCopy.length(); i++) {
            char currentChar = uriCopy.charAt(i);
            if (currentChar==targetChar){
                String before= uriCopy.substring(0,i);
                String after= uriCopy.substring(i+1);
                uriCopy=before+"\\"+after;
            }
        }

        String uriBegin ="http:\\\\";
        if(uriCopy.substring(0,7).equals(uriBegin)){
            uriCopy=uriCopy.substring(7);
        }

        //CHECK IF DIRECTORY EXISTS

        boolean existsSlash = true;
        String addToBaseDir="";

        int slash=0;
        String fullDir = baseDir+ File.separator + uriCopy;//String fullDir = baseDir+ "\\" + uriCopy;
        fullDir=separator(fullDir);
        String saveFullDir= fullDir; //FOR TESTING PURPOSES ONLY
        String checkBaseDir;
        boolean runOnce=false;
        boolean runOnce2 =false;
        String fullPath="";
        while (existsSlash) { //while there's a level I haven't created

            if (fullDir.contains(File.separator)) {
                slash = fullDir.indexOf(File.separator);
                checkBaseDir = fullDir.substring(0, slash); //take first level
                if(!runOnce){
                    addToBaseDir=checkBaseDir;
                    runOnce=true;
                }
            } else {
                break;
                //checkUri = uriCopy;
                //existsSlash = false;
            }

            if(!runOnce2){
                fullPath = addToBaseDir;
                runOnce2=true;
            }else{
                fullPath = addToBaseDir + File.separator + checkBaseDir;
            }


            //every run of the loop:
            //take a level of the path
            //It doesn't exist, so build it
            //add it to path we know variable


            File directory = new File(fullPath);

            if (!directory.exists() || !directory.isDirectory()) { //directory doesn't exist- must be created
                // Create a File object representing the new folder
                //File folder = new File(folderPath);

                // Use mkdir() to create the directory, returns true if successful, false otherwise
                boolean folderCreated = directory.mkdir(); //changed from mkdir to mkdirs

                /*if (folderCreated) {
                    System.out.println("Folder created successfully.");
                } else {
                    System.out.println("Failed to create folder.");
                }*/

            }
            //update uri
            addToBaseDir = fullPath;
            try {
                fullDir = fullDir.substring(slash+1);
            } catch (StringIndexOutOfBoundsException e) {
                fullDir="";
            }
            //check if there's another level of the directory- exists slash
        }


            //Write to a file:
            String finalDir=addToBaseDir+File.separator+fullDir;
            //FileWriter writer = new FileWriter(finalDir+ ".json");
            Path filePath= Path.of(finalDir + ".json");
            //FileWriter writer = new FileWriter(baseDir + File.separator + uriCopy + ".json");
            Path x= Files.write(filePath, jsonElement.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            //writer.append(jsonElement);
            //writer.close();
            //System.out.println(finalDir);



            /*

            //System.out.print(jsonElement);
            //writer.write(jsonElement);
            //writeToFile("ccc.json")
            //gson.toJson(doc,writer);
            File ccc= new File(addToBaseDir+File.separator+fullDir+ ".json");


            if(ccc.exists()){
                System.out.println("File existed before!");
            }
            //writer.close(); //close file
            if(!ccc.exists()){
                System.out.println("File has been destroyed by close method!");
            }
            System.out.println(addToBaseDir+File.separator+fullDir+ ".json");

            System.out.println(jsonElement);

            */



        //add to Map of serialized docs
        this.serializedDocs.put(key, val);


    }
    @Override
    public Value deserialize(Key key) throws IOException {

        /*GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(Document.class, new DocDeserializer());*/
        Gson gsonSer = new GsonBuilder().registerTypeAdapter(DocumentImpl.class, new DocSerializer()).create();
        Gson gsonDeSer = new GsonBuilder().registerTypeAdapter(DocumentImpl.class, new DocDeserializer()).create();



        //else- doc is on disk
        Value doc = this.serializedDocs.get(key);
        //System.out.println(doc.getClass());
        String jsonElement = gsonSer.toJson(doc); //IS THIS RIGHT??? (previously tried 'val') // It's 'val', not 'key' that's used in the above method...
        //System.out.println(jsonElement);                                                                    //key should be a URI...
                                                    //check it^^^

        /*JsonElement jsonElement = gson1.create().toJsonTree(val);*/ // It's 'val', not 'key' that's used in the above method...
                                                                 //key should be a URI...
        //Get deserialized version:
        //Gson gson = gsonBuilder.create();
        DocumentImpl docToReturn = gsonDeSer.fromJson(jsonElement, DocumentImpl.class);

        return (Value) docToReturn;
    }

    @Override
    public boolean delete(Key key) throws IOException {
        //if a Deserializer has the URI (not null), delete and return true
        //else, return false



        Gson gson = new GsonBuilder().registerTypeAdapter(DocumentImpl.class, new DocSerializer()).create();

        if(!this.serializedDocs.containsKey(key)){
            return false; //Piazza @519
        }
        //else- doc is on disk
        Value doc = this.serializedDocs.get(key);
        String jsonElement = gson.toJson(doc);


        String baseDir= this.dir.toString();
        String uri = key.toString();
        String uriBegin ="http://";
        if (uri.startsWith(uriBegin)) {
            uri=uri.substring(7);
        }
        uri=separator(uri); //make sure uri only has File.separator in it
        String fileName = uri+".json";
        //String path = baseDir+"/"+uri+".json";
        fileName=separator(fileName);
        File fileToDelete = new File(this.dir,fileName);
        String absPath=fileToDelete.getAbsolutePath();
        Path filePath = Paths.get(absPath);

        //Files.delete(filePath);
        /*try {
            //System.out.println("In place of delete");
            Files.deleteIfExists(filePath);

            *//*if (Files.deleteIfExists(filePath)) {
                System.out.println("File deleted successfully.");
            } else {
                System.out.println("File does not exist or could not be deleted.");
            }*//*
        } catch (IOException e) {
            e.printStackTrace();
        }*/
        boolean exists= fileToDelete.exists();
        boolean b = fileToDelete.delete();
     //   boolean b= false;
        //System.out.println(b);

        int slash= uri.lastIndexOf(File.separator);
        String path = baseDir+File.separator+uri.substring(0,slash);
        path=separator(path);
        File folder = new File(path);
        deletePath(folder, path);

        this.serializedDocs.remove(key);

        return b;
        //return true;
        //method in File to delete!!!!!!!!!!!!!!!!!
    }


    private void deletePath(File dir, String path){
        String thePath = path;

        //String uriCopy = uri;

        //COMMENTED THIS OUT BECAUSE NOW THE WHOLE PATH SHOULD COME IN WITH ONLY FILE.SEPARATOR IN BETWEEN LEVELS
        /*char targetChar ='/';
        for (int i = 0; i < thePath.length(); i++) {
            char currentChar = thePath.charAt(i);
            if (currentChar==targetChar){
                String before= thePath.substring(0,i);
                String after= thePath.substring(i+1);
                thePath=before+File.separator+after;
            }
        }*/

        File folder = dir; //starts at dir
        File[] listFiles = folder.listFiles();
        if(listFiles==null){
            return;
        }
        while(folder.listFiles().length==0){
            folder.delete();
            int slash= thePath.lastIndexOf(File.separator);
            //int slash= thePath.lastIndexOf("\\");

            //Need the slashes to be consistent so you can search thru5

            thePath=thePath.substring(0,slash);
            folder= new File(thePath); //upper file directory
            if(folder.listFiles()==null){
                return;
            }
        }

    }
}
