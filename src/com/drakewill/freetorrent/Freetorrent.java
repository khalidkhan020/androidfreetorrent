package com.drakewill.freetorrent;
//FreeTorrent - Android Bittorrent App - 2010 Drake Williams
//Derived from the code for AndTor/AndroidTorrent, 2009 Brian Hull
//Which was derived from bitext, a Java Bittorrent library
//Full GPL license text is available in License.txt

import android.app.Activity;
import android.app.Dialog;
import android.app.TabActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TabHost;
import android.widget.TextView;

import com.admob.android.ads.AdManager;

public class Freetorrent extends TabActivity 
{
	//DW 10-31-10 - Memory seems to be OK, at least on smaller torrents, still testing.
	//The issue now is that connections seems to die off after a few minutes and I don't make
	//any new ones. This probaby isn't too hard to fix, though I need to figure out what to call
	//to start new connections. Possibly related to mis-handling currentOpenPieces
	
	public Bundle extras;
	SharedPreferences readSettings;
	SharedPreferences.Editor writeSettings;
	
	//DW 10-18-10 TODO: Move some variables here, so that I don't recreate everything
	//on every Intent call.
	
	//DW -Added these
	public TabHost tabHost;
	//NOTE: It's not very good design to use Activities as tab contents, even though it's possible.
	//It's really hard to make them communicate effectively this way. I will eventually have to change this
	//to use views that change and make sure that the code ends up changing tabs properly.
    
	
	//DW 10-16-10 - Added @Override, not sure if it changes anything
	@Override
    public void onCreate(Bundle savedInstanceState) 
    {
    	super.onCreate(savedInstanceState);
    	AdManager.setTestDevices( new String[] { "20CE55B1D897938EC1408485F0F97300" } ); //Dev's EVO
    	
    	//DW 10-18-10 - Adding version update info.
    	versionUpdateCheck();
    	
    	//DW 10-18-10 - We need to check our intent more closely now
    	Intent me = getIntent();
    	extras = me.getExtras();
    	Uri	torrent=getIntent().getData();
    	
    	//DW 11-2-10 - Adding this, helps wrangle the download into the TabHost some.
    	if (torrent == null && extras != null)
    		torrent = Uri.parse(extras.getString("torrent"));
    
    	setContentView(R.layout.maintabs);
		Resources res = getResources(); // Resource object to get Drawables
        tabHost = getTabHost();  // The activity TabHost
        TabHost.TabSpec spec;  // Resusable TabSpec for each tab
        Intent intent;  // Reusable Intent for each tab
        
     // Create an Intent to launch an Activity for the tab (to be reused)
        intent = new Intent().setClass(this, Browser.class);
     // Initialize a TabSpec for each tab and add it to the TabHost
        spec = tabHost.newTabSpec("SelectUser").setIndicator("Browse Files",res.getDrawable(R.drawable.folder))
        		    .setContent(intent);
        tabHost.addTab(spec);

        // Do the same for the other tabs
        intent = new Intent().setClass(this, GetDownload.class);
        
        //DW 11-2-10 - Moving this here to keep the download tab from being lost.
        //if this is from the listener thing
    	if(torrent!=null)
    	{
    		String thetorrent=torrent.toString();
    		thetorrent=thetorrent.replace("file://","");
    		intent = new Intent();
    		intent.putExtra("torrent", thetorrent);
            
    	}
    	else
    	{
    		tabHost.setCurrentTab(0);    		
    	}

    	intent.setClassName("com.drakewill.freetorrent", "com.drakewill.freetorrent.GetDownload");
        spec = tabHost.newTabSpec("AddUser").setIndicator("Current Download", res.getDrawable(R.drawable.download)).setContent(intent);
        tabHost.addTab(spec);
        
        if (torrent != null)
        {
        	tabHost.setCurrentTab(1);
        }
        else
        {
        	tabHost.setCurrentTab(0);
        }
        	
    }
	
	private void versionUpdateCheck() 
	{
    	readSettings = getSharedPreferences("FreeTorrent", Activity.MODE_PRIVATE);
    	writeSettings = readSettings.edit();
    	
    	//DW - TODO - Update this each version.
    	String version = readSettings.getString("version", "0");
    	if (!version.equals("1.7"))
    	{
    		Dialog d = new Dialog(this);
    		TextView tv = new TextView(this);
    		tv.setPadding(5,5,5,5);
    		tv.setGravity(Gravity.LEFT);
    		tv.setText("Version 1.7 changes:" +
    				"\n\t * Compatibility has been limited to Android 2.1 or newer for now." +
    				"\n\t * Removed the lingering notification after cancelling a download." +
    				"\n\t * Started UI improvements. You can now change tabs and continue to track your torrent while FreeTorrent has focus." +
    				"\n\t * Significant improvements in memory optimiziation were made." +
    				"\n\t * This also increases stabiilty, but download speed may feel throttled for now." +
    				"/n\n\t Thanks to all of you, for sticking with me as I develop FreeTorrent!."); 
    		//DW TODO - Move this (and other string) to be internationalized.
    		Window w = d.getWindow();
    		w.setFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND, WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
    		d.setTitle(R.string.help_menu);
    		d.setContentView(tv);
        	writeSettings.putString("version", "1.7");
        	writeSettings.commit();
    		d.show();
    	}

	}   
}

