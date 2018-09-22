package com.lithium.leona.openstud.activities;

import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.lithium.leona.openstud.R;
import com.lithium.leona.openstud.data.InfoManager;
import com.lithium.leona.openstud.helpers.ClientHelper;
import com.lithium.leona.openstud.helpers.LayoutHelper;
import com.lithium.leona.openstud.listeners.DelayedDrawerListener;

import org.threeten.bp.Duration;
import org.threeten.bp.LocalDate;
import org.threeten.bp.LocalDateTime;
import org.threeten.bp.format.DateTimeFormatter;

import java.lang.ref.WeakReference;

import butterknife.BindView;
import butterknife.ButterKnife;
import lithium.openstud.driver.core.Isee;
import lithium.openstud.driver.core.Openstud;
import lithium.openstud.driver.core.Student;
import lithium.openstud.driver.exceptions.OpenstudConnectionException;
import lithium.openstud.driver.exceptions.OpenstudInvalidCredentialsException;
import lithium.openstud.driver.exceptions.OpenstudInvalidResponseException;

public class ProfileActivity extends AppCompatActivity {

    private static class ProfileEventHandler extends Handler {
        private final WeakReference<ProfileActivity> mActivity;

        public ProfileEventHandler(ProfileActivity activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            ProfileActivity activity = mActivity.get();
            if (activity != null) {
                if (msg.what == ClientHelper.Status.OK.getValue()) {
                    activity.applyInfos(activity.student, activity.isee);
                }
                else if (msg.what == ClientHelper.Status.CONNECTION_ERROR.getValue()) {
                    activity.createRetrySnackBar(R.string.connection_error, Snackbar.LENGTH_LONG);
                }
                else if (msg.what == ClientHelper.Status.INVALID_RESPONSE.getValue()) {
                    activity.createRetrySnackBar(R.string.invalid_response_error, Snackbar.LENGTH_LONG);
                }
                else if (msg.what == ClientHelper.Status.USER_NOT_ENABLED.getValue()) {
                    activity.createTextSnackBar(R.string.user_not_enabled_error, Snackbar.LENGTH_LONG);
                }
                else if (msg.what == ClientHelper.Status.INVALID_CREDENTIALS.getValue() || msg.what == ClientHelper.Status.EXPIRED_CREDENTIALS.getValue()) {
                    InfoManager.clearSharedPreferences(activity.getApplication());
                    Intent i = new Intent(activity, LauncherActivity.class);
                    activity.startActivity(i.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
                    activity.finish();
                }
            }
        }
    }


    @BindView(R.id.drawer_layout) DrawerLayout mDrawerLayout;
    @BindView(R.id.swipe_refresh_layout) SwipeRefreshLayout swipeRefreshLayout;
    @BindView(R.id.toolbar) Toolbar toolbar;
    @BindView(R.id.collapsingToolbar) CollapsingToolbarLayout collapsingToolbarLayout;
    @BindView(R.id.studentId) TextView studentId;
    @BindView(R.id.birthDate) TextView birthDate;
    @BindView(R.id.birthPlace) TextView birthPlace;
    @BindView(R.id.isee) TextView isee_field;
    @BindView(R.id.departmentDescription) TextView departmentDescription;
    @BindView(R.id.courseDescription) TextView courseDescription;
    @BindView(R.id.courseYear) TextView courseYear;
    @BindView(R.id.studentStatus) TextView studentStatus;
    @BindView(R.id.cfu) TextView cfu;
    private DelayedDrawerListener ddl;
    private View headerLayout;
    private NavigationView nv;
    private Student student;
    private Isee isee;
    private Openstud os;
    private ProfileEventHandler h = new ProfileEventHandler(this);
    private LocalDateTime lastUpdate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);
        ButterKnife.bind(this);
        nv = LayoutHelper.setupNavigationDrawer(this, mDrawerLayout);
        setupListeners();
        LayoutHelper.setupToolbar(this, toolbar, R.drawable.ic_baseline_arrow_back);
        headerLayout = nv.getHeaderView(0);
        os = InfoManager.getOpenStud(getApplication());
        student = InfoManager.getInfoStudentCached(getApplication(),os);
        isee = InfoManager.getIseeCached(getApplication(),os);
        if (os == null || student == null) {
            InfoManager.clearSharedPreferences(getApplication());
            Intent i = new Intent(ProfileActivity.this, LauncherActivity.class);
            startActivity(i.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
            finish();
            return;
        }

        applyInfos(student,isee);
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener()
        {
            @Override
            public void onRefresh()
            {
                Thread t1 = new Thread(new Runnable() {
                    public void run() {
                        refresh(os);
                        swipeRefreshLayout.setRefreshing(false);
                    }
                });
                t1.start();
            }
        });
        new Thread(new Runnable() {
            @Override
            public void run() {
                swipeRefreshLayout.setRefreshing(true);
                refresh(os);
                swipeRefreshLayout.setRefreshing(false);
            }
        }).start();
    }

    protected void onRestart() {
        super.onRestart();
        LocalDateTime time = getTimer();
        if (time == null || Duration.between(time,LocalDateTime.now()).toMinutes()>60) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    swipeRefreshLayout.setRefreshing(true);
                    refresh(os);
                    swipeRefreshLayout.setRefreshing(false);
                }
            }).start();
        }
    }



    private void refresh(Openstud os) {
        try {
            student = InfoManager.getInfoStudent(getApplication(), os);
            isee = InfoManager.getIsee(getApplication(), os);
        } catch (OpenstudConnectionException e) {
            h.sendEmptyMessage(ClientHelper.Status.CONNECTION_ERROR.getValue());
            e.printStackTrace();
        } catch (OpenstudInvalidResponseException e) {
            h.sendEmptyMessage(ClientHelper.Status.INVALID_RESPONSE.getValue());
            e.printStackTrace();
        } catch (OpenstudInvalidCredentialsException e) {
            if (e.isPasswordExpired()) h.sendEmptyMessage(ClientHelper.Status.EXPIRED_CREDENTIALS.getValue());
            else h.sendEmptyMessage(ClientHelper.Status.INVALID_CREDENTIALS.getValue());
            e.printStackTrace();
        }
        updateTimer();
    }

    private void createTextSnackBar(int string_id, int length){
        ClientHelper.createTextSnackBar(mDrawerLayout,string_id,length);
    }
    private void createRetrySnackBar(int string_id, int length) {
        Snackbar snackbar = Snackbar
                .make(mDrawerLayout, getResources().getString(string_id), length).setAction(R.string.retry,
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        refresh(os);
                                    }
                                });
                            }
                        });
        snackbar.show();
    }

    private void applyInfos(Student st, Isee isee){
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/uuuu");
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });
        TextView navTitle = headerLayout.findViewById(R.id.nav_title);
        navTitle.setText(getString(R.string.fullname, student.getFirstName(), student.getLastName()));
        collapsingToolbarLayout.setTitle(st.getFirstName()+" "+st.getLastName());
        TextView navSubtitle = headerLayout.findViewById(R.id.nav_subtitle);
        navSubtitle.setText(String.valueOf(st.getStudentID()));
        studentId.setText(String.valueOf(st.getStudentID()));
        LocalDate date = st.getBirthDate();
        birthDate.setText((st.getBirthDate().format(formatter)));
        birthPlace.setText(st.getBirthPlace());
        if(isee == null) isee_field.setText(getResources().getString(R.string.isee_not_avaiable));
        else isee_field.setText(String.valueOf(isee.getValue()));
        departmentDescription.setText(st.getDepartmentName());
        courseDescription.setText(st.getCourseName());
        courseYear.setText(st.getCourseYear());
        studentStatus.setText(st.getStudentStatus());
        cfu.setText(String.valueOf(st.getCfu()));
    }


    @Override
    public void onBackPressed() {
        if (this.mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            this.mDrawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
    private void setupListeners(){
        ddl = new DelayedDrawerListener(){
            @Override
            public void onDrawerClosed(@NonNull View drawerView) {
                int item = getItemPressedAndReset();
                if (item == -1) return;
                switch (item) {
                    case R.id.payments_menu: {
                        Intent intent = new Intent(ProfileActivity.this, PaymentsActivity.class);
                        startActivity(intent);
                        break;
                    }
                    case R.id.exit_menu: {
                        InfoManager.clearSharedPreferences(getApplication());
                        Intent i = new Intent(ProfileActivity.this, LauncherActivity.class);
                        startActivity(i.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                        break;
                    }
                    case R.id.exams_menu: {
                        Intent intent = new Intent(ProfileActivity.this, ExamsActivity.class);
                        startActivity(intent);
                        break;
                    }
                }
            }

        };
        mDrawerLayout.addDrawerListener(ddl);
        nv.setNavigationItemSelectedListener(
                new NavigationView.OnNavigationItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                        mDrawerLayout.closeDrawers();
                        ddl.setItemPressed(item.getItemId());
                        return true;
                    }
                });
    }

    private synchronized void updateTimer(){
        lastUpdate = LocalDateTime.now();
    }

    private synchronized LocalDateTime getTimer(){
        return lastUpdate;
    }

}