package template;

/* import table */
import logist.simulation.Vehicle;
import logist.agent.Agent;
import logist.behavior.DeliberativeBehavior;
import logist.plan.Plan;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import logist.topology.Topology.City;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Array;
import java.util.*;
/**
 * An optimal planner for one vehicle.
 */
@SuppressWarnings("unused")
public class DeliberativeTemplate implements DeliberativeBehavior {

	enum Algorithm { BFS, ASTAR, NAIVE }

	private City[] citiesIndex;
	private Task[] taskList;
	private boolean firstRun = true;

	/* Environment */
	Topology topology;
	TaskDistribution td;
	
	/* the properties of the agent */
	Agent agent;

	/* the planning class */
	Algorithm algorithm;
	ASTAR astar;
	BFS bfs;
	
	@Override
	public void setup(Topology topology, TaskDistribution td, Agent agent) {
		this.topology = topology;
		this.td = td;
		this.agent = agent;
		this.citiesIndex = new City[topology.size()];
		int k = 0;
		for (City c : topology.cities()) {
			citiesIndex[k] = c;
			k++;
		}

		// initialize the planner
		String algorithmName = agent.readProperty("algorithm", String.class, "ASTAR");
		
		// Throws IllegalArgumentException if algorithm is unknown
		algorithm = Algorithm.valueOf(algorithmName.toUpperCase());
	}
	
	@Override
	public Plan plan(Vehicle vehicle, TaskSet tasks) {
		Plan plan;

		if (firstRun) {
			int noTasks = 0;
			for (Iterator<Task> it = tasks.iterator(); it.hasNext();) {
				noTasks = it.next().id;
			}
			noTasks++;
			taskList = new Task[noTasks];
			for (Task t : tasks) {
				taskList[t.id] = t;
			}
			setupParams.taskList = taskList;
		}
		System.out.printf("Computing the plan with algorithm %s\n", agent.readProperty("algorithm", String.class, "NAIVE").toString());
		// Compute the plan with the selected algorithm.
		switch (algorithm) {
		case ASTAR:
			if (firstRun)
				this.astar = new ASTAR(vehicle, this.citiesIndex, tasks);
			else
				this.astar.updatePlan(tasks);
			plan = astar.computePlan();
			break;
		case BFS:
			if (firstRun)
				this.bfs = new BFS(vehicle, this.citiesIndex, tasks);
			else
				this.bfs.updatePlan(tasks);
			plan = bfs.computePlan();
			break;
		case NAIVE:
			plan = naivePlan(vehicle, tasks);
			break;
		default:
			throw new AssertionError("Should not happen.");
		}
		firstRun = false;
		return plan;
	}
	
	private Plan naivePlan(Vehicle vehicle, TaskSet tasks) {
		for(Task task : tasks) {
			System.out.println(task.id + " " + task.pickupCity.name + " " + task.deliveryCity.name);
		}
		City current = vehicle.getCurrentCity();
		Plan plan = new Plan(current);

		for (Task task : tasks) {
			// move: current city => pickup location
			for (City city : current.pathTo(task.pickupCity))
				plan.appendMove(city);

			plan.appendPickup(task);

			// move: pickup location => delivery location
			for (City city : task.path())
				plan.appendMove(city);

			plan.appendDelivery(task);

			// set current city
			current = task.deliveryCity;
		}
		return plan;
	}


	@Override
	public void planCancelled(TaskSet carriedTasks) {
		this.firstRun = false;
	}
}

class State{
	//0 = not picked up not delivered
	//1 = picked up not delivered
	//2 = delivered
	public int[] stateList;
	public double capacityLeft;

	public State(int[] s, double capacityLeft) {
		stateList = s.clone();
		this.capacityLeft = capacityLeft;
	}

	public boolean isFinalState() {
		for (int i = 0; i < (stateList.length-1); i++) {
			if (stateList[i] != 2) {
				return false;
			}
		}
		return true;
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(this.stateList);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null)
			return false;
		State o = (State) obj;
		if (!Arrays.equals(this.stateList, o.stateList))
			return false;
		if (!(this.capacityLeft == o.capacityLeft))
			return false;
		return true;
	}
}

class Node implements Comparable<Node> {
	public Node parent;
	public State state;
	public double cost;

	public Node (State state, double cost, Node parent) {
		this.cost = cost;
		this.state = state;
		this.parent = parent;
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(this.state.stateList);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null)
			return false;
		Node o = (Node) obj;
		if (!Arrays.equals(this.state.stateList, o.state.stateList))
			return false;
		if (this.cost != o.cost)
			return false;
		return true;
	}

	@Override
	public int compareTo(Node o) {
		return (int)(this.getHeuristicCosts() - o.getHeuristicCosts());
	}

	double getHeuristicCosts() {
		double h = 0;
		for (int y = 0; y < (state.stateList.length-1); y++) {
			if (state.stateList[y] == 0) {
				h = setupParams.taskList[y].pickupCity.distanceTo(setupParams.taskList[y].deliveryCity);
				break;
			}
		}
		return cost + h;
	}
}

class setupParams {
	static Task[] taskList;
}



