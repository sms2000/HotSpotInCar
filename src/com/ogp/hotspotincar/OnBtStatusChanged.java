package com.ogp.hotspotincar;

import java.util.List;

public interface OnBtStatusChanged
{
	public void onBtListStatusChanged 				(boolean deviceState, List<BtItem> listItems);
}
