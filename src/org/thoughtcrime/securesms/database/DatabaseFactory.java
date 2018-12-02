/*
 * Copyright (C) 2018 Open Whisper Systems
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

import android.content.Context;
import android.support.annotation.NonNull;

import net.sqlcipher.database.SQLiteDatabase;

import org.thoughtcrime.securesms.contacts.ContactsDatabase;
import org.thoughtcrime.securesms.crypto.AttachmentSecret;
import org.thoughtcrime.securesms.crypto.AttachmentSecretProvider;
import org.thoughtcrime.securesms.crypto.DatabaseSecret;
import org.thoughtcrime.securesms.crypto.DatabaseSecretProvider;
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper;

public class DatabaseFactory {

  private static final Object lock = new Object();

  private static DatabaseFactory instance;

  private final SQLCipherOpenHelper   databaseHelper;
  private final SmsDatabase           sms;
  private final MmsDatabase           mms;
  private final AttachmentDatabase    attachments;
  private final MediaDatabase         media;
  private final ThreadDatabase        thread;
  private final MmsSmsDatabase        mmsSmsDatabase;
  private final GroupDatabase         groupDatabase;
  private final RecipientDatabase     recipientDatabase;
  private final ContactsDatabase      contactsDatabase;
  private final GroupReceiptDatabase  groupReceiptDatabase;
  private final SearchDatabase        searchDatabase;

  public static DatabaseFactory getInstance(Context context) {
    synchronized (lock) {
      if (instance == null)
        instance = new DatabaseFactory(context.getApplicationContext());

      return instance;
    }
  }

  public static MmsSmsDatabase getMmsSmsDatabase(Context context) {
    return getInstance(context).mmsSmsDatabase;
  }

  public static ThreadDatabase getThreadDatabase(Context context) {
    return getInstance(context).thread;
  }

  public static SmsDatabase getSmsDatabase(Context context) {
    return getInstance(context).sms;
  }

  public static MmsDatabase getMmsDatabase(Context context) {
    return getInstance(context).mms;
  }

  public static AttachmentDatabase getAttachmentDatabase(Context context) {
    return getInstance(context).attachments;
  }

  public static MediaDatabase getMediaDatabase(Context context) {
    return getInstance(context).media;
  }

  public static GroupDatabase getGroupDatabase(Context context) {
    return getInstance(context).groupDatabase;
  }

  public static RecipientDatabase getRecipientDatabase(Context context) {
    return getInstance(context).recipientDatabase;
  }

  public static ContactsDatabase getContactsDatabase(Context context) {
    return getInstance(context).contactsDatabase;
  }

  public static GroupReceiptDatabase getGroupReceiptDatabase(Context context) {
    return getInstance(context).groupReceiptDatabase;
  }

  public static SearchDatabase getSearchDatabase(Context context) {
    return getInstance(context).searchDatabase;
  }

  private DatabaseFactory(@NonNull Context context) {
    SQLiteDatabase.loadLibs(context);

    DatabaseSecret      databaseSecret   = new DatabaseSecretProvider(context).getOrCreateDatabaseSecret();
    AttachmentSecret    attachmentSecret = AttachmentSecretProvider.getInstance(context).getOrCreateAttachmentSecret();

    this.databaseHelper       = new SQLCipherOpenHelper(context, databaseSecret);
    this.sms                  = new SmsDatabase(context, databaseHelper);
    this.mms                  = new MmsDatabase(context, databaseHelper);
    this.attachments          = new AttachmentDatabase(context, databaseHelper, attachmentSecret);
    this.media                = new MediaDatabase(context, databaseHelper);
    this.thread               = new ThreadDatabase(context, databaseHelper);
    this.mmsSmsDatabase       = new MmsSmsDatabase(context, databaseHelper);
    this.groupDatabase        = new GroupDatabase(context, databaseHelper);
    this.recipientDatabase    = new RecipientDatabase(context, databaseHelper);
    this.groupReceiptDatabase = new GroupReceiptDatabase(context, databaseHelper);
    this.contactsDatabase     = new ContactsDatabase(context);
    this.searchDatabase       = new SearchDatabase(context, databaseHelper);
  }



}
