package com.lithium.leona.openstud.activities;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.snackbar.Snackbar;
import com.lithium.leona.openstud.R;
import com.lithium.leona.openstud.adapters.EventAdapter;
import com.lithium.leona.openstud.helpers.ClientHelper;
import com.lithium.leona.openstud.helpers.LayoutHelper;
import com.lithium.leona.openstud.helpers.ThemeEngine;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonDataException;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;

import org.threeten.bp.Instant;
import org.threeten.bp.ZoneId;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import butterknife.BindView;
import butterknife.ButterKnife;
import devs.mulham.horizontalcalendar.HorizontalCalendar;
import devs.mulham.horizontalcalendar.utils.HorizontalCalendarListener;
import lithium.openstud.driver.core.OpenstudHelper;
import lithium.openstud.driver.core.models.Event;
import lithium.openstud.driver.core.models.ExamReservation;
import lithium.openstud.driver.core.models.Lesson;
import lithium.openstud.driver.exceptions.OpenstudConnectionException;
import lithium.openstud.driver.exceptions.OpenstudInvalidResponseException;

public class ClassroomTimetableActivity extends BaseDataActivity {

    @BindView(R.id.toolbar)
    Toolbar toolbar;
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
    @BindView(R.id.constraintLayout)
    ConstraintLayout constraintLayout;
    private HorizontalCalendar horizontalCalendar;
    private Calendar defaultDate;
    private List<Event> lessons;
    private Map<Long, List<Lesson>> cachedLessons;
    private EventAdapter adapter;
    private int roomId;
    private ClassroomTimetableHandler h = new ClassroomTimetableHandler(this);
    private Moshi moshi;
    @SuppressLint("UseSparseArrays")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!initData()) return;
        ThemeEngine.applyClassroomTimetableTheme(this);
        setContentView(R.layout.activity_classroom_timetable);
        ButterKnife.bind(this);
        moshi = new Moshi.Builder().build();
        Activity activity = this;
        Bundle bundle = this.getIntent().getExtras();
        roomId = bundle.getInt("roomId", -1);
        cachedLessons = new HashMap<>();
        Calendar startDate = Calendar.getInstance();
        startDate.add(Calendar.DAY_OF_MONTH, -2);
        /* ends after 1 month from now */
        Calendar endDate = Calendar.getInstance();
        endDate.add(Calendar.MONTH, 1);
        restoreInstance(savedInstanceState);
        horizontalCalendar = new HorizontalCalendar.Builder(this, R.id.calendarView)
                .range(startDate, endDate)
                .datesNumberOnScreen(5)
                .defaultSelectedDate(defaultDate)
                .build();
        LayoutHelper.setupToolbar(this, toolbar, R.drawable.ic_baseline_arrow_back);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
        String name = bundle.getString("roomName", null);
        lessons = new LinkedList<>();
        rv.setHasFixedSize(true);
        LinearLayoutManager llm = new LinearLayoutManager(this);
        rv.setLayoutManager(llm);
        adapter = new EventAdapter(this, lessons, new EventAdapter.EventAdapterListener() {
            @Override
            public void addCalendarOnClick(Event ev) {
                ClientHelper.addEventToCalendar(activity, ev);
            }

            @Override
            public void placeReservation(Event ev, ExamReservation res) {

            }

            @Override
            public void deleteReservation(Event ev, ExamReservation res) {

            }
        });
        rv.setAdapter(adapter);

        if (name == null)
            Objects.requireNonNull(getSupportActionBar()).setTitle(R.string.classroom_timetable);
        else Objects.requireNonNull(getSupportActionBar()).setTitle(name);
        if (savedInstanceState == null) setTodayLessonFromBundle(bundle);
        else getLessons(defaultDate, false);
        horizontalCalendar.setCalendarListener(new HorizontalCalendarListener() {
            @Override
            public void onDateSelected(Calendar date, int position) {
                getLessons(date, false);
            }
        });
        swipeRefreshLayout.setColorSchemeResources(R.color.refresh1, R.color.refresh2, R.color.refresh3);
        swipeRefreshLayout.setOnRefreshListener(() -> getLessons(horizontalCalendar.getSelectedDate(), true));
        emptyButton.setOnClickListener(v -> {
            if (!swipeRefreshLayout.isRefreshing())
                getLessons(horizontalCalendar.getSelectedDate(), true);
        });
        emptyText.setText(getResources().getString(R.string.no_lesson_found));
    }

    private void getLessons(Calendar date, boolean refresh) {
        new Thread(() -> {
            try {
                if (!refresh && cachedLessons.containsKey(date.getTimeInMillis()))
                    applyLessons(date, cachedLessons.get(date.getTimeInMillis()));
                else {
                    setRefreshing(true);
                    List<Lesson> newLessons = os.getClassroomTimetable(roomId, Instant.ofEpochMilli(date.getTime().getTime()).atZone(ZoneId.systemDefault()).toLocalDate());
                    applyLessons(date, newLessons);
                }
            } catch (OpenstudConnectionException e) {
                e.printStackTrace();
                h.sendEmptyMessage(ClientHelper.Status.CONNECTION_ERROR.getValue());
            } catch (OpenstudInvalidResponseException e) {
                e.printStackTrace();
                h.sendEmptyMessage(ClientHelper.Status.INVALID_RESPONSE.getValue());
            }
            setRefreshing(false);
        }).start();
    }

    private synchronized void applyLessons(Calendar date, List<Lesson> lessonsUpdate) {
        List<Event> newEvents = OpenstudHelper.generateEventsFromTimetable(lessonsUpdate);
        if (!cachedLessons.containsKey(date.getTimeInMillis()))
            cachedLessons.put(date.getTimeInMillis(), lessonsUpdate);
        lessons.clear();
        lessons.addAll(newEvents);
        runOnUiThread(() -> adapter.notifyDataSetChanged());
        swapViews(newEvents.isEmpty());
    }

    private void setRefreshing(final boolean bool) {
        this.runOnUiThread(() -> swipeRefreshLayout.setRefreshing(bool));
    }

    private void swapViews(boolean empty) {
        runOnUiThread(() -> {
            if (empty) {
                emptyView.setVisibility(View.VISIBLE);
                rv.setVisibility(View.GONE);
            } else {
                emptyView.setVisibility(View.GONE);
                rv.setVisibility(View.VISIBLE);
            }
        });
    }

    private void setTodayLessonFromBundle(Bundle bundle) {
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        String lessonsJson = bundle.getString("todayLessons", null);
        if (lessonsJson == null) return;
        JsonAdapter<List<Lesson>> jsonAdapter = moshi.adapter(Types.newParameterizedType(List.class, Lesson.class));
        List<Lesson> newLessons = null;
        try {
            newLessons = jsonAdapter.fromJson(lessonsJson);
        } catch (JsonDataException | IOException e) {
            e.printStackTrace();
        }
        if (newLessons!=null) applyLessons(today, newLessons);
        runOnUiThread(() -> adapter.notifyDataSetChanged());
    }

    public void onSaveInstanceState(Bundle savedInstance) {
        JsonAdapter<Map<Long,List<Lesson>>> jsonAdapterMap = moshi.adapter(Types.newParameterizedType(Map.class, Long.class, Types.newParameterizedType(List.class, Lesson.class)));
        String jsonMap = jsonAdapterMap.toJson(cachedLessons);
        savedInstance.putString("cachedLessons", jsonMap);
        savedInstance.putLong("currentDate", horizontalCalendar.getSelectedDate().getTimeInMillis());
        super.onSaveInstanceState(savedInstance);
    }

    @SuppressLint("UseSparseArrays")
    private void restoreInstance(Bundle savedInstance) {
        defaultDate = Calendar.getInstance();
        defaultDate.set(Calendar.HOUR_OF_DAY, 0);
        defaultDate.set(Calendar.MINUTE, 0);
        defaultDate.set(Calendar.SECOND, 0);
        defaultDate.set(Calendar.MILLISECOND, 0);
        if (savedInstance != null) {
            long currentDateMillis = savedInstance.getLong("currentDate", -1);
            JsonAdapter<Map<Long,List<Lesson>>> jsonAdapterMap = moshi.adapter(Types.newParameterizedType(Map.class, Long.class, Types.newParameterizedType(List.class, Lesson.class)));
            defaultDate = Calendar.getInstance();
            defaultDate.setTimeInMillis(currentDateMillis);
            String json = savedInstance.getString("cachedLessons", null);
            if (json != null) {
                try {
                    cachedLessons = jsonAdapterMap.fromJson(json);
                } catch (JsonDataException | IOException e) {
                    e.printStackTrace();
                }
            }
            if (json == null) cachedLessons = new HashMap<>();
        }
    }

    private static class ClassroomTimetableHandler extends Handler {
        private final WeakReference<ClassroomTimetableActivity> activity;

        private ClassroomTimetableHandler(ClassroomTimetableActivity activity) {
            this.activity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            final ClassroomTimetableActivity activity = this.activity.get();
            if (activity == null) return;
            View.OnClickListener ocl = v -> activity.getLessons(activity.horizontalCalendar.getSelectedDate(), true);
            if (msg.what == ClientHelper.Status.CONNECTION_ERROR.getValue()) {
                LayoutHelper.createActionSnackBar(activity.constraintLayout, R.string.connection_error, R.string.retry, Snackbar.LENGTH_LONG, ocl);
            } else if (msg.what == ClientHelper.Status.INVALID_RESPONSE.getValue()) {
                LayoutHelper.createActionSnackBar(activity.constraintLayout, R.string.connection_error, R.string.retry, Snackbar.LENGTH_LONG, ocl);
            }
        }
    }

}
