package com.lithium.leona.openstud.fragments;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;
import com.lithium.leona.openstud.R;
import com.lithium.leona.openstud.activities.LauncherActivity;
import com.lithium.leona.openstud.activities.PaymentsActivity;
import com.lithium.leona.openstud.adapters.TaxAdapter;
import com.lithium.leona.openstud.data.InfoManager;
import com.lithium.leona.openstud.helpers.ClientHelper;

import org.threeten.bp.Duration;
import org.threeten.bp.LocalDateTime;

import java.lang.ref.WeakReference;
import java.util.LinkedList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import lithium.openstud.driver.core.Openstud;
import lithium.openstud.driver.core.Tax;
import lithium.openstud.driver.exceptions.OpenstudConnectionException;
import lithium.openstud.driver.exceptions.OpenstudInvalidCredentialsException;
import lithium.openstud.driver.exceptions.OpenstudInvalidResponseException;

public class PaymentsFragment extends Fragment {

    @BindView(R.id.swipe_refresh)
    SwipeRefreshLayout swipeRefreshLayout;
    @BindView(R.id.recyclerView)
    RecyclerView rv;
    @BindView(R.id.empty_layout)
    LinearLayout emptyView;
    @BindView(R.id.empty_button_reload)
    Button emptyButton;
    @BindView(R.id.empty_text)
    TextView emptyText;
    private List<Tax> taxes;
    private TaxAdapter adapter;
    private int mode;
    private Openstud os;
    private PaymentsHandler h = new PaymentsHandler(this);
    private boolean firstStart = true;
    private LocalDateTime lastUpdate;

    public static PaymentsFragment newInstance(int mode) {
        PaymentsFragment frag = new PaymentsFragment();
        Bundle args = new Bundle();
        args.putInt("mode", mode);
        frag.setArguments(args);
        return frag;
    }

    @OnClick(R.id.empty_button_reload)
    public void OnClick(View v) {
        refresh();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.base_swipe_fragment, null);
        ButterKnife.bind(this, v);
        Bundle bundle = getArguments();
        mode = bundle.getInt("mode");
        emptyView.setVisibility(View.GONE);
        os = InfoManager.getOpenStud(getActivity());
        if (os == null) {
            InfoManager.clearSharedPreferences(getActivity());
            Intent i = new Intent(getActivity(), LauncherActivity.class);
            startActivity(i.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            return v;
        }
        taxes = new LinkedList<>();
        List<Tax> taxes_cached = null;
        if (mode == TaxAdapter.Mode.PAID.getValue()) {
            emptyText.setText(getResources().getString(R.string.no_paid_tax_found));
            taxes_cached = InfoManager.getPaidTaxesCached(getActivity(), os);
        } else if (mode == TaxAdapter.Mode.UNPAID.getValue()) {
            emptyText.setText(getResources().getString(R.string.no_unpaid_tax_found));
            taxes_cached = InfoManager.getUnpaidTaxesCached(getActivity(), os);
        }
        if (taxes_cached != null && !taxes_cached.isEmpty()) taxes.addAll(taxes_cached);
        rv.setHasFixedSize(true);
        LinearLayoutManager llm = new LinearLayoutManager(getActivity());
        rv.setLayoutManager(llm);
        adapter = new TaxAdapter(getActivity(), taxes, mode);
        rv.setAdapter(adapter);
        adapter.notifyDataSetChanged();
        swipeRefreshLayout.setColorSchemeResources(R.color.refresh1, R.color.refresh2, R.color.refresh3);
        swipeRefreshLayout.setOnRefreshListener(this::refresh);

        refresh();
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        LocalDateTime time = getTimer();
        if (firstStart) firstStart = false;
        else if (getActivity() != null && (time == null || Duration.between(time, LocalDateTime.now()).toMinutes() > 30))
            refresh();
    }

    private void refresh() {
        final Activity activity = getActivity();
        if (activity == null) return;
        setRefreshing(true);
        setButtonReloadStatus(false);
        new Thread(() -> {
            List<Tax> update = null;
            try {
                if (mode == TaxAdapter.Mode.PAID.getValue())
                    update = InfoManager.getPaidTaxes(activity.getApplication(), os);
                else if (mode == TaxAdapter.Mode.UNPAID.getValue())
                    update = InfoManager.getUnpaidTaxes(activity.getApplication(), os);

                if (update == null)
                    h.sendEmptyMessage(ClientHelper.Status.UNEXPECTED_VALUE.getValue());
                else h.sendEmptyMessage(ClientHelper.Status.OK.getValue());

            } catch (OpenstudConnectionException e) {
                h.sendEmptyMessage(ClientHelper.Status.CONNECTION_ERROR.getValue());
                e.printStackTrace();
            } catch (OpenstudInvalidResponseException e) {
                h.sendEmptyMessage(ClientHelper.Status.INVALID_RESPONSE.getValue());
                e.printStackTrace();
            } catch (OpenstudInvalidCredentialsException e) {
                if (e.isPasswordExpired())
                    h.sendEmptyMessage(ClientHelper.Status.EXPIRED_CREDENTIALS.getValue());
                else h.sendEmptyMessage(ClientHelper.Status.INVALID_CREDENTIALS.getValue());
                e.printStackTrace();
            }

            if (update == null) {
                setRefreshing(false);
                setButtonReloadStatus(true);
                swapViews(taxes);
                return;
            }
            updateTimer();
            refreshDataSet(update);
        }).start();
    }

    public synchronized void refreshDataSet(List<Tax> update) {
        boolean flag = false;
        if (update != null && !taxes.equals(update)) {
            flag = true;
            taxes.clear();
            taxes.addAll(update);
        }
        final boolean finalFlag = flag;
        Activity activity = getActivity();
        if (activity == null) return;
        activity.runOnUiThread(() -> {
            if (finalFlag) adapter.notifyDataSetChanged();
            swapViews(taxes);
            swipeRefreshLayout.setRefreshing(false);
            emptyButton.setEnabled(true);
        });
    }

    private void setRefreshing(final boolean bool) {
        Activity activity = getActivity();
        if (activity == null) return;
        activity.runOnUiThread(() -> swipeRefreshLayout.setRefreshing(bool));
    }

    private synchronized void updateTimer() {
        lastUpdate = LocalDateTime.now();
    }

    private synchronized LocalDateTime getTimer() {
        return lastUpdate;
    }

    private void setButtonReloadStatus(final boolean bool) {
        Activity activity = getActivity();
        if (activity == null) return;
        activity.runOnUiThread(() -> emptyButton.setEnabled(bool));
    }

    private void swapViews(final List<Tax> taxes) {
        Activity activity = getActivity();
        if (activity == null) return;
        activity.runOnUiThread(() -> {
            if (taxes == null || taxes.isEmpty()) {
                emptyView.setVisibility(View.VISIBLE);
                rv.setVisibility(View.GONE);
            } else {
                emptyView.setVisibility(View.GONE);
                rv.setVisibility(View.VISIBLE);
            }
        });
    }

    private static class PaymentsHandler extends Handler {
        private final WeakReference<PaymentsFragment> frag;

        private PaymentsHandler(PaymentsFragment frag) {
            this.frag = new WeakReference<>(frag);
        }

        @Override
        public void handleMessage(Message msg) {
            final PaymentsFragment paymentFrag = frag.get();
            if (paymentFrag == null) return;
            PaymentsActivity activity = (PaymentsActivity) paymentFrag.getActivity();
            if (activity != null) {
                View.OnClickListener ocl = v -> paymentFrag.refresh();
                if (msg.what == ClientHelper.Status.CONNECTION_ERROR.getValue()) {
                    activity.createActionSnackBar(R.string.connection_error, Snackbar.LENGTH_LONG, ocl);
                } else if (msg.what == ClientHelper.Status.INVALID_RESPONSE.getValue()) {
                    activity.createActionSnackBar(R.string.connection_error, Snackbar.LENGTH_LONG, ocl);
                } else if (msg.what == ClientHelper.Status.USER_NOT_ENABLED.getValue()) {
                    activity.createTextSnackBar(R.string.user_not_enabled_error, Snackbar.LENGTH_LONG);
                } else if (msg.what == (ClientHelper.Status.INVALID_CREDENTIALS).getValue() || msg.what == ClientHelper.Status.EXPIRED_CREDENTIALS.getValue()) {
                    InfoManager.clearSharedPreferences(activity.getApplication());
                    Intent i = new Intent(activity, LauncherActivity.class);
                    i.putExtra("error", msg.what);
                    activity.startActivity(i.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
                    activity.finish();
                } else if (msg.what == ClientHelper.Status.UNEXPECTED_VALUE.getValue()) {
                    activity.createTextSnackBar(R.string.invalid_response_error, Snackbar.LENGTH_LONG);
                }
            }
        }
    }

}