package edu.duke.raft;

import java.util.Timer;

public class CandidateMode extends RaftMode {

	int electionTimerID = 5;
	Timer electionTimer;
	int term;
	
	int checkingTimerID = 4;
	Timer checkVotes;
	long checkInterval = 1;

	public void go () {
		synchronized (mLock) {
			// Increment term, vote for myself.
			mConfig.setCurrentTerm(mConfig.getCurrentTerm()+1, mID);
			term = mConfig.getCurrentTerm(); 

			RaftResponses.init(mConfig.getNumServers(), term);
			
			System.out.println ("S" + 
					mID + 
					"." + 
					term + 
					": switched to candidate mode.");
			
			voteForMyself();
			
			callElection();
			
			checkVotes = scheduleTimer(checkInterval, checkingTimerID);

			electionTimer = scheduleTimer(randomTimeout(), electionTimerID);

		}
	}

	private boolean voteForMyself() {
		return RaftResponses.setVote(mID, 0, term);
	}

	// Sends RPC requests for votes to all of the other servers.
	private void callElection() {
		for (int i=1; i<=mConfig.getNumServers(); i++){
			if (i != mID) {
				remoteRequestVote(i, term, mID, mLog.getLastIndex(), mLog.getLastTerm());
			}
		}
	}

	private long randomTimeout() {
		return (long) (Math.random() * (ELECTION_TIMEOUT_MAX - ELECTION_TIMEOUT_MIN) + ELECTION_TIMEOUT_MIN);
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
				mConfig.setCurrentTerm(candidateTerm, candidateID);
				electionTimer.cancel();
				checkVotes.cancel();
				RaftServerImpl.setMode(new FollowerMode());
			} 
			
			// We already voted for ourselves ok.
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
	public int appendEntries(int leaderTerm,
			int leaderID,
			int prevLogIndex,
			int prevLogTerm,
			Entry[] entries,
			int leaderCommit){
		synchronized (mLock) {
			// Oh shit there's another leader
			if (leaderTerm >= term) {
				RaftResponses.clearVotes(term);
				checkVotes.cancel();
				electionTimer.cancel();
				RaftServerImpl.setMode(new FollowerMode());
			} 

			return term;

		}

	}
	
	// True if this server got the majority of votes.
	private boolean hasMajorityVote() {
		boolean has_majority = false;

		int[] votes = RaftResponses.getVotes(term);
		int myVotes = 0;
		
		//System.out.println(votes);

		if (votes==null) {
			return false;
			
		} else {
			//System.out.println(term);
			for (int i=0; i<votes.length; i++){
				//System.out.print(votes[i]+" ");
				if (votes[i]==0) {
					myVotes++;
				}
			}
			//System.out.println("");
			if (myVotes > mConfig.getNumServers()-myVotes) {
				has_majority = true;
			} 
		}
		return has_majority;	
	}

	// @param id of the timer that timed out
	public void handleTimeout(int timerID){

		synchronized (mLock) {
			if (timerID == electionTimerID) {
				if (hasMajorityVote()) {
					// Switching to a leader.
					RaftResponses.clearVotes(term);
					electionTimer.cancel();
					checkVotes.cancel();
					RaftServerImpl.setMode(new LeaderMode());
				} else {
					// Calling a new election.
					RaftResponses.clearVotes(term);
					electionTimer.cancel();
					checkVotes.cancel();
					RaftServerImpl.setMode(new CandidateMode());
				}
				
			} else if (timerID == checkingTimerID) {
				checkVotes.cancel();
				if (hasMajorityVote()) {
					// Switching to a leader.
					RaftResponses.clearVotes(term);
					electionTimer.cancel();
					RaftServerImpl.setMode(new LeaderMode());
				} else {
					checkVotes = scheduleTimer(checkInterval, checkingTimerID);
				}
			}

		}
	}
}