package com.ogp.hotspotincar;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;


public class WatchdogService extends Service implements OnBtStatusChanged
{
	public enum ActuatorState {UNCHANGED, DISABLED, NOT_ACTUATED, ACTUATED};

	
	private static final 	String 			TAG 					= "WatchdogService";
	private static final 	String 			PERSISTANT_STORAGE 		= "WiFiHS4BT";

	public static final 	String 			ACTUATOR_UPDATE_STATUS 	= "update.status";
	public static final 	String 			ASK_UPDATE_STATUS 		= "ask.update.status";
	public static final 	String 			START_ACTIVITY 			= "selector.activity";
	public static final 	String 			ACTUATOR_STATE 			= "actuator.state";

	
	private static WatchdogService			self 				= null;
	
	private BluetoothAdapter 				bluetoothAdapter	= null;
	private BluetoothManager 				bluetoothManager	= null;
	
	private List<BtItem>					listBondedBtDevices = new ArrayList<BtItem>(); 
	private Method 							methodIsConnected;

	// Asynchronous processing
	private WorkerThread		 			workerThread 		= null;
	
	private static boolean 					deviceState			= false;
	
	// Outer interface
	private List<OnBtStatusChanged>			interfaceList		= new ArrayList<OnBtStatusChanged>();
	private Actuator						actuator			= null;
	
	
	
	private final BroadcastReceiver 		mReceiver 			= new BroadcastReceiver() 
	{
	    @Override
	    public void onReceive(Context context, Intent intent) 
	    {
	        String action = intent.getAction();
	        BluetoothDevice device = intent.getParcelableExtra (BluetoothDevice.EXTRA_DEVICE);

	        if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) 
	        {
                boolean btOn = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_ON;
                
                if (deviceState != btOn)
                {
                	Log.i(TAG, String.format("BroadcastReceiver::onReceive. Received <Bluetooth on/off status changed> Status [%s] now..",
                								btOn ? "on" : "off"));
                	
                	deviceState = btOn;
                	btDeviceStateChanged(deviceState);
                }
	        }
	        else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) 
	        {
	            Log.i(TAG, String.format("BroadcastReceiver::onReceive. Received <Connected> for [%s].",
	            						 device.getAddress()));

	            adjustListOfConnectedBtDevices (device, true);
	        }
	        else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) 
	        {
	            Log.i(TAG, String.format("BroadcastReceiver::onReceive. Received <Disconnected> for [%s].",
						 				 device.getAddress()));

	            adjustListOfConnectedBtDevices (device, false);
	        }           
	        else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) 
	        {
	            Log.i(TAG, String.format("BroadcastReceiver::onReceive. Received <BondStateChanged> for [%s]. New state: [%s]",
						 				 device.getAddress(), bondState2String (device.getBondState())));
	            
	            adjustListOfBondedBtDevices (device, BluetoothDevice.BOND_BONDED == device.getBondState());
	        }           
	        else if (ACTUATOR_UPDATE_STATUS.equals(action))
	        {
	            ActuatorState actuatorState = (ActuatorState)intent.getSerializableExtra(ACTUATOR_STATE);

	            Log.i(TAG, String.format("BroadcastReceiver::onReceive. Received <Update state = %s>.",
	            		actuatorState.name()));
	            
				HotspotWidget.updateWidgets(context, actuatorState);
	        }
	    }
	};

	
	private class WorkerThread extends Thread
	{
		private  Handler workerHandler;

		private class FillList implements Runnable
		{
			@Override
			public void run() 
			{
		 		int 					nSelected 		= 0;	
		 		Set<BluetoothDevice> 	pairedDevices 	= bluetoothAdapter.getBondedDevices();
			 		
		 		synchronized(listBondedBtDevices)
		 		{
		 			listBondedBtDevices.clear();
		 			
			 		if (pairedDevices.size() > 0) 
					{
						for (BluetoothDevice device : pairedDevices) 
						{
							String deviceBTName = device.getName();
							String deviceBTAddr = device.getAddress();
							
							boolean connected = isBtDeviceConnected (device);
							boolean selected  = readFromPersistantStorage (deviceBTAddr);
							
							listBondedBtDevices.add (new BtItem(deviceBTName, deviceBTAddr, selected, connected));
			
							if (selected)
							{
								nSelected++;
							}
					    }
					}
		 		}
		 		
				Log.i(TAG, String.format ("WorkerThread::FillList::run. Found [%d] devices. Selected for WFHP: [%d] devices.", 
										   pairedDevices.size(), nSelected));
				
				updateClients();
			}
		}

		
		private class BtConnectedChanged implements Runnable
		{
			private final BluetoothDevice 	device;
			private final boolean 			bConnected;
			
			
			public BtConnectedChanged(BluetoothDevice device, boolean bConnected)
			{
				this.device 	= device;
				this.bConnected = bConnected;
			}
			
			
			@Override
			public void run() 
			{
				boolean bSucceeded = false;
				
				synchronized(listBondedBtDevices)
				{
					for (BtItem btItem : listBondedBtDevices)
					{
						if (btItem.getAddress().equals(device.getAddress()))
						{
							btItem.setConnected (bConnected);
							bSucceeded = true;
							break;
						}
					}
				}
				
				if (bSucceeded)
				{
		            Log.i(TAG, String.format("WorkerThread::BtConnectedChanged::run. Marking the Bt device [%s] as %sconnected.",
							 device.getAddress(), bConnected ? "" : "dis"));
		            
					updateClients();
				}
				else
				{
		            Log.e(TAG, String.format("WorkerThread::BtConnectedChanged::run. Found no Bt device [%s]. Cannot mark.",
							 device.getAddress(), bConnected ? "" : "dis"));
				}
			}
		}
		
		
		private class BtBondedChanged implements Runnable
		{
			private final BluetoothDevice 	device;
			private final boolean 			bBonded;
			
			
			public BtBondedChanged(BluetoothDevice device, boolean bBonded)
			{
				this.device 	= device;
				this.bBonded 	= bBonded;
			}
			
			
			@Override
			public void run() 
			{
				if (!bBonded)
				{
					boolean bSucceeded = false;
					
					synchronized(listBondedBtDevices)
					{
						for (BtItem btItem : listBondedBtDevices)
						{
							if (btItem.getAddress().equals(device.getAddress()))
							{
								listBondedBtDevices.remove (btItem);
								
								bSucceeded = true;
								break;
							}
						}
					}

					if (bSucceeded)
					{
			            Log.i(TAG, String.format("WorkerThread::BtBondedChanged::run. Removed the Bt device [%s].",
       						 device.getAddress()));
			            
						updateClients();
					}
					else
					{
			            Log.e(TAG, String.format("WorkerThread::BtBondedChanged::run. Failed to remove the Bt device [%s]. Not found.",
			            							device.getAddress()));
					}
					return;
				}
				else
				{
					boolean bFound = false;

					synchronized(listBondedBtDevices)
					{
						for (BtItem btItem : listBondedBtDevices)
						{
							if (btItem.getAddress().equals(device.getAddress()))
							{
								bFound = true;
					            break;
							}
						}

						if (!bFound)
						{
							String deviceBTName = device.getName();
							String deviceBTAddr = device.getAddress();
							
							boolean connected = isBtDeviceConnected (device);
							boolean selected  = readFromPersistantStorage (deviceBTAddr);
							listBondedBtDevices.add (new BtItem(deviceBTName, deviceBTAddr, selected, connected));
						}
					}
					
					
					if (bFound)
					{
			            Log.e(TAG, String.format("WorkerThread::BtBondedChanged::run. Failed to add the Bt device [%s]. Already exists.",
        						device.getAddress()));

					}
					else
					{
						Log.i(TAG, String.format("WorkerThread::BtBondedChanged::run. Added the Bt device [%s].",
								 device.getAddress()));
					}
				}
			}
			
		}

		
		public WorkerThread()
		{
			super();
			
			start();
		}
		
		
		public void run()
		{
			Looper.prepare();
			workerHandler = new Handler(); 
			Looper.loop();
		}
		
		
		public void addFillListTask()
		{
			workerHandler.post (new FillList());
		}
		
		
		public void addConnectedTask(BluetoothDevice device, boolean bConnected)
		{
			workerHandler.post (new BtConnectedChanged(device, bConnected));
		}

		
		public void addBondedTask(BluetoothDevice device, boolean bBonded)
		{
			workerHandler.post (new BtBondedChanged(device, bBonded));
		}
	}
	
	
	@Override
	public IBinder onBind (Intent intent)
	{
		return null;
	}


    protected void btDeviceStateChanged(boolean deviceState2) 
    {
    	if (deviceState)
    	{
    		loadBtDevices();
    	}
    	else
    	{
			synchronized(listBondedBtDevices)
			{
				listBondedBtDevices.clear();
			}
			
    		updateClients();	
    	}
	}


	protected void loadBtDevices() 
    {
    	workerThread.addFillListTask();
	}

    
    protected void adjustListOfConnectedBtDevices(BluetoothDevice device, boolean bConnected) 
	{
		workerThread.addConnectedTask (device, bConnected);	
	}


	protected void adjustListOfBondedBtDevices(BluetoothDevice device, boolean bBonded) 
	{
		workerThread.addBondedTask (device, bBonded);
	}


	@Override
	public void onCreate()
	{
		super.onCreate();

		self = this;
		
        if (null == bluetoothAdapter)
        {
        	bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        }

        if (null == bluetoothManager)
        {
        	bluetoothManager = (BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
        }
        
        
        if (null == workerThread)
        {
        	workerThread = new WorkerThread();
        }
        
        try
        {
			Class<?> 		btDeviceInstance = Class.forName (BluetoothDevice.class.getCanonicalName());
			methodIsConnected = btDeviceInstance.getDeclaredMethod ("isConnected");
			methodIsConnected.setAccessible (true);
        }
        catch(Throwable th)
        {
			Log.e(TAG, "onCreate. Major problem: reflection failed for undocumented method 'isActive'.");
			
			Toast.makeText(getApplicationContext(), R.string.reflection_error, Toast.LENGTH_LONG).show();
        	return;
        }
				
        
        deviceState = bluetoothAdapter.getState() == BluetoothAdapter.STATE_ON; 

        
        registerReceiver (mReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        registerReceiver (mReceiver, new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED));
        registerReceiver (mReceiver, new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED));
        registerReceiver (mReceiver, new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED));
        registerReceiver (mReceiver, new IntentFilter(ACTUATOR_UPDATE_STATUS));
        
        registerBtStatusChangedReceiver(this);
        
        actuator = new Actuator(this);
        
    	btDeviceStateChanged (deviceState);

		Log.i(TAG, "onCreate. Success!");
	}


	@Override
	public void onDestroy()
	{
        unregisterReceiver (mReceiver);
        unregisterBtStatusChangedReceiver(this);

        actuator.stop();
        
        actuator 	 = null;
        workerThread = null;
		self 		 = null;
        
		super.onDestroy();
			
		Log.i(TAG, "onDestroy. Success!");
	}

	
	static boolean loadService (Context context)
	{
		try
		{
 			Intent intent = new Intent(context,
								   	   WatchdogService.class);

			context.startService (intent);
			Log.i(TAG, "loadService. Service started.");
			return true;
		}
		catch(Exception e)
		{
			e.printStackTrace();
			Log.e(TAG, "loadService. Major problem: exception!");
			return false;
		}
	}

	
	public static void bootFinished(Context context) 
	{
		Log.w(TAG, "bootFinished. Initiating...");

		loadService (context);
	}


	private boolean isBtDeviceConnected(BluetoothDevice device) 
	{
// !!! Here is the place where Tigers eat you alive !!!		
// TODO: Find a 'documented' way to do it. 		
		try
		{
			Boolean isConnected = (Boolean)(methodIsConnected.invoke(device));
			
			Log.i(TAG, String.format("isBtDeviceConnected. Device [%s] is %sconnected.", 
										device.getAddress(), isConnected ? "" : "dis"));
			return isConnected;
		}
		catch(Throwable th)
		{
			Log.e(TAG, "isBtDeviceConnected. Major problem: reflection failed for undocumented method 'isConnected'.");
			return false;
		}
			
	}

	
	private boolean readFromPersistantStorage (String address) 
	{
		SharedPreferences pref = getSharedPreferences (PERSISTANT_STORAGE, 
				  									   MODE_PRIVATE);
		
		return pref.getBoolean 	(address, false);
	}

	
	private void writeToPersistantStorage (String address, boolean selected) 
	{
		SharedPreferences pref = getSharedPreferences (PERSISTANT_STORAGE, 
													   MODE_PRIVATE);
		
		Editor editor = pref.edit();
		
		editor.putBoolean (address, selected);
		editor.commit();
	}

	
	private String bondState2String(int bondState)
	{
		if (BluetoothDevice.BOND_BONDED  == bondState)	return "Bonded";
		if (BluetoothDevice.BOND_NONE    == bondState)	return "Not bonded";
		if (BluetoothDevice.BOND_BONDING == bondState)	return "Bonding";
		
		return "Unknown";
	}
	

	private void updateClients()
	{
		if (interfaceList.isEmpty())
		{
			Log.i(TAG, "updateClients. Callback list is empty.");
			return;
		}
		
		List<BtItem> list = null;
		
		try
		{
			synchronized(listBondedBtDevices)
			{
				list = new ArrayList<BtItem>(listBondedBtDevices);
			}
		} 
		catch(Throwable th)
		{
			Log.e(TAG, "updateClients. Major error. No active service encountered!");
			th.printStackTrace();
			return;
		}

		
		for (OnBtStatusChanged interfaceItem : interfaceList)
		{
			interfaceItem.onBtListStatusChanged (deviceState, list);	
		}

		Log.i(TAG, "updateClients. Callback list processed.");
	}
	
	
	private void updateClient (OnBtStatusChanged receiver)
	{
		List<BtItem> list = null;
		
		try
		{
			synchronized(listBondedBtDevices)
			{
				list = new ArrayList<BtItem>(listBondedBtDevices);
			}
		} 
		catch(Throwable th)
		{
			Log.e(TAG, "updateClient. Major error. No active service encountered!");
			th.printStackTrace();
			return;
		}

		receiver.onBtListStatusChanged (deviceState, list);	
		Log.i(TAG, "updateClient. Single callback processed.");
	}

	
	public static boolean registerBtStatusChangedReceiver(OnBtStatusChanged receiver)
	{
		try
		{
			synchronized(self.interfaceList)
			{
				if (self.interfaceList.contains(receiver))
				{
					Log.w(TAG, "registerBtStatusChangedReceiver. Cannot add the receiver. Already exists.");
					return false;
				}
				
				self.interfaceList.add(receiver);
			}
			
			self.updateClient (receiver);
			
			Log.i(TAG, "registerBtStatusChangedReceiver. Added receiver.");
			return true;
		}
		catch(Throwable th)
		{
			Log.e(TAG, "registerBtStatusChangedReceiver. Major problem: exception!");
			th.printStackTrace();
		}

		return false;
	}


	public static boolean unregisterBtStatusChangedReceiver(OnBtStatusChanged receiver)
	{
		try
		{
			synchronized(self.interfaceList)
			{
				if (self.interfaceList.contains(receiver))
				{
					self.interfaceList.remove(receiver);
					Log.i(TAG, "unregisterBtStatusChangedReceiver. Removed receiver.");
					return true;
				}
			}
				
			Log.w(TAG, "unregisterBtStatusChangedReceiver. Cannot remove the receiver. Not found.");
			return true;
		}
		catch(Throwable th)
		{
			Log.e(TAG, "unregisterBtStatusChangedReceiver. Major problem: exception!");
			th.printStackTrace();
		}

		return false;
	}
	
	
	public static void selectedItemStatusChanged (String address)
	{
		try
		{
			synchronized(self.listBondedBtDevices)
			{
				for (BtItem btItem : self.listBondedBtDevices)
				{
					if (btItem.getAddress().equals(address))
					{
						btItem.onSelectedClick();
						
						Log.i(TAG, String.format("selectedItemStatusChanged. Found BtItem. New state is [%s].", 
											btItem.getSelected()));

						self.writeToPersistantStorage (address, btItem.getSelected());
						self.updateClients();
						return;
					}
				}
			}
				
			Log.e(TAG, "selectedItemStatusChanged. Cannot change the Item state. Not found.");
		}
		catch(Throwable th)
		{
			Log.e(TAG, "selectedItemStatusChanged. Major problem: exception!");
			th.printStackTrace();
		}
	}


	public static WatchdogService getSelf()
	{
		return self;
	}
	
	
	@Override
	public void onBtListStatusChanged(boolean deviceState, List<BtItem> listItems) 
	{
        Log.v(TAG, String.format ("onBtListStatusChanged. Received [%d] items. Not used in Service.", 
    			listItems.size()));
	}
}
