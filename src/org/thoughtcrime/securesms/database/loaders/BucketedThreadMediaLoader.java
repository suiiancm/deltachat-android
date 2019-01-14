package org.thoughtcrime.securesms.database.loaders;


import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v4.content.AsyncTaskLoader;

import com.annimon.stream.Stream;
import com.b44t.messenger.DcContext;
import com.b44t.messenger.DcMsg;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.connect.DcHelper;
import org.thoughtcrime.securesms.database.Address;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class BucketedThreadMediaLoader extends AsyncTaskLoader<BucketedThreadMediaLoader.BucketedThreadMedia> {

  @SuppressWarnings("unused")
  private static final String TAG = BucketedThreadMediaLoader.class.getSimpleName();

  private final Address         address;

  public BucketedThreadMediaLoader(@NonNull Context context, @NonNull Address address) {
    super(context);
    this.address  = address;

    onContentChanged();
  }

  @Override
  protected void onStartLoading() {
    if (takeContentChanged()) {
      forceLoad();
    }
  }

  @Override
  protected void onStopLoading() {
    cancelLoad();
  }

  @Override
  protected void onAbandon() {
  }

  @Override
  public BucketedThreadMedia loadInBackground() {
    BucketedThreadMedia result   = new BucketedThreadMedia(getContext());
    DcContext context = DcHelper.getContext(getContext());
    int[] messages = context.getChatMedia(address.getDcChatId(), DcMsg.DC_MSG_IMAGE, DcMsg.DC_MSG_GIF, DcMsg.DC_MSG_VIDEO);
    for(int nextId : messages) {
      result.add(context.getMsg(nextId));
    }

    return result;
  }

  public static class BucketedThreadMedia {

    private final TimeBucket   TODAY;
    private final TimeBucket   YESTERDAY;
    private final TimeBucket   THIS_WEEK;
    private final TimeBucket   THIS_MONTH;
    private final MonthBuckets OLDER;

    private final TimeBucket[] TIME_SECTIONS;

    public BucketedThreadMedia(@NonNull Context context) {
      this.TODAY         = new TimeBucket(context.getString(R.string.today), TimeBucket.addToCalendar(Calendar.DAY_OF_YEAR, -1), TimeBucket.addToCalendar(Calendar.YEAR, 1000));
      this.YESTERDAY     = new TimeBucket(context.getString(R.string.yesterday), TimeBucket.addToCalendar(Calendar.DAY_OF_YEAR, -2), TimeBucket.addToCalendar(Calendar.DAY_OF_YEAR, -1));
      this.THIS_WEEK     = new TimeBucket(context.getString(R.string.this_week), TimeBucket.addToCalendar(Calendar.DAY_OF_YEAR, -7), TimeBucket.addToCalendar(Calendar.DAY_OF_YEAR, -2));
      this.THIS_MONTH    = new TimeBucket(context.getString(R.string.this_month), TimeBucket.addToCalendar(Calendar.DAY_OF_YEAR, -30), TimeBucket.addToCalendar(Calendar.DAY_OF_YEAR, -7));
      this.TIME_SECTIONS = new TimeBucket[]{TODAY, YESTERDAY, THIS_WEEK, THIS_MONTH};
      this.OLDER         = new MonthBuckets();
    }

    public void add(DcMsg imageMessage) {
      for (TimeBucket timeSection : TIME_SECTIONS) {
        if (timeSection.inRange(imageMessage.getTimestamp())) {
          timeSection.add(imageMessage);
          return;
        }
      }
      OLDER.add(imageMessage);
    }

    public int getSectionCount() {
      return (int)Stream.of(TIME_SECTIONS)
                        .filter(timeBucket -> !timeBucket.isEmpty())
                        .count() +
             OLDER.getSectionCount();
    }

    public int getSectionItemCount(int section) {
      List<TimeBucket> activeTimeBuckets = Stream.of(TIME_SECTIONS).filter(timeBucket -> !timeBucket.isEmpty()).toList();

      if (section < activeTimeBuckets.size()) return activeTimeBuckets.get(section).getItemCount();
      else                                    return OLDER.getSectionItemCount(section - activeTimeBuckets.size());
    }

    public DcMsg get(int section, int item) {
      List<TimeBucket> activeTimeBuckets = Stream.of(TIME_SECTIONS).filter(timeBucket -> !timeBucket.isEmpty()).toList();

      if (section < activeTimeBuckets.size()) return activeTimeBuckets.get(section).getItem(item);
      else                                    return OLDER.getItem(section - activeTimeBuckets.size(), item);
    }

    public String getName(int section, Locale locale) {
      List<TimeBucket> activeTimeBuckets = Stream.of(TIME_SECTIONS).filter(timeBucket -> !timeBucket.isEmpty()).toList();

      if (section < activeTimeBuckets.size()) return activeTimeBuckets.get(section).getName();
      else                                    return OLDER.getName(section - activeTimeBuckets.size(), locale);
    }

    private static class TimeBucket {

      private final List<DcMsg> records = new LinkedList<>();

      private final long   startTime;
      private final long endTime;
      private final String name;

      TimeBucket(String name, long startTime, long endTime) {
        this.name      = name;
        this.startTime = startTime;
        this.endTime = endTime;
      }

      void add(DcMsg record) {
        this.records.add(record);
      }

      boolean inRange(long timestamp) {
        return timestamp > startTime && timestamp <= endTime;
      }

      boolean isEmpty() {
        return records.isEmpty();
      }

      int getItemCount() {
        return records.size();
      }

      DcMsg getItem(int position) {
        return records.get(position);
      }

      String getName() {
        return name;
      }

      static long addToCalendar(int field, int amount) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(field, amount);
        return calendar.getTimeInMillis();
      }
    }

    private static class MonthBuckets {

      private final Map<Date, List<DcMsg>> months = new HashMap<>();

      void add(DcMsg record) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(record.getTimestamp());

        int  year  = calendar.get(Calendar.YEAR) - 1900;
        int  month = calendar.get(Calendar.MONTH);
        Date date  = new Date(year, month, 1);

        if (months.containsKey(date)) {
          months.get(date).add(record);
        } else {
          List<DcMsg> list = new LinkedList<>();
          list.add(record);
          months.put(date, list);
        }
      }

      int getSectionCount() {
        return months.size();
      }

      int getSectionItemCount(int section) {
        return months.get(getSection(section)).size();
      }

      DcMsg getItem(int section, int position) {
        return months.get(getSection(section)).get(position);
      }

      Date getSection(int section) {
        ArrayList<Date> keys = new ArrayList<>(months.keySet());
        Collections.sort(keys, Collections.reverseOrder());

        return keys.get(section);
      }

      String getName(int section, Locale locale) {
        Date sectionDate = getSection(section);

        return new SimpleDateFormat("MMMM yyyy", locale).format(sectionDate);
      }
    }
  }
}
