package com.ogp.hotspotincar;

import java.util.ArrayList;
import java.util.List;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ToggleButton;


public class SelectorActivity extends Activity implements SelectedCallbackInterface, OnBtStatusChanged 
{
	private static final 	String 			TAG 				= "SelectorActivity";

	private boolean 			attemptedActivation = false; 
	private ListView			listView 			= null;
	private TextView			introText			= null;
	private Handler				handler 			= new Handler();
	private boolean 			pausing 			= false;
	private Context				context				= this;
	private ToggleButton 		btButton;
	
	
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
			
			btOnOffChanged (false, deviceState);
		}
	}
	
	
	@Override
    protected void onCreate(Bundle savedInstanceState) 
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_selector);
        
        btButton = (ToggleButton)findViewById(R.id.btOnOff);
        btButton.setOnCheckedChangeListener(new OnCheckedChangeListener() 
        {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) 
			{
				btOnOffChanged (true, isChecked);
			}
        });
        
        
        Log.i(TAG, "onCreate. Success >>>>>>");
    }

	
    @TargetApi(23)
	public void onResume()
    {
    	super.onResume();
    	
		activateWriteSettings();
		
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

	
	@TargetApi(23)
	private void activateWriteSettings() 
	{
		if (attemptedActivation)
		{
			return;
		}
		
		attemptedActivation = true;

		try
		{
			if (Settings.System.canWrite(context))
			{
				return;
			}
		}
		catch(NoSuchMethodError _)
		{
			return;
		}
		
		
		AlertDialog.Builder dialog = new AlertDialog.Builder(this);
		dialog.setTitle (R.string.request);
		dialog.setMessage (R.string.request_activate_settings);
		dialog.setPositiveButton (R.string.yes, 
								  new DialogInterface.OnClickListener() 
		{
			public void onClick (DialogInterface 	dialog, 
								 int 				id) 
			{
				dialog.cancel();

				Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS);
				intent.setData(Uri.parse("package:" + context.getPackageName()));
		        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

		        try 
		        {
		            context.startActivity(intent);
		        } 
		        catch (Exception e) 
		        {
		        	e.printStackTrace();
		        }		
			}
		});

		dialog.setNegativeButton (R.string.no, 
								  new DialogInterface.OnClickListener() 
		{
			public void onClick (DialogInterface 	dialog, 
								 int 				id) 
			{
				dialog.cancel();
			}
		});

		dialog.show();
	}
	
	
	private void btOnOffChanged (boolean manually, boolean isChecked)
	{
		if (manually)
		{
			WatchdogService.btOnOffStatusChanged (isChecked);
		}
		else
		{
			btButton.setChecked (isChecked);
		}
	}
}
