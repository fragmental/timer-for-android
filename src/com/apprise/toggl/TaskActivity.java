package com.apprise.toggl;

import java.util.Calendar;
import java.util.Date;

import com.apprise.toggl.storage.DatabaseAdapter;
import com.apprise.toggl.storage.DatabaseAdapter.Projects;
import com.apprise.toggl.storage.models.Task;
import com.apprise.toggl.tracking.TimeTrackingService;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SimpleCursorAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.AdapterView.OnItemSelectedListener;

public class TaskActivity extends ApplicationActivity {

  public static final String TASK_ID = "TASK_ID";
  private static final String TAG = "TaskActivity";
  
  private static final int DATE_DIALOG_ID = 0;  
  
  private DatabaseAdapter dbAdapter;
  private TimeTrackingService trackingService;
  private Task task;
  private Button timeTrackingButton;
  private TextView durationView;
  private EditText descriptionView;
  Spinner projectSpinner;
  private TextView dateView;
  private TextView plannedTasksView;
  private TextView tagsView;
  private LinearLayout plannedTasksArea;
  private CheckBox billableCheckBox;
  
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.task);

    dbAdapter = new DatabaseAdapter(this, (Toggl) getApplication());
    dbAdapter.open();
    long _id = getIntent().getLongExtra(TASK_ID, -1);
    task = dbAdapter.findTask(_id);
    dbAdapter.close();

    Intent intent = new Intent(this, TimeTrackingService.class);
    bindService(intent, trackingConnection, BIND_AUTO_CREATE);

    initViews();
    attachEvents();
  }
  
  @Override
  protected void onStart() {
    IntentFilter filter = new IntentFilter(TimeTrackingService.BROADCAST_SECOND_ELAPSED);
    registerReceiver(updateReceiver, filter);
    super.onResume();
  }
  
  @Override
  protected void onStop() {
    unregisterReceiver(updateReceiver);
    super.onPause();
  }

  @Override
  protected void onResume() {
    dbAdapter.open();
    initProjectSpinner();
    initPlannedTasks();
    super.onResume();
  }
  
  @Override
  protected void onPause() {
    dbAdapter.close();
    super.onPause();
  }
  
  @Override
  protected void onDestroy() {
    unbindService(trackingConnection);
    super.onDestroy();
  }

  protected void initViews() {
    timeTrackingButton = (Button) findViewById(R.id.timer_trigger);
    durationView = (TextView) findViewById(R.id.task_timer_duration);
    descriptionView = (EditText) findViewById(R.id.task_description);
    dateView = (TextView) findViewById(R.id.task_date);
    projectSpinner = (Spinner) findViewById(R.id.task_project);
    plannedTasksArea = (LinearLayout) findViewById(R.id.task_planned_tasks_area);
    plannedTasksView = (TextView) findViewById(R.id.task_planned_tasks);
    tagsView = (TextView) findViewById(R.id.task_tags);
    billableCheckBox = (CheckBox) findViewById(R.id.task_billable_cb);

    descriptionView.setText(task.description);
    billableCheckBox.setChecked(task.billable);
    updateDuration();
    initDateView();
  }

  private void initProjectSpinner() {
    Cursor projectsCursor = dbAdapter.findAllProjectsForSpinner();
    startManagingCursor(projectsCursor);

    String[] from = new String[] { Projects.CLIENT_PROJECT_NAME };
    int[] to = new int[] { R.id.project_item_project_name };

    SimpleCursorAdapter projectsAdapter = new SimpleCursorAdapter(this,
        R.layout.project_item, projectsCursor, from, to);
    projectsAdapter.setDropDownViewResource(R.layout.project_dropdown_item);
    projectSpinner.setAdapter(projectsAdapter);
    projectSpinner
        .setOnItemSelectedListener(new OnProjectItemSelectedListener());

    if (task.project != null) {
      for (int i = -1; i < projectsCursor.getCount(); i++) {
        if (task.project._id == projectsAdapter.getItemId(i)) {
          projectSpinner.setSelection(i);
        }
      }
    }
  }

  private void initDateView() {
    dateView.setText(Util.smallDateString(Util.parseStringToDate(task.start)));
  }

  private void initPlannedTasks() {
    if (task.project != null) {
      long project_remote_id = task.project.id;
      Cursor cursor = dbAdapter.findPlannedTasksByProjectId(project_remote_id);
      if (cursor != null) {
        if ((cursor.getCount() == 0) || !cursor.moveToFirst()) {
          plannedTasksArea.setVisibility(LinearLayout.GONE);
        }
        cursor.close();
      }
    } else {
      plannedTasksArea.setVisibility(LinearLayout.GONE);

    }
  }

  protected void attachEvents() {
    timeTrackingButton.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        if (trackingService.isTracking(task)) {
          trackingService.stopTracking();
          // TODO: set task stop date
          saveTask();
          timeTrackingButton.setBackgroundResource(R.drawable.timer_trigger_button);
        }
        else if (trackingService.isTracking()) {
          // is tracking another task
          // stop the other or notify user?
        }
        else {
          trackingService.startTracking(task);
          timeTrackingButton.setBackgroundResource(R.drawable.trigger_active);
        }
      }
    });
    
    findViewById(R.id.task_project_area).setOnClickListener(
    new View.OnClickListener() {
      public void onClick(View v) {
        Log.d(TAG, "clicked project name");
        projectSpinner.performClick();
      }
    });
    
    findViewById(R.id.task_date_area).setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        Log.d(TAG, "clicked date");        
        showDialog(DATE_DIALOG_ID);
      }
    });
    
    billableCheckBox.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        Log.d(TAG, "clicked billable cb");
        task.billable = billableCheckBox.isChecked();
        saveTask();
      }
    });

    findViewById(R.id.task_tags_area).setOnClickListener(
        new View.OnClickListener() {
          public void onClick(View v) {
            Log.d(TAG, "clicked tags");
            // TODO: tags
          }
        });

    findViewById(R.id.task_planned_tasks_area).setOnClickListener(
        new View.OnClickListener() {
          public void onClick(View v) {
            Log.d(TAG, "clicked planned tasks");
            // TODO: planned tasks
          }
        });
  }

  protected void saveTask() {
    Log.d(TAG, "saving task: " + task);
    task.sync_dirty = true;
    if (!dbAdapter.updateTask(task)) {
      dbAdapter.createTask(task);
    }
  }

  private void setDate(int year, int month, int date) {
    Date start = Util.parseStringToDate(task.start);
    Date stop = Util.parseStringToDate(task.stop);

    Calendar cal = (Calendar) Calendar.getInstance().clone();
    cal.set(Calendar.YEAR, year);
    cal.set(Calendar.MONTH, month);
    cal.set(Calendar.DATE, date);
    cal.set(Calendar.HOUR_OF_DAY, start.getHours());
    cal.set(Calendar.MINUTE, start.getMinutes());
    cal.set(Calendar.SECOND, start.getSeconds());
    task.start = Util.formatDateToString(cal.getTime());

    cal.set(Calendar.HOUR_OF_DAY, stop.getHours());
    cal.set(Calendar.MINUTE, stop.getMinutes());
    cal.set(Calendar.SECOND, stop.getSeconds());
    task.stop = Util.formatDateToString(cal.getTime());

    saveTask();
    initDateView();
  }

  private DatePickerDialog.OnDateSetListener mDateSetListener = new DatePickerDialog.OnDateSetListener() {

    public void onDateSet(DatePicker view, int year, int month, int date) {
      setDate(year, month, date);
    }
  };

  @Override
  protected Dialog onCreateDialog(int id) {
    switch (id) {
    case DATE_DIALOG_ID:
      Calendar cal = Util.parseStringToCalendar(task.start);
      int mYear = cal.get(Calendar.YEAR);
      int mMonth = cal.get(Calendar.MONTH);
      int mDay = cal.get(Calendar.DATE);
      return new DatePickerDialog(this, mDateSetListener, mYear, mMonth, mDay);
    }
    return null;
  }

  public class OnProjectItemSelectedListener implements OnItemSelectedListener {

    public void onItemSelected(AdapterView<?> parent, View view, int pos,
        long id) {
      Cursor clickedProject = (Cursor) parent.getItemAtPosition(pos);
      Log.d(TAG, "clickedProject: "
          + clickedProject.getString(clickedProject
              .getColumnIndex(Projects.CLIENT_PROJECT_NAME)));
      long clickedId = clickedProject.getInt(clickedProject
          .getColumnIndex(Projects._ID));
      if (clickedId == -1) {
        Log.d(TAG, "clicked Add new project");
        // TODO: start CreateProject activity
      } else {
        task.project = dbAdapter.findProject(clickedId);
        saveTask();
      }
      clickedProject.close();
    }

    public void onNothingSelected(AdapterView parent) {
      // Do nothing.
    }
  }
  
  private BroadcastReceiver updateReceiver = new BroadcastReceiver() {
    
    @Override
    public void onReceive(Context context, Intent intent) {
      task.duration = trackingService.getCurrentDuration();
      updateDuration();
    }

  };

  private ServiceConnection trackingConnection = new ServiceConnection() {
    
    public void onServiceConnected(ComponentName name, IBinder service) {
      TimeTrackingService.TimeTrackingBinder binding = (TimeTrackingService.TimeTrackingBinder) service;
      trackingService = binding.getService();

      if (trackingService.isTracking(task)) {
        task.duration = trackingService.getCurrentDuration();
        updateDuration();
        timeTrackingButton.setBackgroundResource(R.drawable.trigger_active);
      }
    }

    public void onServiceDisconnected(ComponentName name) {

    }

  };
  
  private void updateDuration() {
    durationView.setText(Util.secondsToHMS(task.duration));
  }
  
}
