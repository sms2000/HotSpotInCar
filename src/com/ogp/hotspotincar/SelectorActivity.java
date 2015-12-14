package com.ogp.hotspotincar;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;


public class SelectorActivity extends Activity implements SelectedCallbackInterface, OnBtStatusChanged 
{
	private static final 	String 			TAG 				= "SelectorActivity";

	private ListView		listView 	= null;
	private TextView		introText	= null;
	private Handler			handler 	= new Handler();
	private boolean 		pausing 	= false;
	private Context			context		= this; 


	private class AttachToService implements Runnable
	{
		@Override
		public void run() 
		{
			if (!pausing)
			{
				if (null == WatchdogService.getSelf())
				{
					handler.postDelayed (new AttachToService(), 100);
				}
				else
				{
			        WatchdogService.registerBtStatusChangedReceiver (SelectorActivity.this);
			        
					Log.i(TAG, "AttachToService::run. Activity attached to Service callback.");
				}
			}
		}
	}
	
	
	private class SynchroTaskStatusChanged implements Runnable
	{
		private SelectedCallbackInterface	hostActivityInterface;
		private List<BtItem> 				listItems;
		private boolean 					deviceState;
		
		
		public SynchroTaskStatusChanged(SelectedCallbackInterface hostActivityInterface, boolean deviceState, List<BtItem> listItems)
		{
			this.deviceState			= deviceState;
			this.hostActivityInterface	= hostActivityInterface;
			this.listItems 				= listItems;
		}
		

		@Override
		public void run() 
		{
			if (null == listItems)
			{
				listItems = new ArrayList<BtItem>();
			}
			
			ItemAdapter customAdapter = new ItemAdapter(hostActivityInterface, listItems);
			listView.setAdapter(customAdapter);

			introText.setText(deviceState ? R.string.intro : R.string.noBt);
		}
	}
	
	
	@Override
    protected void onCreate(Bundle savedInstanceState) 
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_selector);
        
        Log.i(TAG, "onCreate. Success >>>>>>");
    }

	
    public void onResume()
    {
    	super.onResume();
    	
        if (null == listView)
        {
        	listView = (ListView)findViewById (R.id.listView);
        }
        
        
        if (null == introText)
        {
        	introText = (TextView)findViewById (R.id.textIntro);
        }


    	pausing = false;
		WatchdogService.loadService(context);

		attemptToRegisterCallbackReceiver();
    	
        Log.i(TAG, "onResume. Resumed.");
    }
    
    
	public void onPause()
    {
    	super.onPause();
    	
    	pausing = true;
    	
        WatchdogService.unregisterBtStatusChangedReceiver (this);
    	
    	Log.i(TAG, "onPause. Success!");
    }

    
    public void onDestroy()
    {
    	super.onDestroy();
    	
    	Log.i(TAG, "onDestroy. Success!");
    }
    

	public void onClickOK(View _)
    {
    	finish();
    }


    private void attemptToRegisterCallbackReceiver() 
    {
		handler.post(new AttachToService());		
	}


	@Override
	public Activity getActivity() 
	{
		return this;
	}

	
	@Override
	public void onSelectedClick(String address) 
	{
		WatchdogService.selectedItemStatusChanged (address);
	}


	@Override
	public void onBtListStatusChanged(boolean deviceState, List<BtItem> listItems) 
	{
        handler.post (new SynchroTaskStatusChanged(this, deviceState, listItems));

        if (deviceState)
		{
	        Log.i(TAG, String.format ("onBtListStatusChanged. Received [%d] items.", 
	        			listItems.size()));
	
		}
		else
		{
	        Log.i(TAG, "onBtListStatusChanged. No BT device active.");
		}
	}
}
