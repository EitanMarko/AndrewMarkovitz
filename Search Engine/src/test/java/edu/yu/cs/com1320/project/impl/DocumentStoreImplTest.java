package edu.yu.cs.com1320.project.impl;
import edu.yu.cs.com1320.project.stage6.Document;
import edu.yu.cs.com1320.project.stage6.DocumentStore;
import edu.yu.cs.com1320.project.stage6.impl.*;
import org.junit.jupiter.api.Test;


import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class DocumentStoreImplTest {

    BTreeImpl<URI, DocumentImpl> btree = new BTreeImpl<>(); //stores documents

    File notBaseDir = new File(System.getProperty("user.dir")+"/urf");
    DocumentStoreImpl doc = new DocumentStoreImpl(notBaseDir);
    DocumentStoreImpl docStore = new DocumentStoreImpl();

    URI uri1Sentinel = new URI("http://edu.yu.cs/num/aaa");
    String doc1Words= "num aaa";
    byte[] byties1 = doc1Words.getBytes();
    InputStream input1 = new ByteArrayInputStream(byties1);

    URI uri2 = new URI("http://edu.yu.cs/num/bbb");
    String doc2Words= "num bbb";
    byte[] byties2 = doc2Words.getBytes();
    InputStream input2 = new ByteArrayInputStream(byties2);

    URI uri3 = new URI("http://edu.yu.cs/num/ccc");
    String doc3Words= "num ccc eitan";
    byte[] byties3 = doc3Words.getBytes();
    InputStream input3 = new ByteArrayInputStream(byties3);
    URI uri4 = new URI("http://edu.yu.cs/num/ddd");
    String doc4Words= "num ddd";
    byte[] byties4 = doc4Words.getBytes();
    InputStream input4 = new ByteArrayInputStream(byties4);


    URI uri5 = new URI("http://edu.yu.cs/num/eee");
    String doc5Words= "num eee eitan";
    byte[] byties5 = doc5Words.getBytes();
    InputStream input5 = new ByteArrayInputStream(byties5);

    URI uri6 = new URI("http://edu.yu.cs/num/fff");
    String doc6Words= "num fff";
    byte[] byties6 = doc6Words.getBytes();
    InputStream input6 = new ByteArrayInputStream(byties6);


    URI uri7 = new URI("http://edu.yu.cs/num/ggg");
    String doc7Words= "num ggg eitan";
    byte[] byties7 = doc7Words.getBytes();
    InputStream input7 = new ByteArrayInputStream(byties7);


    URI uri8 = new URI("http://edu.yu.cs/num/hhh");
    String doc8Words= "num hhh";
    byte[] byties8 = doc8Words.getBytes();
    InputStream input8 = new ByteArrayInputStream(byties8);

    URI uri9 = new URI("http://edu.yu.cs/num/iii");
    String doc9Words= "num iii";
    byte[] byties9 = doc9Words.getBytes();
    InputStream input9 = new ByteArrayInputStream(byties9);

    DocumentImpl sentinelOne = new DocumentImpl(uri1Sentinel,"num aaa", null);
    DocumentImpl two = new DocumentImpl(uri2,"num bbb", null);
    DocumentImpl oneTwo = new DocumentImpl(uri1Sentinel,"num bbb", null);
    DocumentImpl three = new DocumentImpl(uri3,"num ccc", null);
    DocumentImpl threeFive = new DocumentImpl(uri3,"num eee", null);
    DocumentImpl four = new DocumentImpl(uri4,"num ddd", null);
    DocumentImpl five = new DocumentImpl(uri5,"num eee", null);
    DocumentImpl six = new DocumentImpl(uri6,"num fff", null);
    DocumentImpl seven = new DocumentImpl(uri7,"num ggg yep", null);
    DocumentImpl eight = new DocumentImpl(uri8,"num hhh yep", null);
    DocumentImpl nine = new DocumentImpl(uri9,"num iii", null);

    public DocumentStoreImplTest() throws URISyntaxException {
    }

    //KeySet() method works

    @Test
    public void privateMethod() throws IOException {

        //TEST LINE 124

        doc.put(input1,uri1Sentinel, DocumentStore.DocumentFormat.TXT);
        doc.put(input3,uri3, DocumentStore.DocumentFormat.BINARY);
        doc.put(input5,uri5, DocumentStore.DocumentFormat.TXT);
        doc.put(input7,uri7, DocumentStore.DocumentFormat.TXT);
        doc.put(input2,uri2, DocumentStore.DocumentFormat.TXT);
        doc.put(input6,uri6, DocumentStore.DocumentFormat.TXT); //WHY IS THIS GIVING ME ISSUES???
        doc.put(input4,uri4, DocumentStore.DocumentFormat.TXT);
        doc.put(input9,uri9, DocumentStore.DocumentFormat.TXT);
        doc.put(input8,uri8, DocumentStore.DocumentFormat.TXT);

        //doc.get(uri3).getDocumentTxt().getBytes();
    //    System.out.println(doc.get(uri4).getDocumentBinaryData());
    //    System.out.println(doc.get(uri3).getDocumentBinaryData());

        //InputStream input = new ByteArrayInputStream(null);

        //setMetadata
        doc.setMaxDocumentCount(6);

        List<Document> searched = doc.search("fiveball");

        /*doc.put(uri1,one);
        doc.put(uri3,three);
        doc.put(uri5,five);
        doc.put(uri7,seven);
        doc.put(uri2,two);
        doc.put(uri6,six);
        doc.put(uri4,four);
        doc.put(uri8,eight);*/

        /*assertEquals(nine,doc.get(uri9));
        assertEquals(eight,doc.get(uri8));
        assertEquals(seven,doc.get(uri7));
        assertEquals(six,doc.get(uri6));
        assertEquals(five,doc.get(uri5));
        assertEquals(four,doc.get(uri4));
        assertEquals(three,doc.get(uri3));
        assertEquals(two,doc.get(uri2));
        assertEquals(sentinelOne,doc.get(uri1Sentinel));*/

        //Set<URI> keyset = doc.keySet();
        //assertEquals(9,keyset.size());

        //Collection<Document> vals = doc.values();
        //assertEquals(9,vals.size());
        //MAKE PRIVATE AGAIN    !!!!!!!


    }


    @Test
    public void deleteAll() throws IOException {

        //TEST LINE 124
        doc.put(input1,uri1Sentinel, DocumentStore.DocumentFormat.TXT);
        doc.put(input2,uri2, DocumentStore.DocumentFormat.TXT);
        doc.put(input3,uri3, DocumentStore.DocumentFormat.TXT);

        /*System.out.println(doc.get(uri1Sentinel));
        System.out.println(doc.get(uri2));
        System.out.println(doc.get(uri3));*/

        doc.setMaxDocumentCount(1);
      //  doc.delete(uri1Sentinel);

      //  doc.deleteAllWithPrefix("nu");
       // doc.deleteAllWithPrefix("nu");


       // doc.deleteAll("num");
       // doc.search("num");
        //doc.deleteAll("num");


        /*assertNull(doc.get(uri1Sentinel));
        assertNull(doc.get(uri2));
        assertNull(doc.get(uri3));*/

        //TWO NOTES:
        //      Do what you did in all delete methods! (Call a btreeEntries.remove() for every URI deleted)
        //      FIX Document constructor anyway to account for empty? (In case of Document created outside of store)



        //NOW DEAL WITH:
        //stage4PlainUndoThatImpactsMultiple
        //stage4UndoMetadataOneDocumentThenSearch
        //stage4UndoByURIThatImpactsOne

        //I suspect all these are now resolved because they revolve around the deleteAll method (I think)
        //Make sure nothing in the commit which changed the Document constructor caused any issues
            //Idea was for you to be able to create a document with a wordCountMap if you
            //passed into it a blank map

        //FINAL STEP: Undos- even the ones he didn't test for
            //Should be able to just copy what you did for undo deleteAll()

    }

    @Test
    public void TestReplaceURI() throws IOException {

        //TEST LINE 124

        DocumentImpl newFive = new DocumentImpl(uri3,"num eee", null);

        doc.put(input1,uri1Sentinel, DocumentStore.DocumentFormat.TXT);
        doc.put(input3,uri3, DocumentStore.DocumentFormat.TXT);
        doc.put(input5,uri3, DocumentStore.DocumentFormat.TXT);
        doc.put(input7,uri7, DocumentStore.DocumentFormat.TXT);
        doc.put(input2,uri2, DocumentStore.DocumentFormat.TXT);
        doc.put(input6,uri6, DocumentStore.DocumentFormat.TXT); //WHY IS THIS GIVING ME ISSUES???
        doc.put(input4,uri4, DocumentStore.DocumentFormat.TXT);
        doc.put(input9,uri9, DocumentStore.DocumentFormat.TXT);
        doc.put(input8,uri8, DocumentStore.DocumentFormat.TXT);






        /*doc.put(uri1,one);
        doc.put(uri3,three);
        doc.put(uri5,five);
        doc.put(uri7,seven);
        doc.put(uri2,two);
        doc.put(uri6,six);
        doc.put(uri4,four);
        doc.put(uri8,eight);*/

        assertEquals(nine,doc.get(uri9));
        assertEquals(eight,doc.get(uri8));
        assertEquals(seven,doc.get(uri7));
        assertEquals(six,doc.get(uri6));
        assertEquals(newFive,doc.get(uri3));
        assertEquals(four,doc.get(uri4));
        assertEquals(three,doc.get(uri3));
        assertEquals(two,doc.get(uri2));
        assertEquals(sentinelOne,doc.get(uri1Sentinel));


        //OBSERVATION:
        //If a Document is put with an existing URI, it will have the URI it was put with, and the text it has
        //  For example, inputstream five was put with uri3, so the resulting Document has URI: num/ccc && text: "num eee"

        //Set<URI> keyset = doc.keySet();
        //assertEquals(8,keyset.size());

        //Collection<Document> vals = doc.values();
        //assertEquals(8,vals.size());

    }

    @Test
    public void bogusURI() throws URISyntaxException {

        URI bogusUri = new URI("rfdv/re");
        DocumentImpl x = btree.get(bogusUri);
        assertNull(x);


    }


    @Test
    public void putTest() throws URISyntaxException, IOException {

        File notBaseDir = new File(System.getProperty("user.dir")+"/urf");
        DocumentStoreImpl doc = new DocumentStoreImpl(notBaseDir);

        //TXT PUT
        doc.put(input3,uri3, DocumentStore.DocumentFormat.TXT);
        assertEquals(three,doc.get(uri3));
//        doc.undo();                                     //WHY DOES THIS CREATE RANDOM EMPTY DIRECTORY??????????????????????????
        //assertEquals(three,doc.get(uri3));
 //       assertNull(doc.get(uri3));

        //TXT PUT REPLACE URI
        doc.put(input5,uri3, DocumentStore.DocumentFormat.TXT);
        assertEquals(threeFive,doc.get(uri3));
//        doc.undo();                                        //NO RANDOM EMPTY DIRECTORY

        //BINARY PUT
        doc.put(input8,uri8, DocumentStore.DocumentFormat.TXT);
        assertEquals(eight,doc.get(uri8));
//        doc.undo();                                          //WHY DOES THIS CREATE RANDOM EMPTY DIRECTORY??????????????????????????
 //       assertNull(doc.get(uri8));

        //BINARY PUT REPLACE URI
        doc.put(input2,uri1Sentinel, DocumentStore.DocumentFormat.TXT);
        assertEquals(oneTwo,doc.get(uri1Sentinel));
//        doc.undo();
 //       assertNull(doc.get(uri1Sentinel));


        //PUT OVER LIMIT & UNDO - BINARY AND TXT
        doc.setMaxDocumentCount(2);
   /*
        doc.put(input7,uri7, DocumentStore.DocumentFormat.TXT);
 //       doc.put(input7,uri7, DocumentStore.DocumentFormat.BINARY);
        doc.undo();
        assertNull(doc.get(uri7));*/

        List<Document> searched = doc.search("fiveball");

        doc.setMaxDocumentCount(2);
        assertEquals(oneTwo,doc.get(uri1Sentinel));

        doc.deleteAll("eee");

     //   assertNull(doc.get(uri1Sentinel));



        //doc.setMaxDocumentCount(6);

    }

    @Test
    public void putTestReverted() throws URISyntaxException, IOException {

        File notBaseDir = new File(System.getProperty("user.dir")+"/urf");
        DocumentStoreImpl doc = new DocumentStoreImpl(notBaseDir);

        //TXT PUT
        doc.put(input3,uri3, DocumentStore.DocumentFormat.TXT);
        assertEquals(three,doc.get(uri3));
//        doc.undo();                                     //WHY DOES THIS CREATE RANDOM EMPTY DIRECTORY??????????????????????????
        //assertEquals(three,doc.get(uri3));
        //       assertNull(doc.get(uri3));

        //TXT PUT REPLACE URI
        doc.put(input5,uri3, DocumentStore.DocumentFormat.TXT);
        assertEquals(threeFive,doc.get(uri3));
//        doc.undo();                                        //NO RANDOM EMPTY DIRECTORY

        //BINARY PUT
        doc.put(input8,uri8, DocumentStore.DocumentFormat.TXT);
        assertEquals(eight,doc.get(uri8));
//        doc.undo();                                          //WHY DOES THIS CREATE RANDOM EMPTY DIRECTORY??????????????????????????
        //       assertNull(doc.get(uri8));

        //BINARY PUT REPLACE URI
        doc.put(input2,uri1Sentinel, DocumentStore.DocumentFormat.TXT);
        assertEquals(oneTwo,doc.get(uri1Sentinel));
//        doc.undo();
        //       assertNull(doc.get(uri1Sentinel));


        //PUT OVER LIMIT & UNDO - BINARY AND TXT
        doc.setMaxDocumentCount(2);
   /*
        doc.put(input7,uri7, DocumentStore.DocumentFormat.TXT);
 //       doc.put(input7,uri7, DocumentStore.DocumentFormat.BINARY);
        doc.undo();
        assertNull(doc.get(uri7));*/

        List<Document> searched = doc.search("fiveball");
        /*for(Document v:searched){
            for(String word: v.getWordMap().keySet()){
                System.out.println(word);
            }
            System.out.println("\n");
        }*/

        doc.setMaxDocumentCount(2);
        assertEquals(oneTwo,doc.get(uri1Sentinel));

        doc.deleteAll("eee");

        //   assertNull(doc.get(uri1Sentinel));



        //doc.setMaxDocumentCount(6);

    }



    @Test
    public void metadata() throws IOException {

        File notBaseDir = new File(System.getProperty("user.dir")+"/urf");
        DocumentStoreImpl doc = new DocumentStoreImpl(notBaseDir);
        //TEST LINE 124

        //doc.setMaxDocumentCount(8);
        //doc.setMaxDocumentBytes(55);

        doc.put(input1,uri1Sentinel, DocumentStore.DocumentFormat.TXT);
        doc.setMetadata(uri1Sentinel,"has","metadata");

        doc.put(input2,uri2, DocumentStore.DocumentFormat.TXT);
        doc.setMetadata(uri2,"has","metadata");

        doc.put(input3,uri3, DocumentStore.DocumentFormat.TXT);
        doc.setMetadata(uri3,"has","metadata");
        doc.setMetadata(uri3,"hello","there");

        doc.put(input4,uri4, DocumentStore.DocumentFormat.TXT);
        doc.setMetadata(uri4,"has","metadata");

        doc.put(input5,uri5, DocumentStore.DocumentFormat.TXT);
        doc.setMetadata(uri5,"bring","back5");
        doc.setMetadata(uri5,"has","metadata");
        doc.setMetadata(uri5,"hello","there");

        doc.put(input6,uri6, DocumentStore.DocumentFormat.TXT);
        doc.setMetadata(uri6,"has","metadata");

        doc.put(input7,uri7, DocumentStore.DocumentFormat.TXT);
        doc.setMetadata(uri7,"has","metadata");
        doc.setMetadata(uri7,"hello","there");

        doc.put(input8,uri8, DocumentStore.DocumentFormat.TXT);
        doc.setMetadata(uri8,"has","metadata");

        doc.put(input9,uri9, DocumentStore.DocumentFormat.TXT);
        doc.setMetadata(uri9,"has","metadata");

 //       doc.setMaxDocumentCount(6);
        //doc.setMaxDocumentBytes(60);

     //   DocumentImpl sevenHere = (DocumentImpl) doc.get(uri7,false);

     //   List<Document> searched = doc.search("eitan");

    //    DocumentImpl sevenBefore = seven;
     //   DocumentImpl sevenAfter = (DocumentImpl) doc.get(uri7,false);
     //   assertEquals(sevenHere,sevenAfter);


        doc.setMaxDocumentCount(6);

        //stage6PushToDiskViaMaxDocCountBringBackInViaMetadataSearch:

        HashMap<String,String> metadataMap = new HashMap<>();
        metadataMap.put("has","metadata");
        metadataMap.put("hello","there");

     //   doc.setMaxDocumentBytes(60);

        //stage6PushToDiskViaMaxBytesBringBackInViaMetadataSearch:
    //    List<Document> metaSearched= doc.searchByMetadata(metadataMap);


    //    stage6PushToDiskViaMaxDocCount: test that documents move to and from disk and memory as expected when the maxdoc count is 2
    //    doc.setMaxDocumentCount(4);

      //  stage6PushToDiskViaMaxDocCountBringBackInViaDeleteAndSearch
        assertEquals(eight,doc.get(uri8));
        doc.delete(uri8);
        assertNull(doc.get(uri8));
    //    doc.search("ccc");
     //   doc.search("ddd");
     //   doc.search("eee");
     //   doc.search("fff");




       // stage6PushToDiskViaMaxDocCountViaUndoDelete

        //Test description: test that documents move to and from disk and memory as expected when
        //      a doc is deleted then
        //      another is added to memory
        //      then the delete is undone
        //      causing another doc to be pushed out to disk

        //TEST FAILED
        //TEST FAILURE MESSAGES: doc1 should've been written out to disk, but was not:
        // contents were null ==> expected: not <null>
        // --  org.opentest4j.AssertionFailedError:
        //          doc1 should've been written out to disk, but was not:
        //              contents were null ==> expected: not <null>

        doc.undo(uri8);
        assertEquals(eight,doc.get(uri8));





      //  Set<URI> deletedMeta = doc.deleteAllWithMetadata(metadataMap);

     //   Collection<Document> values = doc.values();
     //   Collection<Document> valuesInMemory = doc.valuesInMemory();
        String hello = "hello";




        //DONE:
        //stage6PushToDiskViaMaxDocCountBringBackInViaMetadataSearch
        //stage6PushToDiskViaMaxBytesBringBackInViaMetadataSearch
        //stage6PushToDiskViaMaxDocCount
        //stage6PushToDiskViaMaxDocCountBringBackInViaDeleteAndSearch

        //TO DO:
        //stage4DeleteAllWithPrefix - 1 point
        //stage4DeleteAllWithMetadata- 1 point
        //stage4DeleteAllTxt- 1 point
        //stage4DeleteAllWithPrefixAndMetadata- 1 point
        //undoNthDeleteByURI- 1 point - check back on this
        //stage4PlainUndoThatImpactsMultiple- 1 point
        //stage4UndoMetadataOneDocumentThenSearch - 1 point
        //stage4UndoByURIThatImpactsEarlierThanLast- 1 point
        //stage4UndoByURIThatImpactsOne- 1 point
        //undoOverwriteByURI- 1 point


    }

    @Test
    public void undoOverwriteByURI() throws IOException {

        File notBaseDir = new File(System.getProperty("user.dir")+"/urf");
        DocumentStoreImpl doc = new DocumentStoreImpl(notBaseDir);

        doc.setMaxDocumentCount(1);

        doc.put(input1,uri1Sentinel, DocumentStore.DocumentFormat.TXT);
        System.out.println(doc.get(uri1Sentinel).getDocumentTxt());

        doc.put(input3,uri3, DocumentStore.DocumentFormat.TXT);
        doc.put(input2,uri1Sentinel, DocumentStore.DocumentFormat.TXT);
        System.out.println(doc.get(uri1Sentinel).getDocumentTxt());

        doc.undo();
        System.out.println(doc.get(uri1Sentinel).getDocumentTxt());
    }

    @Test
    public void undoOverwriteByURIbinary() throws IOException {

        File notBaseDir = new File(System.getProperty("user.dir")+"/urf");
        DocumentStoreImpl doc = new DocumentStoreImpl(notBaseDir);

        doc.setMaxDocumentCount(1);

        doc.put(input1,uri1Sentinel, DocumentStore.DocumentFormat.BINARY);
        byte[] originalBinary = doc.get(uri1Sentinel).getDocumentBinaryData();
        System.out.println(originalBinary);


        doc.put(input3,uri3, DocumentStore.DocumentFormat.BINARY);
        doc.put(input2,uri1Sentinel, DocumentStore.DocumentFormat.BINARY);
        byte[] newBinary = doc.get(uri1Sentinel).getDocumentBinaryData();
        System.out.println(newBinary);

        doc.undo();
        byte[] undoBinary = doc.get(uri1Sentinel).getDocumentBinaryData();
        System.out.println(undoBinary);
        //assertEquals(originalBinary,undoBinary);
        assertArrayEquals(originalBinary, undoBinary);
    }

    @Test
    public void undoDeleteAll() throws IOException {

        File notBaseDir = new File(System.getProperty("user.dir")+"/urf");
        DocumentStoreImpl doc = new DocumentStoreImpl(notBaseDir);
        doc.setMaxDocumentCount(1);

        doc.put(input1,uri1Sentinel, DocumentStore.DocumentFormat.TXT);
        doc.put(input2,uri2, DocumentStore.DocumentFormat.TXT);
        doc.put(input3,uri3, DocumentStore.DocumentFormat.TXT);
        System.out.println(doc.get(uri1Sentinel).getDocumentTxt());
        System.out.println(doc.get(uri2).getDocumentTxt());
        System.out.println(doc.get(uri3).getDocumentTxt());

        doc.deleteAll("num");
        assertNull(doc.get(uri1Sentinel));
        assertNull(doc.get(uri2));
        assertNull(doc.get(uri3));


        doc.undo();
        System.out.println("\nDocs are back:\n");
        System.out.println(doc.get(uri1Sentinel).getDocumentTxt());
        System.out.println(doc.get(uri2).getDocumentTxt());
        System.out.println(doc.get(uri3).getDocumentTxt());



    }

    @Test
    public void undoDeleteAllWithPrefix() throws IOException {

        File notBaseDir = new File(System.getProperty("user.dir")+"/urf");
        DocumentStoreImpl doc = new DocumentStoreImpl(notBaseDir);
        doc.setMaxDocumentCount(1);


        doc.put(input1,uri1Sentinel, DocumentStore.DocumentFormat.TXT);
        doc.put(input2,uri2, DocumentStore.DocumentFormat.TXT);
        doc.put(input3,uri3, DocumentStore.DocumentFormat.TXT);
        System.out.println(doc.get(uri1Sentinel).getDocumentTxt());
        System.out.println(doc.get(uri2).getDocumentTxt());
        System.out.println(doc.get(uri3).getDocumentTxt());

        doc.deleteAllWithPrefix("nu");
        assertNull(doc.get(uri1Sentinel));
        assertNull(doc.get(uri2));
        assertNull(doc.get(uri3));


        doc.undo();
        System.out.println("\nDocs are back:\n");
        System.out.println(doc.get(uri1Sentinel).getDocumentTxt());
        System.out.println(doc.get(uri2).getDocumentTxt());
        System.out.println(doc.get(uri3).getDocumentTxt());

    }

    @Test
    public void undoDeleteAllWithMetadata() throws IOException {

        File notBaseDir = new File(System.getProperty("user.dir")+"/urf");
        DocumentStoreImpl doc = new DocumentStoreImpl(notBaseDir);
        doc.setMaxDocumentCount(2);

        doc.put(input1,uri1Sentinel, DocumentStore.DocumentFormat.TXT);
        doc.setMetadata(uri1Sentinel,"meta","data");

        doc.put(input2,uri2, DocumentStore.DocumentFormat.TXT);
        doc.setMetadata(uri2,"meta","data");

        doc.put(input3,uri3, DocumentStore.DocumentFormat.TXT);
        doc.setMetadata(uri3,"meta","data");

        /*System.out.println(doc.get(uri1Sentinel).getDocumentTxt());
        System.out.println(doc.getMetadata(uri1Sentinel,"meta")+"\n");

        System.out.println(doc.get(uri2).getDocumentTxt());
        System.out.println(doc.getMetadata(uri2,"meta")+"\n");

        System.out.println(doc.get(uri3).getDocumentTxt());
        System.out.println(doc.getMetadata(uri3,"meta")+"\n");*/

        HashMap<String,String> keysVals = new HashMap<>();
        keysVals.put("meta","data");

        //assertTrue(doc.get(uri1Sentinel).getMetadata().containsKey("meta"));
        //assertEquals("data",doc.get(uri1Sentinel).getMetadata().get("meta"));

        Set<URI> x= doc.deleteAllWithMetadata(keysVals);

        for(URI uri:x){
            System.out.println(uri);
        }
        Document a= doc.get(uri1Sentinel);
        Document b= doc.get(uri2);
        Document c= doc.get(uri3);
        assertNull(doc.get(uri1Sentinel));
        assertNull(doc.get(uri2));
        assertNull(doc.get(uri3));


        doc.undo();
        System.out.println("\nDocs are back:\n");
        System.out.println(doc.get(uri1Sentinel).getDocumentTxt());
        System.out.println(doc.get(uri2).getDocumentTxt());
        System.out.println(doc.get(uri3).getDocumentTxt());

    }

    @Test
    public void undoDeleteAllWithKeywordAndMetadata() throws IOException {

        File notBaseDir = new File(System.getProperty("user.dir")+"/urf");
        DocumentStoreImpl doc = new DocumentStoreImpl(notBaseDir);
        doc.setMaxDocumentCount(1);

        doc.put(input1,uri1Sentinel, DocumentStore.DocumentFormat.TXT);
        doc.setMetadata(uri1Sentinel,"meta","data");

        doc.put(input2,uri2, DocumentStore.DocumentFormat.TXT);
        doc.setMetadata(uri2,"meta","data");

        doc.put(input3,uri3, DocumentStore.DocumentFormat.TXT);
        doc.setMetadata(uri3,"meta","data");

        /*System.out.println(doc.get(uri1Sentinel).getDocumentTxt());
        System.out.println(doc.getMetadata(uri1Sentinel,"meta")+"\n");

        System.out.println(doc.get(uri2).getDocumentTxt());
        System.out.println(doc.getMetadata(uri2,"meta")+"\n");

        System.out.println(doc.get(uri3).getDocumentTxt());
        System.out.println(doc.getMetadata(uri3,"meta")+"\n");*/

        HashMap<String,String> keysVals = new HashMap<>();
        keysVals.put("meta","data");

        //assertTrue(doc.get(uri1Sentinel).getMetadata().containsKey("meta"));
        //assertEquals("data",doc.get(uri1Sentinel).getMetadata().get("meta"));

        Set<URI> x= doc.deleteAllWithKeywordAndMetadata("num",keysVals);

        for(URI uri:x){
            System.out.println(uri);
        }
        Document a= doc.get(uri1Sentinel);
        Document b= doc.get(uri2);
        Document c= doc.get(uri3);
        assertNull(doc.get(uri1Sentinel));
        assertNull(doc.get(uri2));
        assertNull(doc.get(uri3));

        doc.undo();
        System.out.println("\nDocs are back:\n");
        System.out.println(doc.get(uri1Sentinel).getDocumentTxt());
        System.out.println(doc.get(uri2).getDocumentTxt());
        System.out.println(doc.get(uri3).getDocumentTxt());
    }

    @Test
    public void undoDeleteAllWithPrefixAndMetadata() throws IOException {

        File notBaseDir = new File(System.getProperty("user.dir")+"/urf");
        DocumentStoreImpl doc = new DocumentStoreImpl(notBaseDir);
        doc.setMaxDocumentCount(1);

        doc.put(input1,uri1Sentinel, DocumentStore.DocumentFormat.TXT);
        doc.setMetadata(uri1Sentinel,"meta","data");

        doc.put(input2,uri2, DocumentStore.DocumentFormat.TXT);
        doc.setMetadata(uri2,"meta","data");

        doc.put(input3,uri3, DocumentStore.DocumentFormat.TXT);
        doc.setMetadata(uri3,"meta","data");

        /*System.out.println(doc.get(uri1Sentinel).getDocumentTxt());
        System.out.println(doc.getMetadata(uri1Sentinel,"meta")+"\n");

        System.out.println(doc.get(uri2).getDocumentTxt());
        System.out.println(doc.getMetadata(uri2,"meta")+"\n");

        System.out.println(doc.get(uri3).getDocumentTxt());
        System.out.println(doc.getMetadata(uri3,"meta")+"\n");*/

        HashMap<String,String> keysVals = new HashMap<>();
        keysVals.put("meta","data");

        //assertTrue(doc.get(uri1Sentinel).getMetadata().containsKey("meta"));
        //assertEquals("data",doc.get(uri1Sentinel).getMetadata().get("meta"));

        Set<URI> x= doc.deleteAllWithPrefixAndMetadata("num",keysVals);

        for(URI uri:x){
            System.out.println(uri);
        }
        Document a= doc.get(uri1Sentinel);
        Document b= doc.get(uri2);
        Document c= doc.get(uri3);
        assertNull(doc.get(uri1Sentinel));
        assertNull(doc.get(uri2));
        assertNull(doc.get(uri3));

        doc.undo();
        System.out.println("\nDocs are back:\n");
        System.out.println(doc.get(uri1Sentinel).getDocumentTxt());
        System.out.println(doc.get(uri2).getDocumentTxt());
        System.out.println(doc.get(uri3).getDocumentTxt());
    }

    @Test
    public void stage6PushToDiskViaMaxDocCountViaUndoDelete() throws IOException {

        File notBaseDir = new File(System.getProperty("user.dir")+"/urf");
        DocumentStoreImpl doc = new DocumentStoreImpl(notBaseDir);
        //TEST LINE 124

        //doc.setMaxDocumentCount(8);
        //doc.setMaxDocumentBytes(55);

        doc.setMaxDocumentCount(6);

        doc.put(input1,uri1Sentinel, DocumentStore.DocumentFormat.TXT);
        doc.setMetadata(uri1Sentinel,"has","metadata");

        doc.put(input2,uri2, DocumentStore.DocumentFormat.TXT);
        doc.setMetadata(uri2,"has","metadata");

        doc.put(input3,uri3, DocumentStore.DocumentFormat.TXT);
        doc.setMetadata(uri3,"has","metadata");
        doc.setMetadata(uri3,"hello","there");

        doc.put(input4,uri4, DocumentStore.DocumentFormat.TXT);
        doc.setMetadata(uri4,"has","metadata");

        doc.put(input5,uri5, DocumentStore.DocumentFormat.TXT);
        doc.setMetadata(uri5,"bring","back5");
        doc.setMetadata(uri5,"has","metadata");
        doc.setMetadata(uri5,"hello","there");

        doc.put(input6,uri6, DocumentStore.DocumentFormat.TXT);
        doc.setMetadata(uri6,"has","metadata");

        doc.put(input7,uri7, DocumentStore.DocumentFormat.TXT);
        doc.setMetadata(uri7,"has","metadata");
        doc.setMetadata(uri7,"hello","there");

        doc.put(input8,uri8, DocumentStore.DocumentFormat.TXT);
        doc.setMetadata(uri8,"has","metadata");

//        doc.put(input9,uri9, DocumentStore.DocumentFormat.TXT);
 //       doc.setMetadata(uri9,"has","metadata");



        //Test description: test that documents move to and from disk and memory as expected when
        //      a doc is deleted then
        doc.delete(uri8);
        //      another is added to memory
        doc.put(input9,uri9, DocumentStore.DocumentFormat.TXT);
        doc.setMetadata(uri9,"has","metadata");
        //      then the delete is undone
        doc.undo(uri8);
        //      causing another doc to be pushed out to disk

        //TEST FAILED
        //TEST FAILURE MESSAGES: doc1 should've been written out to disk, but was not:
        // contents were null ==> expected: not <null>
        // --  org.opentest4j.AssertionFailedError:
        //          doc1 should've been written out to disk, but was not:
        //              contents were null ==> expected: not <null>


     //   assertEquals(eight,doc.get(uri8));

    }

    @Test
    public void overLimit() throws IOException, URISyntaxException {


        File notBaseDir = new File(System.getProperty("user.dir")+"/urf");
        DocumentStoreImpl doc = new DocumentStoreImpl(notBaseDir);
        //TEST LINE 124

        //doc.setMaxDocumentCount(8);
        //doc.setMaxDocumentBytes(55);

        doc.put(input1,uri1Sentinel, DocumentStore.DocumentFormat.BINARY);
        doc.put(input3,uri3, DocumentStore.DocumentFormat.TXT);
        doc.setMetadata(uri3,"bring","back3");
        doc.put(input5,uri5, DocumentStore.DocumentFormat.TXT);
        doc.setMetadata(uri5,"bring","back5");
        doc.put(input7,uri7, DocumentStore.DocumentFormat.TXT);
        doc.put(input2,uri2, DocumentStore.DocumentFormat.BINARY);
        doc.put(input6,uri6, DocumentStore.DocumentFormat.TXT); //WHY IS THIS GIVING ME ISSUES???
        doc.put(input4,uri4, DocumentStore.DocumentFormat.TXT);
        doc.put(input9,uri9, DocumentStore.DocumentFormat.TXT);
        doc.put(input8,uri8, DocumentStore.DocumentFormat.TXT);


        assertEquals(seven,doc.get(uri7));
        List<Document> searched = doc.search("num");
        List<Document> prefixSearched = doc.searchByPrefix("nu");

        assertEquals(nine,doc.get(uri9));
        boolean deleted =doc.delete(uri9);
      assertNull(doc.get(uri9));


        /*  Set<URI> deletedAll = doc.deleteAll("num");
        List<Document> searched2 = doc.search("num");
        doc.undo();
        List<Document> searched3 = doc.search("num");

        assertEquals(searched,searched3);
*/
 /*       List<Document> prefixSearched1 = doc.searchByPrefix("nu");
        Set<URI> deletedAllWithPrefix = doc.deleteAllWithPrefix("nu");
        List<Document> prefixSearched2 = doc.searchByPrefix("nu");
        doc.undo();
        List<Document> prefixSearched3 = doc.searchByPrefix("nu");
        assertEquals(prefixSearched1,prefixSearched3);
*/


        doc.setMetadata(uri6,"hello","world");
        doc.setMetadata(uri7,"hello","world"); //BINARY
        doc.setMetadata(uri8,"hello","world");

        doc.setMetadata(uri6,"goodbye","sun");
     //   doc.setMetadata(uri7,"goodbye","sun"); //BINARY
        doc.setMetadata(uri8,"goodbye","sun");

        HashMap<String,String> meta = new HashMap<>();
        meta.put("hello","world");
        meta.put("goodbye","sun");

        List<Document> searchedByMetadata = doc.searchByMetadata(meta);
//        System.out.println(seven.getDocumentTxt());
 //       List<Document> searchedByKeywordAndMetadata = doc.searchByKeywordAndMetadata("num",meta);
  //      List<Document> searchedByPrefixAndMetadata = doc.searchByPrefixAndMetadata("nu",meta);

 /*       List<Document> searchedByMetadata1 = doc.searchByMetadata(meta);
         Set<URI> deletedAllWithMetadata = doc.deleteAllWithMetadata(meta);
        List<Document> searchedByMetadata2 = doc.searchByMetadata(meta);
        doc.undo();
        List<Document> searchedByMetadata3 = doc.searchByMetadata(meta);
        assertEquals(searchedByMetadata1,searchedByMetadata3);
*/
/*

        List<Document> searchedByKeywordAndMetadata1 = doc.searchByKeywordAndMetadata("num",meta);
        Set<URI> deletedAllWithKeywordAndMetadata = doc.deleteAllWithKeywordAndMetadata("num",meta);
        List<Document> searchedByKeywordAndMetadata2 = doc.searchByKeywordAndMetadata("num",meta);
        doc.undo();
        List<Document> searchedByKeywordAndMetadata3 = doc.searchByKeywordAndMetadata("num",meta);
        assertEquals(searchedByKeywordAndMetadata1,searchedByKeywordAndMetadata3);
*/



/*
        List<Document> searchedByPrefixAndMetadata1 = doc.searchByPrefixAndMetadata("nu",meta);
        Set<URI> deletedAllWithPrefixAndMetadata = doc.deleteAllWithPrefixAndMetadata("nu",meta);
        List<Document> searchedByPrefixAndMetadata2 = doc.searchByPrefixAndMetadata("nu",meta);
        doc.undo();
        List<Document> searchedByPrefixAndMetadata3 = doc.searchByPrefixAndMetadata("nu",meta);
        assertEquals(searchedByPrefixAndMetadata1,searchedByPrefixAndMetadata3);


*/




        doc.setMaxDocumentCount(6);
   //     doc.get(uri3);
        doc.setMaxDocumentBytes(55);

        int bytes1to8 = byties2.length+byties3.length +byties4.length+byties5.length+byties6.length+byties7.length+byties8.length+byties1.length;
        int bytes2to9 =  byties2.length+byties3.length +byties4.length+byties5.length+byties6.length+byties7.length+byties8.length+byties9.length;
        int allBytes= bytes1to8+ byties9.length;
        System.out.println(allBytes);


        //doc.setMaxDocumentCount(5);
        assertEquals(five,doc.get(uri5));
        assertEquals(four,doc.get(uri4));
     //   assertNull(doc.get(uri1Sentinel));
//        System.out.println(allBytes);

        //DocumentImpl v = doc.docPersManager.deserialize(uri3);
        //Map map = v.getWordMap();
        //System.out.println(map);



//        System.out.println(doc.getMetadata(uri3,"bring"));
 //       doc.getMetadata(uri3,"bring");
        System.out.println(doc.getMetadata(uri3,"bring"));
 //       doc.undo();
//        System.out.println(doc.get(uri3).getWordMap());
        //System.out.println(doc.getMetadata(uri3,"bring"));

//        doc.get(uri3);


        /*doc.put(uri1,one);
        doc.put(uri3,three);
        doc.put(uri5,five);
        doc.put(uri7,seven);
        doc.put(uri2,two);
        doc.put(uri6,six);
        doc.put(uri4,four);
        doc.put(uri8,eight);*/

        /*assertEquals(nine,doc.get(uri9));
        assertEquals(eight,doc.get(uri8));
        assertEquals(seven,doc.get(uri7));
        assertEquals(six,doc.get(uri6));
        assertEquals(five,doc.get(uri5));
        assertEquals(four,doc.get(uri4));
        assertEquals(three,doc.get(uri3));
        assertEquals(two,doc.get(uri2));
        assertEquals(sentinelOne,doc.get(uri1Sentinel));*/

        //Set<URI> keyset = doc.keySet();
        //assertEquals(9,keyset.size());

        //Collection<Document> vals = doc.values();
        //assertEquals(9,vals.size());
        //MAKE PRIVATE AGAIN    !!!!!!!


    }

    /*@Test
    public void start() throws URISyntaxException, IOException {

        //BTreeImpl<URI, DocumentImpl> doc = new BTreeImpl<>(); //stores documents
        File folder = new File(System.getProperty("user.dir")); //regular base directory
        DocumentPersistenceManager<URI,DocumentImpl> docPersManager = new DocumentPersistenceManager<>(folder);
        //doc.setPersistenceManager(docPersManager);

        URI uriSentinel = new URI("num/aaa");
        URI uri1 = new URI("num/one");
        URI uri2 = new URI("num/two");
        URI uri3 = new URI("num/three");
        URI uri4 = new URI("num/four");
        URI uri5 = new URI("num/five");
        URI uri6 = new URI("num/six");
        URI uri7 = new URI("num/seven");
        URI uri8 = new URI("num/eight");

        DocumentImpl sentinel = new DocumentImpl(uriSentinel,"num aaa", null);
        DocumentImpl one = new DocumentImpl(uri1,"num one", null);
        DocumentImpl two = new DocumentImpl(uri2,"num two", null);
        DocumentImpl three = new DocumentImpl(uri3,"num three", null);
        DocumentImpl four = new DocumentImpl(uri4,"num four", null);
        DocumentImpl five = new DocumentImpl(uri5,"num five", null);
        DocumentImpl six = new DocumentImpl(uri6,"num six", null);
        DocumentImpl seven = new DocumentImpl(uri7,"num seven", null);
        DocumentImpl eight = new DocumentImpl(uri8,"num eight", null);

        doc.put()

        doc.put(uriSentinel,sentinel);
        doc.put(uri1,one);
        doc.put(uri3,three);
        doc.put(uri5,five);
        doc.put(uri7,seven);
        doc.put(uri2,two);
        doc.put(uri6,six);
        doc.put(uri4,four);
        doc.put(uri8,eight);

        assertEquals(eight,doc.get(uri8));
        assertEquals(seven,doc.get(uri7));
        assertEquals(six,doc.get(uri6));
        assertEquals(five,doc.get(uri5));
        assertEquals(four,doc.get(uri4));
        assertEquals(three,doc.get(uri3));
        assertEquals(two,doc.get(uri2));
        assertEquals(one,doc.get(uri1));

        doc.moveToDisk(uri8);
        String hello= "hello";





        *//*btree.put(1,11);
        btree.put(3,33);
        btree.put(5,55);
        btree.put(7,77);
        btree.put(2,22);
        btree.put(6,66);
        btree.put(4,44);
        btree.put(8,88);
        if(1+2==3){
            System.out.println("genius!");
        }

        assertEquals(11, btree.get(1));
        assertEquals(77, btree.get(7));
        assertEquals(88, btree.get(8));*//*
    }*/

}
