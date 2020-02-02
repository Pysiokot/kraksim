package pl.edu.agh.cs.kraksim.real_extended;

import com.google.common.collect.Sets;
import org.apache.log4j.Logger;
import pl.edu.agh.cs.kraksim.KraksimConfigurator;
import pl.edu.agh.cs.kraksim.core.Action;
import pl.edu.agh.cs.kraksim.core.Lane;
import pl.edu.agh.cs.kraksim.core.Link;
import pl.edu.agh.cs.kraksim.core.Node;
import pl.edu.agh.cs.kraksim.iface.block.LaneBlockIface;
import pl.edu.agh.cs.kraksim.iface.carinfo.CarInfoCursor;
import pl.edu.agh.cs.kraksim.iface.carinfo.LaneCarInfoIface;
import pl.edu.agh.cs.kraksim.iface.mon.CarDriveHandler;
import pl.edu.agh.cs.kraksim.iface.mon.LaneMonIface;
import pl.edu.agh.cs.kraksim.main.CarMoveModel;
import pl.edu.agh.cs.kraksim.main.drivers.Driver;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

public class LaneRealExt implements LaneBlockIface, LaneCarInfoIface, LaneMonIface {
	private static final Logger LOGGER = Logger.getLogger(LaneRealExt.class);
	private final String emergencyVehiclesConfiguration;

	private final Lane lane;
	private final RealEView realView;
	private final RealSimulationParams params;
	private final int offset;
	// MZA: multi-lanes. It had to be changed.
	private final List<Car> obstaclesToAdd = new LinkedList<>();
	private final Map<Integer, InductionLoop> positionInductionLoops;
	private final int speedLimit;
	private final int emergencySpeedLimit;
	private final int emergencySpeedLimitTimesHigher;
	private final int emergencyAcceleration;
	private final double laneChangeDesire;
	private final double rightLaneChangeDesire;
	private final CarMoveModel carMoveModel;
	private boolean blocked;
	private int firstCarPos;
	private boolean carApproaching;
	private boolean wait;

	final int SWITCH_TIME;
	final int MIN_SAFE_DISTANCE;
	final double CRASH_FREE_TIME;	// how much turns ahead car will test speed and positions to determine if switching is safe
	final double PROBABILITY_POWER_VALUE;	// power function for switch lane action probability
	final int INTERSECTION_LANE_SWITCH_TURN_LIMIT; // cars will be forced to switch lanes to correct for next intersection at this * maxSpeed distance

	private LinkedList<Car> cars;
	private ListIterator<Car> carIterator;

	LaneRealExt(Lane lane, RealEView ev, RealSimulationParams params) {
		LOGGER.trace("Constructing LaneRealExt ");
		emergencyVehiclesConfiguration = KraksimConfigurator.getProperty("emergencyVehiclesConfiguration");
		Properties properties = new Properties();
		try {
			BufferedInputStream bis = new BufferedInputStream(new FileInputStream(emergencyVehiclesConfiguration));
			properties.load(bis);
			bis.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		this.emergencySpeedLimitTimesHigher = Integer.valueOf(properties.getProperty("emergencySpeedLimitTimesHigher"));
		this.emergencyAcceleration = Integer.valueOf(properties.getProperty("emergencyAcceleration"));
		this.laneChangeDesire = Double.valueOf(properties.getProperty("laneChangeDesire"));
		this.rightLaneChangeDesire = Double.valueOf(properties.getProperty("rightLaneChangeDesire"));
		this.lane = lane;
		realView = ev;
		this.params = params;
		speedLimit = lane.getSpeedLimit();
		emergencySpeedLimit = getSpeedLimit() * emergencySpeedLimitTimesHigher;
		carMoveModel = params.carMoveModel;

		offset = lane.getOffset();// linkLength() - lane.getLength();
		cars = new LinkedList<>();
		blocked = false;
		positionInductionLoops = new HashMap<>(0);

		SWITCH_TIME = params.getSwitchTime();
		MIN_SAFE_DISTANCE = params.getMinSafeDistance();
		this.CRASH_FREE_TIME = Double.parseDouble(KraksimConfigurator.getProperty("crashFreeTime"));
		this.PROBABILITY_POWER_VALUE = Double.parseDouble(KraksimConfigurator.getProperty("probabilityPowerValue"));
		this.INTERSECTION_LANE_SWITCH_TURN_LIMIT = Integer.parseInt(KraksimConfigurator.getProperty("intersectionLaneSwitchTurnThreshold"));
		
		
		// block cells
		//this.addNewObstaclesFromCorelane();
		//this.finalizeTurnSimulation();
	}

	public CarMoveModel getCarMoveModel() {
		return carMoveModel;
	}

	int getOffset() {
		return offset;
	}

	private int absoluteNumber() {
		return lane.getAbsoluteNumber();
	}

	Node linkEnd() {
		return owner().getEnd();
	}

	/*
	 * Return <0 if lane represented by this object lies on the left of l, 0 if
	 * they are the same lane, >0 if lane represented by this object lies on the
	 * right of l.
	 */
	private int compareLanePositionTo(LaneRealExt l) {
		return absoluteNumber() - l.absoluteNumber();
	}

	LaneRealExt leftNeighbor() {
		return realView.ext(owner().getLaneAbs(absoluteNumber() - 1));
	}

	LaneRealExt rightNeighbor() {
		return realView.ext(owner().getLaneAbs(absoluteNumber() + 1));
	}
	
	boolean hasLeftNeighbor() {
		return absoluteNumber() - 1 >= 0;
	}
	
	boolean hasRightNeighbor() {
		return absoluteNumber() + 1 < realView.ext(owner()).link.getLanes().length;
	}

	public void prepareTurnSimulation() {
		LOGGER.trace(lane);
		Car car = cars.peek();
		if (car != null) {
			setFirstCarPos(car.getPosition());
		} else {
			setFirstCarPos(Integer.MAX_VALUE);
		}
	}
	
	public void prepareIterator() {
		this.carIterator = this.cars.listIterator();		
	}

	/* intersection lane only */
	public void findApproachingCar() {
		LOGGER.trace(lane);
		if (blocked || cars.isEmpty() || wait) {
			carApproaching = false;
			return;
		}

		Car car = cars.getLast();
		carApproaching = (car.getPosition() + Math.max(car.getVelocity(), 1) * params.priorLaneTimeHeadway >= linkLength());
	}

	int linkLength() {
		return owner().getLength();
	}

	private Link owner() {
		return lane.getOwner();
	}

	boolean hasCarPlace() {
		return firstCarPos > offset;
	}

	LaneRealExt getSourceLane(Action action) {
		LaneRealExt sourceLane;
		if (action != null) {
			sourceLane = realView.ext(action.getSource());
			int x = compareLanePositionTo(sourceLane);
			if (x < 0) {
				sourceLane = rightNeighbor();
			} else if (x > 0) {
				sourceLane = leftNeighbor();
			}
		} else {
			sourceLane = this;
		}
		return sourceLane;
	}

	void deadLockRecovery() {
		// ev.ext( lane.getOwner()).
		if (params.getRandomGenerator().nextFloat() < params.victimProb) {
			LOGGER.trace("Deadlock victim: " + lane + " - recovering.");
			setWait(true);
		}
		LOGGER.trace("Deadlock: " + lane + " won't be a victim.");
	}

	boolean checkDeadlock(Lane begin, Lane next) {
		LOGGER.trace("Check for deadlock: " + begin);
		return checkDeadlock(next, Sets.newHashSet(begin));
	}

	private boolean checkDeadlock(Lane next, Set<Lane> visited) {
		LOGGER.trace("Check for deadlock: " + next);
		if (visited.contains(next)) {
			LOGGER.trace(visited);
			return true;
		}
		visited.add(next);

		for (Iterator<Action> it = next.actionIterator(); it.hasNext(); ) {
			Action ac = it.next();
			for (Lane aPl : ac.getPriorLanes()) {
				if (realView.ext(aPl).carApproaching && checkDeadlock(aPl, visited)) {
					return true;
				}
			}
		}

		return false;
	}

	void finalizeTurnSimulation() {
		LOGGER.trace(lane);
		this.removeExpiredObstacleFromCorelane();
		this.addNewObstaclesFromCorelane();
		if (!obstaclesToAdd.isEmpty()) {
			ListIterator<Car> iterator = obstaclesToAdd.listIterator();
			while(iterator.hasNext()) {
				Car obstacleCar = iterator.next();
				Iterator<Car> it = cars.iterator();
				LinkedList<Car> newCarList = new LinkedList<>();
				boolean ins = false;
				while (it.hasNext()) {
					Car iterCar = it.next();
					if(obstacleCar.isObstacle()) {
						// if we have obstacle at cell 3 and cars at ->[c_2]->[c_3]->[c_5] then we need ->[c_2]->[o_3]->[c_3]->[c_5]
						if (iterCar.getPosition() > obstacleCar.getPosition() && !ins) {
							newCarList.add(obstacleCar);
							iterator.remove();
							ins = true;
						} else if (iterCar.getPosition() == obstacleCar.getPosition() && !ins) {
							ins = true;
						}
					}
					newCarList.add(iterCar);						
				}
				if (!ins) {
					newCarList.add(obstacleCar);
					iterator.remove();
				}
				cars = newCarList;
			}
		}
	}

	/**
	 * Gets the position of the nearest car from the entrance to the lane
	 * (how much space there is on the lane to enter)
	 *
	 * @return distance from the nearest car
	 * @author Maciej Zalewski
	 */
	public int getFirstCarPosition() {
		// if there are no cars, return length of the lane
		if (getAllCarsNumber() == 0) {
			return lane.getLength();
		}

		// if there are newly entered cars - show lack of space
		if (!obstaclesToAdd.isEmpty()) {
			return -1;
		}

		// in any other case, return position of the nearest car on the lane
		return cars.peek().getPosition();
	}

	/**
	 * This method returns a total number of cars on the lane
	 * (both "inside" and those that had just entered)
	 *
	 * @return number of cars on the lane
	 * @author Maciej Zalewski
	 */
	public int getAllCarsNumber() {
		return cars.size() + obstaclesToAdd.size();
	}

	public Car getBehindCar(Car car) {
		return getBehindCar(car.getPosition());
	}
	
	public Car getBehindCar(int pos) {
		int minPositiveDistance = Integer.MAX_VALUE;
		Car behindCar = null;
		List<Car> carsOnLane = this.getCars();

		for (Car _car : carsOnLane) {
			int carsDistance = pos - _car.getPosition();
			if (minPositiveDistance > carsDistance && carsDistance > 0) {
				minPositiveDistance = carsDistance;
				behindCar = _car;
			}
		}
		return behindCar;
	}
	
	public Car getFrontCar(Car car) {
		if(!car.getCurrentLane().equals(this)) throw new RuntimeException("wqe");
		if(this.cars.contains(car)) {
			int minPositiveDistance = Integer.MAX_VALUE;
			Car nextCar = null;
			List<Car> carsOnLane = this.getCars();
			boolean passedMyself = false;
			for (Car _car : carsOnLane) {
				//System.out.println("\t" + passedMyself+" " + _car );
				int carsDistance = _car.getPosition() - car.getPosition();
				if (passedMyself && minPositiveDistance > carsDistance && carsDistance >= 0) {
					minPositiveDistance = carsDistance;
					nextCar = _car;
					return nextCar;					
				}
				if(!passedMyself && _car.equals(car)) {
					passedMyself = true;
				}
			}
		} else {
			return getFrontCar(car.getPosition());			
		}
		return null;
	}
	
	public Car getFrontCar(int pos) {
		int minPositiveDistance = Integer.MAX_VALUE;
		Car nextCar = null;
		List<Car> carsOnLane = this.getCars();

		for (Car _car : carsOnLane) {
			int carsDistance = _car.getPosition() - pos;
			if (minPositiveDistance > carsDistance && carsDistance > 0) {
				minPositiveDistance = carsDistance;
				nextCar = _car;
			}
		}
		return nextCar;
	}

	public boolean anyEmergencyCarsOnLane() {
		for (Car car : cars) {
			if (car.isEmergency()) {
				return true;
			}
		}
		return false;
	}

	public int getEmergencyCarsOnLaneNr() {
		int counter = 0;
		for (Car car : cars) {
			if (car.isEmergency()) {
				counter++;
			}
		}
		return counter;
	}

	public synchronized int getClosestEmergencyCarDistance() {
		int closestDistance = linkLength();
		for (Car car : cars) {
			if (car.isEmergency()) {
				int distance = linkLength() - 1 - car.getPosition();
				if (distance < closestDistance) {
					closestDistance = distance;
				}
			}
		}
		return closestDistance;
	}

	// 2019
	
	/**
	 * NOT safe to use during simulation <br>
	 * for safe method use {@link #addCarToLaneWithIterator(Car)} <br>
	 * Use this.car list to add new Car
	 * Iterator will not be changed 
	 */
	public boolean addCarToLane(Car car) {
		boolean added = false;
		if(car.getPosition() == 0) {
			this.cars.addFirst(car);
			added = true;
		} else {
			ListIterator<Car> tempIt = this.cars.listIterator();
			while(tempIt.hasNext()) {
				if(tempIt.next().getPosition() > car.getPosition()) {
					this.carIterator.previous();
					this.carIterator.add(car);
					added = true;	// we can add it in the middle of list
					break;
				}
			}			
		}
		// it needs to be put as last element of list and this.carIterator.hasPrevious() is false
		if(!added) {
			this.carIterator.add(car);
			added = true;
		}
		
		return added;
	}
	
	
	/**
	 * NOT safe to use during simulation <br>
	 * for safe method use {@link #removeCarFromLaneWithIterator(Car)} <br>
	 * Use this.car list to remove Car
	 * Iterator will not be changed 
	 */
	public boolean removeCarFromLane(Car car) {
		boolean removed = false;
		ListIterator<Car> tempIt = this.cars.listIterator();
		while(tempIt.hasNext()) {
			if(tempIt.next().equals(car)) {
				this.carIterator.remove();
				removed = true;	// we can remove it from the middle of list
				break;
			}
		}
				
		return removed;
		
	}
	
	
	/**
	 * Safe to use during simulation <br>
	 * Use this.carIterator to add new Car
	 * It will be added behind (or more than 1 behind) current position of Iterator
	 * Iterator will be pointing at next to newly added car
	 */
	public boolean addCarToLaneWithIterator(Car car) {
		boolean added = false;
		int id = 0;
		if(car instanceof Emergency) {
			//	[c_4] -> [c_5] -> [e_5] -> [c_6] 
			while(this.carIterator.hasNext() && this.carIterator.next().getPosition() <= car.getPosition())
			{ // normalize iterator position, we want it to be in front of car
			}
			while(this.carIterator.hasPrevious()) {
				Car c = this.carIterator.previous();
				if(c.getPosition() <= car.getPosition()) {
					this.carIterator.next();
					this.carIterator.add(car);
					added = true;	// we can add it in the middle of list
					break;
				}
				id++;
			}
			//if(this.carIterator.hasPrevious()) this.carIterator.previous();
		} else {
			while(this.carIterator.hasPrevious()) {
				if(this.carIterator.previous().getPosition() < car.getPosition()) {
					this.carIterator.next();
					this.carIterator.add(car);
					added = true;	// we can add it in the middle of list
					break;
				}
		}
		}
		// it needs to be put as last element of list and this.carIterator.hasPrevious() is false
		if(!added) {
			//System.out.println("add last");
			this.carIterator.add(car);
			added = true;
		}
		
		return added;
	}
	
	/**
	 * Safe to use during simulation <br>
	 * Use this.carIterator to remove Car
	 * It will be removed from behind (or more than 1 behind) current position of Iterator
	 */
	public boolean removeCarFromLaneWithIterator(Car car) {
		boolean removed = false;
		boolean removeFromPrevious;
		
		if(this.carIterator.hasPrevious() && this.carIterator.previous().getPosition() < car.getPosition()) {
			removeFromPrevious = false;
			if(this.carIterator.hasNext()) this.carIterator.next();
		} else {
			removeFromPrevious = true;
			if(this.carIterator.hasNext()) this.carIterator.next();
		}
		
		if(removeFromPrevious) {	
			if(this.carIterator.hasNext()) this.carIterator.next();
			while(this.carIterator.hasPrevious()) {
				if(this.carIterator.previous().equals(car)) {
					this.carIterator.remove();
					removed = true;	// we can remove it from the middle of list
					break;
				}
			}
			//if(this.carIterator.hasPrevious()) this.carIterator.previous();
		} else {
			int stepsForward = 0;
			if(this.carIterator.hasPrevious()) this.carIterator.previous();
			while(this.carIterator.hasNext()) {
				stepsForward++;
				if(this.carIterator.next().equals(car)) {
					this.carIterator.remove();
					removed = true;	// we can remove it from the middle of list
					break;
				}
			}
			if(this.carIterator.hasNext()) this.carIterator.next();
			for(int i=0; i<stepsForward; i++) {
				this.carIterator.previous();
			}
		}
		return removed;
	}
	

	public boolean canAddCarToLane(Car car) {
		ListIterator<Car> tempIt = this.cars.listIterator();
		while(tempIt.hasNext()) {
			if(tempIt.next().getPosition() == car.getPosition()) {
				return false;
			}
		}			
		return true;
	}

	public boolean canAddCarToLaneOnPosition(int pos) {
		ListIterator<Car> tempIt = this.cars.listIterator();
		while(tempIt.hasNext()) {
			Car car = tempIt.next();
			if(car.getPosition() == pos) {
				return false;
			}
		}			
		return true;
	}
	
	public boolean canAddEmergencyToLaneOnPosition(int pos) {
		ListIterator<Car> tempIt = this.cars.listIterator();
		while(tempIt.hasNext()) {
			Car car = tempIt.next();
			if((car instanceof Emergency || car instanceof Obstacle) && car.getPosition() == pos) {
				return false;
			}
		}			
		return true;
	}
	
	Lane getLane() {
		return lane;
	}

	LinkedList<Car> getCars() {
		return cars;
	}

	List<Car> getEnteringCars() {
		return obstaclesToAdd;
	}

	RealEView getRealView() {
		return realView;
	}

	public RealSimulationParams getParams() {
		return params;
	}

	boolean getCarApproaching() {
		return carApproaching;
	}

	boolean getWait() {
		return wait;
	}

	void setWait(boolean wait) {
		this.wait = wait;
	}
	
	ListIterator<Car> getCarsIterator() {
		return carIterator;
	}
	// 2019 end

	public CarInfoCursor carInfoForwardCursor() {
		return new CarInfoCursorForwardImpl();
	}

	public CarInfoCursor carInfoBackwardCursor() {
		return new CarInfoCursorBackwardImpl();
	}

	public boolean isBlocked() {
		return blocked;
	}

	public void block() {
		blocked = true;
	}

	public void unblock() {
		blocked = false;
	}

	public void installInductionLoop(int line, CarDriveHandler handler) throws IndexOutOfBoundsException {
		LOGGER.trace("Instaling IL ona lane " + lane + " at distance: " + line);
		if (line < 0 || line > linkLength()) {
			throw new IndexOutOfBoundsException("line = " + line);
		}

		InductionLoop loop = new InductionLoop(line, handler);
		positionInductionLoops.put(line, loop);
	}
	
	private void addNewObstaclesFromCorelane() {
		List<Integer> cellList = lane.getRecentlyActivatedBlockedCellsIndexList(); 	
		for(Integer blickedCell : cellList) {
			obstaclesToAdd.add(new Obstacle(blickedCell, this));
		}
	}
	
	private void removeExpiredObstacleFromCorelane() {
		List<Integer> cellList = lane.getRecentlyExpiredBlockedCellsIndexList(); 
		Iterator<Car> carsIterator = cars.iterator();
		while (carsIterator.hasNext()) {
			Car car = carsIterator.next();
			if(car.isObstacle() && cellList.contains(car.getPosition())) {
				carsIterator.remove();
			}
		}
		
		ListIterator<Car> obstaclesIterator = obstaclesToAdd.listIterator();
		while(obstaclesIterator.hasNext()) {
			Car car = obstaclesIterator.next();
			if(car.isObstacle() && cellList.contains(car.getPosition())) {
				obstaclesIterator.remove();
			}
		}
	}
	
	public Map<Integer, InductionLoop> getPositionInductionLoops() {
		return positionInductionLoops;
	}
	
	/////////////////////////////////////////////////////////////////////

	public int getEmergencySpeedLimit() {
		return emergencySpeedLimit;
	}

	public int getEmergencyAcceleration() {
		return emergencyAcceleration;
	}

	public int getSpeedLimit() {
		return speedLimit;
	}

	public int getFirstCarPos() {
		return firstCarPos;
	}

	public void setFirstCarPos(int firstCarPos) {
		this.firstCarPos = firstCarPos;
	}

	static class InductionLoop {
		final int line;
		final CarDriveHandler handler;

		private InductionLoop(int line, CarDriveHandler handler) {
			this.line = line;
			this.handler = handler;
		}
	}

	private abstract class CarInfoUniCursorImpl implements CarInfoCursor {
		protected Car car;

		public Lane currentLane() {
			if (car == null) {
				throw new NoSuchElementException();
			}
			return lane;
		}

		public int currentPos() {
			if (car == null) {
				throw new NoSuchElementException();
			}
			return car.getPosition() - offset;
		}

		public int currentVelocity() {
			if (car == null) {
				throw new NoSuchElementException();
			}
			return car.getVelocity();
		}

		public Driver currentDriver() {
			if (car == null) {
				throw new NoSuchElementException();
			}
			return car.getDriver();
		}

		public Lane beforeLane() {
			if (car == null) {
				throw new NoSuchElementException();
			}
			return car.getBeforeLane();
		}

		public int beforePos() {
			if (car == null) {
				throw new NoSuchElementException();
			}

			return car.getBeforePos() - offset;
		}

		public boolean isValid() {
			return (car != null);
		}
	}

	private class CarInfoCursorForwardImpl extends CarInfoUniCursorImpl {

		private final Iterator<Car> cit;

		private CarInfoCursorForwardImpl() {
			cit = cars.iterator();
			if (cit.hasNext()) {
				car = cit.next();
			} else {
				car = null;
			}
			if(car != null && car.isObstacle()) {
				next();
			}
		}

		public void next() {
			if (!cit.hasNext()) {
				car = null;
			} else {
				car = cit.next();
			}
			if(car != null && car.isObstacle()) {
				next();
			}
		}
	}

	private class CarInfoCursorBackwardImpl extends CarInfoUniCursorImpl {
		private final ListIterator<Car> cit;

		private CarInfoCursorBackwardImpl() {
			cit = cars.listIterator(cars.size());
			if (cit.hasPrevious()) {
				car = cit.previous();
			} else {
				car = null;
			}
			if(car != null && car.isObstacle()) {
				this.next();
			}
		}

		public void next() {
			if (!cit.hasPrevious()) {
				car = null;
			} else {
				car = cit.previous();
			}
			if(car != null && car.isObstacle()) {
				next();
			}
		}
	}

	class InductionLoopPointer {
		private int i;

		InductionLoopPointer() {
			i = 0;
		}

		boolean atEnd() {
			return i == positionInductionLoops.size();
		}

		InductionLoop current() {
			return positionInductionLoops.get(i);
		}

		void forward() {
			if (i < positionInductionLoops.size()) {
				i++;
			}
		}
	}
}
