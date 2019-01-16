package com.azure.reactnative.notificationhub;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import com.microsoft.windowsazure.notifications.NotificationsHandler;

import com.facebook.react.HeadlessJsTaskService;

public class ReactNativeBackgroundNotificationHandler extends NotificationsHandler {
    public static final String TAG = "ReactNativeBackgroundNotificationHandler";
    private static final String NOTIFICATION_CHANNEL_ID = "rn-push-notification-channel-id";
    private static final String CHANNEL_ID = "channel_02";
    private static final long DEFAULT_VIBRATION = 300L;

    private Context context;

    @Override
    public void onReceive(Context context, Bundle bundle) {
        this.context = context;
        Log.i(TAG, "We got a notification");
        String taskName = NotificationHubUtil.getInstance().getBackgroundTaskName(context);
        if (taskName != null) {
            sendToBackground(context, bundle, taskName);
        } else {
            Log.d(TAG, "No task name");
        }
    }

    private void sendToBackground(Context context, final Bundle bundle, final @NonNull String taskName) {
        HeadlessJsTaskService.acquireWakeLockNow(context);
        Intent service = new Intent(context, ReactNativeBackgroundNotificationService.class);
        Bundle serviceBundle = new Bundle(bundle);
        serviceBundle.putString("taskName", taskName);
        service.putExtras(serviceBundle);
        context.startService(service);
    }
}
