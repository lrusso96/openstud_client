package com.lithium.leona.openstud.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.lithium.leona.openstud.R;
import com.lithium.leona.openstud.data.PreferenceManager;
import com.lithium.leona.openstud.helpers.ClientHelper;

import org.apache.commons.lang3.StringUtils;
import org.threeten.bp.format.DateTimeFormatter;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.RecyclerView;
import butterknife.BindView;
import butterknife.ButterKnife;
import lithium.openstud.driver.core.Event;
import lithium.openstud.driver.core.EventType;
import lithium.openstud.driver.core.ExamReservation;

public class EventAdapter extends RecyclerView.Adapter<EventAdapter.EventHolder> {

    private List<Event> events;
    private Context context;
    private EventAdapterListener eal;

    public EventAdapter(Context context, List<Event> events, EventAdapterListener eal) {
        this.events = events;
        this.context = context;
        this.eal = eal;
    }

    @NonNull
    @Override
    public EventHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_row_event, parent, false);
        EventHolder holder = new EventHolder(view);
        holder.setContext(context);
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull EventHolder holder, int position) {
        Event res = events.get(position);
        holder.setDetails(res);
    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    private void setupMenu(Event ev, ImageView options) {
        Context wrapper = new ContextThemeWrapper(context, R.style.popupMenuStyle);
        PopupMenu popup = new PopupMenu(wrapper, options);
        switch (ev.getEventType()) {
            case LESSON:
                popup.inflate(R.menu.event_generic_menu);
                break;
            case RESERVED:
                if (ClientHelper.canDeleteReservation(ev.getReservation()))
                    popup.inflate(R.menu.event_active_reservation_menu);
                else popup.inflate(R.menu.event_generic_menu);
                break;
            case DOABLE:
                if (ClientHelper.canPlaceReservation(ev.getReservation()))
                    popup.inflate(R.menu.event_exam_doable_menu);
                else popup.inflate(R.menu.event_generic_menu);
                break;
        }
        options.setOnClickListener(v -> {
            popup.setOnMenuItemClickListener(menuItem -> {
                switch (menuItem.getItemId()) {
                    case R.id.calendar_menu:
                        eal.addCalendarOnClick(ev);
                        break;
                    case R.id.place_reservation_menu:
                        if (ev.getEventType() == EventType.DOABLE)
                            new Thread(() -> eal.placeReservation(ev, ev.getReservation())).start();
                        break;
                    case R.id.delete_reservation_menu:
                        if (ev.getEventType() == EventType.RESERVED)
                            eal.deleteReservation(ev, ev.getReservation());
                        break;
                }
                return false;
            });
            popup.show();
        });
    }

    public interface EventAdapterListener {
        void addCalendarOnClick(Event ev);

        void placeReservation(Event ev, ExamReservation res);

        void deleteReservation(Event ev, ExamReservation res);
    }

    class EventHolder extends RecyclerView.ViewHolder {
        @BindView(R.id.name)
        TextView txtName;
        @BindView(R.id.startDate)
        TextView txtStartDate;
        @BindView(R.id.endDate)
        TextView txtEndDate;
        @BindView(R.id.where)
        TextView txtWhere;
        @BindView(R.id.nameTeacher)
        TextView txtTeacher;
        @BindView(R.id.event_options)
        ImageView options;
        private Context context;

        EventHolder(View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
        }

        private void setContext(Context context) {
            this.context = context;
        }

        @SuppressLint("ResourceType")
        void setDetails(final Event ev) {
            if (ev.getWhere() == null || ev.getWhere().trim().isEmpty())
                txtWhere.setVisibility(View.GONE);
            else if (ev.getEventType() == EventType.LESSON)
                txtWhere.setText(context.getResources().getString(R.string.where_event, ev.getWhere()));
            else
                txtWhere.setText(context.getResources().getString(R.string.info_extra_reservation_format, ev.getWhere()));
            txtTeacher.setText(context.getResources().getString(R.string.teacher_event, StringUtils.capitalize(ev.getTeacher())));
            if (ev.getTeacher() == null) txtTeacher.setVisibility(View.GONE);
            if (ev.getEventType() == EventType.LESSON && !PreferenceManager.isLessonOptionEnabled(context)) {
                options.setVisibility(View.GONE);
            } else {
                options.setVisibility(View.VISIBLE);
                setupMenu(ev, options);
            }
            if (ev.getEventType() == EventType.DOABLE || ev.getEventType() == EventType.RESERVED) {
                txtName.setText(ev.getDescription());
                txtStartDate.setVisibility(View.GONE);
                txtEndDate.setVisibility(View.GONE);
            } else {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
                txtName.setText(ev.getDescription());
                txtStartDate.setText(context.getResources().getString(R.string.start_lesson, ev.getStart().format(formatter)));
                txtEndDate.setText(context.getResources().getString(R.string.end_lesson, ev.getEnd().format(formatter)));
            }
        }
    }
}
