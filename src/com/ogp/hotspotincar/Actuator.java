package com.ogp.hotspotincar;

import java.util.List;

import android.content.Context;
import android.content.Intent;
import android.util.Log;


public class Actuator implements OnBtStatusChanged 
{
	private static final String TAG = "Actuator";

	private Context				context;

	
	public Actuator(Context	context)
	{
		this.context = context;
		
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

	
	private void updateActuatorState (boolean deviceState, boolean actuatorState)
	{
		Log.i(TAG, String.format("updateActuatorState. Status [%s/%s].",
				deviceState ? "on" : "off", actuatorState ? "on" : "off"));
		
		WatchdogService.ActuatorState eActuatorState = WatchdogService.ActuatorState.DISABLED;
		if (deviceState)
		{
			eActuatorState = actuatorState ? WatchdogService.ActuatorState.ACTUATED : WatchdogService.ActuatorState.NOT_ACTUATED;
		}
		
		Intent intent = new Intent();
		intent.setAction (WatchdogService.ACTUATOR_UPDATE_STATUS);
		intent.putExtra(WatchdogService.ACTUATOR_STATE, eActuatorState);
		intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
		context.sendBroadcast(intent);
	}
}
