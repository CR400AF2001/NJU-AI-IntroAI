package othello;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.LinkedList;
import java.util.Random;

import gamePlayer.Action;
import gamePlayer.State;

/**
 * A State in the board game Othello.
 * @author Ashoat Tevosyan
 * @author Peter Brook
 * @since Sat April 23 2011
 * @version CSE 473
 */
public class OthelloState implements State {
	
	// Debug?
	private static final boolean DEBUG = false;
	// The dimension of this board. Since we're using shorts, must be less than or equal to 8.
	private static final int dimension = 8;
	private static final boolean ZOBRIST_HASHING = true;
        
	
	/**
	 * 00 = empty; 01 = ignored; 10 = white; 11 = black.
	 */
	
	// A lookup table to figure out the point differential in a line 
	private static byte[] pointTable = new byte[65536];
	
	/**
	 * Lookup table that has all results for lines from moves.
	 * First index is the current line... 2^16; all possible shorts.
	 * Second index is the index of the move... dimension possibilities.
	 * Third index is the player making the move... 2 players.
	 */
	private static short[][][] lookupTable = new short[65536][dimension][2];
	
	// Lookup table that has stability metrics for lines.
	private static float[] stabilityTable = new float[65536];
	
	// Lookup table that has valid moves for lines
	private static short[][] movesTable = new short[65536][2];
	
	// Lookup table storing the random long corresponding to each state of each square on the board
	private static int[][][] zobristHashes = new int[dimension][dimension][3];
	
	/**
	 * The static constructor to generate our tables.
	 */
	static {
		generateTables((short)0, (byte)0, (byte)0);
		generateHashValues();
		System.out.println("Finished generating tables!");
	}
	
	/**
	 * Generates random bitstrings which correspond to each state of each 
	 * board square. Used for hashing the state.
	 */
	private static void generateHashValues() {
		Random r = new Random(45111);
		for (int i=0; i < dimension; i++) {
			for (int j=0; j < dimension; j++) {
				for (int state=0; state < 3; state++) {
					zobristHashes[i][j][state] = r.nextInt();
				}
			}
		}
	}
	
	/**
	 * Recursively generate the points table.
	 * @param line   The line, as so far specified.
	 * @param points The points, as so far counted.
	 * @param depth  The depth we have so far traversed.
	 */
	private static void generateTables(short line, byte points, byte depth) {
		// End case?
		if (depth == dimension) {
			if (!isValid(line)) return;
			pointTable[line + 32768] = points;
			generateLookupAndMoveValue(line);
			generateStabilityValue(line);
			return;
		}
		// Recursive!
		line = (short)(line << 2);
		generateTables(line, points, (byte)(depth + 1));
		generateTables((short)(line | 1), points, (byte)(depth + 1));
		generateTables((short)(line | 2), (byte)(points + 1), (byte)(depth + 1));
		generateTables((short)(line | 3), (byte)(points - 1), (byte)(depth + 1));
	}
	
	/**
	 * Get an index on a line.
	 * @param line  The line to draw from.
	 * @param index The index on the line we want.
	 * @return The value at the index on this line.
	 */
	private static byte getSpotOnLine(short line, byte index) {
		if (index >= dimension) return 1;
		return (byte)((line >> (index * 2)) & 3);
	}

	/**
	 * Returns a spot represented as a char.
	 * @param x The x-coordinate of the spot we want to retrieve.
	 * @param y The y-coordinate of the spot we want to retrieve.
	 * @return ' ' if the spot is empty; 'O' if the spot has player 1; 'X' if the spot has player 2.
	 */
	public char getSpotAsChar(byte x, byte y) {
		byte spot = getSpotOnLine(hBoard[x], y);
		if (spot == 0) return ' ';
		return spot == 2 ? 'O' : 'X';
	}
	
	/**
	 * Set a value at an index on a line, and return the resultant line.
	 * @param line  The input line.
	 * @param index The input to set the value at.
	 * @param value The value to set.
	 * @return A new line that results from the specified mutation.
	 */
	private static short setSpotOnLine(short line, byte index, byte value) {
		if (index >= dimension) return line;
		short mask = (short)(3 << (index * 2)); 
		return (short)((line & ~mask) | (value << (index * 2)));
	}
	
	/**
	 * Combine the left side, center spot, and right side of a line. 
	 * @param left   The spots to the left of the center spot.
	 * @param center The center spot.
	 * @param right  The spots to the right of the center spot.
	 * @param index  The index at which the center spot is located.
	 * @return The combined line. 
	 */
	private static short combineIntoLine(short left, byte center, short right, byte index) {
		short leftMask = (short)((1 << (index * 2)) - 1);
		short rightMask = (short)~((1 << (index * 2 + 2)) - 1);
		return (short)((right & rightMask) | (left & leftMask) | (center << (index * 2)));
	}
	
	/**
	 * Parse a line into a String.
	 * @param line The line we are parsing.
	 * @return The String we have parsed into.
	 */
	private static String lineToString(short line) {
		StringBuilder builder = new StringBuilder();
		for (byte i = 0; i < dimension; i++) {
			builder.append(getSpotOnLine(line, i));
		}
		return builder.toString();
	}

	/**
	 * Get a short representing only the spots that have changed between two lines.
	 * @param line    The original state of the line.
	 * @param newLine The new state of the line.
	 * @param index   The index of the actual move.
	 * @return A short representing only the changed spots.
	 */
	private static short getFlippedSpots(short line, short newLine, byte index) {
		short mask = (short)~(3 << (index * 2));
		return (short)((newLine ^ line) & mask);
	}
	
	/**
	 * Reverse a line.
	 * @param line The line to reverse.
	 * @return The reversed line.
	 */
	private static short reverseLine(short line) {
		short reverse = 0;
		for (byte i = 0; i < dimension; i++) {
			reverse = (short)(reverse | getSpotOnLine(line, i));
			if (i + 1 == dimension) break;
			reverse = (short)(reverse << 2);
		}
		return reverse;
	}
	
	/**
	 * Check if a line is a valid and possible configuration.
	 * @param line The line to check.
	 * @return True if the line is valid; false otherwise.
	 */
	private static boolean isValid(short line) {
		byte i = 0;
		byte spot = getSpotOnLine(line, i);
		// Go through all the walls at the start
		while (spot == 1 && i < dimension) {
			i++;
			spot = getSpotOnLine(line, i);
		}
		if (i == dimension) return false;
		// Go through all the non-walls
		while (spot != 1) {
			i++;
			spot = getSpotOnLine(line, i);
		}
		// Make sure the second set of walls terminates
		while (spot == 1 && i < dimension) {
			i++;
			spot = getSpotOnLine(line, i);
		}
		return i == dimension;
	}
	
	/**
	 * Generate a lookup value for a particular line.
	 * @param line The line to generate the value for.
	 */
	private static void generateLookupAndMoveValue(short line) {
		short p1MoveLine = 0, p2MoveLine = 0;
		for (byte i = 0; i < dimension; i++) {
			// Is this spot already set?
			byte spot = getSpotOnLine(line, i);
			if (spot != 0) {
				if (DEBUG) System.out.println("Can't move on index " + i + "! Already occupied.");
				if (DEBUG) System.out.println(lineToString(line) + " resolves to itself");
				lookupTable[line + 32768][i][0] = line;
				lookupTable[line + 32768][i][1] = line;
				if ((i + 1) == dimension) break;
				p1MoveLine = (short)(p1MoveLine << 2);
				p2MoveLine = (short)(p2MoveLine << 2);
				continue;
			}
			// Build the right side of possible moves
			byte j = (byte)(i + 1);
			short p1Right = line, p2Right = line;
			byte firstByte = getSpotOnLine(line, j);
			// Is the first byte to the right of this one actually set?
			if (firstByte > 1 && j < dimension) {
				// Is the first byte to the right player 1 or player 2? 
				boolean first = firstByte == 2;
				while (true) {
					j++;
					if (j >= dimension) break;
					byte thisByte = getSpotOnLine(line, j); 
					if (thisByte < 2) break;
					if (first && thisByte == 2) continue;
					if (!first && thisByte == 3) continue;
					// The flips shall occur!
					if (DEBUG) System.out.println("Flips will occur on right!");
					j--;
					if (first) for (; j > i; j--) p2Right = setSpotOnLine(p2Right, j, (byte)3); 
					else for (; j > i; j--) p1Right = setSpotOnLine(p1Right, j, (byte)2);
					break;
				}
			}
			// Build the left side of possible moves
			j = (byte)(i - 1);
			short p1Left = line, p2Left = line;
			firstByte = getSpotOnLine(line, j);
			// Is the first byte to the right of this one actually set?
			if (firstByte > 1 && j > 0) {
				// Is the first byte to the right player 1 or player 2? 
				boolean first = firstByte == 2;
				while (true) {
					j--;
					if (j < 0) break;
					byte thisByte = getSpotOnLine(line, j); 
					if (thisByte < 2) break;
					// Keep going!
					if (first && thisByte == 2) continue;
					if (!first && thisByte == 3) continue;
					// The flips shall occur!
					if (DEBUG) System.out.println("Flips will occur on left!");
					j++;
					if (first) for (; j < i; j++) p2Left = setSpotOnLine(p2Left, j, (byte)3); 
					else for (; j < i; j++) p1Left = setSpotOnLine(p1Left, j, (byte)2);
					break;
				}
			}
			short p1 = combineIntoLine(p1Left, (byte)2, p1Right, i);
			short p2 = combineIntoLine(p2Left, (byte)3, p2Right, i);
			lookupTable[line + 32768][i][1] = p1;
			lookupTable[line + 32768][i][0] = p2;
			if (DEBUG) System.out.println(lineToString(line) + " resolves to " + lineToString(p1));
			if (DEBUG) System.out.println(lineToString(line) + " resolves to " + lineToString(p2));
			// Set to 2 so we can calculate sum quickly using pointsTable
			if (getFlippedSpots(line, p1, i) != 0) {
				p1MoveLine = (short)(p1MoveLine | 2);
			}
			if (getFlippedSpots(line, p2, i) != 0) {
				p2MoveLine = (short)(p2MoveLine | 2);
			}
			// If we're done, don't shift anymore
			if ((i + 1) == dimension) break;
			p1MoveLine = (short)(p1MoveLine << 2);
			p2MoveLine = (short)(p2MoveLine << 2);
		}
		movesTable[line + 32768][1] = reverseLine(p1MoveLine);
		movesTable[line + 32768][0] = reverseLine(p2MoveLine);
		if (DEBUG) System.out.println(lineToString(line) + " allows p1 " + lineToString(reverseLine(p1MoveLine)));
		if (DEBUG) System.out.println(lineToString(line) + " allows p2 " + lineToString(reverseLine(p2MoveLine)));
	}
	
	/**
	 * Generate a stability value for a particular line.
	 * @param line The line to generate the value for.
	 */
	private static void generateStabilityValue(short line) {
		float value = 0;
		byte index = 0; 
		byte spot = getSpotOnLine(line, index);
		// Get through all the initial walls
		while (spot == 1 && index < 8) {
			index++;
			spot = getSpotOnLine(line, index);
		}
		// Go until we find another wall
		byte last = 1;
		byte current = spot;
		byte seen = 0;
		while (spot != 1) { // spot == 1 when index <= dimension
			// No transition?
			if (spot == current) seen++;
			// Transition?
			else {
				// Should we count this?
				if (spot == 0) {
					if (last == 2 && current == 3) value += seen;
					else if (last == 3 && current == 2) value -= seen;
				} else if (last == 0) {
					if (spot == 2 && current == 3) value += seen;
					else if (spot == 3 && current == 2) value -= seen;
				}
				last = current;
				seen = 1;
				current = spot;
			}
			index++;
			spot = getSpotOnLine(line, index);
		}
		// Set the value in the stability table
		if (DEBUG) System.out.println(lineToString(line) + " resolves to " + value);
		stabilityTable[line + 32768] = value;
	}
	
	// Whose move is it? True = player 1; false = player 2.
	public final boolean move;
	// The bit-board storing horizontal lines
	private short[] hBoard; /* board 0 */
	// The bit-board storing vertical lines
	private short[] vBoard; /* board 1 */
	// The bit-boards storing diagonal lines
	private short[] dBoard1 /* board 2 */, dBoard2; /* board 3 */
	// Bit-boards representing valid moves
	private short[] p1MoveBoard, p2MoveBoard;
	// Our parent
	private OthelloState parent;
	// Our hash for the state that we represent
	private int hashValue;
	
	/**
	 * Initialize this child OthelloState.
	 * @param move The player whose move it is.
	 */
	private OthelloState(boolean move) {
		this.move = move;
	}
	
	/**
	 * Initialize a start-game OthelloState.
	 */
	public OthelloState() {
		this.move = true;
		this.hBoard = new short[dimension];
		this.vBoard = new short[dimension];
		// Diagonal boards have more shorts to store
		this.dBoard1 = new short[2 * dimension - 1];
		this.dBoard2 = new short[2 * dimension - 1];
		// Move boards
		this.p1MoveBoard = new short[dimension];
		this.p2MoveBoard = new short[dimension];
		this.generateMoveBoards();
	}
	
	/**
	 * Set the standard start state for Othello.
	 */
	public void setStandardStartState() {
		this.executeMove(false, (byte)3, (byte)3);
		this.executeMove(false, (byte)4, (byte)4);
		this.executeMove(true, (byte)3, (byte)4);
		this.executeMove(true, (byte)4, (byte)3);		
		this.generateMoveBoards();
	}
	
	/** {@inheritDoc} */
	@Override
	public int compareTo(State state) {
		float heuristic = this.heuristic() - state.heuristic();
		return heuristic >= 0 ? (int)Math.ceil(heuristic) : (int)Math.floor(heuristic);
	}
	
	/**
	 * Get the index of a spot within a line.
	 * Different boards will use different indexes for their lines.
	 * @param board The board we are checking.
	 * @param x     The x-coordinate of the spot.
	 * @param y     The y-coordinate of the spot.
	 * @return The index used for this spot.
	 */
	private static byte getIndex(byte board, byte x, byte y) {
		// Only boards 1 and 2 care about which is used as the index
		return board == 0 ? y : x;
	}
	
	/**
	 * Get the line index of a spot within a line.
	 * @param board The board we are checking.
	 * @param x     The x-coordinate of the spot.
	 * @param y     The y-coordinate of the spot.
	 * @return The line index used for this spot.
	 */
	private static byte getLineIndex(byte board, byte x, byte y) {
		if (board == 0) return x;
		if (board == 1) return y;
		if (board == 2) return (byte)(x + y);
		return (byte)(x - y + dimension - 1);
	}
	
	/**
	 * Get the lines relevant to a particular spot on the board.
	 * @param x The x-coordinate of the spot.
	 * @param y The y-coordinate of the spot.
	 * @return The four lines relevant to the specified spot.
	 */
	private short[] getLines(byte x, byte y) {
		short[] lines = new short[4];
		lines[0] = this.hBoard[getLineIndex((byte)0, x, y)];
		lines[1] = this.vBoard[getLineIndex((byte)1, x, y)];
		lines[2] = this.dBoard1[getLineIndex((byte)2, x, y)];
		lines[3] = this.dBoard2[getLineIndex((byte)3, x, y)];
		return lines;
	}
	
	/**
	 * Set a line on a board.
	 * @param board The bit-board to set the line on.
	 * @param line  The actual value to set that line to.
	 * @param x     The x-coordinate of the spot the move was on, so we can figure out the index of the line to change.
	 * @param y     The y-coordinate of the spot the move was on; see above.
	 */
	private void setLine(byte board, short line, short x, short y) {
		switch (board) {
			case 0:
				hBoard[x] = line;
				return;
			case 1:
				vBoard[y] = line;
				return;
			case 2:
				dBoard1[x + y] = line;
				return;
			case 3:
				dBoard2[x - y + dimension - 1] = line;
				return;
		}
	}
	
	/**
	 * Checks if a move on a spot by the current player is valid.
	 * @param x The x-coordinate of the move.
	 * @param y The y-coordinate of the move.
	 * @return  True if valid; false otherwise.
	 */
	public boolean moveIsValid(byte x, byte y, boolean player) {
		// Pass?
		if (x < 0) {
			short[] moveBoard = player ? this.p1MoveBoard : this.p2MoveBoard;
			for (short line : moveBoard) if (line != 0) return false;
			return true;
		}
		// Get current lines on bit-board
		short[] lines = this.getLines(x, y);
		// Check all bit-boards
		for (byte i = 0; i < 4; i++) {
			byte index = getIndex(i, x, y);
			short newLine = lookupTable[lines[i] + 32768][index][player ? 1 : 0];
			// You're moving on top of an occupied space
			if (newLine == lines[i]) return false;
			// A flip occurred, so this move is legal. Ignore changes in index spot.
			if (getFlippedSpots(lines[i], newLine, index) != 0) return true;
		}
		// No flips, so illegal
		return false;
	}
	
	/**
	 * Return a board based on its index.
	 * @param index The index of the board.
	 * @return The board at that index.
	 */
	private short[] getBoardFromIndex(byte index) {
		if (index == 0) return hBoard;
		if (index == 1) return vBoard;
		if (index == 2) return dBoard1;
		return dBoard2;
	}
	
	/**
	 * Get the partial moveBoards in a particular direction.
	 * @param boardIndex The index of the board to get.
	 * @return The partial moveBoard.
	 */
	private short[][] getPartialMoveBoards(byte boardIndex) {
		short[] inputBoard = getBoardFromIndex(boardIndex);
		short[][] outputBoards = new short[2][inputBoard.length];
		for (int i = 0; i < inputBoard.length; i++) {
			outputBoards[0][i] = movesTable[inputBoard[i] + 32768][0];
			outputBoards[1][i] = movesTable[inputBoard[i] + 32768][1];
		}
		return outputBoards;
	}
	
	/**
	 * Get a particular spot on a particular board.
	 * @param board      The board to read from.
	 * @param boardIndex The index of the type of board. 
	 * @param x          The x-coordinate of the spot.
	 * @param y          The y-coordinate of the spot.
	 * @return The requested spot on the provided board.
	 */
	private byte getSpotOnBoardAt(short[] board, byte boardIndex, byte x, byte y) {
		return getSpotOnLine(board[getLineIndex(boardIndex, x, y)], getIndex(boardIndex, x, y));
	}
	
	/**
	 * Generate bit-boards representing the validity of moves for each player.
	 * 00 = invalid; 10 = valid. 2 bits so we can use point lookup table.
	 */
	public void generateMoveBoards() {
		// This isn't pretty, but Java doesn't have better ways
		short[][] hBoards = getPartialMoveBoards((byte)0);
		short[][] vBoards = getPartialMoveBoards((byte)1);
		short[][] dBoard1s = getPartialMoveBoards((byte)2);
		short[][] dBoard2s = getPartialMoveBoards((byte)3);
		for (byte i = 0; i < dimension; i++) {
			for (byte j = 0; j < dimension; j++) {
				this.p1MoveBoard[i] = (short)(this.p1MoveBoard[i] |
						getSpotOnBoardAt(hBoards[1], (byte)0, i, j) |
						getSpotOnBoardAt(vBoards[1], (byte)1, i, j) |
						getSpotOnBoardAt(dBoard1s[1], (byte)2, i, j) |
						getSpotOnBoardAt(dBoard2s[1], (byte)3, i, j));
				this.p2MoveBoard[i] = (short)(this.p2MoveBoard[i] |
						getSpotOnBoardAt(hBoards[0], (byte)0, i, j) |
						getSpotOnBoardAt(vBoards[0], (byte)1, i, j) |
						getSpotOnBoardAt(dBoard1s[0], (byte)2, i, j) |
						getSpotOnBoardAt(dBoard2s[0], (byte)3, i, j));
				if (j + 1 == dimension) break;
				this.p1MoveBoard[i] = (short)(this.p1MoveBoard[i] << 2);
				this.p2MoveBoard[i] = (short)(this.p2MoveBoard[i] << 2);
			}
		}
		if (ZOBRIST_HASHING)
			computeHashValue();
	}

	/**
	 * Flip the value on a line at a particular index.
	 * @param line  The line to use.
	 * @param index The index to flip at.
	 * @return The resultant line.
	 */
	private static short flipValueOnLine(short line, byte index) {
		return (short)(line ^ (1 << (2 * index)));
	}
	
	/**
	 * Change a value on all boards.
	 * @param x     The x-coordinate to set at.
	 * @param y     The y-coordinate to set at.
	 * @param skip  A board to skip setting.
	 * @param value The value to set.
	 */
	private void flipValueOnBoards(byte x, byte y, byte skip) {
		short[] lines = this.getLines(x, y);
		for (byte i = 0; i < 4; i++) {
			if (i == skip) continue; 
			byte index = getIndex(i, x, y);
			short newLine = flipValueOnLine(lines[i], index);
			this.setLine(i, newLine, x, y);
		}
	}
	
	/**
	 * Set a spot to a value without repercussions.
	 * @param x     The x-coordinate of the spot.
	 * @param y     The y-coordinate of the spot.
	 * @param value The value to set to.
	 */
	public void setValueOnBoards(byte x, byte y, byte value) {
		short[] lines = this.getLines(x, y);
		for (byte i = 0; i < 4; i++) {
			byte index = getIndex(i, x, y);
			short newLine = setSpotOnLine(lines[i], index, value);
			this.setLine(i, newLine, x, y);
		}
	}
	
	/**
	 * Execute a move and mutate our bit-boards to represent this.
	 * We assume that the other player is making this move.
	 * @param x The x-coordinate of the move.
	 * @param y The y-coordinate of the move.
	 */
	private void executeMove(boolean player, byte x, byte y) {
		short[] lines = this.getLines(x, y);
		// Check all bit-boards
		for (byte i = 0; i < 4; i++) {
			byte index = getIndex(i, x, y);
			short newLine = lookupTable[lines[i] + 32768][index][player ? 1 : 0];
			// Get a short representing changed spots
			short flippedSpots = getFlippedSpots(lines[i], newLine, index);
			// Find the changed spots
			for (byte j = 0; j < dimension; j++) {
				// Is this spot changed?
				if (getSpotOnLine(flippedSpots, j) == 0) continue;
				// If it's changed, then flip it on all other boards!
				if (i == 0) this.flipValueOnBoards(x, j, i);
				else if (i == 1) this.flipValueOnBoards(j, y, i);
				else if (i == 2) this.flipValueOnBoards(j, (byte)(x + y - j), i);
				else if (i == 3) this.flipValueOnBoards(j, (byte)(j - x + y), i);
			}
			// Set the line that actually changed
			this.setLine(i, newLine, x, y);
		}
	}
	
	/**
	 * Get a new OthelloState that results from a move by the current player.
	 * @param x The x-coordinate of the move.
	 * @param y The y-coordinate of the move.
	 * @return A new OthelloState.
	 */
	public OthelloState childOnMove(byte x, byte y) {
		OthelloState state = (OthelloState)this.clone();
		state.parent = this;
		// Pass?
		if (x < 0) {
			state.p1MoveBoard = this.p1MoveBoard.clone();
			state.p2MoveBoard = this.p2MoveBoard.clone();
			return state;
		} else {
			state.p1MoveBoard = new short[dimension];
			state.p2MoveBoard = new short[dimension];
		}
		// Make the move
		state.executeMove(this.move, x, y);
		// Get move boards
		state.generateMoveBoards();
                
		return state;
	}
        
        /**
         * Get the number of checks for the two players
         * @return scores as int[2]
         */
        public int[] getScores(){
            int scores[] = new int[2];
            scores[0] = scores[1] = 0;
                for(byte j=0; j<dimension; j++){
                    short hb = hBoard[j];
                    for(byte i=0; i<dimension; i++){
                        byte spot = getSpotOnLine(hb, i);
                        if( spot==3 )scores[0] ++;
                        else if( spot==2 ) scores[1] ++;
                    }
                }
            return scores;
        }
	
	/**
	 * Get the stability metric for a particular board.
	 * @param board The board to get the stability metric for.
	 * @return The stability metric for the provided board.
	 */
	private float getStabilitySumForBoard(short[] board) {
		float metric = 0;
		for (short line : board) metric += stabilityTable[line + 32768];
		return metric;
	}
	
	/**
	 * Returns the difference between the stability metric for player 1 and player 2.
	 * @return The difference between the stability metric for player 1 and player 2
	 */
	private float stabilityDifferential() {
		return getStabilitySumForBoard(hBoard)
			 + getStabilitySumForBoard(vBoard)
		     + getStabilitySumForBoard(dBoard1)
		     + getStabilitySumForBoard(dBoard2);
	}
	
	/**
	 * Returns the difference between player 1 and player 2's total piece count.
	 * @return The difference between player 1 and player 2's total piece count.
	 */
	private float pieceDifferential() {
		float difference = 0;
		for (short line : hBoard) difference += pointTable[line + 32768];
		return difference;
	}
	
	/**
	 * Returns the difference between the amount of moves player one and player two have available.
	 * @return the difference between the amount of moves player one and player two have available.
	 */
	private float moveDifferential() {
		float differential = 0;
		for (byte i = 0; i < dimension; i++)
			differential += pointTable[p1MoveBoard[i] + 32768] - pointTable[p2MoveBoard[i] + 32768]; 
		return differential;
	}
	
	/**
	 * Get the difference between the number of corners player 1 and player 2 occupy.
	 * @return The aforementioned difference.
	 */
	private float cornerDifferential() {
		float diff = 0;
		short[] corners = new short[4];
		corners[0] = getSpotOnLine(hBoard[0], (byte)0);
		corners[1] = getSpotOnLine(hBoard[0], (byte)(dimension - 1));
		corners[2] = getSpotOnLine(hBoard[dimension - 1], (byte)0);
		corners[3] = getSpotOnLine(hBoard[dimension - 1], (byte)(dimension - 1));
		for (short corner : corners) if (corner != 0) diff += corner == 2 ? 1 : -1;
		return diff;
	}
	
	/** {@inheritDoc} */
	@Override
	public float heuristic() {
		//System.out.printf("%f %f %f %f\n",this.pieceDifferential(), this.moveDifferential(), this.cornerDifferential(), this.stabilityDifferential());
		Status s = this.getStatus();
		int winconstant = 0;
		switch (s) {
		case PlayerOneWon:
			winconstant = 5000;
			break;
		case PlayerTwoWon:
			winconstant = -5000;
			break;
		default:
			winconstant = 0;
			break;
		}
		return this.pieceDifferential() +
		   8 * this.moveDifferential() +
		  300 * this.cornerDifferential() +
		   1 * this.stabilityDifferential() + 
		   winconstant;
	}
	
	/** {@inheritDoc} */
	@Override
	@SuppressWarnings({ "rawtypes" })
	public List<Action> getActions() {
		List<Action> actions = new LinkedList<Action>();
		short[] moveBoard = this.move ? p1MoveBoard : p2MoveBoard;
		for (byte i = 0; i < dimension; i++) {
			for (byte j = 0; j < dimension; j++) {
				if (getSpotOnLine(moveBoard[i], j) != 0)
					actions.add(new OthelloAction(this.move, i, (byte)(dimension - j - 1)));
			}
		}
		if (actions.isEmpty()) actions.add(new OthelloAction(this.move, (byte)-1, (byte)-1));
		return actions;
	}
	
	/** {@inheritDoc} */
	@Override
	public Status getStatus() {
		// Make sure you only run this after generateMoveBoards!
		// Are there still moves to make?
		for (short line : p1MoveBoard) if (line != 0) return Status.Ongoing;
		for (short line : p2MoveBoard) if (line != 0) return Status.Ongoing;
		// Game over!
		float differential = this.pieceDifferential();
		if (differential == 0) return Status.Draw;
		return differential > 0 ? Status.PlayerOneWon : Status.PlayerTwoWon;
	}
	
	/** {@inheritDoc} */
	@Override
	public State getParentState() {
		return this.parent;
	}
	
	/**
	 * Returns a deep copy of this OthelloState.
	 * @return A deep copy of this OthelloState.
	 */
	@Override
	public Object clone() {
		OthelloState state = new OthelloState(!this.move);
		state.hBoard = this.hBoard.clone();
		state.vBoard = this.vBoard.clone();
		state.dBoard1 = this.dBoard1.clone();
		state.dBoard2 = this.dBoard2.clone();
		return state;
	}
	
	/**
	 * Returns a String representation of this OthelloState.
	 * @return A String representation of this OthelloState.
	 */
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		for (short line : this.hBoard) {
			String lineString = lineToString(line);
			for (int i = 0; i < lineString.length(); i++) {
				char c = lineString.charAt(i);
				if (c == '0') builder.append("   ");
				else if (c == '2') builder.append(" O ");
				else if (c == '3') builder.append(" X ");
				if (i + 1 == lineString.length()) break;
				builder.append("|");
			}
			builder.append("\n");
		}
		return builder.toString();
	}
	
	private void computeHashValue() {
		int hc = 0;
		//00 = empty; 01 = ignored; 10 = white; 11 = black.
		for (int i=0; i < dimension; i++) {
			for (int j=0; j < dimension; j++) {
				// get the value from the board
				short value = getSpotOnBoardAt(hBoard, (byte)0, (byte)i, (byte)j);
				// and convert it into 0, 1 or 2 (the state as an index into the zobristHash table
				int state = 0;
				switch (value) {
				case 0:
					state = 0;
					break;
				case 2:
					state = 1;
					break;
				case 3:
					state = 2;
					break;
				default:
					break;
				}
				
				if (i==0 && j==0) {
					hc = zobristHashes[i][j][state];
				} else {
					hc ^= zobristHashes[i][j][state];
				}
			}
		}
		hashValue = hc;
	}
	
	/**
	 * Returns an integer hash code for this OthelloState.
	 * If two objects are equal, then they must have the same hash code.
	 * @return An integer hash code for this OthelloState.
	 */
	@Override
	public int hashCode() {
		if (ZOBRIST_HASHING)
			return hashValue;
		else
			return hBoard[3] << 16 | hBoard[4];
		
	}
	
	/**
	 * Checks the equivalence between this OthelloState and another Object.
	 * Will screw up if passed anything that isn't an OthelloState.
	 * @param other The Object to compare this one with.
	 * @return True if equal; false otherwise.
	 */
	@Override
	public boolean equals(Object other) {
		OthelloState state = (OthelloState)other; 
		if (this.move != state.move) return false;
		return Arrays.equals(this.hBoard, state.hBoard);
	}
	
	/** {@inheritDoc} */
	@Override
	public String identifier() {
		BigInteger integer = BigInteger.ZERO;
		BigInteger additive = BigInteger.ZERO;
		for (int i = 0; i < 8; i++) {
			integer = integer.add(BigInteger.valueOf(this.hBoard[i]));
			additive = additive.add(BigInteger.valueOf(Short.MAX_VALUE));
			if (i == 8) break;
			integer = integer.shiftLeft(16);
			additive = additive.shiftLeft(16);
		}
		integer = integer.add(additive.divide(BigInteger.valueOf(2)));
		return (this.move ? "1" : "0") + integer.toString();
	}
	
}
