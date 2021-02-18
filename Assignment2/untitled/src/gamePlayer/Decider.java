package gamePlayer;

/**
 * A Decider is a player in a turn-based deterministic game.
 * They decide on an Action to take in a particular State.
 * This class is agnostic to the game it is playing.
 * All relevant decision-making is left to methods on the State class.
 * AIs will uses the heuristics and the such.
 * Humans will probably decide based on printed representations of the State.
 * @author Ashoat Tevosyan
 * @since Mon April 18 2011
 * @version CSE 473
 */
public interface Decider {
	
	/**
	 * Given a State, decide on an Action to take.
	 * @param state The current State we are on.
	 * @return The Action we are going to take.
	 */
	@SuppressWarnings("rawtypes")
	public Action decide(State state);
	
}