package com.drakewill.freetorrent;

//Receiver.java, copyright 2010 Drake Williams
//Released under the terms of the GNU GPL

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;

public class Receiver extends BroadcastReceiver 
{
	@Override
	public void onReceive(Context context, Intent intent) 
	{
		
		Bundle extra = intent.getExtras();
		NetworkInfo ni = (NetworkInfo) extra.get("networkInfo");
		
		Intent message = new Intent("com.drakewill.freetorrent.GetDownload");		
		if (ni.getState() == NetworkInfo.State.DISCONNECTED)
		{
			message.putExtra("ConnectionPresent", false);
		}
		else if (ni.getState() == NetworkInfo.State.CONNECTED)
		{
			message.putExtra("ConnectionPresent", true);
		}
		
		//context.startActivity(message);
		//Android doesn't support this, at least not without the FLAG_ACTIVITY_NEW_TASK flag, and I don't think that'll work with the singleInstance I'm running.
	}

}
