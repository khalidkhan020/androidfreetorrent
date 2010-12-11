package com.drakewill.freetorrent;
//FreeTorrent - Android Bittorrent App - 2010 Drake Williams
//Derived from the code for AndTor/AndroidTorrent, 2009 Brian Hull
//Which was derived from bitext, a Java Bittorrent library
//Full GPL license text is available in License.txt

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

import com.admob.android.ads.AdManager;

public class Freetorrent extends Activity 
{
	//DW 10-31-10 - Memory seems to be OK, at least on smaller torrents, still testing.
	//The issue now is that connections seems to die off after a few minutes and I don't make
	//any new ones. This probaby isn't too hard to fix, though I need to figure out what to call
	//to start new connections. Possibly related to mis-handling currentOpenPieces
	
	//DW 11-20-10 - The tab view is causing more issues than it should be fixing. I'm going to ditch it.
	//Switched to a normal Activity.
	
	public Bundle extras;
	SharedPreferences readSettings;
	SharedPreferences.Editor writeSettings;
	
	boolean torrentRunning = false;
    
	@Override
    public void onCreate(Bundle savedInstanceState) 
    {
    	super.onCreate(savedInstanceState);
    	AdManager.setTestDevices( new String[] { "20CE55B1D897938EC1408485F0F97300" } ); //Dev's EVO
    	
    	//DW 10-18-10 - We need to check our intent more closely now
    	Intent me = getIntent();
    	extras = me.getExtras();
    	Uri	torrent=getIntent().getData();
    	
    	if (torrent == null && extras != null)
    		torrent = Uri.parse(extras.getString("torrent"));
    
		Intent intent;
        
        if (torrent == null)
        {
        	intent = new Intent().setClass(this, Browser.class);
        	startActivity(intent);
        }
        else
        {
        	intent = new Intent().setClass(this, GetDownload.class);
        	intent.putExtra("torrent", torrent.toString().replace("file://",""));
        	startActivityForResult(intent, 1);
        }   
    }
	
	@Override
	public void onNewIntent(Intent i)
	{
    	extras = i.getExtras();
    	Uri	torrent=getIntent().getData();
    	
    	if (torrent == null && extras != null)
    		torrent = Uri.parse(extras.getString("torrent"));
    
		Intent intent;
        
        if (torrent == null && torrentRunning == false)
          	intent = new Intent().setClass(this, Browser.class);
        else
        {
        	intent = new Intent().setClass(this, GetDownload.class);
        	if (torrent != null)
        		intent.putExtra("torrent", torrent.toString().replace("file://",""));
        	torrentRunning = true;
        }
        
        startActivity(intent);
        moveTaskToBack(true);
	}
	
	@Override
	public void onActivityResult(int reqCode, int resCode, Intent data)
	{
		super.onActivityResult(reqCode, resCode, data);
		
		//Currently only 1 reqCode avaiable.
		if (resCode == Activity.RESULT_CANCELED)
			torrentRunning=false;
	}
	
	   
}

