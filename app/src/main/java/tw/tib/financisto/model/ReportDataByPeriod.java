package tw.tib.financisto.model;

import static tw.tib.financisto.db.DatabaseHelper.TRANSACTION_TABLE;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

import tw.tib.financisto.db.DatabaseHelper;
import tw.tib.financisto.db.MyEntityManager;
import tw.tib.financisto.db.DatabaseHelper.AccountColumns;
import tw.tib.financisto.db.DatabaseHelper.TransactionColumns;
import tw.tib.financisto.graph.Report2DChart;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

/**
 * Report data builder that considers filters in a given period.
 * @author Rodrigo
 * @author Abdsandryk
 */
public class ReportDataByPeriod {
	private static final String TAG = "ReportDataByPeriod";

	private static final int RESULT_AMOUNT_COLUMN = 1;
	private static final int RESULT_DATETIME_COLUMN = 2;

	protected Context context;
	
	/*
	 * Period identifiers	 
	 */
	public static final int LAST_QUARTER_PERIOD = 3;
	public static final int LAST_HALF_YEAR_PERIOD = 6;
	public static final int LAST_9_MONTHS_PERIOD = 9;
	public static final int LAST_YEAR_PERIOD = 12;
	public static final int DEFAULT_PERIOD = 6;
	
	/**
	 * The number of months in the report period
	 */
	private int periodLength;
	
	/**
	 * First month of the report period
	 */
	private Calendar startDate;
	
	/**
	 * The minimum monthly result in the report
	 */
	private double min = Double.POSITIVE_INFINITY;
	
	/**
	 * The maximum monthly result in the report
	 */
	private double max = Double.NEGATIVE_INFINITY;
	
	/**
	 * The absolute minimum monthly result in the report (modulus) 
	 */
	private double absMin = Double.POSITIVE_INFINITY;
	
	/**
	 * The absolute maximum monthly result in the report (modulus)
	 */
	private double absMax = Double.NEGATIVE_INFINITY;
	
	/**
	 * The minimum monthly result in the report
	 */
	private double minNonNull = Double.POSITIVE_INFINITY;
	
	/**
	 * The maximum monthly result in the report
	 */
	private double maxNonNull = Double.NEGATIVE_INFINITY;
	
	/**
	 * The absolute minimum monthly result in the report, excluding null values (zero)
	 */
	private double absMinNonNull = Double.POSITIVE_INFINITY;
	
	/**
	 * The absolute maximum monthly result in the report, excluding null values (zero)
	 */
	private double absMaxNonNull = Double.NEGATIVE_INFINITY;
	
	/**
	 * The sum of monthly results in the report
	 */
	private double sum = 0;
	
	/**
	 * The mean of monthly results in the report
	 */
	private double mean = 0;
	
	/**
	 * The mean of monthly results in the report, excluding months with null value (zero)
	 */
	private double meanNonNull = 0;
	
	/**
	 * The data points of the report (period x monthly result)
	 */
	private List<PeriodValue> values = new ArrayList<PeriodValue>();
	
	/**
	 * Constructor for report data builder that considers filters in a given period.
	 * @param periodLength The number of months in the report period
	 * @param currency The report reference currency
	 * @param filterColumn The report filtering column in transactions table 
	 * @param filterId The report filtering id in transactions table 
	 * @param em Database adapter to query data
	 */
	public ReportDataByPeriod(Context context, int periodLength, Currency currency, String filterColumn, long[] filterId, MyEntityManager em) {
		Calendar startPeriod = Report2DChart.getDefaultStartPeriod(periodLength);
		init(context, startPeriod, periodLength, currency, filterColumn, filterId, em);
	}
	
	/**
	 * Constructor for report data builder that considers filters in a given period.
	 * @param periodLength The number of months in the report period
	 * @param currency The report reference currency
	 * @param filterColumn The report filtering column in transactions table 
	 * @param filterId The report filtering id in transactions table 
	 * @param em Database adapter to query data
	 */
	public ReportDataByPeriod(Context context, int periodLength, Currency currency, String filterColumn, long filterId, MyEntityManager em) {
		Calendar startPeriod = Report2DChart.getDefaultStartPeriod(periodLength);
		init(context, startPeriod, periodLength, currency, filterColumn, new long[]{filterId}, em);
	}

	/**
	 * Constructor for report data builder that considers filters in a given period.
	 * @param startDate The first month of the report period
	 * @param periodLength The number of months in the report period
	 * @param currency The report reference currency
	 * @param filterColumn The report filtering column in transactions table 
	 * @param filterId The report filtering id in transactions table 
	 * @param em Database adapter to query data
	 */
	public ReportDataByPeriod(Context context, Calendar startDate, int periodLength, Currency currency, String filterColumn, long[] filterId, MyEntityManager em) {
		init(context, startDate, periodLength, currency, filterColumn, filterId, em);
	}
	
	/**
	 * Constructor for report data builder that considers filters in a given period.
	 * @param startDate The first month of the report period
	 * @param periodLength The number of months in the report period
	 * @param currency The report reference currency
	 * @param filterColumn The report filtering column in transactions table 
	 * @param filterId The report filtering id in transactions table 
	 * @param em Database adapter to query data
	 */
	public ReportDataByPeriod(Context context, Calendar startDate, int periodLength, Currency currency, String filterColumn, long filterId, MyEntityManager em) {
		init(context, startDate, periodLength, currency, filterColumn, new long[]{filterId}, em);
	}
	
	/**
	 * Initialize data.
	 * @param startDate The first month of the report period
	 * @param periodLength The number of months in the report period
	 * @param currency The report reference currency
	 * @param filterColumn The report filtering column in transactions table 
	 * @param filterId The report filtering id in transactions table 
	 * @param em Database adapter to query data
	 */
	private void init(Context context, Calendar startDate, int periodLength, Currency currency, String filterColumn, long[] filterId, MyEntityManager em) {
		this.context = context;
		this.periodLength = periodLength;
		startDate.set(startDate.get(Calendar.YEAR), startDate.get(Calendar.MONTH), 01, 00, 00, 00);
		this.startDate = startDate;
		
		SQLiteDatabase db = em.db();
		Cursor cursor = null;

		fillEmptyList(startDate, periodLength);

		try {
			long t0 = System.nanoTime();
			// search accounts for which the reference currency is the given currency
			int[] accounts = getAccountsByCurrency(currency, db);
			if (accounts.length==0) {
				max=min=0;
				absMax=absMin=0;
				return;
			}
			
			// prepare query based on given report parameters
			String where = getWhereClause(filterColumn, filterId, accounts);
			String[] args = getWhereArgs(startDate, periodLength, filterId, accounts);
			// query data
			cursor = db.query(TRANSACTION_TABLE, new String[]{filterColumn, TransactionColumns.from_amount.name(), TransactionColumns.datetime.name()},
					   where, args, null, null, TransactionColumns.datetime.name());
			long t1 = System.nanoTime();
			// extract data and fill statistics
			extractData(cursor);
			long t2 = System.nanoTime();
			Log.d(TAG, "query=" + (t1-t0) + " ns, extract=" + (t2-t1) + " ns");

		} finally {
			if (cursor!=null) cursor.close();
		}
	}

	/**
	 * Build a where clause based on the following format:
	 * <filteredColumn>=? and (datetime>=? and datetime<=?) and (from_account_id=? or from_account_id=? ...)
	 * 
	 * @param filterColumn The report filter (account, category, location or project)
	 * @param accounts List of account ids for which the reference currency is the report reference currency.
	 * */
	private String getWhereClause(String filterColumn, long[] filterId, int[] accounts) {
		StringBuffer accountsWhere = new StringBuffer();
		// no templates and scheduled transactions
		// don't include transfer to other accounts (transfers to this account is inherently not included)
		accountsWhere.append(TransactionColumns.is_template + "=0 and " + TransactionColumns.to_account_id + "=0");
		
		// report filtering (account, category, location or project)
		accountsWhere.append(" and (");
		for (int i=0;i<filterId.length;i++) 
		{
			if(i!=0)
				accountsWhere.append(" or ");
			accountsWhere.append(filterColumn+"=? ");
		}
		accountsWhere.append(")");
		
		// period
		accountsWhere.append(" and ("+TransactionColumns.datetime +">=? and "+TransactionColumns.datetime +"<?)");
		
		// list of accounts for which the reference currency is the report reference currency
		if(accounts.length>0)
			accountsWhere.append(" and (");
		for (int i=0; i<accounts.length; i++)
		{
			if(i!=0)
				accountsWhere.append(" or ");
			accountsWhere.append(TransactionColumns.from_account_id +"=? ");
		}
		if(accounts.length>0)
			accountsWhere.append(")");
		return accountsWhere.toString();
	}
	
	/**
	 * Build the arguments of the where clause based on the following format:
	 * <filteredColumn>=? and (datetime>=? and datetime<=?) and (from_account_id=? or from_account_id=? ...)
	 * 
	 * @param startDate The first month of the report period
	 * @param periodLength The number of months of the report period
	 * @param filterId The id of the filtering column (account, category, location or project)
	 * @param accounts The ids of accounts to be considered in this query
	 * */
	private String[] getWhereArgs(Calendar startDate, int periodLength, long[] filterId, int[] accounts) {
		String[] args = new String[filterId.length + 2 + accounts.length];

		// The id of the filtered column
		int i=0;
		for (i=0; i<filterId.length ;i++)
			args[i] = Long.toString(filterId[i]);
		
		// The first month of the period in time millis
		args[i] = String.valueOf(startDate.getTimeInMillis());
		
		// The last month of the period in time millis
		i++;
		Calendar endDate = new GregorianCalendar(startDate.get(Calendar.YEAR), startDate.get(Calendar.MONTH), 1, 0, 0, 0);
		endDate.add(Calendar.MONTH, periodLength);
		args[i] = String.valueOf(endDate.getTimeInMillis());
		
		// Account ids to be considered in this query due the report reference currency
		for (int j=0; j<accounts.length; j++) {
			args[j+i+1] = Long.toString(accounts[j]);
		}
		
		return args;
	}

	/**
	 * Fill the list of periodValues with the corresponding month and 0 for value.
	 * 
	 * @param startDate The first month of the period
	 * @param periodLength The number of months in the report period
	 */
	private void fillEmptyList(Calendar startDate, int periodLength) {
		Calendar month;
		for(int index=0; index<periodLength; index++) {
			month = new GregorianCalendar(startDate.get(Calendar.YEAR), startDate.get(Calendar.MONTH)+index, 1, 0, 0, 0);
			PeriodValue periodValue = new PeriodValue(month, 0);
			values.add(periodValue);
		}		
	}

	/**
	 * Build data points to plot chart.
	 * 
	 * @param c Cursor with filtered transactions data.
	 */
	private void extractData(Cursor c) {
	/*
	 * Algorithm
	 *   Transactions data are ordered by date: newer first.
	 *   The first loop threat month by month, the second one threat the monthly transactions in order to get the monthly result.
	 *   When a transaction of a different month comes, change month and step into first loop.
	 *   After getting data, generate statistics based on results.
	 * */
		// First loop: month by month
		while (c.moveToNext()) {
			
			// get month of reference  
			Calendar month = getMonthInTransaction(c);
			
			double result=0;
			boolean stepMonth = false;
			// get result from transactions in the reference month
			do {
				Calendar transactionMonth = getMonthInTransaction(c);
 				if(transactionMonth.compareTo(month)!=0) {
 					stepMonth = true;
					break;
				}
 				result += c.getDouble(RESULT_AMOUNT_COLUMN);
			} while(c.moveToNext());
			
			// If step month, get back to transaction of the new month in cursor.
			if (stepMonth)
				c.moveToPrevious();
			
			// store the result of the month
			PeriodValue periodValue = new PeriodValue(month, result);
			int monthPosition = (month.get(Calendar.YEAR)-startDate.get(Calendar.YEAR))*12+
								 month.get(Calendar.MONTH)-startDate.get(Calendar.MONTH);
			// FIXME date range edge case
			if (monthPosition < values.size()) {
				values.set(monthPosition, periodValue);
			}
			else {
				values.add(periodValue);
			}
		}
		
		// Generating statistics
		max = values.get(0).getValue();
		min = values.get(0).getValue();
		absMax = Math.abs(values.get(0).getValue());
		absMin = Math.abs(values.get(0).getValue());
		minNonNull = Double.POSITIVE_INFINITY;
		maxNonNull = Double.NEGATIVE_INFINITY;
		absMinNonNull = Double.POSITIVE_INFINITY;
		absMaxNonNull = Double.NEGATIVE_INFINITY;
		mean = 0;
		sum = 0;
		double res = 0;
		double absRes = 0;
		int nonNull = 0;

		for (int i=0; i<values.size(); i++) {
			res = values.get(i).getValue();
			absRes = Math.abs(res);
			if (res!=0) {
				nonNull++;
				maxNonNull = res>maxNonNull?res:maxNonNull;
				minNonNull = res<minNonNull?res:minNonNull;
				absMaxNonNull = absRes>absMaxNonNull?absRes:absMaxNonNull;
				absMinNonNull = absRes<absMinNonNull?absRes:absMinNonNull;
			}
			max = max>res?max:res;
			min = min<res?min:res;
			mean += res;
			absMax = absMax>absRes?absMax:absRes;
			absMin = absMin<absRes?absMin:absRes;
		}
		sum = mean;
		mean = sum/values.size();
		meanNonNull = sum/nonNull;
		if (nonNull==0) {
			// no date to plot - set data to display statistics
			minNonNull = 0;
			maxNonNull = 0;
			absMinNonNull = 0;
			absMaxNonNull = 0;
			meanNonNull = 0;
		}
	}
	
	/**
	 * Get the month of a given transaction in the given cursor.
	 * @param c The transactions cursor.
	 * @return The Calendar month.
	 */
	private Calendar getMonthInTransaction(Cursor c) {
		Calendar month = new GregorianCalendar();
		month.setTimeInMillis(c.getLong(RESULT_DATETIME_COLUMN));
		month.set(month.get(Calendar.YEAR), month.get(Calendar.MONTH), 1, 0, 0, 0);
		month.set(Calendar.MILLISECOND, 0);
		return month;
	}
		
	/**
	 * Get the accounts in database for which the reference currency is the given currency.
	 * 
	 * @param currency The report reference currency.
	 * @param db Database to query data from.
	 * 
	 * @return A list of ids of accounts for which the reference currency is the given report currency.
	 */
	public int[] getAccountsByCurrency(Currency currency, SQLiteDatabase db)
	{
		int accounts[] = new int[0];
		
		String where = AccountColumns.CURRENCY_ID+"=?";
		Cursor c = null;
		try {
			c = db.query(DatabaseHelper.ACCOUNT_TABLE, new String[]{AccountColumns.ID}, 
					   where, new String[]{Long.toString(currency.id)}, null, null, null);
			accounts = new int[c.getCount()];
			int index=0;
			while (c.moveToNext()) {
				accounts[index] = c.getInt(0);
				index++;
			} 
		} finally {
			if(c!=null) c.close();
		}
		return accounts;
	}
	
	/**
	 * @return The list of data points (month period and value)
	 */
	public List<PeriodValue> getPeriodValues() {
		return this.values;
	}

	/**
	 * @return The maximum monthly result in the report
	 */
	public double getMaxValue() {
		return max;
	}
	
	/**
	 * @return The absolute maximum monthly result in the report (modulus)
	 */
	public double getAbsoluteMaxValue() {
		return absMax;
	}
	
	/**
	 * @return  The maximum monthly result in the report (modulus), excluding null values (zero)
	 */
	public double getMaxExcludingNulls() {
		return maxNonNull;
	}
	
	/**
	 * @return  The absolute maximum monthly result in the report (modulus), excluding null values (zero)
	 */
	public double getAbsoluteMaxExcludingNulls() {
		return absMaxNonNull;
	}

	/**
	 * @return The mean of monthly results in the report
	 */
	public double getMean() {
		return mean;
	}
	
	/**
	 * @return The mean of monthly results in the report, excluding months with null values (zero)
	 */
	public double getMeanExcludingNulls() {
		return meanNonNull;
	}

	/**
	 * @return The minimum monthly result in the report
	 */
	public double getMinValue() {
		return min;
	}

	/**
	 * @return  The absolute minimum monthly result in the report (modulus)
	 */
	public double getAbsoluteMinValue() {
		return absMin;
	}
	
	/**
	 * @return  The minimum monthly result in the report (modulus), excluding null values (zero)
	 */
	public double getMinExcludingNulls() {
		return minNonNull;
	}
	
	/**
	 * @return  The absolute minimum monthly result in the report (modulus), excluding null values (zero)
	 */
	public double getAbsoluteMinExcludingNulls() {
		return absMinNonNull;
	}
	
	/**
	 * @return The sum of monthly results in the report
	 */
	public double getSum() {
		return sum;
	}	

	/**
	 * @return The number of months in the report period
	 */
	public int getPeriodLength() {
		return this.periodLength;
	}

	/**
	 * @return The first month of the report period.
	 */
	public Calendar getStartPeriod() {
		return this.startDate;
	}

}
