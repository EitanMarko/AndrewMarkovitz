package com.eitan.productivime.BangForYourBuck;

public class Interval {

    public int start;
    public int end;
    public int duration;
    public Interval(String startTime, String endTime) {
        this.start = new Time(startTime).time;
        this.end = new Time(endTime).time;

        if(start >= end){
            throw new IllegalArgumentException("end must be later than start (24-hr clock)");
        }
        if(start % 5 != 0 || end % 5 != 0){
            throw new IllegalArgumentException("Interval's start and end times must be on a 5-minute interval");
        }

        this.duration = end - start;
    }

    public void setStart(String start){
        Time newStart = new Time(start);

        if(newStart.time >= end){
            throw new IllegalArgumentException("start must be earlier than end (24-hr clock)");
        }
        if(newStart.time % 5 != 0){
            throw new IllegalArgumentException("Interval's start time must be on a 5-minute interval");
        }

        this.start = newStart.time;
        this.duration = this.end - this.start;
    }

    public int getStart() {
        return start;
    }

    public void setEnd(String end){
        Time newEnd = new Time(end);

        if(this.start >= newEnd.time){
            throw new IllegalArgumentException("end must be later than start (24-hr clock)");
        }
        if(newEnd.time % 5 != 0){
            throw new IllegalArgumentException("Interval's end time must be on a 5-minute interval");
        }

        this.end = newEnd.time;
        this.duration = this.end - this.start;
    }

    public int getEnd(){
        return end;
    }

    public int getDuration() {
        return duration;
    }

    //Implement Comparable! compareTo() by startTime
        // This way if you have a list of Intervals, you can sort them easily
        // Can merge Intervals by checking if they overlap:
            // If Interval at index 0 has an END TIME which is:

                // (1) Earlier than start time of index 1 interval -
                    // Leave both intervals alone

                // (2) Between start & end times of index 1 interval -
                    // Set interval 0's end time to interval 1's end time
                    // Remove interval 1 from list

                // (3) After end time of index 1 interval -
                    // Remove interval 1 from list
                    // Compare interval 0 to interval 2

}
