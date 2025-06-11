package com.eitan.productivime;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.eitan.productivime.BangForYourBuck.*;
public class TimeTest {
    @Test
    void invalidFormat() {

        assertThrows(IllegalArgumentException.class, () -> {
            Time noHrs = new Time(":21");}); // No digits to denote hours

        assertThrows(IllegalArgumentException.class, () -> {
            Time noMins = new Time("2:");}); // No digits to denote minutes

        assertThrows(IllegalArgumentException.class, () -> {
            Time justColon = new Time(":");}); // No digits to denote hours or minutes

        assertThrows(IllegalArgumentException.class, () -> {
            Time threeHrsDigs = new Time("000:20");}); // >2 digits for hours

        assertThrows(IllegalArgumentException.class, () -> {
            Time oneMinsDig = new Time("1:0");}); // <2 digits for minutes

        assertThrows(IllegalArgumentException.class, () -> {
            Time threeMinsDigs = new Time("1:000");}); // >2 digits for minutes

        Time twoHrsDigs = new Time("10:20"); // 2 digits for hours, 2 for minutes - valid

        Time oneHrsDig = new Time("1:20"); // 1 digit for hours, 2 for minutes - valid



    }


    @Test
    void invalidHours() {

        // Hours can be 0-23, and 0 can have one or two digits
        Time valid = new Time("23:20");
        Time valid2 = new Time("0:20");
        Time valid3 = new Time("00:20");

        assertThrows(IllegalArgumentException.class, () -> {
            Time negHrs = new Time("-1:20");}); // Hours are <0

        assertThrows(IllegalArgumentException.class, () -> {
            Time tooBigHrs = new Time("24:20");}); // Hours are >23

    }


    @Test
    void invalidMinutes() {

        // Minutes can be 0-59
        Time valid = new Time("11:59");
        Time valid2 = new Time("8:00");

        assertThrows(IllegalArgumentException.class, () -> {
            Time negMins = new Time("1:-10");}); // Minutes are <0

        assertThrows(IllegalArgumentException.class, () -> {
            Time tooBigMins = new Time("1:60");}); // Minutes are >59

    }

    @Test
    void TimeEval() {
        Time activity = new Time("11:59");
        assertEquals(719,activity.time); // 'activity' begins 719 minutes into the day

        Time activity2 = new Time("22:38");
        assertEquals(1358,activity2.time); // 'activity2' begins 1358 minutes into the day
    }
}
