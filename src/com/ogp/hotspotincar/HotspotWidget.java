package com.ogp.hotspotincar;


import com.ogp.hotspotincar.WatchdogService.ActuatorState;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.widget.RemoteViews;


public class HotspotWidget extends AppWidgetProvider 
{
    private static final String TAG 				= "HotspotWidget";

    private static 		ActuatorState storedState	= ActuatorState.UNCHANGED;
	

    @Override
    public void onUpdate (Context 			context, 
    					  AppWidgetManager 	appWidgetManager,
    					  int[] 			appWidgetIds) 
    {
    	if (null == appWidgetIds) 
    	{
            appWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(context, HotspotWidget.class));
        }

        for (int widgetId : appWidgetIds) 
        {
        	createWidgetView(context, widgetId, ActuatorState.UNCHANGED);
        }    	

        Log.i(TAG, String.format ("onUpdate. Rearranged [%d] widget(s).",
        							appWidgetIds.length));
    }

    
    @Override
    public void onDisabled(Context context)
    {
    	super.onDisabled (context);
    	
    	int[] appWidgetIds = AppWidgetManager.getInstance(context).getAppWidgetIds(new ComponentName(context, HotspotWidget.class));

        if (0 != appWidgetIds.length) 
        {
            AppWidgetHost host = new AppWidgetHost(context, 0);

            for (int widgetId : appWidgetIds) 
            {
                host.deleteAppWidgetId(widgetId);
            }   
        }
        
        Log.i(TAG, String.format ("onDisabled. Removed [%d] widget(s).",
        				appWidgetIds.length));
    }

    
	public static void updateWidgets (Context context, ActuatorState actuatorState) 
	{
		AppWidgetManager widgetManager = AppWidgetManager.getInstance(context);

		int[] appWidgetIds = widgetManager.getAppWidgetIds(new ComponentName(context, HotspotWidget.class));

        if (0 != appWidgetIds.length) 
        {
            for (int widgetId : appWidgetIds) 
            {
            	createWidgetView (context, widgetId, actuatorState);
            }            	
        }

        Log.i(TAG, String.format("updateWidgets. Updated [%d] widget(s).", 
        		appWidgetIds.length));
	}

	
	@SuppressLint("NewApi")
	private static void createWidgetView (Context context, int widgetId, ActuatorState actuatorState)
    {
    	RemoteViews updateViews = new RemoteViews(context.getPackageName(), 
				  								  R.layout.widget_layout);
    	
    	@SuppressWarnings("deprecation")
		Drawable drawable = context.getResources().getDrawable(getResIdByStatus(actuatorState));
        Bitmap 	 bitmap	  = ((BitmapDrawable)drawable).getBitmap();

    	updateViews.setImageViewBitmap (R.id.bitmap, 
    								    bitmap);

    	Intent intent = new Intent(context.getApplicationContext(), SelectorActivity.class);
    	PendingIntent pendingIntent = PendingIntent.getActivity (context, 
        														 0,
        														 intent,
        														 0);

        updateViews.setOnClickPendingIntent (R.id.widget,
        									 pendingIntent);
        
    	
        ComponentName thisWidget = new ComponentName(context, 
        								HotspotWidget.class);

        AppWidgetManager manager = AppWidgetManager.getInstance (context);
        manager.updateAppWidget (thisWidget, 
        						 updateViews);
	}

	
	private static int getResIdByStatus(ActuatorState actuatorState) 
	{
		if (ActuatorState.UNCHANGED == actuatorState)
		{
			actuatorState = storedState;
		}
		else
		{ 
			storedState = actuatorState; 
		}
		
		switch(actuatorState)
		{
		case NOT_ACTUATED: 	return R.drawable.disconnected;
		case ACTUATED: 		return R.drawable.connected;
		default: 			return R.drawable.disabled;
		}
	}
}
