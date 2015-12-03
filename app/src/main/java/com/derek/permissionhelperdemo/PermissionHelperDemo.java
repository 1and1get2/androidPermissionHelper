package com.derek.permissionhelperdemo;

import android.Manifest;
import android.app.Activity;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.util.SimpleArrayMap;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatButton;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;

import com.derek.permissionhelper.PermissionHelper;
import com.derek.permissionhelper.PermissionHelper.RLog;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class PermissionHelperDemo extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback, PermissionHelper.RequestPermissionsActivity {
    private static final String TAG = "PermissionHelperDemo";
    private LinearLayout rootLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permission_helper_demo);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        rootLayout = (LinearLayout) findViewById(R.id.root);

        AppCompatButton button = new AppCompatButton(this);
        button.setOnClickListener(onClickListener);
        button.setText("Request permission");
        rootLayout.addView(button, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
    }

    private View.OnClickListener onClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Log.v(TAG, "check & request permission");
            PermissionHelper.checkRequestPermission(
                    PermissionHelperDemo.this,
                    new PermissionHelper.PermissionCallBack() {
                        @Override
                        public void onSuccess() {
                            Log.i(TAG, "Permission granted");
                        }

                        @Override
                        public void onFail() {
                            Log.e(TAG, "Critical permission not granted, aborting the ship");
                        }
                    },
                    PermissionHelper.Permissions.newPermissions(Manifest.permission.READ_CONTACTS, true, "RAtInaLE MesSAgE: wE kInDdA nEeD ThiS pErmISsion, cUZ wE jUSt wAnT IT", getPackageManager()),
                    PermissionHelper.Permissions.newPermissionGroup("Title", "Message",
                            PermissionHelper.Permissions.newSubPermissions(Manifest.permission.READ_SMS, false),
                            PermissionHelper.Permissions.newSubPermissions(Manifest.permission.READ_CALENDAR, false),
                            PermissionHelper.Permissions.newSubPermissions(Manifest.permission.READ_CONTACTS, false)),
                    PermissionHelper.Permissions.newPermissionGroup("Title", "Message: call & body",
                            PermissionHelper.Permissions.newSubPermissions(Manifest.permission.CALL_PHONE, false),
                            PermissionHelper.Permissions.newSubPermissions(Manifest.permission.BODY_SENSORS, false),
                            PermissionHelper.Permissions.newSubPermissions(Manifest.permission.READ_SMS, false))
            ).setPermissionResultCallBack(new PermissionHelper.PermissionResultCallBack() {
                @Override
                public void onUpdate(SimpleArrayMap result) {
                    PermissionHelper.RLog.i(TAG, "onUpdate:", result.size());
                }

                @Override
                public void onFinalResult(SimpleArrayMap result) {
                    PermissionHelper.RLog.i(TAG, "onFinalResult:", result.size());
                }
            }).setPermissionShowRationalCallBack(new PermissionHelper.PermissionShowRationalCallBack() {
                        @Override
                        public void onShowRational(Activity activity, String rationaleTitle, String rationaleMessage,
                                                   final PermissionHelper.PostShowRationalCallBack postShowRationalCallBack) {
                            new AlertDialog.Builder(activity)
                                    .setTitle(rationaleTitle)
                                    .setMessage(rationaleMessage)
                                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int which) {
                                            RLog.i(TAG, "INLINE - YES");
                                            postShowRationalCallBack.requestPermission(true);
                                        }
                                    })
                                    .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int which) {
                                            RLog.i(TAG, "INLINE - NO");
                                        }
                                    })
                                    .setIcon(android.R.drawable.ic_menu_help)
                                    .show();
                        }
            });
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_permission_helper_demo, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    /* Permission */
    private List<ActivityCompat.OnRequestPermissionsResultCallback> onRequestPermissionsResultCallbackList = new ArrayList<>();

    private final Lock lock = new ReentrantLock();
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        synchronized (lock) {
            for (ActivityCompat.OnRequestPermissionsResultCallback callback : onRequestPermissionsResultCallbackList) {
                if (callback != null) callback.onRequestPermissionsResult(requestCode, permissions, grantResults);
            }
        }
        //PermissionHelper.cancelAll();
    }
    public void registerOnRequestPermissionsResultCallback(ActivityCompat.OnRequestPermissionsResultCallback callback) {
        synchronized (lock) {onRequestPermissionsResultCallbackList.add(callback);}
    }
    public boolean removeOnRequestPermissionsResultCallback(ActivityCompat.OnRequestPermissionsResultCallback callback) {
        boolean removed;
        synchronized (lock) {
            removed = onRequestPermissionsResultCallbackList.remove(callback);
        }
        return removed;
    }
}
