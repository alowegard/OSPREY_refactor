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
	private ArrayList<PriorityQueue<ScoredAssignment>> templateHeaps;
	private Subproblem sourceProblem;
	private SubproblemConfEnumerator leftSubproblem;
	private SubproblemConfEnumerator rightSubproblem;
	private ChildConfManager childConfs;
	private static RCTuple emptyConf = new RCTuple();
	
	public SubproblemConfEnumerator (Subproblem subproblem, PartialConformationEnergyFunction eFunc) {
		subproblem.addConformationProcessor(this);
		addChildProcessors(subproblem, eFunc);

		energyFunction = eFunc;
		sourceProblem = subproblem;
		int subproblemLocalConfs = subproblem.getTotalMConformations().intValue();
		lambdaHeaps = new ArrayList<>();
		templateHeaps = new ArrayList<>();
		while(lambdaHeaps.size() <= subproblemLocalConfs)
			lambdaHeaps.add(null);
		while(templateHeaps.size() <= subproblemLocalConfs)
			templateHeaps.add(null);
		if(leftSubproblem != null)
		{
			RightConfManager rightSide = null;
			if(rightSubproblem != null)
			{
				rightSide = new RightConfManager(rightSubproblem, sourceProblem.rightSubproblem);
			}
			childConfs = new ChildConfManager(leftSubproblem, rightSide);
		}
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
		double selfEnergy = energyFunction.computePartialEnergy(conformation);
		double leftEnergy = 0;
		if(leftSubproblem != null)
			leftEnergy = leftSubproblem.nextBestEnergy(conformation);
		double rightEnergy = 0;
		if(rightSubproblem != null)
			rightEnergy = rightSubproblem.nextBestEnergy(conformation);

		PriorityQueue<ScoredAssignment> templateHeap = getTemplateHeap(conformation);
		ScoredAssignment assignment = new ScoredAssignment(conformation, selfEnergy, leftEnergy, rightEnergy);
		System.out.println("Processing "+conformation+", adding new template conf "+assignment);
		templateHeap.add(assignment);
		
	}
	


	public RCTuple nextBestConformation(RCTuple queryAssignment)
	{
		PriorityQueue<ScoredAssignment> lambdaHeap = getHeap(queryAssignment);
		if(lambdaHeap.size() < 1)
		{
			System.out.println("Debug empty heap.");
		}
		ScoredAssignment previousHeapRoot = lambdaHeap.poll();
		RCTuple curBestAssignment = previousHeapRoot.assignment;
		RCTuple outputAssignment = previousHeapRoot.assignment;
		outputAssignment = outputAssignment.combineRC(queryAssignment);
		queryAssignment = queryAssignment.combineRC(curBestAssignment);
		if(lambdaHeap.size() < 1)
		{
			System.out.println("Debug empty heap.");
		}
		if(childConfs != null)
		{
			RCTuple nextBestChildConf = childConfs.getNextChildAssignment(queryAssignment);
			outputAssignment = outputAssignment.combineRC(nextBestChildConf);
			if(childConfs.hasMoreConformations(queryAssignment))
			{
				previousHeapRoot.updateLeftScore(childConfs.nextBestEnergy(queryAssignment));
				lambdaHeap.add(previousHeapRoot);
			}
		}
			
		
		return outputAssignment;
	}
	
	private PriorityQueue<ScoredAssignment> getHeap (RCTuple queryAssignment) {
		return getHeap(queryAssignment, false);
	}

	private PriorityQueue<ScoredAssignment> getHeap (RCTuple queryAssignment, boolean isTemplateHeap) {
		int lambdaHeapIndex = sourceProblem.mapSubproblemConfToIndex(queryAssignment);

		String RCTupleKey = queryAssignment.toString();
		//TODO: Add a check to see if the queryAssignment belongs to a template heap.
		if(lambdaHeaps.size() <= lambdaHeapIndex || lambdaHeaps.get(lambdaHeapIndex) == null)
		{
			Map<String, PriorityQueue<ScoredAssignment>> heapMap = new HashMap<>();
			lambdaHeaps.set(lambdaHeapIndex, heapMap);
		}
		if(!lambdaHeaps.get(lambdaHeapIndex).containsKey(RCTupleKey))
			initializeHeap(queryAssignment, RCTupleKey);
		if(isTemplateHeap)
			RCTupleKey = sourceProblem.extractSubproblemMAssignment(queryAssignment).toString();
		PriorityQueue<ScoredAssignment> output = lambdaHeaps.get(lambdaHeapIndex).get(RCTupleKey);
		if(output == null)
		{
			System.err.println("Heap not found.");
		}
		return output;
	}
	
	private PriorityQueue<ScoredAssignment> getTemplateHeap(RCTuple MULambdaAssignment)
	{

		int templateHeapIndex = sourceProblem.mapSubproblemConfToIndex(MULambdaAssignment);
		if(templateHeaps.get(templateHeapIndex) == null)
		{
			assert(sourceProblem.isMULambdaAssignment(MULambdaAssignment));
			templateHeaps.set(templateHeapIndex, new PriorityQueue<>());
		}
		return templateHeaps.get(templateHeapIndex);
	}
	

	private void initializeHeap (RCTuple queryAssignment, String RCTupleKey) {
		int lambdaHeapIndex = sourceProblem.mapSubproblemConfToIndex(queryAssignment);
		RCTuple MTuple = sourceProblem.extractSubproblemAssignment(queryAssignment);

		Map<String, PriorityQueue<ScoredAssignment>> heapMap = lambdaHeaps.get(lambdaHeapIndex);
		PriorityQueue<ScoredAssignment> newHeap = new LazyHeap(getTemplateHeap(MTuple));
		heapMap.put(RCTupleKey, newHeap);
	}
	
	public double nextBestEnergy()
	{
		return nextBestEnergy(emptyConf);
	}
	
	public double nextBestEnergy(RCTuple queryConf)
	{
		if(sourceProblem.printTreeDesign().equals("[2, ] - L Set:[2, ] - M Set:[1, 3, ]"))
		{
			System.out.println("Debug this node.");
		}
		PriorityQueue<ScoredAssignment> heap = getHeap(queryConf);
		if(heap.size() < 1)
		{
			System.out.println("No confs at "+sourceProblem);
			getHeap(queryConf);
		}
		ScoredAssignment bestConf = heap.peek();
		double bestScore = bestConf.score;
		return bestScore;
	}
	
	public boolean hasMoreConformations()
	{
		return hasMoreConformations(emptyConf);
	}
	

	public boolean hasMoreConformations(RCTuple queryConf)
	{
		return getHeap(queryConf).size() > 0;
	}
	

	public RCTuple peekNextBestConformation (RCTuple queryConf) {
		PriorityQueue<ScoredAssignment> heap = getHeap(queryConf);
		if(heap.size() < 1)
		{
			System.out.println("No confs...");
			getHeap(queryConf);
		}
		ScoredAssignment bestConf = heap.peek();
		return bestConf.assignment.copy();
	}

	@Override
	public boolean recurse () {
		return false;
	}
	
	
	/*** This class will encapsulate ALL of the crazy logic that 
	 * goes into maintaining the heaps required for sparse enumeration.
	 * @author Jon
	 *
	 */
	
	private class ChildConfManager
	{
		private Map<String, PriorityQueue<ScoredAssignment>> leftHeapMap = new HashMap<>();;
		private RightConfManager rightConfs;
		private SubproblemConfEnumerator leftSubproblem;
		
		public ChildConfManager(SubproblemConfEnumerator leftEnumerator, RightConfManager rightManager)
		{
			leftSubproblem = leftEnumerator;
			rightConfs = rightManager;
		}
		
		public RCTuple getNextChildAssignment(RCTuple queryAssignment)
		{
			RCTuple nextBestChildConf = null;
			if(rightConfs != null)
			{
				if(!leftHeapMap.containsKey(queryAssignment.toString()))
				{
					RCTuple leftMAssignment = sourceProblem.leftSubproblem.extractSubproblemMAssignment(queryAssignment);
					PriorityQueue<ScoredAssignment> newHeap = new LazyHeap(leftSubproblem.getHeap(leftMAssignment));
					if(newHeap.size() < 1)
					{
						System.err.println("Empty new heap. Should be full of template nodes.");
						leftSubproblem.getHeap(leftMAssignment);
					}
					leftHeapMap.put(queryAssignment.toString(), newHeap);
				}
				PriorityQueue<ScoredAssignment> leftTrackingHeap = leftHeapMap.get(queryAssignment.toString());
				if(leftTrackingHeap.size() < 1)
				{
					System.err.println("Tracking heap is empty??");
				}
				ScoredAssignment nextBestChildAssignment = leftTrackingHeap.poll();
				if(nextBestChildAssignment == null)
				{
					System.err.println("Null best child assignment...");
				}
				nextBestChildConf = nextBestChildAssignment.assignment.copy();

				RCTuple nextBestRightConf = rightConfs.getNextBestRightConf(nextBestChildConf);
				updateChildAssignment(nextBestChildAssignment);
				nextBestChildConf = nextBestChildConf.combineRC(nextBestRightConf);
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
				if(!leftHeapMap.containsKey(queryAssignment.toString()))
					leftHeapMap.put(queryAssignment.toString(), new LazyHeap(leftSubproblem.getHeap(queryAssignment)));
				return leftHeapMap.get(queryAssignment.toString()).peek().score;
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
				return leftHeapMap.get(queryAssignment.toString()).size() > 0;
			}
			else
			{
				return leftSubproblem.hasMoreConformations(queryAssignment);
			}
		}

		private void updateChildAssignment (ScoredAssignment nextBestChildAssignment) {
;			RCTuple nextBestChildQueryConf = nextBestChildAssignment.assignment;
			if(rightConfs.hasMoreConformations(nextBestChildQueryConf))
			{
				nextBestChildAssignment.updateRightScore(rightConfs.peekNextBestEnergy(nextBestChildQueryConf));
				leftHeapMap.get(nextBestChildQueryConf.toString()).add(nextBestChildAssignment);
			}
			
		}
	
		
		
	}
	
	private class RightConfManager 
	{
		private Subproblem rightSubproblem;
		private SubproblemConfEnumerator rightSubproblemEnum;
		private Map<RCTuple, RightConf> rightConfMap = new HashMap<>();
		private Map<String, List<ScoredAssignment>> rightConfLists = new HashMap<>();

		public RightConfManager(SubproblemConfEnumerator rightSideEnum, Subproblem rightProblem)
		{
			rightSubproblemEnum = rightSideEnum;
			rightSubproblem = rightProblem;
		}
		
		private List<ScoredAssignment> getRightConfList (RCTuple queryConf)
		{
			if(!rightConfLists.containsKey(queryConf.toString()))
			{
				RCTuple templateConf = rightSubproblem.extractSubproblemMAssignment(queryConf);
				if(!rightConfLists.containsKey(templateConf.toString()))
				{
					List<ScoredAssignment> newConfList = new LinkedList<>();
					double firstScore = rightSubproblemEnum.nextBestEnergy(queryConf);
					RCTuple firstAssignment = rightSubproblemEnum.peekNextBestConformation(queryConf);
					newConfList.add(new ScoredAssignment(firstAssignment, firstScore, 0, 0));

					rightConfLists.put(templateConf.toString(), newConfList);
				}
				rightConfLists.put(queryConf.toString(), rightConfLists.get(templateConf.toString()));
			}
			return rightConfLists.get(queryConf.toString());
		}
		

		private RightConf getRightConf(RCTuple queryConf)
		{
			if(!rightConfMap.containsKey(queryConf))
			{
				rightConfMap.put(queryConf, new RightConf(
						queryConf, getRightConfList(queryConf)));
			}
			
			return rightConfMap.get(queryConf);
		}
		
		public double peekNextBestEnergy(RCTuple queryConf)
		{
			return getRightConf(queryConf).peekConfEnergy();
		}
		
		public RCTuple getNextBestRightConf(RCTuple queryConf)
		{
			RightConf rightConf = getRightConf(queryConf);
			RCTuple output = rightConf.pollCurConf();
			rightConf.updateConf(rightSubproblemEnum);
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
		private SubproblemConfEnumerator rightSubproblem;
		
		public RightConf(RCTuple conf, List<ScoredAssignment> rightConfs){
			queryAssignment = conf;
			confList = rightConfs;
		}

		public void updateConf (SubproblemConfEnumerator rightSubproblem) {
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

		public RCTuple pollCurConf () {
			if(confList == null)
			{
				System.err.println("No confList. Why do we exist??");
			}
			if(confListIndex >= confList.size())
			{
				System.err.println("No confs...");
			}
			RCTuple output = confList.get(confListIndex).assignment;
			confListIndex++;
			return output;
		}
		
		public RCTuple peekCurConf () {
			return confList.get(confListIndex).assignment;
		}
	}
	
	private class LazyHeap extends PriorityQueue<ScoredAssignment>
	{
		private PriorityQueue<ScoredAssignment> templateQueue;
		private boolean dirty;
		private ScoredAssignment cleanAssignment = null;
		
		public LazyHeap(PriorityQueue<ScoredAssignment> sourceQueue)
		{
			super();
			if(sourceQueue != null)
			{
				templateQueue = new PriorityQueue<>();
				for(ScoredAssignment sourceAssignment : sourceQueue)
				{
					templateQueue.add(sourceAssignment.copy());
					add(sourceAssignment.copy());
				}
			}
			if(size() < 1)
			{
				System.err.println("For your debug example (1CC8), no heap should initialize to size 1.");
			}
			dirty = false;
			peek();
		}
		
		public ScoredAssignment poll()
		{
			cleanHeap();
			ScoredAssignment nextBestAssignment = super.poll();
			if(size() < 1)
			{
				System.out.println("We've emptied this heap. So soon?");
			}
			if(nextBestAssignment == cleanAssignment)
				dirty = true;
			return nextBestAssignment;
		}
		
		private void cleanHeap()
		{
			if(true)
				return;
			if(templateQueue ==  null)
				return;
			if((size() < 1 || dirty) && templateQueue.size() > 0)
			{
				cleanAssignment = templateQueue.poll();
				add(cleanAssignment);
				dirty = false;
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
		
		public String toString()
		{
			return "" + assignment + " - "+score;
		}
	}

	public RCTuple nextBestConformation () {
		return nextBestConformation(emptyConf);
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