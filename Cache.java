import java.util.*;

public class Cache {
	private int blockSize;
	private Vector<byte[]> pages; // you may use: private byte[][] = null;
	private int victim;

	private class Entry {
		public static final int INVALID = -1;
		public boolean reference;
		public boolean dirty;
		public int frame;
		public Entry( ) {
			reference = false;
			dirty = false;
			frame = INVALID;
		}
	}
	private Entry[] pageTable = null;

	private int nextVictim( ) {
		// implement by yourself
		int prevVictim = victim;

		if(prevVictim++ >= pageTable.length - 1) // if prevVic is at the end, circle back
			prevVictim = 0;
		else
			prevVictim = prevVictim++;

		victim = prevVictim;

		int level = 0; // level is the different cases

		while(true) {

			switch(level) { // switch case to define the cases
				case 0:
					// reference and dirty are both (0,0)
					if (pageTable[victim].reference == false && pageTable[victim].dirty == false) {
						return victim; // ideal case
					} else if (victim == pageTable.length - 1) {
						victim = prevVictim; // circle back to the beginning and increment the level
						level++;
					} else
						victim++;
				break;

				case 1:
					// reference and dirty are (0,1)
					if (pageTable[victim].reference == false && pageTable[victim].dirty == true) {
						writeBack(victim);
						return victim;
					} else if (victim == pageTable.length - 1) { // circle back to the beginning
						victim = prevVictim;
						level++;
					} else
						victim++;
				break;

				case 2:
					// reference and dirty are (1,0)
					if (pageTable[victim].reference == true && pageTable[victim].dirty == false) {
						pageTable[victim].reference = false;
						level = 0;
					} else if (victim == pageTable.length - 1) {
						victim = prevVictim;
						level++;
					} else
						victim++;
				break;

				case 3:
					// reference and dirty are (1,1)
					if (pageTable[victim].reference == true && pageTable[victim].dirty == true) {
						pageTable[victim].reference = false;
						level = 0;
					} else if (victim == pageTable.length - 1) {
						victim = prevVictim;
						level++;
					} else
						victim++;
				break;
			}
		}
	}

	private void writeBack( int victimEntry ) {
		if ( pageTable[victimEntry].frame != Entry.INVALID &&
				pageTable[victimEntry].dirty == true ) {
			SysLib.rawwrite(pageTable[victimEntry].frame, (byte[]) pages.elementAt(victimEntry)); // implement by yourself
			pageTable[victimEntry].dirty = false;
		}
	}

	public Cache( int blockSize, int cacheBlocks ) {

		this.blockSize = blockSize;
		pages = new Vector<byte[]>();// instantiate pages

		for(int i = 0; i < cacheBlocks; i++)
		{
			byte[] newPage = new byte[blockSize];
			pages.addElement(newPage);
		}
		victim = cacheBlocks - 1;
		pageTable = new Entry[cacheBlocks];

		for(int i = 0; i < cacheBlocks; i++) {
			pageTable[i] = new Entry();
		}

		// instantiate and initialize pageTable
	}

	public synchronized boolean read( int blockId, byte buffer[] ) {
		if ( blockId < 0 ) {
			SysLib.cerr( "threadOS: a wrong blockId for cread\n" );
			return false;
		}

		// locate a valid page
		for ( int i = 0; i < pageTable.length; i++ ) {
			if ( pageTable[i].frame == blockId ) {
				// cache hit!!
				byte[] temp = new byte[blockSize];
				temp = pages.elementAt(i);
				// copy pages[i] to buffer
				for(int j = 0; j < blockSize; j++) {
					buffer[j] = temp[j];
				}
				pageTable[i].reference = true;
				return true;
			}
		}

		// page miss!!
		// find an invalid page
		// if no invalid page is found, all pages are full
		//    seek for a victim
		int victimEntry = -3;

		for(int i = 0; i < pageTable.length; i++) {
			if(pageTable[i].frame == -1) {
				victimEntry = i;
				break;
			}
		}

		if(victimEntry == -3) {
			victimEntry = nextVictim();
		}

		// write back a dirty copy
		writeBack( victimEntry );
		// read a requested block from disk
		SysLib.rawread(blockId, buffer);


		// cache it
		byte[] page = new byte[blockSize];
		page = pages.elementAt(victimEntry);

		// copy pages[victimEntry] to buffer
		for(int i = 0; i < blockSize; i++){
			buffer[i] = page[i];
		}

		pages.set(victimEntry, page);

		pageTable[victimEntry].frame = blockId;
		pageTable[victimEntry].reference = true;
		return true;
	}

	public synchronized boolean write( int blockId, byte buffer[] ) {
		if ( blockId < 0 ) {
			SysLib.cerr( "threadOS: a wrong blockId for cwrite\n" );
			return false;
		}

		// locate a valid page
		for ( int i = 0; i < pageTable.length; i++ ) {
			if ( pageTable[i].frame == blockId ) {
				// cache hit
				byte[] temp = new byte[blockSize];
				temp = pages.elementAt(i);
				// copy buffer to pages[i]
				for(int j = 0; j < blockSize; j++) {
					buffer[j] = temp[j];
				}
				// pages.set(i, temp);
				pageTable[i].reference = true;
				pageTable[i].dirty = true;
				return true;
			}
		}

		// page miss
		// find an invalid page
		// if no invalid page is found, all pages are full.
		//    seek for a victim
		int victimEntry = -4;

		for(int i = 0; i < pageTable.length; i++) {
			if(pageTable[i].frame == -1) {
				victimEntry = i;
				break;
			}
		}

		if(victimEntry == -4) {
			victimEntry = nextVictim();
		}

		// write back a dirty copy
		writeBack( victimEntry );

		// cache it but not write through.
		// copy buffer to pages[victimEntry]
		pages.set(victimEntry, buffer);

		pageTable[victimEntry].frame = blockId;
		pageTable[victimEntry].reference = true;
		pageTable[victimEntry].dirty = true;
		return true;
	}

	public synchronized void sync( ) {
		for ( int i = 0; i < pageTable.length; i++ )
			writeBack( i );
		SysLib.sync( );
	}

	public synchronized void flush( ) {
		for ( int i = 0; i < pageTable.length; i++ ) {
			writeBack( i );
			pageTable[i].reference = false;
			pageTable[i].frame = Entry.INVALID;
		}
		SysLib.sync( );
	}
}
