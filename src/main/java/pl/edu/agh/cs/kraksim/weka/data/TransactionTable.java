package pl.edu.agh.cs.kraksim.weka.data;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class TransactionTable implements Iterable<Transaction> {
	private List<String> attributeNames = new ArrayList<>();
	private final List<Transaction> transactionArray = new ArrayList<>();

	public int getTransactionSize() {
		if (transactionArray.isEmpty()) {
			return 0;
		}
		return transactionArray.get(0).getTransaction().size();
	}

	public List<Transaction> getTransactions() {
		return transactionArray;
	}

	public void addTransaction(Transaction transaction) {
		transactionArray.add(transaction);
	}

	@Override
	public Iterator<Transaction> iterator() {
		return transactionArray.iterator();
	}

	public List<String> getAttributeNames() {
		return attributeNames;
	}

	public void clear() {
		transactionArray.clear();
	}

	public void setAttributeNames(List<String> attributeNames) {
		this.attributeNames = attributeNames;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		for (Transaction t : transactionArray) {
			List<Double> ali = t.getTransaction();
			for (int i = 0; i < ali.size(); i++) {
				String attributeName = attributeNames.get(i);
				Double attributeValue = ali.get(i);
				builder.append(attributeName).append(':').append(attributeValue).append(", ");
			}
			builder.append('\n');
		}
		return builder.toString();
	}
}
