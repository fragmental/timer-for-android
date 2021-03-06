package com.apprise.toggl;

import com.apprise.toggl.remote.SyncAlarmReceiver;
import com.apprise.toggl.remote.SyncService;
import com.apprise.toggl.storage.DatabaseAdapter;
import com.apprise.toggl.storage.models.User;

import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.SystemClock;
import android.util.Log;

public class Toggl extends Application {

  public static final String PREF_API_TOKEN = "PREF_API_TOKEN";
  public static final String TOGGL_PREFS = "TOGGL_PREFS";
  private static final int SYNC_INTERVAL = 60 * 60 * 1000; // 60 minutes

  private Toggl singleton;
  private SharedPreferences settings;
  private ConnectivityManager connectivityManager;
  private User currentUser;

  private AlarmManager alarmManager;
  private PendingIntent syncAlarmIntent;  
  
  public Toggl getInstance() {
    return singleton;
  }

  public void storeAPIToken(String apiToken) {
    SharedPreferences.Editor editor = settings.edit();
    editor.putString(PREF_API_TOKEN, apiToken);
    editor.commit();
  }

  public String getAPIToken() {
    return settings.getString(PREF_API_TOKEN, null);
  }
  
  public User getCurrentUser() {
    return currentUser;
  }
  
  public void setCurrentUser(User user) {
    currentUser = user;
  }
  
  public void clearCurrentUser() {
    currentUser = null;
  }
  
  public void logIn(User user) {
    setCurrentUser(user);
    storeAPIToken(user.api_token);
  }
  
  public void logOut() {
    clearSyncSchedule();
    clearCurrentUser();
    storeAPIToken(null);
  }
  
  public boolean isConnected() {
    return connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE).isConnectedOrConnecting() ||
        connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnectedOrConnecting();
  }

  public void initSyncSchedule() {
    Log.d("Toggl", "initing schedule");
    Intent intent = new Intent(SyncAlarmReceiver.ACTION_SYNC_ALARM);
    syncAlarmIntent = PendingIntent.getBroadcast(this, 0, intent, 0);

    long timeToRefresh = SystemClock.elapsedRealtime() + SYNC_INTERVAL;

    // Schedule the alarm to sync invoke every SYNC_INTERVAL millis
    alarmManager = (AlarmManager)getSystemService(ALARM_SERVICE);
    alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME,
                    timeToRefresh, SYNC_INTERVAL, syncAlarmIntent);    
  }
  
  public void clearSyncSchedule() {
    alarmManager.cancel(syncAlarmIntent);
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
  }

  @Override
  public void onCreate() {
    super.onCreate();
    singleton = this;
    settings = getSharedPreferences(TOGGL_PREFS, MODE_PRIVATE);
    connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
    IntentFilter connFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
    registerReceiver(updateReceiver, connFilter);
  }

  /* use in case the application has been stopped to free memory */
  public void retrieveCurrentUser(DatabaseAdapter dbAdapter) {
    if (currentUser == null) {
      String apiToken = getAPIToken();
      User user = dbAdapter.findUserByApiToken(apiToken);
      if (user != null) logIn(user);
    }
  }
  
  @Override
  public void onLowMemory() {
    super.onLowMemory();
  }

  @Override
  public void onTerminate() {
    unregisterReceiver(updateReceiver);
    super.onTerminate();    
  }     
  
  protected BroadcastReceiver updateReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      NetworkInfo networkInfo = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);

      if (getCurrentUser() != null && networkInfo != null && networkInfo.isConnected()) {
        if (networkInfo.getType() == ConnectivityManager.TYPE_MOBILE
            || networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
          Intent syncServiceIntent = new Intent(context, SyncService.class);
          context.startService(syncServiceIntent);
        }
      }

    }
  };

}
