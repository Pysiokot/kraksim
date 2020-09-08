package pl.edu.agh.cs.kraksim.learning;

import com.google.common.collect.Lists;
import org.apache.log4j.Logger;
import pl.edu.agh.cs.kraksim.core.City;
import pl.edu.agh.cs.kraksim.core.Lane;
import pl.edu.agh.cs.kraksim.core.Link;
import pl.edu.agh.cs.kraksim.iface.Clock;
import pl.edu.agh.cs.kraksim.iface.carinfo.CarInfoIView;
import pl.edu.agh.cs.kraksim.ministat.MiniStatEView;
//import pl.edu.agh.cs.kraksim.params.DParams;

import java.util.ArrayList;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.StreamSupport;

public class MnEmergencyEnv implements IEnv {
    private static final Logger LOG = Logger.getLogger(MnEmergencyEnv.class);

    private static final double INIT_VALS = 0;
    private static final int NUM_OF_STATE_PARAMS = 2;   // e.g. number of emergency vehicles, traffic density
    private static final int NUM_ACTIONS = 6;   // combinations of two params (d_c, d_e) with three possible values, e.g. d_c = {10, 20, 30}, d_e = {5, 10, 15}

    private static final ArrayList<Integer> DE_VALUES = Lists.newArrayList(5, 10, 15);
    private static final ArrayList<Integer> DC_VALUES = Lists.newArrayList(10, 20, 30);


    private final Link link;
    private final City city;
    private final MiniStatEView statView;
    private final CarInfoIView carInfoView;
    private final Clock clock;

    private double previousDensity;
    private int reward = 0;
    private int timeStamp = 0;
    private int successScore = 0;
    private int failScore = 0;

    public MnEmergencyEnv(Link link, City city, MiniStatEView statView, CarInfoIView carInfoView, Clock clock) {
        this.link = link;
        this.city = city;
        this.statView = statView;
        this.carInfoView = carInfoView;
        this.clock = clock;
    }

    @Override
    public int[] getDimension() {
        int[] retDim = new int[NUM_OF_STATE_PARAMS + 1];
        retDim[0] = 3;  // nr of emergency cars: 0, 1, >1
        retDim[1] = 7;  // density of normal cars: <0.1, 0.1 - 0.2, ...  0.6 - 0.7, >0.7
        retDim[2] = NUM_ACTIONS;  // d_c and d_e combinations
        return retDim;
    }

    @Override
    public int[] getNextState(int action) {
        return getState(action);
    }

    private int[] getState(int action) {

        int emVehState;
        int emVehCount = statView.ext(link).getEmergencyVehiclesCount();
        if (emVehCount == 0) {
            emVehState = 0;
        } else if (emVehCount == 1) {
            emVehState = 1;
        } else {
            emVehState = 2;
        }

        int allLanesLength = StreamSupport.stream(Spliterators.spliteratorUnknownSize(link.laneIterator(), Spliterator.ORDERED), false)
                .mapToInt(Lane::getLength)
                .sum();

        int normalCarsCount = statView.ext(link).getNormalCarsCount();

        double density = ((double) (normalCarsCount + emVehCount)) / allLanesLength;

        int densityState;
        if (density < 0.05) {
            densityState = 0;
        } else if (density >= 0.05 && density < 0.1) {
            densityState = 1;
        } else if (density >= 0.1 && density < 0.15) {
            densityState = 2;
        } else if (density >= 0.15 && density < 0.2) {
            densityState = 3;
        } else if (density >= 0.2 && density < 0.25) {
            densityState = 4;
        } else if (density >= 0.25 && density < 0.3) {
            densityState = 5;
        } else {
            densityState = 6;
        }

        if (density != 0) {
            LOG.info(String.format("Next state: %d %s %d %d %.2f", clock.getTurn(), link.getId(), action, emVehCount, density));
        }

        int[] state = {emVehState, densityState};

        calculateReward(density);
        previousDensity = density;

        return state;
    }

    private void calculateReward(double density) {
        // compare density
        if (previousDensity == 0 || density == 0) {
            reward = 0;
        } else if (previousDensity == density) {
            reward = 0;
        } else if (previousDensity < density) {
            failScore++;
            reward = -50;
        } else {
            successScore++;
            reward = 100;
        }
    }

    @Override
    public double getReward() {
        return reward;
    }

    @Override
    public boolean validAction(int action) {
        updateDParams(action);
        return action >= 0 && action < NUM_ACTIONS;
    }

    private void updateDParams(int action) {
//        int oldDe = DParams.getInstance(link).getDe();
//        int oldDc = DParams.getInstance(link).getDc();
//
//        int tmpDc, tmpDe;
//
//        switch (action) {
//            case 0:
//                tmpDe = DParams.getInstance(link).getDe();
//                tmpDc = getNext(DC_VALUES, oldDc);
//                DParams.getInstance(link).setDc(tmpDc);
//                break;
//            case 1:
//                tmpDe = getNext(DE_VALUES, oldDe);
//                tmpDc = DParams.getInstance(link).getDc();
//                DParams.getInstance(link).setDe(tmpDe);
//                break;
//            case 2:
//                tmpDe = getNext(DE_VALUES, oldDe);
//                tmpDc = getNext(DC_VALUES, oldDc);
//                DParams.getInstance(link).setDe(tmpDe).setDc(tmpDc);
//                break;
//            case 3:
//                tmpDe = DParams.getInstance(link).getDe();
//                tmpDc = getPrevious(DC_VALUES, oldDc);
//                DParams.getInstance(link).setDc(tmpDc);
//                break;
//            case 4:
//                tmpDe = getPrevious(DE_VALUES, oldDe);
//                tmpDc = DParams.getInstance(link).getDc();
//                DParams.getInstance(link).setDe(tmpDe);
//                break;
//            case 5:
//                tmpDe = getPrevious(DE_VALUES, oldDe);
//                tmpDc = getPrevious(DC_VALUES, oldDc);
//                DParams.getInstance(link).setDe(tmpDe).setDc(tmpDc);
//                break;
//            default:
//                throw new RuntimeException("Unknown action");
//        }
//
//        LOG.info(String.format("Next DParams: %d, %s %d %d %d", clock.getTurn(), link.getId(), action, tmpDe, tmpDc));
    }


    private Integer getNext(List<Integer> allValues, int oldParam) {
        int idx = allValues.indexOf(oldParam);
        if (idx == allValues.size() - 1) {
            return oldParam;
        }
        return allValues.get(idx + 1);
    }

    private Integer getPrevious(List<Integer> allValues, int oldParam) {
        int idx = allValues.indexOf(oldParam);
        if (idx == 0) {
            return oldParam;
        }
        return allValues.get(idx - 1);
    }

    @Override
    public boolean endState() {
        if (clock.getTurn() - timeStamp > 50) {
            timeStamp = clock.getTurn();
            return true;
        }
        return false;
    }

    @Override
    public int[] resetState() {
        if (successScore != 0 && failScore != 0) {
            LOG.info(String.format("Reset: %d %s %d %d", clock.getTurn(), link.getId(), successScore, failScore));
            successScore = 0;
            failScore = 0;
        }
        return getState(-1);
    }

    @Override
    public double getInitValues() {
        return INIT_VALS;
    }
}
