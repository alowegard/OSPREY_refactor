/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.duke.cs.osprey.voxq;

import cern.colt.matrix.DoubleFactory1D;
import cern.colt.matrix.DoubleMatrix1D;
import edu.duke.cs.osprey.minimization.CCDMinimizer;
import edu.duke.cs.osprey.minimization.MoleculeModifierAndScorer;
import edu.duke.cs.osprey.structure.PDBIO;

/**
 *
 * This will draw samples from a voxel
 * 
 * @author mhall44
 */
public class IntraVoxelSampler {
    
    MoleculeModifierAndScorer mms;
    int numDOFs;
        
    private static final int numBurnInSamples = 5;
    private static final int thinningFactor = 5;
    
    public static final double RT = 1.9891/1000.0 * 298.15;//RT in kcal/mol..see PoissonBoltzmannEnergy
    
    //acceptance ratio tracking
    double numDrawn=0;
    double numAccepted=0;
    
    
    public IntraVoxelSampler(MoleculeModifierAndScorer mms){
        this.mms = mms;
        numDOFs = mms.getNumDOFs();
        
        //let's start at the minimum
        CCDMinimizer ccdMin = new CCDMinimizer(mms, false);
        ccdMin.minimize();
        
        //OK now can start sampling.  Throw away burn in though.  
        burnIn();
    }
    
    private void burnIn(){
        for(int samp=0; samp<numBurnInSamples; samp++)
            nextSample();
    }
    
    public DoubleMatrix1D nextSample(){
        for(int step=0; step<thinningFactor; step++){
            for(int dof=0; dof<numDOFs; dof++){
                //we can run each dof until acceptance, to avoid autocorrelation
                //since assuming we have a sample ~ correct distribution,
                //sample after a Metropolis step for any dof is also ~ correct distribution
                boolean accepted;
                do {
                    accepted = doMetropolisStep(dof);
                }
                while(!accepted);
            }
        }
        
        //DEBUG!!!!
        //System.out.println("Acceptance ratio: "+(1.0*numAccepted/numDrawn));
        
        return getCurDOFVals();
    }
    
    DoubleMatrix1D getCurDOFVals(){
        DoubleMatrix1D ans = DoubleFactory1D.dense.make(numDOFs);
        for(int dof=0; dof<numDOFs; dof++)
            ans.set(dof, mms.getCurValueOfDOF(dof));
        return ans;
    }
    
    private boolean doMetropolisStep(int dof){
        //Take a Metropolis step for the given DOF
        //return whether accepted or not.  Update curDOFVals if we did
        double origDOFVal = mms.getCurValueOfDOF(dof);
        QuadraticQFunction bluggles = new QuadraticQFunction(mms, dof, origDOFVal);
        
        double newDOFVal = bluggles.drawDOFValue();
        
        double Ediff = mms.getValForDOF(dof,newDOFVal) - mms.getValForDOF(dof,origDOFVal);
        double Prat = Math.exp(-Ediff/RT);
        
        QuadraticQFunction q2 = new QuadraticQFunction(mms, dof, newDOFVal);
        double Qrat = bluggles.evalQ(newDOFVal)/q2.evalQ(origDOFVal);
        
        double metropolisRatio = Prat/Qrat;
        boolean accepted = true;
        if(metropolisRatio<1)
            accepted = (metropolisRatio>Math.random());        
        
        if(accepted){
            mms.setDOF(dof, newDOFVal);
            numAccepted++;
        }
        else
            mms.setDOF(dof, origDOFVal);
        
        
        //DEBUG!!!!
        //System.out.println(mms.getValue(getCurDOFVals()));
        
        numDrawn++;
        
        return accepted;
    }
    
    
    //After init test: Want to start doing G's.  Ensure closed cycles are closed as expected,
    //and not too slow.  
    //We will begin (and maybe continue...) w/ differences between similar confs.  
    
    
    //DEBUG!!!!
    void printMolec(String PDBName){
        System.out.println("DOF VALS BEING WRITTEN TO "+ PDBName +": "+getCurDOFVals());
        PDBIO.writeFile(mms.getMolec(), PDBName);
    }
    
    
}
