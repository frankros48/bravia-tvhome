package com.frank.tvhome;

import java.util.Calendar;

/** A yearly date to count down to. */
final class Countdown {
    static final int CHRISTMAS = -1;
    static final int THANKSGIVING = -2;

    final String name;
    final int month;      // 1-12, or CHRISTMAS / THANKSGIVING
    final int day;
    final int birthYear;  // 0 when not a birthday

    Countdown(String name, int month, int day, int birthYear) {
        this.name = name;
        this.month = month;
        this.day = day;
        this.birthYear = birthYear;
    }

    /** Days from today until the next occurrence (0 = today). */
    int daysLeft(Calendar today) {
        Calendar d = next(today);
        return (int) Math.round((d.getTimeInMillis() - startOfDay(today).getTimeInMillis()) / 86400000.0);
    }

    /** Age being reached at the next occurrence, or 0 for non-birthdays. */
    int turning(Calendar today) {
        return birthYear == 0 ? 0 : next(today).get(Calendar.YEAR) - birthYear;
    }

    private Calendar next(Calendar today) {
        Calendar d = occurrence(today.get(Calendar.YEAR));
        return d.before(startOfDay(today)) ? occurrence(today.get(Calendar.YEAR) + 1) : d;
    }

    private Calendar occurrence(int year) {
        Calendar c = Calendar.getInstance();
        c.clear();
        if (month == CHRISTMAS) {
            c.set(year, Calendar.DECEMBER, 25);
        } else if (month == THANKSGIVING) { // fourth Thursday of November
            c.set(year, Calendar.NOVEMBER, 1);
            int toThursday = (Calendar.THURSDAY - c.get(Calendar.DAY_OF_WEEK) + 7) % 7;
            c.set(year, Calendar.NOVEMBER, 1 + toThursday + 21);
        } else {
            c.set(year, month - 1, day);
        }
        return c;
    }

    private static Calendar startOfDay(Calendar now) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH));
        return c;
    }
}
