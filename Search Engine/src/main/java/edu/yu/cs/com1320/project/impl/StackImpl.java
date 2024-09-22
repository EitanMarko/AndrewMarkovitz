package edu.yu.cs.com1320.project.impl;

import edu.yu.cs.com1320.project.Stack;

public class StackImpl<T> implements Stack<T> {

    private T t;
    private T[] data;
    private int top=-1;
    public StackImpl(){
        this.t=t;
        this.data= (T[])new Object[1];
        this.top=top;


    }

    private int stackArrayLength(){ //MUST BE PRIVATE
        return this.data.length;
    }

    @Override
    public void push(T element) {

        if(element==null){
            throw new IllegalArgumentException(); //are we sure abt this?
        }

        if(top == data.length - 1){ //stack is full
            arrayDoubler();
        }
        top++;
        data[top] = element;
    }



    @Override
    public T pop() {
        if(top == -1) { //nothing in stack
            return null;
        }
        //T item = data[top]; //this is how it was done in slides

        T topOfStack = data[top];
        data[top] = null;
        top--;
       /* if(top==-1){ //if, after pop, the stack is empty
            return null;
        }*/
        //return data[top]; //return new top element
        return topOfStack;
    }

    @Override
    public T peek() {
        if(top == -1) { //nothing in stack
            return null;
        }
        return data[top];
    }

    @Override
    public int size() {
        int counter=0;
        for(int x=0; x<data.length;x++){
            if(data[x]!=null){ //there's something in the array slot
                counter++;
            }
        }
        return counter;
    }

    private void arrayDoubler(){
        //make a new temp array
        //add the values to the temp array
        //make the existing array a new array, double the size
        //fill the new array

        int originalArraySize=this.data.length;
        T[] temp= (T[])new Object[originalArraySize]; //should I cast here or just initialize T[]???

        for(int i=0;i<originalArraySize;i++){
            temp[i]=this.data[i];
        }

        int doubleSize=originalArraySize*2;
        this.data= (T[])new Object[doubleSize]; //should I cast here or just initialize T[]???

        for(int i=0;i<originalArraySize;i++){
            this.data[i]=temp[i];
        }
    }
}
