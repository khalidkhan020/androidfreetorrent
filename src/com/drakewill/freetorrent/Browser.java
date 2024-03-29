package com.drakewill.freetorrent;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.admob.android.ads.AdView;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class Browser extends ListActivity 
{
	private List<String> items = null; //Old
	private List<String> cargs = new ArrayList<String>();
    final ArrayList<View> mViews= new ArrayList<View>();
	/** Called when the activity is first created. */
    
    //DW -Added
    static final private int MENU_ITEM = Menu.FIRST;
    SharedPreferences readSettings;
	SharedPreferences.Editor writeSettings;
	ListView lv = null;
    
    public void onCreate(Bundle savedInstanceState) 
    {
    	super.onCreate(savedInstanceState);
    	
		//11-20-10 DW - Displaying ads at the bottom.
    	if (lv == null)
		{
    		ListView lv = this.getListView();
    		lv.addHeaderView(new AdView(this));
		}
    	
    	//DW 10-13-10 - This is a shoddy hack to kill background threads when the user hits EXIT on the menu.
    	readSettings = getSharedPreferences("FreeTorrent", Activity.MODE_PRIVATE);
    	writeSettings = readSettings.edit();
    	writeSettings.putBoolean("isRunning", true);
    	writeSettings.commit();
        
	    FileFilter filter = new FileFilter() 
	    { 
	    	public boolean accept(File pathname) 
	    	{
	    		//if pathname ends with .torrent, return true
	    		boolean myreturn=false;
	    		String thefile=pathname.toString();
	    		//Thought this would replace the below, guess not.
//	    		if (thefile.endsWith(".torrent"))
//	    		{
//	    			myreturn = true;
//	    		}
	    		//Old code, seems excessive
	            	String [] temp = thefile.split(".torrent");
					for (int i = 0 ; i < temp.length ; i++) 
					{
	              	  	if(temp[i]!=null)
	              	  	{
	              	  		myreturn = true;
	              	  	}
	              	  	else
	              	  	{
	              	  		myreturn=false;
	              	  	}
	                }
	            return myreturn;
	            } 
	        }; 
	        //File test=new File("/sdcard");
	        File test=Environment.getExternalStorageDirectory();
	      
	        if(test.canWrite())
	        {
	        	fill(test.listFiles(filter));
	        }
	        else
	        {
	        	Message acceptMsg = Message.obtain();
	        	new AlertDialog.Builder(this)
	            .setMessage(R.string.sd_error)
	            .setNeutralButton(R.string.ok, null)
	            .show();
	        }
	        
	        versionUpdateCheck();
    }
    
    //DW 10-13-10 - Get a basic menu and a couple of options in place.
    //These could be moved to their own layout later, but for a small number
    //of options I don't think it matters.
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
    	super.onCreateOptionsMenu(menu);
    	//DW 10-13-10 - This is a poor way to kill off background tasks, but it will have to do
    	//until I get a proper background setup in place.
    	writeSettings.putBoolean("isRunning", true);
    	
    	MenuItem menuItem = menu.add(0, MENU_ITEM, Menu.NONE, R.string.exit_menu).setIcon(R.drawable.exit);
    	MenuItem menuItem2 = menu.add(0, MENU_ITEM +1, Menu.NONE, R.string.help_menu).setIcon(R.drawable.help);
    	
		return true;
    }
    
    public boolean onOptionsItemSelected(MenuItem item)
    {
    	super.onOptionsItemSelected(item);
    	
    	switch(item.getItemId())
    	{
    	case (1): //Quit
    		//DW 10-13-10 - Part 2 of shoddy multithreading hack.
    		writeSettings.putBoolean("isRunning", false);
    		writeSettings.commit();
    		moveTaskToBack(true);
    		//finish();
    		//System.exit(0);
    		break;
    	case (2): //Help
    		showHelp();
    		break;
    	}
		return false;
    }
    
    public void showHelp()
    {   
		Dialog d = new Dialog(this);
		TextView tv = new TextView(this);
		tv.setPadding(5,5,5,5);
		tv.setGravity(Gravity.LEFT);
		tv.setText(R.string.help);
		Window w = d.getWindow();
		w.setFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND, WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
		d.setTitle(R.string.help_menu);
		d.setContentView(tv);
		d.show();
    }
   
    private void fill(File[] files) 
    {
    	if (files == null) //DW 10-18-10 - This was the cause of one error.
    	{
    		Dialog d = new Dialog(this);
    		TextView tv = new TextView(this);
    		tv.setPadding(5,5,5,5);
    		tv.setGravity(Gravity.LEFT);
    		tv.setText(R.string.empty);
    		//DW TODO - Move this (and other string) to be internationalized.
    		Window w = d.getWindow();
    		w.setFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND, WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
    		d.setTitle(R.string.empty);
    		d.setContentView(tv);
    		d.show();
    		return;
    	}
    		Arrays.sort(files); //DW 10-11-10 Gets the files in alphabetical order, not random order
	    items = new ArrayList<String>();
	    cargs = new ArrayList<String>();
	    items.add(getString(R.string.to_top));
		int x=0;  
		for( File file : files )
		{
			String filename=file.toString();
			int istorrent=istorrent(filename);
			//if this file isnt a torrent.. dont pass it
			if(istorrent==1)
			{
			    //String myname="";
			    cargs.add(file.getAbsolutePath());
			    items.add(getString(R.string.torrent_file) +" \n"+filename);
			    x++;
			}
			else
			{
				//Let's not show every file. Just .torrents and directories.
				if (file.isDirectory())
				{
					cargs.add(file.getPath());
					items.add(file.getPath());
				}
			}
		}

		ArrayAdapter<String> fileList = new ArrayAdapter<String>(this, R.layout.file_row, items);
		setListAdapter(fileList);
   }
    
   @Override
   protected void onListItemClick(ListView l, View v, int position, long id) 
   {
           int selectionRowID = (int)position;
           if (selectionRowID==1) //11-20 - Used to be 0, before the ad header was moved into the layout.
           {
               fillWithRoot();
           }
           else 
           {
               File file = new File(items.get(selectionRowID-1)); //11-20 added -1
               if (file.isDirectory())
                   fill(file.listFiles());
               else
               {
            	 //if this is a .torrent, open it!
                   if(selectionRowID!=0)
                   {
    	               final String thefile=cargs.get(selectionRowID-2); //11-20 was -1
    	               //System.out.println("Clicked on file "+thefile);
    	               int result=istorrent(thefile);
    	               //start new intent for downloading
    	               if(result==1)
    	               {
    	            	   //get the title from the torrent?
    	            	   //promt for download ?
    	            	   //DW 10-12-10 - What we should do is see if this is already active.
    	            	   //If so, display it's info, not restart the download as this currently does.
    	            	   Builder alert1 = new AlertDialog.Builder(this)
    	                   .setTitle(R.string.app_name)
    	                   .setMessage(R.string.confirm_dl)
    	                   .setPositiveButton("Yes", new DialogInterface.OnClickListener() 
    	                   {
    	                           public void onClick(DialogInterface dialog, int whichButton) 
    	                           {
    	                        	   try
    	                        	   {
    	                              //Put your code in here for a positive response
    	                        	  Intent i = new Intent();
    	         	             	  i.putExtra("torrent", thefile);
    	         	             	  //11-22-10 DW - Consider making this startActivityForResult(), and handing errors there.
    	         	             	  i.setClassName("com.drakewill.freetorrent", "com.drakewill.freetorrent.Freetorrent");
    	         	             	  startActivity(i);
    	                        	   }
    	                        	   catch (Exception ex)
    	                        	   {
    	                        		   //DW 10-20-10 TODO perform real error reporting.
    	                        		   //String s = ex.getMessage();
    	                        		   //s = s;
    	                        	   }
    	                           }
    	                   })
    	                   .setNegativeButton("No", new DialogInterface.OnClickListener() 
    	                   {
    	                           public void onClick(DialogInterface dialog, int whichButton) 
    	                           {
    	                                   //Put your code in here for a negative response
    	                           }
    	                   });
    	            	   
    	            	   alert1.show();
    	               }
                   }
               }
          }
      }
    
   
   private void fillWithRoot() 
   {
	   File file = Environment.getExternalStorageDirectory();
       fill(file.listFiles());
   }
    
   public int istorrent(String file)
   {
	   String searchFor = ".torrent";
	   String base = file;
       int len = searchFor.length();
       int result = 0;
       if (len > 0) 
       {  
    	   int start = base.indexOf(searchFor);
    	   while (start != -1) 
    	   {
                 result++;
                 start = base.indexOf(searchFor, start+len);
           }
        }
       return result;
   }
   
   private void versionUpdateCheck() 
	{
   	readSettings = getSharedPreferences("FreeTorrent", Activity.MODE_PRIVATE);
   	writeSettings = readSettings.edit();
   	
   	//DW - TODO - Update this each version.
   	String version = readSettings.getString("version", "0");
   	if (!version.equals("1.9.4"))
   	{
   		Dialog d = new Dialog(this);
   		TextView tv = new TextView(this);
   		tv.setPadding(5,5,5,5);
   		tv.setGravity(Gravity.LEFT);
   		tv.setText(R.string.update_info);
   		Window w = d.getWindow();
   		w.setFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND, WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
   		d.setTitle(R.string.help_menu);
   		d.setContentView(tv);
       	writeSettings.putString("version", "1.9.4");
       	writeSettings.commit();
   		d.show();
   	}

	}
   
}