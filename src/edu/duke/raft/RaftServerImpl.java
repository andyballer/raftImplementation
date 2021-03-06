package edu.duke.raft;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class RaftServerImpl extends UnicastRemoteObject 
  implements RaftServer {

  private static int mID;
  private static RaftMode mMode;
  
  // @param server's unique id
  public RaftServerImpl (int serverID) throws RemoteException {
    mID = serverID;
  }

  // @param the server's current mode
  public static void setMode (RaftMode mode) {
    if (mode == null) {
      return;
    }
    // only change to a new mode
    if ((mMode == null) || 
        (mMode.getClass () != mode.getClass ())) {
      mMode = mode;
      mode.go ();    
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
			  int lastLogTerm) 
    throws RemoteException {
    return mMode.requestVote (candidateTerm, 
			      candidateID, 
			      lastLogIndex,
			      lastLogTerm);
  }

  // @return 0, if server appended entries; otherwise, server's
  // current term
  public int appendEntries (int leaderTerm,
			    int leaderID,
			    int prevLogIndex,
			    int prevLogTerm,
			    Entry[] entries,
			    int leaderCommit) 
    throws RemoteException  {
    return mMode.appendEntries (leaderTerm,
				leaderID,
				prevLogIndex,
				prevLogTerm,
				entries,
				leaderCommit);
  }
}

  
