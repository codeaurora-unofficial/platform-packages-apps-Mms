/*
 * Copyright (C) 2008 Esmertec AG.
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.mms.ui;

import com.android.mms.data.Contact;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.AsyncQueryHandler;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SqliteWrapper;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.Telephony.Sms;
import android.telephony.MSimSmsManager;
import android.telephony.MSimTelephonyManager;
import android.telephony.SmsManager;
import android.text.TextUtils;
import android.text.SpannableString;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import com.android.mms.R;
import com.android.mms.transaction.MessagingNotification;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.MSimConstants;
import com.android.internal.telephony.TelephonyIntents;

import java.util.ArrayList;

/**
 * Displays a list of the SMS messages stored on the ICC.
 */
public class ManageSimMessages extends Activity
        implements View.OnCreateContextMenuListener {
    private static final Uri ICC_URI = Uri.parse("content://sms/icc");
    private static final Uri ICC1_URI = Uri.parse("content://sms/icc1");
    private static final Uri ICC2_URI = Uri.parse("content://sms/icc2");
    private static final String TAG = "ManageSimMessages";
    private static final int MENU_COPY_TO_PHONE_MEMORY = 0;
    private static final int MENU_DELETE_FROM_SIM = 1;
    private static final int MENU_VIEW = 2;
    private static final int MENU_REPLY = 3;
    private static final int MENU_FORWARD = 4;
    private static final int MENU_CALL_BACK = 5;
    private static final int MENU_ADD_ADDRESS_TO_CONTACTS = 6;
    private static final int MENU_SEND_EMAIL = 7;

    private static final int OPTION_MENU_DELETE_ALL = 0;
    private static final int OPTION_MENU_SIM_CAPACITY = 1;

    private static final int SUB_INVALID = -1;

    private static final int SHOW_LIST = 0;
    private static final int SHOW_EMPTY = 1;
    private static final int SHOW_BUSY = 2;
    private int mState;
    private int mSubscription;

    private Uri mIccUri;
    private ContentResolver mContentResolver;
    private Cursor mCursor = null;
    private ListView mSimList;
    private TextView mMessage;
    private MessageListAdapter mListAdapter = null;
    private AsyncQueryHandler mQueryHandler = null;
    private boolean mIsDeleteAll = false;
    private boolean mIsQuery = false;

    public static final int SIM_FULL_NOTIFICATION_ID = 234;

    // The flag of contacts need update again.
    private boolean mIsNeedUpdateContacts = false;

    private final ContentObserver simChangeObserver =
            new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfUpdate) {
            refreshMessageList();
        }
    };

    // Define this ContentObserver for update the ListView
    // when Contacts information be changed
    private final ContentObserver mContactsChangedObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfUpdate) {
            mIsNeedUpdateContacts = updateContacts();
        }
    };

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
                //can't view sim card message when airplane mode is on
                if (intent.getBooleanExtra("state", false)) {
                    closeContextMenu();
                    updateState(SHOW_EMPTY);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mSubscription = getIntent().getIntExtra(MSimConstants.SUBSCRIPTION_KEY, SUB_INVALID);
        mIccUri = getIccUriBySubscription(mSubscription);

        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        mContentResolver = getContentResolver();
        mQueryHandler = new QueryHandler(mContentResolver, this);
        setContentView(R.layout.sim_list);
        mSimList = (ListView) findViewById(R.id.messages);
        mMessage = (TextView) findViewById(R.id.empty_message);

        ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);

        init();
        registerSimChangeObserver();
        registerSimChangedReceiver();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        registerReceiver(mBroadcastReceiver, filter);

    }

    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);

        init();
    }

    private void init() {
        MessagingNotification.cancelNotification(getApplicationContext(),
                SIM_FULL_NOTIFICATION_ID);

        updateState(SHOW_BUSY);
        if (MessageUtils.sIsIccLoaded) {
            startQuery();
        }
    }

    /**
     * A wrapper of a broadcast receiver which provides network connectivity information
     * for all kinds of network: wifi, mobile, etc.
     */
    private final BroadcastReceiver mIccStateChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(action)) {
                int subscription = intent.getIntExtra(MessageUtils.SUB_KEY, -1);
                String stateExtra = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
                Log.d(TAG, "mIccStateChangedReceiver: Handling incoming intent = "
                    + intent + ", stateExtra = " + stateExtra);

                if (!MessageUtils.isMultiSimEnabledMms()) {
                    subscription = MessageUtils.SUB_INVALID;
                }

                if (IccCardConstants.INTENT_VALUE_ICC_LOADED.equals(stateExtra)
                        && subscription == mSubscription) {
                    startQuery();
                }
            } else if (MessageUtils.ACTION_SIM_STATE_CHANGED0.equals(action)) {
                int subscription = intent.getIntExtra(MessageUtils.SUB_KEY, -1);
                String stateExtra = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
                Log.d(TAG, "mIccStateChangedReceiver: Handling incoming intent = "
                    + intent + ", stateExtra = " + stateExtra);

                if (IccCardConstants.INTENT_VALUE_ICC_LOADED.equals(stateExtra)
                        && subscription == mSubscription) {
                    startQuery();
                }
            } else if (MessageUtils.ACTION_SIM_STATE_CHANGED1.equals(action)) {
                int subscription = intent.getIntExtra(MessageUtils.SUB_KEY, -1);
                String stateExtra = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
                Log.d(TAG, "mIccStateChangedReceiver: Handling incoming intent = "
                    + intent + ", stateExtra = " + stateExtra);

                if (IccCardConstants.INTENT_VALUE_ICC_LOADED.equals(stateExtra)
                        && subscription == mSubscription) {
                    startQuery();
                }
            }
        }
    };

    private Uri getIccUriBySubscription(int subscription) {
        switch (subscription) {
            case MSimConstants.SUB1:
                return ICC1_URI;
            case MSimConstants.SUB2:
                return ICC2_URI;
            default:
                return ICC_URI;
        }
    }

    private class QueryHandler extends AsyncQueryHandler {
        private final ManageSimMessages mParent;

        public QueryHandler(
                ContentResolver contentResolver, ManageSimMessages parent) {
            super(contentResolver);
            mParent = parent;
        }

        @Override
        protected void onQueryComplete(
                int token, Object cookie, Cursor cursor) {
            mCursor = cursor;
            if (mCursor != null) {
                if (!mCursor.moveToFirst()) {
                    // Let user know the SIM is empty
                    updateState(SHOW_EMPTY);
                } else if (mListAdapter == null) {
                    // Note that the MessageListAdapter doesn't support auto-requeries. If we
                    // want to respond to changes we'd need to add a line like:
                    //   mListAdapter.setOnDataSetChangedListener(mDataSetChangedListener);
                    // See ComposeMessageActivity for an example.
                    mListAdapter = new MessageListAdapter(
                            mParent, mCursor, mSimList, false, null);
                    mSimList.setAdapter(mListAdapter);
                    mSimList.setOnCreateContextMenuListener(mParent);
                    updateState(SHOW_LIST);
                } else {
                    mListAdapter.changeCursor(mCursor);
                    updateState(SHOW_LIST);
                }
            } else {
                // Let user know the SIM is empty
                updateState(SHOW_EMPTY);
            }
            // Show option menu when query complete.
            invalidateOptionsMenu();
            mIsQuery = false;
        }
    }

    private void startQuery() {
        try {
            if (mIsQuery) {
                return;
            }
            mIsQuery = true;
            mQueryHandler.startQuery(0, null, mIccUri, null, null, null, null);
        } catch (SQLiteException e) {
            SqliteWrapper.checkSQLiteException(this, e);
        }
    }

    private void refreshMessageList() {
        updateState(SHOW_BUSY);
        if (mCursor != null) {
            mCursor.close();
        }
        startQuery();
    }

    @Override
    public void onCreateContextMenu(
            ContextMenu menu, View v,
            ContextMenu.ContextMenuInfo menuInfo) {
        menu.add(0, MENU_COPY_TO_PHONE_MEMORY, 0,
                 R.string.sim_copy_to_phone_memory);
        menu.add(0, MENU_DELETE_FROM_SIM, 0, R.string.sim_delete);

        Cursor cursor = mListAdapter.getCursor();
        if (isIncomingMessage(cursor)) {
            String address = cursor.getString(cursor.getColumnIndexOrThrow("address"));
            Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", address, null));
            menu.add(0, MENU_REPLY, 0, R.string.menu_reply).setIntent(intent);
        }
        menu.add(0, MENU_FORWARD, 0, R.string.menu_forward);
        addCallAndContactMenuItems(menu, cursor);

        // TODO: Enable this once viewMessage is written.
        // menu.add(0, MENU_VIEW, 0, R.string.sim_view);
    }

    // refs to ComposeMessageActivity.java
    private final void addCallAndContactMenuItems(
            ContextMenu menu, Cursor cursor) {
        String address = cursor.getString(cursor.getColumnIndexOrThrow("address"));
        String body = cursor.getString(cursor.getColumnIndexOrThrow("body"));
        // Add all possible links in the address & message
        StringBuilder textToSpannify = new StringBuilder();
        if (address != null) {
            textToSpannify.append(address + ": ");
        }
        textToSpannify.append(body);

        SpannableString msg = new SpannableString(textToSpannify.toString());
        Linkify.addLinks(msg, Linkify.ALL);
        ArrayList<String> uris =
            MessageUtils.extractUris(msg.getSpans(0, msg.length(), URLSpan.class));

        while (uris.size() > 0) {
            String uriString = uris.remove(0);
            // Remove any dupes so they don't get added to the menu multiple times
            while (uris.contains(uriString)) {
                uris.remove(uriString);
            }

            int sep = uriString.indexOf(":");
            String prefix = null;
            if (sep >= 0) {
                prefix = uriString.substring(0, sep);
                uriString = uriString.substring(sep + 1);
            }
            boolean addToContacts = false;
            if ("mailto".equalsIgnoreCase(prefix))  {
                String sendEmailString = getString(
                        R.string.menu_send_email).replace("%s", uriString);
                Intent intent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("mailto:" + uriString));
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
                menu.add(0, MENU_SEND_EMAIL, 0, sendEmailString)
                    .setIntent(intent);
                addToContacts = !haveEmailContact(uriString);
            } else if ("tel".equalsIgnoreCase(prefix)) {
                String callBackString = getString(
                        R.string.menu_call_back).replace("%s", uriString);
                Intent intent = new Intent(Intent.ACTION_CALL,
                        Uri.parse("tel:" + uriString));
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
                menu.add(0, MENU_CALL_BACK, 0, callBackString)
                    .setIntent(intent);
                addToContacts = !isNumberInContacts(uriString);
            }
            if (addToContacts) {
                Intent intent = ConversationList.createAddContactIntent(uriString);
                String addContactString = getString(
                        R.string.menu_add_address_to_contacts).replace("%s", uriString);
                menu.add(0, MENU_ADD_ADDRESS_TO_CONTACTS, 0, addContactString)
                    .setIntent(intent);
            }
        }
    }

    private boolean haveEmailContact(String emailAddress) {
        Cursor cursor = SqliteWrapper.query(this, getContentResolver(),
                Uri.withAppendedPath(Email.CONTENT_LOOKUP_URI, Uri.encode(emailAddress)),
                new String[] { Contacts.DISPLAY_NAME }, null, null, null);

        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    String name = cursor.getString(0);
                    if (!TextUtils.isEmpty(name)) {
                        return true;
                    }
                }
            } finally {
                cursor.close();
            }
        }
        return false;
    }

    private boolean isNumberInContacts(String phoneNumber) {
        return Contact.get(phoneNumber, false).existsInDatabase();
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info;
        try {
             info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        } catch (ClassCastException exception) {
            Log.e(TAG, "Bad menuInfo.", exception);
            return false;
        }

        final Cursor cursor = (Cursor) mListAdapter.getItem(info.position);
        String messageIndexString =
                cursor.getString(cursor.getColumnIndexOrThrow("index_on_icc"));
        final Uri uri = mIccUri.buildUpon().appendPath(messageIndexString).build();
        switch (item.getItemId()) {
            case MENU_COPY_TO_PHONE_MEMORY:
                copyToPhoneMemory(cursor);
                return true;
            case MENU_DELETE_FROM_SIM:
                confirmDeleteDialog(new OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        updateState(SHOW_BUSY);
                        deleteFromSim(uri);
                        dialog.dismiss();
                    }
                }, R.string.confirm_delete_SIM_message);
                return true;
            case MENU_FORWARD: {
                String smsBody = cursor.getString(cursor.getColumnIndexOrThrow("body"));
                forwardMessage(smsBody);
                return true;
            }
            case MENU_VIEW:
                viewMessage(cursor);
                return true;
        }
        return super.onContextItemSelected(item);
    }

    private void forwardMessage(String smsBody) {
        Intent intent = new Intent(this, ComposeMessageActivity.class);

        intent.putExtra("exit_on_sent", true);
        intent.putExtra("forwarded_message", true);

        intent.putExtra("sms_body", smsBody);
        intent.setClassName(this, "com.android.mms.ui.ForwardMessageActivity");
        startActivity(intent);
    }

    @Override
    protected void onStop() {
        super.onStop();
        MessagingNotification.setCurrentlyDisplayedCardList(false);

        if (mCursor != null && !mCursor.isClosed()) {
            mCursor.close();
        }
    }

    @Override
    public void onDestroy() {
        mContentResolver.unregisterContentObserver(simChangeObserver);
        mContentResolver.unregisterContentObserver(mContactsChangedObserver);
        unregisterReceiver(mIccStateChangedReceiver);
        unregisterReceiver(mBroadcastReceiver);

        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (mIsDeleteAll) {
            mIsDeleteAll = false;
        } else {
            super.onBackPressed();
        }
    }

    private void registerSimChangeObserver() {
        mContentResolver.registerContentObserver(
                mIccUri, true, simChangeObserver);
        mContentResolver.registerContentObserver(Contacts.CONTENT_URI, true,
                mContactsChangedObserver);
    }

    private void registerSimChangedReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        intentFilter.addAction(MessageUtils.ACTION_SIM_STATE_CHANGED0);
        intentFilter.addAction(MessageUtils.ACTION_SIM_STATE_CHANGED1);
        registerReceiver(mIccStateChangedReceiver, intentFilter);
    }

    private void copyToPhoneMemory(Cursor cursor) {
        String address = cursor.getString(
                cursor.getColumnIndexOrThrow("address"));
        String body = cursor.getString(cursor.getColumnIndexOrThrow("body"));
        Long date = cursor.getLong(cursor.getColumnIndexOrThrow("date"));
        int sub_id = cursor.getInt(cursor.getColumnIndexOrThrow("sub_id"));

        try {
            if (isIncomingMessage(cursor)) {
                Sms.Inbox.addMessage(mContentResolver, address, body, null, date,
                              true, sub_id);
            } else {
                Sms.Sent.addMessage(mContentResolver, address, body, null, date, sub_id);
            }
        } catch (SQLiteException e) {
            SqliteWrapper.checkSQLiteException(this, e);
        }
    }

    private boolean isIncomingMessage(Cursor cursor) {
        int messageStatus = cursor.getInt(
                cursor.getColumnIndexOrThrow("status"));

        return (messageStatus == SmsManager.STATUS_ON_ICC_READ) ||
               (messageStatus == SmsManager.STATUS_ON_ICC_UNREAD);
    }

    private void deleteFromSim(Uri uri) {
        try {
            mQueryHandler.startDelete(0, null, uri, null, null);
        } catch (SQLiteException e) {
            SqliteWrapper.checkSQLiteException(this, e);
        }
    }

    private void deleteFromSim(Cursor cursor) {
        String messageIndexString =
                cursor.getString(cursor.getColumnIndexOrThrow("index_on_icc"));
        Uri simUri = mIccUri.buildUpon().appendPath(messageIndexString).build();

        SqliteWrapper.delete(this, mContentResolver, simUri, null, null);
    }

    private void deleteAllFromSim() {
        mIsDeleteAll = true;
        Cursor cursor = (Cursor) mListAdapter.getCursor();

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                mContentResolver.unregisterContentObserver(simChangeObserver);
                int count = cursor.getCount();

                for (int i = 0; i < count; ++i) {
                    if (!mIsDeleteAll || cursor.isClosed()) {
                        break;
                    }
                    cursor.moveToPosition(i);
                    deleteFromSim(cursor);
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        refreshMessageList();
                        registerSimChangeObserver();
                    }
                });
            }
        }

        mIsDeleteAll = false;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.clear();

        if (mState == SHOW_LIST && (null != mCursor) && (mCursor.getCount() > 0)) {
            menu.add(0, OPTION_MENU_DELETE_ALL, 0, R.string.menu_delete_messages).setIcon(
                    android.R.drawable.ic_menu_delete);
        }
        menu.add(0, OPTION_MENU_SIM_CAPACITY, 0, R.string.sim_capacity_title);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case OPTION_MENU_DELETE_ALL:
                confirmDeleteDialog(new OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        updateState(SHOW_BUSY);
                        new Thread() {
                            @Override
                            public void run() {
                                deleteAllFromSim();
                            }
                        }.start();
                        dialog.dismiss();
                    }
                }, R.string.confirm_delete_all_SIM_messages);
                break;

            case OPTION_MENU_SIM_CAPACITY:
                showSimCapacityDialog();
                break;

            case android.R.id.home:
                // The user clicked on the Messaging icon in the action bar. Take them back from
                // wherever they came from
                finish();
                break;
        }

        return true;
    }

    private void confirmDeleteDialog(OnClickListener listener, int messageId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.confirm_dialog_title);
        builder.setIconAttribute(android.R.attr.alertDialogIcon);
        builder.setCancelable(true);
        builder.setPositiveButton(R.string.yes, listener);
        builder.setNegativeButton(R.string.no, null);
        builder.setMessage(messageId);

        builder.show();
    }

    private void showSimCapacityDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.sim_capacity_title);
        builder.setCancelable(true);
        builder.setPositiveButton(R.string.yes, null);
        StringBuilder capacityMessage = new StringBuilder();
        capacityMessage.append(getString(R.string.sim_capacity_used));
        capacityMessage.append(" " + mCursor.getCount() + "\n");
        capacityMessage.append(getString(R.string.sim_capacity_all));
        int iccCapacityAll = -1;
        if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
            iccCapacityAll = MSimSmsManager.getDefault().getSmsCapacityOnIcc(mSubscription);
        } else {
            iccCapacityAll = SmsManager.getDefault().getSmsCapacityOnIcc();
        }

        capacityMessage.append(" " + iccCapacityAll);
        builder.setMessage(capacityMessage);

        builder.show();
    }

    private void updateState(int state) {
        if (mState == state) {
            return;
        }

        mState = state;
        switch (state) {
            case SHOW_LIST:
                mSimList.setVisibility(View.VISIBLE);
                mMessage.setVisibility(View.GONE);
                setTitle(getString(R.string.sim_manage_messages_title));
                setProgressBarIndeterminateVisibility(false);
                mSimList.requestFocus();
                break;
            case SHOW_EMPTY:
                mSimList.setVisibility(View.GONE);
                mMessage.setVisibility(View.VISIBLE);
                setTitle(getString(R.string.sim_manage_messages_title));
                setProgressBarIndeterminateVisibility(false);
                break;
            case SHOW_BUSY:
                mSimList.setVisibility(View.GONE);
                mMessage.setVisibility(View.GONE);
                setTitle(getString(R.string.refreshing));
                setProgressBarIndeterminateVisibility(true);
                break;
            default:
                Log.e(TAG, "Invalid State");
        }
    }

    private void viewMessage(Cursor cursor) {
        // TODO: Add this.
    }

    private void setMessageRead(Context context) {
        Log.d(TAG, "setMessageRead : mSubscription = " + mSubscription);

        ContentValues values = new ContentValues(1);
        values.put("status_on_icc", MessageUtils.STATUS_ON_SIM_READ);
        SqliteWrapper.update(context, getContentResolver(),
            MessageUtils.getIccUriBySubscription(mSubscription),
            values, null, null);
    }

    /**
     * update contact icon, the method be used after add or delete a contact
     * from ManagerSimMessages
     */
    private boolean updateContacts() {

        int count = mSimList.getCount();
        int number = 0;

        for (int i = 0; i < count; i++) {
            MessageListItem item = (MessageListItem) mSimList.getChildAt(i);

            // if the item doesn't show at the interface, it will be null.
            if (item != null) {
                boolean isSelf = Sms.isOutgoingFolder(item.getMessageItem().mBoxId);
                String addr = isSelf ? null : item.getMessageItem().mAddress;
                item.updateAvatarView(this, addr, false);
            } else {
                number++;
            }
        }

        return number == count;
    }

    @Override
    protected void onStart() {
        super.onStart();
        MessagingNotification.setCurrentlyDisplayedCardList(true);
        MessagingNotification.cancelNotification(getApplicationContext(),
                SIM_FULL_NOTIFICATION_ID);
        MessagingNotification.cancelNotification(getApplicationContext(),
                MessagingNotification.getNotificationIDBySubscription(mSubscription));

        setMessageRead(this);

        // if updateContacts call before this method, it will doesn't work well,
        // need updateContacts again.
        if (mIsNeedUpdateContacts) {
            mIsNeedUpdateContacts = updateContacts();
        }
    }
}

