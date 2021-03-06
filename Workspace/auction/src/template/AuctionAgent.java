package template;

//the list of imports
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import logist.LogistSettings;
import logist.behavior.AuctionBehavior;
import logist.config.Parsers;
import logist.agent.Agent;
import logist.simulation.Vehicle;
import logist.plan.Plan;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import logist.topology.Topology.City;
import template.OpponentEstimation.Opponent;
import template.OpponentEstimation.OpponentWrapper;
import template.SLS.SLS;
import template.SLS.Solution;

/**
 * A very simple auction agent that assigns all tasks to its first vehicle and
 * handles them sequentially.
 * 
 */
@SuppressWarnings("unused")
public class AuctionAgent implements AuctionBehavior {

	private Topology topology;
	private TaskDistribution distribution;
	private Agent agent;
	private Random random;
	private Vehicle vehicle;
	private City currentCity;

	public static float planTimeout;
	public static float setupTimeout;
	public static float bidTimeout;
	private double initDiscout;
	public ArrayList<Task> myTasks;
	public ArrayList<Task> myTasksT;
	public SLS currentPlan;
	public SLS potentialPlan;
	private int round;
	public Opponent opponent;

	public static double upperMCBound;

	/*
	TODO:
	- Possible sharding of SLS solution for high number of tasks
	 */

	@Override
	public void setup(Topology topology, TaskDistribution distribution,
			Agent agent) {

		this.topology = topology;
		this.distribution = distribution;
		this.agent = agent;
		this.initDiscout = 0.7;
		this.vehicle = agent.vehicles().get(0);
		this.currentCity = vehicle.homeCity();
		long seed = -9019554669489983951L * currentCity.hashCode() * agent.id();
		this.random = new Random(seed);
		this.myTasks = new ArrayList<Task>();
		this.myTasksT = new ArrayList<Task>();
		this.currentPlan = new SLS(agent.vehicles(), new ArrayList<Task>(), (double)0);
		this.round = 1;
		LogistSettings ls = null;
        try {
            ls = Parsers.parseSettings("config/settings_auction.xml");
        }
        catch (Exception exc) {
            System.out.println("There was a problem loading the configuration file.");
        }

        this.setupTimeout = ls.get(LogistSettings.TimeoutKey.SETUP);
        this.bidTimeout = ls.get(LogistSettings.TimeoutKey.BID);
		this.planTimeout = ls.get(LogistSettings.TimeoutKey.PLAN);

        System.out.printf("Got the following settings during setup:\n setupTimeout: %f sec\n planTimeout: " +
				"%f sec\n bidTimeout: %f sec\n", this.setupTimeout, this.planTimeout, bidTimeout);

        this.upperMCBound = this.calculateBoundary();
        this.opponent = new OpponentWrapper(topology, agent.vehicles());
	}

	@Override
	public void auctionResult(Task previous, int winner, Long[] bids) {

		System.out.println("Bids: " + Arrays.toString(bids));
		if (winner == agent.id()) {
			currentCity = previous.deliveryCity;
			this.myTasks.add(previous);

			this.currentPlan.bestSolution.vehiclePlan = Utils.cloneArray(this.potentialPlan.bestSolution.vehiclePlan);
			opponent.auctionFeedback(previous, bids[(agent.id()+1)%2], false);
		}
		else {
			if (this.myTasksT.size() > 0)
				this.myTasksT.remove(this.myTasksT.size() - 1);
			opponent.auctionFeedback(previous, bids[(agent.id()+1)%2], true);

		}
	}
	
	@Override
	public Long askPrice(Task task) {
		long start = System.currentTimeMillis();
		Random random = new Random();
		// Check whether at least one vehicle can take this task
		boolean feasible = false;
		for (Vehicle v : this.agent.vehicles()) {
			if (v.capacity() >= task.weight) {
				feasible = true;
				break;
			}
		}

		if (feasible == false) return null;

		this.myTasksT.add(task);
		
		this.potentialPlan = new SLS(agent.vehicles(), this.myTasksT, (long) (this.bidTimeout / 2.0));
		
		double marginal = this.potentialPlan.bestSolution.computeCost() - this.currentPlan.bestSolution.computeCost();
		marginal = getRealMarginalCosts(marginal);
		System.out.printf("Marginal costs for the task: %f\n", marginal);
        
		long start1 = System.currentTimeMillis();
		long oppEstimation = opponent.estimateBid(task, (float) (this.bidTimeout / 2.0));
		//Probability of having zero marginal with new new plan
		double p = zeroMarginalProb(this.potentialPlan.bestSolution);
		
		
		//double bid = Math.max(marginal * Configuration.BID_COST_SHARE_AGENT, Configuration.MIN_BID);
		double bid = marginal;
		System.out.println("The final bid of the agent is " + bid);
		
		double constant = 1.1;
		// BIDDING
		if (this.round < 5) {
			bid = task.pickupCity.distanceTo(task.deliveryCity) * this.vehicle.costPerKm();
			bid *= this.initDiscout;
			this.initDiscout += 0.1;
		}
		// If its smaller than marginal costs, it's not worth decreasing the bid that drastically.
		else if (oppEstimation > marginal) {
			bid = bid + (0.3 + random.nextDouble() * 0.7) * (oppEstimation - bid);
		}
		else {
			if (this.myTasks.size() < 5) constant = 1.2 - p;
			bid = (long) (marginal * (constant));
		}
		this.round +=1;
		return (long) bid;
		
		
		//return null;
	}

	@Override
	public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {

		System.out.println("Average error of the opponent estimation over all rounds: " + opponent.getCurrentAvgError());
		
//		System.out.println("Agent " + agent.id() + " has tasks " + tasks);
		long time_start = System.currentTimeMillis();
	
		//Our solution : Recompute again solution using more time !!!!
        SLS sls = new SLS(this.agent.vehicles(), this.myTasks, this.planTimeout );
        List<Plan> plans = new ArrayList<Plan>();
        plans = sls.computePlans();
        long duration =  System.currentTimeMillis() - time_start;
        
        System.out.printf("The plan was generated in %d milliseconds.\n", duration);

        int reward = 0;
        for (Task t: myTasks) {
        	reward += t.reward;
		}

		double profit = reward - sls.bestSolution.computeCost();
        System.out.println("Made a profit of " + profit);

		return plans;
	}

	private double zeroMarginalProb (Solution s) {
		//Update graphPlan
		ArrayList<City> graphPlan = new ArrayList<>();
		//just use the longest plan (we can change then)
		int longestVehicle =0;
		for (int i=1; i< s.vehiclePlan.length; i++) {
			if (s.vehiclePlan[i].size() > longestVehicle) longestVehicle = i;
		}
		//create list of city to be visited (in order)
		for (int i=0; i<s.vehiclePlan[longestVehicle].size(); i++) {
			if (s.vehiclePlan[longestVehicle].get(i).action == 1 )
				graphPlan.add(s.vehiclePlan[longestVehicle].get(i).task.pickupCity);
			else
				graphPlan.add(s.vehiclePlan[longestVehicle].get(i).task.deliveryCity);
		}
		double prob = 0;
		//probability of finding such tasks
		for (int i =0; i<graphPlan.size() -1 ; i++) {
			for (int j=i+1; j<graphPlan.size(); j++) {
				if (i== 0) prob += this.distribution.probability(graphPlan.get(i), graphPlan.get(j));
				else if ( !graphPlan.get(i).equals(graphPlan.get(i-1)) & !graphPlan.get(i).equals(graphPlan.get(j)) && !graphPlan.get(j).equals(graphPlan.get(j-1))) prob += this.distribution.probability(graphPlan.get(i), graphPlan.get(j));
			}
		}
		//normalization factor (uniform distrobution among cities)
		return prob/this.topology.cities().size();
	}

	private double calculateBoundary() {
		double maxDistance = 0;
		for (City c : topology.cities()) {
			for (City d : topology.cities()) {
				if (c.distanceTo(d) > maxDistance) {
					maxDistance = c.distanceTo(d);
				}
			}
		}

		int costPerKm = 0;

		// Get max. cost per km
		for (Vehicle v : agent.vehicles()) {
			if (v.costPerKm() > costPerKm) {
				costPerKm = v.costPerKm();
			}
		}

		// Costs for driving to the the furthest city and delivering in the furthest city
		return 2 * maxDistance * costPerKm;
	}

	public static long getRealMarginalCosts(double marginal) {
		// Minimum 0, Maximum upperMCBound
		return (long) Math.min(Math.max(0, marginal), upperMCBound);
	}
}
