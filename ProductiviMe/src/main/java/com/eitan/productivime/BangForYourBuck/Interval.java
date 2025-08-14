package com.eitan.productivime.BangForYourBuck;

public class Interval implements Comparable<Interval> {

    public int start;
    public int end;
    public int duration;
    private String startString;
    private String endString;
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
        this.startString = startTime;
        this.endString = endTime;
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
        this.startString = start;
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
        this.endString = end;
    }

    public int getEnd(){
        return end;
    }
    public String getStartStr(){
        return this.startString;
    }

    public String getEndStr(){
        return this.endString;
    }

    public int getDuration() {
        return duration;
    }

    @Override
    public int compareTo(Interval other) {
        return Integer.compare(this.start, other.getStart());
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
                    // Compare interval 0 to interval

        // DO THIS AT TIME OF SCHEDULE GENERATION!!!!!!!!!!!!!!
            // Doing so instead every time we add a SetActivity becomes very expensive
                // O(n^2) vs O(n)

        // NEW: (2) is NOT a possibiility because the system rejects adding a SetActivity which
            // overlaps with a previously added SetActivity

}
