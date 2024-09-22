package edu.yu.cs.com1320.project.impl;

import edu.yu.cs.com1320.project.*;
import edu.yu.cs.com1320.project.stage6.Document;

import javax.print.Doc;
import java.util.NoSuchElementException;

public class MinHeapImpl<E extends Comparable<E>> extends MinHeap<E> { //MinHeapImpl<E extends Comparable<E>> extends MinHeap<E>


    //STAGE 6: If E is now a URI, you'll have to get() the Document using E from now on
    //THIS ALSO MEANS the MinHeap must have a reference to the BTree...

    public MinHeapImpl() {
        elements= (E[]) new Comparable[1];
    }

    public void reHeapify(E e){
//The job of reHeapify is to determine whether
// the Document whose time was updated should stay where it is,
// move up in the heap, or move down in the heap,
// and then carry out any move that should occur.

        //The Document used may be in the wrong place in the MinHeap

        //CHECK: IF THE DOC HASN'T BEEN INSERTED, CAN YOU REHEAPIFY?!!!!!!!!!!!!!!!!!!!!

        //If doc.compareTo( its parent- k/2) < 0 , swap()

        int elementIndex= getArrayIndex(e);
        upHeap(elementIndex); //move up if needed
        downHeap(elementIndex); //move down if needed

    }


    protected int getArrayIndex(E e){
        for(int i=0; i< elements.length; i++){
            if(e.equals(elements[i])){
                return i;
            }
        }
        throw new NoSuchElementException();
    }

    protected void doubleArraySize(){

        //Don't need to sort, just double size
        int arraySize=elements.length;
        int newArraySize = arraySize * 2;
        E[] newArray= (E[])new Comparable[newArraySize];
        int counter=0;
        for(E i: elements){
            newArray[counter]=i;
            counter++;
        }

        this.elements=newArray;




    }


/*
    protected boolean isEmpty() {
        return this.count == 0;
    }


    *//**
     * is elements[i] > elements[j]?
     *//*
    protected boolean isGreater(int i, int j) {
        return this.elements[i].compareTo(this.elements[j]) > 0;
    }

    *//**
     * swap the values stored at elements[i] and elements[j]
     *//*
    protected void swap(int i, int j) {
        E temp = this.elements[i];
        this.elements[i] = this.elements[j];
        this.elements[j] = temp;
    }

    *//**
     * while the key at index k is less than its
     * parent's key, swap its contents with its parent’s
     *//*
    protected void upHeap(int k) {
        while (k > 1 && this.isGreater(k / 2, k)) {
            this.swap(k, k / 2);
            k = k / 2;
        }
    }

    *//**
     * move an element down the heap until it is less than
     * both its children or is at the bottom of the heap
     *//*
    protected void downHeap(int k) {
        while (2 * k <= this.count) {
            //identify which of the 2 children are smaller
            int j = 2 * k;
            if (j < this.count && this.isGreater(j, j + 1)) {
                j++;
            }
            //if the current value is < the smaller child, we're done
            if (!this.isGreater(k, j)) {
                break;
            }
            //if not, swap and continue testing
            this.swap(k, j);
            k = j;
        }
    }

    public void insert(E x) {
        // double size of array if necessary
        if (this.count >= this.elements.length - 1) {
            this.doubleArraySize();
        }
        //add x to the bottom of the heap
        this.elements[++this.count] = x;
        //percolate it up to maintain heap order property
        this.upHeap(this.count);
    }

    public E peek(){
        return this.elements[1];
    }

    public E remove() {
        if (isEmpty()) {
            throw new NoSuchElementException("Heap is empty");
        }
        E min = this.elements[1];
        //swap root with last, decrement count
        this.swap(1, this.count--);
        //move new root down as needed
        this.downHeap(1);
        this.elements[this.count + 1] = null; //null it to prepare for GC
        return min;
    }

    //Methods also included in abstract class:

    //protected boolean isGreater(int i, int j)

    //protected void swap(int i, int j)

    //protected void upHeap(int k)

    //protected void downHeap(int k)

    //public void insert(E x)

    //public E peek()

    //public E remove()*/

}