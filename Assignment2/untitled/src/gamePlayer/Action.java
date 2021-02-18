package gamePlayer;

/**
 * An Action is a possible translation from one State to another.
 * Each Action is specific to a State; as such, we need to know which State we are dealing with.
 * @param <T> The State we are an Action for.
 * @author Ashoat Tevosyan
 * @since Mon April 18 2011
 * @version CSE 473
 */
public interface Action<T extends State> {
	
	/**
	 * Can this Action be applied to this State?
	 * @param input The State we are testing this Action on.
	 * @return True if application is possible; false otherwise.
	 */
	public boolean validOn(T input);
	
	/**
	 * Actually apply this Action and return the resultant State 
	 * @param input The State we are applying this Action on.
	 * @return The resultant State from the application of this Action.
	 * @throws InvalidActionException Some Actions can't be applied to some States.
	 */
	public T applyTo(T input) throws InvalidActionException;
	
	/**
	 * For traces, we need to be able to print out a String representation of this Action.
	 * @return A String representation of this Action.
	 */
	@Override
	public String toString();

	/**
	 * Implementers must be able to compare two actions to see if they are the same.
	 * @param obj 
	 * @return
	 */
	@Override
	public boolean equals(Object obj);
}