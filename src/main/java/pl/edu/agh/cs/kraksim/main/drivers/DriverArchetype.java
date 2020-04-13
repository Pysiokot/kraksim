package pl.edu.agh.cs.kraksim.main.drivers;

import java.util.Random;

public class DriverArchetype {
    public float aggression;
    public float tiredness;
    public float independence;
    public float fear;

    public DriverArchetype()
    {
        float rand = new Random().nextFloat();
        if (rand <= 0.25f)
        {
            this.aggression = 1.0f;
            this.fear = 0.0f;
        }
        else if (rand <= 0.5f)
        {
            this.fear = 1.0f;
            this.aggression = 0.0f;
        }
        else if(rand <= 0.75f)
        {
            this.fear = 0.5f;
            this.aggression = 0.1f;
        }
        else
        {
            this.fear = 0.25f;
            this.aggression = 0.3f;
        }
    }

    public float getAggression() {
        return aggression;
    }

    public void setAggression(float aggression) {
        this.aggression = aggression;
    }

    public float getTiredness() {
        return tiredness;
    }

    public void setTiredness(float tiredness) {
        this.tiredness = tiredness;
    }

    public float getIndependence() {
        return independence;
    }

    public void setIndependence(float independence) {
        this.independence = independence;
    }

    public float getFear() {
        return fear;
    }

    public void setFear(float fear) {
        this.fear = fear;
    }
}
