/*
 * Copyright (c) 2012 Denis Solonenko.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */

package tw.tib.financisto.utils;

import android.content.Context;
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import tw.tib.financisto.R;
import tw.tib.financisto.filter.WhereFilter;
import tw.tib.financisto.db.DatabaseAdapter;
import tw.tib.financisto.model.*;
import tw.tib.financisto.model.Total;
import tw.tib.financisto.model.TransactionInfo;
import tw.tib.financisto.recur.Recurrence;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: Denis Solonenko
 * Date: 8/25/11 11:00 PM
 */
public abstract class AbstractPlanner {

    protected Context context;

    protected final DatabaseAdapter db;

    protected final WhereFilter filter;
    protected final Date now;

    public AbstractPlanner(Context context, DatabaseAdapter db, WhereFilter filter, Date now) {
        this.context = context;
        this.db = db;
        this.filter = filter;
        this.now = now;
    }

    public TransactionList getPlannedTransactionsWithTotals() {
        List<TransactionInfo> transactions = getPlannedTransactions();
        Total[] totals = calculateTotals(transactions);
        return new TransactionList(transactions, totals);
    }

    public List<TransactionInfo> getPlannedTransactions() {
        List<TransactionInfo> regularTransactions = asTransactionList(getRegularTransactions());
        List<TransactionInfo> scheduledTransactions = getScheduledTransactions();
        if (scheduledTransactions.isEmpty()) {
            return regularTransactions;
        } else {
            List<TransactionInfo> allTransactions = new ArrayList<TransactionInfo>();
            allTransactions.addAll(regularTransactions);
            allTransactions.addAll(planSchedules(scheduledTransactions));
            sortTransactions(allTransactions);
            return allTransactions;
        }
    }

    protected abstract Total[] calculateTotals(List<TransactionInfo> transactions);

    protected abstract Cursor getRegularTransactions();

    protected List<TransactionInfo> getScheduledTransactions() {
        return db.getAllScheduledTransactions();
    }

    private List<TransactionInfo> planSchedules(List<TransactionInfo> scheduledTransactions) {
        List<TransactionInfo> plannedTransactions = new ArrayList<TransactionInfo>();
        for (TransactionInfo scheduledTransaction : scheduledTransactions) {
            TransactionInfo transaction = prepareScheduledTransaction(scheduledTransaction);
            if (includeScheduledTransaction(transaction)) {
                List<Date> dates = calculatePlannedDates(transaction);
                duplicateTransaction(transaction, dates, plannedTransactions);
            } else if (transaction.isSplitParent()) {
                planSplitSchedules(transaction, plannedTransactions);
            }
        }
        return plannedTransactions;
    }

    private void planSplitSchedules(TransactionInfo scheduledTransaction, List<TransactionInfo> plannedTransactions) {
        List<Date> dates = calculatePlannedDates(scheduledTransaction);
        List<TransactionInfo> splits = db.getSplitsInfoForTransaction(scheduledTransaction.id);
        for (TransactionInfo split : splits) {
            if (includeScheduledSplitTransaction(split)) {
                TransactionInfo transaction = prepareScheduledTransaction(split);
                duplicateTransaction(transaction, dates, plannedTransactions);
            }
        }
    }

    protected abstract TransactionInfo prepareScheduledTransaction(TransactionInfo scheduledTransaction);

    protected abstract boolean includeScheduledTransaction(TransactionInfo transaction);
    protected abstract boolean includeScheduledSplitTransaction(TransactionInfo split);

    private List<Date> calculatePlannedDates(TransactionInfo scheduledTransaction) {
        String recurrence = scheduledTransaction.recurrence;
        Date startDate = getStartDateFromFilter();
        Date endDate = getEndDateFromFilter();
        Date calcDate = startDate.before(now) ? now : startDate;
        if (recurrence == null) {
            Date scheduledDate = new Date(scheduledTransaction.dateTime);
            if (insideTheRequiredPeriod(calcDate, endDate, scheduledDate)) {
                return Collections.singletonList(scheduledDate);
            }
        } else {
            Recurrence r = Recurrence.parse(recurrence);
            try {
                return r.generateDates(calcDate, endDate);
            } catch (IllegalArgumentException e) {
                new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(context,
                        context.getString(R.string.invalid_rrule, e.getMessage()),
                        Toast.LENGTH_LONG).show());
            }
        }
        return Collections.emptyList();
    }

    private Date getStartDateFromFilter() {
        return new Date(filter.getDateTime().getPeriod().start);
    }

    private Date getEndDateFromFilter() {
        return new Date(filter.getDateTime().getPeriod().end);
    }

    private boolean insideTheRequiredPeriod(Date startDate, Date endDate, Date date) {
        return !(date.before(startDate) || date.after(endDate));
    }

    private void duplicateTransaction(TransactionInfo scheduledTransaction, List<Date> dates, List<TransactionInfo> plannedTransactions) {
        if (dates.size() == 1) {
            scheduledTransaction.dateTime = dates.get(0).getTime();
            scheduledTransaction.isTemplate = 0;
            plannedTransactions.add(scheduledTransaction);
        } else {
            for (Date date : dates) {
                TransactionInfo t = scheduledTransaction.clone();
                t.dateTime = date.getTime();
                t.isTemplate = 0;
                plannedTransactions.add(t);
            }
        }
    }

    private void sortTransactions(List<TransactionInfo> transactions) {
        Collections.sort(transactions, createSortComparator());
    }

    protected Comparator<TransactionInfo> createSortComparator() {
        return new Comparator<TransactionInfo>() {
            @Override
            public int compare(TransactionInfo transaction1, TransactionInfo transaction2) {
                return transaction1.dateTime > transaction2.dateTime ? 1 : (transaction1.dateTime < transaction2.dateTime ? -1 : 0);
            }
        };
    }

    protected List<TransactionInfo> asTransactionList(Cursor cursor) {
        try {
            List<TransactionInfo> transactions = new ArrayList<TransactionInfo>(cursor.getCount());
            while (cursor.moveToNext()) {
                transactions.add(TransactionInfo.fromBlotterCursor(cursor));
            }
            return transactions;
        } finally {
            cursor.close();
        }
    }

}
