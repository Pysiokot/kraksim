package pl.edu.agh.cs.kraksim;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import pl.edu.agh.cs.kraksim.main.Simulation;
import pl.edu.agh.cs.kraksim.main.gui.GUISimulationVisualizer;
import pl.edu.agh.cs.kraksim.main.gui.MainVisualisationPanel;
import pl.edu.agh.cs.kraksim.main.gui.SimulationVisualizer;
import pl.edu.agh.cs.kraksim.sna.centrality.KmeansClustering;

import javax.swing.*;
import java.awt.*;
import java.util.Properties;

public class KraksimRunner {
	public static final Logger LOGGER = Logger.getLogger(KraksimRunner.class);

	private static final boolean testTries = false;

	private static final String[] configFiles = new String[] {"low", "mid", "hig"};
	/**
	 * Main
	 *
	 * @param args may contain config file path
	 */
	public static void main(String[] args) {

		if(testTries)
		{
			String startName = configFiles[0];

			for (String subName : configFiles){
				for (int i = 0; i<150 ; i++) {
					if (args.length > 0) {
						KraksimConfigurator.setConfigPath(args[0]);
					}

					System.out.println("Current try: " + Integer.toString(i) + " for: " + subName);

					final Properties props = KraksimConfigurator.getPropertiesFromFile();

					String traffic_file = props.getProperty("travelSchemeFile");
					traffic_file = traffic_file.replaceAll(startName, subName);

					props.setProperty("travelSchemeFile", traffic_file);

					String statOutFileName = props.getProperty("statOutFile") + Integer.toString(i + 1);

					statOutFileName = statOutFileName.replaceAll(startName, subName);

					props.setProperty("statOutFile", statOutFileName);
					// we assume that if there is no word about visualisation in config,
					// then it is necessary...
					// but if there is...
					boolean visualise = !(props.containsKey("visualization") && props.getProperty("visualization").equals("false"));

					//			boolean visualise = false;

					// set up Logger
					PropertyConfigurator.configure("src\\main\\resources\\log4j.properties");


					// set up the prediction
					String predictionEnabled = props.getProperty("enablePrediction");
					String predictionFileConfig = props.getProperty("predictionFile");
					if (!"true".equals(predictionEnabled)) {
						KraksimConfigurator.disablePrediction();
						LOGGER.info("Prediction disabled");
					} else {
						KraksimConfigurator.configurePrediction(predictionFileConfig);
						LOGGER.info("Prediction configured with file: " + predictionFileConfig);
					}

					// start simulation - with or without visualisation
					if (visualise) {
						String lookAndFeel = UIManager.getSystemLookAndFeelClassName();
						try {
							UIManager.setLookAndFeel(lookAndFeel);
						} catch (Exception e) {
							e.printStackTrace();
						}

						javax.swing.SwingUtilities.invokeLater(new Runnable() {
							public void run() {
								JFrame frame = new JFrame("Kraksim Visualiser");
								frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

								frame.getContentPane().add(new MainVisualisationPanel(props));
								frame.setSize(800, 600);
								frame.setVisible(true);
								Dimension dimension = Toolkit.getDefaultToolkit().getScreenSize();
								int x = (int) ((dimension.getWidth() - frame.getWidth()) / 2);
								int y = (int) ((dimension.getHeight() - frame.getHeight()) / 2);
								frame.setLocation(x, y);
							}
						});
					} else {

						KmeansClustering.setProperties(props);

						Simulation sim = new Simulation(KraksimConfigurator.prepareInputParametersForSimulation(props));

						Thread runner = new Thread(sim);
						runner.start();


						//				KmeansClustering.setProperties(props);
						//				Thread simThread = new Thread(new Simulation(KraksimConfigurator.prepareInputParametersForSimulation(props)));

						//				simThread.start();
						try {
							runner.join();
						} catch (InterruptedException e) {
							LOGGER.error("InterruptedException", e);
						}
					}
				}
			}
		}
		else
		{
			runProgram(args);
		}
	}

	private static void runProgram(String[] args)
	{
		if (args.length > 0) {
			KraksimConfigurator.setConfigPath(args[0]);
		}

		final Properties props = KraksimConfigurator.getPropertiesFromFile();

		props.setProperty("statOutFile", props.getProperty("statOutFile"));
		// we assume that if there is no word about visualisation in config,
		// then it is necessary...
		// but if there is...
		boolean visualise = !(props.containsKey("visualization") && props.getProperty("visualization").equals("false"));

		//			boolean visualise = false;

		// set up Logger
		PropertyConfigurator.configure("src\\main\\resources\\log4j.properties");


		// set up the prediction
		String predictionEnabled = props.getProperty("enablePrediction");
		String predictionFileConfig = props.getProperty("predictionFile");
		if (!"true".equals(predictionEnabled)) {
			KraksimConfigurator.disablePrediction();
			LOGGER.info("Prediction disabled");
		} else {
			KraksimConfigurator.configurePrediction(predictionFileConfig);
			LOGGER.info("Prediction configured with file: " + predictionFileConfig);
		}

		// start simulation - with or without visualisation
		if (visualise) {
			String lookAndFeel = UIManager.getSystemLookAndFeelClassName();
			try {
				UIManager.setLookAndFeel(lookAndFeel);
			} catch (Exception e) {
				e.printStackTrace();
			}

			javax.swing.SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					JFrame frame = new JFrame("Kraksim Visualiser");
					frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

					frame.getContentPane().add(new MainVisualisationPanel(props));
					frame.setSize(800, 600);
					frame.setVisible(true);
					Dimension dimension = Toolkit.getDefaultToolkit().getScreenSize();
					int x = (int) ((dimension.getWidth() - frame.getWidth()) / 2);
					int y = (int) ((dimension.getHeight() - frame.getHeight()) / 2);
					frame.setLocation(x, y);
				}
			});
		} else {

			KmeansClustering.setProperties(props);

			Simulation sim = new Simulation(KraksimConfigurator.prepareInputParametersForSimulation(props));

			Thread runner = new Thread(sim);
			runner.start();


			//				KmeansClustering.setProperties(props);
			//				Thread simThread = new Thread(new Simulation(KraksimConfigurator.prepareInputParametersForSimulation(props)));

			//				simThread.start();
			try {
				runner.join();
			} catch (InterruptedException e) {
				LOGGER.error("InterruptedException", e);
			}
		}
	}
}
