package com.ogp.hotspotincar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;


public class BootLoader extends BroadcastReceiver
{
	private static final String TAG = "BootLoader";


	public BootLoader()
	{
		super();
	}


	@Override
	public void onReceive (Context 		context,
						   Intent 		intent)
	{
		try
		{
			String str = intent.getAction();
			if (str.equals ("android.intent.action.BOOT_COMPLETED"))
			{
				Log.i(TAG,  "onReceive. Recognized BOOT_COMPLETED. Starting the service.");
				
				WatchdogService.bootFinished(context.getApplicationContext());
			}

		}
		catch(Exception e)
		{
			Log.e(TAG,  "onReceive. Major problem: exception!");
			e.printStackTrace();
		}
	}
}
