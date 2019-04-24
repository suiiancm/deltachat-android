package org.thoughtcrime.securesms.map;

import android.os.AsyncTask;
import android.util.Log;

import com.b44t.messenger.DcContext;
import com.mapbox.geojson.Feature;
import com.mapbox.mapboxsdk.geometry.LatLngBounds;

import org.thoughtcrime.securesms.map.model.MapSource;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;

import static org.thoughtcrime.securesms.map.MapDataManager.TIMESTAMP_NOW;
import static org.thoughtcrime.securesms.map.MapDataManager.TIME_FRAME;

/**
 * Created by cyberta on 15.04.19.
 */

public class DataCollectionTask extends AsyncTask<Void, Void, Void> {

    private static final String TAG = DataCollectionTask.class.getSimpleName();
    private static final HashSet<DataCollectionTask> instances = new HashSet<>();

    private final int chatId;
    private final int[] contactIds;
    private ConcurrentHashMap<Integer, MapSource> contactMapSources;
    private ConcurrentHashMap<String, LinkedList<Feature>> featureCollections;
    private ConcurrentHashMap<Integer, Feature> lastPositions;
    private final LatLngBounds.Builder boundingBuilder;
    private final DcContext dcContext;
    private final DataCollectionCallback callback;

    public DataCollectionTask(DcContext context,
                              int chatId,
                              int[] contactIds,
                              ConcurrentHashMap<Integer, MapSource> contactMapSources,
                              ConcurrentHashMap featureCollections,
                              ConcurrentHashMap<Integer, Feature> lastPositions,
                              LatLngBounds.Builder boundingBuilder,
                              DataCollectionCallback callback) {
        this.chatId = chatId;
        this.contactMapSources = contactMapSources;
        this.featureCollections = featureCollections;
        this.lastPositions = lastPositions;
        this.boundingBuilder = boundingBuilder;
        this.dcContext = context;
        this.callback = callback;
        this.contactIds = contactIds;
        instances.add(this);
    }

    public static void cancelRunningTasks() {
        for (DataCollectionTask task : instances) {
            task.cancel(true);
        }
    }

    @Override
    protected Void doInBackground(Void... voids) {
        Log.d(TAG, "performance test - collect Data start");
        DataCollector dataCollector = new DataCollector(dcContext,
                contactMapSources,
                featureCollections,
                lastPositions,
                boundingBuilder);
        int markerCounter = 0;
        for (int contactId : contactIds) {

           markerCounter = markerCounter + dataCollector.updateSource(chatId,
                    contactId,
                    System.currentTimeMillis() - TIME_FRAME,
                    TIMESTAMP_NOW);
            if (this.isCancelled()) {
                break;
            }
        }
        return null;
    }

    @Override
    protected void onProgressUpdate(Void... values) {
        if (!this.isCancelled()) {
            callback.onDataCollectionFinished();
        }
    }

    @Override
    protected void onPostExecute(Void value) {
        if (!this.isCancelled()) {
            callback.onDataCollectionFinished();
        }
        instances.remove(this);
        Log.d(TAG, "performance test - collect Data finished");
    }

    @Override
    protected void onCancelled() {
        super.onCancelled();
        instances.remove(this);
    }
}
