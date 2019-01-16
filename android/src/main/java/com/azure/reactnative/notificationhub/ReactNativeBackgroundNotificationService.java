package com.azure.reactnative.notificationhub;

import android.content.Intent;
import android.os.Bundle;
import com.facebook.react.HeadlessJsTaskService;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.jstasks.HeadlessJsTaskConfig;

import javax.annotation.Nullable;

public class ReactNativeBackgroundNotificationService extends HeadlessJsTaskService {
    @Override
    protected @Nullable HeadlessJsTaskConfig getTaskConfig(Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras != null && extras.getString("taskName") != null) {
            return new HeadlessJsTaskConfig(
                    extras.getString("taskName"),
                    Arguments.fromBundle(extras),
                    5000,
                    true
            );
        } else {
            return null;
        }
    }
}
