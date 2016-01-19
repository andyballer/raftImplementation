package edu.duke.raft;

import java.util.Timer;

public class FollowerMode extends RaftMode {

	int heartbeatTimeoutID = 1;
	Timer timer;
	int term;
	boolean inElection;

	public void go () {
		synchronized (mLock) {
			term = mConfig.getCurrentTerm();
			System.out.println ("S" + 
					mID + 
					"." + 
					term + 
					": switched to follower mode.");

			timer = scheduleTimer(randomTimeout(), heartbeatTimeoutID);
			inElection = false;
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

			timer.cancel();
			
			if (candidateTerm > term) {
				// New term, we haven't voted for anyone. 
				timer = scheduleTimer(randomTimeout(), heartbeatTimeoutID);
				term = candidateTerm;
				
				if (lastLogTerm > mLog.getLastTerm()) {
					mConfig.setCurrentTerm(candidateTerm, candidateID);
					return 0;
				} else if (lastLogTerm == mLog.getLastTerm()) {
					if (lastLogIndex >= mLog.getLastIndex()) {
						mConfig.setCurrentTerm(candidateTerm, candidateID);
						return 0;
					} else {
						mConfig.setCurrentTerm(candidateTerm, mConfig.getVotedFor());
						return term;
					}
				} else {
					mConfig.setCurrentTerm(candidateTerm, mConfig.getVotedFor());
					return term;
				}
				
			} else {
				timer = scheduleTimer(randomTimeout(), heartbeatTimeoutID);
				return term;
			}
			

			
			/*if (candidateTerm < term) {
				return term;
				
			} else {
				inElection = true;
				if (mConfig.getVotedFor()==0) {
					timer = scheduleTimer(randomTimeout(), heartbeatTimeoutID);
					mConfig.setCurrentTerm(candidateTerm, candidateID);
					return 0;
				}
				*/
				/*
				if (lastLogTerm > mLog.getLastTerm()) {
					
					// Vote, if we haven't voted yet.
					if (mConfig.getVotedFor() == 0) {
						mConfig.setCurrentTerm(candidateTerm, candidateID);
						timer = scheduleTimer(randomTimeout(), heartbeatTimeoutID);
						return 0;
					} else {
						mConfig.setCurrentTerm(candidateTerm, mConfig.getVotedFor());
					}
					
				} else if (lastLogTerm == mLog.getLastTerm()) {
					
					// They have a longer/same log.
					if (lastLogIndex >= mLog.getLastIndex()) {
						
						// Vote, if we haven't voted yet.
						if (mConfig.getVotedFor() == 0) {
							mConfig.setCurrentTerm(candidateTerm, candidateID);
							timer = scheduleTimer(randomTimeout(), heartbeatTimeoutID);
							return 0;
						} else {
							mConfig.setCurrentTerm(candidateTerm, mConfig.getVotedFor());
						}
					}
					
				} else {
					// Just update the term.
					mConfig.setCurrentTerm(candidateTerm, mConfig.getVotedFor());
				}
				*/
			//}
			
			//timer = scheduleTimer(randomTimeout(), heartbeatTimeoutID);
			//return term;
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
			
			if (leaderTerm < term) {
				return term;
			}
			
			if (leaderTerm > term) {
				mConfig.setCurrentTerm(leaderTerm, 0);
			}
			
			term = leaderTerm;
			
			if (leaderTerm == term) {
				// If we receive a heartbeat message, reset the timer.
				if (entries.length == 0) {
					timer.cancel();
					timer = scheduleTimer(randomTimeout(), heartbeatTimeoutID);
					return 0;
				}
			}

			if (mLog.insert(entries, prevLogIndex, prevLogTerm) == -1) {
				return term;
			} else {
				return 0;
			}
		}

	}  

	// @param id of the timer that timed out
	public void handleTimeout (int timerID) {
		synchronized (mLock) {
			if (timerID == heartbeatTimeoutID) {
				timer.cancel();
				RaftServerImpl.setMode(new CandidateMode());
			}

		}
	}
	
}

