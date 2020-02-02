package pl.edu.agh.cs.kraksim.visual;

import pl.edu.agh.cs.kraksim.core.City;
import pl.edu.agh.cs.kraksim.core.Link;
import pl.edu.agh.cs.kraksim.core.Node;
import pl.edu.agh.cs.kraksim.iface.block.BlockIView;
import pl.edu.agh.cs.kraksim.iface.carinfo.CarInfoCursor;
import pl.edu.agh.cs.kraksim.iface.carinfo.CarInfoIView;
import pl.edu.agh.cs.kraksim.main.drivers.Driver;
import pl.edu.agh.cs.kraksim.ministat.MiniStatEView;
import pl.edu.agh.cs.kraksim.visual.infolayer.InfoProvider;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Iterator;
import java.util.Random;

@SuppressWarnings("serial")
public class VisualizerComponent extends JPanel {

	private transient Dimension modelSize;

	private static final float VEHICLE_SIZE = 2.4f;
	public static final Dimension defaultDimension = new Dimension(640, 480);
	public static final Color BLOCKED_CELL_COLOR = Color.black;

	private transient City city;
	private transient CityMapVisualizer cityMapVisualizer; // NOPMD by
	// Bartosz
	// Rybacki on
	// 7/16/07 10:07
	// PM
	private transient CarInfoIView carInfoView;
	private transient BufferedImage snapshot;
	private float scale = 0.6f;

	private BlockIView blockView;

	private MiniStatEView statView;

	public VisualizerComponent() {
		super();
		setPreferredSize(defaultDimension);
		setMaximumSize(defaultDimension);
		revalidate();
		setClickListener();
	}

	

	public void loadMap(final City city, final CarInfoIView carInfoView,
			final BlockIView blockView, final MiniStatEView statView) {

		this.city = city;
		this.carInfoView = carInfoView;
        InfoProvider.getInstance().setCarInfoIView(this.carInfoView);
		this.blockView = blockView;
        InfoProvider.getInstance().setBlockView(this.blockView);
		this.statView = statView;
        InfoProvider.getInstance().setStatView(this.statView);
		
		modelSize = computeModelSize(city);

		cityMapVisualizer = new CityMapVisualizer(city, blockView,
				statView, modelSize.width, modelSize.height);
		final BufferedImage cityMap = cityMapVisualizer.getCityMap();

		// stworzenie buforaSieciDrog
		// pierwszy snapshot = mapa miasta bez samochodow
		snapshot = new BufferedImage(cityMap.getWidth(), cityMap.getHeight(),
				BufferedImage.TYPE_INT_RGB);
		final Graphics2D g2d = (Graphics2D) snapshot.getGraphics();
		g2d.drawImage(cityMap, null, 0, 0);
		// // save the image
		g2d.dispose();
		// File file = new File("c:/tmp/9x.png");
		// try
		// {
		// ImageIO.write(snapshot,"png",file);
		// System.out.print("Saved");
		// }
		// catch(Throwable e)
		// {
		// System.out.print(e);
		// }
		setDoubleBuffered(true);
		setPreferredSize(computeComponentSize(modelSize, scale));
		setOpaque(true);
		// setBorder(BorderFactory.createLineBorder(Color.RED));
		revalidate();
		update();
	}

	private static Dimension computeModelSize(final City city) {
		double maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE;
		for (Iterator<Node> i = city.nodeIterator(); i.hasNext();) {
			Node node = i.next();
			double x = node.getPoint().getX();
			double y = node.getPoint().getY();
			if (x > maxX) {
				maxX = x;
			}
			if (y > maxY) {
				maxY = y;
			}
		}
		return new Dimension((int) (maxX + 1.0), (int) (maxY + 1.0));
	}

	private static Dimension computeComponentSize(final Dimension modelSize,
			final float scale) {
		final Dimension dim = new Dimension();

		dim.width = (int) ((((float) modelSize.width * 2) + CityMapVisualizer.NODE_THICKNESS * 2) * scale);
		dim.height = (int) ((((float) modelSize.height * 2) + CityMapVisualizer.NODE_THICKNESS * 2) * scale);

		return dim;
	}

	/**
	 * Wywolyywane w Swing
	 */
	@Override
	public void paintComponent(final Graphics graphics) {
		super.paintComponent(graphics);

		final Graphics2D graphics2D = (Graphics2D) graphics;// .create();

		// ustawienie skali
		AffineTransform trans = graphics2D.getTransform();
		trans.scale(scale, scale);
		graphics2D.setTransform(trans);

		// rysowanie
		graphics2D.drawImage(snapshot, null, 0, 0);
		// g2.dispose();
	}

	/**
	 * Wywolywane z watku symulacji (SimulationDriver)
	 */
	public void update() {

		// pobranie ostatniej klatki z modelu zapisanie do snapshot
		// wyswietlenie buforaSieciDrog
		// wyswietlenie snapshot

		BufferedImage cityMap = cityMapVisualizer.getCityMap();

		Graphics2D g2d = snapshot.createGraphics();
		// stworzenie buforaSieciDrog
		// pierwszy snapshot = mapa miasta bez samochodow
		// setPreferredSize(new Dimension(2020, 2020));
		g2d.drawImage(cityMap, null, 0, 0);

		// rysowanie samochodow

		drawVehicles(g2d);
		repaint();
		// parentComponent.pack();

	}

	private void drawVehicles(Graphics2D g2d) {

		for (Iterator<Link> it = city.linkIterator(); it.hasNext();) {
			Link link = it.next();

			Point2D start = link.getBeginning().getPoint();
			Point2D end = link.getEnd().getPoint();
			// wektro prostopadly do osi jezdni
			double[] vectorOrtogonal;
			// wektor jednostkowy wyznaczajÄ…cy kierunek i zwrot ze start do
			// end
			double[] vectorAB;
			double[] vectorPair;

			vectorPair = GeometryHelper.computeVectors(start, end);
			vectorAB = new double[] { vectorPair[0], vectorPair[1] };
			vectorOrtogonal = new double[] { -vectorPair[1], vectorPair[0] };

			double xStart = start.getX() * 2 + vectorAB[0]
					* CityMapVisualizer.NODE_THICKNESS / 2.0;
			double yStart = start.getY() * 2 + vectorAB[1]
					* CityMapVisualizer.NODE_THICKNESS / 2.0;
			double xEnd = end.getX() * 2 - vectorAB[0]
					* CityMapVisualizer.NODE_THICKNESS / 2.0;
			double yEnd = end.getY() * 2 - vectorAB[1]
					* CityMapVisualizer.NODE_THICKNESS / 2.0;

			start = new Point2D.Double(xStart, yStart);
			end = new Point2D.Double(xEnd, yEnd);
			/*
			 * double celluarWidth = (start.distance(end) -
			 * CityMapVisualizer.NODE_THICKNESS) / link.getLength();
			 */
			double celluarWidth = start.distance(end) / link.getLength();

			assert celluarWidth != 0;
			// odleglosc krawedzi pasa od osi jezdni
			float laneRoadAxisOffset = CityMapVisualizer.LANE_WIDTH;
			// iterator pozycji samochodow w komorkach

			CarInfoCursor cursor;

			
			// [START] rysowanie aut na lewym pasie
			int offset = 0;
			int position = 0;
			for (int j = link.leftLaneCount() - 1; j >= 0; j--) {
				// [start] draw blocked Cells on lane
				java.util.List<Integer> blockedCellsList = link.getLeftLane(j).getActiveBlockedCellsIndexList();
				for(Integer blockedCell : blockedCellsList) {
					drawVehicle(g2d, start, laneRoadAxisOffset,
							vectorOrtogonal, blockedCell, vectorAB, celluarWidth, BLOCKED_CELL_COLOR);
				}
				// [end] blocked cells
				cursor = carInfoView.ext(link.getLeftLane(j))
						.carInfoForwardCursor();
				offset = link.getLeftLane(j).getOffset();
				while (cursor.isValid()) {
					position = cursor.currentPos() + offset;
					Color color = ((Driver)cursor.currentDriver()).getCarColor();
					drawVehicle(g2d, start, laneRoadAxisOffset,
							vectorOrtogonal, position, vectorAB, celluarWidth, color);
					cursor.next();
				}
								
				laneRoadAxisOffset += CityMapVisualizer.LANE_WIDTH;
			}
			// [END]
			// [START] rysowanie aut na glownych pasach
			for (int j = 0; j < link.mainLaneCount(); j++) {
				java.util.List<Integer> blockedCellsList = link.getMainLane(j).getActiveBlockedCellsIndexList();
				for(Integer blockedCell : blockedCellsList) {
					drawVehicle(g2d, start, laneRoadAxisOffset,
							vectorOrtogonal, blockedCell, vectorAB, celluarWidth, BLOCKED_CELL_COLOR);
				}
				// [end] blocked cells

				cursor = carInfoView.ext(link.getMainLane(j))
						.carInfoForwardCursor();
				offset = link.getMainLane(j).getOffset();
				while (cursor.isValid()) {
					position = cursor.currentPos() + offset;
					Color color = ((Driver)cursor.currentDriver()).getCarColor();
					drawVehicle(g2d, start, laneRoadAxisOffset,
							vectorOrtogonal, position, vectorAB, celluarWidth, color);
					cursor.next();
				}
				
				// [start] draw blocked Cells on lane
				laneRoadAxisOffset += CityMapVisualizer.LANE_WIDTH;
			}
			// [END]
			// [START] rysowanie aut na prawych pasach
			
			for (int j = link.rightLaneCount() - 1; j >= 0; j--) {
				// [start] draw blocked Cells on lane
				java.util.List<Integer> blockedCellsList = link.getRightLane(j).getActiveBlockedCellsIndexList();
				for(Integer blockedCell : blockedCellsList) {
					drawVehicle(g2d, start, laneRoadAxisOffset,
							vectorOrtogonal, blockedCell, vectorAB, celluarWidth, BLOCKED_CELL_COLOR);
				}
				// [end] blocked cells
				cursor = carInfoView.ext(link.getRightLane(j))
						.carInfoForwardCursor();
				offset = link.getRightLane(j).getOffset();
				while (cursor.isValid()) {
					position = cursor.currentPos() + offset;
					Color color = ((Driver)cursor.currentDriver()).getCarColor();
					drawVehicle(g2d, start, laneRoadAxisOffset,
							vectorOrtogonal, position, vectorAB, celluarWidth, color);
					cursor.next();
				}
					
				laneRoadAxisOffset += CityMapVisualizer.LANE_WIDTH;
			}
			// [END]

		}
	}

	private void drawVehicle(final Graphics2D g2d, final Point2D start,
			final float roadAxisOffset, final double[] vectorOrtogonal,
			final int drawingCelluarNum, final double[] vectorAB,
			final double celluarWidth, Color color) {
		double centerX = start.getX() + roadAxisOffset * vectorOrtogonal[0]
				+ (drawingCelluarNum * celluarWidth) * vectorAB[0];
		double centerY = start.getY() + roadAxisOffset * vectorOrtogonal[1]
				+ (drawingCelluarNum * celluarWidth) * vectorAB[1];
		double delta = VEHICLE_SIZE / 2;
		Rectangle2D vehicle = new Rectangle2D.Double(centerX - delta, centerY
				- delta, 2 * delta, 2 * delta);
		g2d.setColor(color);
		g2d.draw(vehicle);
		g2d.fill(vehicle);
	}

	private void setClickListener() {
		/**
		 * umoĹĽliwia klikanie po linkach (drogach)
		 */
		addMouseListener(new MouseListener() {
			
			@Override
			public void mouseReleased(MouseEvent e) {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public void mousePressed(MouseEvent e) {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public void mouseExited(MouseEvent e) {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public void mouseEntered(MouseEvent e) {
				
			}
			
			@Override
			public void mouseClicked(MouseEvent e) {
				cityMapVisualizer.showInfoMessageBox(e.getX(), e.getY(), scale);
			}
		});
	}
	
	public static double getVehicleSize() {
		return VEHICLE_SIZE;
	}

	public float getScale() {
		return scale;
	}

	public void setScale(float scale) {
		this.scale = scale;
		setPreferredSize(computeComponentSize(modelSize, scale));
		revalidate();
	}
}
