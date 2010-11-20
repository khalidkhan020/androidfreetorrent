/*
 * Java Bittorrent API as its name indicates is a JAVA API that implements the Bittorrent Protocol

 * This project contains two packages:
 * 1. jBittorrentAPI is the "client" part, i.e. it implements all classes needed to publish
 *    files, share them and download them.
 *    This package also contains example classes on how a developer could create new applications.
 * 2. trackerBT is the "tracker" part, i.e. it implements a all classes needed to run
 *    a Bittorrent tracker that coordinates peers exchanges. *
 *
 * Copyright (C) 2007 Baptiste Dubuis, Artificial Intelligence Laboratory, EPFL
 *
 * This file is part of jbittorrentapi-v1.0.zip
 *
 * Java Bittorrent API is free software and a free user study set-up;
 * you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * Java Bittorrent API is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Java Bittorrent API; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 * @version 1.0
 * @author Baptiste Dubuis
 * To contact the author:
 * email: baptiste.dubuis@gmail.com
 *
 * More information about Java Bittorrent API:
 *    http://sourceforge.net/projects/bitext/
 */

package atorrentapi;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.Socket;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

import android.app.Activity;
import android.util.Log;
/**
 * Object that manages all concurrent downloads. It chooses which piece to request
 * to which peer.
 */
public class DownloadManager extends Activity  implements DTListener, PeerUpdateListener, ConListenerInterface 
{
	
	//10-25-10 TODO - Implement onSaveInstanceState properly so that we don't have to re-check files if we're killed by the OS.
	
	// Client ID
    private byte[] clientID;

    public TorrentFile torrent = null;

    private int maxConnectionNumber = 25; //was 200, wsan't even used before, now determined dynamically
    public int nbOfFiles = 0;
    private long length = 0;
    public long left = 0;
    public Piece[] pieceList;
    public BitSet isComplete;
    public BitSet isRequested;
    public int nbPieces;
    private int currentopenpieces=0;
    public RandomAccessFile[] output_files;
    public int maxopenpieces=10; //was 20, is used once in peerReady(), now determined dynamically.
    private PeerUpdater pu = null;
    private ConnectionListener cl = null;

    private List unchokeList = new LinkedList();

    private LinkedHashMap<String, Peer> peerList = null;
    public TreeMap<String, DownloadTask> task = null; //DW 10-28-10 - changed visibility
    private LinkedHashMap<String, BitSet> peerAvailabilies = null;

    LinkedHashMap unchoken = new LinkedHashMap<String, Integer>();
    private long lastUnchoking = 0;
    private short optimisticUnchokeCount = 3;
    
    public float totaldl;
    public float currentrate=0;
    public float totalrate=0;
    public int connectedpeers=0;
    public int totalcomplete=0;
    public int askcontinue=0;
    public int dlcontinue=0;
    
    //DW 10-13-10 - Adding these variables 
    public int totalFiles = 0;
    public int filesInitialized = 0;
    public float percentInitialized = 0;
    public boolean initializingFiles = false;
    public boolean checkingFiles = false;
    private int attemptedConnections = 0;
    public boolean canWrite = true; //10-18-10 - SD Card present or not
    public boolean warnUser = false; //10-21-10 - fixing error where you can't call functions before onCreate() is called.
    public int hashFails = 0; //10-31-10 - There seem to be a lot of these. Wonder if I'm calculating it wrong or if the test torrent peers suck.
    
    //DW These eliminate some extra objects
    DLRateComparator dlrc;
    ULRateComparator ulrc;
      
    /**
     * Create a new manager according to the given torrent and using the client id provided
     * @param torrent TorrentFile
     * @param clientID byte[]
     * @param db 
     */
    public  DownloadManager(TorrentFile torrent, final byte[] clientID, int downloadcontinue) 
    {
        this.clientID = clientID;
        this.peerList = new LinkedHashMap<String, Peer>();
        this.task = new TreeMap<String, DownloadTask>();
        this.peerAvailabilies = new LinkedHashMap<String, BitSet>();

        this.torrent = torrent;
        this.nbPieces = torrent.piece_hash_values_as_binary.size();
        this.pieceList = new Piece[this.nbPieces];
        this.nbOfFiles = this.torrent.length.size();

        this.isComplete = new BitSet(nbPieces);
        this.isRequested = new BitSet(nbPieces);
        this.output_files = new RandomAccessFile[this.nbOfFiles];

        this.length = this.torrent.total_length;
        this.left = this.length;
        
        dlrc = new DLRateComparator();
        ulrc = new ULRateComparator();
        
        //DW 10-17-10 - Implement this code 10-22 - Forgot what this TODO was for.
        //ActivityManager am = new ActivityManager();

        /**
         * Construct all the pieces with the correct length and hash value
         */
        int file = 0;
        int fileoffset = 0;
        
        for (int i = 0; i < this.nbPieces; i++) 
        {
            TreeMap<Integer, Integer> tm = new TreeMap<Integer, Integer>();
            int pieceoffset = 0;
            //DW 10-13-10 endless do-while loop, while not elegant, isn't necessarily the direct issue.
            do 
            {
                tm.put(file, fileoffset);
                if (fileoffset + this.torrent.pieceLength - pieceoffset >= (Integer) (torrent.length.get(file)) && i != this.nbPieces - 1) 
                {
                    pieceoffset += ((Integer) (torrent.length.get(file))).
                            intValue() - fileoffset;
                    file++;
                    fileoffset = 0;
                    if (pieceoffset == this.torrent.pieceLength)
                        break;
                } 
                else
                {
                    fileoffset += this.torrent.pieceLength - pieceoffset;
                    break;
                }
            } while (true);
            
            pieceList[i] = new Piece(i, (i != this.nbPieces - 1) ? this.torrent.pieceLength : ((Long) (this.length % this.torrent.pieceLength)).intValue(),
            		16384, (byte[]) torrent.piece_hash_values_as_binary.get(i),tm);
        }
        
        //DW 10-20-10 - Note that piece length is caculated here, I should check it and adjust some factors per torrent.
        //These values will be a pretty rough guess for now.
        //Typical sizes are 256kb, 512kb, and 1MB. I've seen 4MB pieces on very large torrents though.
        //My safe guess: use 8 MB of RAM or less for pieces. Not sure what the connections use RAM-wise, completely guessing.
        //10-25-10 - Update: The safe guesses on RAM were OK, until I realized how many extra copies would be made at once. 
        //the code uses util.subArray a lot, and there are quite a few places where ram gets eaten up, especially if 
        //several pieces are finished at once.
        //10-28-10 - After a couple false starts on tracking/limiting object use, I think I've got it locked down a little
        //better. Even with that, I'm still a bit over where it should be. Dropping these down a little more from the original guesses
        if (this.torrent.pieceLength > 0 && this.torrent.pieceLength <= 262144)
        {
        	maxopenpieces = 26; //32
        	maxConnectionNumber = 54; //64
        }
        else if (this.torrent.pieceLength > 262144 && this.torrent.pieceLength <= 524288)
        {
        	maxopenpieces = 12;//16
        	maxConnectionNumber = 32; //40
        }
        else if (this.torrent.pieceLength > 524288 && this.torrent.pieceLength <= 1048576)
        {
        	maxopenpieces = 6; //8
        	maxConnectionNumber = 20; //25
        }
        else //pieceLength > 1048576
        {
        	warnUser = true;
        	maxopenpieces = 3; //not sure of the size, but this will let them try. //4
        	maxConnectionNumber = 12; //15
        }
        
        this.lastUnchoking = System.currentTimeMillis();
    }

	//DW Adding this 10-11-10
    public int peerCount()
    {
    	if (pu != null && pu.getList() != null)
    	{
    		return pu.getList().size();
    	}
    	return 0;
    }

    public boolean testComplete(int piece) 
    {
    	//DW 10-18-10 - adding canWrite check
    	if (canWrite)
    	{
        boolean complete = false;
        try
        {
        	this.pieceList[piece].setBlock(0, this.getPieceFromFiles(piece));
        	complete = this.pieceList[piece].verify();
        	this.pieceList[piece].clearData();
        }
        catch (Exception ex)
        {
        	Log.v("FreeTorrent", "testComplete - " + ex.getMessage());
        }
        return complete;
    	}
    	return false;
    }

    /**
     * Create and start the peer updater to retrieve new peers sharing the file
     */
    public void startTrackerUpdate() 
    {
    	//DW 10-18-10 - Adding canWrite check here
    	if (canWrite)
    	{
    		this.pu = new PeerUpdater(this.clientID, this.torrent);
    		this.pu.addPeerUpdateListener(this);
    		this.pu.setListeningPort(this.cl.getConnectedPort());
    		this.pu.setLeft(this.left);
    		this.pu.start();
    	}
    }

    /**
     * Stop the tracker updates
     */
    public void stopTrackerUpdate() 
    {
        this.pu.end();
    }

    /**
     * Create the ConnectionListener to accept incoming connection from peers
     * @param minPort The minimal port number this client should listen on
     * @param maxPort The maximal port number this client should listen on
     * @return True if the listening process is started, false else
     * @todo Should it really be here? Better create it in the implementation
     */
    public boolean startListening(int minPort, int maxPort) 
    {
        this.cl = new ConnectionListener();
        if (this.cl.connect(minPort, maxPort)) 
        {
            this.cl.addConListenerInterface(this);
            return true;
        } 
        else 
        {
        	//DW 10-3-10 Make this use Log.e
            System.err.println("Could not create listening socket...");
            System.err.flush();
            return false;
        }
    }

    /**
     * Close all open files
     */
    public void closeTempFiles() 
    {
        for (int i = 0; i < this.output_files.length; i++)
            try 
        	{
                this.output_files[i].close();
            } 
        	catch (Exception e) 
        	{
                e.printStackTrace();
            }
    }

    /**
     * Save a piece in the corresponding file(s)
     * @param piece int
     */
    public synchronized void savePiece(int piece) 
    {	
    	//DW 10-18-10 - adding canWrite check
    	if (canWrite)
    	{
    	
        byte[] data = this.pieceList[piece].data();
        int remainingData = data.length;
        
        for (Iterator it = this.pieceList[piece].getFileAndOffset().keySet().iterator(); it.hasNext(); ) 
        {
            try 
            {
                Integer file = (Integer) (it.next());
                int remaining = ((Integer)this.torrent.length.get(file.intValue())).intValue() - ((Integer) (this.pieceList[piece].getFileAndOffset().get(file))).intValue();
                this.output_files[file.intValue()].seek(((Integer) (this.pieceList[piece].getFileAndOffset().get(file))).intValue());
                this.output_files[file.intValue()].write(data, data.length - remainingData, (remaining < remainingData) ? remaining : remainingData);
                remainingData -= remaining;
            } 
            catch (IOException ioe) 
            {
               Log.e("FreeTorrent", "savePiece error - " + ioe.getMessage());
            }
        }
        data = null;
        this.pieceList[piece].clearData();
        Log.i("FreeTorrent", "Piece " + piece + " cleared [savePiece]");
    	}
    }

    /**
     * Check if the current download is complete
     * @return boolean
     */
    public synchronized boolean isComplete() 
    {
        synchronized (this.isComplete) 
        {
            return (this.isComplete.cardinality() == this.nbPieces);
        }
    }

    /**
     * Returns the number of pieces currently requested to peers
     * @return int
     */
    public synchronized int cardinalityR() 
    {
        return this.isRequested.cardinality();
    }

    /**
     * Returns the piece with the given index
     * @param index The piece index
     * @return Piece The piece with the given index
     */
    public synchronized Piece getPiece(int index) 
    {
        synchronized (this.pieceList) 
        {
            return this.pieceList[index];
        }
    }

    /**
     * Check if the piece with the given index is complete and verified
     * @param piece The piece index
     * @return boolean
     */
    public synchronized boolean isPieceComplete(int piece) 
    {
        synchronized (this.isComplete) 
        {
            return this.isComplete.get(piece);
        }
    }

    /**
     * Check if the piece with the given index is requested by a peer
     * @param piece The piece index
     * @return boolean
     */
    public synchronized boolean isPieceRequested(int piece) 
    {
        synchronized (this.isRequested) 
        {
            return this.isRequested.get(piece);
        }
    }

    /**
     * Mark a piece as complete or not according to the parameters
     * @param piece The index of the piece to be updated
     * @param is True if the piece is now complete, false otherwise
     */
    public synchronized void setComplete(int piece, boolean is) 
    {
        synchronized (this.isComplete) 
        {
            this.isComplete.set(piece, is);
        }
    }

    /**
     * Mark a piece as requested or not according to the parameters
     * @param piece The index of the piece to be updated
     * @param is True if the piece is now requested, false otherwise
     */

    public synchronized void setRequested(int piece, boolean is) 
    {
        synchronized (this.isRequested) 
        {
            this.isRequested.set(piece, is);
        }
    }

    /**
     * Returns a String representing the piece being requested by peers.
     * Used only for pretty-printing.
     * @return String
     */
    public synchronized String requestedBits() 
    {
        String s = "";
        synchronized (this.isRequested) 
        {
            for (int i = 0; i < this.nbPieces; i++)
                s += this.isRequested.get(i) ? 1 : 0;
        }
        return s;
    }

    /**
     * Returns the index of the piece that could be downloaded by the peer in parameter
     * @param id The id of the peer that wants to download
     * @return int The index of the piece to request
     */
    private synchronized int choosePiece2Download(String id) 
    {
        synchronized (this.isComplete) 
        {
            int index = 0;
            ArrayList<Integer> possible = new ArrayList<Integer>(this.nbPieces);
            for (int i = 0; i < this.nbPieces; i++) 
            {
                if ((!this.isPieceRequested(i) ||
                     (this.isComplete.cardinality() > this.nbPieces - 3)) &&
                    (!this.isPieceComplete(i)) &&
                    this.peerAvailabilies.get(id) != null) 
                {
                    if (this.peerAvailabilies.get(id).get(i))
                        possible.add(i);
                }
            }
            if (possible.size() > 0) 
            {
                Random r = new Random(System.currentTimeMillis());
                index = possible.get(r.nextInt(possible.size()));
                this.setRequested(index, true);
                return (index);
            }
            return -1;
        }
    }

    /**
     * Removes a task and peer after the task sends a completion message.
     * Completion can be caused by an error (bad request, ...) or simply by the
     * end of the connection
     * @param id Task idendity
     * @param reason Reason of the completion
     */
    public synchronized void taskCompleted(String id, int reason) 
    {
    	Log.i("FreeTorrent", "Ending task " + id + ", reason: " + reason + " [taskCompleted]");
        switch (reason) 
        {
        	case DownloadTask.CONNECTION_REFUSED:
        	case DownloadTask.UNKNOWN_HOST:
        	case DownloadTask.MALFORMED_MESSAGE:
        	case DownloadTask.TIMEOUT:
            break;
        	
        	default:
        		//retryAllConnections();
        		break;
        }
        this.peerAvailabilies.remove(id);
        this.task.remove(id);
        this.peerList.remove(id);
        attemptedConnections--; //DW 10-14-10 added //10-28-10 readded
        currentopenpieces--; //DW 10-28-10
        //DW 10-31-10 - testing out my quick hack here. //MMMMno
        retryAllConnections();
    }

    /**
     * Received when a piece has been fully downloaded by a task. The piece might
     * have been corrupted, in which case the manager will request it again later.
     * If it has been successfully downloaded and verified, the piece status is
     * set to 'complete', a 'HAVE' message is sent to all connected peers and the
     * piece is saved into the corresponding file(s)
     * @param peerID String
     * @param i int
     * @param complete boolean
     */
    public synchronized void pieceCompleted(String peerID, int i, boolean complete) 
    {
    	//DW 10-18-10 - adding canWrite check
    	if (canWrite)
    	{
        synchronized (this.isRequested) 
        {
            this.isRequested.clear(i);
        }
        synchronized (this.isComplete) 
        {
            if (complete && !this.isPieceComplete(i)) 
            {
                pu.updateParameters(this.torrent.pieceLength, 0, "");
                this.isComplete.set(i, complete);
                 totaldl = (float) (((float) (100.0)) *
                                         ((float) (this.isComplete.cardinality())) /
                                         ((float) (this.nbPieces)));

                for (Iterator it = this.task.keySet().iterator(); it.hasNext(); )
                    try 
                	{
                        this.task.get(it.next()).ms.addMessageToQueue( new Message_PP(PeerProtocol.HAVE,
                                               Utils.intToByteArray(i), 1));
                    } 
                	catch (NullPointerException npe) 
                	{
                	}
                	this.savePiece(i); //DW 10-3-10 savePiece clears out the data, this isn't the memory leak location
                	totalcomplete++;
                	currentopenpieces--;    	
            } 
            else 
            {
            	//DW 10-31-10 Failed pieces never seem to be cleared out properly
                this.pieceList[i].clearData();
                currentopenpieces--;
            	Log.i("FreeTorrent", "Piece " + i + "cleared [pieceCompleted]");
            }

            if (this.isComplete.cardinality() == this.nbPieces) 
            {
                this.notify();
            }
        }
    	}
    }

    /**
     * Set the status of the piece to requested or not
     * @param i int
     * @param requested boolean
     */
    public synchronized void pieceRequested(int i, boolean requested) 
    {
        this.isRequested.set(i, requested);
    }

    /**
     * Choose which of the connected peers should be unchoked and authorized to
     * upload from this client. A peer gets unchoked if it is not interested, or
     * if it is interested and has one of the 5 highest download rate among the
     * interested peers. \r\n Every 3 times this method is called, calls the
     * optimisticUnchoke method, which unchoke a peer no matter its download rate,
     * in a try to find a better source
     */
    public synchronized void unchokePeers() 
    {
    	//DW 10-18-10 - adding canWrite test
    	if (canWrite)
    	{
    	totalrate=0;
    	connectedpeers=0;
        synchronized (this.task) 
        {
            int nbNotInterested = 0;
            int nbDownloaders = 0;
            int nbChoked = 0;
            this.unchoken.clear();
            List<Peer> l = new LinkedList<Peer>(this.peerList.values());
            
            if (!this.isComplete())
                Collections.sort(l, dlrc);
            else
                Collections.sort(l, ulrc);
            for (Iterator it = l.iterator(); it.hasNext(); ) 
            {
                Peer p = (Peer) it.next();
                if(p.isConnected())
                {
                	connectedpeers++;
                }
                if (p.getDLRate(false) > 0)
                {
                	float thisrate = p.getDLRate(true) / (1024 * 10);
                	totalrate+=(totalrate+thisrate);
                }
                                
                DownloadTask dt = this.task.get(p.toString());
                
                if (nbDownloaders < 5 && dt != null) 
                {
                    if (!p.isInterested()) 
                    {
                        this.unchoken.put(p.toString(), p);
                        if (p.isChoked())
                            dt.ms.addMessageToQueue(new Message_PP(PeerProtocol.UNCHOKE));
                        p.setChoked(false);

                        while (this.unchokeList.remove(p))
                            ;
                        nbNotInterested++;
                    } 
                    else if (p.isChoked()) 
                    {
                        this.unchoken.put(p.toString(), p);
                        dt.ms.addMessageToQueue(new Message_PP(PeerProtocol.UNCHOKE));
                        p.setChoked(false);
                        while (this.unchokeList.remove(p))
                            ;
                        nbDownloaders++;
                    }

                } 
                else 
                {
                    if (!p.isChoked()) 
                    {
                        dt.ms.addMessageToQueue(new Message_PP(PeerProtocol.CHOKE));
                        p.setChoked(true);
                    }
                    if (!this.unchokeList.contains(p))
                        this.unchokeList.add(p);
                    nbChoked++;
                }
                p = null;
                dt = null;
            }//for
        }
        this.lastUnchoking = System.currentTimeMillis();
        if (this.optimisticUnchokeCount-- == 0)
        {
            this.optimisticUnchoke();
            this.optimisticUnchokeCount = 3;
        }
        if(totaldl>=100)
        {
        }
    	}
    }

    private synchronized void optimisticUnchoke() 
    {
        if (!this.unchokeList.isEmpty()) 
        {
            Peer p = null;
            do 
            {
                p = (Peer)this.unchokeList.remove(0);
                synchronized (this.task) {
                    DownloadTask dt = this.task.get(p.toString());
                    if (dt != null) 
                    {
                        dt.ms.addMessageToQueue(new Message_PP(PeerProtocol.UNCHOKE));
                        p.setChoked(false);
                        this.unchoken.put(p.toString(), p);
                    } 
                    else
                        p = null;
                    dt = null;
                }
            } while ((p == null) && (!this.unchokeList.isEmpty()));
            p = null;
        }
    }

    /**
     * Received when a task is ready to download or upload. In such a case, if
     * there is a piece that can be downloaded from the corresponding peer, then
     * request the piece
     * @param peerID String
     */
    public synchronized void peerReady(String peerID) 
    {	
    	if (System.currentTimeMillis() - this.lastUnchoking > 10000)
	            this.unchokePeers();
	    if (currentopenpieces < maxopenpieces)
	    {
	        int piece2request = this.choosePiece2Download(peerID);
	        if (piece2request != -1)
	        {
	            this.task.get(peerID).requestPiece(this.pieceList[piece2request]);
	            currentopenpieces=this.task.get(peerID).getopenpieces();
	        }
    	}
    }

    /**
     * Received when a peer request a piece. If the piece is available (which
     * should always be the case according to Bittorrent protocol) and we are
     * able and willing to upload, the send the piece to the peer
     * @param peerID String
     * @param piece int
     * @param begin int
     * @param length int
     */
    public synchronized void peerRequest(String peerID, int piece, int begin, int length) 
    {

    	//DW 10-14-10 - Limiting connections
    	if ((connectedpeers < maxConnectionNumber) && currentopenpieces < maxopenpieces)
    	{
    		if (this.isPieceComplete(piece)) 
    		{
    			DownloadTask dt = this.task.get(peerID);
    			if (dt != null) 
    			{
    				dt.ms.addMessageToQueue(new Message_PP(PeerProtocol.PIECE,Utils.concat(Utils.intToByteArray(piece),
                            Utils.concat(Utils.intToByteArray(begin), this.getPieceBlock(piece, begin, length)))));
    				dt.peer.setULRate(length);
    			}
    			dt = null;
    			this.pu.updateParameters(0, length, "");
    		}
    		else 
    		{
    			try 
    			{
    				this.task.get(peerID).end();
    			} 
    			catch (Exception e) 
    			{
    			}
    			this.task.remove(peerID);
    			this.peerList.remove(peerID);
    			this.unchoken.remove(peerID);
    		}
    	}//if currentConnections
    }

    /**
     * Load piece data from the existing files
     * @param piece int
     * @return byte[] 
     */
    public synchronized byte[] getPieceFromFiles(int piece) 
    {
    
    	//DW 10-25-10 - How much of this is necessary? We only need back the byte[] with data.
    	try
    	{
        byte[] data = new byte[this.pieceList[piece].getLength()];
        int remainingData = data.length;
        for (Iterator it = this.pieceList[piece].getFileAndOffset().keySet().iterator(); it.hasNext(); ) 
        {
            try 
            {
                Integer file = (Integer) (it.next());
                int remaining = ((Integer)this.torrent.length.get(file.intValue())).intValue() - ((Integer) (this.pieceList[piece].getFileAndOffset().get(file))).intValue();
                this.output_files[file.intValue()].seek(((Integer) (this.pieceList[piece].getFileAndOffset().get(file))).intValue());
                this.output_files[file.intValue()].read(data, data.length - remainingData, (remaining < remainingData) ? remaining : remainingData);
                remainingData -= remaining;
            } 
            catch (IOException ioe) 
            {
                System.err.println(ioe.getMessage());
            }
        }
        return data;
    	}
        catch (Exception ex)
        {
        	Log.e("FreeTorrent", ex.getMessage());
        }
        return null; //DW 10-15-10 Shouldn't get here, ideally.
        
    }

    /**
     * Get a piece block from the existing file(s)
     * @param piece int
     * @param begin int
     * @param length int
     * @return byte[]
     */
    public synchronized byte[] getPieceBlock(int piece, int begin, int length) 
    {
        return Utils.subArray(this.getPieceFromFiles(piece), begin, length);
    }

    /**
     * Update the piece availabilities for a given peer
     * @param peerID String
     * @param has BitSet
     */
    public synchronized void peerAvailability(String peerID, BitSet has) 
    {
        this.peerAvailabilies.put(peerID, has);
        BitSet interest = (BitSet) (has.clone());
        interest.andNot(this.isComplete);
        DownloadTask dt = this.task.get(peerID);
        if (dt != null) 
        {
            if (interest.cardinality() > 0 && !dt.peer.isInteresting()) 
            {
                dt.ms.addMessageToQueue(new Message_PP(PeerProtocol.INTERESTED, 2));
                dt.peer.setInteresting(true);
            }
        }
        dt = null;
    }

    //DW 10-14-10 - this is only called from updatePeerList()
    public synchronized void connect(Peer p) 
    {
    	//10-28-10 - AttemptedConnections nevers drops down, this stalls after a short time.
    	//DW 10-14-10 - Now limiting the number of active connections we can have at once.
    	//if (attemptedConnections < maxConnectionNumber)
    	if (currentopenpieces < maxopenpieces)
    	{
    		DownloadTask dt = new DownloadTask(p,
                                           this.torrent.info_hash_as_binary,
                                           this.clientID, true,
                                           this.getBitField());
    		
    		dt.addDTListener(this);
    		dt.start();
    	//	attemptedConnections++;
    		currentopenpieces++;
    	}
    }

    //DW 10-14-10This never gets called. How about that.
    public synchronized void disconnect(Peer p) 
    {
        DownloadTask dt = task.remove(p.toString());
        if (dt != null) 
        {
            dt.end();
            dt = null;
        }
    }

    /**
     * Given the list in parameter, check if the peers are already present in
     * the peer list. If not, then add them and create a new task for them
     * @param list LinkedHashMap 
     */
    public synchronized void updatePeerList(LinkedHashMap list) 
    {
        synchronized (this.task) 
        {
            Set keyset = list.keySet();
            
            for (Iterator i = keyset.iterator(); i.hasNext(); ) 
            {
                String key = (String) i.next();
                if (!this.task.containsKey(key)) 
                {
                    Peer p = (Peer) list.get(key);
                    this.peerList.put(p.toString(), p);
                    this.connect(p);
                }
            }
        }
    }

    /**
     * Called when an update try fail. At the moment, simply display a message
     * @param error int
     * @param message String
     */
    public void updateFailed(int error, String message) 
    {
        System.err.println(message);
        System.err.flush();
    }

    /**
     * Add the download task to the list of active (i.e. Handshake is ok) tasks
     * @param id String
     * @param dt DownloadTask
     */
    public synchronized void addActiveTask(String id, DownloadTask dt) 
    {
        synchronized (this.task) 
        {
        	//10-31-10 - Removing this check causes the app to crash. 
        	//DW 10-14-10 - Trying to cap our thread count, though this might be done earlier
        	if (this.task.size() < maxopenpieces) //DW 10-30-10 changing from hard-coded 20
        	{
        		this.task.put(id, dt);
        		Log.i("FreeTorrent", "ActiveTask added[addActiveTask]");
        	}
        	else
        	{
        		//DW 10-14-10 just checking
        		Log.i("FreeTorrent", "ActiveTask denied, array full [addActiveTask]");
        		//DW 11-2-10 - Doing this causes a null reference error in the suddenly nulled DownloadTask.
        		//dt.end();
        		//dt = null;
        	}
        }
    }

    /**
     * Called when a new peer connects to the client. Check if it is already
     * registered in the peer list, and if not, create a new DownloadTask for it
     * @param s Socket
     */
    public synchronized void connectionAccepted(Socket s) 
    {
        synchronized (this.task) 
        {
            String id = s.getInetAddress().getHostAddress() +
                        ":" + s.getPort();
            if (!this.task.containsKey(id)) 
            {
            	//DW 10-30-10 - re-adding this check, against maxopenpieces instead.
            	if (this.task.size() < maxopenpieces)
            	{
                DownloadTask dt = new DownloadTask(null, this.torrent.info_hash_as_binary, this.clientID, false, this.getBitField(), s);
                dt.addDTListener(this);
                this.peerList.put(dt.getPeer().toString(), dt.getPeer());
                this.task.put(dt.getPeer().toString(), dt);
                dt.start();
                Log.i("FreeTorrent", "Task added [connectionAccepted]");
                //currentopenpieces++; //10-30-10 changes
            }
            else
            	Log.i("FreeTorrent", "Task denied, array full [connectionAccepted]");
            }
        }
    }

    /**
     * Compute the bitfield byte array from the isComplete BitSet
     * @return byte[]
     */
    public byte[] getBitField() 
    {
        int l = (int) Math.ceil((double)this.nbPieces / 8.0);
        byte[] bitfield = new byte[l];
        for (int i = 0; i < this.nbPieces; i++)
            if (this.isComplete.get(i)) 
            {
                bitfield[i / 8] |= 1 << (7 - i % 8);
            }
        return bitfield;
    }

    public float getCompleted() 
    {
        try 
        {
            return (float) (((float) (100.0)) * ((float)
                                                 (this.isComplete.cardinality())) / ((float) (this.nbPieces)));
        } 
        catch (Exception e) 
        {
            return 0.00f;
        }
    }

    public float getDLRate() 
    {
        try 
        {
            float rate = 0.00f;
            List<Peer> l = new LinkedList<Peer>(this.peerList.values());

            for (Iterator it = l.iterator(); it.hasNext(); ) 
            {
                Peer p = (Peer) it.next();
                if (p.getDLRate(false) > 0)
                    rate = rate + p.getDLRate(true);

            }
            return rate / (1024 * 10);
        } 
        catch (Exception e) 
        {
            return 0.00f;
        }
    }

    public float getULRate() {
        try 
        {
            float rate = 0.00f;
            List<Peer> l = new LinkedList<Peer>(this.peerList.values());

            for (Iterator it = l.iterator(); it.hasNext(); ) 
            {
                Peer p = (Peer) it.next();
                if (p.getULRate(false) > 0)
                    rate = rate + p.getULRate(true);
            }
            return rate / (1024 * 10);
        } 
        catch (Exception e) 
        {
            return 0.00f;
        }
    }

    //DW 10-18-10 - These 2 are new, test them out.
	public void killWrites() 
	{
			this.task.clear();
			canWrite = false;
			stopTrackerUpdate();
			closeTempFiles(); 
	}

	public void resumeWrites() 
	{
		canWrite = true;
		//checkTempFiles(); //DW - I removed this, need to bring it back.
		//updatePeerList(peerList);
		startTrackerUpdate();
	}
	
	//DW 10-31-10 - Lazy way around losing connections so quickly.
	public void retryAllConnections()
	{
		Set keyset = peerList.keySet();
        
        for (Iterator i = keyset.iterator(); i.hasNext(); )
        {
        	String key = (String) i.next();
        	Peer p = (Peer) peerList.get(key); 
            if (p.isInterested() && !p.isChoked() && p.isConnected()) 
            {
                connect(p);
            }
        }
	}
}
