/*
 * Copyright (c) 2012 Denis Solonenko.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */

package tw.tib.financisto.datetime;

import android.content.Context;

import tw.tib.financisto.R;
import tw.tib.financisto.utils.LocalizableEnum;
import tw.tib.financisto.utils.MyPreferences;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: denis.solonenko
 * Date: 12/17/12 9:08 PM
 */
public enum PeriodType implements LocalizableEnum {
    TODAY(R.string.period_today, true, true) {
        @Override
        public Period calculatePeriod(Context context, long refTime) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(refTime);
            long start = DateUtils.startOfDay(c).getTimeInMillis();
            long end = DateUtils.endOfDay(c).getTimeInMillis();
            return new Period(PeriodType.TODAY, start, end);
        }
    },
    YESTERDAY(R.string.period_yesterday, true, false) {
        @Override
        public Period calculatePeriod(Context context, long refTime) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(refTime);
            c.add(Calendar.DAY_OF_MONTH, -1);
            long start = DateUtils.startOfDay(c).getTimeInMillis();
            long end = DateUtils.endOfDay(c).getTimeInMillis();
            return new Period(PeriodType.YESTERDAY, start, end);
        }
    },
    THIS_WEEK(R.string.period_this_week, true, true) {
        @Override
        public Period calculatePeriod(Context context, long refTime) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(refTime);
            int dayOfWeek = c.get(Calendar.DAY_OF_WEEK);
            int firstDayOfWeek;
            switch (MyPreferences.getFirstDayOfWeek(context)) {
                case SUNDAY -> firstDayOfWeek = Calendar.SUNDAY;
                case MONDAY -> firstDayOfWeek = Calendar.MONDAY;
                default -> firstDayOfWeek = c.getFirstDayOfWeek();
            }
            long start, end;
            if (dayOfWeek != firstDayOfWeek) {
                c.add(Calendar.DAY_OF_MONTH, -(dayOfWeek > firstDayOfWeek ?
                        (dayOfWeek - firstDayOfWeek) :
                        (dayOfWeek - firstDayOfWeek + 7)));
            }
            start = DateUtils.startOfDay(c).getTimeInMillis();
            c.add(Calendar.DAY_OF_MONTH, 6);
            end = DateUtils.endOfDay(c).getTimeInMillis();
            return new Period(PeriodType.THIS_WEEK, start, end);
        }
    },
    THIS_MONTH(R.string.period_this_month, true, true) {
        @Override
        public Period calculatePeriod(Context context, long refTime) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(refTime);
            c.set(Calendar.DAY_OF_MONTH, 1);
            long start = DateUtils.startOfDay(c).getTimeInMillis();
            c.add(Calendar.MONTH, 1);
            c.add(Calendar.DAY_OF_MONTH, -1);
            long end = DateUtils.endOfDay(c).getTimeInMillis();
            return new Period(PeriodType.THIS_MONTH, start, end);
        }
    },
    THIS_YEAR(R.string.period_this_year, true, true) {
        public Period calculatePeriod(Context context, long refTime) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(refTime);
            c.set(Calendar.DAY_OF_YEAR, 1);
            long start = DateUtils.startOfDay(c).getTimeInMillis();
            c.add(Calendar.YEAR, 1);
            c.add(Calendar.DAY_OF_MONTH, -1);
            long end = DateUtils.endOfDay(c).getTimeInMillis();
            return new Period(PeriodType.THIS_YEAR, start, end);
        }
    },
    LAST_WEEK(R.string.period_last_week, true, false) {
        @Override
        public Period calculatePeriod(Context context, long refTime) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(refTime);
            c.add(Calendar.DAY_OF_YEAR, -7);
            int dayOfWeek = c.get(Calendar.DAY_OF_WEEK);
            int firstDayOfWeek;
            switch (MyPreferences.getFirstDayOfWeek(context)) {
                case SUNDAY -> firstDayOfWeek = Calendar.SUNDAY;
                case MONDAY -> firstDayOfWeek = Calendar.MONDAY;
                default -> firstDayOfWeek = c.getFirstDayOfWeek();
            }
            long start, end;
            if (dayOfWeek != firstDayOfWeek) {
                c.add(Calendar.DAY_OF_MONTH, -(dayOfWeek > firstDayOfWeek ?
                        (dayOfWeek - firstDayOfWeek) :
                        (dayOfWeek - firstDayOfWeek + 7)));
            }
            start = DateUtils.startOfDay(c).getTimeInMillis();
            c.add(Calendar.DAY_OF_MONTH, 6);
            end = DateUtils.endOfDay(c).getTimeInMillis();
            return new Period(PeriodType.LAST_WEEK, start, end);
        }
    },
    LAST_MONTH(R.string.period_last_month, true, false) {
        @Override
        public Period calculatePeriod(Context context, long refTime) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(refTime);
            c.add(Calendar.MONTH, -1);
            c.set(Calendar.DAY_OF_MONTH, 1);
            long start = DateUtils.startOfDay(c).getTimeInMillis();
            c.add(Calendar.MONTH, 1);
            c.add(Calendar.DAY_OF_MONTH, -1);
            long end = DateUtils.endOfDay(c).getTimeInMillis();
            return new Period(PeriodType.LAST_MONTH, start, end);
        }
    },
    LAST_YEAR(R.string.period_last_year, true, false) {
        @Override
        public Period calculatePeriod(Context context, long refTime) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(refTime);
            c.add(Calendar.YEAR, -1);
            c.set(Calendar.DAY_OF_YEAR, 1);
            long start = DateUtils.startOfDay(c).getTimeInMillis();
            c.add(Calendar.YEAR, 1);
            c.add(Calendar.DAY_OF_YEAR, -1);
            long end = DateUtils.endOfDay(c).getTimeInMillis();
            return new Period(PeriodType.LAST_YEAR, start, end);
        }
    },
    THIS_AND_LAST_WEEK(R.string.period_this_and_last_week, true, false) {
        @Override
        public Period calculatePeriod(Context context, long refTime) {
            Period lastWeek = LAST_WEEK.calculatePeriod(context, refTime);
            Period thisWeek = THIS_WEEK.calculatePeriod(context, refTime);
            return new Period(PeriodType.THIS_AND_LAST_WEEK, lastWeek.start, thisWeek.end);
        }
    },
    THIS_AND_LAST_MONTH(R.string.period_this_and_last_month, true, false) {
        @Override
        public Period calculatePeriod(Context context, long refTime) {
            Period lastMonth = LAST_MONTH.calculatePeriod(context, refTime);
            Period thisMonth = THIS_MONTH.calculatePeriod(context, refTime);
            return new Period(PeriodType.THIS_AND_LAST_MONTH, lastMonth.start, thisMonth.end);
        }
    },
    THIS_AND_LAST_YEAR(R.string.period_this_and_last_year, true, false) {
        @Override
        public Period calculatePeriod(Context context, long refTime) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(refTime);
            c.add(Calendar.YEAR, -1);
            c.set(Calendar.DAY_OF_YEAR, 1);
            long start = DateUtils.startOfDay(c).getTimeInMillis();

            c = Calendar.getInstance();
            c.setTimeInMillis(refTime);
            c.add(Calendar.YEAR, 1);
            c.set(Calendar.DAY_OF_YEAR, 1);
            c.add(Calendar.DAY_OF_YEAR, -1);
            long end = DateUtils.endOfDay(c).getTimeInMillis();

            return new Period(PeriodType.THIS_AND_LAST_YEAR, start, end);
        }
    },
    TOMORROW(R.string.period_tomorrow, false, true) {
        @Override
        public Period calculatePeriod(Context context, long refTime) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(refTime);
            c.add(Calendar.DAY_OF_MONTH, 1);
            long start = DateUtils.startOfDay(c).getTimeInMillis();
            long end = DateUtils.endOfDay(c).getTimeInMillis();
            return new Period(PeriodType.TOMORROW, start, end);
        }
    },
    NEXT_WEEK(R.string.period_next_week, false, true) {
        @Override
        public Period calculatePeriod(Context context, long refTime) {
            Period thisWeek = THIS_WEEK.calculatePeriod(context, refTime);
            Calendar start = Calendar.getInstance();
            start.setTimeInMillis(thisWeek.start);
            start.add(Calendar.DAY_OF_MONTH, 7);
            Calendar end = Calendar.getInstance();
            end.setTimeInMillis(thisWeek.end);
            end.add(Calendar.DAY_OF_MONTH, 7);
            return new Period(PeriodType.NEXT_WEEK, start.getTimeInMillis(), end.getTimeInMillis());
        }
    },
    NEXT_MONTH(R.string.period_next_month, false, true) {
        @Override
        public Period calculatePeriod(Context context, long refTime) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(refTime);
            c.add(Calendar.MONTH, 1);
            c.set(Calendar.DAY_OF_MONTH, 1);
            long start = DateUtils.startOfDay(c).getTimeInMillis();
            c.add(Calendar.MONTH, 1);
            c.add(Calendar.DAY_OF_MONTH, -1);
            long end = DateUtils.endOfDay(c).getTimeInMillis();
            return new Period(PeriodType.NEXT_MONTH, start, end);
        }
    },
    THIS_AND_NEXT_MONTH(R.string.period_this_and_next_month, false, true) {
        @Override
        public Period calculatePeriod(Context context, long refTime) {
            Period thisMonth = THIS_MONTH.calculatePeriod(context, refTime);
            Period nextMonth = NEXT_MONTH.calculatePeriod(context, refTime);
            return new Period(PeriodType.THIS_AND_NEXT_MONTH, thisMonth.start, nextMonth.end);
        }
    },
    NEXT_3_MONTHS(R.string.period_next_3_months, false, true) {
        @Override
        public Period calculatePeriod(Context context, long refTime) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(refTime);
            c.set(Calendar.DAY_OF_MONTH, 1);
            long start = DateUtils.startOfDay(c).getTimeInMillis();
            c.add(Calendar.MONTH, 3);
            c.add(Calendar.DAY_OF_MONTH, -1);
            long end = DateUtils.endOfDay(c).getTimeInMillis();
            return new Period(PeriodType.NEXT_3_MONTHS, start, end);
        }
    },
    CUSTOM(R.string.period_custom, true, true) {
        @Override
        public Period calculatePeriod(Context context, long refTime) {
            return null;
        }
    };

    public static PeriodType[] allRegular() {
        List<PeriodType> types = new ArrayList<PeriodType>();
        for (PeriodType periodType : PeriodType.values()) {
            if (periodType.inPast) {
                types.add(periodType);
            }
        }
        return types.toArray(new PeriodType[types.size()]);
    }

    public static PeriodType[] allPlanner() {
        List<PeriodType> types = new ArrayList<PeriodType>();
        for (PeriodType periodType : PeriodType.values()) {
            if (periodType.inFuture) {
                types.add(periodType);
            }
        }
        return types.toArray(new PeriodType[types.size()]);
    }

    public final int titleId;
    public final boolean inPast;
    public final boolean inFuture;

    PeriodType(int titleId, boolean inPast,boolean inFuture) {
        this.titleId = titleId;
        this.inPast = inPast;
        this.inFuture = inFuture;
    }

    public int getTitleId() {
        return titleId;
    }

    public abstract Period calculatePeriod(Context context, long refTime);

    public Period calculatePeriod(Context context) {
        return calculatePeriod(context, System.currentTimeMillis());
    }
}
