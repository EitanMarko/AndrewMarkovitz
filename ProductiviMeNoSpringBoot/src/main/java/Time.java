public class Time {
    public int time; //long?
    public Time(String timeStr) {
        //Take the string representing what time it is and convert it to a value
        //Midnight is 0
        //1:00 a.m. is 60
        //2:00 a.m. is 120
        //etc.

        int colonIndex = timeStr.lastIndexOf(":");
        if(colonIndex == timeStr.length()-1 || colonIndex == 0){ //":" is first or last char in string (no hr/min)
            throw new IllegalArgumentException("Please input proper formatting of time: '<HOUR>:<MINUTES>'");
        }

        String hourStr = timeStr.substring(0,colonIndex);
        String minsStr = timeStr.substring(colonIndex+1);

        if(hourStr.charAt(0) == '-'){
            throw new IllegalArgumentException("Hours must be between 0-23");
        }
        if(minsStr.charAt(0) == '-'){
            throw new IllegalArgumentException("Minutes must be between 0-59");
        }
        if(minsStr.length() != 2){
            throw new IllegalArgumentException("There should be 2 digits to denote minutes");
        }
        if(hourStr.length()>2){
            throw new IllegalArgumentException("There can be maximum 2 digits to denote hours");
        }

        int hour = Integer.parseInt(hourStr);
        int mins = Integer.parseInt(minsStr);

        if(hour<0 || hour>23){
            throw new IllegalArgumentException("Hours must be between 0-23");
        }
        if(mins<0 || mins>59){
            throw new IllegalArgumentException("Minutes must be between 0-59");
        }

        time = (hour*60) + mins;
    }
}
