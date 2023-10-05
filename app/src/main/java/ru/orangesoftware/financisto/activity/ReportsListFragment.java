package ru.orangesoftware.financisto.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.ListFragment;

import ru.orangesoftware.financisto.R;
import ru.orangesoftware.financisto.adapter.ReportListAdapter;
import ru.orangesoftware.financisto.db.MyEntityManager;
import ru.orangesoftware.financisto.graph.Report2DChart;
import ru.orangesoftware.financisto.model.Currency;
import ru.orangesoftware.financisto.report.Report;
import ru.orangesoftware.financisto.report.ReportType;
import ru.orangesoftware.financisto.utils.PinProtection;

public class ReportsListFragment extends ListFragment {
    public static final String EXTRA_REPORT_TYPE = "reportType";

    public final ReportType[] reports = new ReportType[]{
            ReportType.BY_PERIOD,
            ReportType.BY_CATEGORY,
            ReportType.BY_PAYEE,
            ReportType.BY_LOCATION,
            ReportType.BY_PROJECT,
            ReportType.BY_ACCOUNT_BY_PERIOD,
            ReportType.BY_CATEGORY_BY_PERIOD,
            ReportType.BY_PAYEE_BY_PERIOD,
            ReportType.BY_LOCATION_BY_PERIOD,
            ReportType.BY_PROJECT_BY_PERIOD
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.reports_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setListAdapter(new ReportListAdapter(getContext(), reports));
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onPause() {
        super.onPause();
        PinProtection.lock(getContext());
    }

    @Override
    public void onResume() {
        super.onResume();
        PinProtection.unlock(getContext());
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        if (reports[position].isConventionalBarReport()) {
            // Conventional Bars reports
            Intent intent = new Intent(getContext(), ReportActivity.class);
            intent.putExtra(EXTRA_REPORT_TYPE, reports[position].name());
            startActivity(intent);
        } else {
            // 2D Chart reports
            Intent intent = new Intent(getContext(), Report2DChartActivity.class);
            intent.putExtra(Report2DChart.REPORT_TYPE, reports[position].name());
            startActivity(intent);
        }
    }

    public static Report createReport(Context context, MyEntityManager em, Bundle extras) {
        String reportTypeName = extras.getString(EXTRA_REPORT_TYPE);
        ReportType reportType = ReportType.valueOf(reportTypeName);
        Currency c = em.getHomeCurrency();
        return reportType.createReport(context, c);
    }
}
