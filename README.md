# androidPermissionHelper
refer to official api website:
[http://developer.android.com/training/permissions/index.html]
[http://developer.android.com/guide/topics/security/permissions.html]

any suggestion / imporvement / bug, [please let me know: 1and1get2@gmail.com](mailto:1and1get2@gmail.com) 

for testing, use these command from adb shell
```
shell@crespo:/ $ pm grant com.paymark.expenses android.permission.CAMERA
shell@crespo:/ $ pm revoke com.paymark.expenses android.permission.CAMERA
```

```java
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
```