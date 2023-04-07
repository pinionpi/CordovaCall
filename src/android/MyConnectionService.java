package com.dmarc.cordovacall;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.PluginResult;

import android.app.Activity;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.drawable.Icon;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.CallLog;
import android.provider.ContactsContract;
import androidx.annotation.RequiresApi;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.StatusHints;
import android.telecom.TelecomManager;
import android.os.Handler;
import android.net.Uri;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;

import android.util.Log;
import android.widget.Toast;

@RequiresApi(api = Build.VERSION_CODES.M)
public class MyConnectionService extends ConnectionService {

    private static String TAG = "MyConnectionService";
    private static Connection conn;

    public static Connection getConnection() {
        return conn;
    }

    public static void deinitConnection() {
        conn = null;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public Connection onCreateIncomingConnection(final PhoneAccountHandle connectionManagerPhoneAccount, final ConnectionRequest request) {
        final Connection connection = new Connection() {
            @Override
            public void onAnswer() {
                Log.i(TAG, "onCreateIncomingConnection/onAnswer");

                // 2020-06-24 Fix InCall Microphone got disable on Samsung S8+ Android 9 (Pie) => Add CAPABILITY_MUTE
                // 2020-08-04 Remove CAPABILITY_HOLD
                //setConnectionCapabilities(getConnectionCapabilities() | Connection.CAPABILITY_HOLD | Connection.CAPABILITY_MUTE);
                setConnectionCapabilities(getConnectionCapabilities() | Connection.CAPABILITY_MUTE);
                setAudioModeIsVoip(true);
                Log.d(TAG, "onCreateIncomingConnection/onAnswer: cap is " + Connection.capabilitiesToString(getConnectionCapabilities())); // [Capabilities: CAPABILITY_HOLD CAPABILITY_MUTE]

                this.setActive();

                // 2020-07-15 Fix stuck native call session
                CordovaCall.getCordova().getThreadPool().execute(new Runnable() {
                    public void run() {
                        try {
                            Thread.sleep(500);
                            onAnswerOpenMainActivityThenCallack();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }

            // 2020-07-15 Fix stuck native call session
            public void onAnswerOpenMainActivityThenCallack() {

                Context context = CordovaCall.getCordova().getActivity().getApplicationContext();
                CordovaInterface cdv = CordovaCall.getCordova();

                /*
                // 2021-03-25 Android Bluetooth for answer incoming call => Not working
                AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                if (audioManager.isBluetoothScoAvailableOffCall()) {
                    Log.i(TAG, "onCreateIncomingConnection/onAnswer/isBluetoothScoAvailableOffCall=true");
                    if (audioManager.isBluetoothScoOn()) {
                        audioManager.stopBluetoothSco();
                        audioManager.setBluetoothScoOn(false);
                    }
                    {
                        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                        audioManager.startBluetoothSco();
                        audioManager.setSpeakerphoneOn(false);
                        audioManager.setBluetoothScoOn(true); // BluetoothScoOn
                    }
                } else {
                    Log.i(TAG, "onCreateIncomingConnection/onAnswer/isBluetoothScoAvailableOffCall=false");
                }
                 */

                Activity main = cdv.getActivity();
                ExecutorService exec = cdv.getThreadPool();
                Log.i(TAG, "onCreateIncomingConnection/onAnswer/delay: Open Activity Name is " + main.getClass().getName());

                Log.i(TAG, "onCreateIncomingConnection/onAnswer: Intent BROUGHT_TO_FRONT with existing AndroidManifest.xml singleTop");
                Intent intent = new Intent(context, main.getClass());
                //intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);
                intent.addFlags(
                        //Intent.FLAG_ACTIVITY_NO_USER_ACTION |
                        Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT |
                        //Intent.FLAG_ACTIVITY_REORDER_TO_FRONT |
                        Intent.FLAG_ACTIVITY_SINGLE_TOP |
                        Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(intent);

                /* OLD
                Intent intent = new Intent(CordovaCall.getCordova().getActivity().getApplicationContext(), CordovaCall.getCordova().getActivity().getClass());
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);
                CordovaCall.getCordova().getActivity().getApplicationContext().startActivity(intent);
                 */

                ArrayList<CallbackContext> callbackContexts = CordovaCall.getCallbackContexts().get("answer");
                for (final CallbackContext callbackContext : callbackContexts) {
                    exec.execute(new Runnable() {
                        public void run() {
                            PluginResult result = new PluginResult(PluginResult.Status.OK, "answer event called successfully");
                            result.setKeepCallback(true);
                            callbackContext.sendPluginResult(result);
                        }
                    });
                }
            }

            @Override
            public void onReject() {
                DisconnectCause cause = new DisconnectCause(DisconnectCause.REJECTED);
                this.setDisconnected(cause);
                this.destroy();
                conn = null;
                ArrayList<CallbackContext> callbackContexts = CordovaCall.getCallbackContexts().get("reject");
                for (final CallbackContext callbackContext : callbackContexts) {
                    CordovaCall.getCordova().getThreadPool().execute(new Runnable() {
                        public void run() {
                            PluginResult result = new PluginResult(PluginResult.Status.OK, "reject event called successfully");
                            result.setKeepCallback(true);
                            callbackContext.sendPluginResult(result);
                        }
                    });
                }
            }

            @Override
            public void onAbort() {
                super.onAbort();
            }

            @Override
            public void onDisconnect() {
                DisconnectCause cause = new DisconnectCause(DisconnectCause.LOCAL);
                this.setDisconnected(cause);
                this.destroy();
                conn = null;
                ArrayList<CallbackContext> callbackContexts = CordovaCall.getCallbackContexts().get("hangup");
                for (final CallbackContext callbackContext : callbackContexts) {
                    CordovaCall.getCordova().getThreadPool().execute(new Runnable() {
                        public void run() {
                            PluginResult result = new PluginResult(PluginResult.Status.OK, "hangup event called successfully");
                            result.setKeepCallback(true);
                            callbackContext.sendPluginResult(result);
                        }
                    });
                }
            }
        };
        Bundle extras = request.getExtras();

        /*
         */
        // https://developer.android.com/guide/topics/connectivity/telecom/selfManaged
        // PROPERTY_SELF_MANAGED (26+)
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            connection.setConnectionProperties(Connection.PROPERTY_SELF_MANAGED);
            //connection.setConnectionProperties(connection.getConnectionProperties() | Connection.PROPERTY_SELF_MANAGED);
        }

        /*
        extras.putBoolean(PhoneAccount.EXTRA_LOG_SELF_MANAGED_CALLS, false);
        connection.setInitializing();
        connection.setExtras(extras);
        */

        // Need Contact :: https://stackoverflow.com/questions/46390916/connectionservice-telecommanager-not-showing-up-in-call-history-correctly
        String from = extras.getString("from");
        String fromId = extras.getString("fromId");
        Log.i(TAG, "onCreateIncomingConnection: from " + fromId + " :: " + from);

        // https://github.com/twilio/voice-quickstart-android/pull/140/files/b078374753e02b092748c92cf1cdb4bb5e83efca
        //connection.setAddress(request.getAddress(), TelecomManager.PRESENTATION_ALLOWED);
        connection.setExtras(request.getExtras());

        //connection.setAddress(Uri.parse(from), TelecomManager.PRESENTATION_ALLOWED); // CallLog "Unknown" or some integer
        if (fromId != null && fromId.length() != 0) {
            connection.setAddress(Uri.fromParts("application", "@Work", fromId), TelecomManager.PRESENTATION_ALLOWED); // CallLog shows atwork@eunite.com
            //connection.setAddress(Uri.fromParts("uden", fromId, null), TelecomManager.PRESENTATION_ALLOWED); // CallLog shows atwork@eunite.com
        } else if (from != null && from.length() != 0) {
            connection.setAddress(Uri.fromParts("mailto", "@Work", null), TelecomManager.PRESENTATION_ALLOWED); // CallLog shows atwork@eunite.com
        } else {
            //connection.setAddress(Uri.fromParts("mailto", "atwork@eunite.com", null), TelecomManager.PRESENTATION_ALLOWED); // CallLog shows atwork@eunite.com
            connection.setAddress(request.getAddress(), TelecomManager.PRESENTATION_ALLOWED);
        }
        connection.setCallerDisplayName(from, TelecomManager.PRESENTATION_ALLOWED);

        // Uncomment to test calls contact select/create
        ///testContactSelect();
        //testContactCreate();

        Icon icon = CordovaCall.getIcon();
        if(icon != null) {
            StatusHints statusHints = new StatusHints((CharSequence)"", icon, new Bundle());
            connection.setStatusHints(statusHints);
        }
        conn = connection;
        ArrayList<CallbackContext> callbackContexts = CordovaCall.getCallbackContexts().get("receiveCall");
        for (final CallbackContext callbackContext : callbackContexts) {
            CordovaCall.getCordova().getThreadPool().execute(new Runnable() {
                public void run() {
                    PluginResult result = new PluginResult(PluginResult.Status.OK, "receiveCall event called successfully");
                    result.setKeepCallback(true);
                    callbackContext.sendPluginResult(result);
                }
            });
        }
        return connection;
    }

    // ContentResolver
    // ContentResolver resolver = getApplicationContext().getContentResolver();
    // https://stackoverflow.com/questions/6587674/android-contacts-display-name-and-phone-numbers-in-single-database-query
    // EMAIL
    // https://stackoverflow.com/questions/24652876/how-to-receive-local-contacts-with-emai-addresses-using-the-content-resolver
    private void testContactSelect() {
        Log.i(TAG, "ContentResolver TEST");
        /*
        Log.i(TAG, "Profile._ID=" + ContactsContract.Profile._ID); // "_id"
        Log.i(TAG, "Profile.DISPLAY_NAME_PRIMARY=" + ContactsContract.Profile.DISPLAY_NAME_PRIMARY); // display_name
        Log.i(TAG, "Profile.LOOKUP_KEY=" + ContactsContract.Profile.LOOKUP_KEY); // lookup
        Log.i(TAG, "Profile.PHOTO_THUMBNAIL_URI=" + ContactsContract.Profile.PHOTO_THUMBNAIL_URI); // photo_thumb_uri

        Log.i(TAG, "Phone._ID=" + ContactsContract.CommonDataKinds.Phone._ID); // "_id"
        Log.i(TAG, "Phone.CONTACT_ID=" + ContactsContract.CommonDataKinds.Phone.CONTACT_ID); // contact_id
        Log.i(TAG, "Phone.DISPLAY_NAME=" + ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME); // display_name
        Log.i(TAG, "Phone.NUMBER=" + ContactsContract.CommonDataKinds.Phone.NUMBER); // data1
        Log.i(TAG, "Email.ADDRESS=" + ContactsContract.CommonDataKinds.Email.ADDRESS); // data1
        */

        // TODO ContactsContract.Contacts.CONTENT_URI for all?
        Uri uri = ContactsContract.Contacts.CONTENT_URI;
        String[] projection = null;
        String selection = null;
        String[] selectionArgs = new String[0];
        String order = null;

        /*
        // https://developer.android.com/guide/topics/providers/contacts-provider
        projection = new String[] {
            ContactsContract.Profile._ID,
            ContactsContract.Profile.DISPLAY_NAME_PRIMARY,
            ContactsContract.Profile.LOOKUP_KEY,
            ContactsContract.Profile.PHOTO_THUMBNAIL_URI
            };
        */

        // PHONE or EMAIL
        String type = "EMAIL";
        switch (type) {
            /*
            case "ID":
            {
                selection = ContactsContract.CommonDataKinds.Phone.CONTACT_ID+ " = ?";
                selectionArgs = new String[] { id };
            }
            */
            case "EMAIL":
            {
                uri = ContactsContract.CommonDataKinds.Email.CONTENT_URI;
                projection = new String[] {
                        ContactsContract.Contacts.Entity.RAW_CONTACT_ID,
                        ContactsContract.Contacts._ID,
                        ContactsContract.Contacts.PHOTO_ID,
                        ContactsContract.Contacts.DISPLAY_NAME,
                        //ContactsContract.CommonDataKinds.Email.TIMES_CONTACTED,
                        ContactsContract.CommonDataKinds.Email.DATA};
                selection = ContactsContract.CommonDataKinds.Email.DATA + " != ?";
                selectionArgs = new String[]{""};
                // SORT_KEY_PRIMARY :: https://developer.android.com/reference/android/provider/ContactsContract.ContactNameColumns.html#SORT_KEY_PRIMARY
                //order = ContactsContract.Contacts.DISPLAY_NAME + " ASC";
                // RAW_CONTACT_ID :: https://developer.android.com/guide/topics/providers/contacts-provider
                order = ContactsContract.Contacts.Entity.RAW_CONTACT_ID + " ASC";
                //order = ContactsContract.CommonDataKinds.Email.TIMES_CONTACTED + " DESC";
                break;
            }
            case "PHONE":
            default:
            {
                uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
                projection = new String[] {
                        ContactsContract.Contacts._ID,
                        ContactsContract.Contacts.PHOTO_ID,
                        ContactsContract.Contacts.DISPLAY_NAME,
                        //ContactsContract.CommonDataKinds.Phone.TIMES_CONTACTED,
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                        ContactsContract.CommonDataKinds.Phone.TYPE
                };
                selection = ContactsContract.CommonDataKinds.Phone.TYPE + " = ?";
                selectionArgs = new String[]{String.valueOf(ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)}; // TYPE_MOBILE, TYPE_WORK, etc.
                order = ContactsContract.Contacts.DISPLAY_NAME + " ASC";
                //order = ContactsContract.CommonDataKinds.Phone.TIMES_CONTACTED + " DESC";
                break;
            }
        }

        Cursor cursor = getContentResolver().query(uri, projection, selection, selectionArgs, order);

        int index_ID = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone._ID);
        int index_RAW_CONTACT_ID = cursor.getColumnIndex(ContactsContract.Contacts.Entity.RAW_CONTACT_ID);
        //int indexContactId = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID);

        int indexDisplayName = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME);
        int indexPhotoId = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_ID);

        //int indexPhoneNumber = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
        //int indexEmail = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS);
        int indexEmail = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.DATA);

        if(cursor.moveToFirst()) {
            do {
                long id   = cursor.getLong(index_ID);
                long rawContactId = cursor.getLong(index_RAW_CONTACT_ID);
                //String contactId   = cursor.getString(indexContactId);
                String displayName   = cursor.getString(indexDisplayName);
                //String photoId = cursor.getString(indexPhotoId);
                //String phoneNumber = cursor.getString(indexPhoneNumber);
                String email = cursor.getString(indexEmail);
                // Do work...
                //Log.i(TAG, "contact: " + id + " :: " + displayName);
                //Log.i(TAG, "contact: " + id + " :: " + contactId + " :: " + displayName + " :: " + phoneNumber + " :: " + emailAddress);
                //Log.i(TAG, "contact: " + id + " :: " + rawContactId + " :: " + displayName + " :: " + photoId + " :: " + email);
                Log.i(TAG, "contact: " + id + " :: " + rawContactId + " :: " + displayName + " :: " + email);
            } while (cursor.moveToNext());
        }
    }

    // See applyBatch of ContentProviderOperation
    // https://stackoverflow.com/questions/4744187/how-to-add-new-contacts-in-android
    // https://developer.android.com/guide/topics/providers/contacts-provider#RawContactsExample
    private void testContactCreate() {
        /*
        String DisplayName = "XYZ";
        String MobileNumber = "123456";
        String HomeNumber = "1111";
        String WorkNumber = "2222";
        String emailID = "email@nomail.com";
        String company = "bad";
        String jobTitle = "abcd";
         */
        String DisplayName = "@Work";
        String MobileNumber = "";
        String HomeNumber = "";
        String WorkNumber = "";
        String emailID = "atwork@eunite.com";
        String company = "";
        String jobTitle = "";
        Log.i(TAG, "ContactCreate INIT: " + DisplayName);

        ArrayList < ContentProviderOperation > ops = new ArrayList < ContentProviderOperation > ();

        ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .build());

        //------------------------------------------------------ Names
        if (DisplayName != null) {
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(
                            ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
                            DisplayName).build());
        }

        //------------------------------------------------------ Mobile Number
        if (MobileNumber != null) {
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, MobileNumber)
                    .withValue(ContactsContract.CommonDataKinds.Phone.TYPE,
                            ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                    .build());
        }

        //------------------------------------------------------ Home Numbers
        if (HomeNumber != null) {
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, HomeNumber)
                    .withValue(ContactsContract.CommonDataKinds.Phone.TYPE,
                            ContactsContract.CommonDataKinds.Phone.TYPE_HOME)
                    .build());
        }

        //------------------------------------------------------ Work Numbers
        if (WorkNumber != null) {
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, WorkNumber)
                    .withValue(ContactsContract.CommonDataKinds.Phone.TYPE,
                            ContactsContract.CommonDataKinds.Phone.TYPE_WORK)
                    .build());
        }

        //------------------------------------------------------ Email
        if (emailID != null) {
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Email.DATA, emailID)
                    .withValue(ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.TYPE_WORK)
                    .build());
        }

        //------------------------------------------------------ Organization
        if (!company.equals("") && !jobTitle.equals("")) {
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, company)
                    .withValue(ContactsContract.CommonDataKinds.Organization.TYPE, ContactsContract.CommonDataKinds.Organization.TYPE_WORK)
                    .withValue(ContactsContract.CommonDataKinds.Organization.TITLE, jobTitle)
                    .withValue(ContactsContract.CommonDataKinds.Organization.TYPE, ContactsContract.CommonDataKinds.Organization.TYPE_WORK)
                    .build());
        }

        // Asking the Contact provider to create a new contact
        try {
            getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
            Log.i(TAG, "ContactCreate DONE");
        } catch (Exception ex) {
            Log.i(TAG, "ContactCreate FAIL", ex);
            Toast.makeText(getApplicationContext(), "Exception: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public Connection onCreateOutgoingConnection(PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {
        final Connection connection = new Connection() {
            @Override
            public void onAnswer() {
                super.onAnswer();
            }

            @Override
            public void onReject() {
                super.onReject();
            }

            @Override
            public void onAbort() {
                super.onAbort();
            }

            @Override
            public void onDisconnect() {
                DisconnectCause cause = new DisconnectCause(DisconnectCause.LOCAL);
                this.setDisconnected(cause);
                this.destroy();
                conn = null;
                ArrayList<CallbackContext> callbackContexts = CordovaCall.getCallbackContexts().get("hangup");
                for (final CallbackContext callbackContext : callbackContexts) {
                    CordovaCall.getCordova().getThreadPool().execute(new Runnable() {
                        public void run() {
                            PluginResult result = new PluginResult(PluginResult.Status.OK, "hangup event called successfully");
                            result.setKeepCallback(true);
                            callbackContext.sendPluginResult(result);
                        }
                    });
                }
            }

            @Override
            public void onStateChanged(int state) {
              if(state == Connection.STATE_DIALING) {
                final Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Intent intent = new Intent(CordovaCall.getCordova().getActivity().getApplicationContext(), CordovaCall.getCordova().getActivity().getClass());
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        CordovaCall.getCordova().getActivity().getApplicationContext().startActivity(intent);
                    }
                }, 500);
              }
            }
        };
        connection.setAddress(Uri.parse(request.getExtras().getString("to")), TelecomManager.PRESENTATION_ALLOWED);
        Icon icon = CordovaCall.getIcon();
        if(icon != null) {
            StatusHints statusHints = new StatusHints((CharSequence)"", icon, new Bundle());
            connection.setStatusHints(statusHints);
        }
        connection.setDialing();
        conn = connection;
        ArrayList<CallbackContext> callbackContexts = CordovaCall.getCallbackContexts().get("sendCall");
        if(callbackContexts != null) {
            for (final CallbackContext callbackContext : callbackContexts) {
                CordovaCall.getCordova().getThreadPool().execute(new Runnable() {
                    public void run() {
                        PluginResult result = new PluginResult(PluginResult.Status.OK, "sendCall event called successfully");
                        result.setKeepCallback(true);
                        callbackContext.sendPluginResult(result);
                    }
                });
            }
        }
        return connection;
    }
}
