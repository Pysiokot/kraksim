package pl.edu.agh.cs.kraksim.real_extended;

import org.apache.log4j.Logger;
import pl.edu.agh.cs.kraksim.AssumptionNotSatisfiedException;
import pl.edu.agh.cs.kraksim.core.Action;
import pl.edu.agh.cs.kraksim.core.Lane;
import pl.edu.agh.cs.kraksim.core.Link;
import pl.edu.agh.cs.kraksim.core.exceptions.ExtensionCreationException;
import pl.edu.agh.cs.kraksim.iface.block.LinkBlockIface;
import pl.edu.agh.cs.kraksim.iface.mon.CarDriveHandler;
import pl.edu.agh.cs.kraksim.iface.mon.LinkMonIface;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class LinkRealExt implements LinkBlockIface, LinkMonIface {
	private static final Logger LOGGER = Logger.getLogger(LinkRealExt.class);
	protected final Link link;
	protected final RealEView ev;

	LinkRealExt(Link link, RealEView ev, RealSimulationParams params) throws ExtensionCreationException {
		LOGGER.trace("Creating.");
		if (link.getLength() < params.priorLaneTimeHeadway * params.maxVelocity) {
			throw new ExtensionCreationException(String.format("real module requires link ls at least %d cells long", link.getLength()));
		}

		this.link = link;
		this.ev = ev;
	}

	void prepareTurnSimulation() {
		LOGGER.trace(link);

		for (int i = 0; i < laneCount(); i++) {
			// sets firstCarPos for lane
			laneExt(i).prepareTurnSimulation();
		}
	}

	private int laneCount() {
		return link.laneCount();
	}

	/* in absolute numbering, from left to right, starting from 0 */
	private LaneRealExt laneExt(int n) {
		return ev.ext(link.getLaneAbs(n));
	}

	/* intersection link only */
	public void findApproachingCars() {
		LOGGER.trace(link);

		for (int i = 0; i < laneCount(); i++) {
			// sets <bool> carApproaching for each lane
			laneExt(i).findApproachingCar();
		}
	}

	public void fireAllEntranceHandlers(Car car) {
		for(CarDriveHandler handler : this.link.getEntranceCarHandlers()) {
			handler.handleCarDrive(car.getVelocity(), car.getDriver());
		}
	}
	
	public void fireAllExitHandlers(Car car) {
		for(CarDriveHandler handler : this.link.getExitCarHandlers()) {
			handler.handleCarDrive(car.getVelocity(), car.getDriver());
		}
	}
	
	void simulateTurn() {
		LOGGER.trace(link);
		List laneList = Arrays.asList(this.link.getLanes());
		for (Object lane : laneList) {
			this.ev.ext((Lane) lane).prepareIterator();
		}
		GigaIterator gi = new GigaIterator(link, ev);
		while(gi.hasNext()){
			Car car = gi.next();
			if(car.canMoveThisTurn()) {
				car.simulateTurn();
			}
			car.updateTurnNumber();

		}

		//		for (int i = 0; i < laneCount(); i++) {
//			// for each car on link	switch lanes (if needed) and drive car, maybe leave lane
//			laneExt(i).simulateTurn();
//		}
	}

	void finalizeTurnSimulation() {
		LOGGER.trace(link);

		for (int i = 0; i < laneCount(); i++) {
			laneExt(i).finalizeTurnSimulation();
		}
	}

	public void block() {
		for (int i = 0; i < laneCount(); i++) {
			laneExt(i).block();
		}
	}

	public void unblock() {
		for (int i = 0; i < laneCount(); i++) {
			laneExt(i).unblock();
		}
	}

	public void installInductionLoops(int line, CarDriveHandler handler) throws IndexOutOfBoundsException {
		if (line < 0 || line > link.getLength()) {
			throw new IndexOutOfBoundsException("line = " + line);
		}

		for (int i = 0; i < laneCount(); i++) {
			LaneRealExt l = laneExt(i);
			if (line >= l.getOffset()) {
				laneExt(i).installInductionLoop(line, handler);
			}
		}
	}

	// 2019

	/**
	 * This method returns lane to enter on this link
	 *
	 * @param car entering car
	 * @return lane to enter or null if cannot enter any lane
	 */
	Lane getLaneToEnter(Car car) {
		// obtaining next goal of the entered car
		Link nextLink = null;
		if (car.hasNextTripPoint()) {
			nextLink = car.peekNextTripPoint();
		} else {
			// if there is no next point, it means, that the car
			// is heading to a gateway. If this link does not lead
			// to a gateway - time to throw some exception...

			if (!link.getEnd().isGateway()) {
				throw new AssumptionNotSatisfiedException();
			}
		}

		// obtaining list of actions heading to the given destination
		List<Action> actions = link.findActions(nextLink);

		MultiLaneRoutingHelper laneHelper = new MultiLaneRoutingHelper(ev);
		// choosing the best action from the given list
		Action nextAction = laneHelper.chooseBestAction(actions);
		// choosing the best lane to enter in order to get to lane given in action
		Lane nextLane = laneHelper.chooseBestLaneForAction(nextAction, link, car);

		// if null then no lane can be entered for given action
		if (nextLane == null) {
			return null;
		}
		//car.refreshTripRoute();

		if (!car.hasNextTripPoint()) {
			car.setActionForNextIntersection(null);
		} else {
			car.setActionForNextIntersection(nextAction);
		}

		return nextLane;
	}

	/**
	 * Method creates a list containing distances to obstacles on each lane <br>
	 * Uses car's position and visibility
	 *
	 * @param car car that wants to know what is ahead
	 * @return int of distances to obstacles on each lane
	 */
	public int[] getObstaclesAhead(Car car) {

		Lane[] allLanes = this.link.getLanes();
		Lane currentLane = car.getCurrentLane().getLane();
		int visibility = car.getObstacleVisibility();
		int[] nearestObstacleDistanceList = new int[allLanes.length];

		for (int i = 0; i < allLanes.length; i++) {
			Lane lane = allLanes[i];
			if (lane == currentLane || (lane.getOffset() > car.getPosition())) {
				continue;
			}
			ArrayList<Integer> blockedCells = (ArrayList<Integer>) lane.getActiveBlockedCellsIndexList();
			int furthestDistance = visibility + 1;
			if (!blockedCells.isEmpty()) {
				for (Integer obstacleIndex : blockedCells) {
					int dist = obstacleIndex - car.getPosition();
					if (dist < 0) {
						dist = visibility + 1;
					}
					furthestDistance = Math.min(furthestDistance, dist);
				}
			}
			nearestObstacleDistanceList[i] = furthestDistance;
		}
		return nearestObstacleDistanceList;
	}
}
