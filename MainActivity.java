package com.aniruddhakulkarni.fittest;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.data.Bucket;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataSource;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.result.DailyTotalResult;
import com.google.android.gms.fitness.result.DataReadResult;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_OAUTH = 1;
    public static GoogleApiClient mClient = null;
    private boolean authInProgress = false;
    private SimpleDateFormat formatter_t_z = new SimpleDateFormat("yyyy-MM-dd HH:mm:ssZ", Locale.ENGLISH);
    private HashMap<String, Boolean> manualStepsHashMap;
    private SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
    private static final String KEY_LAST_STEPS_POSTED_ON = "last_updated_steps_date";
    private SharedPreferences sharedPreferences;
    private static final String PREF_NAME = "steps_pref";
    private TextView tv, tvTwo;
    private StringBuilder stringBuilder ;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        sharedPreferences = getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        tv = (TextView) findViewById(R.id.tv);
        tvTwo = (TextView) findViewById(R.id.tv_two);
        buildFitnessClient();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_OAUTH) {
            authInProgress = false;
            if (resultCode == RESULT_OK) {

                if (!mClient.isConnecting() && !mClient.isConnected()) {
                    mClient.connect();
                }
            }
        }
    }

    public void buildFitnessClient() {

        mClient = new GoogleApiClient.Builder(this)
                .addApi(Fitness.HISTORY_API)
                .addApi(Fitness.SENSORS_API)
                .addApi(Fitness.CONFIG_API)
                .addScope(new Scope(Scopes.FITNESS_ACTIVITY_READ_WRITE))
                .addConnectionCallbacks(
                        new GoogleApiClient.ConnectionCallbacks() {
                            @Override
                            public void onConnected(Bundle bundle) {
                                if (mClient.isConnected()) {
                                    int permissionCheck = ContextCompat.checkSelfPermission(getApplicationContext(),
                                            Manifest.permission.GET_ACCOUNTS);
                                    /*if(android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

                                        int hasWriteContactsPermission = checkSelfPermission(Manifest.permission.GET_ACCOUNTS);
                                        if (hasWriteContactsPermission != PackageManager.PERMISSION_GRANTED) {
                                            requestPermissions(new String[] {Manifest.permission.GET_ACCOUNTS},
                                                    REQUEST_ACCOUNT_PERMISSIONS);
                                            return;
                                        }
                                    }*/
                                }

                                    if (isAvailableForBulkStepsPost()) {
                                        String start = getLastStepsPostedDate();
                                        if (!start.trim().equals("")) {

                                            Calendar calendar = Calendar.getInstance();
                                            try {
                                                calendar.setTime(simpleDateFormat.parse(start));
                                                calendar.add(Calendar.DATE, -2);
                                                Toast.makeText(MainActivity.this, "Fetching historical steps", Toast.LENGTH_SHORT).show();
                                                new FitHistoryStepsTask().execute(simpleDateFormat.format(calendar.getTime()), simpleDateFormat.format(new Date()));
                                            } catch (ParseException e) {
                                                e.printStackTrace();
                                            }
                                        }
                                    } else {
                                        //AsyncTask to get current day's steps
                                        Toast.makeText(MainActivity.this, "Fetching today's steps data", Toast.LENGTH_SHORT).show();
                                        new RetrieveFitDailySteps().execute();
                                    }
                            }

                            @Override
                            public void onConnectionSuspended(int i) {
                                if (i == GoogleApiClient.ConnectionCallbacks.CAUSE_NETWORK_LOST) {
                                    Log.e("TAG", "Connection lost.  Cause: Network Lost.");
                                } else if (i == GoogleApiClient.ConnectionCallbacks.CAUSE_SERVICE_DISCONNECTED) {
                                    Log.e("TAG", "Connection lost.  Reason: Service Disconnected");
                                }
                            }
                        }
                )
                .addOnConnectionFailedListener(
                        new GoogleApiClient.OnConnectionFailedListener() {
                            @Override
                            public void onConnectionFailed(ConnectionResult result) {
                                Log.e("TAG", "Connection failed. Cause: " + result.getErrorCode());

                                if (result.hasResolution()) {
                                    if( !authInProgress ) {
                                        try {
                                            authInProgress = true;
                                            result.startResolutionForResult(MainActivity.this, REQUEST_OAUTH);
                                        } catch (IntentSender.SendIntentException e) {
                                            Log.e("TAG",
                                                    "Exception while starting resolution activity", e);
                                        }
                                    }
                                }
                            }
                        }
                )
                .build();

        mClient.connect();
    }

    private class GetDataSet extends AsyncTask<Void, Void, DataSet>{
        @Override
        protected DataSet doInBackground(Void... voids) {
            manualStepsHashMap = new HashMap<>();
            DataSet dataSet = getDataSet(getLastStepsPostedDate(), simpleDateFormat.format(new Date()));
            return dataSet;
        }

        @Override
        protected void onPostExecute(DataSet dataSet) {
            super.onPostExecute(dataSet);
            stringBuilder = new StringBuilder();
            showDataSet(dataSet, true);
            if(manualStepsHashMap.size() > 0) {
                iterateHashMap();
            }else {
                Toast.makeText(MainActivity.this, "Thanks for playing fair game", Toast.LENGTH_SHORT).show();
            }

            setLastStepsPostedDate(simpleDateFormat.format(new Date()));
        }
    }


    private class RetrieveFitDailySteps extends AsyncTask<Void, Void, DailyTotalResult> {

        @Override
        protected DailyTotalResult doInBackground(Void... params) {
            DailyTotalResult result = getStepDataForToday();
            return result;
        }

        @Override
        protected void onPostExecute(DailyTotalResult result) {
            super.onPostExecute(result);
            setLastStepsPostedDate(simpleDateFormat.format(new Date()));
            manualStepsHashMap = new HashMap<>();
            stringBuilder = new StringBuilder();
            showDataSet(result.getTotal(), true);

            if(manualStepsHashMap.size() > 0) {
                iterateHashMap();
            }else {
                Toast.makeText(MainActivity.this, "Thanks for playing fair game", Toast.LENGTH_SHORT).show();
            }
        }

        private DailyTotalResult getStepDataForToday() {

            DailyTotalResult result = Fitness.HistoryApi.readDailyTotal(mClient, DataType.TYPE_STEP_COUNT_DELTA).await(1, TimeUnit.MINUTES);

            return result;
        }
    }

    private DataSet getDataSet(String start, String end){
        long startTime = -9999, endTime = -9999;
        try {
            Date startDate = simpleDateFormat.parse(start);
            Date endDate = simpleDateFormat.parse(end);
            startTime = startDate.getTime();
            endTime = endDate.getTime();

        } catch (Exception e) {
            e.printStackTrace();
        }

        if (startTime == -9999 || endTime == -9999) {
            return null ;
        }
        PendingResult<DataReadResult> pendingResult = Fitness.HistoryApi.readData(
                mClient,
                new DataReadRequest.Builder()
                        .read(DataType.TYPE_STEP_COUNT_DELTA)
                        .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                        .build());

        DataReadResult readDataResult = pendingResult.await();
        DataSet dataSet = readDataResult.getDataSet(DataType.TYPE_STEP_COUNT_DELTA);
        return dataSet;

    }

    private void showDataSet(DataSet dataSet, boolean checkManuallyAddedSteps) {
        for (DataPoint dp : dataSet.getDataPoints()) {
            long end = dp.getEndTime(TimeUnit.MILLISECONDS);
            Date date = new Date(end);
            DataSource ds = dp.getOriginalDataSource();
            String stream = ds.getStreamName();
            for (Field field : dp.getDataType().getFields()) {
                if (field.getName().contains("steps")) {
                    try {
                        JSONObject object = new JSONObject();
                        String strDate = formatter_t_z.format(date);
                        int steps = dp.getValue(field).asInt();
                        if(stream.equalsIgnoreCase("user_input")) {

                            stringBuilder.append("You're caught!!!")
                                    .append(" ")
                                    .append(steps)
                                    .append(" ")
                                    .append("on")
                                    .append(" ")
                                    .append(strDate)
                            .append("\n");
                            manualStepsHashMap.put(simpleDateFormat.format(date), true);
                        }
                        object.put("date_time", strDate);
                        object.put("value", steps);
                        tv.setText(object.toString());
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
            if(checkManuallyAddedSteps)
                tv.setText(stringBuilder.toString());
        }

    }



    private class FitHistoryStepsTask extends AsyncTask<String, Void, DataReadResult> {

        @Override
        protected DataReadResult doInBackground(String... params) {
            DataReadResult dataReadResult = getHistorySteps(params[0], params[1]);

            return dataReadResult;
        }

        @Override
        protected void onPostExecute(DataReadResult dataReadResult) {
            super.onPostExecute(dataReadResult);
            stringBuilder = new StringBuilder();
            if (dataReadResult != null) {
                if (dataReadResult.getBuckets().size() > 0) {
                    for (Bucket bucket : dataReadResult.getBuckets()) {
                        List<DataSet> dataSets = bucket.getDataSets();
                        manualStepsHashMap = new HashMap<>();
                        for (DataSet dataSet : dataSets) {
                            showDataSet(dataSet, false);
                        }
                    }

                }
            }

            new GetDataSet().execute();
        }

        private DataReadResult getHistorySteps(String start, String end) {
            long startTime = -9999, endTime = -9999;
            try {
                Date startDate = simpleDateFormat.parse(start);
                Date endDate = simpleDateFormat.parse(end);
                startTime = startDate.getTime();
                endTime = endDate.getTime();

            } catch (Exception e) {
                e.printStackTrace();
            }

            if (startTime == -9999 || endTime == -9999) {
                return null;
            }
            DataSource ESTIMATED_STEP_DELTAS = new DataSource.Builder()
                    .setDataType(DataType.TYPE_STEP_COUNT_DELTA)
                    .setType(DataSource.TYPE_DERIVED)
                    .setStreamName("estimated_steps")
                    .setAppPackageName("com.google.android.gms")
                    .build();

            DataReadRequest readRequest = new DataReadRequest.Builder()
                    .aggregate(ESTIMATED_STEP_DELTAS, DataType.TYPE_STEP_COUNT_DELTA)
                    .bucketByTime(1, TimeUnit.DAYS)
                    .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                    .build();


            DataReadResult dataReadResult = Fitness.HistoryApi.readData(mClient, readRequest).await(1, TimeUnit.MINUTES);
            return dataReadResult;
        }

    }

    private void iterateHashMap(){
       StringBuilder builder = new StringBuilder();
       builder.append("You have been cheating on the date/s");
       for( Map.Entry entry : manualStepsHashMap.entrySet()){
           builder.append("\n")
                   .append(entry.getKey().toString());
       }
       //showNotification(builder.toString());
        tvTwo.setText(builder.toString());
    }

    public boolean isAvailableForBulkStepsPost() {
        try {
            String lastUpdatedDate = getLastStepsPostedDate();
            if (lastUpdatedDate.trim().equals(""))
                return false;
            Date lastUpdated = simpleDateFormat.parse(lastUpdatedDate);
            Date current = simpleDateFormat.parse(simpleDateFormat.format(new Date()));

            if (current.compareTo(lastUpdated) > 0) {
                return true;
            } else if (current.compareTo(lastUpdated) == 0) {
                return false;
            } else
                return false;

        } catch (ParseException e) {
            // TODO Auto-generated catch block
            return false;
        }
    }

    public String getLastStepsPostedDate() {
        return sharedPreferences.getString(KEY_LAST_STEPS_POSTED_ON, "");
    }

    public void setLastStepsPostedDate(String date) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_LAST_STEPS_POSTED_ON, date);
        editor.apply();
    }

}
