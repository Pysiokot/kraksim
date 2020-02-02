package pl.edu.agh.cs.kraksim.iface.sim;

import pl.edu.agh.cs.kraksim.main.drivers.Driver;

public interface CitySimIface {
	void setCommonTravelEndHandler(TravelEndHandler handler);

	void insertTravel(Driver driver, Route route, boolean rerouting);

	void simulateTurn();
}
