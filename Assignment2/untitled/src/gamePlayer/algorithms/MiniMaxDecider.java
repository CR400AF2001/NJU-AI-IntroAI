package gamePlayer.algorithms;

import gamePlayer.Action;
import gamePlayer.Decider;
import gamePlayer.InvalidActionException;
import gamePlayer.State;
import gamePlayer.State.Status;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * This class represents an AI Decider that uses a MiniMax algorithm.
 * We use alpha-beta pruning, but besides that we're pretty vanilla.
 * @author Ashoat Tevosyan
 * @author Peter Brook 
 * @since Mon April 28 2011
 * @version CSE 473
 */
public class MiniMaxDecider implements Decider {
	
	// Are we maximizing or minimizing?
	private boolean maximize;
	// The depth to which we should analyze the search space
	private int depth;
	// HashMap to avoid recalculating States
	private Map<State, Float> computedStates;
	// Used to generate a graph of the search space for each turn in SVG format
	private static final boolean DEBUG = true;
	
	/**
	 * Initialize this MiniMaxDecider. 
	 * @param maximize Are we maximizing or minimizing on this turn? True if the former.
	 * @param depth    The depth to which we should analyze the search space.
	 */
	public MiniMaxDecider(boolean maximize, int depth) {
		this.maximize = maximize;
		this.depth = depth;
		computedStates = new HashMap<State, Float>();
	}
	
	/**
	 * Decide which state to go into.
	 * We manually MiniMax the first layer so we can figure out which heuristic is from which Action.
	 * Also, we want to be able to choose randomly between equally good options.
	 * "I'm the decider, and I decide what is best." - George W. Bush
	 * @param state The start State for our search.
	 * @return The Action we are deciding to take.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Action decide(State state) {
		// Choose randomly between equally good options
		float value = maximize ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;
		float alpha = Float.NEGATIVE_INFINITY;
		float beta = Float.POSITIVE_INFINITY;
		List<Action> bestActions = new ArrayList<Action>();
		// Iterate!
		int flag = maximize ? 1 : -1;
		for (Action action : state.getActions()) {
			try {
				// Algorithm!
				State newState = action.applyTo(state);
				float newValue = this.miniMaxRecursor(newState, 1, !this.maximize, alpha, beta);
				// Better candidates?
				if (flag * newValue > flag * value) {
					value = newValue;
					bestActions.clear();
				}
				// Add it to the list of candidates?
				if (flag * newValue >= flag * value) bestActions.add(action);
			} catch (InvalidActionException e) {
				throw new RuntimeException("Invalid action!");
			}
		}
		// If there are more than one best actions, pick one of the best randomly
		Collections.shuffle(bestActions);
		return bestActions.get(0);
	}
	
	/**
	 * The true implementation of the MiniMax algorithm!
	 * Thoroughly commented for your convenience.
	 * @param state    The State we are currently parsing.
	 * @param alpha    The alpha bound for alpha-beta pruning.
	 * @param beta     The beta bound for alpha-beta pruning.
	 * @param depth    The current depth we are at.
	 * @param maximize Are we maximizing? If not, we are minimizing.
	 * @return The best point count we can get on this branch of the state space to the specified depth.
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public float miniMaxRecursor(State state, int depth, boolean maximize, float alpha, float beta) {
		// Has this state already been computed?
		if (computedStates.containsKey(state)) 
                    // Return the stored result
                    return computedStates.get(state);
		// Is this state done?
		if (state.getStatus() != Status.Ongoing)
                    // Store and return
                    return finalize(state, state.heuristic());
		// Have we reached the end of the line?
		if (depth == this.depth)
                    //Return the heuristic value
                    return state.heuristic();
                
		// If not, recurse further. Identify the best actions to take.
		float value = maximize ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;
		int flag = maximize ? 1 : -1;
		List<Action> test = state.getActions();
		for (Action action : test) {
			// Check it. Is it better? If so, keep it.
			try {
				State childState = action.applyTo(state);
				float newValue = this.miniMaxRecursor(childState, depth + 1, !maximize, alpha, beta);
				//Record the best value
				if (flag * newValue > flag * value){
					value = newValue;
				}
				//加入alpha-beta剪枝
				if(maximize){
					if(value >= beta)
						return finalize(state, value);
					if(value > alpha)
						alpha = value;
				}
				else{
					if(value <= alpha)
						return finalize(state, value);
					if(value < beta)
						beta = value;
				}
			} catch (InvalidActionException e) {
                                //Should not go here
				throw new RuntimeException("Invalid action!");
			}
		}
		// Store so we don't have to compute it again.
		return finalize(state, value);
	}
	
	/**
	 * Handy private function to stick into HashMap before returning.
	 * We don't always want to stick into our HashMap, so use carefully.
	 * @param state The State we are hashing.
	 * @param value The value that State has.
	 * @return The value we were passed.
	 */
	private float finalize(State state, float value) {
		// THIS IS BROKEN DO NOT USE
		//computedStates.put(state, value);
		return value;
	}
	
}