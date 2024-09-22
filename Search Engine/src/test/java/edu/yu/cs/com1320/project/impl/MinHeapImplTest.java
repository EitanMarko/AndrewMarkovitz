package edu.yu.cs.com1320.project.impl;

import edu.yu.cs.com1320.project.stage6.DocumentStore;
import edu.yu.cs.com1320.project.stage6.impl.DocumentImpl;
import edu.yu.cs.com1320.project.stage6.impl.DocumentStoreImpl;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class MinHeapImplTest {


    URI uri1= new URI("one://exale.com:8042/oer/tre?nm=fet#nos");
    DocumentImpl doc1= new DocumentImpl(uri1, "words", null);
    String doc1Words= "words";

    URI uri2= new URI("two://exale.com:8042/oer/tre?nm=fet#nos");
    DocumentImpl doc2= new DocumentImpl(uri2, "words and words", null);
    String doc2Words= "words and words";

    URI uri3= new URI("three://exale.com:8042/oer/tre?nm=fet#nos");
    DocumentImpl doc3= new DocumentImpl(uri3, "words and words and words", null);
    String doc3Words= "words and words and words";

    URI uri4= new URI("four://exale.com:8042/oer/tre?nm=fet#nos");
    DocumentImpl doc4= new DocumentImpl(uri4, "words and words and words and words", null);
    String doc4Words= "words and words and words and words";

    URI uri5= new URI("five://exale.com:8042/oer/tre?nm=fet#nos");
    DocumentImpl doc5= new DocumentImpl(uri5, "words and words and words and words and words", null);
    String doc5Words= "words and words and words and words and words";

    byte[] byties = doc1Words.getBytes();
    //byte[] byties2 = { 6, 2, 3, 4, 5 };
    byte[] byties2 = doc2Words.getBytes();
    //        byte[] byties3 = { 7, 2, 3, 4, 5 };
    byte[] byties3 = doc3Words.getBytes();
    //        byte[] byties4 = { 8, 2, 3, 4, 5 };
    byte[] byties4 = doc4Words.getBytes();
    //        byte[] byties5 = { 9, 2, 3, 4, 5 };
    byte[] byties5 = doc5Words.getBytes();
    InputStream one = new ByteArrayInputStream(byties);
    InputStream two = new ByteArrayInputStream(byties2);
    InputStream three = new ByteArrayInputStream(byties3);
    InputStream four = new ByteArrayInputStream(byties4);
    InputStream five = new ByteArrayInputStream(byties5);

    public MinHeapImplTest() throws URISyntaxException {
    }


    /*@Test
    public void getURItest() throws IOException, URISyntaxException {

        DocumentStoreImpl store = new DocumentStoreImpl();
        store.put(one, uri1, DocumentStore.DocumentFormat.TXT);
        store.put(two, uri2, DocumentStore.DocumentFormat.TXT);
        store.put(three, uri3, DocumentStore.DocumentFormat.BINARY);
        store.put(four, uri4, DocumentStore.DocumentFormat.TXT);
        store.put(five, uri5, DocumentStore.DocumentFormat.TXT);

        store.get(uri3);
        store.get(uri1);
        store.get(uri5);
        store.get(uri2);
        store.get(uri4);

        store.setMaxDocumentCount(4);

        assertNull(store.get(uri3));
        assertEquals(doc1, store.get(uri1));
        assertEquals(doc2, store.get(uri2));
        assertEquals(doc4, store.get(uri4));
        assertEquals(doc5, store.get(uri5));

        store.setMaxDocumentCount(2);

        assertNull(store.get(uri3));
        assertNull(store.get(uri1));
        assertNull(store.get(uri2));
        assertEquals(doc4, store.get(uri4));
        assertEquals(doc5, store.get(uri5));

        store.setMaxDocumentCount(4);

        assertNull(store.get(uri3));
        assertNull(store.get(uri1));
        assertNull(store.get(uri2));
        assertEquals(doc4, store.get(uri4));
        assertEquals(doc5, store.get(uri5));

        store.setMaxDocumentCount(1);

        assertNull(store.get(uri3));
        assertNull(store.get(uri1));
        assertNull(store.get(uri2));
        assertNull(store.get(uri4));
        assertEquals(doc5, store.get(uri5));

    }*/


}
