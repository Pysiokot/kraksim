package pl.edu.agh.cs.kraksim.ministat;

public class CarsBelowMinVelocity {
    private final double VELOCITY_THRESHOLD = 0D;

    private int totalCarsCount;
    private int carsAboveThreshold;

    private boolean countCarsWaitingForGreen;

    public CarsBelowMinVelocity(boolean countCarsWaitingForGreen){
        this.countCarsWaitingForGreen = countCarsWaitingForGreen;
    }

    public void insertCarInfo(double vel, boolean isWaitingForGreen){
        if(isWaitingForGreen && !countCarsWaitingForGreen)
        {
            return;
        }

        totalCarsCount++;

        if(vel >= VELOCITY_THRESHOLD)
        {
            carsAboveThreshold++;
        }
    }
}
