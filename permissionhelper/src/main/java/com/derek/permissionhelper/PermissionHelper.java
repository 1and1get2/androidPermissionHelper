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

import java.lang.ref.WeakReference;
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
    private SimpleArrayMap<String, Boolean> resultList; // used to record all the permission status

    private List<Runnable> pendingList;

    private PermissionCallBack permissionCallBack;
    private PermissionShowRationalCallBack permissionShowRationalCallBack;
    private PermissionResultCallBack permissionResultCallBack;

    private WeakReference<PermissionHelper> self;

    public static final class Permissions {
        private boolean isGroup = false;
        private boolean shouldShowRational = false;
        private Permissions[] permissionGroup;

        private String permissionStr;
        private boolean critical = true;
        private String rationaleTitle, rationaleMessage;
        private boolean granted = false;

        public Permissions(){}
        public Permissions(String permissionStr, boolean critical, String rationaleTitle, String rationaleMessage) {
            this.permissionStr = permissionStr;
            this.critical = critical;
            this.rationaleTitle = rationaleTitle;
            this.rationaleMessage = rationaleMessage;
            this.shouldShowRational = true;
        }
        public Permissions(String permissionStr, boolean critical) {
            this.permissionStr = permissionStr;
            this.critical = critical;
        }
        public Permissions showRational(boolean show) {
            this.shouldShowRational = show;
            return this;
        }
        public Permissions critical(boolean critical) {
            this.critical = critical;
            return this;
        }
        public Permissions title(String rationaleTitle) {
            this.rationaleTitle = rationaleTitle;
            return this;
        }
        public Permissions message(String rationaleMessage) {
            this.rationaleMessage = rationaleMessage;
            return this;
        }

        public String getRationaleTitle() {
            return rationaleTitle != null ? rationaleTitle : "";
        }
        public String getRationaleMessage() {
            return rationaleMessage != null ? rationaleMessage : "";
        }
        public String getRationaleTitle(Context context) {
            return rationaleTitle != null ?
                    rationaleTitle :
                    (isGroup ? toString() : PermissionHelper.getPermissionLabel(permissionStr, context.getPackageManager()).toString());
        }
        public String getRationaleMessage(Context context) {
            return rationaleMessage != null ?
                    rationaleMessage :
                    (isGroup ? toString() : PermissionHelper.getPermissionDescription(permissionStr, context.getPackageManager()).toString());
        }

        @Override
        public String toString() {
            return isGroup ?
                    "Permission Group:" + Arrays.toString(permissionGroup) :
                    "Permission:" + permissionStr;
        }

        /**
         * group of permissions share the same title and message
         * @param permissions
         * @return
         */
        public static Permissions newPermissionGroup(Permissions... permissions) {
            Permissions permission = new Permissions();
            permission.permissionGroup = permissions;
            permission.isGroup = true;
            return permission;
        }
        public static Permissions newPermissionGroup(String rationaleTitle, String rationaleMessage, Permissions... permissions) {
            Permissions permission = new Permissions();
            permission.permissionGroup = permissions;
            permission.rationaleTitle = rationaleTitle;
            permission.rationaleMessage = rationaleMessage;
            permission.shouldShowRational = true;
            permission.isGroup = true;
            return permission;
        }
        public static Permissions newSubPermissions(String permissionStr, boolean critical) {
            Permissions permission = new Permissions(permissionStr, critical);
            return permission;
        }
        public static Permissions newPermissions(String permissionStr, boolean critical, String rationaleTitle, String rationaleMessage) {
            Permissions permission = new Permissions();
            permission.shouldShowRational = true;
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

    public PermissionHelper() {
        this.permissionShowRationalCallBack = permissionShowRationalCallBackInternal;
    }

    private synchronized boolean checkRequestIndividualPermissionInternal(final Activity activity, final Permissions individualPermission){
        final String permissionStr = individualPermission.permissionStr;

        if (! hasPermission(activity, individualPermission)){
            RLog.d(TAG, "Permission", individualPermission.toString(), "is not granted yet");
            if (individualPermission.shouldShowRational && shouldShowRequestPermissionRationale(activity, individualPermission)) {
                showRational(activity, individualPermission);
            } else {
                RLog.d(TAG, "No need to Show explanation for Permission (or can't)", permissionStr);
                requestPermission(activity, individualPermission);

            }
            return false;
        } else {
            RLog.d(TAG, "Permission", permissionStr, "is already granted");
            resultList.put(permissionStr, true);
            executePendingList();
            return true;
        }
    }

    /**
     * ENTRY POINT:
     * ask for a number of permissions (have to be wrapped in the form of @{Permission[]}
     * @param activity
     * @param permissionCallBack
     * @param permissions
     */
    private PermissionHelper checkRequestPermissionInternal(final Activity activity, final PermissionCallBack permissionCallBack, final Permissions... permissions) {
        if (! (activity instanceof RequestPermissionsActivity)) {
            RLog.e(TAG, "ERROR: Activity must implement RequestPermissionsActivity interface");
            return null;
        }
        this.requestPermissionsActivity = SC.ast(activity, RequestPermissionsActivity.class);
        requestPermissionsActivity.registerOnRequestPermissionsResultCallback(this);

        permissionsList = new SimpleArrayMap<>(permissions.length);
        pendingList = new ArrayList<>(permissions.length);
        resultList = new SimpleArrayMap<>();

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
        return this;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        RLog.d(TAG, "onRequestPermissionsResult has been called, request code:", requestCode);

        resultReturned.put(requestCode, true);
        Permissions permission = permissionsList.remove(requestCode);
        if (permission == null) {
            RLog.e(TAG, "Unable to get permission, which should never happen");
        } else {
            parseResult(permission, permissions, grantResults);
        }
        executePendingList();

    }
    public PermissionHelper setPermissionShowRationalCallBack (PermissionShowRationalCallBack permissionShowRationalCallBack) {
        this.permissionShowRationalCallBack = permissionShowRationalCallBack;
        return this;
    }
    public PermissionHelper setPermissionResultCallBack(PermissionResultCallBack callBack) {
        this.permissionResultCallBack = callBack;
        return this;
    }
    private void showRational(final Activity activity, final Permissions individualPermission) {
        RLog.d(TAG, "Showing explanation for Permission", individualPermission.toString());
        final String rationaleTitle = individualPermission.getRationaleTitle(activity.getApplicationContext());
        final String rationaleMessage = individualPermission.getRationaleMessage(activity.getApplicationContext());

        permissionShowRationalCallBack.onShowRational(activity, rationaleTitle, rationaleMessage, new PostShowRationalCallBack() {
            @Override
            public void requestPermission(boolean requestPermission) {
                if (requestPermission) PermissionHelper.this.requestPermission(activity, individualPermission);
            }
        });
    }
    private void executePendingList(){
        if (permissionResultCallBack != null) permissionResultCallBack.onUpdate(this.resultList);
        if (pendingList.size() >= 1) {
            pendingList.remove(0).run();
        } else {
            finish();
        }
    }
    private void finish(){
        requestPermissionsActivity.removeOnRequestPermissionsResultCallback(this);
        if (permissionResultCallBack != null) permissionResultCallBack.onFinalResult(this.resultList);
        if (checkCriticalPermissionSatisfied(this.rootPermissionGroup)) {
            onSuccess();
        } else {
            onFail();
        }
        permissionHelpers.remove(self);
    }
    private void onSuccess(){
        RLog.v(TAG, "Succeed");
        if (permissionCallBack != null) permissionCallBack.onSuccess();
    }

    private void onFail(){
        RLog.v(TAG, "Failed");
        if (permissionCallBack != null) permissionCallBack.onFail();
    }

    public void cancel() {
        permissionCallBack = null;
        permissionShowRationalCallBack = null;
        permissionResultCallBack = null;

        requestPermissionsActivity = null;
        rootPermissionGroup = null;
        resultReturned = null;
        permissionsList = null;
        resultList = null;

        pendingList = null;

        self = null;
        requestPermissionsActivity = null;
    }

    /* Launcher */
    public static List<WeakReference<PermissionHelper>> permissionHelpers;

    public static PermissionHelper checkRequestPermission(Activity activity, PermissionCallBack permissionCallBack, final Permissions... permissions){
        RLog.d(TAG, "checkRequestPermission");
        if (permissionHelpers == null) permissionHelpers = new ArrayList<>();
        PermissionHelper helper = new PermissionHelper();
        helper.checkRequestPermissionInternal(activity, permissionCallBack, permissions);
        helper.self = new WeakReference<>(helper);
        permissionHelpers.add(helper.self);
        return helper;
    }

    // DON'T USE THIS
    @Deprecated
    public static void cancelAll(){
        RLog.i(TAG, "on cancel, clear everything");
        if (permissionHelpers == null) return;
        for (WeakReference<PermissionHelper> weakReference : permissionHelpers) {
            PermissionHelper helper = weakReference.get();
            if (helper != null) helper.cancel();
        }
    }

    /* Helper */
    private synchronized int requestPermission(Activity activity, Permissions permission){
        int requestCode = getNewRequestCode();
        RLog.d(TAG, "requesting permission:", permission.toString(), "with requestCode:", requestCode);
        ActivityCompat.requestPermissions(activity,
                getPermissionStr(permission),
                requestCode);
        resultReturned.put(requestCode, false);
        permissionsList.put(requestCode, permission);/**/
        return requestCode;
    }
    private void parseResult(Permissions permission, @NonNull String[] permissions, @NonNull int[] grantResults){
        if (permission.isGroup) {
            for (Permissions sub : permission.permissionGroup) {
                parseResult(sub, permissions, grantResults);
            }
        } else {
            for (int i = 0; i < permissions.length; i++) {
                String permissionStr = permissions[i];
                if (permissionStr.equals(permission.permissionStr)) {
                    permission.granted = grantResults.length > i && grantResults[i] == PackageManager.PERMISSION_GRANTED;
                    resultList.put(permissionStr, permission.granted);
                    if (permissionResultCallBack != null) permissionResultCallBack.onUpdate(this.resultList);
                    RLog.i(TAG, "Permission", permission.permissionStr, "granted? ---=== >>>", permission.granted);
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

    public final PermissionShowRationalCallBack permissionShowRationalCallBackInternal = new PermissionShowRationalCallBack() {
        @Override
        public final void onShowRational(final Activity activity, final String rationaleTitle, final String rationaleMessage, final PostShowRationalCallBack postShowRationalCallBack) {
            new AlertDialog.Builder(activity)
                    .setTitle(rationaleTitle)
                    .setMessage(rationaleMessage)
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            postShowRationalCallBack.requestPermission(true);
                        }
                    })
                    .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {

                        }
                    })
                    .setIcon(android.R.drawable.btn_dialog)
                    .show();
        }
    };

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

    /* Interfaces */
    public interface PermissionResultCallBack {
        void onUpdate(SimpleArrayMap result);
        void onFinalResult(SimpleArrayMap result);

    }
    public interface RequestPermissionsActivity {
        void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults);
        void registerOnRequestPermissionsResultCallback(ActivityCompat.OnRequestPermissionsResultCallback callback);
        boolean removeOnRequestPermissionsResultCallback(ActivityCompat.OnRequestPermissionsResultCallback callback);
    }
    public interface PermissionShowRationalCallBack {
        void onShowRational(final Activity activity, final String rationaleTitle, final String rationaleMessage, final PostShowRationalCallBack postShowRationalCallBack);
    }
    public interface PostShowRationalCallBack {
        void requestPermission(boolean requestPermission);
    }
    public interface PermissionCallBack {
        void onSuccess();
        void onFail();
    }

    /* Util */
    private static String combineObjectsToString(Object... objects){
        if(objects == null){
            return "null";
        }

        StringBuilder stringBuilder = new StringBuilder();

        String message = "";

        for(Object object : objects){
            if(object != null) {
                stringBuilder.append(object.toString());
            }else {
                stringBuilder.append("[null]");
            }

            stringBuilder.append(" ");
        }

        return stringBuilder.toString();
    }
    public static class RLog{
        public static void d (String tag, Object... objects){
            Log.d(tag, combineObjectsToString(objects));
        }
        public static void i (String tag, Object... objects){
            Log.i(tag, combineObjectsToString(objects));
        }
        public static void v (String tag, Object... objects){
            Log.v(tag, combineObjectsToString(objects));
        }
        public static void e (String tag, Object... objects){
            Log.e(tag, combineObjectsToString(objects));
        }
    }
    public static class SC {
        public static <T> T ast(Object object, Class<T> clazz){
            if(object == null) return null;

            if(clazz == null || clazz.isAssignableFrom(object.getClass()) || object.getClass().isAssignableFrom(clazz)){
                try {
                    if(clazz != null) return clazz.cast(object);
                    else return (T) object;

                }catch (Exception e){
                    Log.e(TAG, "Object is not of type " + clazz);
                    return null;
                }

            }else {
                return null;
            }
        }
    }

}
