package edu.yu.cs.com1320.project.impl;

import edu.yu.cs.com1320.project.BTree;

import edu.yu.cs.com1320.project.stage6.impl.DocumentImpl;
import edu.yu.cs.com1320.project.stage6.impl.DocumentPersistenceManager;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assertions.*;

public class BTreeImplTest {

    BTree<Integer,Integer> btree = new BTreeImpl<>();

    @Test
    public void http() {

        String uriBegin ="http:\\\\";
        System.out.println(uriBegin);
        String uriBegin2 = "http:\\"+"\\";
        assertEquals(uriBegin,uriBegin2);

    }

    @Test
    public void start() throws URISyntaxException, IOException {

        BTreeImpl<URI, DocumentImpl> doc = new BTreeImpl<>(); //stores documents
        File folder = new File(System.getProperty("user.dir")); //regular base directory
        DocumentPersistenceManager<URI,DocumentImpl> docPersManager = new DocumentPersistenceManager<>(folder);
        doc.setPersistenceManager(docPersManager);

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





        /*btree.put(1,11);
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
        assertEquals(88, btree.get(8));*/
    }

    @Test
    public void sentinel() throws URISyntaxException {


        btree.put(3,33);
        btree.put(5,55);
        btree.put(7,77);
        btree.put(2,22);
        btree.put(6,66);
        btree.put(4,44);
        btree.put(8,88);
        btree.put(1,11);

        assertEquals(11, btree.get(1));
        assertEquals(77, btree.get(7));
        assertEquals(88, btree.get(8));

    }
}
