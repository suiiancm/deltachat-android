/*******************************************************************************
 *
 *                              Delta Chat Android
 *                           (C) 2017 Björn Petersen
 *                    Contact: r10s@b44t.com, http://b44t.com
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see http://www.gnu.org/licenses/ .
 *
 ******************************************************************************/


package org.thoughtcrime.securesms.connect;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;


public class AttachmentsContentProvider extends ContentProvider {

    /* We save all attachments in our private files-directory
    that cannot be read by other apps.

    When starting an Intent for viewing, we cannot use the paths.
    Instead, we give a content://-url that results in calls to this class.

    (An alternative would be to copy files to view to a public directory, however, this would
    lead to duplicate data.
    Another alternative would be to write all attachments to a public directory, however, this
    may lead to security problems as files are system-wide-readable and would also cause problems
    if the user or another app deletes these files) */

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        ApplicationDcContext dcContext = DcHelper.getContext(getContext());

        String path = uri.getPath();
        if (!dcContext.sharedFiles.containsKey(path)) {
            throw new FileNotFoundException("File was not shared before.");
        }

        File privateFile = new File(dcContext.getBlobdir(), path);
        return ParcelFileDescriptor.open(privateFile, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public int delete(Uri arg0, String arg1, String[] arg2) {
        return 0;
    }

    @Override
    public String getType(Uri arg0) {
        return null;
    }

    @Override
    public Uri insert(Uri arg0, ContentValues arg1) {
        return null;
    }

    @Override
    public boolean onCreate() {
        return false;
    }

    @Override
    public Cursor query(Uri arg0, String[] arg1, String arg2, String[] arg3,
                        String arg4) {
        return null;
    }

    @Override
    public int update(Uri arg0, ContentValues arg1, String arg2, String[] arg3) {
        return 0;
    }
}
