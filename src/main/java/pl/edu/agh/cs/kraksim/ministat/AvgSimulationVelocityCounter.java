package pl.edu.agh.cs.kraksim.ministat;

import java.util.LinkedList;

public class AvgSimulationVelocityCounter {

    private LinkedList<Double> avgVelocities;

    public AvgSimulationVelocityCounter()
    {
        avgVelocities = new LinkedList<>();
    }

    public void insertTurnAvgVelocity(double velocity)
    {
        avgVelocities.add(velocity);
    }

    private double getVelocitiesCount()
    {
        double sum = 0;

        for (double vel : avgVelocities)
        {
            sum += vel;
        }

        return sum;
    }

    public double getAvgSimulationVelocity()
    {
        return getVelocitiesCount() / avgVelocities.size();
    }
}
