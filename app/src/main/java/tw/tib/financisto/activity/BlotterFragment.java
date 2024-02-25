package tw.tib.financisto.activity;

import static android.app.Activity.RESULT_FIRST_USER;
import static android.app.Activity.RESULT_OK;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ListAdapter;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.Lifecycle;
import androidx.loader.content.Loader;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import greendroid.widget.QuickActionGrid;
import greendroid.widget.QuickActionWidget;
import tw.tib.financisto.R;
import tw.tib.financisto.adapter.BlotterListAdapter;
import tw.tib.financisto.adapter.TransactionsListAdapter;
import tw.tib.financisto.blotter.AccountTotalCalculationTask;
import tw.tib.financisto.blotter.BlotterFilter;
import tw.tib.financisto.blotter.BlotterTotalCalculationTask;
import tw.tib.financisto.blotter.TotalCalculationTask;
import tw.tib.financisto.dialog.TransactionInfoDialog;
import tw.tib.financisto.filter.Criteria;
import tw.tib.financisto.filter.WhereFilter;
import tw.tib.financisto.db.DatabaseAdapter;
import tw.tib.financisto.model.Account;
import tw.tib.financisto.model.AccountType;
import tw.tib.financisto.model.Budget;
import tw.tib.financisto.model.Transaction;
import tw.tib.financisto.utils.IntegrityCheckRunningBalance;
import tw.tib.financisto.utils.MenuItemInfo;
import tw.tib.financisto.utils.MyPreferences;
import tw.tib.financisto.utils.PinProtection;
import tw.tib.financisto.view.NodeInflater;

public class BlotterFragment extends AbstractListFragment implements BlotterOperations.BlotterOperationsCallback {
    private static final String TAG = "BlotterFragment";
    public static final String SAVE_FILTER = "saveFilter";
    public static final String EXTRA_FILTER_ACCOUNTS = "filterAccounts";
    public static final String GO_TO_TRANSACTION = "goToTransaction";

    private static final int NEW_TRANSACTION_REQUEST = 1;
    private static final int NEW_TRANSFER_REQUEST = 3;
    private static final int NEW_TRANSACTION_FROM_TEMPLATE_REQUEST = 5;
    private static final int MONTHLY_VIEW_REQUEST = 6;
    private static final int BILL_PREVIEW_REQUEST = 7;

    protected static final int FILTER_REQUEST = 6;
    private static final int MENU_DUPLICATE = MENU_ADD + 1;
    private static final int MENU_SAVE_AS_TEMPLATE = MENU_ADD + 2;
    private static final int MENU_SHOW_IN_ACCOUNT_BLOTTER = MENU_ADD + 3;

    protected TextView totalText;
    protected TextView emptyText;
    protected ProgressBar progressBar;

    protected ImageButton bFilter;
    protected ImageButton bTransfer;
    protected ImageButton bTemplate;
    protected ImageButton bSearch;
//    protected ImageButton bMenu;

    protected QuickActionGrid transactionActionGrid;
    protected QuickActionGrid addButtonActionGrid;

    private TotalCalculationTask calculationTask;

    protected boolean saveFilter;
    protected WhereFilter blotterFilter = WhereFilter.empty();

    protected static final long BEFORE_INITIAL_LOAD = -1;
    protected long lastTxId = BEFORE_INITIAL_LOAD;

    protected boolean isAccountBlotter = false;
    protected boolean showAllBlotterButtons = true;
    protected boolean isQuickMenuEnabledForTransaction = false;
    protected boolean isQuickMenuShowAdditionalTransactionStatus = false;
    protected boolean isQuickMenuShowDuplicateKeepTime = false;
    protected boolean isQuickMenuShowDuplicateKeepDateTime = false;

    protected OnBackPressedCallback backCallback;

    private static final Pattern amountSearchPattern = Pattern.compile("^([<>])?(\\d+(?:\\.\\d+)?)(?:~(\\d+(?:\\.\\d+)?))?$");

    private NodeInflater inflater;
    private long selectedId = -1;

    public BlotterFragment(int layoutId) {
        super(layoutId);
    }

    public BlotterFragment() {
        super(R.layout.blotter);
    }

    public BlotterFragment(boolean saveFilter) {
        super(R.layout.blotter);
        this.saveFilter = saveFilter;
    }

    protected void calculateTotals(WhereFilter filter) {
        if (calculationTask != null) {
            calculationTask.stop();
            calculationTask.cancel(true);
        }
        calculationTask = createTotalCalculationTask(filter);
        calculationTask.execute();
    }

    protected TotalCalculationTask createTotalCalculationTask(WhereFilter filter) {
        if (filter.getAccountId() > 0) {
            return new AccountTotalCalculationTask(getContext(), db, filter, totalText);
        } else {
            return new BlotterTotalCalculationTask(getContext(), db, filter, totalText);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater layoutInflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = super.onCreateView(layoutInflater, container, savedInstanceState);
        inflater = new NodeInflater(layoutInflater);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        integrityCheck();

        backCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                FrameLayout searchLayout = view.findViewById(R.id.search_text_frame);
                if (searchLayout != null && searchLayout.getVisibility() == View.VISIBLE) {
                    searchLayout.setVisibility(View.GONE);
                    this.setEnabled(false);
                }
            }
        };

        getActivity().getOnBackPressedDispatcher().addCallback(backCallback);

        showAllBlotterButtons = !MyPreferences.isCollapseBlotterButtons(getContext());

        isQuickMenuEnabledForTransaction = MyPreferences.isQuickMenuEnabledForTransaction(getContext());
        isQuickMenuShowAdditionalTransactionStatus = MyPreferences.isQuickMenuShowAdditionalTransactionStatus(getContext());
        isQuickMenuShowDuplicateKeepTime = MyPreferences.isQuickMenuShowDuplicateKeepTime(getContext());
        isQuickMenuShowDuplicateKeepDateTime = MyPreferences.isQuickMenuShowDuplicateKeepDateTime(getContext());

        if (showAllBlotterButtons) {
            bTransfer = view.findViewById(R.id.bTransfer);
            if (bTransfer != null) {
                bTransfer.setVisibility(View.VISIBLE);
                bTransfer.setOnClickListener(arg0 -> addItem(NEW_TRANSFER_REQUEST, TransferActivity.class));
            }

            bTemplate = view.findViewById(R.id.bTemplate);
            if (bTemplate != null) {
                bTemplate.setVisibility(View.VISIBLE);
                bTemplate.setOnClickListener(v -> createFromTemplate());
            }
        }

        bFilter = view.findViewById(R.id.bFilter);
        if (bFilter != null) {
            bFilter.setOnClickListener(v -> {
                Intent intent = new Intent(getContext(), BlotterFilterActivity.class);
                blotterFilter.toIntent(intent);
                intent.putExtra(BlotterFilterActivity.IS_ACCOUNT_FILTER, isAccountBlotter && blotterFilter.getAccountId() > 0);
                startActivityForResult(intent, FILTER_REQUEST);
            });
        }

        totalText = view.findViewById(R.id.total);
        if (totalText != null) {
            totalText.setOnClickListener(v -> showTotals());
        }

        emptyText = view.findViewById(android.R.id.empty);
        progressBar = view.findViewById(android.R.id.progress);

        Bundle args = getArguments();
        if (args != null) {
            blotterFilter = WhereFilter.fromBundle(args);
            isAccountBlotter = args.getBoolean(BlotterFilterActivity.IS_ACCOUNT_FILTER, false);
        }
        if (savedInstanceState != null) {
            blotterFilter = WhereFilter.fromBundle(savedInstanceState);
        }
        if (saveFilter && blotterFilter.isEmpty()) {
            blotterFilter = WhereFilter.fromSharedPreferences(getContext().getSharedPreferences(this.getClass().getName(), 0));
        }

        bSearch = view.findViewById(R.id.bSearch);
        if (bSearch != null) {
            bSearch.setOnClickListener(method -> {
                EditText searchText = view.findViewById(R.id.search_text);
                FrameLayout searchLayout = view.findViewById(R.id.search_text_frame);
                ImageButton searchTextClearButton = view.findViewById(R.id.search_text_clear);
                InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);

                searchText.setOnFocusChangeListener((v, b) -> {
                    if (!v.hasFocus()) {
                        imm.hideSoftInputFromWindow(searchLayout.getWindowToken(), 0);
                    }
                });

                searchTextClearButton.setOnClickListener(v -> {
                    searchText.setText("");
                });

                if (searchLayout.getVisibility() == View.VISIBLE) {
                    imm.hideSoftInputFromWindow(searchLayout.getWindowToken(), 0);
                    searchLayout.setVisibility(View.GONE);
                    backCallback.setEnabled(false);
                    return;
                }

                searchLayout.setVisibility(View.VISIBLE);
                searchText.requestFocusFromTouch();
                imm.showSoftInput(searchText, InputMethodManager.SHOW_IMPLICIT);
                backCallback.setEnabled(true);

                searchText.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                    }

                    @Override
                    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                    }

                    @Override
                    public void afterTextChanged(Editable editable) {
                        ImageButton clearButton = view.findViewById(R.id.search_text_clear);
                        String text = editable.toString();
                        blotterFilter.remove(BlotterFilter.NOTE);
                        while (blotterFilter.remove(BlotterFilter.FROM_AMOUNT) != null);
                        while (blotterFilter.remove(BlotterFilter.ORIGINAL_FROM_AMOUNT) != null);

                        if (!text.isEmpty()) {
                            Criteria amount = null;
                            Matcher m = amountSearchPattern.matcher(text);
                            if (m.matches()) {
                                if (m.group(1) == null && m.group(3) == null) {
                                    // 123.45
                                    String val = Double.toString(Math.floor(Double.parseDouble(m.group(2)) * 100));
                                    amount = Criteria.or(
                                            Criteria.eq(BlotterFilter.FROM_AMOUNT, val),
                                            Criteria.eq(BlotterFilter.FROM_AMOUNT, "-" + val),
                                            Criteria.eq(BlotterFilter.ORIGINAL_FROM_AMOUNT, val),
                                            Criteria.eq(BlotterFilter.ORIGINAL_FROM_AMOUNT, "-" + val));
                                }
                                else if (m.group(3) == null) {
                                    // >123.45, <123.45
                                    String val = Double.toString(Math.floor(Double.parseDouble(m.group(2)) * 100));
                                    if (m.group(1).equals("<")) {
                                        amount = Criteria.or(
                                                Criteria.and(
                                                        Criteria.lt(BlotterFilter.FROM_AMOUNT, val),
                                                        Criteria.gt(BlotterFilter.FROM_AMOUNT, "0")),
                                                Criteria.and(
                                                        Criteria.gt(BlotterFilter.FROM_AMOUNT, "-" + val),
                                                        Criteria.lt(BlotterFilter.FROM_AMOUNT, "0")),
                                                Criteria.and(
                                                        Criteria.lt(BlotterFilter.ORIGINAL_FROM_AMOUNT, val),
                                                        Criteria.gt(BlotterFilter.ORIGINAL_FROM_AMOUNT, "0")),
                                                Criteria.and(
                                                        Criteria.gt(BlotterFilter.ORIGINAL_FROM_AMOUNT, "-" + val),
                                                        Criteria.lt(BlotterFilter.ORIGINAL_FROM_AMOUNT, "0")));
                                    }
                                    else if (m.group(1).equals(">")) {
                                        amount = Criteria.or(
                                                Criteria.gt(BlotterFilter.FROM_AMOUNT, val),
                                                Criteria.lt(BlotterFilter.FROM_AMOUNT, "-" + val),
                                                Criteria.gt(BlotterFilter.ORIGINAL_FROM_AMOUNT, val),
                                                Criteria.lt(BlotterFilter.ORIGINAL_FROM_AMOUNT, "-" + val));
                                    }
                                }
                                else if (m.group(1) == null) {
                                    // 100~900
                                    String val2 = Double.toString(Math.floor(Double.parseDouble(m.group(2)) * 100));
                                    String val3 = Double.toString(Math.floor(Double.parseDouble(m.group(3)) * 100));
                                    amount = Criteria.or(
                                            Criteria.btw(BlotterFilter.FROM_AMOUNT, val2, val3),
                                            Criteria.btw(BlotterFilter.FROM_AMOUNT, "-" + val3, "-" + val2),
                                            Criteria.btw(BlotterFilter.ORIGINAL_FROM_AMOUNT, val2, val3),
                                            Criteria.btw(BlotterFilter.ORIGINAL_FROM_AMOUNT, "-" + val3, "-" + val2));
                                }
                            }
                            String likePattern = String.format("%%%s%%", text);
                            if (amount == null) {
                                blotterFilter.eq(Criteria.or(
                                        Criteria.like(BlotterFilter.NOTE, likePattern),
                                        Criteria.like(BlotterFilter.PAYEE, likePattern)
                                ));
                            }
                            else {
                                blotterFilter.eq(Criteria.or(
                                        amount,
                                        Criteria.like(BlotterFilter.NOTE, likePattern),
                                        Criteria.like(BlotterFilter.PAYEE, likePattern)
                                ));
                            }


                            clearButton.setVisibility(View.VISIBLE);
                        } else {
                            clearButton.setVisibility(View.GONE);
                        }

                        recreateCursor();
                        applyFilter();
                        saveFilter();
                    }
                });

                if (blotterFilter.get(BlotterFilter.NOTE) != null) {
                    String searchFilterText = blotterFilter.get(BlotterFilter.NOTE).getStringValue();
                    if (!searchFilterText.isEmpty()) {
                        searchFilterText = searchFilterText.substring(1, searchFilterText.length() - 1);
                        searchText.setText(searchFilterText);
                    }
                }
            });
        }

        applyFilter();
        prepareTransactionActionGrid();
        prepareAddButtonActionGrid();

        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                if (isAccountBlotter) {
                    long accountId = blotterFilter.getAccountId();
                    if (accountId != -1) {
                        // get account type
                        Account account = db.getAccount(accountId);
                        AccountType type = AccountType.valueOf(account.type);
                        if (type.isCreditCard) {
                            // Show menu for Credit Cards - bill
                            menuInflater.inflate(R.menu.ccard_blotter_menu, menu);
                        } else {
                            // Show menu for other accounts - monthly view
                            menuInflater.inflate(R.menu.blotter_menu, menu);
                        }
                    }
                }
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                long accountId = blotterFilter.getAccountId();
                Intent intent = new Intent(getContext(), MonthlyViewActivity.class);
                intent.putExtra(MonthlyViewActivity.ACCOUNT_EXTRA, accountId);

                switch (menuItem.getItemId()) {
                    case R.id.opt_menu_month:
                        // call credit card bill activity sending account id
                        intent.putExtra(MonthlyViewActivity.BILL_PREVIEW_EXTRA, false);
                        startActivityForResult(intent, MONTHLY_VIEW_REQUEST);
                        return true;

                    case R.id.opt_menu_bill:
                        if (accountId != -1) {
                            Account account = db.getAccount(accountId);

                            // call credit card bill activity sending account id
                            if (account.paymentDay > 0 && account.closingDay > 0) {
                                intent.putExtra(MonthlyViewActivity.BILL_PREVIEW_EXTRA, true);
                                startActivityForResult(intent, BILL_PREVIEW_REQUEST);
                            } else {
                                // display message: need payment and closing day
                                AlertDialog.Builder dlgAlert = new AlertDialog.Builder(getContext());
                                dlgAlert.setMessage(R.string.statement_error);
                                dlgAlert.setTitle(R.string.ccard_statement);
                                dlgAlert.setPositiveButton(R.string.ok, null);
                                dlgAlert.setCancelable(true);
                                dlgAlert.create().show();
                            }
                        }
                        return true;
                }
                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    private void showTotals() {
        Intent intent = new Intent(getContext(), BlotterTotalsDetailsActivity.class);
        blotterFilter.toIntent(intent);
        startActivityForResult(intent, -1);
    }

    protected void prepareTransactionActionGrid() {
        transactionActionGrid = new QuickActionGrid(getContext());
        transactionActionGrid.addQuickAction(new MyQuickAction(getContext(), R.drawable.ic_action_info, R.string.info));
        transactionActionGrid.addQuickAction(new MyQuickAction(getContext(), R.drawable.ic_action_edit, R.string.edit));
        transactionActionGrid.addQuickAction(new MyQuickAction(getContext(), R.drawable.ic_action_trash, R.string.delete));
        if (isQuickMenuShowAdditionalTransactionStatus) {
            transactionActionGrid.addQuickAction(new MyQuickAction(getContext(), R.drawable.ic_action_status_restored, MyQuickAction.NO_FILTER, R.string.transaction_status_restored));
            transactionActionGrid.addQuickAction(new MyQuickAction(getContext(), R.drawable.ic_action_status_pending, MyQuickAction.NO_FILTER, R.string.transaction_status_pending));
            transactionActionGrid.addQuickAction(new MyQuickAction(getContext(), R.drawable.ic_action_status_empty, MyQuickAction.NO_FILTER, R.string.transaction_status_unreconciled));
        }
        transactionActionGrid.addQuickAction(new MyQuickAction(getContext(), R.drawable.ic_action_copy, R.string.duplicate));
        transactionActionGrid.addQuickAction(new MyQuickAction(getContext(), R.drawable.ic_action_status_cleared, MyQuickAction.NO_FILTER, R.string.clear));
        transactionActionGrid.addQuickAction(new MyQuickAction(getContext(), R.drawable.ic_action_status_reconciled, MyQuickAction.NO_FILTER, R.string.reconcile));
        if (isQuickMenuShowDuplicateKeepTime) {
            transactionActionGrid.addQuickAction(new MyQuickAction(getContext(), R.drawable.ic_action_copy_keep_time, MyQuickAction.NO_FILTER, R.string.duplicate_keep_time));
        }
        if (isQuickMenuShowDuplicateKeepDateTime) {
            transactionActionGrid.addQuickAction(new MyQuickAction(getContext(), R.drawable.ic_action_copy_keep_time, getResources().getColor(R.color.holo_orange_dark), R.string.duplicate_keep_date_time));
        }
        transactionActionGrid.setOnQuickActionClickListener(transactionActionListener);
    }

    private QuickActionWidget.OnQuickActionClickListener transactionActionListener = (widget, position, action) -> {
        int titleId = ((MyQuickAction) action).titleId;

        if (titleId == R.string.info) {
            showTransactionInfo(selectedId);
        }
        else if (titleId == R.string.edit) {
            editTransaction(selectedId);
        }
        else if (titleId == R.string.delete) {
            deleteTransaction(selectedId);
        }
        else if (titleId == R.string.transaction_status_restored) {
            restoreTransaction(selectedId);
        }
        else if (titleId == R.string.transaction_status_pending) {
            pendingTransaction(selectedId);
        }
        else if (titleId == R.string.transaction_status_unreconciled) {
            unreconcileTransaction(selectedId);
        }
        else if (titleId == R.string.duplicate) {
            duplicateTransaction(selectedId, 1);
        }
        else if (titleId == R.string.clear) {
            clearTransaction(selectedId);
        }
        else if (titleId == R.string.reconcile) {
            reconcileTransaction(selectedId);
        }
        else if (titleId == R.string.duplicate_keep_time) {
            duplicateTransactionKeepTime(selectedId);
        }
        else if (titleId == R.string.duplicate_keep_date_time) {
            duplicateTransactionKeepDateTime(selectedId);
        }
    };

    private void prepareAddButtonActionGrid() {
        addButtonActionGrid = new QuickActionGrid(getContext());
        addButtonActionGrid.addQuickAction(new MyQuickAction(getContext(), R.drawable.actionbar_add_big, R.string.transaction));
        addButtonActionGrid.addQuickAction(new MyQuickAction(getContext(), R.drawable.ic_action_transfer, R.string.transfer));
        if (addTemplateToAddButton()) {
            addButtonActionGrid.addQuickAction(new MyQuickAction(getContext(), R.drawable.actionbar_tiles_large, R.string.template));
        } else {
            addButtonActionGrid.setNumColumns(2);
        }
        addButtonActionGrid.setOnQuickActionClickListener(addButtonActionListener);
    }

    protected boolean addTemplateToAddButton() {
        return true;
    }

    private QuickActionWidget.OnQuickActionClickListener addButtonActionListener = (widget, position, action) -> {
        switch (position) {
            case 0:
                addItem(NEW_TRANSACTION_REQUEST, TransactionActivity.class);
                break;
            case 1:
                addItem(NEW_TRANSFER_REQUEST, TransferActivity.class);
                break;
            case 2:
                createFromTemplate();
                break;
        }
    };

    private void restoreTransaction(long selectedId) {
        new BlotterOperations(getContext(), this, db, selectedId).restoreTransaction();
        recreateCursor();
    }

    private void pendingTransaction(long selectedId) {
        new BlotterOperations(getContext(), this, db, selectedId).pendingTransaction();
        recreateCursor();
    }

    private void unreconcileTransaction(long selectedId) {
        new BlotterOperations(getContext(), this, db, selectedId).unreconcileTransaction();
        recreateCursor();
    }

    private void clearTransaction(long selectedId) {
        new BlotterOperations(getContext(), this, db, selectedId).clearTransaction();
        recreateCursor();
    }

    private void reconcileTransaction(long selectedId) {
        new BlotterOperations(getContext(), this, db, selectedId).reconcileTransaction();
        recreateCursor();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        blotterFilter.toBundle(outState);
    }

    protected void createFromTemplate() {
        Intent intent = new Intent(getContext(), SelectTemplateActivity.class);
        startActivityForResult(intent, NEW_TRANSACTION_FROM_TEMPLATE_REQUEST);
    }

    @Override
    protected List<MenuItemInfo> createContextMenus(long id) {
        if (blotterFilter.isTemplate() || blotterFilter.isSchedule()) {
            return super.createContextMenus(id);
        } else {
            List<MenuItemInfo> menus = super.createContextMenus(id);
            menus.add(new MenuItemInfo(MENU_DUPLICATE, R.string.duplicate));
            menus.add(new MenuItemInfo(MENU_SAVE_AS_TEMPLATE, R.string.save_as_template));
            menus.add(new MenuItemInfo(MENU_SHOW_IN_ACCOUNT_BLOTTER, R.string.transaction_show_in_account_blotter));
            return menus;
        }
    }

    @Override
    public boolean onPopupItemSelected(int itemId, View view, int position, long id) {
        if (!super.onPopupItemSelected(itemId, view, position, id)) {
            switch (itemId) {
                case MENU_DUPLICATE:
                    duplicateTransaction(id, 1);
                    return true;
                case MENU_SAVE_AS_TEMPLATE:
                    new BlotterOperations(getContext(), this, db, id).duplicateAsTemplate();
                    Toast.makeText(getContext(), R.string.save_as_template_success, Toast.LENGTH_SHORT).show();
                    return true;
                case MENU_SHOW_IN_ACCOUNT_BLOTTER:
                    Transaction t = db.getTransaction(id);
                    Account a = db.getAccount(t.fromAccountId);
                    Intent intent = new Intent(getContext(), BlotterActivity.class);
                    Criteria.eq(BlotterFilter.FROM_ACCOUNT_ID, String.valueOf(a.id))
                            .toIntent(a.title, intent);
                    intent.putExtra(BlotterFilterActivity.IS_ACCOUNT_FILTER, true);
                    intent.putExtra(GO_TO_TRANSACTION, id);
                    startActivity(intent);
                    return true;
            }
        }
        return false;
    }

    private long duplicateTransactionKeepTime(long id) {
        return duplicateTransaction(id, 1, KeepTime.KEEP_TIME);
    }

    private long duplicateTransactionKeepDateTime(long id) {
        return duplicateTransaction(id, 1, KeepTime.KEEP_DATE_TIME);
    }

    private long duplicateTransaction(long id, int multiplier) {
        return duplicateTransaction(id, multiplier, KeepTime.NONE);
    }

    enum KeepTime {
        NONE,
        KEEP_TIME,
        KEEP_DATE_TIME,
    }

    private long duplicateTransaction(long id, int multiplier, KeepTime keepTime) {
        long newId;
        if (keepTime == KeepTime.KEEP_TIME) {
            newId = new BlotterOperations(getContext(), this, db, id).duplicateTransactionKeepTime();
        }
        else if (keepTime == KeepTime.KEEP_DATE_TIME) {
            newId = new BlotterOperations(getContext(), this, db, id).duplicateTransactionKeepDateTime();
        }
        else {
            newId = new BlotterOperations(getContext(), this, db, id).duplicateTransaction(multiplier);
        }
        String toastText;
        if (multiplier > 1) {
            toastText = getString(R.string.duplicate_success_with_multiplier, multiplier);
        } else {
            toastText = getString(R.string.duplicate_success);
        }
        Toast.makeText(getContext(), toastText, Toast.LENGTH_LONG).show();
        recreateCursor();
        AccountWidget.updateWidgets(getContext());
        return newId;
    }

    @Override
    protected void addItem() {
        if (showAllBlotterButtons) {
            addItem(NEW_TRANSACTION_REQUEST, TransactionActivity.class);
        } else {
            addButtonActionGrid.show(bAdd);
        }
    }

    protected void addItem(int requestId, Class<? extends AbstractTransactionActivity> clazz) {
        Intent intent = new Intent(getContext(), clazz);
        long accountId = blotterFilter.getAccountId();
        if (accountId != -1) {
            intent.putExtra(TransactionActivity.ACCOUNT_ID_EXTRA, accountId);
        }
        else {
            long budgetId = blotterFilter.getBudgetId();
            if (budgetId != -1) {
                Budget budget = db.load(Budget.class, budgetId);
                if (budget.account != null) {
                    intent.putExtra(TransactionActivity.ACCOUNT_ID_EXTRA, budget.account.id);
                }
            }
        }

        intent.putExtra(TransactionActivity.TEMPLATE_EXTRA, blotterFilter.getIsTemplate());
        startActivityForResult(intent, requestId);
    }

    @Override
    protected Cursor createCursor() {
        Cursor c;
        WhereFilter blotterFilterCopy = WhereFilter.copyOf(blotterFilter);

        new Handler(Looper.getMainLooper()).post(()-> {
            emptyText.setVisibility(View.GONE);
            progressBar.setVisibility(View.VISIBLE);
            calculateTotals(blotterFilterCopy);
        });

        long t1 = System.nanoTime();
        if (db == null) {
            db = new DatabaseAdapter(getActivity());
            db.open();
        }
        this.lastTxId = db.getLastTransactionId();
        long t2 = System.nanoTime();
        Log.d(TAG, "getLastTransactionId() = " + lastTxId + ", " + String.format("%,d", (t2 - t1)) + " ns");
        long accountId = blotterFilterCopy.getAccountId();
        if (accountId != -1) {
            c = db.getBlotterForAccount(blotterFilterCopy);
        } else {
            c = db.getBlotter(blotterFilterCopy);
        }
        c.getCount();
        Log.d(TAG, "createCursor: " + String.format("%,d", (System.nanoTime() - t1)) + " ns");
        return c;
    }

    @Override
    protected ListAdapter createAdapter(Cursor cursor) {
        ListAdapter a;
        long t1 = System.currentTimeMillis();
        long accountId = blotterFilter.getAccountId();
        if (accountId != -1) {
            a = new TransactionsListAdapter(getContext(), db, cursor);
        } else {
            a = new BlotterListAdapter(getContext(), db, cursor);
        }
        if (a.getCount() == 0) {
            emptyText.setVisibility(View.VISIBLE);
        }
        progressBar.setVisibility(View.GONE);
        Log.d(TAG, "createAdapter: " + (System.currentTimeMillis() - t1) + " ms");
        return a;
    }

    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor data) {
        super.onLoadFinished(loader, data);

        Bundle args = getArguments();
        if (args == null) return;

        long txId = args.getLong(GO_TO_TRANSACTION, -1);
        args.remove(GO_TO_TRANSACTION);

        if (txId != -1) {
            int pos = 0;
            data.moveToFirst();
            while (data.getLong(0) != txId && !data.isAfterLast()) {
                data.moveToNext();
                pos += 1;
            }
            setSelection(pos);
        }
    }

    @Override
    protected void deleteItem(View v, int position, final long id) {
        deleteTransaction(id);
    }

    private void deleteTransaction(long id) {
        new BlotterOperations(getContext(), this, db, id).deleteTransaction();
    }

    public void afterDeletingTransaction(long id) {
        recreateCursor();
        AccountWidget.updateWidgets(getContext());
    }

    @Override
    public void editItem(View v, int position, long id) {
        editTransaction(id);
    }

    private void editTransaction(long id) {
        new BlotterOperations(getContext(), this, db, id).editTransaction();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(getClass().getSimpleName(), "onActivityResult requestCode=" + requestCode + " resultCode=" + resultCode);

        if (requestCode == FILTER_REQUEST) {
            if (resultCode == RESULT_FIRST_USER) {
                blotterFilter.clear();
            } else if (resultCode == RESULT_OK) {
                blotterFilter = WhereFilter.fromIntent(data);
            }
            if (saveFilter) {
                saveFilter();
            }
            applyFilter();
        } else if (resultCode == RESULT_OK && requestCode == NEW_TRANSACTION_FROM_TEMPLATE_REQUEST) {
            createTransactionFromTemplate(data);
        }
        if (resultCode == RESULT_OK || resultCode == RESULT_FIRST_USER) {
            Log.d(getClass().getSimpleName(), "RESULT_OK || RESULT_FIRST_USER");
        }
        recreateCursor();
    }

    private void createTransactionFromTemplate(Intent data) {
        long templateId = data.getLongExtra(SelectTemplateFragment.TEMPLATE_ID, -1);
        int multiplier = data.getIntExtra(SelectTemplateFragment.MULTIPLIER, 1);
        boolean edit = data.getBooleanExtra(SelectTemplateFragment.EDIT_AFTER_CREATION, false);
        if (templateId > 0) {
            long id = duplicateTransaction(templateId, multiplier);
            Transaction t = db.getTransaction(id);
            if (t.fromAmount == 0 || edit) {
                new BlotterOperations(getContext(), this, db, id).asNewFromTemplate().editTransaction();
            }
        }
    }

    private void saveFilter() {
        SharedPreferences preferences = getContext().getSharedPreferences(this.getClass().getName(), 0);
        blotterFilter.toSharedPreferences(preferences);
    }

    protected void applyFilter() {
        long accountId = blotterFilter.getAccountId();
        if (accountId != -1) {
            Account a = db.getAccount(accountId);
            bAdd.setVisibility(a != null && a.isActive ? View.VISIBLE : View.GONE);
            if (showAllBlotterButtons) {
                bTransfer.setVisibility(a != null && a.isActive ? View.VISIBLE : View.GONE);
            }
        }
        String title = blotterFilter.getTitle();
        if (title != null && !title.isEmpty()) {
            ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
            if (actionBar != null) {
                actionBar.setTitle(title);
                actionBar.setSubtitle(R.string.blotter);
            }
        }
        updateFilterImage();
    }

    protected void updateFilterImage() {
        FilterState.updateFilterColor(getContext(), blotterFilter, bFilter);
    }

    @Override
    protected void onItemClick(View v, int position, long id) {
        if (isQuickMenuEnabledForTransaction) {
            selectedId = id;
            transactionActionGrid.show(v);
        } else {
            showTransactionInfo(id);
        }
    }

    @Override
    protected void viewItem(View v, int position, long id) {
        showTransactionInfo(id);
    }

    private void showTransactionInfo(long id) {
        TransactionInfoDialog transactionInfoView = new TransactionInfoDialog(getContext(), db, inflater);
        transactionInfoView.show(getContext(), this, id);
    }

    @Override
    public void integrityCheck() {
        new IntegrityCheckTask(this).execute(new IntegrityCheckRunningBalance(getContext(), db));
    }

    public boolean onBackPressed()
    {
        FrameLayout searchLayout = getView().findViewById(R.id.search_text_frame);
        if (searchLayout != null && searchLayout.getVisibility() == View.VISIBLE) {
            searchLayout.setVisibility(View.GONE);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        if (lastTxId != BEFORE_INITIAL_LOAD) {
            long t1 = System.nanoTime();
            long currentLastTxId = db.getLastTransactionId();
            Log.d(TAG, "getLastTransactionId() = " + lastTxId + ", " + String.format("%,d", System.nanoTime() - t1) + " ns");
            if (currentLastTxId != lastTxId) {
                Log.d(TAG, "lastTxId " + lastTxId + " != " + currentLastTxId + ", recreating cursor");
                recreateCursor();
            }
        }

        if (PinProtection.isUnlocked()) {
            Log.d(this.getClass().getSimpleName(), "onResume isUnlocked, show list");
            getListView().setVisibility(View.VISIBLE);
        }
        else {
            // still locked, don't show account list balances
            Log.d(this.getClass().getSimpleName(), "onResume NOT isUnlocked, hide list");
            getListView().setVisibility(View.INVISIBLE);
        }
    }
}
