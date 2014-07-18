package com.fy.ballacce;

import android.app.Activity;
import android.content.Intent;
import android.net.nsd.NsdServiceInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.View;


public class DiscoveryActivity extends Activity {
	private NsdHelper mNsdHelper; 
	private final String KEY_IP = "IP";
	private final String KEY_PORT = "PORT";
	private final String TAG = "DiscoveryActivity";
	
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.discovery);
		
		mNsdHelper = new NsdHelper(this);
		mNsdHelper.initializeResolveListener();
		mNsdHelper.initializeDiscoveryListener();
	}
	
    public void clickDiscover(View v) {
    	Log.v(TAG, "clickDiscover");
        mNsdHelper.discoverServices();
    }
    
    public void clickConnect(View v) {
        NsdServiceInfo service = mNsdHelper.getChosenServiceInfo();
        if (service != null) {
            Log.d(TAG, "Connecting.");
            Log.v(TAG, "discovery service : " + service);
            
			Intent intent = new Intent();
			intent.setClass(DiscoveryActivity.this, BallAcceActivity.class);
			String strIp = service.getHost().toString().substring(1);
			Log.v(TAG, "strIp : " + strIp);
			Bundle bundle = new Bundle();
			bundle.putString(KEY_IP, strIp);
			bundle.putInt(KEY_PORT, service.getPort());
			intent.putExtras(bundle);
			startActivity(intent);
        } else {
            Log.d(TAG, "No service to connect to!");
        }
    }
}
