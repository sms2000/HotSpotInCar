package com.ogp.hotspotincar;

import android.view.View;


public class BtItem 
{
	private String 			name;
	private String 			address;
	private boolean 		bSelected;
	private boolean 		bConnected;
	private View			view;
	
	
	public BtItem(String name, String address, boolean bChecked, boolean bConnected)
	{
		this.name 		= name;
		this.address	= address;
		this.bSelected 	= bChecked;
		this.bConnected	= bConnected;
		this.view		= null;
	}

	
	public void onSelectedClick()
	{
		bSelected = !bSelected;
	}
	
	
	public void setConnected (boolean connected)
	{
		bConnected = connected;
	}


	public void setSelected (boolean selected)
	{
		bSelected = selected;
	}

	
	public boolean getSelected() 
	{
		return bSelected;
	}

	
	public boolean getConnected() 
	{
		return bConnected;
	}


	public CharSequence getAddress() 
	{
		return address;
	}


	public CharSequence getName() 
	{
		return name;
	}
	
	
	public View getView()
	{
		return view;
	}


	public void setView(View view) 
	{
		this.view = view;
	}
}
