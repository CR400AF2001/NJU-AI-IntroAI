package gamePlayer.algorithms;

import java.util.LinkedHashMap;

@SuppressWarnings("serial")
public class TranspositionTable<K, V> extends LinkedHashMap<K, V> {

	private int MAX_ENTRIES;

	public TranspositionTable(int maxEntries) {
		super(maxEntries, 0.75f, true);
		this.MAX_ENTRIES = maxEntries;
	}
	
	@Override
	protected boolean removeEldestEntry(java.util.Map.Entry<K, V> eldest) {
		return size() > MAX_ENTRIES;
	}

}
