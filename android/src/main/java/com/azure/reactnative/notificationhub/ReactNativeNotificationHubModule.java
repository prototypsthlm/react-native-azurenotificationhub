package com.azure.reactnative.notificationhub;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import com.microsoft.windowsazure.messaging.NotificationHub;
import com.microsoft.windowsazure.notifications.NotificationsManager;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.UiThreadUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Set;

public class ReactNativeNotificationHubModule extends ReactContextBaseJavaModule implements LifecycleEventListener {
    public static final String NOTIF_REGISTER_AZURE_HUB_EVENT = "azureNotificationHubRegistered";
    public static final String NOTIF_AZURE_HUB_REGISTRATION_ERROR_EVENT = "azureNotificationHubRegistrationError";
    public static final String DEVICE_NOTIF_EVENT = "remoteNotificationReceived";

    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
    private static final int NOTIFICATION_DELAY_ON_START = 0;

    private static final String ERROR_INVALID_ARGUMENTS = "E_INVALID_ARGUMENTS";
    private static final String ERROR_PLAY_SERVICES = "E_PLAY_SERVICES";
    private static final String ERROR_NOTIFICATION_HUB = "E_NOTIFICATION_HUB";
    private static final String ERROR_NOT_REGISTERED = "E_NOT_REGISTERED";

    private ReactApplicationContext  mReactContext;
    private LocalBroadcastReceiver  mLocalBroadcastReceiver;

    public ReactNativeNotificationHubModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.mReactContext = reactContext;
        this.mLocalBroadcastReceiver = new LocalBroadcastReceiver();
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(reactContext);
        localBroadcastManager.registerReceiver(mLocalBroadcastReceiver, new IntentFilter(ReactNativeRegistrationIntentService.TAG));
        localBroadcastManager.registerReceiver(mLocalBroadcastReceiver, new IntentFilter(ReactNativeNotificationsHandler.TAG));
        reactContext.addLifecycleEventListener(this);
    }

    @Override
    public String getName() {
        return "AzureNotificationHub";
    }

    @ReactMethod
    public void register(ReadableMap config, Promise promise) {
        NotificationHubUtil notificationHubUtil = NotificationHubUtil.getInstance();
        String connectionString = config.getString("connectionString");
        if (connectionString == null) {
            promise.reject(ERROR_INVALID_ARGUMENTS, "Connection string cannot be null.");
        }

        String hubName = config.getString("hubName");
        if (hubName == null) {
            promise.reject(ERROR_INVALID_ARGUMENTS, "Hub name cannot be null.");
        }

        String senderID = config.getString("senderID");
        if (senderID == null) {
            promise.reject(ERROR_INVALID_ARGUMENTS, "Sender ID cannot be null.");
        }

        String[] tags = null;
        if (config.hasKey("tags") && !config.isNull("tags")) {
            ReadableArray tagsJson = config.getArray("tags");
            tags = new String[tagsJson.size()];
            for (int i = 0; i < tagsJson.size(); ++i) {
                tags[i] = tagsJson.getString(i);
            }
        }

        ReactContext reactContext = getReactApplicationContext();
        notificationHubUtil.setConnectionString(reactContext, connectionString);
        notificationHubUtil.setHubName(reactContext, hubName);
        notificationHubUtil.setTags(reactContext, tags);

        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(reactContext);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (apiAvailability.isUserResolvableError(resultCode)) {
                UiThreadUtil.runOnUiThread(
                        new GoogleApiAvailabilityRunnable(
                                getCurrentActivity(),
                                apiAvailability,
                                resultCode));
                promise.reject(ERROR_PLAY_SERVICES, "User must enable Google Play Services.");
            } else {
                promise.reject(ERROR_PLAY_SERVICES, "This device is not supported by Google Play Services.");
            }
            return;
        }

        Intent intent = new Intent(reactContext, ReactNativeRegistrationIntentService.class);
        reactContext.startService(intent);
        NotificationsManager.handleNotifications(reactContext, senderID, ReactNativeNotificationsHandler.class);
    }

    @ReactMethod
    public void unregister(Promise promise) {
        NotificationHubUtil notificationHubUtil = NotificationHubUtil.getInstance();

        ReactContext reactContext = getReactApplicationContext();
        String connectionString = notificationHubUtil.getConnectionString(reactContext);
        String hubName = notificationHubUtil.getHubName(reactContext);
        String registrationId = notificationHubUtil.getRegistrationID(reactContext);

        if (connectionString == null || hubName == null || registrationId == null) {
            promise.reject(ERROR_NOT_REGISTERED, "No registration to Azure Notification Hub.");
        }

        NotificationHub hub = new NotificationHub(hubName, connectionString, reactContext);
        try {
            hub.unregister();
            notificationHubUtil.setRegistrationID(reactContext, null);
            NotificationsManager.stopHandlingNotifications(reactContext);
        } catch (Exception e) {
            promise.reject(ERROR_NOTIFICATION_HUB, e);
        }
    }

    private Bundle getBundleFromIntent(Intent intent) {
        Bundle bundle = null;
        if (intent.hasExtra("notification")) {
            bundle = intent.getBundleExtra("notification");
        } else if (intent.hasExtra("google.message_id")) {
            bundle = intent.getExtras();
        }
        return bundle;
    }

    @ReactMethod
    public void getInitialNotification(Promise promise) {
        WritableMap params = Arguments.createMap();
        Activity activity = getCurrentActivity();
        if (activity != null) {
            Intent intent = activity.getIntent();
            Bundle bundle = getBundleFromIntent(intent);
            if (bundle != null) {
                bundle.putBoolean("openedByNotification", true);
                String bundleString = convertJSON(bundle);
                params.putString("dataJSON", bundleString);
            }
        }
        promise.resolve(params);
    }

    private String convertJSON(Bundle bundle) {
        JSONObject json = new JSONObject();
        Set<String> keys = bundle.keySet();
        for (String key : keys) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    json.put(key, JSONObject.wrap(bundle.get(key)));
                } else {
                    json.put(key, bundle.get(key));
                }
            } catch (JSONException e) {
                return null;
            }
        }
        return json.toString();
    }

    @Override
    public void onHostResume() {
        /*
        So we've moved handling notifications to getInitialNotification instead of pushing them
        as when pushing we're not sure that all of react native components have mounted yet
        Activity activity = getCurrentActivity();
        if (activity != null) {
            Intent intent = activity.getIntent();
            if (intent != null) {
                Bundle bundle = getBundleFromIntent(intent);
                if (bundle != null) {
                    bundle.putBoolean("openedByNotification", true);
                    new ReactNativeNotificationsHandler().sendBroadcast(mReactContext, bundle, NOTIFICATION_DELAY_ON_START);
                }
            }
        }*/
    }
    @Override
    public void onHostPause() {}
    @Override
    public void onHostDestroy() {}
    public class LocalBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String event = intent.getStringExtra("event");
            String data = intent.getStringExtra("data");
            mReactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                    .emit(event, data);
        }
    }

    private static class GoogleApiAvailabilityRunnable implements Runnable {
        private final Activity activity;
        private final GoogleApiAvailability apiAvailability;
        private final int resultCode;

        public GoogleApiAvailabilityRunnable(
                Activity activity,
                GoogleApiAvailability apiAvailability,
                int resultCode) {
            this.activity = activity;
            this.apiAvailability = apiAvailability;
            this.resultCode = resultCode;
        }

        @Override
        public void run() {
            apiAvailability.getErrorDialog(activity, resultCode, PLAY_SERVICES_RESOLUTION_REQUEST).show();
        }
    }
}
