package edu.duke.cs.osprey.multistatekstar;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

/**
 * 
 * @author Adegoke Ojewole (ao68@duke.edu)
 * Domain size determined by the number of rotamers in the bound state
 */
@SuppressWarnings("serial")
public class ResidueOrderStaticMinDomain extends ResidueOrderDynamicScore {

	private enum ScoreType {
		NUMSPLITS,
		PRODUCT,
		SUM;
	}

	private HashMap<Integer, ArrayList<ArrayList<Integer>>> residueValues;//residue values for bound state
	//in each unassigned pos

	private ScoreType scoreType;

	public ResidueOrderStaticMinDomain(MSSearchProblem[][] objFcnSearch, String method) {
		super(objFcnSearch, "discrepancy");
		this.residueValues = computeResidueValues(objFcnSearch);

		switch(method.toLowerCase()) {
		case "product":
			scoreType = ScoreType.PRODUCT;
			break;
		case "sum":
			scoreType = ScoreType.SUM;
			break;
		case "numsplits":
			scoreType = ScoreType.NUMSPLITS;
			break;
		default:
			throw new UnsupportedOperationException("ERROR: method must be in the set {numsplits, product, sum}");
		}
	}

	private HashMap<Integer, ArrayList<ArrayList<Integer>>> computeResidueValues(MSSearchProblem[][] objFcnSearch) {
		int complex = objFcnSearch[0].length-1;
		ArrayList<Integer> unassignedPos = objFcnSearch[0][complex].getPosNums(false);

		HashMap<Integer, ArrayList<ArrayList<Integer>>> ans = new HashMap<>();

		for(int pos : unassignedPos) {
			ArrayList<ArrayList<Integer>> numRCsAtPos = new ArrayList<>();

			for(int state=0;state<objFcnSearch.length;++state) {
				ArrayList<Integer> stateNumRCsAtPos = new ArrayList<>();

				MSSearchProblem complexSearch = objFcnSearch[state][complex];
				for(String AAType : complexSearch.settings.AATypeOptions.get(pos)) {
					int numRCs = complexSearch.rcsAtPosForAA(complexSearch.pruneMat, pos, AAType, false).size();
					//numRCs += complexSearch.rcsAtPosForAA(complexSearch.pruneMat, pos, AAType, true).size();
					stateNumRCsAtPos.add(numRCs);
				}

				stateNumRCsAtPos.trimToSize();
				numRCsAtPos.add(stateNumRCsAtPos);
			}

			numRCsAtPos.trimToSize();
			ans.put(pos, numRCsAtPos);
		}

		return ans;
	}

	//score is product of domain sizes of bound states
	protected BigDecimal getBoundStateDomainProduct(ResidueAssignment residueAssignment, 
			ArrayList<ArrayList<ArrayList<AAAssignment>>> aaAssignments) {
		//we only want bound state aa assignments
		int complex = residueAssignment.length()-1;
		int numSplits = aaAssignments.get(0).size();

		long prodRCs = 1;

		for(int split=0;split<numSplits;++split) {
			ArrayList<AAAssignment> assignments = aaAssignments.get(complex).get(split);
			for(AAAssignment aaa : assignments) {

				for(ArrayList<Integer> numRCsAtPos : residueValues.get(aaa.residuePos))
					prodRCs *= numRCsAtPos.get(aaa.AATypePos);

			}
		}

		return BigDecimal.valueOf(prodRCs);
	}

	//score is sum of domain sizes of bound states
	protected BigDecimal getBoundStateDomainSum(ResidueAssignment residueAssignment, 
			ArrayList<ArrayList<ArrayList<AAAssignment>>> aaAssignments) {
		//we only want bound state aa assignments
		int complex = residueAssignment.length()-1;
		int numSplits = aaAssignments.get(0).size();

		long sumRCs = 0;

		for(int split=0;split<numSplits;++split) {
			ArrayList<AAAssignment> assignments = aaAssignments.get(complex).get(split);
			for(AAAssignment aaa : assignments) {

				for(ArrayList<Integer> numRCsAtPos : residueValues.get(aaa.residuePos))
					sumRCs += numRCsAtPos.get(aaa.AATypePos);

			}
		}

		return BigDecimal.valueOf(sumRCs);
	}
	
	protected BigDecimal getNumberOfSplits(ResidueAssignment residueAssignment, 
			ArrayList<ArrayList<ArrayList<AAAssignment>>> aaAssignments) {
		int numSplits = aaAssignments.get(0).size();
		return BigDecimal.valueOf(numSplits);
	}

	protected ArrayList<ResidueAssignmentScore> scoreUnassignedPos(LMB objFcn, 
			MSSearchProblem[][] objFcnSearch, 
			ArrayList<Integer> unassignedPos,
			int numMaxMut) {

		int numSubStates = objFcnSearch[0].length;
		MSSearchProblem complex = objFcnSearch[0][numSubStates-1];

		ArrayList<ResidueAssignment> residueAssignments = new ArrayList<>();

		//get assignments
		for(int splitPos : unassignedPos) {

			if(complex.getNumAssignedPos()==0) {//root, split all possible from unbound
				residueAssignments = getUnboundResidueAssignments(objFcnSearch[0]);
				break;
			}

			else {//can directly score bound state
				residueAssignments.add(getBoundResidueAssignments(objFcnSearch[0], splitPos));
			}
		}

		//assignments.trimToSize();

		//now score each assignment
		clearResidue2AAAssignments();//first clear previous entries

		ArrayList<ResidueAssignmentScore> residueAssignmentScores = new ArrayList<>();
		for(ResidueAssignment residueAssignment : residueAssignments) {
			BigDecimal score = getResidueAssignmentScore(residueAssignment, objFcnSearch, numMaxMut);
			residueAssignmentScores.add(new ResidueAssignmentScore(residueAssignment, score));
		}

		//assignmentScores.trimToSize();
		return residueAssignmentScores;
	}

	public BigDecimal getResidueAssignmentScore(ResidueAssignment residueAssignment,
			MSSearchProblem[][] objFcnSearch, 
			int numMaxMut) {
		//each residue assignment corresponds to one or more allowed AA assignments.
		//score is based on allowed AA assignments only
		ArrayList<ArrayList<ArrayList<AAAssignment>>> aaAssignments = getAllowedAAAsignments(objFcnSearch, residueAssignment, numMaxMut);

		//store here, so we can retrieve best from here later without recomputation
		storeResidue2AAAssignments(residueAssignment, aaAssignments);

		switch(scoreType) {
		case PRODUCT:
			return getBoundStateDomainProduct(residueAssignment, aaAssignments);
		case SUM:
			return getBoundStateDomainSum(residueAssignment, aaAssignments);
		case NUMSPLITS:
			return getNumberOfSplits(residueAssignment, aaAssignments);
		default:
			throw new UnsupportedOperationException("ERROR: scoretype must be in the set {numsplits, product, sum}");
		}
	}

	protected ResidueAssignment getBestResidueAssignment(ArrayList<ResidueAssignmentScore> order) {
		//sort assignments by increasing score
		Collections.sort(order, new Comparator<ResidueAssignmentScore>() {
			@Override
			public int compare(ResidueAssignmentScore a1, ResidueAssignmentScore a2) {
				if(a1.score.compareTo(a2.score)==0) {
					ArrayList<Integer> a1Complex = a1.assignment.get(a1.assignment.length()-1);
					ArrayList<Integer> a2Complex = a2.assignment.get(a2.assignment.length()-1);
					
					int a1Sum = 0, a2Sum = 0;
					for(int pos : a1Complex) a1Sum += pos;
					for(int pos : a2Complex) a2Sum += pos;
					
					return a1Sum <= a2Sum ? -1 : 1;
				}
				
				return a1.score.compareTo(a2.score)<0 ? -1 : 1;
			}
		});

		ResidueAssignment best = order.get(0).assignment;
		return best;
	}

	public ArrayList<ResidueAssignmentScore> scoreAllResidueAssignments(
			LMB objFcn, 
			MSSearchProblem[][] objFcnSearch,
			KStarScore[] objFcnScores, 
			int numMaxMut) {

		//get number of unassigned positions
		int complex = objFcnSearch[0].length-1;
		ArrayList<Integer> unassignedPos = objFcnSearch[0][complex].getPosNums(false);

		if(unassignedPos.size()==0)
			throw new RuntimeException("ERROR: there are no unassigned positions");

		//score unassigned residues by objfcn
		ArrayList<ResidueAssignmentScore> scores = scoreUnassignedPos(objFcn, objFcnSearch, unassignedPos, numMaxMut);

		return scores;
	}

	@Override
	public ArrayList<ArrayList<ArrayList<AAAssignment>>> getNextAssignments(
			LMB objFcn,
			MSSearchProblem[][] objFcnSearch,
			KStarScore[] objFcnScores,
			int numMaxMut
			) {
		ArrayList<ResidueAssignmentScore> scores = scoreAllResidueAssignments(objFcn, objFcnSearch, objFcnScores, numMaxMut);		

		//order unassigned residues by score and return best residue
		ResidueAssignment best = getBestResidueAssignment(scores);

		//get allowed AA assignments corresponding to best residue assignment
		return getAAAssignments(best);
	}

}
