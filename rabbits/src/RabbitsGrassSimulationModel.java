import java.awt.Color;
import java.util.ArrayList;

import uchicago.src.sim.analysis.DataSource;
import uchicago.src.sim.analysis.OpenSequenceGraph;
import uchicago.src.sim.analysis.Sequence;
import uchicago.src.sim.engine.BasicAction;
import uchicago.src.sim.engine.Schedule;
import uchicago.src.sim.engine.SimModelImpl;
import uchicago.src.sim.engine.SimInit;
import uchicago.src.sim.gui.DisplaySurface;
import uchicago.src.sim.gui.ColorMap;
import uchicago.src.sim.gui.Object2DDisplay;
import uchicago.src.sim.gui.Value2DDisplay;
import uchicago.src.sim.util.SimUtilities;


/**
 * Class that implements the simulation model for the rabbits grass
 * simulation.  This is the first class which needs to be setup in
 * order to run Repast simulation. It manages the entire RePast
 * environment and the simulation.
 *
 * @author 
 */


public class RabbitsGrassSimulationModel extends SimModelImpl {	
	
	// Default values
	private static final int NUMRABBITS = 30;
	private static final int GRIDXSIZE = 20;
	private static final int GRIDYSIZE = 20;
	private static final int GRASSRATE = 10;
	private static final int BIRTHTHRESHOLD = 15;

	private Schedule schedule;
	private RabbitsGrassSimulationSpace grassSpace;
	private DisplaySurface displaySurf;
	private ArrayList rabbitsList;
	private OpenSequenceGraph amountGrassInSpace;
	private OpenSequenceGraph amountRabbitsInSpace;
	
	private int numRabbits = NUMRABBITS;
	private int gridXSize = GRIDXSIZE;
	private int gridYSize = GRIDYSIZE;
	private int grassRate = GRASSRATE;
	private int birthThreshold = BIRTHTHRESHOLD;

	
	public static void main(String[] args) {
			
		System.out.println("Rabbit skeleton");
		SimInit init = new SimInit();
	    RabbitsGrassSimulationModel model = new RabbitsGrassSimulationModel();
	    init.loadModel(model, "", false);
			
	}
	
	class grassInSpace implements DataSource, Sequence {
		public Object execute() {
			return getSValue();
		}
		public double getSValue() {
			return (double)grassSpace.getTotalGrass();
		}
	}
	
	class rabbitsInSpace implements DataSource, Sequence {
		public Object execute() {
			return getSValue();
		}
		public double getSValue() {
			return (double)grassSpace.getTotalRabbits();
		}
	}

	public void setup() {
		System.out.println("Running setup");
		grassSpace = null;
		rabbitsList = new ArrayList();
		schedule = new Schedule(1);

		//Tear Down displays
		if (displaySurf != null) {
			displaySurf.dispose();
		}
		displaySurf = null;

		if (amountGrassInSpace != null){
			amountGrassInSpace.dispose();
		}
		amountGrassInSpace = null;

		if (amountRabbitsInSpace != null){
			amountRabbitsInSpace.dispose();
		}
		amountRabbitsInSpace = null;

		// Create display
		displaySurf = new DisplaySurface(this, "Rabbit Grass Simulation 1");
		amountGrassInSpace = new OpenSequenceGraph("Amount of Grass in Space", this);
		amountRabbitsInSpace = new OpenSequenceGraph("Amounts of Rabbits in Space", this);

		// Register display
        registerDisplaySurface("Rabbit Grass Simulation 1", displaySurf);
        this.registerMediaProducer("Plot0", amountGrassInSpace);
        this.registerMediaProducer("Plot1", amountRabbitsInSpace);

	}

	public void begin() {
		System.out.println("Running begin");
		buildModel();
	    buildSchedule();
	    buildDisplay();

	    displaySurf.display();
	    amountGrassInSpace.display();
	    amountRabbitsInSpace.display();
	}
	
	public void buildModel() {
		System.out.println("Running BuildModel");

	    grassSpace = new RabbitsGrassSimulationSpace (gridXSize, gridYSize);
	    for (int i = 0; i < numRabbits; i++) {
	    	addNewRabbits();
	    }

	    // Only for reporting purposes
	    for (int i = 0; i < rabbitsList.size(); i++) {
	    	RabbitsGrassSimulationAgent ra = (RabbitsGrassSimulationAgent)rabbitsList.get(i);
	    	ra.report();
	    }
	}
	
	public void buildSchedule() {
		System.out.println("Running BuildSchedule");

		/**
		 * Simulation step, symbolizing an epoch within the simulation.
		 */
		class SimulationStep extends BasicAction{
			public void execute() {

				// 1.) Grow grass
				if (!grassSpace.spreadGrass(grassRate)) {
					System.out.println("GRID FULL OF GRASS !!"); 
				}
				SimUtilities.shuffle(rabbitsList);

				for (int i=0; i < rabbitsList.size(); i++) {
				    RabbitsGrassSimulationAgent rsa = (RabbitsGrassSimulationAgent)rabbitsList.get(i);

					// 2.) If applicable, bear new rabbits
					if (rsa.getEnergy() > BIRTHTHRESHOLD) {
						//See whether new rabbit won't be placed in the space 
						if( addNewRabbits()) {
							rsa.giveBirth((int)(BIRTHTHRESHOLD * 2 / 3) );
						}
						else {
							System.out.println("Rabbit not placed!!");
						}
					}
					// 3.) Move rabbits
					rsa.step();
				}
				// 4.) If applicable, ´let rabbits die
				cleanDeadRabbits();
				displaySurf.updateDisplay();
			}
		}

		schedule.scheduleActionBeginning(0, new SimulationStep());

		// Update displayed grass
		class updateGrassInSpace extends BasicAction{
			public void execute(){
				amountGrassInSpace.step();
			}
		}
		schedule.scheduleActionAtInterval(10, new updateGrassInSpace());

		// Update displayed rabbits
		class updateRabbitsInSpace extends BasicAction{
			public void execute(){
				amountRabbitsInSpace.step();
			}
		}
		schedule.scheduleActionAtInterval(10, new updateRabbitsInSpace());
	}

	public void buildDisplay() {

		System.out.println("Running BuildDisplay");

	    ColorMap map = new ColorMap();

	    map.mapColor(1, new Color(0, 170, 0));
	    map.mapColor(0, new Color(0, 239, 255));

	    Value2DDisplay displayGrass = new Value2DDisplay(grassSpace.getCurrentGrassSpace(), map);

	    Object2DDisplay displayRabbits = new Object2DDisplay(grassSpace.getCurrentRabbitsSpace());
	    displayRabbits.setObjectList(rabbitsList);

	    displaySurf.addDisplayableProbeable(displayGrass, "Garden");
	    displaySurf.addDisplayableProbeable(displayRabbits, "Rabbits");

	    amountGrassInSpace.addSequence("Grass in Space", new grassInSpace ());
	    amountRabbitsInSpace.addSequence("Rabbits in Space", new rabbitsInSpace());
	}

	// Generates new RabbitGrassSimulationAgent and places it onto the grassSpace and the rabbitsList.
	private boolean addNewRabbits () {
		RabbitsGrassSimulationAgent a = new RabbitsGrassSimulationAgent ();
		
		if (grassSpace.addAgent(a)) {
			rabbitsList.add(a);
			return true;
		}
		return false;
	}

	// Identify rabbits without energy and remove them.
	private int cleanDeadRabbits(){
	    int count = 0;
	    for (int i = (rabbitsList.size() - 1); i >= 0 ; i--){
	    	RabbitsGrassSimulationAgent rsa = (RabbitsGrassSimulationAgent)rabbitsList.get(i);
	      	if (rsa.getEnergy() < 1){
				grassSpace.removeRabbitsAt(rsa.getX(), rsa.getY());
				rabbitsList.remove(i);
				count++;
	      	}
	    }
	    // Number of rabbits that died during this step
	    return count;
	}
	
	public String[] getInitParam() {
		String [] initParams = { "numRabbits", "gridXSize", "gridYSize", "grassRate", "birthThreshold" };
		return initParams;
	}

	public String getName() {
		return "Rabbit";
	}

	public Schedule getSchedule() {
		return schedule;
	}
		
	public int getNumRabbits(){
	    return numRabbits;
	}

	public void setNumRabbits(int n){
		numRabbits = n;
	}

	public int getGridXSize(){
		return gridXSize;
	}

	public void setGridXSize(int x){
	    gridXSize = x;
	}

	public int getGridYSize(){
	    return gridYSize;
	}

	public void setGridYSize(int y){
		gridYSize = y;
	}
    
	public int getGrassRate() {
		return grassRate;
	}
	
	public void setGrassRate (int rate) {
		grassRate = rate;
	}
		 
	public int getBirthThreshold() {
		return birthThreshold;
	}
	
	public void setBirthThreshold(int t) {
		birthThreshold = t;
	}
	
}
