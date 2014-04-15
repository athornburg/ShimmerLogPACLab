//This is just a generic case for using a service in an activity. 

package io.pacmonitorandroid.app.main;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import io.pacmonitorandroid.app.service.ShimmerService;
import io.pacmonitorandroid.app.service.ShimmerService.LocalBinder;

public class ServiceActivity extends Activity {
	static ShimmerService mService;
	boolean mServiceBind=false;
	protected boolean mServiceFirstTime=true;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!isMyServiceRunning())
        {
        	Log.d("ShimmerH","Oncreate2");
        	Intent intent=new Intent(this, ShimmerService.class);
        	startService(intent);
        	if (mServiceFirstTime==true){
        		Log.d("ShimmerH","Oncreate3");
    			getApplicationContext().bindService(intent,mTestServiceConnection, Context.BIND_AUTO_CREATE);
    			mServiceFirstTime=false;
    		}
        }
     }
	
    public void onPause(){
  	  super.onPause();
  	  if(mServiceBind == true){
  		  getApplicationContext().unbindService(mTestServiceConnection);
  	  }
  	 }
    
	public void onResume(){
		super.onResume();
	  	Intent intent=new Intent(this, ShimmerService.class);
	  	Log.d("ShimmerH","on Resume");
	  	getApplicationContext().bindService(intent,mTestServiceConnection, Context.BIND_AUTO_CREATE);
	}
    
    
	
    protected ServiceConnection mTestServiceConnection = new ServiceConnection() {

      	public void onServiceConnected(ComponentName arg0, IBinder service) {
      		// TODO Auto-generated method stub
      		Log.d("ShimmerService", "srvice connected");
      		LocalBinder binder = (ShimmerService.LocalBinder) service;
      		mService = binder.getService();
      		mServiceBind = true;
      		//update the view
      		}

      	public void onServiceDisconnected(ComponentName arg0) {
      		// TODO Auto-generated method stub
      		mServiceBind = false;
      	}
    };
    protected boolean isMyServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if ("com.shimmerresearch.service.ShimmerServiceCBBC".equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
      
    
}
