package edu.duke.cs.osprey.multistatekstar;

import java.math.BigDecimal;
import java.util.ArrayList;

import edu.duke.cs.osprey.kstar.pfunc.PartitionFunction;

/**
 * 
 * @author Adegoke Ojewole (ao68@duke.edu)
 * 
 */
public interface KStarScore {

	public static final BigDecimal MAX_VALUE = new BigDecimal("2e2048");
	public static final BigDecimal MIN_VALUE = new BigDecimal("-2e2048");
	
	public enum KStarScoreType {
	    Minimized,//i.e. minimization
	    PairWiseMinimized,//pw min numerator and denominator
	    MinimizedLowerBound,//discrete numerator, pw min denominator
	    MinimizedUpperBound,//pw min numerator, discrete denominator
	    Discrete,//discrete
	    DiscreteLowerBound,//discrete numerator and denominator
	    DiscreteUpperBound;//discrete numerator and denominator
	}
	
	public enum PartitionFunctionType {
		Minimized,//i.e. minimization
		Discrete,//no min; either discrete or pw min
		UpperBound,//1+epsilon on pw min or GMEC-based
		LowerBound;//GMEC-based
	}
	
	public MSKStarSettings getSettings();
	public BigDecimal getScore();
	public BigDecimal getLowerBoundScore();
	public BigDecimal getUpperBoundScore();
	public BigDecimal getDenominator();
	public BigDecimal getNumerator();
	
	public PartitionFunction getPartitionFunction(int state);
	
	public String toString();
	public void compute(long maxNumConfs);
	public void computeUnboundStates(long maxNumConfs);
	public void computeBoundState(long maxNumConfs);
	public boolean constrSatisfied();
	public boolean checkConstraints(int state);
	public boolean checkConstraints(int state, Boolean negCoeff);
	public boolean isFullyAssigned();
	public boolean isFinal();
	public boolean isComputed();
	public boolean isFullyProcessed();
	public ArrayList<MSSearchProblem> getUpperBoundSearch();
	
}
