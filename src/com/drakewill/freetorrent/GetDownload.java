package com.drakewill.freetorrent;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Random;

import android.app.Activity;
import android.app.ActivityGroup;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.StatFs;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RemoteViews;
import android.widget.TextView;
import android.widget.Toast;
import atorrentapi.Constants;
import atorrentapi.DownloadManager;
import atorrentapi.TorrentFile;
import atorrentapi.TorrentProcessor;
import atorrentapi.Utils;

//DW 10-12-10 - Used to be an Activity
public class GetDownload extends ActivityGroup implements Runnable 
{
	private ProgressBar mProgress;
	private RandomAccessFile[] output_files;
	private float TOTAL = 0;
	private int PEERS;
	private int PIECES = 0;
	private int COMPLETEDPIECES = 0;
	private static final int MEGABYTE = 1024 * 1024;
	private TorrentFile t;
	public DownloadManager dm; //10-22-10 TODO: consider making this static
	private Button closebutton;
	public int dlcontinue = 1; //DW 10-15-10 Was 0, let's try enabled.
	public int n;
	
	//DW added
	private int TotalPeers = 0;
	NotificationManager nm;
	static int TorrentCount = 0;
	PowerManager PM;
	SharedPreferences readSettings;
	private boolean initializingFiles;
	private float percentInitialized;
	private int filesInitialized;
	private boolean Seeding = false;
	private int pieceNum = 0;
	private int totalPieces = 0;
	private boolean checkingPieces = false;
	private Notification update_status_bar;
	private boolean downloadOK = true; //Is it OK to download?

	@Override
	public void onCreate(Bundle savedInstanceState) 
	{
		// need to override the saved instance so they come back here until the download is done.
		//DW - what did he mean by this? 
		try
		{
		super.onCreate(savedInstanceState);
		nm = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
		PM = (PowerManager)getSystemService(Context.POWER_SERVICE);
		readSettings = getSharedPreferences("FreeTorrent", Activity.MODE_PRIVATE);
		
		//DW 10-17-10 TODO - Check to make sure the SD Card is mounted before trying to get info on it
		}
		catch (Exception ex)
		{
			Log.e("FreeTorrent", "GetDownload - " + ex.getMessage());
		}
		
		//DW - Moving all local declaration here for clarity
		String thetorrent = null;
		boolean exists = false;
		Bundle extras = getIntent().getExtras();
		TorrentProcessor tp = new TorrentProcessor();
		final long total_length;
		ArrayList<?> name;
		long megs;
		File path = Environment.getExternalStorageDirectory();
		StatFs stat = new StatFs(path.getPath());
		long freeSpace = 0; 
		Constants.SAVEPATH = path + "/FreeTorrent/";
		boolean sufficientFreeSpace = true;
		Toast toast;
		boolean SDCardSane = true;
		
		//DW 10-18-10 - We need to check our intent more closely now
    	Intent me = getIntent();
    	extras = me.getExtras();
    	
    	//DW 10-18-10 - Intent check
    	//10-22-10 TODO - these probably cause null pointer exceptions, since DM isnt likely initialized correctly
    	if (me.getAction() == Intent.ACTION_MEDIA_BAD_REMOVAL ||
    			me.getAction() == Intent.ACTION_MEDIA_EJECT ||
    			me.getAction() == Intent.ACTION_MEDIA_REMOVED ||
    			me.getAction() == Intent.ACTION_MEDIA_SHARED ||
    			me.getAction() == Intent.ACTION_MEDIA_UNMOUNTED ||
    			me.getAction() == Intent.ACTION_MEDIA_UNMOUNTABLE)
    	{
    		//We need to stop writing to the SD card and pause/stop activities for now.
    		dm.killWrites();
    	}
    	else if (me.getAction() == Intent.ACTION_MEDIA_MOUNTED)
    	{
    		//We can start or restart any activities now.
    		dm.resumeWrites();
    	}
		
		//This is a workaround for long = (int * int) overflowing and returning invalid values
		freeSpace = stat.getBlockSize();
		freeSpace = freeSpace * stat.getAvailableBlocks();
		
		// get torrent file from intent
		if (extras != null) {
			thetorrent = extras.getString("torrent");
		}
		
		if (thetorrent != null) 
		{
			File file = new File(thetorrent);
			exists = file.exists();
		}
		if (!exists) 
		{
			//change view
			setContentView(R.layout.error);
			
			TextView text = (TextView) findViewById(R.id.TextView01);// header
			text.setText("No file selected. You can download a .torrent file from the web and then open it from the SDCard, or the browsers download manager. All downloads will be in the FreeTorrent folder on your SDCard.");
		} 
		else 
		{
			setContentView(R.layout.main2);
			TextView text = (TextView) findViewById(R.id.TextView01);// header
			TextView text2 = (TextView) findViewById(R.id.TextView02);// filename
			closebutton = (Button) this.findViewById(R.id.Button01);
			text.setText("File Size:");

			//DW 10-13-10 - This causes a few seconds of lag. Check into optimizing this.
			t = tp.getTorrentFile(tp.parseTorrent(thetorrent));
			
			//DW 11-22-10 - Forgot to check this for null returns. Should have noticed this earlier.
			if (t == null)
			{
				//11-22-10 DW - I thought I put this block into a function somewhere. Can't find it.
				Dialog d = new Dialog(this);
		   		TextView tv = new TextView(this);
		   		tv.setPadding(5,5,5,5);
		   		tv.setGravity(Gravity.LEFT);
		   		tv.setText("Error: Invalid torrent file!");
		   		//DW TODO - Move this (and other string) to be internationalized.
		   		Window w = d.getWindow();
		   		w.setFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND, WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
		   		d.setTitle("Error");
		   		d.setContentView(tv);
		   		d.show();
				
				return ;
			}

			// here's where we get the data
			total_length = t.total_length;
			name = t.name;

			megs = total_length / MEGABYTE;
			
			//DW 10/11/10 - My first change: Making sure we have the free space to download the torrent
			if ( freeSpace < t.total_length) //Compare against actual sizes, not MB size
			{
				text.setTextColor(android.graphics.Color.RED);
				closebutton.setEnabled(false);
				sufficientFreeSpace = false;
				
				toast = Toast.makeText(this, "Not enough free space on SD card", Toast.LENGTH_SHORT);
				toast.show();
			}
			
			
			text.setText("Filesize: " + megs + "Mb [Free space: " +  (freeSpace / MEGABYTE)  + "Mb]");
			int namecount = name.size();

			text2.setText("Downloading : " + namecount + " files\n \n");

			mProgress = (ProgressBar) findViewById(R.id.ProgressBar);

			//DW 10-19-10 - Adding check for SD card sanity
			if(!android.os.Environment.getExternalStorageState().equals(android.os.Environment.MEDIA_MOUNTED))
			{
				text.setTextColor(android.graphics.Color.RED);
				closebutton.setEnabled(false);
				SDCardSane = false;
				
				toast = Toast.makeText(this, "No SD Card mounted", Toast.LENGTH_SHORT);
				toast.show();
			}

			//DW 10/11/10 - Here is where we should stop if there's insufficient space
			if (sufficientFreeSpace && SDCardSane)
			{
			final Thread downloadthread = new Thread(this);
			downloadthread.start();
			// System.out.println("Thread should be started now");

			closebutton.setOnClickListener(new OnClickListener() 
			{
				public void onClick(View v) 
				{
					//DW 11-20-10 - If 0 pieces are downloaded, delete the files.
					if (COMPLETEDPIECES == 0)
					{
						if (dm.output_files.length == 1)
						{
							try 
							{
								dm.output_files[0].close();
								File f = new File(Constants.SAVEPATH + ((String) (dm.torrent.name.get(0))));
								f.delete();
							} 
							catch (IOException e) 
							{
								// TODO Auto-generated catch block
								//e.printStackTrace();
							}
						}
						else
						{
							for (int i = 0; i < dm.output_files.length; i++) 
							{
								try 
								{
									dm.output_files[i].close();
									File f = new File(Constants.SAVEPATH + "/" + ((String) (dm.torrent.name.get(i))));
									f.delete();
								} 
								catch (IOException e) 
								{
									// TODO Auto-generated catch block
									//e.printStackTrace();
								}
							}
						}
					}
					
					nm.cancel(0);
					System.exit(1);

					// exit and return to tab 0 ?

				}
			});
			}//if sufficientfreespace

		}// end else if for card check //DW What card check?

	}// end function on create

	public void run() 
	{
		//DW 10-12-10 - Adding notification item. Will also show me if concurrent downloads work (They do, but aren't user accessible)
		update_status_bar = new Notification(R.drawable.icon, "Torrent started", System.currentTimeMillis());
		update_status_bar.flags = update_status_bar.flags | Notification.FLAG_ONGOING_EVENT;
		update_status_bar.contentView = new RemoteViews(this.getPackageName(), R.layout.notification_layout);
		//int notification_number = 0;
		//Intent intent = new Intent(this, GetDownload.class);
		Intent intent = new Intent(this, Freetorrent.class); //Show the maintabs view, not just GetDownload
		PendingIntent pIntent = PendingIntent.getActivity(this, 0, intent, 0);
		update_status_bar.contentIntent = pIntent;
		nm.notify(0, update_status_bar);
		boolean Finished = false; //DW 11-2-10
		
		
		//Power Management
		final PowerManager.WakeLock wl = PM.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FreeTorrent");
		wl.acquire();
		Looper.prepare();
		
		//DW 10-13-10 - This runs both the creation of new files (if not present)
		//and checking the files for existing pieces downloaded. It takes a very 
		//long time, and doesn't give any feedback until we start downloading
		//11-22-10 TODO: This is faster, but needs more feedback.
		dm = new DownloadManager(t, Utils.generateID(), dlcontinue);
		
		//10-22-10 - Fixing a crash introduced in 1.5 by calling warnUser in DownloadManager instead of GetDownload.
		//We need a GUI thread and to have called onCreate() to do that correctly.
		if (dm.warnUser == true)
			warnUser("This torrent uses large sized pieces, which may run slowly or crash FreeTorrent. FreeTorrent will still attempt to download it.");
		
		if (downloadOK == false)
		{
			//cancel downloading, user decided against it.
			dm = null;
			return;			
		}
		
		//DW 10-16-10 - moved file setup out of DownloadManager and into GetDownload, so
		//the user can see the progress of setting up files and pieces.
		handler.sendEmptyMessage(0);
		PIECES = dm.nbPieces;

		//DW 10-16-10 - Calling this now, so we can see its progress.
		//DW 11-2-10 - This might not be verifying the last piece correctly,
		//Or I'm not saving the last piece correctly. one of the two.
		if (!makeFiles())
			checkExistingPieces();

		// generate a random number 5 digit number
		Random generator = new Random();
		n = generator.nextInt(12343);

		int startport = n;
		int endport = (n + 1000);

		dm.startListening(startport, endport);
		dm.startTrackerUpdate();
		dm.unchokePeers();
		TOTAL = 0;

		byte[] b = new byte[0];
		int x = 0;
		while (true)  
		{
			try 
			{
				synchronized (b) 
				{
					b.wait(3000);
					//DW 10-13-10 - and finally, part 3 of shoddy background thread fix.
					if (readSettings.getBoolean("isRunning", true) == false)
					{
						///DW 10-13-10 The user wants the entire activity to stop.
						//This will be replaced with better background thread management in a future release.
						wl.release();
						nm.cancel(0);
						System.exit(0); //11-22-10 DW - Restored this
					}
					TOTAL = dm.totaldl;

					PEERS = dm.connectedpeers;
					COMPLETEDPIECES = dm.totalcomplete;
					TotalPeers = dm.peerCount();
					
					initializingFiles = dm.initializingFiles;
					filesInitialized = dm.filesInitialized;
					percentInitialized = dm.percentInitialized;
					
					update_status_bar.contentView.setProgressBar(R.id.status_progress, 100, (int) TOTAL, false);
					update_status_bar.contentView.setTextViewText(R.id.status_text, "Download progress: " + TOTAL + "%");
					//DW 11-2-10 - Found why a finished torrent loops the alert. Fixed.
					if (!Finished) 
						nm.notify(0, update_status_bar);
					
					handler.sendEmptyMessage(0);

					if (x == 50) //at 100, this unchokes every 5 minutes (and optimistically unchokes once every 15. Let's drop that in half.
					{
						dm.unchokePeers();

						b.notifyAll();
						x = 0;
					}
					x++;
				}
			} 
			catch (Exception e) 
			{
				e.printStackTrace();
			}
			if (dm.isComplete() && !Seeding && !Finished)
			{
				try
				{
					Finished = true;
					wl.release();
				}
				catch (Exception ex)
				{
					Log.e("FreeTorrent", "GetDownload - WakeLock Release Failed");
				}
				imdone();
			}
		}
	}

	private Handler handler = new Handler() 
{
		@Override
		public void handleMessage(Message msg) 
		{

			TextView pieces = (TextView) findViewById(R.id.pieces);
			pieces.setText(COMPLETEDPIECES + " pieces done out of " + PIECES);
			//pieces.setText(dm.totalcomplete + " pieces done out of " + PIECES);

			TextView warning = (TextView) findViewById(R.id.TextView06);
			warning.setText("Now downloading : " + TOTAL + "%" );
			// + " [" + (int) dm.totalrate + "kbps]" - This wasn't quite right.
			
			//DW 10-16-10 - Change button text when complete
			if (Seeding)
			{
				Button cancelButton = (Button)findViewById(R.id.Button01);
				cancelButton.setText("Stop Seeding");
			}
			
			//DW 10-14-10 TODO - This is the best entry to change to handle file initialization
			TextView text3 = (TextView) findViewById(R.id.TextView03); // file
			if (initializingFiles)
			{
				text3.setText("Initializing file " + filesInitialized + " (" + percentInitialized + "%)");
			}
			else 
			if (checkingPieces)
			{
				//int per = (pieceNum / totalPieces) * 100;
				text3.setText("Checking piece " + pieceNum + " of " + totalPieces);
			}
			else
			{
				mProgress.setProgress((int) TOTAL);
					text3.setText("Connected to " + PEERS + " peers (" + TotalPeers + " available)");
			}

		}
	};

	public void imdone() 
	{
		int icon = R.drawable.icon;
		CharSequence tickerText = "Torrent complete";
		long when = System.currentTimeMillis();

		update_status_bar = new Notification(icon, tickerText, when);

		Context context = getApplicationContext();
		CharSequence contentTitle = "Torrent completed";
		CharSequence contentText = "Your torrent is done. Please check the /FreeTorrent folder on your SD card!";
		Intent notificationIntent = new Intent(this, Freetorrent.class);
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,notificationIntent, 0);
		update_status_bar.setLatestEventInfo(context, contentTitle, contentText,	contentIntent);
		
		Seeding = true;

		nm.notify(0, update_status_bar); //DW 11-2-10 Was notification_number, decided it needs to always be 0 [I won't run multiples at once]
		handler.sendEmptyMessage(0);
	}
	
	//DW 10-16-10 - This was moved from DownloadManager
	private boolean makeFiles()
	{
		initializingFiles = true;
		boolean returnValue = true; //If all files are created, don't bother checking pieces.
		
    	//DW 10-13-10 - This appears to be the major lag issue when downloading.
        String saveas = Constants.SAVEPATH; // Should be configurable
        if (dm.nbOfFiles > 1)
            saveas += dm.torrent.saveAs + "/";
        new File(saveas).mkdirs();
        for (int i = 0; i < dm.nbOfFiles; i++) 
        {	
        	File temp = new File(saveas + ((String) (dm.torrent.name.get(i))));
            
        	if(temp.exists())
        	{
        		//DW 10-14-10 TODO - This should examine the files, and mark down which pieces are 
        		//already complete. Instead, it just redownloads the whole file.
        		returnValue = false;
        		try 
        		{
					dm.output_files[i] = new RandomAccessFile(temp, "rw");
				} 
        		catch (FileNotFoundException e) 
        		{
					// TODO Why does this cause more crashes?
					//Log.e("FreeTorrent", "checkTempFiles - " + e.getMessage());
				} //DW This needs set here too.
        	}
        	else
        	{
        	try 
        	{
        		//DW 10-13-10 - Found some of the lag. RandomAccessFile is amazingly slow in Java as a whole.
        		//Replacing the 2 lines below this with an alternate file write.
               // this.output_files[i] = new RandomAccessFile(temp, "rw");
                //this.output_files[i].setLength((Integer)this.torrent.length.get(i));
        		
        		//Now, new method: Write bytes directly to the disk, see if it's any faster.
        		//At the least, we can get some feedback on progress this way.
        		//Note that 512kb is completely arbitrary, and can be adjusted in the future
        		initializingFiles = true;
        		int j = 0; //counter
        		final int WRITESIZE = 262144; //DW 10-20-10 - was 524288, trying to be more efficient.
        		int fileSize = (Integer) dm.torrent.length.get(i);
        		int fileRemainder = fileSize % WRITESIZE;
        		byte[] fileBytes = new byte[WRITESIZE];
        		FileOutputStream fos = new FileOutputStream(temp, true);
        		
        			for( j = 0; j < fileSize; j+= WRITESIZE)
            		{
        				if (fileSize < WRITESIZE)
        				{
        					fileBytes = new byte[(Integer) dm.torrent.length.get(j)];
                    		fos.write(fileBytes);
                    		percentInitialized = (fileSize / j * WRITESIZE);
        				}
        				else
        				{
                    		fos.write(fileBytes);
        				}
            		}
        			//If the file was over 250kb, we need one more write to make sure the file's complete
        			if (fileRemainder != fileSize)
        			{
    					fileBytes = new byte[fileRemainder];
                		fos.write(fileBytes);
        			}
        			fos.close();
        			fileBytes = null;
        			//Now that we've written the file in a slightly more friendly way, we still need to open the RandomAccessFile.
                    this.output_files[i] = new RandomAccessFile(temp, "rw");
                    //sanity check here:
                    if ((int) this.output_files[i].length() == (Integer) dm.torrent.length.get(i))
                    {
                    	//DW 10-20-10 perform real error reporting.
                    	//String S = "ERROR";
                    }
                    filesInitialized++;
        	}
        	catch (IOException ioe) 
        	{
        		//DW 10-19-10 - The Log.e calls are causing a lot of crashes for some reason.
//                System.err.println("Could not create temp files");
//                ioe.printStackTrace();
        		//Log.e("FreeTorrent", ioe.getMessage());
                initializingFiles = false;
            }
        	catch (Exception ex)
        	{
        		//if (ex.getMessage() != null)
        			//Log.e("FreeTorrent", ex.getMessage());
        		//else
        			//Log.e("FreeTorrent", "makeFiles - unknown error");
        		initializingFiles = false;
        	}
        }
        }
		initializingFiles = false;
		return returnValue;
	}
	
	private void checkExistingPieces()
	{
		checkingPieces = true;
		totalPieces = dm.nbPieces;
		
		if(dlcontinue==1)
        {
			byte[] testPiece;
			//DW 10-21-10 - This ran the GC every loop because of testComplete. 
			//Moved some objects here to reduce that.
        	for (int i = 0; i < dm.nbPieces; i++)
        	{	
        		//DW 11-2-10 - The fact that this works indicates that this code is good for comparing hashes. The large number of 
        		//hashfails are likely cauesd by something else.
        		testPiece = dm.getPieceFromFiles(i);
        		
        		//DW 11-30-10 - This didn't trigger null results before 1.9.1. Did I change something that broke this?
        		if (testPiece != null)
        		{
        			if (Utils.byteArrayToByteString(Utils.hash(testPiece)).matches(Utils.byteArrayToByteString(dm.pieceList[i].sha1)))
        				//	if (dm.testComplete(i)) 
        			{
        				Log.d("FreeTorrent", "Piece " + i  + " is already complete");
        				dm.setComplete(i, true);
        				dm.isComplete.set(i, true);
        				dm.isRequested.clear(i);
        				dm.totalcomplete++;
        				dm.totaldl = (float) (((float) (100.0)) * ((float) (dm.isComplete.cardinality())) / ((float) (dm.nbPieces)));
        				dm.left -= dm.pieceList[i].getLength();
        				//	DW 10-28-10 - UI Improvement.
        				COMPLETEDPIECES++;
        			}
        		}
            	pieceNum = i + 1;
        		handler.sendEmptyMessage(0);
        	}
        }
		checkingPieces = false;
	}
	
    //DW 10-21-10 TODO - I use this block of code a lot, but it's so useful. I should move it to Utils.
    private void warnUser(String string) 
    {
    	AlertDialog.Builder builder = new AlertDialog.Builder(this);
    	builder.setMessage(string)
    	       .setCancelable(false)
    	       .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
    	           public void onClick(DialogInterface dialog, int id) {
    	                //contine downloading the torrent
    	        	   downloadOK = true;
    	           }
    	       })
    	       .setNegativeButton("No", new DialogInterface.OnClickListener() {
    	           public void onClick(DialogInterface dialog, int id) {
    	        	   //cancel this download.
    	        	   downloadOK = false;
    	                //dialog.cancel();
    	           }
    	       });
    	AlertDialog alert = builder.create();
    	alert.show(); //Necessary?
	}
    
    @Override
    public void onNewIntent(Intent i)
    {
    	//Nothing!
    }

}// end class
