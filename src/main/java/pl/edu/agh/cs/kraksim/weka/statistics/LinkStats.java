package pl.edu.agh.cs.kraksim.weka.statistics;

import pl.edu.agh.cs.kraksim.weka.PredictionSetup;
import pl.edu.agh.cs.kraksim.weka.data.LinkInfo;
import pl.edu.agh.cs.kraksim.weka.utils.Neighbours;

import java.util.*;
import java.util.Map.Entry;

public class LinkStats {
	private final Map<LinkInfo, Long> linkCongestions = new HashMap<>();
	private final Map<LinkInfo, Long> linkTPCongestions = new HashMap<>();
	private final Map<LinkInfo, Long> linkTNCongestions = new HashMap<>();
	private final Map<LinkInfo, Long> linkFNCongestions = new HashMap<>();
	private final Map<LinkInfo, Long> linkFPCongestions = new HashMap<>();

	public LinkStats(PredictionSetup setup) {
		Map<LinkInfo, Neighbours> neighbourArray = setup.getNeighbourArray();
		Set<LinkInfo> links = neighbourArray.keySet();
		for (LinkInfo link : links) {
			linkCongestions.put(link, 0L);
			linkTPCongestions.put(link, 0L);
			linkTNCongestions.put(link, 0L);
			linkFNCongestions.put(link, 0L);
			linkFPCongestions.put(link, 0L);
		}
	}

	public void countCongestionOnLink(int linkNumber) {
		LinkInfo link = new LinkInfo(linkNumber, "", 0);
		linkCongestions.put(link, linkCongestions.get(link) + 1);
	}

	@Override
	public String toString() {
		StringBuilder sb= new StringBuilder("\nLINK STATS\n\n");
		List<Entry<LinkInfo, Long>> links = sortLinksByCongestions();
		for (Entry<LinkInfo, Long> entry : links) {
			LinkInfo link = entry.getKey();
			Long congestions = entry.getValue();
			sb.append(link.linkId).append(": ").append(formatCongestions(congestions)).append(' ').append(tpString(link)).append(' ').append(fnString(link)).append(' ').append(tnString(link)).append(' ').append(fpString(link)).append('\n');
		}
		return sb.toString();
	}

	private List<Entry<LinkInfo, Long>> sortLinksByCongestions() {
		List<Entry<LinkInfo, Long>> sortedLinks = new ArrayList<>(linkCongestions.entrySet());
		Collections.sort(sortedLinks, new Comparator<Entry<LinkInfo, Long>>() {

			@Override
			public int compare(Entry<LinkInfo, Long> e1, Entry<LinkInfo, Long> e2) {
				return e2.getValue().compareTo(e1.getValue());
			}
		});

		return sortedLinks;
	}

	public void countTruePositive(int linkNumber) {
		LinkInfo link = new LinkInfo(linkNumber, "", 0);
		linkTPCongestions.put(link, linkTPCongestions.get(link) + 1);
	}

	public void countFalseNegative(int linkNumber) {
		LinkInfo link = new LinkInfo(linkNumber, "", 0);
		linkFNCongestions.put(link, linkFNCongestions.get(link) + 1);
	}

	public void countFalsePositive(int linkNumber) {
		LinkInfo link = new LinkInfo(linkNumber, "", 0);
		linkFPCongestions.put(link, linkFPCongestions.get(link) + 1);
	}

	public void countTrueNegative(int linkNumber) {
		LinkInfo link = new LinkInfo(linkNumber, "", 0);
		linkTNCongestions.put(link, linkTNCongestions.get(link) + 1);
	}

	public String tnString(LinkInfo link) {
		Long tn = linkTNCongestions.get(link);
		Long fp = linkFPCongestions.get(link);

		long nonCongestions = tn + fp;
		double predicted = (double) tn / nonCongestions;

		return format("TN", tn, predicted);
	}

	public String fpString(LinkInfo link) {
		Long tn = linkTNCongestions.get(link);
		Long fp = linkFPCongestions.get(link);

		long nonCongestions = tn + fp;
		double nonPredicted = (double) fp / nonCongestions;

		return format("FP", fp, nonPredicted);
	}

	public String tpString(LinkInfo link) {
		Long tp = linkTPCongestions.get(link);
		Long fn = linkFNCongestions.get(link);

		long congestions = tp + fn;
		double predicted = (double) tp / congestions;

		return format("TP", tp, predicted);
	}

	public String fnString(LinkInfo link) {
		Long tp = linkTPCongestions.get(link);
		Long fn = linkFNCongestions.get(link);

		long congestions = tp + fn;
		double nonPredicted = (double) fn / congestions;

		return format("FN", fn, nonPredicted);
	}

	public String format(String s, long l, double d) {
		return String.format(s + ": %8d[%.3f]", l, d);
	}

	private String formatCongestions(Long congestions) {
		return String.format("%8d", congestions);
	}
}
