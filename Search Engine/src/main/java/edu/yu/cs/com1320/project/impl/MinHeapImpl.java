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


}