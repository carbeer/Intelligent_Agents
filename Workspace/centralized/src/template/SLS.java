package template;

import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import logist.LogistSettings;

import logist.Measures;
import logist.behavior.AuctionBehavior;
import logist.behavior.CentralizedBehavior;
import logist.agent.Agent;
import logist.config.Parsers;
import logist.simulation.Vehicle;
import logist.plan.Plan;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import logist.topology.Topology.City;

import java.util.Random;
import java.util.ArrayList;

public class SLS {
	
	ArrayList<Tupla>[] solutions;
	int numTasks;
	int numVechicles;
	int numCities;
	private Task[] taskList;
	private Vehicle[] vehiclesList;
	private double timeout;
	private double fixedProb;
	
	
	public SLS (Topology topology, List<Vehicle> vehicles, Task[] taskList, double timeout) {
		this.taskList = taskList;
		this.vehiclesList = new Vehicle[this.numVechicles];
		this.numVechicles = vehicles.size();
		this.numTasks = this.taskList.length;
		this.numCities = topology.size();
		this.solutions = (ArrayList<Tupla>[]) new ArrayList[this.numVechicles];
		this.timeout = timeout;
		//randomly chosen 
		this.fixedProb = 0.4;
		
		int k=0;
		for (Vehicle v : vehicles) {
			this.vehiclesList[k] = v;
			k++;
		}
		search();
	}
	
	private void search() {
		Set<ArrayList<Tupla>[]> neighbors = new HashSet<>();
		initialSolution();
		ArrayList<Tupla>[] tempSolution = this.solutions.clone();
		
		while (true) {
			chooseNeighbors(tempSolution, neighbors);
			localSearch(neighbors, tempSolution);
			//remove all these neighbors
			neighbors.clear();
		}
	}

	private List<Plan> computePlans() {
		List<Plan> plans = new ArrayList<Plan>();
		int i = 0;
		for (ArrayList<Tupla> tupleList : solutions) {
			City currentCity = vehiclesList[i].getCurrentCity();
			Plan plan = Plan.EMPTY;
			for (Tupla tuple : tupleList) {
				switch (tuple.action) {
					// Pickup task
					case 1:
						for (City c : currentCity.pathTo(tuple.task.pickupCity)) {
							plan.appendMove(c);
						}
						plan.appendPickup(tuple.task);
						currentCity = tuple.task.pickupCity;
						break;
					// Deliver task
					case 2:
						for (City c : currentCity.pathTo(tuple.task.deliveryCity)) {
							plan.appendMove(c);
						}
						plan.appendDelivery(tuple.task);
						currentCity = tuple.task.deliveryCity;
						break;
				}
			}
			i++;
			plans.add(plan);
		}
		return plans;
	}

	private void initialSolution() {
		//TO be optimized
		//at least there is one vehicle, give it sequentially all tasks
		int capacity = this.vehiclesList[0].capacity();
		City currentCity = this.vehiclesList[0].getCurrentCity();
		double costkm = this.vehiclesList[0].costPerKm();
		
		for (int i=0; i<this.numTasks; i++) {
			capacity -= this.taskList[i].weight;
			this.solutions[0].add(new Tupla(this.taskList[i], 1, capacity, costkm * currentCity.distanceTo(this.taskList[i].pickupCity)));
			capacity += this.taskList[i].weight;
			currentCity = this.taskList[i].pickupCity;
			this.solutions[0].add(new Tupla(this.taskList[i], 2, capacity, costkm * currentCity.distanceTo(this.taskList[i].deliveryCity)));
			currentCity = this.taskList[i].deliveryCity;
		}
	}

	/**
	 *
	 * @param s
	 * @param ns
	 */
	private void chooseNeighbors(ArrayList<Tupla>[] s, Set<ArrayList<Tupla>[]> ns) {
		Random rand = new Random();
		int randomVehicle = rand.nextInt(this.numVechicles);
		int a1;
		int a2;
		//Change order in randomVehicle
		//s.length is always even (one pickup one delivery for every task)
		for (int i=0; i < s[randomVehicle].size() / 2; i++ ) {
			a1 = rand.nextInt(s[randomVehicle].size());
			a2 = rand.nextInt(s[randomVehicle].size());
			ArrayList<Tupla> newList = swap(randomVehicle, a1, a2, s[randomVehicle]);
			if (newList != null) {
				//if it is allowed, I generate new solution changing the plan for randomVehicle
				ArrayList<Tupla>[] newSolution = s.clone();
				newSolution[randomVehicle] = newList;
				//fix the cumulative cost of that list
				fixCost(newSolution[randomVehicle], randomVehicle);
				if (!ns.contains(newSolution)) ns.add(newSolution);
			}
		}
		//Change tasks among vehicles (to choose how many)
		int howMany = 10;
		//it is always allowed 
		for (int i=0; i<howMany; i++) {
			a1 = rand.nextInt(this.numVechicles);
			a2 = rand.nextInt(this.numVechicles);
			//You can always add at the end with the new capacity
			ArrayList<Tupla>[] newSolution = s.clone();
			if (s[a1].size() > 0) {
				//it is always allowed as adding a sequential action does not affect previous capacity
				changeVehicle(a1, a2, newSolution, rand);
				if (!ns.contains(newSolution)) ns.add(newSolution);
			}
		}
	}
	
	private void changeVehicle(int v1, int v2, ArrayList<Tupla>[] s, Random rand) {
		//take the first(for sure the first action is a pickup  
		//Remove and shift (can be random then)
		Tupla entry = s[v1].remove(0);
		int i=0;
		boolean find = false;
		//remove the delivery action and rearrange the capacities
		while (!find) {
			//remove this element does not affect subsequent actions as the weight of the task was removed anyway
			if (s[v1].get(i).task == entry.task) { 
				s[v1].remove(i);
				find = true;
			}
			//if we've not found the delivery jet, just add the weight to every capacity
			else {
				s[v1].get(i).capacityLeft += entry.task.weight;
			}
		}
		//added at the end, no need to iterate for capacity (at the end full v2 capacity)
		entry.capacityLeft = this.vehiclesList[v2].capacity() - entry.task.weight;
		Tupla deliverEntry = new Tupla (entry.task, 2, this.vehiclesList[v2].capacity(), 0.);
		s[v2].add(entry);
		s[v2].add(deliverEntry);
		fixCost(s[v1], v1);
		fixCost(s[v2], v2);
	}

	/**
	 *
	 * @param v Current vehicle
	 * @param a1 Action 1
	 * @param a2 Action 2
	 * @param vPlan Plan of the vehicle
	 * @return
	 */
	private ArrayList<Tupla> swap(int v, int a1, int a2, ArrayList<Tupla> vPlan ) {
		ArrayList<Tupla> neighbor = (ArrayList<Tupla>) vPlan.clone();
		//swap elements
		Tupla temp = neighbor.get(a1);
		neighbor.set(a1, neighbor.get(a2));
		neighbor.set(a2, temp);
		//Short circuit evaluation
		if (checkSwap(neighbor, a1, this.vehiclesList[v].capacity()) && checkSwap(neighbor, a2, this.vehiclesList[v].capacity())) {
			return neighbor;
		}
		return null;
	}
	 
	private boolean checkSwap(ArrayList<Tupla> p, int a, double vehicleCapacity) {
		//check logical order
		if (p.get(a).action == 1) {
			//check if delivered is first than new pick up position (just if it has moved forward)
			for (int i=0; i <a; i++ ) if (p.get(i).task.equals(p.get(a).task))return false;
		}
		else {
			//check if picked up after new delivery
			for (int i=a+1; i < p.size(); i++) if (p.get(i).task.equals(p.get(a).task)) return false;		
		}
		//given that's logically correct, check for capacity constraints and update capacity
		double capacityLeft = vehicleCapacity;
		for (int j = 0; j < p.size(); j++ ) {
			//update capacity 
			if(p.get(j).action == 1) {
				p.get(j).capacityLeft = capacityLeft - p.get(j).task.weight;
				//if at some points I violate constraints
				if (capacityLeft <0) return false;					
			}
			else {
				p.get(j).capacityLeft = capacityLeft + p.get(j).task.weight;
			}
			capacityLeft = p.get(j).capacityLeft;
		}
		return true;
	}
	
	private void fixCost (ArrayList<Tupla> list, int v) {
		City currentCity = this.vehiclesList[v].getCurrentCity();
		list.get(0).cost = currentCity.distanceTo(list.get(0).task.pickupCity);
		currentCity = list.get(0).task.pickupCity;
		
		for (int i=1; i<list.size(); i++) {
			if (list.get(i).action == 1) {
				list.get(i).cost = list.get(i-1).cost + currentCity.distanceTo(list.get(i).task.pickupCity);
				currentCity = list.get(i).task.pickupCity;
			}
			else {
				list.get(i).cost = list.get(i-1).cost + currentCity.distanceTo(list.get(i).task.deliveryCity);
				currentCity = list.get(i).task.deliveryCity;
			}
		}
	}
	//Compute cost of a solution
	private double computeCost (ArrayList<Tupla>[] s) {
		double cost =0;
		for (int i=0; i < s.length; i++) {
			cost += s[i].get(s[i].size() -1).cost;
		}
		return cost;
	}
	
	private void localSearch(Set<ArrayList<Tupla>[]> ns, ArrayList<Tupla>[] ts) {
		//just to initialize 
		ArrayList<Tupla>[] best = ts;
		double bestCost = Double.POSITIVE_INFINITY;
		double newCost;

		//for sure best will an element of ns given the positive_infinity
		for (ArrayList<Tupla>[] s : ns) {
			newCost = computeCost(s);
			if (newCost < bestCost) {
				best = s;
				bestCost = newCost;
			}
		}
		if (bestCost < computeCost(ts)) ts = best;
		else {
			Random rand = new Random();
			double p = rand.nextDouble();
			if (p < this.fixedProb) ts = best;
		}
	}

	private class Tupla{
		public Task task;
		public int action;
		public double capacityLeft;
		double cost;
		
		public Tupla(Task task, int action, double capacityLeft, double cost) {
			this.task = task;
			this.action = action;
			this.capacityLeft = capacityLeft;
			this.cost = cost;
		}
	}
}
