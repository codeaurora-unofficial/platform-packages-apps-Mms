/*
 * Copyright (c) 2014 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to
 * deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
 * sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */

package com.android.mms.rcs;

import com.android.mms.R;
import com.android.mms.data.ContactList;
import com.android.mms.data.Conversation;
import com.android.mms.data.WorkingMessage;
import com.android.mms.ui.ComposeMessageActivity;
import com.suntek.mway.rcs.client.aidl.provider.SuntekMessageData;
import com.suntek.mway.rcs.client.aidl.provider.model.ChatMessage;
import com.suntek.mway.rcs.client.api.util.ServiceDisconnectedException;
import com.suntek.mway.rcs.client.api.util.log.LogHelper;
import com.suntek.mway.rcs.client.aidl.contacts.RCSContact;
import com.suntek.mway.rcs.client.aidl.plugin.entity.profile.Profile;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.suntek.mway.rcs.client.aidl.provider.model.GroupChatModel;
import com.suntek.mway.rcs.client.aidl.provider.model.MessageSessionModel;
import com.suntek.mway.rcs.client.api.util.FileSuffixException;
import com.suntek.mway.rcs.client.api.util.FileTransferException;
import com.suntek.mway.rcs.client.api.im.impl.MessageApi;

import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;

import java.io.File;
import java.io.FileInputStream;

import com.suntek.mway.rcs.client.api.util.FileDurationException;

public class RcsChatMessageUtils {
    public static ChatMessage getChatMessageOnSMSDB(Context context, long id) {
        Uri uri = Uri.parse("content://sms/");
        Cursor cursor = context.getContentResolver()
                .query(uri, new String []{"rcs_id"}, "_id = ?", new String[] {
                        String.valueOf(id)
                }, null);
        ChatMessage msg = null;
        if (cursor != null && cursor.moveToFirst()) {
            if (!cursor.isAfterLast()) {
                String rcsId =cursor.getString(cursor.getColumnIndex("rcs_id"));
                try {
                    msg = RcsApiManager.getMessageApi().getMessageById(rcsId);
                } catch (Exception e) {
                    e.printStackTrace();
                }


            }
        }
        if (cursor != null) {
            cursor.close();
        }
        return msg;

    }


    public static ChatMessage getTestChatMessage() {
        ChatMessage msg = new ChatMessage();
        msg.setMsgType(SuntekMessageData.MSG_TYPE_AUDIO);
        msg.setData("BurnMessage");
        // msg.setMsgType(SuntekMessageData.MSG_TYPE_IMAGE);
        // msg.setFilename("a.jpg");
        return msg;
    }
    public static GeoLocation readMapXml(String filepath) {
        GeoLocation geo = null;
        try {
            GeoLocationParser handler = new GeoLocationParser(
                    new FileInputStream(new File(filepath)));
            geo = handler.getGeoLocation();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return geo;

    }
    public static boolean renameFile(String oldFilePath, String newFilePath) {
        if (TextUtils.isEmpty(oldFilePath) || TextUtils.isEmpty(newFilePath)) {
            return false;
        }
        File oldFile = new File(oldFilePath);
        File newFile = new File(newFilePath);
        return oldFile.renameTo(newFile);
    }
    public static String getFilePath(ChatMessage cMsg)
            throws ServiceDisconnectedException {
        String path = RcsApiManager.getMessageApi()
                .getFilepath(cMsg);
        if (path != null && new File(path).exists()) {
            return path;
        } else {
            if (path != null && path.lastIndexOf("/") != -1) {
                path = path.substring(0, path.lastIndexOf("/") + 1);
                return path + cMsg.getFilename();
            } else {
                return null;
            }
        }

    }

    public static String[] toStringArray(List<String> strList) {
        String[] array = new String[strList.size()];
        strList.toArray(array);
        return array;
    }

    public static boolean isFileDownload(String filePath, long fileSize) {
        if (TextUtils.isEmpty(filePath)) {
            return false;
        }
        if (fileSize == 0)
            return false;
        boolean isDownload = false;
        File file = new File(filePath);
        if (file != null) {
            LogHelper.trace("filePath = " + filePath + " ; thisFileSize = "
                    + file.length() + " ; fileSize = " + fileSize);
            if (file.exists() && file.length() >= fileSize) {
                isDownload = true;
            }
        }
        return isDownload;
    }

    public static void startBurnMessageActivity(Context mContext, int rcs_is_burn, long smsId,
            long rcsId) {
        Log.i("rcs_test", "it is burn message,id = " + smsId);
        if (rcs_is_burn == 1) {
            Toast.makeText(mContext, mContext.getString(R.string.message_is_burnd),
                    Toast.LENGTH_LONG).show();
        } else {
            mContext.startActivity(new Intent(mContext, BurnFlagMessageActivity.class)
                    .putExtra("smsId", smsId).putExtra("rcsId", rcsId));
        }

    }

    public static void favoritedMessage (Context context,long messageId){
        Uri uri = Uri.parse("content://sms/");
        ContentValues values =new ContentValues() ;
        values.put("favourite", "1");
        context.getContentResolver().update(uri, values,"_id = ?", new String[] {
                String.valueOf(messageId)});

    }

    public static void unFavoritedMessage(Context context, long messageId) {
        Uri uri = Uri.parse("content://sms/");
        ContentValues values = new ContentValues();
        values.put("favourite", "0");
        context.getContentResolver().update(uri, values, "_id = ?", new String[] {
                String.valueOf(messageId)
            });

    }

    public static boolean isFavoritedMessage(Context context, long messageId) {
        boolean isFavorited = false;
        Uri uri = Uri.parse("content://sms/");
        Cursor cursor = context.getContentResolver()
                .query(uri, new String[] {
                    "favourite"
                }, "_id = ?", new String[] {
                        String.valueOf(messageId)
                }, null);
        if (cursor != null && cursor.moveToFirst()) {
            if (!cursor.isAfterLast()) {
                int favorited = cursor.getInt(cursor.getColumnIndex("favourite"));
                if (favorited == 1) {
                    isFavorited = true;
                }
            }
        }
        if (cursor != null) {
            cursor.close();
        }
        return isFavorited;

    }

    public static boolean forwardToGroupMessage(long threadId, List<String> numberList,
            ChatMessage chatMessage,GroupChatModel groupChatModel)
            throws  ServiceDisconnectedException, FileSuffixException, FileTransferException ,FileDurationException{
        if ( chatMessage == null) {
            return false;
        }
        if(groupChatModel == null){
            return false;
        }
        threadId = groupChatModel.getThreadId();
        MessageApi messageApi = RcsApiManager.getMessageApi();
        int msgType = chatMessage.getMsgType();
        switch (msgType) {
            //      case TYPE_SMS:
                  case SuntekMessageData.MSG_TYPE_TEXT:
                Log.i("RCS_UI",
                        "threadId=" + threadId + ";conversationId="
                                + groupChatModel.getConversationId() + ";date="
                                + chatMessage.getData() + ";groupId="
                                + String.valueOf(groupChatModel.getId()));
                     messageApi
                              .sendGroupMessage(threadId, groupChatModel.getConversationId(), -1,
                                      chatMessage.getData(),
                                      String.valueOf(groupChatModel.getId()));
                      break;
                  case SuntekMessageData.MSG_TYPE_AUDIO:
                      Log.i("RCS_UI",
                              "threadId=" + threadId +
                              ";conversationId="+ groupChatModel.getConversationId() +
                               ";getFilePath="+ getFilePath(chatMessage) +
                               ";getAudioLength=" + getAudioLength(chatMessage));
                     messageApi
                              .sendGroupAudioFile(threadId, groupChatModel.getConversationId(),
                                      -1, getFilePath(chatMessage), getAudioLength(chatMessage),
                                      String.valueOf(groupChatModel.getId()), false);
                      break;
                  case SuntekMessageData.MSG_TYPE_VIDEO: {
                            String newFilePath = getForwordFileName(chatMessage);
                            String path = messageApi.getFilepath(chatMessage);
                            boolean success = renameFile(path, newFilePath);
                            if (success) {
                                messageApi.sendGroupVideoFile(threadId, groupChatModel.getConversationId(), -1,
                                        newFilePath, getAudioLength(chatMessage),
                                        String.valueOf(groupChatModel.getId()), false);
                            } else {
                                return false;
                            }
                        }
                      break;
                  case SuntekMessageData.MSG_TYPE_IMAGE: {
                        String newFilePath = getForwordFileName(chatMessage);
                        String path = messageApi.getFilepath(chatMessage);
                        boolean success = renameFile(path, newFilePath);
                        if (success) {
                            messageApi.sendGroupImageFile(threadId, groupChatModel.getConversationId(), -1,
                                    newFilePath, String.valueOf(groupChatModel.getId()), 100);
                        } else {
                            return false;
                        }
                    }
                      break;
                  case SuntekMessageData.MSG_TYPE_CONTACT:
                // Profile profile = ChatMessageUtil.readVcardFile(filePath);
                // messageApi
                // .sendGroupVCard(threadId, groupChatModel.getConversationId(),
                // -1,
                // ProfileManager.profileToRCSContact(profile),
                // String.valueOf(groupChatModel.getId()));
                      messageApi.sendGroupVCard(threadId, groupChatModel.getConversationId(), -1,
                              RcsUtils.RCS_MMS_VCARD_PATH, String.valueOf(groupChatModel.getId()));
                      break;
                  case SuntekMessageData.MSG_TYPE_MAP:
                      GeoLocation geo = readMapXml(getFilePath(chatMessage));
                     messageApi
                              .sendGroupLocation(threadId, groupChatModel.getConversationId(),
                                      -1, geo.getLat(), geo.getLng(), geo.getLabel(),
                                      String.valueOf(groupChatModel.getId()));
                      break;

                  default:
                      break;

        }
        return true;
    }
    
    
    public static void sendForwardRcsMessage(Intent data,int mRcsId,Context context) { 
        Bundle bundle = data.getExtras().getBundle("result");
        if(bundle == null){
            return;
        }
        final Set<String> keySet = bundle.keySet();
        final int recipientCount = (keySet != null) ? keySet.size() : 0;
        final ContactList list;

        list = ContactList.blockingGetByUris(buildUris(keySet, recipientCount));

        long a = -1;
        boolean success = false;
        String[] numbers = list.getNumbers(false);
        try {
            ChatMessage message = RcsApiManager.getMessageApi().getMessageById(mRcsId + "");
            success = RcsChatMessageUtils.forwardMessage(a,
                    Arrays.asList(list.getNumbers()), message);
            if (success) {
                Toast.makeText(context,
               context.getString(R.string.forward_message_success), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context,
             context.getString(R.string.forward_message_fail), Toast.LENGTH_SHORT).show();
            }
        } catch (NumberFormatException e) {
            e.printStackTrace();
            Toast.makeText(context,
          context.getString(R.string.forward_message_fail), Toast.LENGTH_SHORT).show();
        } catch(ServiceDisconnectedException e){
            e.printStackTrace();
            Toast.makeText(context,
        context.getString(R.string.forward_message_fail), Toast.LENGTH_SHORT).show();
        }
    }
    
    private static Uri[] buildUris(final Set<String> keySet, final int newPickRecipientsCount) {
        Uri[] newUris = new Uri[newPickRecipientsCount];
        Iterator<String> it = keySet.iterator();
        int i = 0;
        while (it.hasNext()) {
            String id = it.next();
            newUris[i++] = ContentUris.withAppendedId(Phone.CONTENT_URI, Integer.parseInt(id));
            if (i == newPickRecipientsCount) {
                break;
            }
        }
        return newUris;
    }
    
    public static void forwardContactOrConversation(Context context,OnClickListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setCancelable(true);
        builder.setTitle(R.string.select_contact_conversation);
        builder.setItems(new String[] {
                        context.getString(R.string.forward_contact),
                        context.getString(R.string.forward_conversation),
                        context.getString(R.string.forward_contact_group)
        },listener);
        builder.show();
    }
    
    public static void sendForwardRcsMessageToConv(Intent data,int mRcsId,Context context)
    {               if(data != null && !"".equals(data)){
        ContactList contactList  = new ContactList();
        HashSet<Long> threadIds = (HashSet)data.getSerializableExtra("selectThreadId");
        Log.i("RCS_UI","threadIds.size="+threadIds.size());
        Iterator it = threadIds.iterator();
        Conversation conv = null;
        while(it.hasNext()){
            conv = Conversation.get(context,Long.valueOf(it.next().toString()),true);
            contactList.addAll(conv.getRecipients());
        }
        try {
            long a = -1;
            boolean success = false;
            ChatMessage message = RcsApiManager.getMessageApi().getMessageById(
                    mRcsId + "");
        if (conv.isGroupChat()) {
            GroupChatModel groupChatModel = conv.getGroupChat();
            success = RcsChatMessageUtils.forwardToGroupMessage(a,
                    Arrays.asList(contactList.getNumbers()), message,
                    groupChatModel);
        } else {
            success = RcsChatMessageUtils.forwardMessage(a,
                    Arrays.asList(contactList.getNumbers()), message);
        }
            if (success) {
                Toast.makeText(context,
                		context.getString(R.string.forward_message_success),
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context,
                		context.getString(R.string.forward_message_fail),
                        Toast.LENGTH_SHORT).show();
            }
        } catch (NumberFormatException e) {
            e.printStackTrace();
        } catch (ServiceDisconnectedException e) {
            e.printStackTrace();
        } catch (FileSuffixException e) {
            e.printStackTrace();
           
        } catch (FileTransferException e) {
            e.printStackTrace();
         
        } catch (FileDurationException e) {
            e.printStackTrace();
       
        }
    }
    	}
    
    //forward
    //forward List<String> String[]
    public static boolean forwardMessage(long threadId, List<String> numberList,
            ChatMessage chatMessage) {
        try {
            if ( chatMessage == null) {
                return false;
            }
            int msgType = chatMessage.getMsgType();
            int chatType;
            String filePath = getFilePath(chatMessage);
            MessageSessionModel model = null;
            MessageApi messageApi = RcsApiManager.getMessageApi();
            // model = messageApi
            // .getMessageSessionByThreadId(threadId);
                if (numberList != null) {
                    if (numberList.size() == 1) {
                        chatType = SuntekMessageData.CHAT_TYPE_ONE2ONE;
                    } else {
                        chatType = SuntekMessageData.CHAT_TYPE_ONE2GROUP;
                    }
                } else {
                    return false;
                }
            Log.i("RCS_UI","CHATtype="+chatType);
            switch (chatType) {
                case SuntekMessageData.CHAT_TYPE_ONE2ONE: {
                    Log.i("RCS_UI","ONOTOONE="+SuntekMessageData.CHAT_TYPE_ONE2ONE);
                    Log.i("RCS_UI","one_to_one"+"nubmerlsitSize="+numberList.size());
                    String number;
                    if (model == null) {
                        if (numberList != null && numberList.size() > 0) {
                            number = numberList.get(0);
                        } else {
                            return false;
                        }
                    } else {
                        number = model.getContact();
                    }
                    switch (msgType) {

                        case SuntekMessageData.MSG_TYPE_TEXT:
                           messageApi.sendTextMessage(threadId, number,
                                            chatMessage.getData(),
                                            SuntekMessageData.MSG_BURN_AFTER_READ_NOT, 0);
                            break;
                        case SuntekMessageData.MSG_TYPE_AUDIO:
                            messageApi.sendAudioFile(threadId, -1, number, filePath,
                                            getAudioLength(chatMessage),
                                            SuntekMessageData.MSG_BURN_AFTER_READ_NOT, 0,false);
                            break;
                        case SuntekMessageData.MSG_TYPE_VIDEO: {
                            String newFilePath = getForwordFileName(chatMessage);
                            boolean success = renameFile(filePath, newFilePath);
                            if (success) {
                                messageApi.sendVideoFile(threadId, -1, number, newFilePath,
                                        getAudioLength(chatMessage),
                                        SuntekMessageData.MSG_BURN_AFTER_READ_NOT, 0, false);
                            }
                        }
                            break;
                        case SuntekMessageData.MSG_TYPE_IMAGE: {
                            String newFilePath = getForwordFileName(chatMessage);
                            boolean success = renameFile(filePath, newFilePath);
                            if (success) {
                                messageApi.sendImageFile(threadId, -1, number, newFilePath,
                                        SuntekMessageData.MSG_BURN_AFTER_READ_NOT, 0, 100);
                            } else {
                                return false;
                            }
                        }
                            break;
                        case SuntekMessageData.MSG_TYPE_CONTACT:
                            messageApi.sendVCard(threadId, -1, number, RcsUtils.RCS_MMS_VCARD_PATH);

                            break;
                        case SuntekMessageData.MSG_TYPE_MAP:
                            GeoLocation geo = RcsUtils.readMapXml(filePath);
                           messageApi.sendLocation(threadId, -1, number, geo.getLat(),
                                            geo.getLng(), geo.getLabel());
                            break;

                        default:
                            break;
                    }
                    break;
                }
                case SuntekMessageData.CHAT_TYPE_ONE2GROUP: {
                    Log.i("RCS_UI", "one_to_group");
                    List<String> array;
                    if (model != null) {
                        String numbers = model.getReceiversOfOne2Many();
                        if (TextUtils.isEmpty(numbers)) {
                            Log.i("RCS_UI", "NUMBERS IS NULL");
                            return false;
                        }
                        String[] numberArray = numbers.split(",");
                        if (numberArray == null) {
                            Log.i("RCS_UI", "NUMBERS IS NULL");
                            return false;
                        }
                        array = Arrays.asList(numberArray);
                    } else {
                        array = numberList;
                    }
                    switch (msgType) {

                        case SuntekMessageData.MSG_TYPE_TEXT:
                           messageApi
                                    .sendOne2ManyTextMessage(threadId, array,
                                            chatMessage.getData(),
                                            SuntekMessageData.MSG_BURN_AFTER_READ_NOT, 0);
                            break;
                        case SuntekMessageData.MSG_TYPE_AUDIO:
                            messageApi
                                    .sendOne2ManyAudioFile(threadId, -1, array,
                                            filePath, getAudioLength(chatMessage),
                                            SuntekMessageData.MSG_BURN_AFTER_READ_NOT, 0, false);
                            break;
                        case SuntekMessageData.MSG_TYPE_VIDEO: {
                            String newFilePath = getForwordFileName(chatMessage);
                            boolean success = renameFile(filePath, newFilePath);
                            if (success) {
                                messageApi.sendOne2ManyVideoFile(threadId, -1, array, newFilePath,
                                        getAudioLength(chatMessage),
                                        SuntekMessageData.MSG_BURN_AFTER_READ_NOT, 0, false);
                            } else {
                                return false;
                            }
                        }
                            break;
                        case SuntekMessageData.MSG_TYPE_IMAGE: {
                            String newFilePath = getForwordFileName(chatMessage);
                            boolean success = renameFile(filePath, newFilePath);
                            if (success) {
                                messageApi.sendOne2ManyImageFile(threadId, -1, array, newFilePath,
                                        SuntekMessageData.MSG_BURN_AFTER_READ_NOT, 0, 100);
                            } else {
                                return false;
                            }
                        }
                            break;
                        case SuntekMessageData.MSG_TYPE_CONTACT:
                            messageApi.sendOne2ManyVCard(threadId, -1, array,
                                    RcsUtils.RCS_MMS_VCARD_PATH);
                            break;
                        case SuntekMessageData.MSG_TYPE_MAP:
                            GeoLocation geo = RcsUtils.readMapXml(filePath);
                           messageApi.sendOne2ManyLocation(threadId, -1, array,
                                            geo.getLat(),
                                            geo.getLng(), geo.getLabel());
                            break;

                        default:
                            break;
                    }
                    break;
                }
                case SuntekMessageData.CHAT_TYPE_PUBLIC:

                    break;
                default:
                    break;
            }

        }catch (ServiceDisconnectedException e) {
            e.printStackTrace();
            Log.i("RCS_UI","serviceDisconnectedException");
           return false;
        } catch (FileSuffixException e) {
            e.printStackTrace();
            Log.i("RCS_UI","FileSuffixException");
            return false;
        } catch (FileTransferException e) {
            e.printStackTrace();
            Log.i("RCS_UI","FileTransferException");
            return false;
        } catch (FileDurationException e) {
            e.printStackTrace();
            Log.i("RCS_UI","FileDurationException");
            return false;
        }
        return true;
    }

    public static String getForwordFileName(ChatMessage cMsg)
            throws ServiceDisconnectedException {
        MessageApi messageApi = RcsApiManager.getMessageApi();
        String path = messageApi
                .getFilepath(cMsg);
        if (path != null && path.lastIndexOf("/") != -1) {
            path = path.substring(0, path.lastIndexOf("/") + 1);
            return path + cMsg.getFilename();
        } else {
            return null;
        }
    }

    public static int getAudioLength(ChatMessage cMsg) {
        if (cMsg == null) {
            return 0;
        }
        int len = 0;
        try {
            String lens = cMsg.getData();
            String length = lens.substring(7, lens.lastIndexOf("-"));
            len = Integer.parseInt(length);
        } catch (Exception e) {
            Log.w("RCS_UI",e);
            e.printStackTrace();
            len = 0;
        }
        return len;
    }

}
