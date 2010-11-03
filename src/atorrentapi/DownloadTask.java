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

import java.io.*;
import java.util.*;
import java.net.*;
import android.util.Log;

//DW 10-14-10 - This class is a little excessive for Android, since 
//the program will create 1 DownloadTask for each peer on the list it seems.

import com.drakewill.freetorrent.EventListenerList;

/**
 * Class representing a task that downloads pieces from a remote peer
 *
 */
public class DownloadTask extends Thread implements IncomingListener, OutgoingListener 
{

	public int openpieces=0;
    private static final int IDLE = 0;
    private static final int WAIT_HS = 1;
    private static final int WAIT_BFORHAVE = 2;
    private static final int WAIT_UNCHOKE = 4;
    private static final int READY_2_DL = 5;
    private static final int DOWNLOADING = 6;
    private static final int WAIT_BLOCK = 7;

    public static final int TASK_COMPLETED = 0;
    public static final int UNKNOWN_HOST = 1;
    public static final int CONNECTION_REFUSED = 2;
    public static final int BAD_HANDSHAKE = 3;
    public static final int MALFORMED_MESSAGE = 4;
    public static final int TIMEOUT = 5;
    public static final int UNKNOWN_ERROR=99; //DW added 10-14-10

    private int state = DownloadTask.IDLE;
    private boolean run = true;
    private byte[] fileID;
    private byte[] myID;
    public Peer peer;

    private final boolean initiate;
    public byte[] bitfield = null;
    private boolean isDownloading = false; //This one might be used elsewhere.

    private Piece downloadPiece = null;
    private int offset = 0;

    private final EventListenerList listeners = new EventListenerList();

    private Socket peerConnection = null;
    private OutputStream os = null;
    private InputStream is = null;

    public MessageSender ms = null;
    public MessageReceiver mr = null;

    private long downloaded = 0;
    private long updateTime = 0;
    private long lmrt = 0;

    private LinkedList<Integer> pendingRequest;
	
    //DW added 10-14-10
    private long startTime = 0;

    /**
     * Start the downloading process from the remote peer in parameter
     * @param peer The peer to connect to
     * @param fileID The file to be downloaded
     * @param myID The id of the current client
     * @param init True if this client initiate the connection, false otherwise
     * @param s Only set if this client receive a connection request from the remote peer
     * @param bitfield The piece currently owned by this client
     */
    public DownloadTask(Peer peer, byte[] fileID, byte[] myID, boolean init, byte[] bitfield,
                        Socket s) 
    {
    	//DW 10-14-10 - Attempting to choke the CPU a little less
    	android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
    	
        this.pendingRequest = new LinkedList<Integer>();
        this.fileID = fileID;
        this.myID = myID;
        this.initiate = init;
        this.bitfield = bitfield;
        if (s != null) 
        {
            try 
            {
                this.peerConnection = s;
                String peerIP = this.peerConnection.getInetAddress().getHostAddress();
                int peerPort = this.peerConnection.getPort();

                this.is = this.peerConnection.getInputStream();
                this.os = this.peerConnection.getOutputStream();
                this.peer = new Peer();
                this.peer.setIP(peerIP);
                this.peer.setPort(peerPort);
            } 
            catch (IOException ioe) 
            {
            }
        } 
        else
            this.peer = peer;
        //lm = new LogManager("logs/downloads.log");
    }

    /**
     * Start the downloading process from the remote peer in parameter
     * @param peer The peer to connect to
     * @param fileID The file to be downloaded
     * @param myID The id of the current client
     * @param init True if this client initiate the connection, false otherwise
     * @param bitfield The pieces currently owned by this client
     */
    public DownloadTask(Peer peer, byte[] fileID, byte[] myID, boolean init, byte[] bitfield) 
    {
        this(peer, fileID, myID, init, bitfield, null);
    }

    /**
     * Inits the connection to the remote peer. Also init the message sender and receiver.
     * If necessary, starts the handshake with the peer
     * @throws UnknownHostException If the remote peer is unknown
     * @throws IOException If the connection to the remote peer fails (reset, ...)
     */
    public void initConnection() throws UnknownHostException, IOException 
    {
    	//DW 10-28-10 - Debugging helper
    	Log.v("FreeTorrent", "DownloadTask " + this.getId() + " started");
        if (this.peerConnection == null && !this.peer.isConnected()) 
        {
            this.peerConnection = new Socket(this.peer.getIP(),  this.peer.getPort());
            this.os = this.peerConnection.getOutputStream();
            this.is = this.peerConnection.getInputStream();
            this.peer.setConnected(true);
        }

        if (ms == null)
        {
        	ms = new MessageSender(this.peer.toString(), this.os);
        	ms.addOutgoingListener(this);
        	ms.start();
        }
        if (mr == null)
        {
        	mr = new MessageReceiver(this.peer.toString(), this.is);
        	mr.addIncomingListener(this);
        	mr.start();
        }

        try
        {
        	this.fireAddActiveTask(peer.toString(), this);
            //DW 11-2-10 - Null reference was caused here due to killing this object while running it.
            //Consider fixing that by having some variable be changed if the task failed to be added.
            //Still, try/catch for now
        	if(this.fileID!=null && this.myID!=null)
        	{
        		if (this.initiate && this.fileID!=null && this.myID!=null) 
        		{
        			ms.addMessageToQueue(new Message_HS(this.fileID, this.myID));
        			this.changeState(DownloadTask.WAIT_HS);
        		} 
        		else
        		{
        			this.changeState(DownloadTask.WAIT_BFORHAVE);
        		}
        	}
        }
        catch (Exception ex)
        {
        	Log.e("FreeTorrent", "Error adding active task:" + ex.getCause());
        }
    }

    public void run() 
    {
    	Log.d("FreeTorrent", "new DownloadTask started");
    	long lastDownloadCount = 0;
        try 
        {
            this.initConnection();
            /**
             * Wait for the task to end, i.e. the peer to return to IDLE state
             */
            while (this.run)
                synchronized (this) 
                {
                	//DW 10-14-10 I was just killing every connection after a minute. Let's only kill idle ones.
//                	
//                	if (startTime == 0)
//                		startTime = System.currentTimeMillis();
//                	if (this.peer.getDL() == lastDownloadCount)
//                	{
//                		if (System.currentTimeMillis() - startTime > 1200000)
//                		{
//                			//	This connection has done nothing for 2 minutes. Kill it, try another one.
//                			this.fireTaskCompleted(this.peer.toString(), DownloadTask.TIMEOUT);
//                		}
//                	}
//                	else //update download count, reset idle timer
//                	{
//                		lastDownloadCount = this.peer.getDL();
//                		startTime = System.currentTimeMillis();
//                	}
//                	//DW 10-16-10 - Adding a bit of a pause to ease up on the CPU
//                	try {
//						wait(200);
//					} catch (InterruptedException e) {
//						// TODO Auto-generated catch block
//						e.printStackTrace();
//					}
                    try 
                    {
						this.wait();
					} 
                    catch (InterruptedException e) 
                    {
						//e.printStackTrace();
					} //DW - originally the only line in this block
                }
            //DW 10-14-10 - Let's try marking this as complete once it's not running anymore.
            //this.fireTaskCompleted(this.peer.toString(), DownloadTask.TASK_COMPLETED);
        } 
        catch (UnknownHostException uhe) 
        {
            this.fireTaskCompleted(this.peer.toString(), DownloadTask.UNKNOWN_HOST);
        } 
        catch (IOException ioe) 
        {
            this.fireTaskCompleted(this.peer.toString(), DownloadTask.CONNECTION_REFUSED);
        }
        //There was another mandatory catch block hhere, but removing .wait donesnt require it.
    }

    /**
     * Clear the piece currently downloading
     */
    private synchronized void clear() 
    {
        if (downloadPiece != null) 
        {
            this.firePieceRequested(downloadPiece.getIndex(), false);
            downloadPiece = null;
        }
    }

    /**
     * Returns this peer object
     * @return Peer
     */
    public synchronized Peer getPeer()
    {
        return this.peer;
    }

    /**
     * Request a peer to the peer
     * @param p The piece to be requested to the peer
     */
    public synchronized void requestPiece(Piece p) 
    {
        synchronized (this) 
        {
            this.downloadPiece = p;
            if (this.state == DownloadTask.READY_2_DL)
            {
            	openpieces++;
                this.changeState(DownloadTask.DOWNLOADING);
                Log.d("FreeTorrent", "Requested piece" + p.index + " [requestPiece]");
            }
        }
    }

    /**
     * Returns the total amount of bytes downloaded by this task so far
     * @return int
     */
    public synchronized int checkDownloaded()
    {
        int d = new Long(this.downloaded).intValue();
        return d;
    }

    /**
     * Fired when the connection to the remote peer has been closed. This method
     * clear this task data and send a message to the DownloadManager, informing
     * it that this peer connection has been closed, resulting in the deletion of
     * this task
     */
    public synchronized void connectionClosed() 
    {
    	Log.d("FreeTorrent", "Piece Cancelled [connectionClosed]");
        this.clear();
        this.fireTaskCompleted(this.peer.toString(), DownloadTask.CONNECTION_REFUSED);
        
    }

    /**
     * Fired when a keep-alive message has been sent by the MessageSender.
     * If at the time the keep-alive was sent, this peer has not received any
     * message from the remote peer since more that 3 (DW: now 1) minutes, the remote peer is
     * considered as dead, and we close the connection, then inform the
     * DownloadManager that this connection timed out...
     *
     * Otherwise, inform the DownloadManager that this task is still alive and has
     * not been used for a long time...
     */
    public synchronized void keepAliveSent() 
    {
    	//DW 10-14-10 - this was 180,000ms, changed to 60,000ms
        if (System.currentTimeMillis() - this.lmrt > 60000) 
        {
        	Log.d("FreeTorrent", "Piece " + this.downloadPiece.index + "Cancelled [KeepAliveSent]");
            this.clear();
            this.fireTaskCompleted(this.peer.toString(), DownloadTask.TIMEOUT);
            return;
        }
        this.firePeerReady(this.peer.toString());
    }

    /**
     * According to the message type, change the state of the task (peer) and
     * take the necessary actions
     * @param m Message
     */
    public synchronized void messageReceived(Message m) 
    {
    	try
    	{
        if (m == null) 
        {
            this.fireTaskCompleted(this.peer.toString(), DownloadTask.MALFORMED_MESSAGE);
            return;
        }
        this.lmrt = System.currentTimeMillis();

        if (m.getType() == PeerProtocol.HANDSHAKE) 
        {
            Message_HS hs = (Message_HS) m;

            // Check that the requested file is the one this client is sharing
            if (Utils.bytesCompare(hs.getFileID(), this.fileID)) 
            {
                if (!initiate) 
                { // If not already done, send handshake message
                    this.peer.setID(new String(hs.getPeerID()));
                    this.ms.addMessageToQueue(new Message_HS(this.fileID, this.myID));
                }

                this.ms.addMessageToQueue(new Message_PP(PeerProtocol.BITFIELD, this.bitfield));

                this.changeState(DownloadTask.WAIT_BFORHAVE);
            } 
            else
            {
            	this.fireTaskCompleted(this.peer.toString(), DownloadTask.BAD_HANDSHAKE);
            }
            hs = null;

        }
        else 
        {
            Message_PP message = (Message_PP) m;
            switch (message.getType()) 
            {
            case PeerProtocol.KEEP_ALIVE:
                // Nothing to do, just keep the connection open
                break;

            case PeerProtocol.CHOKE:
                /*
                 * Change the choking state to true, meaning remote peer
                 * will not accept any request message from this client
                 */
                this.peer.setChoking(true);
                this.isDownloading = false;
                break;

            case PeerProtocol.UNCHOKE:
                /*
                 * Change the choking state to false, meaning this client now
                 * accepts request messages from this client.
                 * If this task was already downloading a piece, then continue.
                 * Otherwise, advertise DownloadManager that it is ready to do so
                 */
                this.peer.setChoking(false);
                if (this.downloadPiece == null) 
                {
                    this.changeState(DownloadTask.READY_2_DL);
                } 
                else
                    this.changeState(DownloadTask.DOWNLOADING);
                break;

            case PeerProtocol.INTERESTED:
                /*
                 * Change the interested state of the remote peer to true,
                 * meaning this peer will start downloading from this client if
                 * it is unchoked
                 */
                this.peer.setInterested(true);
                break;

            case PeerProtocol.NOT_INTERESTED:
                /*
                 * Change the interested state of the remote peer to true,
                 * meaning this peer will not start downloading from this client
                 * if it is unchoked
                 */

                this.peer.setInterested(false);
                break;

            case PeerProtocol.HAVE:
                /*
                 * Update the peer piece list with the piece described in this
                 * message and advertise DownloadManager of the change
                 */
                this.peer.setHasPiece(Utils.byteArrayToInt(message.getPayload()), true);
                this.firePeerAvailability(this.peer.toString(), this.peer.getHasPiece());
                break;

            case PeerProtocol.BITFIELD:
                /*
                 * Update the peer piece list with the piece described in this
                 * message and advertise DownloadManager of the change
                 */
                this.peer.setHasPiece(message.getPayload());
                this.firePeerAvailability(this.peer.toString(), this.peer.getHasPiece());
                this.changeState(DownloadTask.WAIT_UNCHOKE);
                break;

            case PeerProtocol.REQUEST:
                /*
                 * If the peer is not choked, advertise the DownloadManager of
                 * this request. Otherwise, end connection since the peer does
                 * not respect the Bittorrent protocol
                 */
            	
            	
                if(!this.peer.isChoked())
                {
                	//DW 10-25-10 - Easing up a bit on memory here
                	byte[] payload = message.getPayload();
                	byte[] sub1 = new byte[4];
                	byte[] sub2 = new byte[4];
                	byte[] sub3 = new byte[4];
                	System.arraycopy(payload, 0, sub1, 0, 4);
                	System.arraycopy(payload, 4, sub2, 0, 4);
                	System.arraycopy(payload, 8, sub3, 0, 4);
                	
                	this.firePeerRequest(this.peer.toString(), Utils.byteArrayToInt(sub1), Utils.byteArrayToInt(sub2),
                          Utils.byteArrayToInt(sub3));
                }
                else
                {
                    this.fireTaskCompleted(this.peer.toString(), DownloadTask.MALFORMED_MESSAGE);
                }
                break;

            case PeerProtocol.PIECE:
                /**
                 * Sets the block of data downloaded in the piece block list and
                 * update the peer download rate. Removes the piece block from
                 * the pending request list and change state.
                 */
            	//DW 10-31-10 - Should this save the piece, or request another one?
            	//No. This block is called for each 16kb piece received, not the entire
            	//piece.
            		
            	if(this.downloadPiece!=null)
            	{
            		Log.d("FreeTorrent", "Piece " + this.downloadPiece.index + " chunk received [messageReceived]");
            		
            		byte[] payload = message.getPayload();
            		int begin;
            		byte[] header = new byte[4];
            		System.arraycopy(payload, 4, header, 0, 4);
            		begin = Utils.byteArrayToInt(header);
            		
	                byte[] data = new byte[payload.length - 8];
	                System.arraycopy(payload, 8, data, 0, payload.length - 8);
	                this.downloadPiece.setBlock(begin,data);
	                this.peer.setDLRate(data.length);
	                this.pendingRequest.remove(new Integer(begin)); //10-31-10 - This has to stay this way for no clear reason. Fails otherwise.
	                data  = null; //DW 10-25-10 - Freeing this up a bit faster now.
	                //this.downloadPiece.clearData(); //This too. - HURR DONT DO THIS
	                if (this.pendingRequest.size() == 0)
	                {
	                	//Log.d("FreeTorrent", "Piece " + this.downloadPiece.index + " finished [messageReceived]");
	                    this.isDownloading = false;
	                }
	                this.changeState(DownloadTask.DOWNLOADING);
            	}
	                break;
            	
            case PeerProtocol.CANCEL:
                // TODO: Still to implement the cancel message. Not used here
                break;

            case PeerProtocol.PORT:
                // TODO: Still to implement the port message. Not used here
                break;
            }
            message = null;
        }
    	}
        catch(Exception e)
        {
        	//DW 10-14-10 - This seems to be appropriate
        	//this.fireTaskCompleted(this.peer.toString(), DownloadTask.MALFORMED_MESSAGE);
        	Log.e("FreeTorrent", "Error processing message: " + e.getCause() + " [messageReceived]");
        	m = null;
        }
        m = null;
    }

    /**
     * Change the state of the task. State depends on the previously received messages
     * This is here that are taken the most important decisions about the messages to
     * be sent to the remote peer
     * @param newState The new state of the download task
     */
    private synchronized void changeState(int newState) 
    {
        this.state = newState;
        switch (newState) 
        {
        case WAIT_BLOCK:
            /**
             * Keep a certain number of unanswered requests, for performance.
             * If only sending 1 request an waiting, it is a loss of time and
             * bandwidth because of the RTT to the remote peer
             */
            if (this.pendingRequest.size() < 5 &&  offset < downloadPiece.getLength())
                this.changeState(DownloadTask.DOWNLOADING);
            break;
        case READY_2_DL:
            /**
             * Advertise the DownloadManager that this task is ready to download
             */
            this.firePeerReady(this.peer.toString());
            break;
        case DOWNLOADING:

            /**
             * If offset is bigger than the piece length and the pending request size
             * is 0, then we have downloaded all the piece blocks and we can verify
             * the integrity of the data
             */
            if (offset >= downloadPiece.getLength()) 
            {
                if (this.pendingRequest.size() == 0) 
                {
                    int p = downloadPiece.getIndex();
                    offset = 0;
                    if (downloadPiece.verify()) 
                    {
                    	Log.d("FreeTorrent", "Piece " + this.downloadPiece.index + "verified [changeState]");
                        this.firePieceCompleted(p, true);
                        this.changeState(DownloadTask.READY_2_DL);
                    } 
                    else 
                    {
                    	Log.d("FreeTorrent", "Piece " + this.downloadPiece.index + "failed verification [changeState]");
                        this.firePieceCompleted(p, false);
                        this.changeState(DownloadTask.READY_2_DL);
                    }
                    this.clear();
                    this.changeState(READY_2_DL);
                }
            } 
            else if (downloadPiece != null && !this.peer.isChoking()) 
            {
                byte[] pieceIndex = Utils.intToByteArray(downloadPiece.getIndex());
                byte[] begin = Utils.intToByteArray(offset);

                int length = downloadPiece.getLength() - offset;
                if (length >= PeerProtocol.BLOCK_SIZE)
                {
                    length = PeerProtocol.BLOCK_SIZE;
                }
                ms.addMessageToQueue(new Message_PP(PeerProtocol.REQUEST, Utils.concat(pieceIndex, Utils.concat(begin, Utils.intToByteArray(length))), 2));
                if(this.updateTime == 0)
                    this.updateTime = System.currentTimeMillis();
                this.pendingRequest.add(new Integer(offset));
                offset += PeerProtocol.BLOCK_SIZE;
                this.isDownloading = true;
                this.changeState(DownloadTask.WAIT_BLOCK);
            }

            break;
        }
    }

    public synchronized void addDTListener(DTListener listener) 
    {
        listeners.add(DTListener.class, listener);
    }

    public synchronized void removeDTListener(DTListener listener) 
    {
        listeners.remove(DTListener.class, listener);
    }

    public synchronized DTListener[] getDTListeners() 
    {
        return listeners.getListeners(DTListener.class);
    }

    /**
     * Fired to inform if the given piece is requested or not...
     * @param piece int
     * @param requested boolean
     */
    private synchronized void firePieceRequested(int piece, boolean requested) 
    {
        for (DTListener listener : getDTListeners()) 
        {
            listener.pieceRequested(piece, requested);
        }
    }

    /**
     * Fired to inform that the given piece has been completed or not
     * @param piece int
     * @param complete boolean
     */
    private synchronized void firePieceCompleted(int piece, boolean complete) 
    {
        for (DTListener listener : getDTListeners()) 
        {
            listener.pieceCompleted(this.peer.toString(), piece, complete);
        }
        
    }

    /**
     * Fired to inform that the task is finished for a certain reason
     * @param id String
     * @param reason Reason why the task ended
     */
    private synchronized void fireTaskCompleted(String id, int reason) 
    {
        this.end();
        for (DTListener listener : getDTListeners()) 
        {
            listener.taskCompleted(id, reason);
        }
    }

    /**
     * Fired to inform that this task is ready to download
     * @param id String
     */
    private synchronized void firePeerReady(String id) 
    {
        for (DTListener listener : getDTListeners()) 
        {
            listener.peerReady(id);
        }
    }

    /**
     * Fired to inform that the peer requests a piece block
     * @param peerID String
     * @param piece int
     * @param begin int
     * @param length int
     */
    private synchronized void firePeerRequest(String peerID, int piece, int begin, int length) 
    {
        for (DTListener listener : getDTListeners()) 
        {
            listener.peerRequest(peerID, piece, begin, length);
        }

    }

    /**
     * Fired to inform that the availability of this peer has changed
     * @param id String
     * @param hasPiece BitSet
     */
    private synchronized void firePeerAvailability(String id, BitSet hasPiece) 
    {
        for (DTListener listener : getDTListeners()) 
        {
            listener.peerAvailability(id, hasPiece);
        }
    }

    /**
     * Fired to inform that this task has completed the handshake and is now
     * ready to communicate with the remote peer
     * @param id String
     * @param dt DownloadTask
     */
    private synchronized void fireAddActiveTask(String id, DownloadTask dt) 
    {
        for (DTListener listener : getDTListeners()) 
        {
            listener.addActiveTask(id, dt);
        }
    }

    /**
     * Stops this thread by setting the 'run' variable to false and closing
     * the communication thread (Message receiver and sender). Closes the
     * connection to the remote peer
     */
    public synchronized void end()
    {
    	//DW 10-28-10 - Test
    	Log.v("FreeTorrent", "DownloadTask " + this.getId() + "ended");
        this.changeState(DownloadTask.IDLE);
        this.run = false;
        synchronized(this)
        {
            if(this.ms != null)
            {
            	this.ms.stopThread();
            	this.ms = null;
            }
        if(this.mr != null)
        {
            this.mr.stopThread();
            this.mr = null;
        }
        try
        {
            this.peerConnection.close();
        }
        catch(Exception e)
        {
        	
        }
        try
        {
            this.is.close();
        }
        catch(Exception e)
        {
        	
        }
        try
        {
            this.os.close();
        }
        catch(Exception e)
        {
        	
        }
        this.peerConnection = null;

        this.notifyAll();
        }
    }

    protected void finalize() throws Throwable
    {
        if(this.peerConnection != null)
        {
            try
            {
                try
                {
                    this.is.close();
                }
                finally
                {
                    this.is = null;
                }
                try
                {
                    this.os.close();
                }
                finally
                {
                    this.os = null;
                }

                if (!this.peerConnection.isClosed())
                    this.peerConnection.close();
                this.peerConnection = null;

            }
            finally
            {
                super.finalize();
            }
        }
    }

	public int getopenpieces() 
	{
		return this.openpieces;
	}
	
	public void setopenpices(int num)
	{
		this.openpieces=num;
	}

}
