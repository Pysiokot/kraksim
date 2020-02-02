package pl.edu.agh.cs.kraksim.weka.statistics;

import java.util.*;

public class Archive<T> implements Iterable<Integer> {
	private final List<Integer> turnList = new ArrayList<>();
	private final Map<Integer, T[]> congestionList = new HashMap<>();

	public void storeStatistics(int turn, T[] congestionTable) {
		turnList.add(0, turn);
		congestionList.put(turn, congestionTable);
	}

	public T getCongestionByTimeDistance(int timeDistance, int linkNumber) {
		int turn = turnList.get(timeDistance);
		return congestionList.get(turn)[linkNumber];
	}

	public T getCongestionByTurn(int turn, int linkNumber) {
		return congestionList.get(turn)[linkNumber];
	}

	public List<T> getCongestionListByTurn(int turn) {
		return Arrays.asList(congestionList.get(turn));
	}

	@Override
	public Iterator<Integer> iterator() {
		return turnList.iterator();
	}

	@Override
	public String toString() {
		StringBuilder text = new StringBuilder();
		for (Integer turn : turnList) {
			text.append(turn).append(", ");
			for (T congestion : congestionList.get(turn)) {
				text.append(congestion).append(", ");
			}
			text.append('\n');
		}
		return text.toString();
	}

	public List<Integer> getTurns() {
		return turnList;
	}

	public void clear() {
		turnList.clear();
		congestionList.clear();
	}
}
