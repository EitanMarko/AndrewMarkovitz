package edu.yu.cs.com1320.project.stage6.impl;
import edu.yu.cs.com1320.project.BTree;
import edu.yu.cs.com1320.project.Stack;
//import edu.yu.cs.com1320.project.impl.HashTableImpl; //No longer in use
import edu.yu.cs.com1320.project.impl.BTreeImpl;
import edu.yu.cs.com1320.project.impl.MinHeapImpl;
import edu.yu.cs.com1320.project.impl.StackImpl;
import edu.yu.cs.com1320.project.impl.TrieImpl;
import edu.yu.cs.com1320.project.stage6.*;
//import edu.yu.cs.com1320.project.undo.Command;   //No longer in use
import edu.yu.cs.com1320.project.undo.CommandSet;
import edu.yu.cs.com1320.project.undo.GenericCommand;
import edu.yu.cs.com1320.project.undo.Undoable;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class DocumentStoreImpl implements DocumentStore {
    private BTreeImpl<URI, DocumentImpl> doc; //stores documents
    private StackImpl<Undoable> commandStack;//= new StackImpl<>();
    private TrieImpl<BTreeAccessor> docsTrie;
    private MinHeapImpl<BTreeAccessor> minHeap;
    private int maxDocumentCount;
    private boolean maxDocCountHasBeenSet;
    private int maxDocumentBytes;
    private boolean maxDocBytesHaveBeenSet;
    private DocumentPersistenceManager<URI,DocumentImpl> docPersManager;
    private DocumentImpl sentinel;
    private URI sentienlUri;
    private HashSet<URI> btreeEntries;
    private HashSet<BTreeAccessor> allAccessors;
    private StackImpl<DocumentImpl> diskDocs;
    private HashMap <URI,BTreeAccessor> ultimateMinHeapAccessRecords;
    private HashMap <URI,BTreeAccessor> ultimateTrieAccessRecords;


    public DocumentStoreImpl()  {
        this.doc = new BTreeImpl<>();

        try {
            this.sentienlUri = new URI("http://aaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        this.sentinel = new DocumentImpl(this.sentienlUri,"sentinel",null);
        //this.sentinel.setLastUseTime(0); //long?
        this.doc.put(this.sentienlUri,this.sentinel);

        this.commandStack = new StackImpl<Undoable>();
        this.docsTrie = new TrieImpl();
        this.minHeap= new MinHeapImpl<>();
        this.btreeEntries = new HashSet<>();
        this.allAccessors = new HashSet<>();

        //NEED THIS???
        File folder = new File(System.getProperty("user.dir")); //regular base directory
        this.docPersManager = new DocumentPersistenceManager<>(folder);
        this.doc.setPersistenceManager(this.docPersManager);
        this.diskDocs = new StackImpl<>();
        this.ultimateMinHeapAccessRecords = new HashMap<>();
        this.ultimateTrieAccessRecords = new HashMap<>();
    }

    public DocumentStoreImpl(File baseDir)  {
        this.doc = new BTreeImpl<>();

        try {
            this.sentienlUri = new URI("http://aaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        this.sentinel = new DocumentImpl(this.sentienlUri,"sentinel",null);
        //this.sentinel.setLastUseTime(0); //long?
        this.doc.put(this.sentienlUri,this.sentinel);

        this.commandStack = new StackImpl<Undoable>();
        this.docsTrie = new TrieImpl<>();
        this.minHeap= new MinHeapImpl<>();
        this.docPersManager = new DocumentPersistenceManager(baseDir); //put a file directory here. Where are the documents stored?

        this.doc.setPersistenceManager(this.docPersManager);
        this.btreeEntries = new HashSet<>();
        this.allAccessors = new HashSet<>();
        this.diskDocs = new StackImpl<>();
        this.ultimateMinHeapAccessRecords = new HashMap<>();
        this.ultimateTrieAccessRecords = new HashMap<>();
    }


    private class BTreeAccessor implements Comparable<BTreeAccessor> {
        private BTreeImpl<URI, DocumentImpl> btree;
        private URI uri;
        private long lastUseTime;
        private BTreeAccessor(URI uri, BTreeImpl<URI, DocumentImpl> btree){
            this.btree = btree;
            this.uri = uri;
            //this.lastUseTime = get().getLastUseTime();

        }

        public DocumentImpl get(){
            return btree.get(uri);
            //return bTreeGet(btree, uri); //Commented out bc bTreeGet() is just called in all places needed.
                                        //This method facilitates get from actual BTreeAccessor
                                        //bTreeGet() is called when there's no BTreeAccessor to get() from

        }
        public long getLastUseTime(){
            return get().getLastUseTime();
        }

        public void setLastUseTime(long timeInNanoseconds) {
            get().setLastUseTime(System.nanoTime());
        }

        public void setBTree(BTreeImpl<URI,DocumentImpl> btree){
            this.btree = btree;
        }

        @Override
        public int compareTo(BTreeAccessor o) {
            return Long.compare(this.getLastUseTime(),o.getLastUseTime());
        }
    }

    private DocumentImpl bTreeGet(BTreeImpl<URI, DocumentImpl> btree, URI uri, boolean callInPutMethod) {
        if(btree.get(uri)==null){ //Doc was written to disk
            DocumentImpl bringBackDoc = null;
            try { //SEE IF THERE'S A DOC TO DESERIALIZE
                bringBackDoc = docPersManager.deserialize(uri);
            } catch (IOException e) {
                return null ;
            }

            if(bringBackDoc==null){ //No doc to serialize
                return null;
            }

            if (bringBackDoc.getWordMap()!=null) { //TXT DOC

                String bringBackWords= bringBackDoc.getDocumentTxt();         //IF ITS A BINARY DOC: NULL
                byte[] BringBackBytes = bringBackWords.getBytes();              //IF NULL.getBytes() --> NullPointerException
                InputStream input = new ByteArrayInputStream(BringBackBytes);

                if (!callInPutMethod) {
                    try {
                        put(input, uri,DocumentFormat.TXT); //this will take care of checking over limits
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    btree.get(uri).setMetadata(bringBackDoc.getMetadata()); //make sure metadata gets put back
                }
            }
            else{ //BINARY doc

                byte[] BringBackBytes = bringBackDoc.getDocumentBinaryData();             //CHECK THAT THIS IS RIGHT!!!!
                InputStream input = new ByteArrayInputStream(BringBackBytes);

                if (!callInPutMethod) {
                    try {
                        put(input, uri,DocumentFormat.BINARY);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    btree.get(uri).setMetadata(bringBackDoc.getMetadata()); //make sure metadata gets put back
                }
            }


            //MEMORY MANAGEMENT AND SETLASTUSETIME WILL BE HANDLED BY PUT????
            //DELETE FILE FROM DISK NOW THAT IT'S BEEN BROUGHT BACK:
            if (!callInPutMethod) { //We actually brought the document back - 8/7/24
                try {
                    docPersManager.delete(uri);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

        }

        if (btree.get(uri)!=null) {
            btree.get(uri).setLastUseTime(System.nanoTime()); //set time to now
        }
        return btree.get(uri);
    }

    private Set<BTreeAccessor> getMinHeapAccessors(){

        StackImpl stack = new StackImpl<BTreeAccessor>();

        Set<BTreeAccessor> accessors = new HashSet<>();
        while(minHeap.peek()!=null){ //for all minHeapAccessors
            BTreeAccessor removed = minHeap.remove();
            accessors.add(removed);
            stack.push(removed);
        }
        while(stack.size()!=0) { //for all elements of the stack
            //Document hold = (Document) stack.pop(); //pop from stack and hold
            BTreeAccessor hold = (BTreeAccessor) stack.pop();
            minHeap.insert(hold); //put back into minHeap
            minHeap.reHeapify(hold); //reheapify
        }
        return accessors;
    }

    private BTreeAccessor getSpecificMinHeapAccessor(URI uri){
        for(BTreeAccessor accessor : getMinHeapAccessors()){
            if(accessor.get().getKey()==uri){
                return accessor;
            }
        }
        //doesn't exist in minHeap
        return null;
    }

    private Set<BTreeAccessor> getTrieAccessors(){
        Set<String> words = new HashSet<>();
        for(URI uri : btreeEntries){
            if(this.doc.get(uri)!=null){ //confirmed: you would get null here
                words.addAll(this.doc.get(uri).getWords());
            }
        }
        Set<BTreeAccessor> accessors = new HashSet<>();
        for(String str : words){
            accessors.addAll(docsTrie.get(str));
        }

        return accessors;
    }

    private BTreeAccessor getSpecificTrieAccessor(URI uri){

        for(BTreeAccessor accessor : getTrieAccessors()){
            if(accessor.get().getKey()==uri){              //CHANGE THIS 9and all instances of accessor.get()- the get() shuld NOT call BTreeGET() bc we don't want to SOMETHING with these documents!
                return accessor;
            }
        }
        //doesn't exist in Trie
        return null;

    }

    private BTreeAccessor getSpecificAccessor(URI uri){

        for(BTreeAccessor accessor : allAccessors){
            if(accessor.get().getKey()==uri){
                return accessor;
            }
        }
        return null;

    }




    private BTreeAccessor getSpecificDocFromHeap(BTreeAccessor x) throws URISyntaxException {

        URI errorUri = null; //getSpecificDocFromHeap didn't find the doc you were looking for
        BTreeAccessor toReturn = new BTreeAccessor(errorUri, this.doc);
        StackImpl stack = new StackImpl<BTreeAccessor>();
        //TRY-CATCH??? See above
        //for(int i=0; i<doc.size(); i++)
        while(minHeap.peek()!=null){ //for all documents
            if(minHeap.peek().get().getKey()!=x.get().getKey()){ //if the top of the minHeap is NOT what you're looking for

                stack.push(minHeap.remove()); //remove the document and push to temp stack
            }
            else{ //you've found desired document
                toReturn= minHeap.peek();
                stack.push(minHeap.remove()); //remove the document and push to temp stack
                break;
            }
        }
        while(stack.size()!=0) { //for all elements of the stack
            //Document hold = (Document) stack.pop(); //pop from stack and hold
            BTreeAccessor hold = (BTreeAccessor) stack.pop();
            minHeap.insert(hold); //put back into minHeap
            minHeap.reHeapify(hold); //reheapify
        }

        return toReturn;
    }

    /**
     * set the given key-value metadata pair for the document at the given uri
     *
     * @param uri
     * @param key
     * @param value
     * @return the old value, or null if there was no previous value
     * @throws IllegalArgumentException if the uri is null or blank, if there is no document stored at that uri, or if the key is null or blank
     */
    @Override
    public String setMetadata(URI uri, String key, String value) throws IOException{
        if (uri == null || uri.toString().isEmpty()) {
            throw new IllegalArgumentException();
        }
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException();
        }
        /*if (get(uri) == null) //there is no document stored at that uri
        {
            throw new IllegalArgumentException();
        }*/
        boolean onDisk;
        if(doc.get(uri)==null){  //Pulled from disk
            onDisk=true;
        }
        else {             //Already on disk
            onDisk=false;
        }
        DocumentImpl theDoc = bTreeGet(doc,uri,false);   //Brings a document back into memory if it exists

        if(theDoc==null){ //Document never existed on disk or in BTree
            throw new IllegalArgumentException();
        }
        //else... Document existed, and now exists in memory in BTree

        if (theDoc.getMetadataValue(key) == null) //there was no previous value
        {
            theDoc.setMetadataValue(key, value); //setting the metadata

            BTreeAccessor minHeapAccessor = getSpecificMinHeapAccessor(uri);
            GenericCommand newC = new GenericCommand(uri, (u) -> {
                commandStack.pop();
                theDoc.setMetadataValue(key, null); //don't need separate method b/c setMetadataValue doesn't create a new Command
                setNanoAndReHeap(getSpecificMinHeapAccessor(uri)); //set nanoTime and reHeap
                undoBTreeGet(doc,uri,minHeapAccessor,null,onDisk);
            });
            commandStack.push(newC);

            setNanoAndReHeap(getSpecificMinHeapAccessor(uri)); //set nanoTime and reHeap
            return null;
        }
       /* else
        {*/
        String old = theDoc.getMetadataValue(key); //storing old value
        //}
        theDoc.setMetadataValue(key, value); //setting the metadata

        BTreeAccessor minHeapAccessor = getSpecificMinHeapAccessor(uri);
        GenericCommand newC = new GenericCommand(uri, (u) -> {
            commandStack.pop();
            theDoc.setMetadataValue(key, old);
            setNanoAndReHeap(getSpecificMinHeapAccessor(uri)); //set nanoTime and reHeap
            undoBTreeGet(doc,uri,minHeapAccessor,null,onDisk);
        });
        commandStack.push(newC);

        setNanoAndReHeap(getSpecificMinHeapAccessor(uri)); //set nanoTime and reHeap

        return old; //returning the old value
    }

    private void undoBTreeGet(BTreeImpl<URI, DocumentImpl> btree, URI uri,BTreeAccessor accessor, DocumentImpl document, boolean onDisk){

        //IF IT WAS TAKEN FROM DISK:

        //kick back to disk
        //file in memory:
            //lastusetimegoes back to old - does this matter if its not in heap anyway? NOPE!
            //MOVES TO DISK - if this removes from heap, no problem

        if (onDisk) {
            //BTreeAccessor documentAccessor = getSpecificMinHeapAccessor(uri);
            deleteSpecificDocFromHeap(accessor); //BUT THEN WE TRY TO ACCESS IT FROM DOC

//            deleteSpecificDocFromHeap(accessor); //BUT THEN WE TRY TO ACCESS IT FROM DOC


            if (document==null) {
                Set<String> r = accessor.get().getWords();
                for (String h : r) { //for all words in the doc
                    docsTrie.delete(h, getSpecificTrieAccessor(accessor.get().getKey())); //delete the specific doc from each word in the doc in the trie
                }
            }
            else {
                Set<String> r = document.getWords();
                for (String h : r) { //for all words in the doc
                    docsTrie.delete(h, getSpecificTrieAccessor(document.getKey())); //delete the specific doc from each word in the doc in the trie
                }
            }


            if (document==null) {
                docToDisk(accessor); //HERE WE DELETE FROM BTREE
            }
            else{
                docToDisk(document);
                try {
                    docPersManager.delete(document.getKey());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }


            //If the original action kicked an old document out of memory,
            //          put it back in memory, set its lastusetime to its old lastusetime, and reheapify


            //                  :

            DocumentImpl hold = diskDocs.pop();
            DocumentImpl bringBackDoc = diskDocs.pop();
            diskDocs.push(hold);
            Long lastUseTime = bringBackDoc.getLastUseTime();


            if (bringBackDoc.getWordMap()!=null) { //TXT DOC

                String bringBackWords= bringBackDoc.getDocumentTxt();         //IF ITS A BINARY DOC: NULL
                byte[] BringBackBytes = bringBackWords.getBytes();              //IF NULL.getBytes() --> NullPointerException
                InputStream input = new ByteArrayInputStream(BringBackBytes);
                URI bringBackURI = bringBackDoc.getKey();

                try {

                    //MAKE A PRIVATE PUT THAT DOESNT MAKE A NEW COMMAND!!!!!
                    put(input, bringBackURI,DocumentFormat.TXT,"Private Put!"); //this will take care of checking over limits
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                btree.get(bringBackURI).setMetadata(bringBackDoc.getMetadata()); //make sure metadata gets put back
                btree.get(bringBackURI).setLastUseTime(lastUseTime);
                minHeap.reHeapify(getSpecificMinHeapAccessor(bringBackURI));

                /*if (!callInPutMethod) {
                    try {
                        put(input, uri,DocumentFormat.TXT); //this will take care of checking over limits
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    btree.get(uri).setMetadata(bringBackDoc.getMetadata()); //make sure metadata gets put back
                }*/

            }
            else{ //BINARY doc

                byte[] BringBackBytes = bringBackDoc.getDocumentBinaryData();             //CHECK THAT THIS IS RIGHT!!!!
                InputStream input = new ByteArrayInputStream(BringBackBytes);
                URI bringBackURI = bringBackDoc.getKey();


                try {

                    //MAKE A PRIVATE PUT THAT DOESNT MAKE A NEW COMMAND!!!!!
                    put(input, bringBackURI,DocumentFormat.BINARY,"Private Put!");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                btree.get(bringBackURI).setMetadata(bringBackDoc.getMetadata()); //make sure metadata gets put back
                btree.get(bringBackURI).setLastUseTime(lastUseTime);
                minHeap.reHeapify(getSpecificMinHeapAccessor(bringBackURI));

                /*if (!callInPutMethod) {
                    try {
                        put(input, uri,DocumentFormat.BINARY);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    btree.get(uri).setMetadata(bringBackDoc.getMetadata()); //make sure metadata gets put back
                }*/

            }
        }


    }

    private int put(InputStream input, URI url, DocumentFormat format, String privatePutNoCommands) throws IOException {
        btreeEntries.add(url);

        int oldHash = 0;
        if (url == null || url.toString().isEmpty() || format == null) {//|| url.toString().isEmpty() ?????????????????
            throw new IllegalArgumentException();
        }
        if (input == null) { //this is a delete
            DocumentImpl theDoc = bTreeGet(doc,url,true);   //Brings a document back into memory if it exists
            if (theDoc != null) { //there is a doc
                int old = get(url).hashCode();
                delete(url, "Private delete, no commands!"); //Now we brought it back into memory, but this should put it back to disk
                return old;
            } else { //no doc to delete
                return 0;
            }
        } else { //PUT
            byte[] theInput = input.readAllBytes(); //now you have a byte array of the inputstream
            if (format == DocumentFormat.TXT) {

                ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(theInput);
                String inputStr = new String(byteArrayInputStream.readAllBytes(), StandardCharsets.UTF_8);
                DocumentImpl w = new DocumentImpl(url, inputStr, null);
                BTreeAccessor bTreeAccessor = new BTreeAccessor(url,this.doc);

                //int wouldBeMemory = getTotalMemory() + w.getDocumentBinaryData().length;
                int newDocBytes =w.getDocumentTxt().getBytes().length; //w.getDocumentBinaryData().length;
                if(maxDocBytesHaveBeenSet && newDocBytes>this.maxDocumentBytes)
                {
                    throw new IllegalArgumentException();
                }

                DocumentImpl theDoc = bTreeGet(doc,url,true);   //Brings a document back into memory if it exists
                if (theDoc == null) { //No previous doc for URI-- //CHECK THIS

                    this.doc.put(url, w);

                    Set<String> r = w.getWords();
                    for (String h : r) { //for all words in the doc
                        //docsTrie.put(h, w);
                        docsTrie.put(h, bTreeAccessor); //put the specific doc in each word of the doc in the trie
                    }

                    //PRIVATE PUT - NO COMMANDS!!!

                    /*GenericCommand newC = new GenericCommand(url, (u) -> {
                        commandStack.pop();
                        undoPut(url); //don't need to set nano, cuz it's not in the heap anymore
                        for (String h : r) { //delete doc from all places in the trie
                            //docsTrie.delete(h, w);
                            docsTrie.delete(h, bTreeAccessor);
                        }

                    }
                    );
                    commandStack.push(newC);*/


                    //add to minHeap
                    minHeap.insert(bTreeAccessor);
                    setNanoAndReHeap(getSpecificMinHeapAccessor(url)); //set nanoTime and reHeap...needed here?
                    //allAccessors.add(bTreeAccessor);
                    checkOverLimit();
                    return 0;
                } else { //YES previous doc for URI
                    oldHash = get(url).hashCode(); //store hashCode of old doc for URI
                    Document oldDoc = get(url);
                    BTreeAccessor oldDocTrieAccessor = getSpecificTrieAccessor(url);
                    BTreeAccessor oldDocMinHeapAccessor = getSpecificMinHeapAccessor(url);
                    this.doc.put(url, w); //Replace doc

                    //STAGE 6:
                    //If you create a BTreeAccessor here with the same parameters
                    //  and throw it into the docsTrie.delete()
                    //   it should delete the instance of BTreeAccessor in the trie for that doc
                    // since this BTree will be == to the BTreeAccessor in the trie



                    Set<String> oldDocWords = oldDoc.getWords();
                    for (String n : oldDocWords) {
                        //docsTrie.delete(n, oldDoc); STAGE 6
                        docsTrie.delete(n, getSpecificTrieAccessor(url));
                    }
                    //DELETE OLD DOC FROM minHeap????????????????????????????????
                    //Idea: use peek(), and then keep making heaps
                    //      with smaller arrays until peek() returns desired Document
                    BTreeAccessor s= getSpecificMinHeapAccessor(url);
                    deleteSpecificDocFromHeap(s);
                    //deleteSpecificDocFromHeap(getSpecificMinHeapAccessor(url));

                    Set<String> r = w.getWords();
                    for (String h : r) { //for all words in the doc
                        docsTrie.put(h, bTreeAccessor); //put the specific doc in each word of the doc in the trie
                    }


                    //get rid of previous doc at url from trie, then add new doc to trie

                    //PRIVATE PUT - NO COMMANDS!!!

                    /*GenericCommand newC = new GenericCommand(url, (u) -> {
                        commandStack.pop();
                        //delete new doc from heap - STAGE6- DO THIS FIRST so there's only one accessor to find and delete
                        deleteSpecificDocFromHeap(getSpecificMinHeapAccessor(w.getKey()));
                        //put back old doc
                        undoReplaceURI(oldDocMinHeapAccessor); //Thiis puts in the new one

                        for (String h : r) { //delete doc from all places in the trie
                            docsTrie.delete(h, bTreeAccessor);
                        }
                        for (String n : oldDocWords) { //put back old doc in all places in the trie
                            docsTrie.put(n, oldDocTrieAccessor);
                        }
                        checkOverLimit();

                    });
                    commandStack.push(newC);*/

                    minHeap.insert(bTreeAccessor);
                    setNanoAndReHeap(getSpecificMinHeapAccessor(url)); //set nanoTime and reHeap...needed here?
                    //allAccessors.add(bTreeAccessor);
                    checkOverLimit();
                }
            }
            if (format == DocumentFormat.BINARY) { //Give enum to doc in DocumentImpl??????????????????????????????????????
                DocumentImpl c = new DocumentImpl(url, theInput);
                BTreeAccessor bTreeAccessor = new BTreeAccessor(url,this.doc);
                DocumentImpl theDoc = bTreeGet(doc,url,true);   //Brings a document back into memory if it exists
                if (theDoc == null) { //No previous doc for URI-- //CHECK THIS

                    //int wouldBeMemory = getTotalMemory() + c.getDocumentBinaryData().length;
                    int newDocBytes =c.getDocumentBinaryData().length;
                    if(maxDocBytesHaveBeenSet && newDocBytes>this.maxDocumentBytes)
                    {
                        throw new IllegalArgumentException();
                    }

                    this.doc.put(url, c); //put new doc

                    //PRIVATE PUT - NO COMMANDS!!!

                    /*GenericCommand newC = new GenericCommand(url, (u) -> {
                        commandStack.pop();
                        undoPut(url); //in logic- setNanoAndReHeap() ?????
                    }
                    );
                    commandStack.push(newC);*/

                    minHeap.insert(bTreeAccessor);
                    BTreeAccessor f = getSpecificMinHeapAccessor(url);
                    setNanoAndReHeap(f); //set nanoTime and reHeap...needed here?
                    //allAccessors.add(bTreeAccessor);
                    checkOverLimit();

                    return 0;
                } else { //YES previous doc for URI
                    oldHash = get(url).hashCode(); //store hashCode of old doc for URI
                    Document oldDoc = get(url);
                    //BTreeAccessor oldDocTrieAccessor = getSpecificTrieAccessor(url);
                    BTreeAccessor oldDocMinHeapAccessor = getSpecificMinHeapAccessor(url);
                    this.doc.put(url, c); //Replace doc

                    deleteSpecificDocFromHeap(getSpecificMinHeapAccessor(oldDoc.getKey())); //old doc gone from heap

                    //PRIVATE PUT - NO COMMANDS!!!

                    /*GenericCommand newC = new GenericCommand(url, (u) -> {
                        commandStack.pop();
                        //delete new doc from heap - STAGE6- DO THIS FIRST so there's only one accessor to find and delete
                        deleteSpecificDocFromHeap(getSpecificMinHeapAccessor(c.getKey()));
                        //put back old doc
                        undoReplaceURI(oldDocMinHeapAccessor); //in logic- set nanoTime and reHeap

                        checkOverLimit();

                    });
                    commandStack.push(newC);*/

                    minHeap.insert(bTreeAccessor);
                    setNanoAndReHeap(getSpecificMinHeapAccessor(url)); //set nanoTime and reHeap...needed here?
                    //allAccessors.add(bTreeAccessor);
                    checkOverLimit();
                }
            }
            return oldHash;
        }
    }

    private boolean delete(URI url, String privateDeleteNoCommand) {
        DocumentImpl theDoc = bTreeGet(doc,url,false);   //Brings a document back into memory if it exists ... this method is by definition called on a non-null Doc, so this check is superfluous but sill...
        if (theDoc == null) //no document exists with that URI
        {
            return false;
        } else {
            //delete the document
            Document oldDoc = null;//store doc before deleting
            try {
                oldDoc = get(url);
            } catch (IOException e) { //will never hit this
                return false;
            }
            BTreeAccessor oldDocMinHeapAccessor = getSpecificMinHeapAccessor(url);
            BTreeAccessor oldDocTrieAccessor = getSpecificTrieAccessor(url);
            //this.doc.put(url, null);//this is a DELETE from HashTable
            docToDisk(oldDocMinHeapAccessor); //STAGE6 "DELETE"
            deleteSpecificDocFromHeap(getSpecificMinHeapAccessor(url)); //deletes from heap
            //deleteDocFromCommands(url);

            //docsTrie.delete(oldDoc.getDocumentTxt(),oldDoc);
            Set<String> r = oldDoc.getWords();
            for (String h : r) { //for all words in the doc
                docsTrie.delete(h, getSpecificTrieAccessor(oldDoc.getKey())); //delete the specific doc from each word in the doc in the trie
            }

            //PRIVATE DELETE- NO COMMAND!!!

            /*GenericCommand newC = new GenericCommand(url, (u) -> {
                commandStack.pop();
                undoDelete(oldDocMinHeapAccessor);
                for (String h : r) { //for all words in the doc
                    docsTrie.put(h, oldDocTrieAccessor); //put back the specific doc to each word in the trie
                }

            });
            commandStack.push(newC);*/

            return true;
        }
        //I want to create a lambda that does the action of the undo
        //but doesn't create a new command

        //If i undo a put, I'm deleting
        //put() command lambda:
        //      undoes the command on commandstack
        //      deletes, but does NOT add to commandstack
        //That's where this method comes in...
        //Same logic as the delete, but doesn't add to the command stack
        //THIS METHOD IS USED EXCLUSIVELY FOR THE LAMBDA
    }
    /**
     * get the value corresponding to the given metadata key for the document at the given uri
     *
     * @param uri
     * @param key
     * @return the value, or null if there was no value
     * @throws IllegalArgumentException if the uri is null or blank, if there is no document stored at that uri, or if the key is null or blank
     */

    @Override
    public String getMetadata(URI uri, String key) throws IOException{
        if (uri == null || uri.toString().isEmpty()) //if the uri is null or blank
        {
            throw new IllegalArgumentException();
        }
        //if (get(uri) == null) //there is no document stored at that uri
        DocumentImpl theDoc = bTreeGet(doc,uri, false);   //Brings a document back into memory if it exists
        if(theDoc ==null)
        {
            throw new IllegalArgumentException();
        }
        if (key == null || key.isEmpty()) //if the key is null or blank
        {
            throw new IllegalArgumentException();
        }
        if (theDoc.getMetadataValue(key) == null) //no value
        {
            setNanoAndReHeap(getSpecificMinHeapAccessor(uri)); //set nanoTime and reHeap
            return null;
        }
        setNanoAndReHeap(getSpecificMinHeapAccessor(uri)); //set nanoTime and reHeap
        return theDoc.getMetadataValue(key); //returning the value
    }


    private Set<URI> keySetInMemory(){

        //to guard against a delete, check for every URI that it gets something
        //What if it's null? Does in disk count?
        Set<URI> keys = new HashSet<>();
        for(URI uri : btreeEntries){
            if(this.doc.get(uri)!=null){ //ASSUMING IF ITS ON DISK IT DOESN'T COUNT AS IN THE STORE
                keys.add(uri);
            }
        }

        return keys;
    }
    private Set<URI> keySet(){

        //to guard against a delete, check for every URI that it gets something
        //What if it's null? Does in disk count?
        /*for(URI uri : btreeEntries){
            if(this.doc.get(uri)!=null){ //ASSUMING IF ITS ON DISK IT DOESN'T COUNT AS IN THE STORE
                keys.add(uri);
            }
        }*/

        return new HashSet<>(btreeEntries);
    }


    private Collection<Document> values(){
        //for all URIs in keySet(),  get values and store them in here
        Collection<Document> vals = new HashSet<>();
        Set<URI> keys= keySet();
        for(URI uri : keys){
            if(this.doc.get(uri)!=null) { //doc is in memory
                DocumentImpl doc = this.doc.get(uri);
                vals.add(doc);
            }
            else{ //doc is in disk
                try {
                    vals.add(docPersManager.deserialize(uri));
                } catch (IOException ex) { //Doc doesn't exist anywhere (should never hit this bc keySet() returns btreeEntries, which should only hold URIs that exist)
                    throw new RuntimeException(ex);
                }
            }
        }
        return vals;
    }

    private Collection<Document> valuesInMemory(){
        //for all URIs in keySet(),  get values and store them in here
        Collection<Document> vals = new HashSet<>();
        for(URI uri : keySetInMemory()){
            vals.add(this.doc.get(uri));
        }
        return vals;
    }


    private int size(){
        return keySetInMemory().size();
    }



    /**
     * @param input  the document being put
     * @param url    unique identifier for the document
     * @param format indicates which type of document format is being passed
     * @return if there is no previous doc at the given URI, return 0. If there is a previous doc, return the hashCode of the previous doc. If InputStream is null, this is a delete, and thus return either the hashCode of the deleted doc or 0 if there is no doc to delete.
     * @throws IOException              if there is an issue reading input
     * @throws IllegalArgumentException if url or format are null
     */


    @Override
    public int put(InputStream input, URI url, DocumentFormat format) throws IOException {
        btreeEntries.add(url);

        int oldHash = 0;
        if (url == null || url.toString().isEmpty() || format == null) {//|| url.toString().isEmpty() ?????????????????
            throw new IllegalArgumentException();
        }
        if (input == null) { //this is a delete
            DocumentImpl theDoc = bTreeGet(doc,url,true);   //Brings a document back into memory if it exists
            if (theDoc != null) { //there is a doc
                int old = get(url).hashCode();
                delete(url); //Now we brought it back into memory, but this should put it back to disk
                return old;
            } else { //no doc to delete
                return 0;
            }
        } else { //PUT
            byte[] theInput = input.readAllBytes(); //now you have a byte array of the inputstream
            if (format == DocumentFormat.TXT) {

                ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(theInput);
                String inputStr = new String(byteArrayInputStream.readAllBytes(), StandardCharsets.UTF_8);
                DocumentImpl w = new DocumentImpl(url, inputStr, null);
                BTreeAccessor bTreeAccessor = new BTreeAccessor(url,this.doc);

                //int wouldBeMemory = getTotalMemory() + w.getDocumentBinaryData().length;
                int newDocBytes =w.getDocumentTxt().getBytes().length; //w.getDocumentBinaryData().length;
                if(maxDocBytesHaveBeenSet && newDocBytes>this.maxDocumentBytes)
                {
                    throw new IllegalArgumentException();
                }

                boolean onDisk;
                if(doc.get(url)==null){  //Pulled from disk - NOT in BTree
                    onDisk=true;
                }
                else {             //Already on disk
                    onDisk=false;
                }
                //if it was out of memory before, then you brought it  in


                DocumentImpl theDoc = bTreeGet(doc,url,true);   //Brings a document back into memory if it exists
                DocumentImpl existsOnDisk = docPersManager.deserialize(url);
                if (theDoc == null && existsOnDisk==null) { //No previous doc for URI-- //CHECK THIS
                    onDisk=false;

                    this.doc.put(url, w);

                    Set<String> r = w.getWords();
                    for (String h : r) { //for all words in the doc
                        //docsTrie.put(h, w);
                        docsTrie.put(h, bTreeAccessor); //put the specific doc in each word of the doc in the trie
                    }

                    BTreeAccessor minHeapAccessor = bTreeAccessor;
                    boolean finalOnDisk = onDisk;
                    GenericCommand newC = new GenericCommand(url, (u) -> {
                        commandStack.pop();
                        undoPut(url); //don't need to set nano, cuz it's not in the heap anymore
                        for (String h : r) { //delete doc from all places in the trie
                            //docsTrie.delete(h, w);
                            docsTrie.delete(h, bTreeAccessor);
                        }
                        undoBTreeGet(doc,url,minHeapAccessor,w, finalOnDisk);

                    }
                    );
                    commandStack.push(newC);
                    //add to minHeap
                    minHeap.insert(bTreeAccessor);
                    setNanoAndReHeap(getSpecificMinHeapAccessor(url)); //set nanoTime and reHeap...needed here?
                    //allAccessors.add(bTreeAccessor);
                    checkOverLimit();
                    return 0;
                } else { //YES previous doc for URI

                    //There was a prev doc at the URi, but what if it wasn't brought in from disk?
                    DocumentImpl getHashFrom = theDoc;
                    if(existsOnDisk!=null){
                        onDisk =true;
                        doc.put(url,existsOnDisk);
                        docPersManager.delete(url);
                        getHashFrom = existsOnDisk;
                    }
                    else {
                        onDisk = false;
                    }
                    oldHash = get(url).hashCode(); //store hashCode of old doc for URI
                    Document oldDoc = get(url);
                    BTreeAccessor oldDocTrieAccessor = getSpecificTrieAccessor(url); //make ultimateTrieAccessRecords?
                    BTreeAccessor oldDocMinHeapAccessor = getSpecificMinHeapAccessor(url); //maybe get this from ultimateMinHeapAccessRecords... also what abt trie?
                    if(oldDocMinHeapAccessor == null){
                        oldDocMinHeapAccessor=ultimateMinHeapAccessRecords.get(url);
                    }
                    if(oldDocTrieAccessor == null){
                        oldDocTrieAccessor=ultimateTrieAccessRecords.get(url);
                    }

                    //When we replace the doc, the accessor can't access the old doc
                    //Now we create a new accessor based on a bTree which has the old doc in it
                    BTreeImpl<URI, DocumentImpl> btreeCopy = new BTreeImpl<>();
                    btreeCopy.put(url,oldDocMinHeapAccessor.get());
                    oldDocMinHeapAccessor = new BTreeAccessor(url,btreeCopy);
                    oldDocTrieAccessor = new BTreeAccessor(url,btreeCopy);

                    this.doc.put(url, w); //Replace doc

                    //STAGE 6:
                    //If you create a BTreeAccessor here with the same parameters
                    //  and throw it into the docsTrie.delete()
                    //   it should delete the instance of BTreeAccessor in the trie for that doc
                    // since this BTree will be == to the BTreeAccessor in the trie



                    Set<String> oldDocWords = oldDoc.getWords();
                    for (String n : oldDocWords) {
                        //docsTrie.delete(n, oldDoc); STAGE 6
                    //    BTreeAccessor trieaccess= getSpecificTrieAccessor(url);
                        docsTrie.delete(n, getSpecificTrieAccessor(url));
                    }
                    //DELETE OLD DOC FROM minHeap????????????????????????????????
                    //Idea: use peek(), and then keep making heaps
                    //      with smaller arrays until peek() returns desired Document
                    BTreeAccessor s= getSpecificMinHeapAccessor(url);
                    if (s!=null) { //s will be null if doc was brought back from disk
                        deleteSpecificDocFromHeap(s);
                    }
                    //deleteSpecificDocFromHeap(getSpecificMinHeapAccessor(url));

                    Set<String> r = w.getWords();
                    for (String h : r) { //for all words in the doc
                        docsTrie.put(h, bTreeAccessor); //put the specific doc in each word of the doc in the trie
                    }
                    //this.doc.put(url, w); //Replace doc

                    //get rid of previous doc at url from trie, then add new doc to trie

                    boolean finalOnDisk = onDisk;
                    BTreeAccessor minHeapAccessor = bTreeAccessor;
                    BTreeAccessor finalOldDocMinHeapAccessor = oldDocMinHeapAccessor;
                    BTreeAccessor finalOldDocTrieAccessor = oldDocTrieAccessor;
                    GenericCommand newC = new GenericCommand(url, (u) -> {
                        commandStack.pop();
                        //delete new doc from heap - STAGE6- DO THIS FIRST so there's only one accessor to find and delete
                        deleteSpecificDocFromHeap(getSpecificMinHeapAccessor(w.getKey()));
                        //put back old doc
                        undoReplaceURI(finalOldDocMinHeapAccessor); //Thiis puts in the new one
                        /*//delete new doc from heap
                        deleteSpecificDocFromHeap(getSpecificMinHeapAccessor(w.getKey()));*/
                        for (String h : r) { //delete doc from all places in the trie
                            docsTrie.delete(h, bTreeAccessor);
                        }
                        for (String n : oldDocWords) { //put back old doc in all places in the trie
                            docsTrie.put(n, finalOldDocTrieAccessor);
                        }
                       // undoBTreeGet(doc,url,minHeapAccessor,w, finalOnDisk);
                        finalOldDocMinHeapAccessor.setBTree(doc);
                        finalOldDocTrieAccessor.setBTree(doc);
                        checkOverLimit();

                    });
                    commandStack.push(newC);

                    minHeap.insert(bTreeAccessor);
                    setNanoAndReHeap(getSpecificMinHeapAccessor(url)); //set nanoTime and reHeap...needed here?
                    //allAccessors.add(bTreeAccessor);
                    checkOverLimit();
                }
            }
            if (format == DocumentFormat.BINARY) { //Give enum to doc in DocumentImpl??????????????????????????????????????
                DocumentImpl c = new DocumentImpl(url, theInput);
                BTreeAccessor bTreeAccessor = new BTreeAccessor(url,this.doc);


                boolean onDisk;
                if(doc.get(url)==null){  //Pulled from disk
                    onDisk=true;
                }
                else {             //Already on disk
                    onDisk=false;
                }
                DocumentImpl theDoc = bTreeGet(doc,url,true);   //Brings a document back into memory if it exists
                if (theDoc == null) { //No previous doc for URI-- //CHECK THIS
                    onDisk=false;

                    //int wouldBeMemory = getTotalMemory() + c.getDocumentBinaryData().length;
                    int newDocBytes =c.getDocumentBinaryData().length;
                    if(maxDocBytesHaveBeenSet && newDocBytes>this.maxDocumentBytes)
                    {
                        throw new IllegalArgumentException();
                    }

                    this.doc.put(url, c); //put new doc

                    boolean finalOnDisk = onDisk;
                    BTreeAccessor minHeapAccessor = bTreeAccessor;
                    GenericCommand newC = new GenericCommand(url, (u) -> {
                        commandStack.pop();
                        undoPut(url); //in logic- setNanoAndReHeap() ?????
                        undoBTreeGet(doc,url,minHeapAccessor,c, finalOnDisk);
                    }
                    );
                    commandStack.push(newC);

                    minHeap.insert(bTreeAccessor);
                    BTreeAccessor f = getSpecificMinHeapAccessor(url);
                    setNanoAndReHeap(f); //set nanoTime and reHeap...needed here?
                    //allAccessors.add(bTreeAccessor);
                    checkOverLimit();

                    return 0;
                } else { //YES previous doc for URI
                    oldHash = get(url).hashCode(); //store hashCode of old doc for URI
                    Document oldDoc = get(url);
                    //BTreeAccessor oldDocTrieAccessor = getSpecificTrieAccessor(url);
                    BTreeAccessor oldDocMinHeapAccessor = getSpecificMinHeapAccessor(url);
                    this.doc.put(url, c); //Replace doc

                    deleteSpecificDocFromHeap(getSpecificMinHeapAccessor(oldDoc.getKey())); //old doc gone from heap

                    boolean finalOnDisk = onDisk;
                    BTreeAccessor minHeapAccessor = bTreeAccessor;
                    GenericCommand newC = new GenericCommand(url, (u) -> {
                        commandStack.pop();
                        //delete new doc from heap - STAGE6- DO THIS FIRST so there's only one accessor to find and delete
                        deleteSpecificDocFromHeap(getSpecificMinHeapAccessor(c.getKey()));
                        //put back old doc
                        undoReplaceURI(oldDocMinHeapAccessor); //in logic- set nanoTime and reHeap
                        /*//delete new doc from heap
                        deleteSpecificDocFromHeap(getSpecificMinHeapAccessor(c.getKey()));*/
                        undoBTreeGet(doc,url,minHeapAccessor,c, finalOnDisk);
                        checkOverLimit();

                    });
                    commandStack.push(newC);

                    minHeap.insert(bTreeAccessor);
                    setNanoAndReHeap(getSpecificMinHeapAccessor(url)); //set nanoTime and reHeap...needed here?
                    //allAccessors.add(bTreeAccessor);
                    checkOverLimit();
                }
            }
            return oldHash;
        }
    }

    private void undoReplaceURI(BTreeAccessor x) {
        //must take all the info of the previous doc and put()
        //this acts as its own put() method

        //bTreeGet(doc,x.get().getKey());
        URI theUri = x.get().getKey();

        minHeap.insert(x);
        setNanoAndReHeap(x); //set nanoTime and reHeap
        //DELETE SPECIFIC DOC FROM HEAP IN LOGIC THAT USES THIS
        //Maybe pass doc to be deleted to this method and do the logic here?

        this.doc.put(theUri, x.get());


    }

    private void undoPut(URI uri) {
        //This method executes a delete WITHOUT adding
        //      a delete command to the commandstack

        DocumentImpl theDoc = bTreeGet(doc,uri,false);   //Brings a document back into memory if it exists
        if (theDoc != null) //document exists with that URI
        {
            BTreeAccessor specificDoc = getSpecificMinHeapAccessor(uri);

            //this.doc.put(uri, null);//this is a DELETE from HashTable

            //delete specific doc from minHeap
            //setNanoAndReHeap(get(uri)); //set nanoTime and reHeap
            deleteSpecificDocFromHeap(getSpecificMinHeapAccessor(uri));

            URI holdUri = specificDoc.get().getKey();
            doc.put(holdUri,null);
            /*docToDisk(specificDoc); //STAGE6 "DELETE"
            try {
                docPersManager.delete(holdUri);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }*/


        }



    }

    private void deleteSpecificDocFromHeap(BTreeAccessor x){

        //for every element in the heap, remove() and see if it's the Document you're looking for
        //if not, save the Document removed to a Stack
        //if it is, you've removed the right Document.
        //      Now, put all the documents back into the heap (and reHeapify after each put back?)

        //try-catch the NoSuchElementException
            //if you catch the exception, the element is not there. Act accordingly.

        //x.setLastUseTime(System.nanoTime()); //don't really need to do this, since you're deleting it anyway

        StackImpl stack = new StackImpl<BTreeAccessor>();
        //TRY-CATCH??? See above
        //for(int i=0; i<doc.size(); i++)
        while(minHeap.peek()!=null){ //for all documents
            if(minHeap.peek().hashCode()!=x.hashCode()){ //if the top of the minHeap is NOT what you're looking for

                BTreeAccessor removed = minHeap.remove();
                stack.push(removed); //remove the document and push to temp stack
            }
            else{ //you've found desired document
                minHeap.remove();
                break;
            }
        }
        while(stack.size()!=0) { //for all elements of the stack
            //Document hold = (Document) stack.pop(); //pop from stack and hold
            BTreeAccessor hold = (BTreeAccessor) stack.pop();
            minHeap.insert(hold); //put back into minHeap
            minHeap.reHeapify(hold); //reheapify
        }
    }

    /*private int getHeapSize(){
        while(minHeap.peek()!=null){

        }
    }*/

    /**
     * @param url the unique identifier of the document to get
     * @return the given document
     */
    private Document get(URI url,boolean x){
        return doc.get(url);
    }

    @Override
    public Document get(URI url) throws IOException{
        //Document x= this.doc.get(url);
        DocumentImpl x = bTreeGet(doc,url,false); //ctrl-f theDoc

        if(x==null){
            return null;
        }

        setNanoAndReHeap(getSpecificMinHeapAccessor(x.getKey())); //set nanoTime and reHeap
        return x;
        //see piazza Q@22

    }

    @Override
    public void undo() throws IllegalStateException {
        if (commandStack.peek() == null) {//nothing in stack
            throw new IllegalStateException();
        }

        commandStack.peek().undo(); //throw exception if stack is empty? I thought null (StackImpl)...

        //if it's a CommandSet, undoes all commands in the CommandSet
        //If after the undo the top is an empty CommandSet, pop from the commandStack

        if(commandStack.peek() instanceof CommandSet<?> &&
                ((CommandSet<?>) commandStack.peek()).size()==0) { //CommandSet is empty
            commandStack.pop();
        }


    }

    @Override
    public void undo(URI url) throws IllegalStateException {
        Stack<Undoable> tempStack = new StackImpl<>();
        if (commandStack.peek() == null) { //nothing in stack
            throw new IllegalStateException();
        }
        while (commandStack.peek() != null) { //running thru stack
            if (commandStack.peek() instanceof GenericCommand<?>) {
                if (((GenericCommand<URI>) commandStack.peek()).getTarget() != url) {
                    tempStack.push(commandStack.peek()); //PUT COMMANDS IN TEMP STACK
                    commandStack.pop();
                } else { //Same URI... stop popping
                    break;
                }
            }
            if (commandStack.peek() instanceof CommandSet<?>) { //checking to see if the doc exists in a GenericCommand of the CommandSet
                if(((CommandSet<URI>) commandStack.peek()).containsTarget(url)){
                    break;
                }
                else{
                    tempStack.push(commandStack.peek());
                    commandStack.pop();
                }
            }
        }
        if (commandStack.peek() == null) { //gone thru the stack, and no matching URI
            while (tempStack.peek() != null) { //push all back to commandStack
                commandStack.push(tempStack.peek());
                tempStack.pop();
            }
            throw new IllegalStateException(); //no matching URI- throw exception
        }
        if (commandStack.peek() instanceof GenericCommand<?>) {
            if (((GenericCommand<URI>) commandStack.peek()).getTarget() == url) {
                commandStack.peek().undo();
            }
        }
        if ( commandStack.peek() instanceof CommandSet<?>) {
            ((CommandSet<URI>) commandStack.peek()).undo(url);
            if(((CommandSet<URI>) commandStack.peek()).size()==0){ //if the CommandSet is empty, pop() the CommandSet from the commandStack
                commandStack.pop();
            }
        }
        while (tempStack.peek() != null) { //push all back to commandStack
            commandStack.push(tempStack.peek());
            tempStack.pop(); //take off top of tempStack
        }
    }


    /**
     * @param url the unique identifier of the document to delete
     * @return true if the document is deleted, false if no document exists with that URI
     */
    @Override
    public boolean delete(URI url) {
        DocumentImpl theDoc = bTreeGet(doc,url,false);   //Brings a document back into memory if it exists ... this method is by definition called on a non-null Doc, so this check is superfluous but sill...
        if (theDoc == null) //no document exists with that URI
        {
            return false;
        } else {
            //delete the document
            DocumentImpl oldDoc = null;//store doc before deleting
            try {
                oldDoc = (DocumentImpl) get(url);
            } catch (IOException e) {
                return false;
            }
            BTreeAccessor oldDocMinHeapAccessor = getSpecificMinHeapAccessor(url);
            BTreeAccessor oldDocTrieAccessor = getSpecificTrieAccessor(url);
            //this.doc.put(url, null);//this is a DELETE from HashTable
     //       docToDisk(oldDocMinHeapAccessor); //STAGE6 "DELETE"
            deleteSpecificDocFromHeap(getSpecificMinHeapAccessor(url)); //deletes from heap
            //deleteDocFromCommands(url);

            //docsTrie.delete(oldDoc.getDocumentTxt(),oldDoc);
            Set<String> r = oldDoc.getWords();
            for (String h : r) { //for all words in the doc
                docsTrie.delete(h, getSpecificTrieAccessor(oldDoc.getKey())); //delete the specific doc from each word in the doc in the trie
            }

            DocumentImpl finalOldDoc = oldDoc;
            GenericCommand newC = new GenericCommand(url, (u) -> {
                commandStack.pop();
                undoDelete(oldDocMinHeapAccessor,finalOldDoc);
                for (String h : r) { //for all words in the doc
                    docsTrie.put(h, oldDocTrieAccessor); //put back the specific doc to each word in the trie
                }

            });
            commandStack.push(newC);
            doc.put(url,null);    //value is null in BTree and it's not in memory
            btreeEntries.remove(url);
            return true;
        }
        //I want to create a lambda that does the action of the undo
        //but doesn't create a new command

        //If i undo a put, I'm deleting
        //put() command lambda:
        //      undoes the command on commandstack
        //      deletes, but does NOT add to commandstack
        //That's where this method comes in...
        //Same logic as the delete, but doesn't add to the command stack
        //THIS METHOD IS USED EXCLUSIVELY FOR THE LAMBDA
    }

    private boolean deleteUriForCommandSet(URI url) {
        //SAME AS delete(URI), but doesn't create a command
        //This is done so a GenericCommand can be made at the methods which use this method,
            //which are then added from there to a CommandSet

      //  DocumentImpl theDoc = bTreeGet(doc,url,false);   //Brings a document back into memory if it exists
        boolean deleted;
        try {
            deleted= docPersManager.delete(url);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        //Now it's either been deleted, exists in memory, or there's no Document with that URI
        if(deleted){
            return true; //it's been deleted
        }

        //Now it either exists in memory, or there's no Document with that URI

        if(get(url,true)==null){ //If there's no Document with that URI
            return false;

        } else { //The document exists in memory


            //delete the document
            Document oldDoc = null;//store doc before deleting
            oldDoc = get(url,true);
            //this.doc.put(url, null);//this is a DELETE from HashTable
            BTreeAccessor specificDoc = getSpecificMinHeapAccessor(url);

            deleteSpecificDocFromHeap(specificDoc); //deletes from heap

            //docsTrie.delete(oldDoc.getDocumentTxt(),oldDoc);
            Set<String> r = oldDoc.getWords();
            for (String h : r) { //for all words in the doc
                docsTrie.delete(h, getSpecificTrieAccessor(oldDoc.getKey())); //delete the specific doc from each word in the doc in the trie
            }
         //   docToDisk(specificDoc);
            doc.put(url,null);    //value is null in BTree and it's not in memory
            return true;
        }
    }




    private void undoDelete(BTreeAccessor d, DocumentImpl document) {

        //int wouldBeMemory = getTotalMemory() + d.getDocumentBinaryData().length;

        //if the doc is TXT, int newDocBytes =d.getDocumentTxt().getBytes().length;
        //if the doc in BINARY, do this
        int newDocBytes;
        if(document.getDocumentTxt()==null){ //BINARY doc
            newDocBytes =document.getDocumentBinaryData().length;
        }
        else{ //TXT doc
            newDocBytes =document.getDocumentTxt().getBytes().length;
        }

        //int newDocBytes =d.getDocumentBinaryData().length;
        if(maxDocBytesHaveBeenSet && newDocBytes>this.maxDocumentBytes)
        {
            throw new IllegalArgumentException();
        }
        URI theUri=document.getKey();


        //HAVE TO ADD TO minHeap??????????????????????????????????????????????????????
        this.doc.put(theUri, document);
        d.setLastUseTime(System.nanoTime()); //update nanoTime
        minHeap.insert(d);
        setNanoAndReHeap(d); //set nanoTime and reHeap...needed here?
        //checkOverLimit(); //REHEAP???

        this.doc.put(theUri, document);

        btreeEntries.add(theUri);
        checkOverLimit(); //REHEAP???

    }


    private List<Document> NoReheapSearch(String keyword) {

        //create a trie that the put() method uses
        Comparator<Document> com = new Comparator<>() {
            @Override
            public int compare(Document o1, Document o2) {
                return o2.wordCount(keyword)-o1.wordCount(keyword);
                //reversed the sort in trie, so reversed comparator back here
                //check that this was right to do
            }

        };

        Set<URI> URIs= keySet();
        /*Set<Document> documentsInStore = new HashSet<>();
        for(URI uri: URIs){
            documentsInStore.add(get(uri));
        }*/
        Collection<Document> documentsInStore= values();

        Set<Document> hasKeyword = new HashSet<>();
        for(Document j: documentsInStore){
            if(j.getWords().contains(keyword)){
                hasKeyword.add(j);
            }
        }
        TrieImpl<Document> trieAgain = new TrieImpl<>();
        for(Document o: hasKeyword){
            trieAgain.put(keyword,o);
            //setNanoAndReHeap(o);
        }

        List<Document> keywordDocs = trieAgain.getSorted(keyword,com);

        return keywordDocs;
    }

    private List<Document> NoReheapSearchByMetadata(Map<String, String> keysValues) {

        List<Document> docs = new ArrayList<>();
        Set<String> keys = keysValues.keySet();

        if(keys.isEmpty()){ //empty map
            return docs;
        }

        Set<URI> n =keySet(); //all the URIs for docs in the store
        for(URI f: n){ //for all URI's
            boolean contains= true;
            for(String k : keys){ //for all keys in keysValues
                if(getMetadataNoReheap(f,k)==null || !getMetadataNoReheap(f,k).equals(keysValues.get(k))){ //if a key doesn't match the metadata (or exist)
                    contains = false; //DOES NOT CONTAIN
                }
            }
            if(contains){ //only contains if all keys mapped to vals
                Document checkDocDeleted = getNoReheap(f);
                if(checkDocDeleted==null){ //Will be null if doc is from disk
                    try {
                        checkDocDeleted = docPersManager.deserialize(f); //if from disk, return doc itself instead of null
                    } catch (IOException e) {
                        throw new RuntimeException("Tried to deserialize");
                    }
                }

                docs.add(checkDocDeleted);
                //setNanoAndReHeap(get(f)); //was searched, update time
            }
        }
        return docs;
    }


    private List<Document> NoReheapSearchByPrefix(String keywordPrefix) {

        Comparator<Document> com = new Comparator<>() {
            @Override
            public int compare(Document o1, Document o2) {

                //getWords() - all the words in a document
                //substring the first letters of each word returned in the set by length of keywordPrefix
                //add each of these words to a new set
                //add up all the wordCounts of the documents in the new set
                //compare the totals

                int o1Count=0;
                Set<String> o1Words = o1.getWords();
                for(String word : o1Words){
                    if(word.startsWith(keywordPrefix)){
                        //o1Prefixes.add(word);
                        o1Count+=o1.wordCount(word);
                    }
                }

                int o2Count=0;
                Set<String> o2Words = o2.getWords();
                for(String word : o2Words){
                    if(word.startsWith(keywordPrefix)){
                        //o2Prefixes.add(word);
                        o2Count+=o2.wordCount(word);
                    }
                }
                return o2Count-o1Count;
            }
        };

        Set<URI> URIs= keySet();
        /*Set<Document> documentsInStore = new HashSet<>();
        for(URI uri: URIs){
            documentsInStore.add(get(uri));
        }*/
        Collection<Document> documentsInStore= values();

        Set<Document> docHasPrefix = new HashSet<>();
        for(Document j: documentsInStore){

            Set<String> wordsInDoc = j.getWords();
            for(String l: wordsInDoc){
                if(l.startsWith(keywordPrefix)){
                    docHasPrefix.add(j);
                }
            }

        }
        TrieImpl<Document> trieAgain = new TrieImpl<>();
        for(Document o: docHasPrefix){
            trieAgain.put(keywordPrefix,o);
            //setNanoAndReHeap(o);
        }

        List<Document> prefixDocs = trieAgain.getAllWithPrefixSorted(keywordPrefix,com);
        return prefixDocs;

    }

    @Override
    public List<Document> search(String keyword) throws IOException{

        //create a trie that the put() method uses
        Comparator<Document> com = new Comparator<>() {
            @Override
            public int compare(Document o1, Document o2) {
                return o2.wordCount(keyword)-o1.wordCount(keyword);
                //reversed the sort in trie, so reversed comparator back here
                //check that this was right to do
            }

        };

        //search thru the docs map
            //for every doc, call the getWords() method
            //if the Set<String> returned contains the keyword, add to new set
        //      Then, create new trie with the documents contained at a specific index (the keyword) of the trie
                //Finally, call getSorted() on that index of the new trie

        Set<URI> URIs= keySet();
        /*Set<Document> documentsInStore = new HashSet<>();
        for(URI uri: URIs){
            documentsInStore.add(get(uri));
        }*/
        Collection<Document> documentsInStore= values();

        Set<Document> hasKeyword = new HashSet<>();
        boolean offDisk =false;
        for(Document j: documentsInStore){
            if(doc.get(j.getKey())!=null)//doc exists in memory
            {
                offDisk =false;
            }
            else { //doc exists on disk
                offDisk = true;
            }
            if(j.getWords().contains(keyword)){
                hasKeyword.add(j);

                //STAGE 6 ADD:
                searchPutter(j);
                if(offDisk){
                    try {
                        docPersManager.delete(j.getKey()); //bc we're putting it back in memory
                    } catch (IOException e) { //doesn't exist on disk- will never hit this catch bc by definition we brought it "offDisk"
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        TrieImpl<Document> trieAgain = new TrieImpl<>();
        for(Document o: hasKeyword){
            trieAgain.put(keyword,o);
//            setNanoAndReHeap(getSpecificMinHeapAccessor(o.getKey())); //NEED THIS???
        }

        List<Document> keywordDocs = trieAgain.getSorted(keyword,com);

        return keywordDocs;
    }

    private void searchPutter(Document document){

        if(document.getWordMap()!=null) { //TXT DOC
            try {
                String docTxt = document.getDocumentTxt();
                byte[] bytesToPut = docTxt.getBytes();             //CHECK THAT THIS IS RIGHT!!!!
                InputStream input = new ByteArrayInputStream(bytesToPut);
                put(input, document.getKey(),DocumentFormat.TXT);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        else { //BINARY DOC
            try {
                byte[] bytesToPut = document.getDocumentBinaryData();             //CHECK THAT THIS IS RIGHT!!!!
                InputStream input = new ByteArrayInputStream(bytesToPut);
                put(input, document.getKey(),DocumentFormat.BINARY);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
    @Override
    public List<Document> searchByPrefix(String keywordPrefix) throws IOException{

        Comparator<Document> com = new Comparator<>() {
            @Override
            public int compare(Document o1, Document o2) {

                //getWords() - all the words in a document
                //substring the first letters of each word returned in the set by length of keywordPrefix
                //add each of these words to a new set
                //add up all the wordCounts of the documents in the new set
                //compare the totals

                int o1Count=0;
                Set<String> o1Words = o1.getWords();
                for(String word : o1Words){
                    if(word.startsWith(keywordPrefix)){
                        //o1Prefixes.add(word);
                        o1Count+=o1.wordCount(word);
                    }
                }

                int o2Count=0;
                Set<String> o2Words = o2.getWords();
                for(String word : o2Words){
                    if(word.startsWith(keywordPrefix)){
                        //o2Prefixes.add(word);
                        o2Count+=o2.wordCount(word);
                    }
                }
                return o2Count-o1Count;
            }
        };

        Set<URI> URIs= keySet();
        /*Set<Document> documentsInStore = new HashSet<>();
        for(URI uri: URIs){
            documentsInStore.add(get(uri));
        }*/
        Collection<Document> documentsInStore= values();

        Set<Document> docHasPrefix = new HashSet<>();
        for(Document j: documentsInStore){

            Set<String> wordsInDoc = j.getWords();
            for(String l: wordsInDoc){
                if(l.startsWith(keywordPrefix)){
                    docHasPrefix.add(j);
                }
            }

        }
        TrieImpl<Document> trieAgain = new TrieImpl<>();
        for(Document o: docHasPrefix){
            trieAgain.put(keywordPrefix,o);
            setNanoAndReHeap(getSpecificMinHeapAccessor(o.getKey()));
        }

        List<Document> prefixDocs = trieAgain.getAllWithPrefixSorted(keywordPrefix,com);
        return prefixDocs;

    }

    @Override
    public Set<URI> deleteAll(String keyword) {

        //MAKE SURE DOCS ARE DELETED FROM TRIE!!!

        //getWords() of every doc
        //if the Set<String> returned includes the keyword, call deleteAll() on the text of the doc
        /*Set<URI> URIs= doc.keySet();
        Set<Document> documentsInStore = new HashSet<>();
        for(URI uri: URIs){
            documentsInStore.add(get(uri));
        }*/
        Collection<Document> documentsInStore= values();

        //Set<Document> hasKeyword = new HashSet<>();
        Set<Document> docsToDelete= new HashSet<>();
        for(Document j: documentsInStore){
            if(j.getWords().contains(keyword)){
                docsToDelete.add(j);
            }
        }

        CommandSet<URI> newComSet = new CommandSet<>();

        Set<URI> deletedDocs = new HashSet<>();
        for(Document h : docsToDelete) {
            AtomicReference<BTreeAccessor> delDocMinHeapAccessor = new AtomicReference<>(getSpecificMinHeapAccessor(h.getKey()));
            BTreeAccessor delDocTrieAccessor = getSpecificTrieAccessor(h.getKey());

            deletedDocs.add(h.getKey()); //add to returned Set of deleted docs

         //   deleteSpecificDocFromHeap(getSpecificMinHeapAccessor(h.getKey()));
            deleteUriForCommandSet(h.getKey()); //delete from HashMap, trie, and heap
            //setNanoAndReHeap(h);



            GenericCommand newC = new GenericCommand(h.getKey(), (u) -> {


                //The docs on disk have no accessors, so we have to keep them somewhere in case we're undoing a delete from disk
                if(delDocMinHeapAccessor.get() ==null){
                    delDocMinHeapAccessor.set(ultimateMinHeapAccessRecords.get(h.getKey()));
                }
                //doesn't need to be popped from commandStack, b/c it's in a CommandStack, not a standalone GenericCommand
                //only action must be undone

                //what's action? A: deleting all
                //undo: put() back all
                //store all docs before deletion
                //then for undo, put them back

                //STAGE 5: insert back to minHeap
                //if(doc.size()<this.maxDocumentCount) {
                    undoDelete(delDocMinHeapAccessor.get(), (DocumentImpl) h); //puts back to HashMap, minHeap

                    //put back to all places in the trie
                    Set<String> v = h.getWords();
                    for (String k : v) {
                        docsTrie.put(k, delDocTrieAccessor);
                    }
               // }

            });
            newComSet.addCommand(newC);

        }

        for(URI uri: deletedDocs){
            btreeEntries.remove(uri);
        }
        commandStack.push(newComSet);

        return deletedDocs;

    }

    @Override
    public Set<URI> deleteAllWithPrefix(String keywordPrefix) {

        //MAKE SURE DOCS ARE DELETED FROM TRIE!!!


        /*Set<URI> URIs= doc.keySet();
        Set<Document> documentsInStore = new HashSet<>();
        for(URI uri: URIs){
            documentsInStore.add(get(uri));
        }*/
        Collection<Document> documentsInStore= values();

        Set<Document> docHasPrefix = new HashSet<>();
        for(Document j: documentsInStore) {

            Set<String> wordsInDoc = j.getWords();
            for (String l : wordsInDoc) {
                if (l.startsWith(keywordPrefix)) {
                    docHasPrefix.add(j);
                }
            }
        }
        //Set<Document> c = docsTrie.deleteAllWithPrefix(keywordPrefix); //delete from trie

        CommandSet<URI> newComSet = new CommandSet<>();

        Set<URI> deletedPrefixDocs = new HashSet<>();
        for(Document e : docHasPrefix){
            AtomicReference<BTreeAccessor> delDocMinHeapAccessor = new AtomicReference<>(getSpecificMinHeapAccessor(e.getKey()));
            BTreeAccessor delDocTrieAccessor = getSpecificTrieAccessor(e.getKey());

            deletedPrefixDocs.add(e.getKey()); //add to returned Set of deleted docs

        //    deleteSpecificDocFromHeap(getSpecificMinHeapAccessor(e.getKey()));
            deleteUriForCommandSet(e.getKey()); //delete from HashMap and trie
            //setNanoAndReHeap(e);

            GenericCommand newC = new GenericCommand(e.getKey(), (u) -> {
                if(delDocMinHeapAccessor.get() ==null){
                    delDocMinHeapAccessor.set(ultimateMinHeapAccessRecords.get(e.getKey()));
                }

                //doesn't need to be popped from commandStack, b/c it's in a CommandStack, not a standalone GenericCommand
                //only action must be undone

                //what's action? A: deleting all with prefix
                //undo: put() back all with prefix
                //store all docs before deletion
                //then for undo, put them back

                undoDelete(delDocMinHeapAccessor.get(), (DocumentImpl) e);
                Set<String> v= e.getWords();
                for(String k: v){
                    docsTrie.put(k,delDocTrieAccessor);
                }

            });
            newComSet.addCommand(newC);
        }

        for(URI uri: deletedPrefixDocs){
            btreeEntries.remove(uri);
        }
        commandStack.push(newComSet);
        return deletedPrefixDocs;
    }

    private Document getNoReheap(URI url) {
        Document x= this.doc.get(url);
        //setNanoAndReHeap(x); //DO NOT set nanoTime and reHeap
        return x;
        //see piazza Q@22

    }
    private String getMetadataNoReheap(URI uri, String key) {
        if (uri == null || uri.toString().isEmpty()) //if the uri is null or blank
        {
            throw new IllegalArgumentException();
        }
        if (getNoReheap(uri) == null) //there is no document stored at that uri
        {
            if(ultimateMinHeapAccessRecords.containsKey(uri)){
                try {
                    Document toFindMetadata = docPersManager.deserialize(uri);
                    return toFindMetadata.getMetadataValue(key);
                } catch (IOException e) {
                    System.out.println("Doc doesn't exist anywhere");
                }

            }

            throw new IllegalArgumentException();
        }
        if (key == null || key.isEmpty()) //if the key is null or blank
        {
            throw new IllegalArgumentException();
        }
        if (getNoReheap(uri).getMetadataValue(key) == null) //no value
        {
            //setNanoAndReHeap(get(uri)); //DO NOT set nanoTime and reHeap
            return null;
        }
        //setNanoAndReHeap(get(uri)); //DO NOT set nanoTime and reHeap
        return getNoReheap(uri).getMetadataValue(key); //returning the value
    }

    private String getMetadataNoReheapOffDisk(DocumentImpl doc, String key) {
        if (doc.getKey() == null || doc.getKey().toString().isEmpty()) //if the uri is null or blank
        {
            throw new IllegalArgumentException();
        }
        /*if (getNoReheap(doc.getKey()) == null) //there is no document stored at that uri
        {
            throw new IllegalArgumentException();
        }*/  //there's no doc here by definition bc its off disk...
        if (key == null || key.isEmpty()) //if the key is null or blank
        {
            throw new IllegalArgumentException();
        }
        if (doc.getMetadataValue(key) == null) //no value
        {
            //setNanoAndReHeap(get(uri)); //DO NOT set nanoTime and reHeap
            return null;
        }
        //setNanoAndReHeap(get(uri)); //DO NOT set nanoTime and reHeap
        return doc.getMetadataValue(key); //returning the value
    }

    @Override
    public List<Document> searchByMetadata(Map<String, String> keysValues) throws IOException{

        List<Document> docs = new ArrayList<>();
        Set<String> keys = keysValues.keySet();

        if(keys.isEmpty()){ //empty map
            return docs;
        }



        //for every document in the DocumentStore, if it contains the given key,
            //check if the metadata is the same
        //do this for every key
        //do this in every document

        //instead maybe get the Documents and extract their URIs
        //This way there's no get() done

        Set<URI> n =keySet(); //all the URIs for docs in the store
        boolean offDisk =false;
        for(URI f: n){ //for all URI's
            if(doc.get(f)!=null)//doc exists in memory
            {
                offDisk =false;
            }
            else { //doc exists on disk
                offDisk = true;
            }
            boolean contains= true;
            if (!offDisk) { //doc was in memory
                for(String k : keys){ //for all keys in keysValues
                    if(getMetadataNoReheap(f,k)==null || !getMetadataNoReheap(f,k).equals(keysValues.get(k))){ //if a key doesn't match the metadata (or exist)
                        contains = false; //DOES NOT CONTAIN
                    }
                }
                if(contains){ //only contains if all keys mapped to vals
                    docs.add(get(f));
                    setNanoAndReHeap(getSpecificMinHeapAccessor(f)); //was searched, update time
                }
            }
            else { //doc was on disk, if not deleted
                DocumentImpl docOffDisk = null;
                try {
                    docOffDisk= docPersManager.deserialize(f);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
           //     getMetadataNoReheapOffDisk
                if (docOffDisk!=null) { //doc was in disk, NOT deleted
                    for(String k : keys){ //for all keys in keysValues
                        if(getMetadataNoReheapOffDisk(docOffDisk,k)==null || !getMetadataNoReheapOffDisk(docOffDisk,k).equals(keysValues.get(k))){ //if a key doesn't match the metadata (or exist)
                            contains = false; //DOES NOT CONTAIN
                        }
                    }
                    if(contains){ //only contains if all keys mapped to vals
                        docs.add(docOffDisk);
                        searchPutter(docOffDisk);
                        //don't need to update time and reheap bc u just put it
                      //  setNanoAndReHeap(getSpecificMinHeapAccessor(f)); //was searched, update time
                        try {
                            docPersManager.delete(f);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }
        }


        /*Set<Document> hasKeyword = new HashSet<>();
        boolean offDisk =false;
        for(Document j: documentsInStore){
            if(doc.get(j.getKey())!=null)//doc exists in memory
            {
                offDisk =false;
            }
            else { //doc exists on disk
                offDisk = true;
            }
            if(j.getWords().contains(keyword)){
                hasKeyword.add(j);

                //STAGE 6 ADD:
                searchPutter(j);
                if(offDisk){
                    try {
                        docPersManager.delete(j.getKey()); //bc we're putting it back in memory
                    } catch (IOException e) { //doesn't exist on disk- will never hit this catch bc by definition we brought it "offDisk"
                        throw new RuntimeException(e);
                    }
                }
            }
        } */
        return docs;
    }

    @Override
    public List<Document> searchByKeywordAndMetadata(String keyword, Map<String, String> keysValues) throws IOException{

        //Do a search() - returns List<Document>
        //Do a searchByMetadata() - returns List<Document>

        //for all Documents returned by the search, if they are in the list returned by the searchByMetadata(),
        //      add to the returned list

        List<Document> keyAndMeta = new ArrayList<>();
        List<Document> keySearch = NoReheapSearch(keyword);
        List<Document> metaSearch = NoReheapSearchByMetadata(keysValues);

        for(Document a : keySearch){
            if(metaSearch.contains(a)){
                keyAndMeta.add(a);
                setNanoAndReHeap(getSpecificMinHeapAccessor(a.getKey())); //was searched, update time
            }
        }
        return keyAndMeta;
    }

    @Override
    public List<Document> searchByPrefixAndMetadata(String keywordPrefix, Map<String, String> keysValues) throws IOException{
        //Do a searchByPrefix() - returns List<Document>
        //Do a searchByMetadata() - returns List<Document>

        //for all Documents returned by the searchByPrefix(), if they are in the list returned by the searchByMetadata(),
        //      add to the returned list

        List<Document> prefixAndMeta = new ArrayList<>();
        List<Document> prefixSearch = NoReheapSearchByPrefix(keywordPrefix);
        List<Document> metaSearch = NoReheapSearchByMetadata(keysValues);

        for(Document b : prefixSearch){
            if(metaSearch.contains(b)){
                prefixAndMeta.add(b);
                setNanoAndReHeap(getSpecificMinHeapAccessor(b.getKey())); //was searched, update time
            }
        }
        return prefixAndMeta;

    }

    @Override
    public Set<URI> deleteAllWithMetadata(Map<String, String> keysValues) throws IOException{

        List<Document> containsMeta = searchByMetadata(keysValues); //The docs on disk have no accessors

        //delete from trie
            //a doc is stored at every word in the trie
            //so find every word in the trie, and delete from each word
                //getWords() - returns Set<String> of words

        CommandSet<URI> newComSet = new CommandSet<>();

        Set<URI> t = new HashSet<>();
        for(Document o : containsMeta){
            AtomicReference<BTreeAccessor> delDocMinHeapAccessor = new AtomicReference<>(getSpecificMinHeapAccessor(o.getKey()));
            BTreeAccessor delDocTrieAccessor = getSpecificTrieAccessor(o.getKey());
            deleteUriForCommandSet(o.getKey()); //delete from HashMap and trie
            t.add(o.getKey()); //add to returned Set of deleted docs

            //deleteSpecificDocFromHeap(o); //Don't need this b/c deleteUriForCommandSet does it
            //setNanoAndReHeap(o);

            GenericCommand newC = new GenericCommand(o.getKey(), (u) -> {
                //doesn't need to be popped from commandStack, b/c it's in a CommandStack, not a standalone GenericCommand
                //only action must be undone

                //what's action? A: deleting all with metadata
                //undo: put() back all with metadata
                //store all docs before deletion
                //then for undo, put them back


                //The docs on disk have no accessors, so we have to keep them somewhere in case we're undoing a delete from disk
                if(delDocMinHeapAccessor.get() ==null){
                    delDocMinHeapAccessor.set(ultimateMinHeapAccessRecords.get(o.getKey()));
                }
                /*if(delDocTrieAccessor==null){
                    delDocTrieAccessor= ultimateTrieAccessRecords.get(o.getKey());
                }*/

                undoDelete(delDocMinHeapAccessor.get(), (DocumentImpl) o);
                Set<String> v= o.getWords();
                for(String k: v){
                    docsTrie.put(k,delDocTrieAccessor);
                }

            });
            newComSet.addCommand(newC);

        }
        for(URI uri: t){
          //  docPersManager.delete(uri);
            btreeEntries.remove(uri);
        }
        commandStack.push(newComSet);
        return t;
    }

    private boolean hasURI(List<Document> list, Document doc){

        //This method exists to check for URIs of Documents in the case that are coming from disk
        // since calling contains() will only compare the Documents themselves
        // whereas Documents from disk are essentially the same, though not exactly

        for(Document x : list){
            if(Objects.equals(x.getKey().toString(), doc.getKey().toString())){
                return true;
            }

        }
        return false;
    }

    @Override
    public Set<URI> deleteAllWithKeywordAndMetadata(String keyword, Map<String, String> keysValues)  throws IOException{

        //Do a search() - returns List<Document>
        //Do a searchByMetadata() - returns List<Document>

        //for all Documents returned by the search, if they are in the list returned by the searchByMetadata(),
        //      add to wittled down list

        //Delete all Documents in wittled down list, and return a set of their URIs

        List<Document> keyAndMeta = new ArrayList<>();
        List<Document> keySearch = NoReheapSearch(keyword); //NoReheapSearch()
        List<Document> metaSearch = NoReheapSearchByMetadata(keysValues); //NoReheapSearchByMetadata

        for(Document a : keySearch){
            if(metaSearch.contains(a) || hasURI(metaSearch,a)){
                keyAndMeta.add(a);
            }
        }
        //No there's a list of docs with the matching keys and metadata

        CommandSet<URI> newComSet = new CommandSet<>();

        Set<URI> t = new HashSet<>();
        for(Document o : keyAndMeta){
            AtomicReference<BTreeAccessor> delDocMinHeapAccessor = new AtomicReference<>(getSpecificMinHeapAccessor(o.getKey()));
            BTreeAccessor delDocTrieAccessor = getSpecificTrieAccessor(o.getKey());
            deleteUriForCommandSet(o.getKey()); //delete from HashMap and trie
            t.add(o.getKey()); //add to returned Set of deleted URIs

            //deleteSpecificDocFromHeap(o); Don't need this b/c deleteUriForCommandSet does it
            //setNanoAndReHeap(o);

            GenericCommand newC = new GenericCommand(o.getKey(), (u) -> {
                if(delDocMinHeapAccessor.get() ==null){
                    delDocMinHeapAccessor.set(ultimateMinHeapAccessRecords.get(o.getKey()));
                }

                //doesn't need to be popped from commandStack, b/c it's in a CommandStack, not a standalone GenericCommand
                //only action must be undone

                //what's action? A: deleting all with keyword and metadata
                //undo: put() back all with keyword and metadata
                //store all docs before deletion
                //then for undo, put them back

                undoDelete(delDocMinHeapAccessor.get(), (DocumentImpl) o);
                Set<String> v= o.getWords();
                for(String k: v){
                    docsTrie.put(k,delDocTrieAccessor);
                }

            });
            newComSet.addCommand(newC);
        }
        for(URI uri: t){
            btreeEntries.remove(uri);
        }
        commandStack.push(newComSet);
        return t;


    }

    @Override
    public Set<URI> deleteAllWithPrefixAndMetadata(String keywordPrefix, Map<String, String> keysValues) throws IOException{


        //Do a searchByPrefix() - returns List<Document>
        //Do a searchByMetadata() - returns List<Document>

        //for all Documents returned by the searchByPrefix, if they are in the list returned by the searchByMetadata(),
        //      add to wittled down list

        //Delete all Documents in wittled down list, and return a set of their URIs

        List<Document> prefixAndMeta = new ArrayList<>();
        List<Document> prefixSearch = NoReheapSearchByPrefix(keywordPrefix); //NoReheapSearchByPrefix()
        List<Document> metaSearch = NoReheapSearchByMetadata(keysValues);       //NoReheapSearchByMetadata()

        for(Document a : prefixSearch){
            if(metaSearch.contains(a) || hasURI(metaSearch,a)){
                prefixAndMeta.add(a);
            }
        }
        //No there's a list of docs with the matching keys and metadata

        CommandSet<URI> newComSet = new CommandSet<>();

        Set<URI> t = new HashSet<>();
        for(Document o : prefixAndMeta){
            AtomicReference<BTreeAccessor> delDocMinHeapAccessor = new AtomicReference<>(getSpecificMinHeapAccessor(o.getKey()));
            BTreeAccessor delDocTrieAccessor = getSpecificTrieAccessor(o.getKey());
            deleteUriForCommandSet(o.getKey()); //delete from HashMap and trie
            t.add(o.getKey()); //add to returned Set of deleted URIs

            //deleteSpecificDocFromHeap(o); Don't need this b/c deleteUriForCommandSet does it
            //setNanoAndReHeap(o);

            GenericCommand newC = new GenericCommand(o.getKey(), (u) -> {

                if(delDocMinHeapAccessor.get() ==null){
                    delDocMinHeapAccessor.set(ultimateMinHeapAccessRecords.get(o.getKey()));
                }

                undoDelete(delDocMinHeapAccessor.get(),(DocumentImpl) o);
                Set<String> v= o.getWords();
                for(String k: v){
                    docsTrie.put(k,delDocTrieAccessor);
                }

            });
            newComSet.addCommand(newC);

        }
        for(URI uri: t){
            btreeEntries.remove(uri);
        }
        commandStack.push(newComSet);
        return t;

    }

    private int getTotalMemory(){
        int totalMemory=0;

        Collection<Document> documentsInStore= valuesInMemory();

        for(Document i: documentsInStore){

            int docMemory;
            if(i.getDocumentTxt()==null){ //BINARY doc
                docMemory =i.getDocumentBinaryData().length;
            }
            else{ //TXT doc
                docMemory =i.getDocumentTxt().getBytes().length;
            }

            totalMemory+=docMemory;
            //totalMemory+=i.getDocumentBinaryData().length;
        }
        return totalMemory;
    }

    private void deleteDocFromCommands(URI url){
        Stack<Undoable> tempStack = new StackImpl<>();
        /*if (commandStack.peek() == null) { //nothing in stack
            throw new IllegalStateException();
        }*/
        while (commandStack.peek() != null) { //running thru stack
            if (commandStack.peek() instanceof GenericCommand<?>) {
                if (((GenericCommand<URI>) commandStack.peek()).getTarget() != url) {
                    tempStack.push(commandStack.peek()); //PUT COMMANDS IN TEMP STACK
//                    commandStack.pop();
                } //else- Same URI... pop() but don't push to tempstack
                commandStack.pop();
            }
            if (commandStack.peek() instanceof CommandSet<?>) { //doc DOES NOT exist in a GenericCommand of the CommandSet
                CommandSet<URI> newComSet = null;
                if(!((CommandSet<URI>) commandStack.peek()).containsTarget(url)){
                    tempStack.push(commandStack.peek());
                }
                //commandStack.pop(); //Just remove specific Doc from CommandSet
                else { //doc DOES exist in a GenericCommand of the CommandSet- so find and extract it
                    newComSet = new CommandSet<>();
                    Iterator<GenericCommand<URI>> iterator= ((CommandSet<URI>) commandStack.peek()).iterator();
                    while (iterator.hasNext()){
                        GenericCommand<URI> nextCommand= iterator.next();
                        if(nextCommand.getTarget() !=url){
                            newComSet.addCommand(nextCommand);
                            //newComSet.add(iterator.next());
                        }
                    }
                }

                //the problem is i can't add to a commandSet. I can only create one

                commandStack.pop();
                if(newComSet!=null){
                    tempStack.push(newComSet);
                }

                //create new CommandSet
                //iterate thru all commands
                    //if it DOES NOT have the Document, add to new CommandSet
                    //if it HAS the Document, DO NOT add to new Command Set
                //push the new CommandSet to the tempStack (if it's not empty)
            }
        }
        while (tempStack.peek() != null) { //push all back to commandStack
            commandStack.push(tempStack.peek());
            tempStack.pop(); //take off top of tempStack
        }
    }

    private void checkOverLimit() {
        if (maxDocCountHasBeenSet) {
            while(size()>this.maxDocumentCount){
                BTreeAccessor c = minHeap.remove(); //remove from minHeap
                //doc.put(c.get().getKey(),null);//delete from HashTable
                deleteDocFromCommands(c.get().getKey());

                Set<String> r = c.get().getWords();
                for (String h : r) { //for all words in the doc
                    docsTrie.delete(h, getSpecificTrieAccessor(c.get().getKey())); //delete the specific doc from each word in the doc in the trie
                }

                docToDisk(c);
            }
        }
        if (maxDocBytesHaveBeenSet) {
            while(getTotalMemory()>this.maxDocumentBytes){
                BTreeAccessor c = minHeap.remove(); //remove from minHeap
                //doc.put(c.get().getKey(),null);//delete from HashTable
                deleteDocFromCommands(c.get().getKey());

                Set<String> r = c.get().getWords();
                for (String h : r) { //for all words in the doc
                    docsTrie.delete(h, getSpecificTrieAccessor(c.get().getKey())); //delete the specific doc from each word in the doc in the trie
                }

                docToDisk(c);
            }
        }
    }

    private void docToDisk(DocumentImpl c) {


        try {
            diskDocs.push(c);
            doc.moveToDisk(c.getKey()); //move to disk INSTEAD of deleting
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    private void docToDisk(BTreeAccessor c) {

        ultimateMinHeapAccessRecords.put(c.get().getKey(),c);
        ultimateTrieAccessRecords.put(c.get().getKey(),c);
        try {
            diskDocs.push(c.get());
            doc.moveToDisk(c.get().getKey()); //move to disk INSTEAD of deleting
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setMaxDocumentCount(int limit) {
        if(limit<1){
            throw new IllegalArgumentException();
        }

        this.maxDocumentCount=limit;
        maxDocCountHasBeenSet=true;
        //in get(), put(), etc. check this.
        //      If false, just do action.
        //      If true, check limit and act accordingly!

        while(size()>limit){
            BTreeAccessor c = minHeap.remove(); //remove from minHeap
            //doc.put(c.get().getKey(),null);//delete from HashTable
            deleteDocFromCommands(c.get().getKey());

            Set<String> r = c.get().getWords();
            for (String h : r) { //for all words in the doc
                docsTrie.delete(h, getSpecificTrieAccessor(c.get().getKey())); //delete the specific doc from each word in the doc in the trie
            }
            docToDisk(c);
        }

    }


    @Override
    public void setMaxDocumentBytes(int limit) {
        if(limit<1){
            throw new IllegalArgumentException();
        }
        this.maxDocumentBytes=limit;
        maxDocBytesHaveBeenSet=true;
        //in get(), put(), etc. check this.
        //      If false, just do action.
        //      If true, check limit and act accordingly!

        while(getTotalMemory()>limit){
            BTreeAccessor c = minHeap.remove(); //remove from minHeap
            //doc.put(c.get().getKey(),null);//delete from HashTable
            deleteDocFromCommands(c.get().getKey());

            Set<String> r = c.get().getWords();
            for (String h : r) { //for all words in the doc
                docsTrie.delete(h, getSpecificTrieAccessor(c.get().getKey())); //delete the specific doc from each word in the doc in the trie
            }
            docToDisk(c);
        }
    }

    private void setNanoAndReHeap(BTreeAccessor x){
        if(x==null){
            return;
        }

        x.setLastUseTime(System.nanoTime()); //update nanoTime

        DocumentImpl theDoc = bTreeGet(doc,x.get().getKey(),false);
        if(theDoc!=null){ //if Document exists in the store (and therefore the minHeap)
            minHeap.reHeapify(x); //reHeap
        }


    }

    private TrieImpl<BTreeAccessor> getDocsTrie() {
        return docsTrie;
    }
}
