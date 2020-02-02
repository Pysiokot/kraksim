package pl.edu.agh.cs.kraksim.main.gui;

public interface SimulationVisualizer {

	void startLearningPhase(int phaseNum);

	void startTestingPhase();

	void endPhase();

	void end(long elapsed);

	void update(int turn);
}
