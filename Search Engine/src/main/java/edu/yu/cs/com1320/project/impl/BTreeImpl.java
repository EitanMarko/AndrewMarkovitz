package edu.yu.cs.com1320.project.impl;

import com.google.gson.Gson;
import edu.yu.cs.com1320.project.BTree;
import edu.yu.cs.com1320.project.stage6.PersistenceManager;
import edu.yu.cs.com1320.project.stage6.impl.DocumentImpl;
import edu.yu.cs.com1320.project.stage6.impl.DocumentPersistenceManager;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;

public class BTreeImpl<Key extends Comparable<Key>, Value> implements BTree<Key, Value> {



    //max children per B-tree node = MAX-1 (must be an even number and greater than 2)
    private static final int MAX = 4;
    private BTreeImpl.Node root; //root of the B-tree
    private BTreeImpl.Node leftMostExternalNode;
    private int height; //height of the B-tree
    private int n; //number of key-value pairs in the B-tree
    private DocumentPersistenceManager<Key,Value> docPersManager;
                                    //URI, DocumentImpl ???????

    //B-tree node data type
    private static final class Node
    {
        private int entryCount; // number of entries
        private BTreeImpl.Entry[] entries = new BTreeImpl.Entry[BTreeImpl.MAX]; // the array of children
        private BTreeImpl.Node next;
        private BTreeImpl.Node previous;

        // create a node with k entries
        private Node(int k)
        {
            this.entryCount = k;
        }

    }

    //internal nodes: only use key and child
    //external nodes: only use key and value
    private static class Entry
    {
        private Comparable key;
        private Object val;
        private BTreeImpl.Node child;

        public Entry(Comparable key, Object val, BTreeImpl.Node child)
        {
            this.key = key;
            this.val = val;
            this.child = child;
        }

    }

    /**
     * Initializes an empty B-tree.
     */
    public BTreeImpl()
    {
        this.root = new BTreeImpl.Node(0);
        this.leftMostExternalNode = this.root;
    }

    /**
     * Returns true if this symbol table is empty.
     *
     * @return {@code true} if this symbol table is empty; {@code false}
     *         otherwise
     */
    private boolean isEmpty()
    {
        return this.size() == 0;
    }

    /**
     * @return the number of key-value pairs in this symbol table
     */
    private int size()
    {
        return this.n;
    }

    /**
     * @return the height of this B-tree
     */
    private int height()
    {
        return this.height;
    }

    /**
     * returns a list of all the entries in the Btree, ordered by key
     * @return
     */


    /**
     * Returns the value associated with the given key.
     *
     * @param key the key
     * @return the value associated with the given key if the key is in the
     *         symbol table and {@code null} if the key is not in the symbol
     *         table
     * @throws IllegalArgumentException if {@code key} is {@code null}
     */
    @Override
    public Value get(Key key)
    {
        if (key == null)
        {
            throw new IllegalArgumentException("argument to get() is null");
        }
        BTreeImpl.Entry entry = this.get(this.root, key, this.height);
        if(entry != null)
        {
            return (Value)entry.val;
        }
        return null;
    }

    private BTreeImpl.Entry get(BTreeImpl.Node currentNode, Key key, int height)
    {
        BTreeImpl.Entry[] entries = currentNode.entries;

        //current node is external (i.e. height == 0)
        //Leaf  Node
        if (height == 0)
        {
            for (int j = 0; j < currentNode.entryCount; j++)
            {
                if(isEqual(key, entries[j].key))
                {
                    //found desired key. Return its value
                    return entries[j];
                }
            }
            //didn't find the key
            return null;
        }

        //current node is internal (height > 0)
        //Internal Node... keep looking
        else
        {
            for (int j = 0; j < currentNode.entryCount; j++)
            {
                //if (we are at the last key in this node OR the key we
                //are looking for is less than the next key, i.e. the
                //desired key must be in the subtree below the current entry),
                //then recurse into the current entry’s child
                if (j + 1 == currentNode.entryCount || less(key, entries[j + 1].key))
                {
                    return this.get(entries[j].child, key, height - 1);
                }
            }
            //didn't find the key
            return null;
        }
    }

    /**
     *
     * @param key
     */
    private void delete(Key key)
    {
        put(key, null);
    } //NEED THIS???

    /**
     * Inserts the key-value pair into the symbol table, overwriting the old
     * value with the new value if the key is already in the symbol table. If
     * the value is {@code null}, this effectively deletes the key from the
     * symbol table.
     *
     * @param key the key
     * @param val the value
     * @return
     * @throws IllegalArgumentException if {@code key} is {@code null}
     */
    @Override
    public Value put(Key key, Value val)
    {
        if (key == null)
        {
            throw new IllegalArgumentException("argument key to put() is null");
        }
        //if the key already exists in the b-tree, simply replace the value
        BTreeImpl.Entry alreadyThere = this.get(this.root, key, this.height);
        if(alreadyThere != null)
        {
            alreadyThere.val = val;
            return val;
        }

        BTreeImpl.Node newNode = this.put(this.root, key, val, this.height);
        this.n++; //number of key-value pairs in the B-tree
        if (newNode == null) //no split of root, we're done
        {
            return val;
        }

        //split the root:                       //SEE SLIDES 22-23- CASE WHERE ROOT MUST BE SPLIT
        //Create a new node to be the root.
        //Set the old root to be new root's first entry.
        //Set the node returned from the call to put to be new root's second entry
        BTreeImpl.Node newRoot = new BTreeImpl.Node(2);
        newRoot.entries[0] = new BTreeImpl.Entry(this.root.entries[0].key, null, this.root);
        newRoot.entries[1] = new BTreeImpl.Entry(newNode.entries[0].key, null, newNode);
        this.root = newRoot;
        //a split at the root always increases the tree height by 1
        this.height++;
        return val;
    }

    /**
     *
     * @param currentNode
     * @param key
     * @param val
     * @param height
     * @return null if no new node was created (i.e. just added a new Entry into an existing node). If a new node was created due to the need to split, returns the new node
     */
    private BTreeImpl.Node put(BTreeImpl.Node currentNode, Key key, Value val, int height)
    {
        int j;
        BTreeImpl.Entry newEntry = new BTreeImpl.Entry(key, val, null);

        //external node
        //Leaf Node
        if (height == 0)
        {
            //find index in currentNode’s entry[] to insert new entry
            //we look for key < entry.key since we want to leave j
            //pointing to the slot to insert the new entry, hence we want to find
            //the first entry in the current node that key is LESS THAN
            for (j = 0; j < currentNode.entryCount; j++)
            {
                if (less(key, currentNode.entries[j].key))
                {
                    break;
                }
            }
        }

        // internal node
        else
        {
            //find index in node entry array to insert the new entry
            for (j = 0; j < currentNode.entryCount; j++)
            {
                //if (we are at the last key in this node OR the key we
                //are looking for is less than the next key, i.e. the
                //desired key must be added to the subtree below the current entry),
                //then do a recursive call to put on the current entry’s child
                if ((j + 1 == currentNode.entryCount) || less(key, currentNode.entries[j + 1].key))
                {
                    //increment j (j++) after the call so that a new entry created by a split
                    //will be inserted in the next slot
                    BTreeImpl.Node newNode = this.put(currentNode.entries[j++].child, key, val, height - 1);
                    if (newNode == null)
                    {
                        return null;
                    }
                    //if the call to put returned a node, it means I need to add a new entry to
                    //the current node
                    newEntry.key = newNode.entries[0].key;
                    newEntry.val = null;
                    newEntry.child = newNode;
                    break;
                }
            }
        }
        //shift entries over one place to make room for new entry
        for (int i = currentNode.entryCount; i > j; i--)
        {
            currentNode.entries[i] = currentNode.entries[i - 1];
        }
        //add new entry
        currentNode.entries[j] = newEntry;
        currentNode.entryCount++;
        if (currentNode.entryCount < BTreeImpl.MAX)
        {
            //no structural changes needed in the tree
            //so just return null
            return null;
        }
        else
        {
            //will have to create new entry in the parent due
            //to the split, so return the new node, which is
            //the node for which the new entry will be created
            return this.split(currentNode, height);
        }
    }

    /**
     * split node in half
     * @param currentNode
     * @return new node
     */
    private Node split(Node currentNode, int height)
    {
        BTreeImpl.Node newNode = new BTreeImpl.Node(BTreeImpl.MAX / 2);
        //by changing currentNode.entryCount, we will treat any value
        //at index higher than the new currentNode.entryCount as if
        //it doesn't exist
        currentNode.entryCount = BTreeImpl.MAX / 2;
        //copy top half of h into t
        for (int j = 0; j < BTreeImpl.MAX / 2; j++)
        {
            newNode.entries[j] = currentNode.entries[BTreeImpl.MAX / 2 + j];

            //set the latter elements to null in the original Node- MY ADD FROM SLIDES
            currentNode.entries[BTreeImpl.MAX/2+j] = null;
        }

        currentNode.entryCount = BTreeImpl.MAX/2;

        return newNode;
    }
    private static boolean less(Comparable k1, Comparable k2)
    {
        return k1.compareTo(k2) < 0;
    }

    private static boolean isEqual(Comparable k1, Comparable k2)
    {
        return k1.compareTo(k2) == 0;
    }

    private Entry getEntry(Key key)
    {
        if (key == null)
        {
            throw new IllegalArgumentException("argument to get() is null");
        }
        BTreeImpl.Entry entry = this.get(this.root, key, this.height);
        if(entry != null)
        {
            return entry;
        }
        return null;
    }
    @Override
    public void moveToDisk(Key k) throws IOException {


        Entry entry= getEntry(k);
        Value doc= get(k);
        entry.val=null;
        docPersManager.serialize(k, doc);

        //When a document is moved to disk, the entry in the BTree has null as its value instead of
        //a reference to the document in memory.

        // When a document is written out to disk, it is removed from the MinHeap which
        //is managing memory.

    }

    @Override
    public void setPersistenceManager(PersistenceManager<Key, Value> pm) {

        this.docPersManager = (DocumentPersistenceManager<Key, Value>) pm;

    }

}
