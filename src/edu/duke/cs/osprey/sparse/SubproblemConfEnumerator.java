package edu.duke.cs.osprey.sparse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import edu.duke.cs.osprey.confspace.RCTuple;

public class SubproblemConfEnumerator implements ConformationProcessor {

	private PartialConformationEnergyFunction energyFunction;
	private ArrayList<Map<String, PriorityQueue<ScoredAssignment>>> lambdaHeaps; 
	private Subproblem sourceProblem;
	private SubproblemConfEnumerator leftSubproblem;
	private SubproblemConfEnumerator rightSubproblem;
	private RightConfManager rightConfs;
	private ChildConfManager childConfs;
	private static RCTuple emptyConf = new RCTuple();
	
	public SubproblemConfEnumerator (Subproblem subproblem, PartialConformationEnergyFunction eFunc) {
		subproblem.addConformationProcessor(this);
		addChildProcessors(subproblem, eFunc);
		energyFunction = eFunc;
		sourceProblem = subproblem;
		int subproblemLocalConfs = subproblem.getTotalLocalConformations().intValue();
		lambdaHeaps = new ArrayList<>();
		while(lambdaHeaps.size() <= subproblemLocalConfs)
			lambdaHeaps.add(null);
	}

	private void addChildProcessors (Subproblem subproblem, PartialConformationEnergyFunction eFunc) {
		if(subproblem.leftSubproblem != null)
		{
			leftSubproblem = new SubproblemConfEnumerator(subproblem.leftSubproblem, eFunc);
		}
		if(subproblem.rightSubproblem != null)
		{
			rightSubproblem  = new SubproblemConfEnumerator(subproblem.rightSubproblem, eFunc);
		}
	}

	@Override
	public void processConformation (RCTuple conformation) {
		if(energyFunction == null)
		{
			System.out.println("Huh?");
		}
		double selfEnergy = energyFunction.computePartialEnergy(conformation);
		double leftEnergy = 0;
		if(leftSubproblem != null)
			leftEnergy = leftSubproblem.nextBestEnergy(conformation);
		double rightEnergy = 0;
		if(rightSubproblem != null)
			rightEnergy = rightSubproblem.nextBestEnergy(conformation);
		getHeap(conformation).add(new ScoredAssignment(conformation, selfEnergy, leftEnergy, rightEnergy));
	}
	


	public RCTuple nextBestConformation(RCTuple queryAssignment)
	{
		PriorityQueue<ScoredAssignment> lambdaHeap = getHeap(queryAssignment);
		ScoredAssignment previousHeapRoot = lambdaHeap.poll();
		RCTuple curBestAssignment = previousHeapRoot.assignment;
		RCTuple outputAssignment = previousHeapRoot.assignment.copy();
		outputAssignment.combineRC(queryAssignment);
		queryAssignment.combineRC(curBestAssignment);
		RCTuple nextBestChildConf = childConfs.getNextChildAssignment(queryAssignment);
		outputAssignment.combineRC(nextBestChildConf);
		if(childConfs.hasMoreConformations(queryAssignment))
		{
			previousHeapRoot.updateLeftScore(childConfs.nextBestEnergy(queryAssignment));
			lambdaHeap.add(previousHeapRoot);
		}
			
		
		return outputAssignment;
	}

	private PriorityQueue<ScoredAssignment> getHeap (RCTuple queryAssignment) {
		int lambdaHeapIndex = sourceProblem.mapSubproblemConfToIndex(queryAssignment);
		String RCTupleKey = queryAssignment.toString();
		if(lambdaHeaps.size() <= lambdaHeapIndex || lambdaHeaps.get(lambdaHeapIndex) == null)
		{
			Map<String, PriorityQueue<ScoredAssignment>> heapMap = new HashMap<>();
			lambdaHeaps.set(lambdaHeapIndex, heapMap);
		}
		if(!lambdaHeaps.get(lambdaHeapIndex).containsKey(RCTupleKey))
			initializeHeap(queryAssignment);
		return lambdaHeaps.get(lambdaHeapIndex).get(RCTupleKey);
	}

	private void initializeHeap (RCTuple queryAssignment) {
		int lambdaHeapIndex = sourceProblem.mapSubproblemConfToIndex(queryAssignment);
		String RCTupleKey = queryAssignment.toString(); 
		Map<String, PriorityQueue<ScoredAssignment>> heapMap = lambdaHeaps.get(lambdaHeapIndex);
		PriorityQueue<ScoredAssignment> newHeap = new LazyHeap(heapMap.get(RCTupleKey));
		heapMap.put(RCTupleKey, newHeap);
	}
	
	public double nextBestEnergy(RCTuple MAssignment)
	{
		return getHeap(MAssignment).peek().score;
	}

	public boolean hasMoreConformations(RCTuple MAssignment)
	{
		return lambdaHeaps.get(sourceProblem.mapSubproblemConfToIndex(MAssignment)).size() > 0;
	}
	
	
	/*** This class will encapsulate ALL of the crazy logic that 
	 * goes into maintaining the heaps required for sparse enumeration.
	 * @author Jon
	 *
	 */
	
	private class ChildConfManager
	{
		private Map<RCTuple, PriorityQueue<ScoredAssignment>> leftHeapMap;
		private RightConfManager rightConfs;
		private SubproblemConfEnumerator leftSubproblem;
		
		public RCTuple getNextChildAssignment(RCTuple queryAssignment)
		{
			RCTuple nextBestChildConf = null;
			if(rightConfs != null)
			{
				if(!leftHeapMap.containsKey(queryAssignment))
					leftHeapMap.put(queryAssignment, new LazyHeap(leftSubproblem.getHeap(queryAssignment)));
				PriorityQueue<ScoredAssignment> leftTrackingHeap = leftHeapMap.get(queryAssignment);
				ScoredAssignment nextBestChildAssignment = leftTrackingHeap.poll();
				nextBestChildConf = nextBestChildAssignment.assignment.copy();

				RCTuple nextBestRightConf = rightConfs.getNextBestRightConf(nextBestChildConf);
				updateChildAssignment(nextBestChildAssignment);
				nextBestChildConf.combineRC(nextBestRightConf);
			}
			else
			{
				nextBestChildConf = leftSubproblem.nextBestConformation(queryAssignment);
			}
			return nextBestChildConf;

		}

		public double nextBestEnergy (RCTuple queryAssignment) {
			if(rightConfs != null)
			{
				if(!leftHeapMap.containsKey(queryAssignment))
					leftHeapMap.put(queryAssignment, new LazyHeap(leftSubproblem.getHeap(queryAssignment)));
				return leftHeapMap.get(queryAssignment).peek().score;
			}
			else
			{
				return leftSubproblem.nextBestEnergy(queryAssignment);
			}
		}

		public boolean hasMoreConformations(RCTuple queryAssignment)
		{
			if(rightConfs!=null)
			{
				return leftHeapMap.get(queryAssignment).size() > 0;
			}
			else
			{
				return leftSubproblem.hasMoreConformations(queryAssignment);
			}
		}

		private void updateChildAssignment (ScoredAssignment nextBestChildAssignment) {
			RCTuple nextBestChildQueryConf = nextBestChildAssignment.assignment;
			if(rightConfs.hasMoreConformations(nextBestChildQueryConf))
			{
				nextBestChildAssignment.updateRightScore(rightConfs.peekNextBestEnergy(nextBestChildQueryConf));
				leftHeapMap.get(nextBestChildQueryConf).add(nextBestChildAssignment);
			}
			
		}
		
		
	}
	
	private class RightConfManager 
	{
		private SubproblemConfEnumerator rightSubproblem;
		private Map<RCTuple, RightConf> rightConfMap;
		private Map<RCTuple, List<ScoredAssignment>> rightConfLists;

		
		private List<ScoredAssignment> getRightConfList (RCTuple queryConf)
		{
			if(!rightConfLists.containsKey(queryConf))
				rightConfLists.put(queryConf, new LinkedList<>());
			return rightConfLists.get(queryConf);
		}
		

		private RightConf getRightConf(RCTuple queryConf)
		{
			if(!rightConfMap.containsKey(queryConf))
				rightConfMap.put(queryConf, new RightConf(
						queryConf, getRightConfList(queryConf)));
			
			return rightConfMap.get(queryConf);
		}
		
		public double peekNextBestEnergy(RCTuple queryConf)
		{
			return getRightConf(queryConf).peekConfEnergy();
		}
		
		public RCTuple getNextBestRightConf(RCTuple queryConf)
		{
			RightConf rightConf = rightConfMap.get(queryConf);
			RCTuple output = rightConf.getCurConf();
			rightConf.updateConf(rightSubproblem);
			return output;
		}
		
		public boolean hasMoreConformations(RCTuple leftConf)
		{
			return getRightConf(leftConf).hasMoreConformations();
		}
	}
	
	private class RightConf
	{
		private RCTuple queryAssignment;
		private int confListIndex = 0;
		private List<ScoredAssignment> confList;
		
		public RightConf(RCTuple conf, List<ScoredAssignment> rightConfs){
			queryAssignment = conf;
			confList = rightConfs;
		}

		public void updateConf (SubproblemConfEnumerator rightSubproblem) {
			confListIndex++;
			if(!hasMoreConformations() && rightSubproblem.hasMoreConformations(queryAssignment))
			{
				double nextRightConfE = rightSubproblem.nextBestEnergy(queryAssignment);
				RCTuple nextRightConf = rightSubproblem.nextBestConformation(queryAssignment);
				confList.add(new ScoredAssignment(nextRightConf, nextRightConfE, 0, 0));
			}
		}

		public double peekConfEnergy () {
			return confList.get(confListIndex).score;
		}

		public boolean hasMoreConformations () {
			return confListIndex + 1 < confList.size();
		}

		public RCTuple getCurConf () {
			return confList.get(confListIndex).assignment;
		}
	}
	
	private class LazyHeap extends PriorityQueue<ScoredAssignment>
	{
		private PriorityQueue<ScoredAssignment> templateQueue;
		private boolean dirty;
		private ScoredAssignment cleanAssignment;
		
		public LazyHeap(PriorityQueue<ScoredAssignment> sourceQueue)
		{
			super();
			templateQueue = sourceQueue;
		}
		
		public ScoredAssignment poll()
		{
			cleanHeap();
			ScoredAssignment nextBestAssignment = super.poll();
			if(nextBestAssignment == cleanAssignment)
				dirty = true;
			return nextBestAssignment;
		}
		
		private void cleanHeap()
		{
			if(size() < 1 || dirty)
			{
				cleanAssignment = templateQueue.poll();
				add(cleanAssignment);
			}
		}
		
		public ScoredAssignment peek()
		{
			cleanHeap();
			return super.peek();
		}
		
	}
	
	private class ScoredAssignment implements Comparable<ScoredAssignment>
	{
		double score = 0;
		private double selfScore = 0;
		private double leftScore = 0;
		private double rightScore = 0;
		RCTuple assignment;
		public ScoredAssignment (RCTuple conformation, double selfEnergy,
				double leftBestEnergy, double rightBestEnergy) {
			assignment = conformation;
			selfScore = selfEnergy;
			leftScore = leftBestEnergy;
			rightScore = rightBestEnergy;
			score = leftScore+rightScore+selfScore;
		}
		
		public void updateScore(double leftBestEnergy, double rightBestEnergy)
		{
			leftScore = leftBestEnergy;
			rightScore = rightBestEnergy;
			score = leftScore+rightScore+selfScore;
		}
		
		public void updateLeftScore(double leftBestEnergy)
		{
			leftScore = leftBestEnergy;
			score = leftScore+rightScore+selfScore;
		}
		
		public void updateRightScore(double rightBestEnergy)
		{
			rightScore = rightBestEnergy;
			score = leftScore+rightScore+selfScore;
		}
		@Override
		public int compareTo (ScoredAssignment arg0) {
			return Double.compare(score, arg0.score);
		}
		
		public ScoredAssignment copy()
		{
			return new ScoredAssignment(assignment, selfScore, leftScore, rightScore);
		}
	}

	public RCTuple nextBestConformation () {
		return nextBestConformation(emptyConf);
	}

	@Override
	public boolean recurse () {
		return false;
	}
}




/* Old code follows.
public void bTrackBestConfRemoveEarlyNew(RotTypeMap bestPosAARot[], int[] bestState)
{
    boolean reinsert = false;
    PriorityQueue<Conf> outHeap = getHeap(bestPosAARot, bestState, ""+this.hashCode());
    
    
    if(printHeap)
        outputInitialDebugData(bestPosAARot, outHeap);
    
    if(outHeap.size() > 1 && lambda.size() < 1)
        System.out.println("Impossibiruuuu");
    debugPrint("Begin. Polling heap...");
    Conf nextState = outHeap.poll();
    // Add lambda to the solution 
    nextState.fillRotTypeMap(bestPosAARot);
    RotTypeMap[] bestPosAARotOld = Arrays.copyOf(bestPosAARot, bestPosAARot.length);
    
    double curBest = nextState.energy;
    debugPrint("Next state is "+nextState+", energy "+nextState.energy);

    if(rightChild != null)
    {
    	debugPrint("right child is not null, performing left and right operations...");
        TreeEdge leftEdge = leftChild.getCofEdge();
        int[] leftMLambda = nextState.conformation; 
        int[] leftM = getMstateForEdgeCurState(leftMLambda, leftEdge);
        TreeEdge rightEdge = rightChild.getCofEdge();
        
        debugPrint("Getting right solutoins for "+RTMToString(bestPosAARot));
        List<RightConf> rightConfs = getRightSolutions(bestPosAARot);
        if(rightConfs.size() < 1)
        {
        	debugPrint("Generating new list, list is empty...");
            int[] rightMLambda = nextState.conformation; 
            int[] rightM = getMstateForEdgeCurState(rightMLambda, rightEdge);
            getNewRightconf(bestPosAARotOld, rightEdge, rightConfs, rightM);
        }
        debugPrint("Getting secondary heap from "+leftEdge.L+leftEdge.lambda+"...");
        LazyHeap<Conf> secondaryHeap = getSecondaryHeap(bestPosAARotOld, leftM);
        if(leftChild.getCofEdge().lambda.size() < 1 && secondaryHeap.size() > 1)
            System.out.println("IMPOSSIBIRU!?!?!!");
        if((secondaryHeap.dirty || secondaryHeap.size() < 1) && leftEdge.moreConformations(bestPosAARotOld, leftM)) 
        {
        	debugPrint("Populating heap with new conformation...");
            // Acquire new left conformation for the heap 
            RotTypeMap[] bestPosAARotLeft = Arrays.copyOf(bestPosAARotOld, bestPosAARot.length);
            
            double newLeftEnergy = leftEdge.peekEnergy(bestPosAARotLeft, leftM);
            leftEdge.bTrackBestConfRemoveEarlyNew(bestPosAARotLeft, leftM);
            Conf newLeftConf = new Conf(bestPosAARotLeft, newLeftEnergy);
            newLeftConf.updateLeftEnergy(rightConfs.get(0).energy);
            secondaryHeap.cleanNode = newLeftConf;
            secondaryHeap.add(newLeftConf);
            secondaryHeap.dirty = false;
            if(leftChild.getCofEdge().lambda.size() < 1 && secondaryHeap.size() > 1)
                System.out.println("IMPOSSIBIRU!?!?!!");
        }
        
        // Maintain cleanliness 
		PriorityQueue<Conf> copy = new PriorityQueue<Conf>();

		for(Conf c : secondaryHeap)
		{
		    copy.add(c);
		}

		while(!copy.isEmpty())
		{
		    Conf c = copy.poll();
		    debugPrint(c+"$"+c.energy+", left "+c.leftEnergy+", self "+c.selfEnergy+", right "+c.rightEnergy);
		}
        Conf leftConf = secondaryHeap.poll();
        debugPrint("Polled left conformation from secondary heap: "+leftConf+", "+leftConf.energy);

		PriorityQueue<Conf> copy2 = new PriorityQueue<Conf>();

		for(Conf c : secondaryHeap)
		{
		    copy2.add(c);
		}

		while(!copy2.isEmpty())
		{
		    Conf c = copy2.poll();
		    debugPrint(c+"$"+c.energy+", left "+c.leftEnergy+", self "+c.selfEnergy+", right "+c.rightEnergy);
		}
        leftConf.fillConf(bestPosAARot);
        
        if(leftConf == secondaryHeap.cleanNode)
            secondaryHeap.dirty = true;
        int index = getRightOffset(leftConf.toString());
        debugPrint("Right offset index is "+index);

        int[] rightMLambda = nextState.conformation; 
        int[] rightM = getMstateForEdgeCurState(rightMLambda, rightEdge);
        while(rightConfs.size() <= index + 1 && rightEdge.moreConformations(bestPosAARotOld, rightM))
        {
            debugPrint("Getting extra conformation for next run...");
            getNewRightconf(bestPosAARotOld, rightEdge, rightConfs, rightM);
        }
                    
        RightConf rightConf = rightConfs.get(index);    
        rightConf.fillRotTypeMap(bestPosAARot);        
        debugPrint("Returned right conformation is "+rightConf+", energy "+rightConf.energy);
        
        if(index + 1 < rightConfs.size() || rightEdge.moreConformations(bestPosAARotOld, rightM))
        {
            RightConf nextRightConf = rightConfs.get(index + 1);
            leftConf.updateLeftEnergy(nextRightConf.energy);
            rightSolutionOffset.put(leftConf.toString(), index + 1);
            debugPrint("Reinsert "+leftConf+" with new energy "+leftConf.energy
            		+" into secondary heap, right conf is "+nextRightConf+", energy "+nextRightConf.energy);
            secondaryHeap.add(leftConf);
            reinsert = true;
        }
        
        if((secondaryHeap.dirty || secondaryHeap.size() < 1) && leftEdge.moreConformations(bestPosAARotOld, leftM)) 
        {
            // Acquire new left conformation for the heap 
            RotTypeMap[] bestPosAARotLeft = Arrays.copyOf(bestPosAARotOld, bestPosAARot.length);
            
            double newLeftEnergy = leftEdge.peekEnergy(bestPosAARotLeft, leftM);
            leftEdge.bTrackBestConfRemoveEarlyNew(bestPosAARotLeft, leftM);
            Conf newLeftConf = new Conf(bestPosAARotLeft, newLeftEnergy);
            newLeftConf.updateLeftEnergy(rightConfs.get(0).energy);
            secondaryHeap.cleanNode = newLeftConf;
            secondaryHeap.add(newLeftConf);
            secondaryHeap.dirty = false;
            if(leftChild.getCofEdge().lambda.size() < 1 && secondaryHeap.size() > 1)
                System.out.println("IMPOSSIBIRU!?!?!!");
        }

        if(secondaryHeap.size() > 0)
        {
            nextState.updateLeftEnergy(secondaryHeap.peek().energy);
            reinsert = true;
        }
        else
            reinsert = false;
        
    }

    else if(leftChild != null)
    {
    	debugPrint("Only one child. Recursing...");
        TreeEdge leftEdge = leftChild.getCofEdge();
        int[] leftMLambda = nextState.conformation; 
        int[] leftM = getMstateForEdgeCurState(leftMLambda, leftEdge);
        if(leftM == null)
            leftM = leftMLambda;
        leftEdge.bTrackBestConfRemoveEarlyNew(bestPosAARot, leftM);        
        nextState.updateLeftEnergy(leftEdge.peekEnergy(bestPosAARot, leftM));
        debugPrint(nextState+" has new energy "+nextState.energy);
        reinsert = leftEdge.moreConformations(bestPosAARot, leftM);
    }
    if(reinsert)
        outHeap.add(nextState);
    
    checkHeap(outHeap);
    if(outHeap.size() > 1)
    {
        double curNewBest = outHeap.peek().energy;
        if(curNewBest < curBest)
        {
            System.err.println("Out of order. Terminating...");
            //System.exit(-1);
        }
    }
    if(printHeap)
        printEndDebugInfo(bestPosAARot, outHeap);

}
*/