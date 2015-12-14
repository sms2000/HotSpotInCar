package com.ogp.hotspotincar;

import java.util.List;

import android.annotation.SuppressLint;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;


public class ItemAdapter extends ArrayAdapter<BtItem>
{
	private List<BtItem>				items;
	private SelectedCallbackInterface	activityInterface;		
	
	
	public ItemAdapter(SelectedCallbackInterface activityInterface, List<BtItem> items) 
	{
		super(activityInterface.getActivity(), R.layout.item_select, items);
		
		this.items 				= items;
		this.activityInterface 	= activityInterface;
	}

	
	@SuppressLint("InflateParams")
	@Override
	public View getView (final int position, final View convertView, final ViewGroup parent) 
	{
		final BtItem item = items.get (position);
		
		if (null == item.getView())
		{
			item.setView (activityInterface.getActivity().getLayoutInflater().inflate(R.layout.item_select, null));
		}
		
		final View   view = item.getView();

		((TextView)view.findViewById (R.id.text1)).setText (item.getName());
		((TextView)view.findViewById (R.id.text2)).setText (item.getAddress());
		
		((ImageView)view.findViewById (R.id.connected)).setImageResource(item.getConnected() ? R.drawable.connected : R.drawable.disconnected);

		CheckBox cb = ((CheckBox)view.findViewById (R.id.checkbox));
		cb.setChecked (item.getSelected());
		cb.setOnClickListener(new OnClickListener()
					{
						@Override
						public void onClick(View view) 
						{
							activityInterface.onSelectedClick ((String)view.getTag());
						}
					});
		
		cb.setTag (item.getAddress());
		return view;
	}
}
