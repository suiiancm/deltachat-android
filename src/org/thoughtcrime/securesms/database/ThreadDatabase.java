/*
 * Copyright (C) 2011 Whisper Systems
 * Copyright (C) 2013-2017 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.annimon.stream.Stream;

import net.sqlcipher.database.SQLiteDatabase;

import org.thoughtcrime.securesms.database.GroupDatabase.GroupRecord;
import org.thoughtcrime.securesms.database.RecipientDatabase.RecipientSettings;
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper;
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.ThreadRecord;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.guava.Optional;

import java.io.Closeable;
import java.util.List;

public class ThreadDatabase extends Database {

  private static final String TAG = ThreadDatabase.class.getSimpleName();

  public  static final String TABLE_NAME             = "thread";
  public  static final String ID                     = "_id";
  public  static final String DATE                   = "date";
  public  static final String MESSAGE_COUNT          = "message_count";
  public  static final String ADDRESS                = "recipient_ids";
  public  static final String SNIPPET                = "snippet";
  private static final String SNIPPET_CHARSET        = "snippet_cs";
  public  static final String READ                   = "read";
  public  static final String UNREAD_COUNT           = "unread_count";
  public  static final String TYPE                   = "type";
  private static final String ERROR                  = "error";
  public  static final String SNIPPET_TYPE           = "snippet_type";
  public  static final String SNIPPET_URI            = "snippet_uri";
  public  static final String ARCHIVED               = "archived";
  public  static final String STATUS                 = "status";
  public  static final String DELIVERY_RECEIPT_COUNT = "delivery_receipt_count";
  public  static final String READ_RECEIPT_COUNT     = "read_receipt_count";
  public  static final String EXPIRES_IN             = "expires_in";
  public  static final String LAST_SEEN              = "last_seen";
  private static final String HAS_SENT               = "has_sent";

  public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " ("                    +
    ID + " INTEGER PRIMARY KEY, " + DATE + " INTEGER DEFAULT 0, "                                  +
    MESSAGE_COUNT + " INTEGER DEFAULT 0, " + ADDRESS + " TEXT, " + SNIPPET + " TEXT, "             +
    SNIPPET_CHARSET + " INTEGER DEFAULT 0, " + READ + " INTEGER DEFAULT 1, "                       +
    TYPE + " INTEGER DEFAULT 0, " + ERROR + " INTEGER DEFAULT 0, "                                 +
    SNIPPET_TYPE + " INTEGER DEFAULT 0, " + SNIPPET_URI + " TEXT DEFAULT NULL, "                   +
    ARCHIVED + " INTEGER DEFAULT 0, " + STATUS + " INTEGER DEFAULT 0, "                            +
    DELIVERY_RECEIPT_COUNT + " INTEGER DEFAULT 0, " + EXPIRES_IN + " INTEGER DEFAULT 0, "          +
    LAST_SEEN + " INTEGER DEFAULT 0, " + HAS_SENT + " INTEGER DEFAULT 0, "                         +
    READ_RECEIPT_COUNT + " INTEGER DEFAULT 0, " + UNREAD_COUNT + " INTEGER DEFAULT 0);";

  public static final String[] CREATE_INDEXS = {
    "CREATE INDEX IF NOT EXISTS thread_recipient_ids_index ON " + TABLE_NAME + " (" + ADDRESS + ");",
    "CREATE INDEX IF NOT EXISTS archived_count_index ON " + TABLE_NAME + " (" + ARCHIVED + ", " + MESSAGE_COUNT + ");",
  };

  private static final String[] THREAD_PROJECTION = {
      ID, DATE, MESSAGE_COUNT, ADDRESS, SNIPPET, SNIPPET_CHARSET, READ, UNREAD_COUNT, TYPE, ERROR, SNIPPET_TYPE,
      SNIPPET_URI, ARCHIVED, STATUS, DELIVERY_RECEIPT_COUNT, EXPIRES_IN, LAST_SEEN, READ_RECEIPT_COUNT
  };

  private static final List<String> TYPED_THREAD_PROJECTION = Stream.of(THREAD_PROJECTION)
                                                                    .map(columnName -> TABLE_NAME + "." + columnName)
                                                                    .toList();

  private static final List<String> COMBINED_THREAD_RECIPIENT_GROUP_PROJECTION = Stream.concat(Stream.concat(Stream.of(TYPED_THREAD_PROJECTION),
                                                                                                             Stream.of(RecipientDatabase.TYPED_RECIPIENT_PROJECTION)),
                                                                                               Stream.of(GroupDatabase.TYPED_GROUP_PROJECTION))
                                                                                       .toList();

  public ThreadDatabase(Context context, SQLCipherOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }

  private long createThreadForRecipient(Address address, boolean group, int distributionType) {
    ContentValues contentValues = new ContentValues(4);
    long date                   = System.currentTimeMillis();

    contentValues.put(DATE, date - date % 1000);
    contentValues.put(ADDRESS, address.serialize());

    if (group)
      contentValues.put(TYPE, distributionType);

    contentValues.put(MESSAGE_COUNT, 0);

    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    return db.insert(TABLE_NAME, null, contentValues);
  }

  private void updateThread(long threadId, long count, String body, @Nullable Uri attachment,
                            long date, int status, int deliveryReceiptCount, long type, boolean unarchive,
                            long expiresIn, int readReceiptCount)
  {
    ContentValues contentValues = new ContentValues(7);
    contentValues.put(DATE, date - date % 1000);
    contentValues.put(MESSAGE_COUNT, count);
    contentValues.put(SNIPPET, body);
    contentValues.put(SNIPPET_URI, attachment == null ? null : attachment.toString());
    contentValues.put(SNIPPET_TYPE, type);
    contentValues.put(STATUS, status);
    contentValues.put(DELIVERY_RECEIPT_COUNT, deliveryReceiptCount);
    contentValues.put(READ_RECEIPT_COUNT, readReceiptCount);
    contentValues.put(EXPIRES_IN, expiresIn);

    if (unarchive) {
      contentValues.put(ARCHIVED, 0);
    }

    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.update(TABLE_NAME, contentValues, ID + " = ?", new String[] {threadId + ""});
    notifyConversationListListeners();
  }

  private void deleteThread(long threadId) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();
    db.delete(TABLE_NAME, ID_WHERE, new String[] {threadId + ""});
    notifyConversationListListeners();
  }

  public Cursor getDirectShareList() {
    SQLiteDatabase db    = databaseHelper.getReadableDatabase();
    String         query = createQuery(MESSAGE_COUNT + " != 0", 0);

    return db.rawQuery(query, null);
  }

  public long getThreadIdFor(Recipient recipient) {
    return getThreadIdFor(recipient, DistributionTypes.DEFAULT);
  }

  public long getThreadIdFor(Recipient recipient, int distributionType) {
    SQLiteDatabase db            = databaseHelper.getReadableDatabase();
    String         where         = ADDRESS + " = ?";
    String[]       recipientsArg = new String[]{recipient.getAddress().serialize()};
    Cursor         cursor        = null;

    try {
      cursor = db.query(TABLE_NAME, new String[]{ID}, where, recipientsArg, null, null, null);

      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getLong(cursor.getColumnIndexOrThrow(ID));
      } else {
        return createThreadForRecipient(recipient.getAddress(), recipient.isGroupRecipient(), distributionType);
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  public boolean update(long threadId, boolean unarchive) {
    MmsSmsDatabase mmsSmsDatabase = DatabaseFactory.getMmsSmsDatabase(context);
    long count                    = mmsSmsDatabase.getConversationCount(threadId);

    if (count == 0) {
      deleteThread(threadId);
      notifyConversationListListeners();
      return true;
    }

    MmsSmsDatabase.Reader reader = null;

    try {
      reader = mmsSmsDatabase.readerFor(mmsSmsDatabase.getConversationSnippet(threadId));
      MessageRecord record;

      if (reader != null && (record = reader.getNext()) != null) {
        updateThread(threadId, count, getFormattedBodyFor(record), getAttachmentUriFor(record),
                     record.getTimestamp(), record.getDeliveryStatus(), record.getDeliveryReceiptCount(),
                     record.getType(), unarchive, record.getExpiresIn(), record.getReadReceiptCount());
        notifyConversationListListeners();
        return false;
      } else {
        deleteThread(threadId);
        notifyConversationListListeners();
        return true;
      }
    } finally {
      if (reader != null)
        reader.close();
    }
  }

  private @NonNull String getFormattedBodyFor(@NonNull MessageRecord messageRecord) {
    return messageRecord.getBody();
  }

  private @Nullable Uri getAttachmentUriFor(MessageRecord record) {
    if (!record.isMms() || record.isMmsNotification() || record.isGroupAction()) return null;

    SlideDeck slideDeck = ((MediaMmsMessageRecord)record).getSlideDeck();
    Slide     thumbnail = slideDeck.getThumbnailSlide();

    return thumbnail != null ? thumbnail.getThumbnailUri() : null;
  }

  private @NonNull String createQuery(@NonNull String where, int limit) {
    String projection = Util.join(COMBINED_THREAD_RECIPIENT_GROUP_PROJECTION, ",");
    String query =
    "SELECT " + projection + " FROM " + TABLE_NAME +
           " LEFT OUTER JOIN " + RecipientDatabase.TABLE_NAME +
           " ON " + TABLE_NAME + "." + ADDRESS + " = " + RecipientDatabase.TABLE_NAME + "." + RecipientDatabase.ADDRESS +
           " LEFT OUTER JOIN " + GroupDatabase.TABLE_NAME +
           " ON " + TABLE_NAME + "." + ADDRESS + " = " + GroupDatabase.TABLE_NAME + "." + GroupDatabase.GROUP_ID +
           " WHERE " + where +
           " ORDER BY " + TABLE_NAME + "." + DATE + " DESC";

    if (limit >  0) {
      query += " LIMIT " + limit;
    }

    return query;
  }

  public Reader readerFor(Cursor cursor) {
    return new Reader(cursor);
  }

  public static class DistributionTypes {
    public static final int DEFAULT      = 2;
    public static final int CONVERSATION = 2;
    public static final int ARCHIVE      = 3;
    public static final int INBOX_ZERO   = 4;
  }

  public class Reader implements Closeable {

    private final Cursor cursor;

    public Reader(Cursor cursor) {
      this.cursor = cursor;
    }

    public ThreadRecord getNext() {
      if (cursor == null || !cursor.moveToNext())
        return null;

      return getCurrent();
    }

    public ThreadRecord getCurrent() {
      long    threadId         = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.ID));
      int     distributionType = cursor.getInt(cursor.getColumnIndexOrThrow(ThreadDatabase.TYPE));
      Address address          = Address.fromSerialized(cursor.getString(cursor.getColumnIndexOrThrow(ThreadDatabase.ADDRESS)));

      Optional<RecipientSettings> settings;
      Optional<GroupRecord>       groupRecord;

      if (distributionType != DistributionTypes.ARCHIVE && distributionType != DistributionTypes.INBOX_ZERO) {
        settings    = DatabaseFactory.getRecipientDatabase(context).getRecipientSettings(cursor);
        groupRecord = DatabaseFactory.getGroupDatabase(context).getGroup(cursor);
      } else {
        settings    = Optional.absent();
        groupRecord = Optional.absent();
      }

      Recipient          recipient            = Recipient.from(context, address, settings, groupRecord, true);
      String             body                 = cursor.getString(cursor.getColumnIndexOrThrow(ThreadDatabase.SNIPPET));
      long               date                 = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.DATE));
      long               count                = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.MESSAGE_COUNT));
      int                unreadCount          = cursor.getInt(cursor.getColumnIndexOrThrow(ThreadDatabase.UNREAD_COUNT));
      long               type                 = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.SNIPPET_TYPE));
      boolean            archived             = cursor.getInt(cursor.getColumnIndex(ThreadDatabase.ARCHIVED)) != 0;
      int                status               = cursor.getInt(cursor.getColumnIndexOrThrow(ThreadDatabase.STATUS));
      int                deliveryReceiptCount = cursor.getInt(cursor.getColumnIndexOrThrow(ThreadDatabase.DELIVERY_RECEIPT_COUNT));
      int                readReceiptCount     = cursor.getInt(cursor.getColumnIndexOrThrow(ThreadDatabase.READ_RECEIPT_COUNT));
      long               expiresIn            = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.EXPIRES_IN));
      long               lastSeen             = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.LAST_SEEN));
      Uri                snippetUri           = getSnippetUri(cursor);

//      if (!TextSecurePreferences.isReadReceiptsEnabled(context)) {
//        readReceiptCount = 0;
//      }

      return new ThreadRecord(context, body, snippetUri, recipient, date, count,
                              unreadCount, threadId, deliveryReceiptCount, status, type,
                              distributionType, archived, expiresIn, lastSeen, readReceiptCount, false, null);
    }

    private @Nullable Uri getSnippetUri(Cursor cursor) {
      if (cursor.isNull(cursor.getColumnIndexOrThrow(ThreadDatabase.SNIPPET_URI))) {
        return null;
      }

      try {
        return Uri.parse(cursor.getString(cursor.getColumnIndexOrThrow(ThreadDatabase.SNIPPET_URI)));
      } catch (IllegalArgumentException e) {
        Log.w(TAG, e);
        return null;
      }
    }

    @Override
    public void close() {
      if (cursor != null) {
        cursor.close();
      }
    }
  }
}
