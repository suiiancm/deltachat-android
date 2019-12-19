package org.thoughtcrime.securesms.connect;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

public class NetworkStateReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        try {
            ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = manager.getActiveNetworkInfo();

            if (ni != null && ni.getState() == NetworkInfo.State.CONNECTED) {
                Log.i("DeltaChat", "++++++++++++++++++ Connected ++++++++++++++++++");
                ApplicationDcContext dcContext = DcHelper.getContext(context);
                new Thread(() -> {
                    // call dc_maybe_network() from a worker thread.
                    // theoretically, dc_maybe_network() can be called from the main thread and returns at once,
                    // however, in reality, it does currently halt things for some seconds.
                    // this is a workaround that make things usable for now.
                    Log.i("DeltaChat", "calling maybeNetwork()");
                    dcContext.maybeNetwork();
                    Log.i("DeltaChat", "maybeNetwork() returned");
                }).start();
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
