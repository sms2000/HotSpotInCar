package com.ogp.hotspotincar;

import java.lang.reflect.Method;
import java.util.List;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.util.Log;
import android.net.wifi.WifiConfiguration;

//
// Beware! 
// Uses undocumented features. Supported in Android 4.3.x to 6.0.1. 
//

public class Actuator implements OnBtStatusChanged 
{
	private static final String TAG 					= "Actuator";
    private static final int 	WIFI_AP_STATE_FAILED 	= 4;
    
	private Context				context;

	private WifiManager 		wifimanager;
	private Method 				wifiControlMethod;
    private Method 				wifiApConfigurationMethod;
    private Method 				wifiApState;
    
	
	@TargetApi(23)
	public Actuator(Context	context)
	{
		this.context 	 	= context;
		this.wifimanager 	= (WifiManager)context.getSystemService(Context.WIFI_SERVICE);
		
		try 
		{
			this.wifiControlMethod 			= wifimanager.getClass().getMethod("setWifiApEnabled", WifiConfiguration.class, boolean.class);
			this.wifiApConfigurationMethod 	= wifimanager.getClass().getMethod("getWifiApConfiguration");
			this.wifiApState 				= wifimanager.getClass().getMethod("getWifiApState");
		} 
		catch (NoSuchMethodException e) 
		{
			e.printStackTrace();
		}
		
		WatchdogService.registerBtStatusChangedReceiver(this);
	}

	
	public void stop()
	{
		WatchdogService.unregisterBtStatusChangedReceiver(this);
	}
	
	
	//
	// Overrides
	//
	@Override
	public void onBtListStatusChanged(boolean deviceState, List<BtItem> listItems) 
	{
		if (deviceState)
		{
	        Log.i(TAG, String.format ("onBtListStatusChanged. Received [%d] items.", 
	    			listItems.size()));
	
	        boolean actuate = false;
	        
	        for (BtItem btItem : listItems)
	        {
	        	if (btItem.getConnected() && btItem.getSelected())
	        	{
	        		actuate = true;
	        		break;
	        	}
	        }
	        
        	updateActuatorState (true, actuate);
	
        	Log.i(TAG, String.format ("onBtListStatusChanged. New target actuator state: [%s].", 
        			actuate ? "activated" : "disabled"));
		}
		else
		{
			updateActuatorState (false, false);
        	Log.i(TAG, "onBtListStatusChanged. BT device is off");
		}
        
	}

	
	public boolean setWifiApState(WifiConfiguration config, boolean enabled) 
	{
		try 
		{
			if (enabled)
			{
		        wifimanager.setWifiEnabled (!enabled);
		        Thread.sleep(150);
		        return (Boolean)wifiControlMethod.invoke(wifimanager, config, enabled);
			}
			else
			{
		        Boolean ret = (Boolean)wifiControlMethod.invoke(wifimanager, config, enabled);
		        Thread.sleep(150);
		        wifimanager.setWifiEnabled (!enabled);
		        return ret;
			}
		} 
		catch (Exception e) 
		{
			e.printStackTrace();
	        return false;
		}
	}
	
	
	public WifiConfiguration getWifiApConfiguration()
	{
		try
		{
			return (WifiConfiguration)wifiApConfigurationMethod.invoke(wifimanager);
		}
		catch(Exception e)
		{
			e.printStackTrace();
			return null;
		}
	}
	
	
	public int getWifiApState() 
	{
		try 
		{
			return (Integer)wifiApState.invoke(wifimanager);
		} 
		catch (Exception e) 
		{
			e.printStackTrace();
			return WIFI_AP_STATE_FAILED;
		}
	}	
	
	
	private void updateActuatorState (boolean deviceState, boolean actuatorState)
	{
		Log.i(TAG, String.format("updateActuatorState. Status [%s/%s].",
				deviceState ? "on" : "off", actuatorState ? "on" : "off"));
		
		WatchdogService.ActuatorState eActuatorState = WatchdogService.ActuatorState.DISABLED;
		if (deviceState)
		{
			eActuatorState = actuatorState ? WatchdogService.ActuatorState.ACTUATED : WatchdogService.ActuatorState.NOT_ACTUATED;
		}
		
		setWifiApState (getWifiApConfiguration(), WatchdogService.ActuatorState.ACTUATED == eActuatorState);
		
		Intent intent = new Intent();
		intent.setAction (WatchdogService.ACTUATOR_UPDATE_STATUS);
		intent.putExtra(WatchdogService.ACTUATOR_STATE, eActuatorState);
		intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
		context.sendBroadcast(intent);
	}
}

