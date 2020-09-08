package pl.edu.agh.cs.kraksim.learning;

import com.sun.org.apache.bcel.internal.generic.ARETURN;
import org.apache.log4j.Logger;
import pl.edu.agh.cs.kraksim.core.Link;

import java.lang.reflect.Array;
import java.util.Arrays;

public class QLearner {
    private static final Logger qlLogger = Logger.getLogger(QLearner.class);

    IEnv thisWorld;
    Policy policy;

    double epsilon;
    double alpha;
    double gamma;
    double lambda;

    int[] dimSize;
    int[] state;
    int action;

    public boolean running;
    boolean shouldResetState = true;

    private Link link;

    public QLearner() {}

    public QLearner(IEnv world, Link l) {
        link = l;

        // Getting the world from the invoking method.
        thisWorld = world;

        // Get dimensions of the world.
        dimSize = thisWorld.getDimension();

        // Creating new policy with dimensions to suit the world.
        policy = new Policy(dimSize);

        // Initializing the policy with the initial values defined by the world.
        policy.initValues(thisWorld.getInitValues());

        // set default values
        epsilon = 0.1;
        alpha = 0.2;
        gamma = 0.8;
        lambda = 0.1;

        System.out.println("QLearner initialised");

    }

    // FIXME cleanup
    // execute one epoch of QLearning
    public void runEpoch() {
//        runPreEpoch();
////             Kraksim simulation
//        runPostEpoch();
    }

    public void runPreEpoch() {
        if (shouldResetState) {
            state = thisWorld.resetState();
            shouldResetState = false;
        }

        if (thisWorld.endState()) {
            shouldResetState = true;
        }
        // Calculate d_e and d_c based on metrics e.g. avg velocity
        action = selectAction(state);
    }

    public void runPostEpoch() {
        int[] newState = thisWorld.getNextState(action);
        double reward = thisWorld.getReward();

        double this_Q = policy.getQValue(state, action);
        double max_Q = policy.getMaxQValue(newState);

        // Calculate new Value for Q
        double new_Q = this_Q + alpha * (reward + gamma * max_Q - this_Q);
        policy.setQValue(state, action, new_Q);

        // Set state to the new state.
        state = newState;
    }

    /**
     * Greedy action selector
     *
     * @param state
     * @return
     */
    private int selectAction(int[] state) {

        double[] qValues = policy.getQValuesAt(state);
        int selectedAction = -1;

        double maxQ = -Double.MAX_VALUE;
        int[] doubleValues = new int[qValues.length];
        int maxDV = 0;

        //Explore
        if (Math.random() < epsilon) {
            selectedAction = -1;
        } else {

            for (int action = 0; action < qValues.length; action++) {
                if (qValues[action] > maxQ) {
                    selectedAction = action;
                    maxQ = qValues[action];
                    maxDV = 0;
                    doubleValues[maxDV] = selectedAction;
                } else if (qValues[action] == maxQ) {
                    maxDV++;
                    doubleValues[maxDV] = action;
                }
            }

            if (maxDV > 0) {
                int randomIndex = (int) (Math.random() * (maxDV + 1));
                selectedAction = doubleValues[randomIndex];
            }
        }

        // Select random action if all qValues == 0 or exploring.
        if (selectedAction == -1) {

            // System.out.println( "Exploring ..." );
            selectedAction = (int) (Math.random() * qValues.length);
        }

        // Choose new action if not valid.
        while (!thisWorld.validAction(selectedAction)) {

            selectedAction = (int) (Math.random() * qValues.length);
            // System.out.println( "Invalid action, new one:" + selectedAction);
        }

        return selectedAction;
    }


    public void dumpStats()
    {
//        if(link.getId().equals("L1R1") || link.getId().equals("R1L1") || link.getId().equals("L1T1") || link.getId().equals("T1L1") || link.getId().equals("R1T1") || link.getId().equals("T1R1"))
//        {
//
//        }
//        else
//        {
//            return;
//        }
//
//        for (int i = 0; i < 7; i++)
//        {
//            int[] state = {i};
//            double[] qValues = policy.getQValuesAt(state);
//            int maxAction = -1;
//            for (int j = 0; j < qValues.length; j++)
//            {
//                maxAction = selectAction(state);
//            }
//
//            qlLogger.info("Id: " + link.getId() + " S: " + state[0] + " A: " + maxAction);
//        }

        Object[] a = new Object[1];
        a[0] = policy.getQTable();

//        Object[][] tmpTab = new Object[1][];
//        tmpTab = ;

        qlLogger.info(Arrays.deepToString(a));
    }

    public Policy getPolicy() {
        return policy;
    }

    public void setAlpha(double a) {
        if (a >= 0 && a < 1) {
            alpha = a;
        }
    }

    public double getAlpha() {
        return alpha;
    }

    public void setGamma(double g) {
        if (g > 0 && g < 1) {
            gamma = g;
        }
    }

    public double getGamma() {
        return gamma;
    }

    public void setEpsilon(double e) {
        if (e > 0 && e < 1) {
            epsilon = e;
        }
    }

    public double getEpsilon() {
        return epsilon;
    }

    //AK: let us clear the policy
    public Policy newPolicy() {
        policy = new Policy(dimSize);
        // Initializing the policy with the initial values defined by the world.
        policy.initValues(thisWorld.getInitValues());
        return policy;
    }
}
	
