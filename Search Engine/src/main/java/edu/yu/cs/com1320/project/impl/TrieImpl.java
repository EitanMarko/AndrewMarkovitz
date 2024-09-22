package edu.yu.cs.com1320.project.impl;

import edu.yu.cs.com1320.project.Trie;
import edu.yu.cs.com1320.project.stage6.Document;
import edu.yu.cs.com1320.project.stage6.impl.*;
import edu.yu.cs.com1320.project.stage6.DocumentStore;
//import edu.yu.cs.com1320.project.stage4.impl.DocumentImpl;

import javax.print.Doc;
import java.util.*;

public class TrieImpl<Value> implements Trie<Value> {

    private static final int alphabetSize = 256; // extended ASCII
    private Node<Value> root;

    private class Node<Value>
    {

        private Node<Value>[] links = new Node[alphabetSize];

        private Set<Value> docSet= new HashSet<>();

        private Node(){

            this.links= links;
            this.docSet= docSet;

        }

        private List<Node> getChildren(){
            //return a collection of Node that can be iterated through- List (ordered)

            //iterate through the links[] and add all Nodes with values to a list
            List<Node> children= new ArrayList<>();
            for(int i=0; i<alphabetSize; i++){
                if(this.links[i]!=null){ //there's a child here
                    children.add(this.links[i]); //add Node to list of children
                }
            }
            return children;
        }
    }
    public TrieImpl(){
        this.root=new Node<Value>();
    }


    private String stripPunctuation(String key){
        String trimmedKey= key.trim();
        String lettersAndNums="";
        char[] docChars= trimmedKey.toCharArray();
        for (char ch : docChars){
            if(Character.isAlphabetic(ch) || Character.isDigit(ch) || Character.isWhitespace(ch)){
                lettersAndNums= lettersAndNums + ch;
            }
        }
        return lettersAndNums;
    }


    @Override
    public void put(String key, Value val) {
        //delete the value from this key
        if(val==null){
            return;
        }
        else{
            String properKeyToPut= stripPunctuation(key);
            this.root=put(this.root, properKeyToPut,val,0);
        }

    }

    private Node put(Node x, String key, Value val, int d){
        //create a new Node if needed
        if(x == null){
            x= new Node<>();
        }

        //we've reached the last Node in the key,
        // so set the value for the key and return the Node
        if(d== key.length()){
            x.docSet.add(val);
            return x;
        }

        //else, proceed to the next node in the chain of Nodes that forms the desired key
        char c= key.charAt(d);
        x.links[c]=this.put(x.links[c],key, val, d+1);
        return x;
    }

    @Override
    public List<Value> getSorted(String key, Comparator<Value> comparator) {

        Set<Value> docsSet= get(key);

        if(docsSet.isEmpty()){
            List<Value> emptyList= new ArrayList<>();
            return emptyList;
        }

        List<Value> docsList= new ArrayList<>(docsSet);

        docsList.sort(comparator);
        return docsList;
    }

    @Override
    public Set<Value> get(String key) {
        Node x= this.get(this.root, key, 0);
        if(x==null){
            Set<Value> emptySet= new HashSet<>();
            return emptySet;
        }
        return x.docSet;  //return set
        //if set at last Node is empty, it'll return the empty set
    }

    private Node get(Node x, String key, int d){

        //link from parent was null- miss
        if(x == null){
            return null;
        }

        //If we've reached the last Node in the key, return the node.
        //when this returns, the whole stack will unwind and the last Node will return to caller
        if(d == key.length()){
            return x;
        }

        //proceed to the next Node in the chain of Nodes that forms the desired key
        char c = key.charAt(d);
        return this.get(x.links[c],key,d+1);

    }

    @Override
    public List<Value> getAllWithPrefixSorted(String prefix, Comparator comparator) {

    //have to find the last node of the prefix string
        //ex: "hello" --> "o" node
    //then all nodes that have a value in them under that node

    //create a set
        //every node that has this prefix, add the values in its set to this set

    //preorder, postorder, or breadth search. Must traverse every Node under "o"
        //PROBABLY POSTORDER, since you're using that for deleteAllWithPrefix(), so it'll be easier
            //DIFFERENT POSTORDER- WITHOUT deleting empty nodes

    //then do what you did in getSorted() -->
        // turn the set into a list and sort in descending (reversed) order



        Node prefixRoot= this.get(this.root, prefix, 0);
        List<Node> nodes=postorderTraversal(prefixRoot); //returns a List of all the Nodes in the tree


        List<Value> vals = new ArrayList<>(); //list of all values

        for (Node node : nodes) {

            //ADD docSet from that Node into the set of values
            vals.addAll(node.docSet);
            //SET the Node to null
            //node=null;
        }

        //vals.sort(comparator.reversed()); //sort the nodes in descending order of comparator specified

        vals.sort(comparator);
        return vals;
    }

    private List<Node> postorderTraversal(Node n){

         //returns all the Nodes in the tree by post-order

        List<Node> nodes= new ArrayList<>();

        if(n==null){
            return nodes;
        }
        if(n.getChildren().isEmpty()){ //Node has no children
            nodes.add(n); //add Node to List
            return nodes; //return
        }
        //else (has Children)
        else{
            //List children= n.getChildren();
            List<Node> children = n.getChildren();
            for(Node child : children){ //for all Nodes, get children
                nodes.addAll(postorderTraversal(child));

            }
            nodes.add(n);
            return nodes;
        }
            //for all Children, find children


        //doesn't return all Nodes
    }




    @Override
    public Set<Value> deleteAllWithPrefix(String prefix) {

        //similar to getAllWithPrefixSorted(), but it's a delete and you're returning a set

        //have to find the last node of the prefix string
        //ex: "hello" --> "o" node
        //then all nodes that have a value in them under that node

        //POSTORDER traverse through the subtree, so you delete the leaves (leafs) before visiting inner nodes
        //Make this postorder traversal so that it deletes the Nodes with no values


        //get the Node at the top of the subtree
        Node<Value> prefixNode = this.get(this.root, prefix, 0);
        List<Node> u = postorderTraversal(prefixNode); //ordered list of nodes in the subtree
        //Set<Node> uSet=new HashSet<>(u);

        Set<Value> vals = new HashSet<>(); //set of all values

        for (Node node : u) {

            //ADD docSet from that Node into the set of values
            vals.addAll(node.docSet);
            //SET the Node to null
            //node=null;
        }
            if (prefix.length() > 1) {
                String fatherStr = prefix.substring(0, prefix.length() - 1);
                Node<Value> fatherNode = this.get(this.root, fatherStr, 0);
                char prefixStr = prefix.charAt(prefix.length() - 1);
                fatherNode.links[prefixStr] = null;

                //Find the element at the o'th place in links
            }

            //if its length 1, set it to the reference in root at prefix place in links
            if(prefix.length()==1){
                char prefixStr = prefix.charAt(0); //cuz its only length 1
                this.root.links[prefixStr]=null;
            }

            //this.deleteAll(prefix); //delete the prefix and all its sub-Nodes

            return vals;

    }
    @Override
    public Set<Value> deleteAll(String key) {

        //you'll first find the Set in the Node that's to be deleted
        //then return that Set

        Set<Value> y = deleteAll(this.root, key, 0);
        return y;
    }

    private Set<Value> deleteAll(Node x, String key, int d)
    {
        Set<Value> holdDeletes= new HashSet<>();

        if (x == null)
        {
            return null;
        }
        //we're at the node to "del"
        if (d == key.length())
        {
            holdDeletes.addAll(get(key));

            x.docSet.clear(); //got rid of all elements in the set
        }
        //continue down the trie to the target node
        else
        {
            char c = key.charAt(d);
//            x.links[c].docSet = this.deleteAll(x.links[c], key, d + 1);
            holdDeletes = this.deleteAll(x.links[c], key, d + 1);
            //added docSet here!!!^
        }
        //this node has a val – do nothing, return the node
        if (!x.docSet.isEmpty())
        {                      //Gets here after element is cleared of values


            return holdDeletes;
        }
        //remove subtrie rooted at x if it is completely empty
        for (int c = 0; c < alphabetSize; c++)
        {
            if (x.links[c] != null)
            {
                //RETURN THE DOCSET

                return holdDeletes; //not empty
            }
        }
        //empty - set this link to null in the parent
        x=null;
        return holdDeletes;
    }

    @Override
    public Value delete(String key, Value val) {
        Node y = delete(this.root, key, val, 0);

        if(y==null){
            return null; //did not contain the given value... or Node was null
        }
        return val; //else, return value, which is deleted
    }

    private Node delete(Node x, String key, Value val, int d)
    {
        //Method would return Value, but it messes with the logic.
        //Instead, it functions to delete, and return null if the node doesn't contain the given value
        //If it returns a non-null Node (the original Node),
        //      it doesn't matter, bc the public delete() only checks for null

        //Helper method returns Node that was passed




        if (x == null)
        {
            return null;
        }
        //we're at the node to "del" a value
        if (d == key.length())
        {
            if(x.docSet.contains(val))
            {
                x.docSet.remove(val);
            }
            else{ //value NOT in set
                return null;
            }
        }
        //continue down the trie to the target node
        else
        {
            char c = key.charAt(d);
            x.links[c] = this.delete(x.links[c], key, val, d + 1);
        }
        //this node has a val – do nothing, return the node
        if (!x.docSet.isEmpty())//WILL NEVER HIT THIS THOUGH?????????????????????????????
        {                      //Gets here after element is cleared of values
            return x;
        }
        //remove subtrie rooted at x if it is completely empty
        for (int c = 0; c < alphabetSize; c++)
        {
            if (x.links[c] != null)
            {
                return x; //not empty
            }
        }
        //empty - set this link to null in the parent
        return null;
    }
}