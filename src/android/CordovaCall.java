package com.dmarc.cordovacall;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;

import android.os.Build;
import android.os.Bundle;
import android.os.UserHandle;
import androidx.annotation.RequiresApi;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.Manifest;
import android.telecom.Connection;
import org.json.JSONArray;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.HashMap;
import android.graphics.drawable.Icon;
import android.media.AudioManager;
import android.util.Log;

import com.eunite.atwork.R;

public class CordovaCall extends CordovaPlugin {

    private static String TAG = "CordovaCall";
    public static final int CALL_PHONE_REQ_CODE = 0;
    public static final int REAL_PHONE_CALL = 1;
    private int permissionCounter = 0;
    private String pendingAction;
    private TelecomManager tm;
    private PhoneAccountHandle handle;
    private PhoneAccount phoneAccount;
    private UserHandle userHandle;
    private CallbackContext callbackContext;
    private String appName;
    private String from;
    private String fromId;
    private String to;
    private String toId;
    private String realCallTo;
    private static HashMap<String, ArrayList<CallbackContext>> callbackContextMap = new HashMap<String, ArrayList<CallbackContext>>();
    private static CordovaInterface cordovaInterface;
    private static Icon icon;

    public static HashMap<String, ArrayList<CallbackContext>> getCallbackContexts() {
        return callbackContextMap;
    }

    public static CordovaInterface getCordova() {
        return cordovaInterface;
    }

    public static Icon getIcon() {
        return icon;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        Log.d(TAG, "registerEvent: initialize ...");
        cordovaInterface = cordova;
        super.initialize(cordova, webView);
        Context context = this.cordova.getActivity().getApplicationContext();
        appName = getApplicationName(context);
        handle = new PhoneAccountHandle(new ComponentName(context, MyConnectionService.class), appName);
        // TODO PhoneAccountHandle with UserHandle
        //userHandle = new UserHandle(context);
        //handle = new PhoneAccountHandle(new ComponentName(context, MyConnectionService.class), appName, userHandle);
        tm = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
        /*
        2020-07-04 Disable registerPhoneAccount in Android (comment code block)
        */
        // <preference name="StatusBarBackgroundColor" value="#0D47A1" />
        if(android.os.Build.VERSION.SDK_INT >= 26) {
            //Bundle extras = new Bundle();
            //extras.putBoolean(PhoneAccount.EXTRA_LOG_SELF_MANAGED_CALLS, false);
            phoneAccount = new PhoneAccount.Builder(handle, appName)
                  .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
                  //.setIcon(Icon.createWithResource(context, R.drawable.ic_stat_name)) // where to take effect?
                  //.setHighlightColor(0xff8000e0) // where to take effect?
                  //.addSupportedUriScheme("sip") // try scheme "eunite", "atwork", "uden"?
                  //.setExtras(extras)
                  .build();
          tm.registerPhoneAccount(phoneAccount);
        }
        /*
        // 2023-01-19 Fix Error Android 13 API 33 K.Mod
        // 2023-01-30 Fix Error Android ALL by KM
        if(android.os.Build.VERSION.SDK_INT >= 23 && android.os.Build.VERSION.SDK_INT <= 32) {
            Bundle extras = new Bundle();
            extras.putBoolean(PhoneAccount.EXTRA_LOG_SELF_MANAGED_CALLS, false);
            phoneAccount = new PhoneAccount.Builder(handle, appName)
                   .setCapabilities(PhoneAccount.CAPABILITY_CALL_SUBJECT)
                   .setIcon(Icon.createWithResource(context, R.drawable.ic_stat_name))
                   .setHighlightColor(0x000D47A1) // notification center, native call session ui
                   .build();
            extras.putBoolean(PhoneAccount.EXTRA_LOG_SELF_MANAGED_CALLS, false);
            tm.registerPhoneAccount(phoneAccount);
        }
         */
        callbackContextMap.put("answer",new ArrayList<CallbackContext>());
        callbackContextMap.put("reject",new ArrayList<CallbackContext>());
        callbackContextMap.put("hangup",new ArrayList<CallbackContext>());
        callbackContextMap.put("sendCall",new ArrayList<CallbackContext>());
        callbackContextMap.put("receiveCall",new ArrayList<CallbackContext>());
        Log.d(TAG, "registerEvent: initialize END. callbackContextMap=" + callbackContextMap);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void onResume(boolean multitasking) {
        super.onResume(multitasking);
        this.checkCallPermission();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        this.callbackContext = callbackContext;
        if (action.equals("receiveCall")) {
            Connection conn = MyConnectionService.getConnection();
            if(conn != null) {
                if(conn.getState() == Connection.STATE_ACTIVE) {
                    this.callbackContext.error("You can't receive a call right now because you're already in a call");
                } else {
                    this.callbackContext.error("You can't receive a call right now");
                }
            } else {
                from = args.getString(0);
                fromId = args.getString(1);
                permissionCounter = 2;
                pendingAction = "receiveCall";
                this.checkCallPermission();
            }
            return true;
        } else if (action.equals("sendCall")) {
            Connection conn = MyConnectionService.getConnection();
            if(conn != null) {
                if(conn.getState() == Connection.STATE_ACTIVE) {
                    this.callbackContext.error("You can't make a call right now because you're already in a call");
                } else if(conn.getState() == Connection.STATE_DIALING) {
                    this.callbackContext.error("You can't make a call right now because you're already trying to make a call");
                } else {
                    this.callbackContext.error("You can't make a call right now");
                }
            } else {
                to = args.getString(0);
                toId = args.getString(1);
                permissionCounter = 2;
                pendingAction = "sendCall";
                this.checkCallPermission();
                /*cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        getCallPhonePermission();
                    }
                });*/
            }
            return true;
        } else if (action.equals("connectCall")) {
            Connection conn = MyConnectionService.getConnection();
            if(conn == null) {
                this.callbackContext.error("No call exists for you to connect");
            } else if(conn.getState() == Connection.STATE_ACTIVE) {
                this.callbackContext.error("Your call is already connected");
            } else {
                conn.setActive();
                Intent intent = new Intent(this.cordova.getActivity().getApplicationContext(), this.cordova.getActivity().getClass());
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);
                this.cordova.getActivity().getApplicationContext().startActivity(intent);
                this.callbackContext.success("Call connected successfully");
            }
            return true;
        } else if (action.equals("endCall")) {
            Connection conn = MyConnectionService.getConnection();
            if(conn == null) {
                this.callbackContext.error("No call exists for you to end");
            } else {
                DisconnectCause cause = new DisconnectCause(DisconnectCause.LOCAL);
                conn.setDisconnected(cause);
                conn.destroy();
                MyConnectionService.deinitConnection();
                ArrayList<CallbackContext> callbackContexts = CordovaCall.getCallbackContexts().get("hangup");
                for (final CallbackContext cbContext : callbackContexts) {
                    cordova.getThreadPool().execute(new Runnable() {
                        public void run() {
                            PluginResult result = new PluginResult(PluginResult.Status.OK, "hangup event called successfully");
                            result.setKeepCallback(true);
                            cbContext.sendPluginResult(result);
                        }
                    });
                }
                this.callbackContext.success("Call ended successfully");
            }
            return true;
        } else if (action.equals("registerEvent")) {
            String eventType = args.getString(0);
            ArrayList<CallbackContext> callbackContextList = callbackContextMap.get(eventType);
            Log.d(TAG, "registerEvent: eventType=" + eventType + ", callbackContextList=" + callbackContextList);
            callbackContextList.add(this.callbackContext);
            return true;
        } else if (action.equals("setAppName")) {
            String appName = args.getString(0);
            handle = new PhoneAccountHandle(new ComponentName(this.cordova.getActivity().getApplicationContext(),MyConnectionService.class),appName);
            if(android.os.Build.VERSION.SDK_INT >= 26) {
              phoneAccount = new PhoneAccount.Builder(handle, appName)
                  .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
                  .build();
              tm.registerPhoneAccount(phoneAccount);
            }
            if(android.os.Build.VERSION.SDK_INT >= 23) {
              phoneAccount = new PhoneAccount.Builder(handle, appName)
                   .setCapabilities(PhoneAccount.CAPABILITY_CALL_SUBJECT)
                   .build();
              tm.registerPhoneAccount(phoneAccount);
            }
            this.callbackContext.success("App Name Changed Successfully");
            return true;
        } else if (action.equals("setIcon")) {
            String iconName = args.getString(0);
            int iconId = this.cordova.getActivity().getApplicationContext().getResources().getIdentifier(iconName, "drawable", this.cordova.getActivity().getPackageName());
            if(iconId != 0) {
                icon = Icon.createWithResource(this.cordova.getActivity(), iconId);
                this.callbackContext.success("Icon Changed Successfully");
            } else {
                this.callbackContext.error("This icon does not exist. Make sure to add it to the res/drawable folder the right way.");
            }
            return true;
        } else if (action.equals("mute")) {
            this.mute();
            this.callbackContext.success("Muted Successfully");
            return true;
        } else if (action.equals("unmute")) {
            this.unmute();
            this.callbackContext.success("Unmuted Successfully");
            return true;
        } else if (action.equals("speakerOn")) {
            this.speakerOn();
            this.callbackContext.success("Speakerphone is on");
            return true;
        } else if (action.equals("speakerOff")) {
            this.speakerOff();
            this.callbackContext.success("Speakerphone is off");
            return true;
        } else if (action.equals("callNumber")) {
            realCallTo = args.getString(0);
            if(realCallTo != null) {
              cordova.getThreadPool().execute(new Runnable() {
                  public void run() {
                      callNumberPhonePermission();
                  }
              });
              this.callbackContext.success("Call Successful");
            } else {
              this.callbackContext.error("Call Failed. You need to enter a phone number.");
            }
            return true;
        }
        return false;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void checkCallPermission() {
        if(permissionCounter >= 1) {
            PhoneAccount currentPhoneAccount = tm.getPhoneAccount(handle);
            if(currentPhoneAccount.isEnabled()) {
                if(pendingAction == "receiveCall") {
                    this.receiveCall();
                } else if(pendingAction == "sendCall") {
                    this.sendCall();
                }
            } else {
                if(permissionCounter == 2) {
                    Intent phoneIntent = new Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS);
                    phoneIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    this.cordova.getActivity().getApplicationContext().startActivity(phoneIntent);
                } else {
                    this.callbackContext.error("You need to accept phone account permissions in order to send and receive calls");
                }
            }
        }
        permissionCounter--;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void receiveCall() {
        Bundle callInfo = new Bundle();
        callInfo.putString("from", from);
        callInfo.putString("fromId", fromId);
        //callInfo.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle); // https://stackoverflow.com/questions/53868446/using-telecommanager-with-our-custom-protocol
        //callInfo.putBoolean(PhoneAccount.EXTRA_LOG_SELF_MANAGED_CALLS, false); // 2020-08-03 EXTRA_LOG_SELF_MANAGED_CALLS=false
        tm.addNewIncomingCall(handle, callInfo);
        permissionCounter = 0;
        this.callbackContext.success("Incoming call successful");
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void sendCall() {
        Uri uri = Uri.fromParts("tel", to, null);
        Bundle callInfoBundle = new Bundle();
        callInfoBundle.putString("to",to);
        Bundle callInfo = new Bundle();
        callInfo.putParcelable(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS,callInfoBundle);
        callInfo.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle);
        callInfo.putBoolean(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, true);
        tm.placeCall(uri, callInfo);
        permissionCounter = 0;
        this.callbackContext.success("Outgoing call successful");
    }

    private void mute() {
        AudioManager audioManager = (AudioManager) this.cordova.getActivity().getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        audioManager.setMicrophoneMute(true);
    }

    private void unmute() {
        AudioManager audioManager = (AudioManager) this.cordova.getActivity().getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        audioManager.setMicrophoneMute(false);
    }

    private void speakerOn() {
        AudioManager audioManager = (AudioManager) this.cordova.getActivity().getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        audioManager.setSpeakerphoneOn(true);
    }

    private void speakerOff() {
        AudioManager audioManager = (AudioManager) this.cordova.getActivity().getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        audioManager.setSpeakerphoneOn(false);
    }

    public static String getApplicationName(Context context) {
      ApplicationInfo applicationInfo = context.getApplicationInfo();
      int stringId = applicationInfo.labelRes;
      return stringId == 0 ? applicationInfo.nonLocalizedLabel.toString() : context.getString(stringId);
    }

    protected void getCallPhonePermission() {
        cordova.requestPermission(this, CALL_PHONE_REQ_CODE, Manifest.permission.CALL_PHONE);
    }

    protected void callNumberPhonePermission() {
        cordova.requestPermission(this, REAL_PHONE_CALL, Manifest.permission.CALL_PHONE);
    }

    private void callNumber() {
        try {
          Intent intent = new Intent(Intent.ACTION_CALL, Uri.fromParts("tel", realCallTo, null));
          this.cordova.getActivity().getApplicationContext().startActivity(intent);
        } catch(Exception e) {
          this.callbackContext.error("Call Failed");
        }
        this.callbackContext.success("Call Successful");
    }

    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException
    {
        for(int r:grantResults)
        {
            if(r == PackageManager.PERMISSION_DENIED)
            {
                this.callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, "CALL_PHONE Permission Denied"));
                return;
            }
        }
        switch(requestCode)
        {
            case CALL_PHONE_REQ_CODE:
                this.sendCall();
                break;
            case REAL_PHONE_CALL:
                this.callNumber();
                break;
        }
    }
}
