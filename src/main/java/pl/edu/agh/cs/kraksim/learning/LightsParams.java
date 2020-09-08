package pl.edu.agh.cs.kraksim.learning;

import pl.edu.agh.cs.kraksim.core.Link;

import java.util.HashMap;
import java.util.Map;

public class LightsParams {
    private static final Map<Link, LightsParams> linkToLightsParams = new HashMap<>();
    private static Integer P = 10;
    private static Integer M_G = 15;

    private int waitingCars;
    private int min_green;

    private LightsParams(int wc, int mg) {
        this.waitingCars = wc;
        this.min_green = mg;
    }

    public static LightsParams getInstance(Link link) {
        if (P == null || M_G == null) {
            throw new RuntimeException("DParams has not been initialized");
        }

        if (linkToLightsParams.get(link) == null) {
            linkToLightsParams.put(link, new LightsParams(P, M_G));
        }

        return linkToLightsParams.get(link);
    }

    public static void init(int de, int dc) {
        P = de;
        M_G = dc;
    }

    public int getWaitingCars()
    {
        return waitingCars;
    }

    public int getMin_green()
    {
        return min_green;
    }

    public LightsParams setWaitingCars(int newWC)
    {
        waitingCars = newWC;
        return this;
    }

    public LightsParams setMin_green(int newMG)
    {
        min_green = newMG;
        return this;
    }
}
