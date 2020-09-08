package pl.edu.agh.cs.kraksim.main.drivers;

import java.util.Random;

public class DriverArchetype {
    public static float[] rozklad;

    public float aggression;
    public float tiredness;
    public float independence;
    public float fear;

    public DriverArchetype()
    {
        Random r = new Random();
        float rand = r.nextFloat();
        if (rand <= rozklad[0]) // agresywny kierowca
        {
            this.aggression = 1.0f;
            this.fear = 0.0f;
            this.tiredness = 0.0f;
            this.independence = 0.8f;
        }
        else if (rand <= rozklad[1]) // zmęczony kierowca
        {
            this.fear = 0.5f;
            this.aggression = 0.1f;
            this.tiredness = 1.0f;
            this.independence = 0.95f;
        }
        else if(rand <= rozklad[2]) // normalny kierowca
        {
            this.fear = 0.25f;
            this.aggression = 0.3f;
            this.tiredness = 0.0f;
            this.independence = 0.5f;
        }
        else if(rand <= rozklad[3]) // niedzielny kierowca
        {
            this.fear = 1.0f;
            this.aggression = 0.0f;
            this.tiredness = 0.0f;
            this.independence = 0.1f;
        }
        else if(rand <= rozklad[4])// losowy
        {
            this.fear = r.nextFloat();
            this.aggression = r.nextFloat();
            this.tiredness = r.nextFloat();
            this.independence = r.nextFloat();
        }


//        this.aggression = 1.0f;
//        this.fear = 0.0f;
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
