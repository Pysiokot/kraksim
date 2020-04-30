package pl.edu.agh.cs.kraksim.real_extended;

import com.google.common.collect.Lists;
import org.apache.log4j.Logger;
import pl.edu.agh.cs.kraksim.KraksimConfigurator;
import pl.edu.agh.cs.kraksim.core.Action;
import pl.edu.agh.cs.kraksim.core.Lane;
import pl.edu.agh.cs.kraksim.core.Link;
import pl.edu.agh.cs.kraksim.core.Node;
import pl.edu.agh.cs.kraksim.iface.sim.Route;
import pl.edu.agh.cs.kraksim.main.CarMoveModel;
import pl.edu.agh.cs.kraksim.main.Simulation;
import pl.edu.agh.cs.kraksim.main.drivers.Driver;
import pl.edu.agh.cs.kraksim.real_extended.LaneRealExt.InductionLoop;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.Map.Entry;

public class Car {
	private static final Logger LOGGER = Logger.getLogger(Car.class);
	private final boolean isTEST2013Enabled;
	private final Driver driver;
	private List<Node> TEST2013intersectionsList = new LinkedList<>();
	private Map<Node, List<Link>> TEST2013linkIntersectionsList = new HashMap<>();
	/*
	 * Iterator through route's link. linkIterator.next() is the next (not
	 * current!) link, the car will drive.
	 */
	private ListIterator<Link> linkIterator;
	private Action actionForNextIntersection;
	protected int pos;
	private int velocity;
	//  private ListIterator<Link> copyLinkIterator;
	private long TEST2013waitCounter = 0;
	private long TEST2013waitLimit;
	private boolean braking = false;
	private int enterPos = -1;
	private Lane beforeLane;
	private int beforePos;
	private boolean rerouting = false;
	private int obstacleVisibility;
	private int acceleration;

	// 2019
	private LaneRealExt currentLane = null;
	private int updateInTurn = -1;
	// 	random value for each turn of choosing correct lane switch method, 
	//	if < switchLaneActionProbability -> algorithm
	//	if > switchLaneActionProbability -> switch lane for intersection
	private double switchLaneMethodRandom;	// random value used to calculate SwitchLaneMethod
	protected int switchLaneUrgency = 0;	// number of turns car is in LaneSwitch.WANT_ ... -> reduces switch lane conditions

	// 2020
	private boolean isUsingDriverArchetype = true;

	protected LaneSwitch switchToLane = LaneSwitch.NO_CHANGE;
	
	private enum SwitchLaneMethod  { INTERSECTION_LANE, LOCAL_TRAFFIC_ALGORITHM }
	private SwitchLaneMethod switchLaneMethod;
	
	Car(Driver driver, Route route, boolean rerouting) {
		// == reading TEST2013 configuration
		//Properties prop = KraksimConfigurator.getPropertiesFromFile();
		String test2013enabled = KraksimConfigurator.getProperty("TEST2013Enabled");
		if (test2013enabled != null && test2013enabled.trim().equals("true")) {
			isTEST2013Enabled = true;

			String test2013intersectionVisitor = KraksimConfigurator.getProperty("TEST2013IntersectionVisitor");
			if (test2013intersectionVisitor != null && test2013intersectionVisitor.trim().equals("true")) {
				boolean isTEST2013IntersectionVisitorEnabled = true;
				TEST2013intersectionsList = new LinkedList<>();
			} else {
				boolean isTEST2013IntersectionVisitorEnabled = false;
			}


			String test2013intersectionLinkVisitor = KraksimConfigurator.getProperty("TEST2013IntersectionLinkVisitor");

			if (test2013intersectionLinkVisitor != null && test2013intersectionLinkVisitor.trim().equals("true")) {
				TEST2013linkIntersectionsList = new HashMap<>();
			}

			String test2013waitLimit = KraksimConfigurator.getProperty("TEST2013WaitLimit");
			TEST2013waitLimit = Integer.valueOf(test2013waitLimit);
		} else {
			isTEST2013Enabled = false;
		}
		// =end of= reading TEST2013 configuration

		this.driver = driver;
		this.rerouting = rerouting;
		linkIterator = route.linkIterator();
		// copyLinkIterator = route.linkIterator();
		// Important. See the notice above.
		linkIterator.next();
		// copyLinkIterator.next();

		beforeLane = null;
		beforePos = 0;

		obstacleVisibility = Integer.parseInt(KraksimConfigurator.getProperty("obstacleVisibility"));
		setAcceleration(Integer.parseInt(KraksimConfigurator.getProperty("carAcceleration")));

		LOGGER.trace("\n Driver= " + driver + "\n rerouting= " + rerouting);
	}
	
	public Car() {
		this.isTEST2013Enabled = false;
		this.driver = null;
	}

	
	public void refreshTripRoute() {
		// TODO: make it configurable from properties file (>10yo todo)
		// ListIterator<Link> copyLinkIter = linkIterator;
		if (!linkIterator.hasNext()) {
			return;
		}
		ListIterator<Link> newlinkIterator;
		if (rerouting) {
			newlinkIterator = driver.updateRouteFrom(linkIterator.next());
			linkIterator.previous();
			if (newlinkIterator != null) {
				linkIterator = newlinkIterator;
				LOGGER.trace("New Route ");
			} else {
				LOGGER.trace("OLD Route ");
			}
		}
	}
	
//////////////////////////////////////////////////////////////////////////
//	SIMULATION
	
/////////////////////////////////////////////////////////////////
//	Lane Changes Methods
	
	/** formula for calculating probability based on current position <br>
	 *   ~(distance_traveled / lane_length) with sharp limit at the end <br>
	 *   more if car is closer to the end
	 */
	protected double switchLaneActionProbability() {
		int d_linkLength = this.currentLane.linkLength();
		int d_intersection = d_linkLength - this.getPosition() -1;
		int maxSpeed = this.getSpeedLimit();
		int t_limitDistancetoIntersection = this.currentLane.INTERSECTION_LANE_SWITCH_TURN_LIMIT;	// const from config file
		int d_limitDistancetoIntersection = t_limitDistancetoIntersection * maxSpeed;	// limit distance to intersection
		int t_currentToIntersectionMaxSpeed = d_intersection / maxSpeed;
		if(t_limitDistancetoIntersection >= t_currentToIntersectionMaxSpeed)	return 1;	// we are too close to intersection
		double prob = (double)(this.getPosition() - d_limitDistancetoIntersection) / (double)(d_linkLength - d_limitDistancetoIntersection);
		return Math.pow(prob, this.currentLane.PROBABILITY_POWER_VALUE);
	}
	
	/**
	 * @return true if current lane allows to cross intersection
	 */
	protected boolean isThisLaneGoodForNextIntersection() {
		return isGivenLaneGoodForNextIntersection(this.currentLane.getLane());
	}
	
	/**
	 * @return true if given lane allows to cross intersection
	 */
	private boolean isGivenLaneGoodForNextIntersection(Lane givenLane) {
		Action actionIntersection = this.getActionForNextIntersection();
		int laneIntersectionRel = actionIntersection.getSource().getRelativeNumber();
		int currentLaneRel = givenLane.getRelativeNumber();
		int currentLaneAbs = givenLane.getAbsoluteNumber();
		if(laneIntersectionRel == 0) {	// main lane is good, can be on any lane in main group
			return currentLaneRel == 0;
		} else if(laneIntersectionRel < 0) {	// left turn
			if(actionIntersection.getSource().existsAtThisPosition(pos)) {
				return currentLaneRel < 0;				
			} else {
				return (currentLaneAbs - this.currentLane.getLane().getOwner().leftLaneCount()) == 0;
			}
		} else {	// right turn
			if(actionIntersection.getSource().existsAtThisPosition(pos)) {
				return currentLaneRel > 0;				
			} else {
				return (currentLaneAbs - this.currentLane.getLane().getOwner().leftLaneCount() - this.currentLane.getLane().getOwner().mainLaneCount() +1) == 0;
			}
		}
	}
	
	/**
	 * @return true if direction will change lane to good one <br>
	 * 	 true if direction will move car closer to good lane
	 */
	private boolean isDirectionBetterForNextIntersection(LaneSwitch direction) {
		LaneRealExt targetLane = this.getLaneFromDirection(direction);
		return isLaneBetterForNextIntersection(targetLane);
	}
	
	/**
	 * @return true if targetLane is good for intersection <br>
	 *  true if targetLane will move car closer to good lane
	 */
	private boolean isLaneBetterForNextIntersection(LaneRealExt targetLane) {
		if(targetLane == null)	return false;	// no lane can't be better
		Action actionIntersection = this.getActionForNextIntersection();
		int laneIntersectionAbs = actionIntersection.getSource().getAbsoluteNumber();

		int dstToIntersection = isUsingDriverArchetype ? this.getCurrentLane().linkLength() - this.getPosition() - 1 : 10;

		return (this.isGivenLaneGoodForNextIntersection(targetLane.getLane())
				|| Math.abs(targetLane.getLane().getAbsoluteNumber() - laneIntersectionAbs) < Math.abs(this.currentLane.getLane().getAbsoluteNumber() - laneIntersectionAbs)
				) && dstToIntersection < 100;
	}
	
	/**
	 * set lane switch state to move car closer to correct lane for next intersection
	 */
	private void setSwitchToLaneStateForIntersection() {
		if(isThisLaneGoodForNextIntersection()) {
			// if this is good lane - dont change
			this.switchToLane = LaneSwitch.NO_CHANGE;	// current lane is good
			return;
		}
		
		// if we have to change find direction that will improve current lane (not to good but to better than current)
		if(isDirectionBetterForNextIntersection(LaneSwitch.LEFT) || isCarInFrontSlower(this.getCurrentLane().getFrontCar(this))) {	// maybe left is better
			// left is better
			if(this.checkIfCanSwitchTo(LaneSwitch.LEFT)) {
				this.switchToLane = LaneSwitch.LEFT;
			} else {
				// we want to go left but its not possible, we can go to forge switch state (WANTS_LEFT) but we have to be lucky second time
				if(this.currentLane.getParams().getRandomGenerator().nextDouble() < this.switchLaneActionProbability()) {
					this.switchToLane = LaneSwitch.WANTS_LEFT;	
				} else {
					this.switchToLane = LaneSwitch.NO_CHANGE;	
				}
			}
			
		} else if(isDirectionBetterForNextIntersection(LaneSwitch.RIGHT) || isCarInFrontSlower(this.getCurrentLane().getFrontCar(this))) {	// maybe right is better
			// right is better
			if(this.checkIfCanSwitchTo(LaneSwitch.RIGHT)) {
				this.switchToLane = LaneSwitch.RIGHT;
			} else {
				if(this.currentLane.getParams().getRandomGenerator().nextDouble() < this.switchLaneActionProbability()) {
					this.switchToLane = LaneSwitch.WANTS_RIGHT;
				} else {
					this.switchToLane = LaneSwitch.NO_CHANGE;	
				}
			}
			
		} else {
			this.switchToLane = LaneSwitch.NO_CHANGE;

//			throw new RuntimeException("no good action for next intersection");
		}
		
		if(this.getLaneFromLaneSwitchState() != null && !this.getLaneFromLaneSwitchState().getLane().existsAtThisPosition(pos)) {
			// Target lane starts later, nothing to do, we are on best possible lane
			this.switchToLane = LaneSwitch.NO_CHANGE;
		}
	}
	
	/**
	 * set lane switch state looking at local situation on the road <br>
	 * reacts differently if in intersection lane switch action <br>
	 * {@link Car#switchLaneAlgorithm} for lane switch algorithm
	 */
	private void setSwitchToLaneStateForAlgorithm() {
		if(this.switchToLane == LaneSwitch.WANTS_LEFT || this.switchToLane == LaneSwitch.WANTS_RIGHT) {
			// car already has an action and will try to do it this turn
			return;
		}
		int switchLaneForceLeft = -1;
		int switchLaneForceRight = -1;

		if(this.currentLane.hasLeftNeighbor()
			&& !(this.currentLane.leftNeighbor().getLane().isMainLane() ^ this.currentLane.getLane().isMainLane())	//nXOR, they must be the same type
			&& (this.currentLane.leftNeighbor().getLane().isMainLane() || this.isGivenLaneGoodForNextIntersection(this.currentLane.leftNeighbor().getLane()))
			&& (this.switchLaneMethod == SwitchLaneMethod.LOCAL_TRAFFIC_ALGORITHM || this.isLaneBetterForNextIntersection(this.currentLane.leftNeighbor()))
			) {
				// switch to left if left lane exists, is a main lane and left lane is correct for next interaction if it needs to be (distance based probability)
				switchLaneForceLeft = this.switchLaneAlgorithm(this.currentLane.leftNeighbor());	
		}
		
		if(this.currentLane.hasRightNeighbor()
			&& !(this.currentLane.rightNeighbor().getLane().isMainLane() ^ this.currentLane.getLane().isMainLane())	//nXOR, they must be the same type
			&& (this.currentLane.rightNeighbor().getLane().isMainLane() || this.isGivenLaneGoodForNextIntersection(this.currentLane.rightNeighbor().getLane()))
			&& (this.switchLaneMethod == SwitchLaneMethod.LOCAL_TRAFFIC_ALGORITHM || this.isLaneBetterForNextIntersection(this.currentLane.rightNeighbor()))
			) {
				// switch to left if right lane exists, is a main lane and right lane is correct for next interaction if it needs to be (distance based probability)
				switchLaneForceRight = this.switchLaneAlgorithm(this.currentLane.rightNeighbor());	
			}
		
		//	Choose best lane to switch base on gap to next car
		if(switchLaneForceLeft > switchLaneForceRight) {
			this.switchToLane = LaneSwitch.LEFT;	// left is better
		} else if(switchLaneForceLeft < switchLaneForceRight) {
			this.switchToLane = LaneSwitch.RIGHT;	// right is better
		} else if(switchLaneForceLeft == switchLaneForceRight && switchLaneForceLeft > 0) {
			this.switchToLane = LaneSwitch.RIGHT;	// its the same and bots are good (>0) -> better to go right
		} else {
			this.switchToLane = LaneSwitch.NO_CHANGE;	// there are no good lanes to switch
		}
	}
	
	/** @return lane car want to switch to */
	protected LaneRealExt getLaneFromLaneSwitchState() {
		return getLaneFromDirection(this.switchToLane);
	}
	
	/** @return lane in given direction from current, null is such lane doesn't exists */
	private LaneRealExt getLaneFromDirection(LaneSwitch direction) {
		try {
			switch(direction) {
			case NO_CHANGE:
				return this.currentLane;
			case LEFT:
				return this.currentLane.leftNeighbor();
			case RIGHT:
				return this.currentLane.rightNeighbor();
			case WANTS_LEFT:
				return this.currentLane.leftNeighbor();
			case WANTS_RIGHT:
				return this.currentLane.rightNeighbor();
			default:
				throw new RuntimeException("wrong LaneSwitchState : " + this.switchToLane);
			}
		} catch (java.lang.ArrayIndexOutOfBoundsException e) {
			return null;
		}
	}
	
	
///////////////////////////////////////////////////////////
//		Switch Lane Algorithm
	/**
	 * check if lane neiLane is good to switch to and return its score
	 */
	protected int switchLaneAlgorithm(LaneRealExt neiLane) {
		if(neiLane == null) return -1;
		Car neiCarBehind = neiLane.getBehindCar(this.pos+1);
		Car neiCarFront = neiLane.getFrontCar(this.pos-1);
		Car thisCarFront = this.currentLane.getFrontCar(this.pos);
		// gap - number of free cells : [c] [] [] [c] -> gap == 2
		int gapNeiFront = neiCarFront != null ? neiCarFront.getPosition() - this.pos - 1 : neiLane.linkLength() - this.pos -1;
		if(isMyLaneBad(thisCarFront) && isOtherLaneBetter(thisCarFront, neiCarFront, neiLane) && canSwitchLaneToOther(neiCarBehind, neiCarFront, neiLane)) {
			return gapNeiFront;	// score for this lane switch
		}
		return -1;
	}
	
	/** is distance to next car less than my speed  */
	protected boolean isMyLaneBad(Car carInFront) {
		int gapThisFront = 	carInFront != null ? carInFront.getPosition() - this.pos - 1 : this.currentLane.linkLength() - this.pos -1;
		boolean carInFrontSlower = isCarInFrontSlower(carInFront);
		return gapThisFront <= this.velocity || !carInFrontSlower;
	}
	
	/** other lane better if it has more space to next car in front */
	protected boolean isOtherLaneBetter(Car carInFront, Car otherCarFront, LaneRealExt otherLane) {
		int gapThisFront = carInFront != null	? carInFront.getPosition() - this.pos - 1	: this.currentLane.linkLength() - this.pos - 1;
		boolean carInFrontSlower = isCarInFrontSlower(carInFront);
		int gapNeiFront = otherCarFront != null	? otherCarFront.getPosition() - this.pos - 1	: otherLane.linkLength() - this.pos - 1;
		boolean obstacleClose = false;
		for(Integer obstacleIndex : otherLane.getLane().getActiveBlockedCellsIndexList()) {
			int dist = obstacleIndex - getPosition();	// [C] --> [o]
			if(dist < 0) continue;
			else if(dist < Integer.parseInt(KraksimConfigurator.getProperty("obstacleVisibility"))) {
				obstacleClose = true;
				break;
			}
		}
		return ((gapNeiFront-1) > gapThisFront && !obstacleClose) || carInFrontSlower;
	}

	private boolean isCarInFrontSlower(Car carInFront) {
		boolean isCarInFrontSlower = false;
		if(isUsingDriverArchetype)
		{
			int gapThisFront = carInFront != null	? carInFront.getPosition() - this.pos - 1	: this.currentLane.linkLength() - this.pos - 1;
			if(gapThisFront <= getFutureVelocity() * 2)
			{
				 isCarInFrontSlower = carInFront != null && carInFront.getVelocity() + 1 < this.getVelocity() + this.getAcceleration();
			}
		}
		return isCarInFrontSlower;
	}

	/** is it safe to switch lanes, tests my speed, others speed, gaps between cars, niceness of lane switch (how much space do I need) */
	protected boolean canSwitchLaneToOther(Car otherCarBehind, Car otherCarFront, LaneRealExt otherLane) {
		int gapNeiFront =	otherCarFront != null	? otherCarFront.getPosition() - this.pos - 1 	: otherLane.linkLength() - this.pos - 1;
		int gapNeiBehind =	otherCarBehind != null	? this.pos - otherCarBehind.getPosition() - 1	: this.pos - 1;
		double crashFreeTurns = this.currentLane.CRASH_FREE_TIME;	// turns until crash, gap must be bigger than velocity * crashFreeTurns, == 1 -> after this turn it will look good
		
		double crashFreeMultiplier;
		if(Double.parseDouble(KraksimConfigurator.getProperty("turnsToIgnoreCrashRules")) == 0) {
			crashFreeMultiplier = 0;
		} else {
			crashFreeMultiplier = Math.max(1 - switchLaneUrgency / Double.parseDouble(KraksimConfigurator.getProperty("turnsToIgnoreCrashRules")), 0);
		}
		// crashFreeTurns * crashFreeMultiplier = how much do I care about safety of my lane switch, number of fg
		
		// calculate condition for front and behind gap
		boolean spaceInFront = 
				gapNeiFront >= Math.round((this.getVelocity() - this.getAcceleration()) * crashFreeTurns * crashFreeMultiplier);
		// (crashFreeTurns - 1) -> car behind already did his move this turn
		// WARNING: not always, when emergency performs swap he might be in front of a car which haven't move this turn yet, feel free to fix it
		boolean spaceBehind = 
				otherCarBehind == null 
				|| gapNeiBehind >= Math.round(
						(otherCarBehind.getFutureVelocity()-(this.getVelocity() - this.getAcceleration())) * (crashFreeTurns - 1) * crashFreeMultiplier
						);
		return spaceInFront && spaceBehind && otherLane.getLane().existsAtThisPosition(this.getPosition());
	}	
//		[end] Switch Lane Algorithm
///////////////////////////////////////////////////////////
	
	/**
	 * uses part of Switch Lane Algorithm to check if car can switch lanes (based on gap, velocity)
	 * @return true if car can switch lane in given direction
	 */
	protected boolean checkIfCanSwitchTo(LaneSwitch direction) {
		LaneRealExt otherLane;
		if(direction == LaneSwitch.LEFT) {
			if(this.currentLane.hasLeftNeighbor())
				otherLane = this.currentLane.leftNeighbor();
			else
				return false;
		} else if(direction == LaneSwitch.RIGHT) {
			if(this.currentLane.hasRightNeighbor())
				otherLane = this.currentLane.rightNeighbor();
			else
				return false;
		} else {
			return true;	// can always switch if there is not switch
		}
		Car carBehind = otherLane.getBehindCar(this.pos+1);
		Car carFront = otherLane.getFrontCar(this.pos-1);
		return canSwitchLaneToOther(carBehind, carFront, otherLane);
	}
	
	/**
	 * uses part of Switch Lane Algorithm to check if car can switch lanes (based on gap, velocity)
	 * @return true if car can switch lane in given direction
	 */
	protected boolean checkIfCanSwitchToIgnoreObstacles(LaneSwitch direction) {
		LaneRealExt otherLane;
		if(direction == LaneSwitch.LEFT) {
			if(this.currentLane.hasLeftNeighbor())
				otherLane = this.currentLane.leftNeighbor();
			else
				return false;
		} else if(direction == LaneSwitch.RIGHT) {
			if(this.currentLane.hasRightNeighbor())
				otherLane = this.currentLane.rightNeighbor();
			else
				return false;
		} else {
			return true;	// can always switch if there is not switch
		}
		Car carBehind = otherLane.getBehindCar(this.pos+1);
		Car carFront = otherLane.getFrontCar(this.pos-1);
		if(carBehind instanceof Obstacle && carFront instanceof Obstacle) {
			return carFront.getPosition() != this.getPosition();
		}
		if(carBehind instanceof Obstacle) carBehind = null;
		if(carFront instanceof Obstacle) carFront = null;
		return canSwitchLaneToOther(carBehind, carFront, otherLane);
	}
	
	/**
	 * uses part of Switch Lane Algorithm to check if car can switch lanes (based on gap, velocity)
	 * @return true if car can switch lane in given direction
	 */
	private boolean checkIfCanSwitchTo(LaneRealExt otherLane) {
		if(otherLane.equals(this.currentLane)) {
			return true;
		}
		Car carBehind = otherLane.getBehindCar(this.pos+1);
		Car carFront = otherLane.getFrontCar(this.pos-1);
		return canSwitchLaneToOther(carBehind, carFront, otherLane);
	}
	
	private int getLaneNumberToBypassObstacle(int distanceToObstacle){

		// get a list containing distance to nearest obstacle for each lane
		int[] nearestObstacleDistanceList = currentLane.getRealView().ext(currentLane.getLane().getOwner()).getObstaclesAhead(this);

		int currentLaneNumber = currentLane.getLane().getAbsoluteNumber();
		boolean chosen = false;
		int id = -1;
		while(!chosen) {

			int furthest = -1;
			// pick the lane where obstacle is the furthest and further than obstacle on current lane
			if(this.currentLane.getParams().getRandomGenerator().nextFloat() < 0.5) {	
				for(int i = nearestObstacleDistanceList.length-1; i>=0; i--){
					if(i == currentLaneNumber) continue;
					if(nearestObstacleDistanceList[i] > furthest && nearestObstacleDistanceList[i] > distanceToObstacle){
						id = i;
						furthest = nearestObstacleDistanceList[i];
					}
				}
			} else {
				for(int i = 0; i<nearestObstacleDistanceList.length; i++){
					if(i == currentLaneNumber) continue;
					if(nearestObstacleDistanceList[i] > furthest && nearestObstacleDistanceList[i] > distanceToObstacle){
						id = i;
						furthest = nearestObstacleDistanceList[i];
					}
				}
			}

			if(furthest == -1){
				id = currentLaneNumber;
				break;
			}

			int direction;
			if(id - currentLaneNumber < 0) direction = 1;
			else direction = -1;
			int tmp = id + direction;
			chosen = true;
			// checking all lanes between chosen and current
			while(tmp != currentLaneNumber){
				// if any lane between is worse then find another
				if(nearestObstacleDistanceList[tmp] < distanceToObstacle){
					nearestObstacleDistanceList[id] = -1;
					chosen = false;
					break;
				}
				tmp += direction;
			}
		}
		return id;
	}



	/**
	 * Check if there is need to switch lanes (obstacle, emergency etc) <br>
	 * If not check switchLaneAlgorithms on all neighbors lanes <br>
	 * Action for obstacle or emergency have priority
	 * @return correct switch lane state
	 */
	void switchLanesState(){
		//	if car is not emergency or car behind me is emergency - go right
		// 	if obstacle in front - try to switch
		//	if car dont want to switch - check setSwitchToLaneStateForAlgorithm - maybe it will switch

		// calculate distance to nearest obstacle, must be not more than obstacleVisibility param
		int distanceToNextObstacle = Integer.MAX_VALUE;
		for(Integer obstacleIndex : this.currentLane.getLane().getActiveBlockedCellsIndexList()) {
			int dist = obstacleIndex - getPosition();	// [C] --> [o]
			if(dist < 0) continue;
			distanceToNextObstacle = Math.min(distanceToNextObstacle, dist);
		}
		
		//	check for obstacles
		if(distanceToNextObstacle <= obstacleVisibility) {
			int desiredLaneNumber = getLaneNumberToBypassObstacle(distanceToNextObstacle);
			//System.out.println("obstacle " + desiredLaneNumber);
			if(desiredLaneNumber < currentLane.getLane().getAbsoluteNumber()){
				if(checkIfCanSwitchToIgnoreObstacles(LaneSwitch.LEFT)){
					this.switchToLane = LaneSwitch.LEFT;
				}
				else{
					if(distanceToNextObstacle < this.currentLane.getSpeedLimit()) {
						this.switchToLane = LaneSwitch.WANTS_LEFT;						
					} else {
						this.switchToLane = LaneSwitch.NO_CHANGE;
					}
				}
			}
			else if(desiredLaneNumber > currentLane.getLane().getAbsoluteNumber()){
				if(checkIfCanSwitchToIgnoreObstacles(LaneSwitch.RIGHT)){
					this.switchToLane = LaneSwitch.RIGHT;
				}
				else{
					if(distanceToNextObstacle < this.currentLane.getSpeedLimit()) {
						this.switchToLane = LaneSwitch.WANTS_RIGHT;						
					} else {
						this.switchToLane = LaneSwitch.NO_CHANGE;
					}
				}
			}
			else{
				this.switchToLane = LaneSwitch.NO_CHANGE;
			}
		}
		else if (!isEmergency() && this.currentLane.getBehindCar(this) != null && this.currentLane.getBehindCar(this).isEmergency()) {
			if(this.getCurrentLane().hasRightNeighbor() && this.getCurrentLane().rightNeighbor().getLane().isMainLane()) {	
				if(checkIfCanSwitchTo(LaneSwitch.RIGHT)) {
					this.switchToLane = LaneSwitch.RIGHT;
				} else {
					this.switchToLane = LaneSwitch.WANTS_RIGHT;
				} 
			}
		} 
		// if car wanted to switch lanes in previous turn, check if its possible now
		else if(this.switchToLane == LaneSwitch.WANTS_LEFT) {
			if(checkIfCanSwitchTo(LaneSwitch.LEFT)) {
				this.switchToLane = LaneSwitch.LEFT;
			}
			
		} 
		else if(this.switchToLane == LaneSwitch.WANTS_RIGHT) {
			if(checkIfCanSwitchTo(LaneSwitch.RIGHT)) {
				this.switchToLane = LaneSwitch.RIGHT;
			}
		} 
		else {	
			
			// this part has to set new switchToLane state, it has 2 available algorithms, for intersection and based on local traffic
			this.switchLaneMethodRandom = this.currentLane.getParams().getRandomGenerator().nextDouble();
			if(this.getActionForNextIntersection() != null) {
				// number of lanes to good lane for next intersection
				int intersectionSwitchMultiplier =  Math.max(0, Math.abs(this.currentLane.getLane().getAbsoluteNumber() - this.getActionForNextIntersection().getSource().getAbsoluteNumber()));
				if(this.switchLaneMethodRandom * intersectionSwitchMultiplier < this.switchLaneActionProbability()) {
					this.switchLaneMethod = SwitchLaneMethod.INTERSECTION_LANE;
				} else {
					this.switchLaneMethod = SwitchLaneMethod.LOCAL_TRAFFIC_ALGORITHM;
				}
			} else {
				this.switchLaneMethod = SwitchLaneMethod.LOCAL_TRAFFIC_ALGORITHM;
			}
			
			// we know what algorithm to use now
			if(this.switchLaneMethod == SwitchLaneMethod.INTERSECTION_LANE) {
				if(this.isThisLaneGoodForNextIntersection()) {
					// if we are currently on good lane car can switch lanes to locally better if target lane is also good got next intersection
					this.setSwitchToLaneStateForAlgorithm(); // behaves differently based on switchLaneMethod
				} else {
					// moves car closer to good lane
					this.setSwitchToLaneStateForIntersection();
				}
			} else {
				// dont care about next intersection, just pick locally better lane
				this.setSwitchToLaneStateForAlgorithm();	// behaves differently based on switchLaneMethod
			}
		}
		
		// if u want to switch to lane which does not exists, u dont want to switch lanes
		if(this.getLaneFromLaneSwitchState() == null) {
			this.switchToLane = LaneSwitch.NO_CHANGE;
		}
		
	}
	
//	[end] Lane Changes Methods
/////////////////////////////////////////////////////////////////
	
	/**
	 * Nagel-Schreckenberg <br>
	 * Perform all necessary actions for car
	 */
	void simulateTurn() {
		LOGGER.trace("car simulation : " + this);
		if(this.isObstacle()) {	// dont simulate obstacles
			return;
		}
		if(!this.canMoveThisTurn()) {	// car already did this turn
			return;
		}

		Car nextCar = this.currentLane.getFrontCar(this);
		
		// remember starting point
		this.setBeforeLane(this.currentLane.getLane());
		this.setBeforePos(this.getPosition());
		
		// Acceleration
		int speedLimit = this.getSpeedLimit();

		if(isUsingDriverArchetype)
		{
			speedLimit *= 1+(driver.getArchetype().aggression / 2f - driver.getArchetype().fear / 3f);
		}

		this.velocity = Math.min(speedLimit, this.velocity+this.getAcceleration());
		
		// random decelerations, car model actions (Nagel-Schreckenberg multi-lane switch etc), sets switchToLane state 
		handleCorrectModel(nextCar);
		
		driveCar(nextCar);
		
		fireAllInductionLoopPointers();
		
		this.updateTurnNumber();	// used to determine canMoveThisTurn()
	}
	
	/** fire all InductionLoops */
	private void fireAllInductionLoopPointers() {
		if(this.currentLane.getLane().equals(this.beforeLane)) {	// I'm sill on the same lane
			if(this.pos != this.beforePos)  {// fire loop only if position changed, gates spawn cars at pos=0 so they were counted as 2
				fireInductionLoopForLane(this.currentLane, this.pos, this.beforePos);
			}
		} else {	// lane switched
			LaneRealExt beforeLaneReal = this.currentLane.getRealView().ext(beforeLane);
			fireInductionLoopForLane(beforeLaneReal, this.beforeLane.getLength()+this.beforeLane.getOffset()+1, this.beforePos);	// force to fire leaving lane induction
			fireInductionLoopForLane(this.currentLane, this.pos, -1);	// force to fire entering induction loop
		}
	}
	
	/**
	 * fire InductionLoop if I crossed its border
	 * @param lane 
	 * @param position current position
	 * @param positionBefore position in previous turn
	 */
	public void fireInductionLoopForLane(LaneRealExt lane, int position, int positionBefore) {
		for(Entry<Integer, InductionLoop> entry : lane.getPositionInductionLoops().entrySet()) {
			if(positionBefore <= entry.getKey() && entry.getKey() < position) {
				entry.getValue().handler.handleCarDrive(getVelocity(), getDriver());
			}
		}
	}
	
	/**	Perform action based on current car move model	
	 * changes speed and sets lane switch (if NS model)
	 */
	private void handleCorrectModel(Car nextCar) {
		boolean velocityZero = this.getVelocity() <= 0; // VDR - check for v = 0 (slow start)
		float decisionChance = this.currentLane.getParams().getRandomGenerator().nextFloat();
		float rand = this.currentLane.getParams().getRandomGenerator().nextFloat();
		CarMoveModel carMoveModel = this.currentLane.getCarMoveModel();
		switch (carMoveModel.getName()) {
		case CarMoveModel.MODEL_NAGEL:
			if (decisionChance < carMoveModel.getFloatParameter(CarMoveModel.MODEL_NAGEL_MOVE_PROB)) {
				maybeDeccelerate(rand);
			}
			break;
		case CarMoveModel.MODEL_MULTINAGEL: // is used by default, it works with obstacles and turn lanes on intersections
			if (decisionChance < carMoveModel.getFloatParameter(CarMoveModel.MODEL_MULTINAGEL_MOVE_PROB) && velocity > 1) {
				maybeDeccelerate(rand);
			}
			this.switchLanesState();
			break;
		// deceleration if vdr
		case CarMoveModel.MODEL_VDR:
			// if v = 0 => different (greater) chance of deceleration
			if (velocityZero) {
				if (decisionChance < carMoveModel.getFloatParameter(CarMoveModel.MODEL_VDR_0_PROB)) {
					maybeDeccelerate(rand);
				}
			} else {
				if (decisionChance < carMoveModel.getFloatParameter(CarMoveModel.MODEL_VDR_MOVE_PROB)) {
					maybeDeccelerate(rand);
				}
			}
			break;
		// Brake light model
		case CarMoveModel.MODEL_BRAKELIGHT:
			if (velocityZero) {
				if (decisionChance < carMoveModel.getFloatParameter(CarMoveModel.MODEL_BRAKELIGHT_0_PROB)) {
					maybeDeccelerate(rand);
				}
			} else {
				if (nextCar != null && nextCar.isBraking()) {
					int threshold = carMoveModel.getIntParameter(CarMoveModel.MODEL_BRAKELIGHT_DISTANCE_THRESHOLD);
					double ts = (threshold < velocity) ? threshold : velocity;
					double th = (nextCar.getPosition() - this.getPosition()) / (double) velocity;
					if (th < ts) {
						if (decisionChance < carMoveModel.getFloatParameter(CarMoveModel.MODEL_BRAKELIGHT_BRAKE_PROB)) {
							maybeDeccelerate(rand);
							this.setBraking(true, this.isEmergency());
						} else {
							this.setBraking(false, this.isEmergency());
						}
					}
				} else {
					if (decisionChance < carMoveModel.getFloatParameter(CarMoveModel.MODEL_BRAKELIGHT_MOVE_PROB)) {
						maybeDeccelerate(rand);
						this.setBraking(true, this.isEmergency());
					} else {
						this.setBraking(false, this.isEmergency());
					}
				}
			}
			break;
		default:
			throw new RuntimeException("Unknown model! " + carMoveModel.getName());
		}

	}

	private void maybeDeccelerate(float chance) {
		if(!isUsingDriverArchetype)
		{
			velocity--;
			return;
		}

		if(driver.getArchetype().getFear() > 0.5f)
		{
			velocity--;
		}
		else if(chance >= driver.getArchetype().getAggression())
		{
			velocity--;
		}
	}

	/**
	 * perform lane switch if set <br>
	 * move car base on its speed and car in front
	 * @param nextCar car in front of this
	 */
	void driveCar(Car nextCar) {
		if(this.switchToLane == LaneSwitch.LEFT || this.switchToLane == LaneSwitch.RIGHT) {
			this.changeLanes(this.getLaneFromLaneSwitchState());
			nextCar = this.currentLane.getFrontCar(this);	// nextCar changed
			this.velocity = Math.max(this.velocity-1, 0);	// Reduce by 1
			this.switchLaneUrgency = 0;
			
		} else if(this.switchToLane == LaneSwitch.WANTS_LEFT || this.switchToLane == LaneSwitch.WANTS_RIGHT) {
			// cancel acceleration
			this.setVelocity(Math.max(this.getVelocity()-this.getAcceleration(), 1));	// by default reduce speed to 1 if looking for a lane switch	
			this.switchLaneUrgency++;
		}
		
		// force stop and force lane switch if on wrong lane for intersection and close to the end of the road
		if(this.getActionForNextIntersection() != null && !this.isThisLaneGoodForNextIntersection()) {	
			int lanesDifForCorrectForIntersection 
				= Math.abs(this.currentLane.getLane().getAbsoluteNumber() - this.getActionForNextIntersection().getSource().getAbsoluteNumber());
			int distaneToIntersection = this.currentLane.linkLength() - this.pos;
			if(!this.isThisLaneGoodForNextIntersection()
					&& distaneToIntersection < lanesDifForCorrectForIntersection * Integer.parseInt(KraksimConfigurator.getProperty("forceStopOnWrongLaneForIntersection"))) {
				this.setVelocity(Math.max(this.getVelocity()-this.getAcceleration(), 0));
			}
		}
		
		int freeCellsInFront;
		if (nextCar != null) {
			freeCellsInFront = nextCar.getPosition() - this.pos - 1;
		} else {
			freeCellsInFront = this.currentLane.linkLength() - this.pos -1;
		}
		freeCellsInFront = Math.max(freeCellsInFront,  0);
		
		//	move car forward |velocity| squares if lane ended do intersection/gateway function
		int distanceTraveled = 0;
		int distanceTraveledOnPreviousLane = 0;	// used in intersection crossing
		if(freeCellsInFront >= this.velocity) {	// simple move forward
			
			distanceTraveled = this.velocity;
			
		} else if (nextCar != null) {	// there is car in front, will crash, go only as far as u can
			
			distanceTraveled = freeCellsInFront;
			
		} else if(this.getActionForNextIntersection() != null){	// road ended, intersection
			
			distanceTraveled = freeCellsInFront;
			boolean crossed = this.crossIntersection();
			if(crossed) {
				nextCar = this.currentLane.getFrontCar(this);	// nextCar changed
				if (nextCar != null) {	// distance to new car also
					freeCellsInFront = nextCar.getPosition() - this.pos - 1;
				} else {
					freeCellsInFront = this.currentLane.linkLength() - this.pos -1;
				}
				distanceTraveledOnPreviousLane = distanceTraveled;
				distanceTraveled += 
						Math.max(
								Math.min(
										Math.min(
											freeCellsInFront, this.getVelocity() - distanceTraveledOnPreviousLane - 1)
											, this.getSpeedLimit()
										)
								,0);
			}
			
		} else {	// road ended, gateway
			
			try {
				((GatewayRealExt) this.currentLane.getRealView().ext(this.currentLane.linkEnd())).acceptCar(this);				
			} catch (Exception e) {
				e.printStackTrace();
				throw e;
			}
			this.currentLane.removeCarFromLaneWithIterator(this);
			distanceTraveled = freeCellsInFront + 2;
			
		}
		
		this.setPosition(this.pos + distanceTraveled - distanceTraveledOnPreviousLane);
		this.setVelocity(distanceTraveled);
	}

	/**
	 * removes car from current lane and adds it to otherLane
	 * changes this.currentLane
	 * @param otherLane new lane for this car
	 */
	public void changeLanes(LaneRealExt otherLane) {
		this.currentLane.removeCarFromLaneWithIterator(this);
		otherLane.addCarToLaneWithIterator(this);
		Car cx = this.currentLane.getCars().peek();
		// update lane firstCarPos for old lane
		if (cx != null) {
			this.currentLane.setFirstCarPos(cx.getPosition());
		} else {
			this.currentLane.setFirstCarPos(Integer.MAX_VALUE);
		}
		this.currentLane = otherLane;
	}

	/**
	 * removes car from current lane and moves it across the intersection <br>
	 * changes this.currentLane, sets position to 0 
	 * @return true if car moved across intersection
	 */
	public boolean crossIntersection() {
		if(this.currentLane.isBlocked()) {
			return false;
		}
		LinkRealExt targetLink = this.currentLane.getRealView().ext(this.actionForNextIntersection.getTarget());
		Lane targetLaneNormal = targetLink.getLaneToEnter(this);	// sets this.actionForNextIntersection
		if(targetLaneNormal == null) {
			return false;	// no good lanes after intersection
		}
		// we are good to cross intersection 
		LaneRealExt targetLane = this.currentLane.getRealView().ext(targetLaneNormal);		
		this.currentLane.removeCarFromLaneWithIterator(this);
		this.setPosition(0);
		targetLane.addCarToLaneWithIterator(this);
		if(this.hasNextTripPoint()) {
			this.nextTripPoint();
		}
		targetLink.fireAllEntranceHandlers(this);
		this.currentLane.getRealView().ext(this.currentLane.getLane().getOwner()).fireAllExitHandlers(this);
		this.currentLane = targetLane;
		return true;
	}

	/**
	 * Has to be called after move is simulated
	 */
	public void updateTurnNumber() {
		this.updateInTurn = Simulation.turnNumber;
	}

	/**
	 * @return true if car can move this turn <br>
	 * 	false if car was already moved this turn
	 */
	public boolean canMoveThisTurn() {
		return this.updateInTurn < Simulation.turnNumber;
	}
	
	public int getSpeedLimit() {
		return this.currentLane.getSpeedLimit();
	}
	
	
	//////////////////////////////////////////////////////////////////
	//	TEST2013 methods
	
	protected void TEST2013onNewAction(Action action) {
		if (action == null || action.getSource() == null) {
			return;
		}

		Link sourceLink = action.getSource().getOwner();
		Node nextIntersection = action.getTarget().getBeginning();

		// check if this intersection has been visited before
		// from this particular link
		if (TEST2013linkIntersectionsList.containsKey(sourceLink)) {
			List<Link> links = TEST2013linkIntersectionsList.get(nextIntersection);
			if (links.contains(sourceLink)) {
				LOGGER.fatal(String.format("Vehicle has already visited intersection (id:%s) from link (%s). This should _NEVER_ happen", nextIntersection.getId(), sourceLink.getId()));
			} else {
				links.add(sourceLink);
			}
		} else {
			TEST2013linkIntersectionsList.put(nextIntersection, Lists.newArrayList(sourceLink));
		}

		// check if this intersection has been visited before
		if (TEST2013intersectionsList.contains(nextIntersection)) {
			LOGGER.warn(String.format("Vehicle has already been at intersection (id:%s)", nextIntersection.getId()));
		} else {
			TEST2013intersectionsList.add(nextIntersection);
		}
	}
	
	public void TEST2013updateCarPosition(int position) {
		if (isTEST2013Enabled) {
			if (pos == position) {
				TEST2013waitCounter++;
				if (TEST2013waitCounter > TEST2013waitLimit) {
					LOGGER.info(String.format("%s hasn't move for %d turns.", toString(), TEST2013waitCounter));
				}
			} else {
				TEST2013waitCounter = 0;
			}
		}
	}
///////////////////////////////////////////////////////////////////////////////////////
// GET & SET area

	public int getAcceleration() {
		return acceleration;
	}

	public void setAcceleration(int acceleration) {
		this.acceleration = acceleration;
	}
	
	public Driver getDriver() {
		return driver;
	}
	
	public int getPosition() {
		return pos;
	}
	
	public void setPosition(int pos) {
		this.pos = pos;
	}
	
	public int getVelocity() {
		return velocity;
	}
	
	/** @return min( velocity + 1 , Speed Limit )	 */
	public int getFutureVelocity() {
		return Math.min(velocity + this.getAcceleration(), this.getSpeedLimit());
	}
	
	public void setVelocity(int velocity) {
		this.velocity = velocity;
	}
	
	public int getEnterPos() {
		return enterPos;
	}
	
	public void setEnterPos(int enterPos) {
		this.enterPos = enterPos;
	}
	
	public boolean isEmergency() {
		return getDriver().isEmergency();
	}
	
	public boolean isObstacle() {
		return false;
	}
	
	public boolean isBraking() {
		return braking;
	}
	
	public void setBraking(boolean braking, boolean emergency) {
		if (emergency) {
			if (braking) {
				driver.setCarColor(Color.BLACK);
			} else {
				driver.setCarColor(Color.BLUE);
			}
		} else {
			if (braking) {
				driver.setCarColor(Color.RED);
			} else {
				driver.setCarColor(Color.YELLOW);
			}
		}
		this.braking = braking;
	}
	
	public String toString() {
		if(currentLane == null) {
			return driver + " in [ CAR bPos=" + beforePos + ",cPos=" + pos + ",v=" + velocity + " lane: " + "null"+ " switch: " + this.switchToLane.toString() +   ']';
			
		}
		return driver + " in [ CAR bPos=" + beforePos + ",cPos=" + pos + ",v=" + velocity + " lane: " + this.currentLane.getLane().getAbsoluteNumber()+ " switch: " + this.switchToLane.toString() + ']';
	}
	
	int getBeforePos() {
		return beforePos;
	}
	
	void setBeforePos(int beforePos) {
		this.beforePos = beforePos;
	}
	
	/** @return trip_list.hasNext()	 */
	public boolean hasNextTripPoint() {
		return linkIterator.hasNext();
	}
	
	/** @return trip_list.next() */
	public Link nextTripPoint() {
		return linkIterator.next();
	}
	
	public Link peekNextTripPoint() {
		// copyLinkIterator.next();
		if (linkIterator.hasNext()) {
			Link result = linkIterator.next();
			linkIterator.previous();
			return result;
		} else {
			return null;
		}
	}
	
	public Action getActionForNextIntersection(){
		return this.actionForNextIntersection;
	}
	
	public void setActionForNextIntersection(Action actionForNextIntersection){
		if (isTEST2013Enabled) {
			TEST2013onNewAction(actionForNextIntersection);
		}
		this.actionForNextIntersection = actionForNextIntersection;
	}

	public Lane getBeforeLane() {
		return beforeLane;
	}

	public void setBeforeLane(Lane beforeLane) {
		this.beforeLane = beforeLane;
	}

    public LaneSwitch getLaneSwitch() {
        return switchToLane;
    }

	public void setLaneSwitch(LaneSwitch lane){
		this.switchToLane = lane;
	}

	public LaneRealExt getCurrentLane() {
		return currentLane;
	}

	public void setCurrentLane(LaneRealExt currentLane) {
		this.currentLane = currentLane;
	}

	int getObstacleVisibility(){
		return obstacleVisibility;
	}

}
