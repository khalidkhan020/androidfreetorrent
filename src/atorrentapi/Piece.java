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

import java.util.*;

import android.util.Log;

/**
 * Class representing a piece according to bittorrent definition.
 * The piece is a part of data of the target file(s)
 *
 * @author Baptiste Dubuis
 * @version 0.1
 */
public class Piece 
{

    private TreeMap<Integer, Integer> filesAndoffset;
    /**
     * Index of the piece within the file(s)
     */
    int index;
    /**
     * Length of the piece. It should be constant, except for the last piece of the file(s)
     */
    int length;
    /**
     * Map containing the piece data
     */
    TreeMap<Integer, byte[]> pieceBlock;
    /**
     * SHA1 hash of the piece contained in the torrent file. At the end of the download
     * this value must correspond to the SHA1 hash of the pieceBlock map concatenated
     */
    public byte[] sha1;

    public Piece(int index, int length, int blockSize, byte[] sha1){
        this(index, length, blockSize, sha1, null);
    }

    /**
     * Constructor of a Piece
     * @param index Index of the piece
     * @param length Length of the piece
     * @param blockSize Size of a block of data
     * @param sha1 SHA1 hash that must be verified at the end of download
     * @param m HashTable containing the file(s) this piece belongs to and the index in these
     */
    public Piece(int index, int length, int blockSize, byte[] sha1, TreeMap<Integer, Integer> m) {
        this.index = index;
        this.length = length;
        this.pieceBlock = new TreeMap<Integer, byte[]>();
        this.sha1 = sha1;
        if(m != null)
            this.filesAndoffset = m;
        else
            this.filesAndoffset = new TreeMap<Integer, Integer>();
    }

//    public Piece() 
//    {
//		// TODO Auto-generated constructor stub
//	}

	public void clearData()
	{
        this.pieceBlock.clear();
    }

    public void setFileAndOffset(int file, int offset)
    {
        this.filesAndoffset.put(file, offset);
    }

    public TreeMap getFileAndOffset()
    {
        return this.filesAndoffset;
    }

    /**
     * Return the index of the piece
     * @return int
     */
    public synchronized int getIndex()
    {
        return this.index;
    }

    /**
     * Returns the length of the piece
     * @return int
     */
    public synchronized int getLength()
    {
        return this.length;
    }

    /**
     * Set a block of data at the corresponding offset
     * @param offset Offset of the data within the current piece
     * @param data Data to be set at the given offset
     */
    public synchronized void setBlock(int offset, byte[] data)
    {
        this.pieceBlock.put(offset, data);
    }

    /**
     * Returns the concatenated value of the pieceBlock map. This represent the piece data
     * @return byte[]
     */
    public  byte[] data()
    {
    	//DW 10-25-10 - This is a major RAM eater too.
    	//Each time concat get called, it eats another chunk of RAM slightly bigger than the first.
    	//The GC can't keep up when there's 10-30 downloadTasks at once doing that.
    	//Replacing this code with the block below.
//    	 byte[] data = new byte[0];
//    	 for(Iterator it = this.pieceBlock.keySet().iterator(); it.hasNext();)
//    	 {
//    		    data = Utils.concat(data, this.pieceBlock.get(it.next()));	           
//    	 }
    	 //return data;
    //}
    	
       	//DW 10-25-10 - System.arraycopy is ~9x faster than doing it yourself, according to Google.
    	//I've confirmed that this method takes up way less memory when running as well.
    	byte[] data = new byte[length];
    	byte[] tempdata = new byte[0];
    	int offset = 0;
    	
    	for(Iterator it = this.pieceBlock.keySet().iterator(); it.hasNext();)
       	{
    		tempdata = this.pieceBlock.get(it.next());
    		//DW 12-11-10 - Adding in some debugging info, hopefully to track down why hashfails are so common.
    		Log.d("FreeTorrent", "Piece " + index + " block " + offset + " length " + tempdata.length);
    		
    		System.arraycopy(tempdata, 0, data, offset, tempdata.length);
    		offset += tempdata.length;
       	}
    	return data;
    }

    /**
     * Verify if the downloaded data corresponds to the original data contained in the torrent
     * by comparing it to the SHA1 hash in the torrent
     * @return boolean
     */
    public synchronized boolean verify()
    {
    	String dlHash = Utils.byteArrayToByteString(Utils.hash(this.data()));
    	String torrentHash = Utils.byteArrayToByteString(this.sha1);
    	
    	//DW 11-2-10 - Awfully large number of hashfails in the latest version.
    	//Trying to figure out why.
    	//Log.d("FreeTorrent", "Downloaded hash for piece " + index + " is " + dlHash);
    	//Log.d("FreeTorrent", "Tracker's hash for piece " + index + " is " + torrentHash);
        
    	return dlHash.matches(torrentHash); 
    }

    /**
     * Print some information about the Piece
     * @return String
     */
    public synchronized String toString()
    {
        String s = "";
        s += "Piece " + this.index + "[" + this.length + "Bytes], part of file";
        if(this.filesAndoffset.size() > 1)
            s += "s";
        for(Iterator it = this.filesAndoffset.keySet().iterator(); it.hasNext();)
        {
            int key = ((Integer)(it.next())).intValue();
            s += " " + key + " [offset = " + this.filesAndoffset.get(key)+"]";
            if(it.hasNext())
                s += " and";
        }
        return s;
    }
}
