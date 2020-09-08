package pl.edu.agh.cs.kraksim.learning;

import com.google.common.collect.Lists;
import org.apache.log4j.Logger;
import pl.edu.agh.cs.kraksim.core.City;
import pl.edu.agh.cs.kraksim.core.Link;
import pl.edu.agh.cs.kraksim.iface.Clock;
import pl.edu.agh.cs.kraksim.iface.carinfo.CarInfoIView;
import pl.edu.agh.cs.kraksim.ministat.MiniStatEView;

import java.util.ArrayList;
import java.util.List;

//import pl.edu.agh.cs.kraksim.params.DParams;

public class WaitingCarsEnv implements IEnv {
    private static final Logger LOG = Logger.getLogger(WaitingCarsEnv.class);

    private static final double INIT_VALS = 0;
    private static final int NUM_OF_STATE_PARAMS = 1;   // e.g. number of emergency vehicles, traffic density
    private static final int NUM_ACTIONS = 6;   // combinations of two params (d_c, d_e) with three possible values, e.g. d_c = {10, 20, 30}, d_e = {5, 10, 15}

    private static final ArrayList<Integer> LightP_Values = Lists.newArrayList(1, 3, 5, 10, 15, 20);
    private static final ArrayList<Integer> LightMinGreen_Values = Lists.newArrayList(5, 10, 15, 20, 25, 30);


    private final Link link;
    private final City city;
    private final MiniStatEView statView;
    private final CarInfoIView carInfoView;
    private final Clock clock;

    private double previousWaitingCars;
    private int reward = 0;
    private int timeStamp = 0;
    private int successScore = 0;
    private int failScore = 0;

    public WaitingCarsEnv(Link link, City city, MiniStatEView statView, CarInfoIView carInfoView, Clock clock) {
        this.link = link;
        this.city = city;
        this.statView = statView;
        this.carInfoView = carInfoView;
        this.clock = clock;
    }

    @Override
    public int[] getDimension() {
        int[] retDim = new int[NUM_OF_STATE_PARAMS + 1];
//        retDim[0] = 3;  // nr of emergency cars: 0, 1, >1
        retDim[0] = 7;  // density of normal cars: <0.1, 0.1 - 0.2, ...  0.6 - 0.7, >0.7
        retDim[1] = NUM_ACTIONS;  // d_c and d_e combinations
        return retDim;
    }

    @Override
    public int[] getNextState(int action) {
        return getState(action);
    }

    private int[] getState(int action) {

        int waitingCarsState;

        long waitingCarsCount = statView.ext(link).getCarCountOnRedLigth();

        if (waitingCarsCount <= 4) {
            waitingCarsState = 0;
        } else if (waitingCarsCount <= 8) {
            waitingCarsState = 1;
        } else if (waitingCarsCount <= 15) {
            waitingCarsState = 2;
        } else if (waitingCarsCount <= 25) {
            waitingCarsState = 3;
        } else if (waitingCarsCount <= 45) {
            waitingCarsState = 4;
        } else if (waitingCarsCount <= 80) {
            waitingCarsState = 5;
        } else {
            waitingCarsState = 6;
        }

        if (waitingCarsState != 0) {
            LOG.info(String.format("Next state: %d %s %d %d", clock.getTurn(), link.getId(), action, waitingCarsState));
        }

        int[] state = {waitingCarsState};

        calculateReward(waitingCarsState);
        previousWaitingCars = waitingCarsState;

        return state;
    }

    private void calculateReward(double waitingCarsState) {
        // compare density
        if (previousWaitingCars == 0 || waitingCarsState == 0) {
            reward = 0;
        } else if (previousWaitingCars == waitingCarsState) {
            reward = 0;
        } else if (previousWaitingCars < waitingCarsState) {
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
        int oldWaitingCars = LightsParams.getInstance(link).getWaitingCars();
        int oldMinGreen = LightsParams.getInstance(link).getMin_green();

        int tmpMG, tmpMC;

        switch (action) {
            case 0:
                tmpMC = LightsParams.getInstance(link).getWaitingCars();
                tmpMG = getNext(LightMinGreen_Values, oldMinGreen);
                LightsParams.getInstance(link).setMin_green(tmpMG);
                break;
            case 1:
                tmpMC = getNext(LightP_Values, oldWaitingCars);
                tmpMG = LightsParams.getInstance(link).getMin_green();
                LightsParams.getInstance(link).setWaitingCars(tmpMC);
                break;
            case 2:
                tmpMC = getNext(LightP_Values, oldWaitingCars);
                tmpMG = getNext(LightMinGreen_Values, oldMinGreen);
                LightsParams.getInstance(link).setWaitingCars(tmpMC).setMin_green(tmpMG);
                break;
            case 3:
                tmpMC = LightsParams.getInstance(link).getWaitingCars();
                tmpMG = getPrevious(LightMinGreen_Values, oldMinGreen);
                LightsParams.getInstance(link).setMin_green(tmpMG);
                break;
            case 4:
                tmpMC = getPrevious(LightP_Values, oldWaitingCars);
                tmpMG = LightsParams.getInstance(link).getMin_green();
                LightsParams.getInstance(link).setWaitingCars(tmpMC);
                break;
            case 5:
                tmpMC = getPrevious(LightP_Values, oldWaitingCars);
                tmpMG = getPrevious(LightMinGreen_Values, oldMinGreen);
                LightsParams.getInstance(link).setWaitingCars(tmpMC).setMin_green(tmpMG);
                break;
            default:
                throw new RuntimeException("Unknown action");
        }

        LOG.info(String.format("<lightsParams: turn=\"%d\", link=\"%s\" action=\"%d\" threshold=\"%d\" min_green=\"%d\" waiting=\"%d\" oldGreen=\"%d\"/>", clock.getTurn(), link.getId(), action, tmpMC, tmpMG, (int)statView.ext(link).getCarCountOnRedLigth(), oldMinGreen));
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
        if (clock.getTurn() - timeStamp > 200) {
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
