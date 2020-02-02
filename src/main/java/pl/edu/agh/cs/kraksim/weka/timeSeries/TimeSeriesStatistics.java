package pl.edu.agh.cs.kraksim.weka.timeSeries;

import pl.edu.agh.cs.kraksim.core.Link;
import pl.edu.agh.cs.kraksim.weka.PredictionSetup;
import pl.edu.agh.cs.kraksim.weka.data.*;
import pl.edu.agh.cs.kraksim.weka.statistics.Statistics;
import pl.edu.agh.cs.kraksim.weka.utils.Neighbours;
import weka.core.Attribute;
import weka.core.FastVector;
import weka.core.Instance;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class TimeSeriesStatistics extends Statistics {
	private final TimeSeriesPredictor classificationPrediction;
	private final TimeSeriesTransactionCreator transactionCreator;
	private final TimeSeriesClassValue classValue;

	public TimeSeriesStatistics(PredictionSetup setup, TimeSeriesPredictor classificationPrediction, TimeSeriesTransactionCreator transactionCreator) {
		super(setup);
		this.classificationPrediction = classificationPrediction;
		this.transactionCreator = transactionCreator;
		classValue = new TimeSeriesClassValue(setup);
	}

	@Override
	public void predict(int turn) {
		Map<LinkInfo, ClassifiersInfo> classifiersMap = classificationPrediction.getClassifierMap();
		Map<IntersectionInfo, ClassifiersInfo> intersectionClassifiers = classificationPrediction.getIntersectionClassifiers();
		predictHistory(classifiersMap, intersectionClassifiers);

		int congestionTimePrediction = setup.getMinNumberOfInfluencedTimesteps();
		congestionTimePrediction += setup.getPredictionSize();
		int predictedTurn = turn + congestionTimePrediction * (int) setup.getWorldStateUpdatePeriod();

		Double[] predictionTable = new Double[classifiersMap.keySet().size()];
		for (LinkInfo linkInfo : classifiersMap.keySet()) {
			double prediction = getClassification(linkInfo, classifiersMap);
			predictionTable[linkInfo.linkNumber] = prediction;
			if (discretizer.classBelongsToCongestionClassSet(prediction)) {
				currentPredictionContainer.addPrediction(linkInfo, congestionTimePrediction);
			}
		}

		classDataPredictionArchive.storeStatistics(predictedTurn, predictionTable);
		removePredictedHistory();
	}

	private void removePredictedHistory() {
		int predictionSize = setup.getPredictionSize();
		while (predictionSize > 0) {
			historyArchive.remove();
			predictionSize--;
		}
	}

	private void predictHistory(Map<LinkInfo, ClassifiersInfo> classifiersMap, Map<IntersectionInfo, ClassifiersInfo> intersectionClassifiers) {
		int predictionSize = setup.getPredictionSize();
		while (predictionSize > 0) {
			AssociatedWorldState predictedWorldState = predictWorldState(classifiersMap, intersectionClassifiers);
			historyArchive.add(-100, predictedWorldState);
			predictionSize--;
		}
	}

	private double getClassification(LinkInfo linkInfo, Map<LinkInfo, ClassifiersInfo> linkClassifiers) {
		ClassifiersInfo classifiersInfo = linkClassifiers.get(linkInfo);
		ClassifierInfo classifierInfo = chooseClassifierInfo(classifiersInfo);
		FastVector attributes = classifierInfo.attributes;
		Instance testInstance = createTestInstance(linkInfo, attributes);
		testInstance.setDataset(classifierInfo.trainingHeaderSet);
		testInstance.setClassMissing();
		try {
			return classifierInfo.classifier.classifyInstance(testInstance);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return 0.0;
	}

	private AssociatedWorldState predictWorldState(Map<LinkInfo, ClassifiersInfo> classifiersMap, Map<IntersectionInfo, ClassifiersInfo> intersectionClassifiers) {

		AssociatedWorldState worldState = new AssociatedWorldState();
		worldState.roads = predictRoads(classifiersMap);
		worldState.intersections = predictsIntersections(intersectionClassifiers);
		return worldState;
	}

	private WorldStateIntersections predictsIntersections(Map<IntersectionInfo, ClassifiersInfo> intersectionClassifiers) {
		Map<IntersectionInfo, Neighbours> neighbours = setup.getIntersectionNeighbours();
		Set<IntersectionInfo> infoSet = neighbours.keySet();
		Map<String, Integer> actualPhaseMap = new HashMap<>();
		Map<String, Long> phaseWillLastMap = new HashMap<>();
		Map<String, Long> phaseLastMap = new HashMap<>();
		for (IntersectionInfo intersectionInfo : infoSet) {
			ClassifiersInfo classifiersInfo = intersectionClassifiers.get(intersectionInfo);
			ClassifierInfo classifierInfo;

			String intersectionId = intersectionInfo.intersectionId;
			if (setup.getPhase()) {
				classifierInfo = classifiersInfo.phase;
				Integer result = (int) predictValue(intersectionInfo, classifierInfo);
				actualPhaseMap.put(intersectionId, result);
			}
			if (setup.getPhaseWillLast()) {
				classifierInfo = classifiersInfo.phaseWillLast;
				Long result = (long) predictValue(intersectionInfo, classifierInfo);
				phaseWillLastMap.put(intersectionId, result);
			}
			if (setup.getPhaseLast()) {
				classifierInfo = classifiersInfo.phaseLast;
				Long result = (long) predictValue(intersectionInfo, classifierInfo);
				phaseLastMap.put(intersectionId, result);
			}
		}
		WorldStateIntersections intersections = new WorldStateIntersections();
		intersections.setActualPhaseMap(actualPhaseMap);
		intersections.setPhaseWillLastMap(phaseWillLastMap);
		intersections.setPhaseLastMap(phaseLastMap);
		return intersections;
	}

	private WorldStateRoads predictRoads(Map<LinkInfo, ClassifiersInfo> classifiersMap) {
		Map<LinkInfo, Neighbours> neighbours = setup.getNeighbourArray();
		Set<LinkInfo> links = neighbours.keySet();
		double[] carsDensityTable = new double[links.size()];
		double[] durationLevelTable = new double[links.size()];
		double[] carsOutLinkTable = new double[links.size()];
		double[] carsInLinkTable = new double[links.size()];
		double[] carsOnLinkTable = new double[links.size()];
		double[] evaluationTable = new double[links.size()];
		double[] greenDurationTable = new double[links.size()];
		for (LinkInfo linkInfo : links) {
			int linkNumber = linkInfo.linkNumber;
			ClassifiersInfo classifiersInfo = classifiersMap.get(linkInfo);
			ClassifierInfo classifierInfo;
			if (setup.getCarsDensity()) {
				classifierInfo = classifiersInfo.carsDensityInfo;
				carsDensityTable[linkNumber] = predictValue(linkInfo, classifierInfo);
			}
			if (setup.getDurationLevel()) {
				classifierInfo = classifiersInfo.durationLevelInfo;
				durationLevelTable[linkNumber] = predictValue(linkInfo, classifierInfo);
			}
			if (setup.getCarsOut()) {
				classifierInfo = classifiersInfo.carsOutInfo;
				carsOutLinkTable[linkNumber] = predictValue(linkInfo, classifierInfo);
			}
			if (setup.getCarsIn()) {
				classifierInfo = classifiersInfo.carsInInfo;
				carsInLinkTable[linkNumber] = predictValue(linkInfo, classifierInfo);
			}
			if (setup.getCarsOn()) {
				classifierInfo = classifiersInfo.carsOnInfo;
				carsOnLinkTable[linkNumber] = predictValue(linkInfo, classifierInfo);
			}
			if (setup.getEvaluation()) {
				classifierInfo = classifiersInfo.evaluationInfo;
				evaluationTable[linkNumber] = predictValue(linkInfo, classifierInfo);
			}
			if (setup.getGreenDuration()) {
				classifierInfo = classifiersInfo.greenDurationInfo;
				greenDurationTable[linkNumber] = predictValue(linkInfo, classifierInfo);
			}
		}
		WorldStateRoads roads = new WorldStateRoads();
		roads.setDurationLevelTable(durationLevelTable);
		roads.setCarsOutLinkTable(carsOutLinkTable);
		roads.setCarsInLinkTable(carsInLinkTable);
		roads.setCarsOnLinkTable(carsOnLinkTable);
		roads.setCarsDensityTable(carsDensityTable);
		roads.setEvaluationTable(evaluationTable);
		roads.setGreenDurationTable(greenDurationTable);
		return roads;
	}

	private double predictValue(Info linkInfo, ClassifierInfo classifierInfo) {
		FastVector attributes = classifierInfo.attributes;
		Instance testInstance = createTestInstance(linkInfo, attributes);
		testInstance.setDataset(classifierInfo.trainingHeaderSet);
		testInstance.setClassMissing();
		try {
			return classifierInfo.classifier.classifyInstance(testInstance);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return 0.0;
	}

	private ClassifierInfo chooseClassifierInfo(ClassifiersInfo classifiersInfo) {
		String dataType = setup.getRegressionDataType();
		switch (dataType) {
			case "carsDensity":
				return classifiersInfo.carsDensityInfo;
			case "carsOut":
				return classifiersInfo.carsOutInfo;
			case "carsOn":
				return classifiersInfo.carsOnInfo;
			case "carsIn":
				return classifiersInfo.carsInInfo;
			case "durationLevel":
				return classifiersInfo.durationLevelInfo;
			case "evaluation":
				return classifiersInfo.evaluationInfo;
			case "greenDuration":
				return classifiersInfo.greenDurationInfo;
		}
		return null;
	}

	private Instance createTestInstance(Info linkInfo, FastVector attributes) {
		Transaction transaction = transactionCreator.createTestTransaction(historyArchive, linkInfo);
		Instance testInstance = new Instance(attributes.size());
		for (int i = 0; i < attributes.size(); i++) {
			Attribute attribute = (Attribute) attributes.elementAt(i);
			Double value = transaction.getTransaction().get(i);
			testInstance.setValue(attribute, value);
		}
		return testInstance;
	}

	public void add(int turn, AssociatedWorldState worldState) {
		historyArchive.add(turn, worldState);

		Double[] classes = classValue.getClassValues(worldState.roads);
		classDataArchive.storeStatistics(turn, classes);

		Boolean[] congestions = discretizer.classesToCongestions(classes);
		congestionsArchive.storeStatistics(turn, congestions);
	}

	public boolean willAppearTrafficJam(Link link) {
		return currentPredictionContainer.willAppearTrafficJam(link);
	}
}
