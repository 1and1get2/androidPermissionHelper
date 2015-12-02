package com.derek.permissionhelper;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.net.Uri;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.util.SimpleArrayMap;
import android.support.v7.app.AlertDialog;
import android.util.Log;

import junit.framework.Assert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by derek on 2/12/15.
 */
public class PermissionHelper implements ActivityCompat.OnRequestPermissionsResultCallback{
    private static final String TAG = PermissionHelper.class.getSimpleName();
    private static final AtomicInteger sNextGeneratedRequestCode = new AtomicInteger(1);

    private RequestPermissionsActivity requestPermissionsActivity;
    private Permissions[] rootPermissionGroup;
    private SimpleArrayMap<Integer, Boolean> resultReturned = new SimpleArrayMap<>();
    private SimpleArrayMap<Integer, Permissions> permissionsList;
    private List<Runnable> pendingList;
    private PermissionCallBack permissionCallBack;

    public static final class Permissions {
        private boolean isGroup = false;
        private Permissions[] permissionGroup;

        private String permissionStr;
        private boolean critical;
        private String rationaleTitle, rationaleMessage;
        private boolean granted = false;

        public Permissions(){}
        public Permissions(String permissionStr, boolean critical, String rationaleTitle, String rationaleMessage) {
            this.permissionStr = permissionStr;
            this.critical = critical;
            this.rationaleTitle = rationaleTitle;
            this.rationaleMessage = rationaleMessage;
        }
        public Permissions(String permissionStr, boolean critical) {
            this.permissionStr = permissionStr;
            this.critical = critical;
        }

        @Override
        public String toString() {
            return isGroup ?
                    Arrays.toString(permissionGroup) :
                    "Permission:" + permissionStr;
        }

        // group of permissions share the same title and message
        public static Permissions newPermissionGroup(String rationaleTitle, String rationaleMessage, Permissions... permissions) {
            Permissions permission = new Permissions();
            permission.permissionGroup = permissions;
            permission.rationaleTitle = rationaleTitle;
            permission.rationaleMessage = rationaleMessage;
            permission.isGroup = true;
            return permission;
        }
        public static Permissions newSubPermissions(String permissionStr, boolean critical) {
            Permissions permission = new Permissions(permissionStr, critical);
            return permission;
        }
        public static Permissions newPermissions(String permissionStr, boolean critical, String rationaleTitle, String rationaleMessage) {
            Permissions permission = new Permissions();
            permission.permissionStr = permissionStr;
            permission.critical = critical;
            permission.rationaleTitle = rationaleTitle;
            permission.rationaleMessage = rationaleMessage;
            return permission;
        }

        public static Permissions newPermissions(String permissionStr, String rationaleTitle, String rationaleMessage) {
            return newPermissions(permissionStr, true, rationaleTitle, rationaleMessage);
        }
        public static Permissions newPermissions(String permissionStr, boolean critical, String rationaleMessage, PackageManager packageManager) {
            return newPermissions(permissionStr, critical, "Permission required:" + getPermissionLabel(permissionStr, packageManager), rationaleMessage);
        }
        public boolean isSatisfied() {
            if (isGroup) {
                boolean satisfied = true;
                for (Permissions permissions : permissionGroup) {
                    satisfied &= permissions.isSatisfied();
                }
                return satisfied;
            } else {
                return granted || !critical;
            }
        }
    }

    private boolean checkRequestIndividualPermissionInternal(final Activity activity, final Permissions individualPermission){
        final boolean isGroup = individualPermission.isGroup;
        final Permissions[] permissions = individualPermission.permissionGroup;
        final String permissionStr = individualPermission.permissionStr;
        final String rationaleTitle = individualPermission.rationaleTitle;
        final String rationaleMessage = individualPermission.rationaleMessage;
        final boolean critical = individualPermission.critical;

        if (! hasPermission(activity, individualPermission)){
            Log.d(TAG, "Permission " + individualPermission.toString() + " is not granted yet");
            if (shouldShowRequestPermissionRationale(activity, individualPermission)
                    /*&& StringHelper.isNull(rationaleMessage)*/) {
                Log.d(TAG, "Showing explanation for Permission " + individualPermission.toString());
                new AlertDialog.Builder(activity)
                        .setTitle(rationaleTitle)
                        .setMessage(rationaleMessage)
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                requestPermission(activity, individualPermission);
                            }
                        })
                        .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                requestPermissionsActivity.removeOnRequestPermissionsResultCallback(PermissionHelper.this);
                                if (permissionCallBack != null) permissionCallBack.onFail();
                            }
                        })
                        .setIcon(android.R.drawable.btn_dialog)
                        .show();
            } else {
                Log.d(TAG, "No need to Show explanation for Permission (or can't) " + permissionStr);
                requestPermission(activity, individualPermission);

            }
            return false;
        } else {
            Log.d(TAG, "Permission " + permissionStr + "is already granted");
            return true;
        }
    }

    /**
     * ENTRY POINT:
     * ask for a number of permissions (have to be wrapped in the form of @{Permission[]}
     * @param activity
     * @param permissions
     * @param onSuccess
     * @param onFail
     */

    /**
     * ENTRY POINT:
     * ask for a number of permissions (have to be wrapped in the form of @{Permission[]}
     * @param activity
     * @param permissionCallBack
     * @param permissions
     */
    private void checkRequestPermissionInternal(final Activity activity, final PermissionCallBack permissionCallBack, final Permissions... permissions) {
        if (! (activity instanceof RequestPermissionsActivity)) {
            Log.e(TAG, "ERROR: Activity must implement RequestPermissionsActivity interface");
            return;
        }
        this.requestPermissionsActivity = (RequestPermissionsActivity) activity;
        requestPermissionsActivity.registerOnRequestPermissionsResultCallback(this);

        permissionsList = new SimpleArrayMap<>(permissions.length);
        pendingList = new ArrayList<>(permissions.length);

        this.rootPermissionGroup = permissions;
        this.permissionCallBack = permissionCallBack;

        for (int i = 0; i < permissions.length; i++){
            final int index  = i;
            pendingList.add(new Runnable() {
                @Override
                public void run() {
                    checkRequestIndividualPermissionInternal(activity, permissions[index]);
                }
            });
        }

        executePendingList();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult has been called, request code:" + requestCode);

        resultReturned.put(requestCode, true);
        Assert.assertTrue("permissionsList key has to contain :" + requestCode, permissionsList.containsKey(requestCode));
        Permissions permission = permissionsList.get(requestCode);
        permissionsList.remove(requestCode);
        parseResult(permission, permissions, grantResults);

        if (pendingList.size() == 0){
            requestPermissionsActivity.removeOnRequestPermissionsResultCallback(this);
            if (checkCriticalPermissionSatisfied(this.rootPermissionGroup)) {
                if (permissionCallBack != null) permissionCallBack.onSuccess();
            } else {
                if (permissionCallBack != null) permissionCallBack.onFail();
            }
        } else {
            executePendingList();
        }

    }
    private void executePendingList(){
        pendingList.remove(0).run();
    }

    /* Launcher */
    public static PermissionHelper checkRequestPermission(Activity activity, PermissionCallBack permissionCallBack, final Permissions... permissions){
        Log.d(TAG, "checkRequestPermission");
        PermissionHelper helper = new PermissionHelper();
        helper.checkRequestPermissionInternal(activity, permissionCallBack, permissions);
        return helper;
    }

    /* Helper */
    private synchronized int requestPermission(Activity activity, Permissions permission){
        int requestCode = getNewRequestCode();
        Log.d(TAG, "requesting permission:" + permission.toString() + " with requestCode:" + requestCode);
        ActivityCompat.requestPermissions(activity,
                getPermissionStr(permission),
                requestCode);
        resultReturned.put(requestCode, false);
        permissionsList.put(requestCode, permission);
        return requestCode;
    }
    private static void parseResult(Permissions permission, @NonNull String[] permissions, @NonNull int[] grantResults){
        if (permission.isGroup) {
            for (Permissions sub : permission.permissionGroup) {
                parseResult(sub, permissions, grantResults);
            }
        } else {
            for (int i = 0; i < permissions.length; i++) {
                String permissionStr = permissions[i];
                if (permissionStr.equals(permission.permissionStr)) {
                    permission.granted = grantResults.length > i && grantResults[i] == PackageManager.PERMISSION_GRANTED;
                    Log.i(TAG, "Permission" + permission.permissionStr + "granted? ---=== >>>" + permission.granted);
                    break;
                }
            }
        }
    }
    private static boolean checkCriticalPermissionSatisfied(Permissions... permissionGroup) {
        boolean satisfied = true;
        for (Permissions permissions : permissionGroup) {
            satisfied &= permissions.isSatisfied();
        }
        return satisfied;
    }
    private static String[] getPermissionStr(Permissions permission){
        String[] strings;
        if (permission.isGroup) {
            int length = permission.permissionGroup.length;
            strings = new String[length];
            for (int i = 0; i < length; i++) {
                strings[i] = permission.permissionGroup[i].permissionStr;
            }
        } else {
            strings = new String[] {permission.permissionStr};
        }
        return strings;
    }
    private static int getNewRequestCode(){
        for (;;) {
            final int result = sNextGeneratedRequestCode.get();
            int newValue = result + 1;
            if (newValue > 0x00FFFFFF) newValue = 1; // Roll over to 1, not 0.
            if (sNextGeneratedRequestCode.compareAndSet(result, newValue)) {
                return result;
            }
        }
    }
    public static void openAppSetting(Activity activity) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
        intent.setData(uri);
        activity.startActivityForResult(intent, 0);
    }

    public static boolean hasPermission(Context context, String permission) {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
        //return PackageManager.PERMISSION_GRANTED == pkgmanager.checkPermission(permissionStr, packageName);
    }
    private static boolean hasPermission(Context context, Permissions permission) {
        if (permission.isGroup) {
            boolean granted = true;
            for (Permissions permissions : permission.permissionGroup) {
                granted &= hasPermission(context, permissions);
            }
            return granted;
        } else {
            return permission.granted = hasPermission(context, permission.permissionStr);
        }
    }
    private static boolean shouldShowRequestPermissionRationale(Activity activity, Permissions permissions){
        if (permissions.isGroup) {
            for (Permissions permission : permissions.permissionGroup) {
                if (shouldShowRequestPermissionRationale(activity, permission)) return true;
            }
            return false;
        } else {
            return ActivityCompat.shouldShowRequestPermissionRationale(activity, permissions.permissionStr);
        }
    }

    /**
     * Returns permissions' name (human-readable label) by permissionStr key
     */
    public static CharSequence getPermissionLabel(String permission, PackageManager packageManager) {
        try {
            PermissionInfo permissionInfo = packageManager.getPermissionInfo(permission, 0);
            return permissionInfo.loadLabel(packageManager);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return permission;
        //return null;
    }


    public static CharSequence getPermissionDescription(String permission, PackageManager packageManager) {
        try {
            PermissionInfo permissionInfo = packageManager.getPermissionInfo(permission, 0);
            return permissionInfo.loadDescription(packageManager);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    public interface RequestPermissionsActivity {
        public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults);
        public void registerOnRequestPermissionsResultCallback(ActivityCompat.OnRequestPermissionsResultCallback callback);
        public boolean removeOnRequestPermissionsResultCallback(ActivityCompat.OnRequestPermissionsResultCallback callback);
    }
    public interface PermissionCallBack {
        public void onSuccess();
        public void onFail();
    }
}
