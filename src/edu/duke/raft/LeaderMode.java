package edu.duke.raft;

import java.util.Timer;

public class LeaderMode extends RaftMode {

	Timer heartbeatTimer;
	int heartbeatTimerID = 14;
	int term;
	
	int[] nextIndex;

	int highestCommitted;
	
	Timer dumbServersTimer;
	int dumbServerID = 76;

  public void go () {
	 synchronized (mLock) {
    	term = mConfig.getCurrentTerm();
    	RaftResponses.init(mConfig.getNumServers(), term);
    	
    	System.out.println ("S" + 
			  mID + 
			  "." + 
			  term + 
			  ": switched to leader mode.");

    	sendHeartbeats();
        // The ID for the heartbeat timer = 14.
        heartbeatTimer = scheduleTimer(HEARTBEAT_INTERVAL, heartbeatTimerID);
        
        highestCommitted = mLog.getLastIndex();
        
        nextIndex = new int[mConfig.getNumServers()+1];
        
        
        //Entry entry0 = new Entry(term, 3);
        //Entry entry1 = new Entry(term, 3);
        
        Entry[] testEntries = new Entry[2];
        
        //testEntries[0] = entry0;
        //testEntries[1] = entry1;
        
        appendEntries(term, mID, mLog.getLastIndex(), mLog.getLastTerm(), testEntries, highestCommitted);
    }
  }

  // @param candidate’s term
  // @param candidate requesting vote
  // @param index of candidate’s last log entry
  // @param term of candidate’s last log entry
  // @return 0, if server votes for candidate; otherwise, server's
  // current term
  public int requestVote (int candidateTerm,
			  int candidateID,
			  int lastLogIndex,
			  int lastLogTerm) {
    synchronized (mLock) {
    	
    	if (candidateTerm > term) {
    		term = candidateTerm;
    		mConfig.setCurrentTerm(candidateTerm, 0);
    		heartbeatTimer.cancel();
    		RaftServerImpl.setMode(new FollowerMode());
    	} 
    	
    	return term;
    }
  }
  

  // @param leader’s term
  // @param current leader
  // @param index of log entry before entries to append
  // @param term of log entry before entries to append
  // @param entries to append (in order of 0 to append.length-1)
  // @param index of highest committed entry
  // @return 0, if server appended entries; otherwise, server's
  // current term
  public int appendEntries (int leaderTerm,
			    int leaderID,
			    int prevLogIndex,
			    int prevLogTerm,
			    Entry[] entries,
			    int leaderCommit) {
    synchronized (mLock) {
    	
    	// Bad request.
    	if (leaderTerm < mConfig.getCurrentTerm() || prevLogIndex != mLog.getLastIndex()
    			|| prevLogTerm != mLog.getLastTerm()) {
    		return term;
    	}
    	
    	// Setting next index to be the index where I want them to append.
    	for (int j=0; j<nextIndex.length; j++) {
    		nextIndex[j] = mLog.getLastIndex() + 1;
    	}
    	
    	int originalNextIndex = mLog.getLastIndex() + 1;
    	
    	// Adding the stuff to my own log.
    	/*if (mLog.insert(entries, prevLogIndex, prevLogTerm) == -1) {
    		return term;
    	} else {
    		// My last index should have changed.
    		nextIndex[mID] = mLog.getLastIndex() + 1;
    	}*/
    	
    	// Sending the remote request to everyone.
    	for (int i=1; i<=mConfig.getNumServers(); i++) {
    		if (i != mID) {
    			remoteAppendEntries(i, term, mID, prevLogIndex, prevLogTerm, entries, leaderCommit);
    		}
    	}
    	
    	boolean hasMajority = false;
    	
    	// Keep sending RPCs until we have a majority.
    	while (!hasMajority) {
    		
	    	// Tallying my responses.
	    	int[] responses = RaftResponses.getAppendResponses(term);
	    	int countResponses = 0;
	    	
	    	// Iterating through each of the follower servers.
	    	for (int p=1; p<responses.length; p++) {
	    		if (p != mID) {
		    		// Successfully appended
		    		if (responses[p] == 0) {
		    			countResponses++;
		    			// Caught up with the leader.
		    			nextIndex[p] = mLog.getLastIndex() + 1;
		    		} else {
		    			// Go back one and try again.
		    			nextIndex[p] = nextIndex[p] - 1;
		    			
		    			int difference = originalNextIndex - nextIndex[p];
		    			
		    			Entry[] newEntries = new Entry[entries.length+difference];
		    			
		    			for (int x = 0; x<difference; x++) {
		    				// Does this work for blank entries?
		    				newEntries[x] = mLog.getEntry(nextIndex[p] + x);
		    			}
		    			
		    			for (int k=difference; k<entries.length+difference; k++) {
		    				newEntries[k] = entries[k-difference];
		    			}
		    			
		    			int prevLogTerm2 = mLog.getEntry(nextIndex[p]-1).term;
		    			
		    			remoteAppendEntries(p, term, mID, nextIndex[p]-1, prevLogTerm2, newEntries, highestCommitted);
		    		}
	    		}
	    	}
	    	
	    	// Don't count myself, the leader. See if there's majority.
	    	if (countResponses > mConfig.getNumServers() - 1 - countResponses) {
	    		hasMajority = true;
	    		break;
	    	}
    	}
    	
    	int[] laterResponses = RaftResponses.getAppendResponses(term);
 
    	// Make sure that everything is up to date.
    	for (int b=1; b<laterResponses.length; b++) {
    		if (laterResponses[b]==0) {
    			if (nextIndex[b] != mLog.getLastIndex()+1) {
    				nextIndex[b] = mLog.getLastIndex()+1;
    			}
    		}
    	}
    	
    	if (hasMajority) {
    		highestCommitted = mLog.getLastIndex();
    		dumbServersTimer = scheduleTimer(dumbServerID, 3);
    		return 0;
    	} else {
    		return term;
    	}
    	
    }
  }
  
  private void appendToDumbServers() {
	  
	  dumbServersTimer.cancel();
	  
	  int caughtUpServers = 0;
	  
	  int pHighestCommitted = mLog.getLastIndex();
	  
	  while (caughtUpServers < mConfig.getNumServers()) {
		  
		  caughtUpServers = 0;
		  
		  for (int i=1; i<nextIndex.length; i++) {
			  
			  if (nextIndex[i] == pHighestCommitted+1) {
				  caughtUpServers++;
			  } else {
				  // Problematic.
				  
				  int difference = pHighestCommitted - nextIndex[i] + 1;
				  
				  Entry[] newEntries = new Entry[difference];
				  
				  for (int j=1; j<=difference; j++) {
					  newEntries[j-1] = mLog.getEntry(pHighestCommitted - (difference - j));
				  }
				  
				  int prevLogTerm = mLog.getEntry(nextIndex[i]-1).term;
				  
				  remoteAppendEntries(i, term, mID, nextIndex[i]-1, prevLogTerm, newEntries, pHighestCommitted);
				  
				  int[] responses = RaftResponses.getAppendResponses(term);
				  
				  if (responses[i] == 0) {
					  nextIndex[i] = pHighestCommitted + 1;
					  caughtUpServers++;
				  } else {
					  nextIndex[i] = nextIndex[i] - 1;
				  }
				  
			  }
		  }
	  }

  }
  
  // @return 0, if the server sent a heartbeat; otherwise, server's
  // current term
  private void sendHeartbeats() {
	    Entry[] empty = new Entry[0];
	    
	    // Is the highest index in leader's log the highest committed index?
	    for (int i=1; i<=mConfig.getNumServers(); i++) {
	    	if (i != mID) {
	    		remoteAppendEntries(i, term, mID, mLog.getLastIndex(), mLog.getLastTerm(), empty, highestCommitted);
	    	}
	    }
  }
  
  // @param id of the timer that timed out
  public void handleTimeout (int timerID) {
    synchronized (mLock) {

      if (timerID == heartbeatTimerID) {
    	 sendHeartbeats();
    	 
    	 heartbeatTimer.cancel();
    	 // Reset the heartbeat timer.
    	 heartbeatTimer = scheduleTimer(HEARTBEAT_INTERVAL, heartbeatTimerID);
      }
      
      if (timerID == dumbServerID) {
    	  appendToDumbServers();
      }
      
    }
  }

}


