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
 *******************************************************************************
 *
 * File:    MrChatlist.java
 * Purpose: Wrap around mrchatlist_t
 *
 ******************************************************************************/


package com.b44t.messenger;


public class MrChatlist {
    public MrChatlist(long hChatlist) {
        m_hChatlist = hChatlist;
    }

    @Override protected void finalize() throws Throwable {
        super.finalize();
        MrChatlistUnref(m_hChatlist);
        m_hChatlist = 0;
    }

    public int getCnt() {
        return MrChatlistGetCnt(m_hChatlist);
    }

    public MrChat getChatByIndex(int index) {
        return new MrChat(MrChatlistGetChatByIndex(m_hChatlist, index));
    }

    public MrMsg getMsgByIndex(int index) {
        return new MrMsg(MrChatlistGetMsgByIndex(m_hChatlist, index));
    }

    public MrLot getSummaryByIndex(int index, MrChat chat) {
        return new MrLot(MrChatlistGetSummaryByIndex(m_hChatlist, index, chat.getCPtr()));
    }

    private long                  m_hChatlist;
    private native static void    MrChatlistUnref            (long hChatlist);
    private native static int     MrChatlistGetCnt           (long hChatlist);
    private native static long    MrChatlistGetChatByIndex   (long hChatlist, int index); // returns hChat which must be unref'd after usage
    private native static long    MrChatlistGetMsgByIndex    (long hChatlist, int index); // returns hMsg which must be unref'd after usage
    private native static long    MrChatlistGetSummaryByIndex(long hChatlist, int index, long hChat);
}
