package edu.yu.cs.com1320.project.stage6.impl;
import edu.yu.cs.com1320.project.stage6.*;
//import edu.yu.cs.com1320.project.HashTable;  //No longer in use
//import edu.yu.cs.com1320.project.impl.HashTableImpl; //No longer in use


import java.net.URI;
import java.util.*;



public class DocumentImpl implements Document, Comparable<Document>  {

    private HashMap<String, String> meta;
    private URI uri;
    private String txt;
    private byte[] binaryData;
    private DocumentStore.DocumentFormat type;

    private HashMap<String, Integer> wordCount;
    private long lastUsedTime;
    private int memory;
    private boolean isBinaryDoc;


    public DocumentImpl(URI uri, String txt, Map<String, Integer> wordCountMap)
    {
        if(uri==null || uri.toString().isEmpty())
        {
            throw new IllegalArgumentException();
        }
        if(txt==null || txt.isEmpty())
        {
            throw new IllegalArgumentException();
        }

        this.txt=txt;


        //if wordCountMap == null || isEmpty- create the map
        //else- make it what was passed in

        if(wordCountMap == null || wordCountMap.isEmpty()){ //empty in the case the Document is created but not by the DocumentStore's put() method
            //this.wordCount = (HashMap<String, Integer>) wordCountMap;

            this.wordCount = new HashMap<>(); //maps words in doc to amount of times it appears

            Set<String> wordsList= this.getWords();
            for(String word : wordsList){
                this.wordCount.put(word,wordCount(word));
            }
        }
        else { // !null - this doc was just deserialized

            this.wordCount = (HashMap<String, Integer>) wordCountMap;

            /*this.wordCount = new HashMap<>(); //maps words in doc to amount of times it appears

            Set<String> wordsList= this.getWords();
            for(String word : wordsList){
                this.wordCount.put(word,wordCount(word));
            }*/
        }
        this.uri=uri;
        //this.txt=txt;
        this.meta= new HashMap<>();
        this.lastUsedTime=System.nanoTime();
        this.isBinaryDoc=false;


    }

    public DocumentImpl(URI uri, byte[] binaryData)
    {
        if(uri==null || uri.toString().isEmpty())
        {
            throw new IllegalArgumentException();
        }
        if(binaryData==null || binaryData.length==0)
        {
            throw new IllegalArgumentException();
        }
        this.uri=uri;
        this.binaryData=binaryData;
        //this.metadata= new HashTableImpl<>();
        this.meta = new HashMap<>();
        this.lastUsedTime=System.nanoTime();
        this.memory= binaryData.length;
        this.isBinaryDoc=true;
    }


    /**
     * @param key   key of document metadata to store a value for
     * @param value value to store
     * @return old value, or null if there was no old value
     * @throws IllegalArgumentException if the key is null or blank
     */
    @Override
    public String setMetadataValue(String key, String value)
    {
        if(key==null || key.isEmpty())
        {
            throw new IllegalArgumentException();
        }
        return this.meta.put(key,value); //make sure this actually "puts"

        /*if(this.metadata.get(key)==null) //no old value
        {
            this.metadata.put(key,value);
            return null;
        }
        //only reaches here if there IS an old value
        String old=this.metadata.get(key);
        this.metadata.replace(key,value);
        return old;*/
    }

    /**
     * @param key metadata key whose value we want to retrieve
     * @return corresponding value, or null if there is no such key
     * @throws IllegalArgumentException if the key is null or blank
     */
    @Override
    public String getMetadataValue(String key) {
        if(key==null || key.isEmpty())
        {
            throw new IllegalArgumentException();
        }
        if(!this.meta.containsKey(key)) //key does NOT exist //key exists
        {
            return null;
        }
        //only get here if key does NOT exist
        return this.meta.get(key);//key exists
    }

    /**
     * @return a COPY of the metadata saved in this document
     */
    @Override
    public HashMap<String, String> getMetadata() {

        //get all the keys
        //use the get() method to get their values
        //put them in a new HashTable
        HashMap<String,String> q= new HashMap<>();
        Set<String> allTheKeys= this.meta.keySet();

        List<String> keysList = new ArrayList<>(allTheKeys);
        for(String s: keysList)
        {
            String theVal = this.meta.get(s);
            q.put(s,theVal);
        }

        return q;
        //return a COPY
        //maybe check Account.java in Ass. 7 for ref on unmodifiable list (if applicable)
    }

    @Override
    public void setMetadata(HashMap<String, String> metadata) {

        for(String i: metadata.keySet()){
            this.meta.put(i,metadata.get(i));
        }

    }

    /**
     * @return content of text document
     */
    @Override
    public String getDocumentTxt() {
        return this.txt;
    }

    /**
     * @return content of binary data document
     */
    @Override
    public byte[] getDocumentBinaryData() {
        return this.binaryData;
    }

    /**
     * @return URI which uniquely identifies this document
     */
    @Override
    public URI getKey() {
        return this.uri;
    }

    private List<String> wordsList(){ //returns list of words... includes repeats

        String docTxt= this.getDocumentTxt();
        String trimmedDocTxt= docTxt.trim();
        String lettersAndNums="";
        char[] docChars= trimmedDocTxt.toCharArray();
        for (char ch : docChars){
            if(Character.isAlphabetic(ch) || Character.isDigit(ch) || Character.isWhitespace(ch)){
                lettersAndNums= lettersAndNums + ch;
            }
        }
        String[] emptyWords= lettersAndNums.split(" ");
        List<String> words = new ArrayList<>();
        for( String i : emptyWords){
            if(!i.isEmpty()){
                words.add(i);
            }
        }

        return words;
    }

    @Override
    public int wordCount(String word) {

        if(this.isBinaryDoc){ //Its a BINARY doc- A TXT doc has a null binaryData[]
            return 0;
        }

        List<String> words = this.wordsList();
        int wordCount=0;
        for(String x: words){
            if(x.equals(word)){
                wordCount++;
            }
        }

        return wordCount;
    }

    @Override
    public Set<String> getWords() {

        Set<String> emptySet = new HashSet<>();

        if(this.isBinaryDoc){ //Its a BINARY doc- A TXT doc has a null binaryData[]
            return emptySet; //make sure this is right!!!
        }

        Set<String> words = new HashSet<>(this.wordsList());

        return words;
    }


    @Override //GIVEN BY JUDAH
    public int hashCode()
    {
        int result=uri.hashCode();
        result=31*result+(txt!=null?txt.hashCode():0); //this.txt?????????????????????
        result=31*result+Arrays.hashCode(this.binaryData);
        return Math.abs(result);
    }

    @Override //MADE MYSELF
    public boolean equals(Object obj)
    {
        if(this==obj) //see if it's the same object
        {
            return true;
        }
        if(obj==null) //see if it's null
        {
            return false;
        }
        if(getClass()!=obj.getClass()) //see if they're from the same class
        {
            return false;
        }
        DocumentImpl otherDoc= (DocumentImpl)obj;
        return this.getKey()==otherDoc.getKey();
    }


    @Override
    public long getLastUseTime() {
        return this.lastUsedTime;
    }

    @Override
    public void setLastUseTime(long timeInNanoseconds) {
        this.lastUsedTime=timeInNanoseconds;
    }

    /**
     * @return a copy of the word to count map so it can be serialized
     */
    @Override
    public HashMap<String, Integer> getWordMap() {
        return this.wordCount;
    }

    /**
     * This must set the word to count map durlng deserialization
     *
     * @param wordMap
     */
    @Override
    public void setWordMap(HashMap<String, Integer> wordMap) {
         this.wordCount = wordMap;
    }

    @Override
    public int compareTo(Document o) {
        return Long.compare(this.getLastUseTime(),o.getLastUseTime());
    }
}
