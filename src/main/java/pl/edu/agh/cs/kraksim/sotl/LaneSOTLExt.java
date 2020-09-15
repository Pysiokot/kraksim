package pl.edu.agh.cs.kraksim.sotl;

import org.apache.log4j.Logger;
import pl.edu.agh.cs.kraksim.core.Lane;
import pl.edu.agh.cs.kraksim.iface.block.BlockIView;
import pl.edu.agh.cs.kraksim.iface.block.LaneBlockIface;
import pl.edu.agh.cs.kraksim.iface.eval.LaneEvalIface;
import pl.edu.agh.cs.kraksim.iface.mon.CarDriveHandler;
import pl.edu.agh.cs.kraksim.iface.mon.LaneMonIface;
import pl.edu.agh.cs.kraksim.iface.mon.MonIView;
import pl.edu.agh.cs.kraksim.learning.LightsParams;
import pl.edu.agh.cs.kraksim.main.Simulation;

class LaneSOTLExt implements LaneEvalIface {
	private static final Logger LOGGER = Logger.getLogger(LaneSOTLExt.class);

	private final SOTLParams params;
	private final LaneBlockIface laneBlockExt;

	private volatile int carCount = 0;
	private int sotlLaneValue = 0;
	private final String id;

	private final Lane thisLane;

	LaneSOTLExt(final Lane lane, MonIView monView, BlockIView blockView, SOTLParams params) {
		LOGGER.trace(lane);
		thisLane = lane;
		this.params = params;
		id = lane.getOwner().getId() + ':' + lane.getAbsoluteNumber();

		laneBlockExt = blockView.ext(lane);
		LaneMonIface laneMonitoring = monView.ext(lane);
		int zoneBegin = lane.getOwner().getLength() - Math.min(params.zoneLength, lane.getLength());
		laneMonitoring.installInductionLoop(zoneBegin, new CarDriveHandler() {
			public synchronized void handleCarDrive(int velocity, Object driver) {
				LOGGER.trace(" >>>>>>> INDUCTION LOOP FIRED" + lane + "  " + carCount + "++");
				carCount++;
			}
		});

		int zoneEnd = lane.getOwner().getLength();
		laneMonitoring.installInductionLoop(zoneEnd, new CarDriveHandler() {
			public synchronized void handleCarDrive(int velocity, Object driver) {
				LOGGER.trace(" >>>>>>> INDUCTION LOOP FIRED" + lane + "  " + carCount + "--");
				carCount--;
			}
		});
	}

	void turnEnded() {
		if (laneBlockExt.isBlocked()) {
			sotlLaneValue += carCount;
		} else {
			sotlLaneValue = 0;
		}
	}

	public float getEvaluation() {
		LOGGER.trace(id + " carCount=" + carCount + ", sotlValue=" + sotlLaneValue + ", blocked=" + laneBlockExt.isBlocked());

		int thresh = Simulation.useLearning ? LightsParams.getInstance(thisLane.getOwner()).getWaitingCars() : params.threshold;

		if (sotlLaneValue > thresh) {
			return sotlLaneValue;
		} else {
			return sotlLaneValue;
		}
	}

	public int getMinGreenDuration() {
		int ret = (int) ((carCount) * (float) params.carStartDelay + (carCount / (float) params.carMaxVelocity));

		int minVal = Simulation.useLearning ? LightsParams.getInstance(thisLane.getOwner()).getMin_green() : params.getMinGreenDuration();

		return Math.max(ret, minVal);
	}

	public void increaseMinGreenDurForLane()
	{
		params.increaseGreenDuration();
	}

	public void decreaseMinGreenDurForLane()
	{
		params.decreaseGreenDuration();
	}
}
