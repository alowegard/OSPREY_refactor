/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.duke.cs.osprey.confspace;

import java.io.Serializable;
import java.util.ArrayList;

import edu.duke.cs.osprey.dof.DegreeOfFreedom;
import edu.duke.cs.osprey.restypes.ResidueTemplate;

/**
 *
 * @author mhall44
 * @author am439
 */
public class RC implements Serializable {
    //A residue conformation.  Meant to be part of a PositionConfSpace
    
    public String AAType;//amino-acid type
    public ResidueTemplate template; // RC-specific template, null means use template library
    public int rotNum;//rotamer number (same library, for the given AAType, or refers to this.template when set)
    
    //bounds on degrees of freedom
    //some of these are defined by the AAType and rotNum
    public ArrayList<DegreeOfFreedom> DOFs;
    public ArrayList<Double> DOFmin;//minimum values of the DOFs for conformations in the RC
    public ArrayList<Double> DOFmax;
    //note: for AA type we do not use DOFmin or DOFmax (can leave at 0 or whatever): use AAType instead
    
    
    public int RCIndex;//index within the RCs for this residue in the PositionConfSpace

    public RC(String AAType, ResidueTemplate template, int rotNum, ArrayList<DegreeOfFreedom> DOFs, ArrayList<Double> DOFmin, ArrayList<Double> DOFmax, int RCIndex) {
        this.AAType = AAType;
        this.template = template;
        this.rotNum = rotNum;
        this.DOFs = DOFs;
        this.DOFmin = DOFmin;
        this.DOFmax = DOFmax;
        this.RCIndex = RCIndex;
    }
    
    public RC(RC other) {
    	this.AAType = other.AAType;
    	this.template = other.template;
    	this.rotNum = other.rotNum;
    	this.DOFs = other.DOFs;
    	this.DOFmin = new ArrayList<>(other.DOFmin);
    	this.DOFmax = new ArrayList<>(other.DOFmax);
    	this.RCIndex = other.RCIndex;
    }
    
    
    public boolean isParametricallyIncompatibleWith(RC rc2){
        //Two RCs are parametrically incompatible if and only if there is a DOF that they share
        //for which they have different intervals
        //Thus, a conformation has a well-defined voxel if and only if it contains no parametrically
        //incompatible pairs of RCs
        
        final double tol = 1e-8;
        
        for(int dof=0; dof<DOFs.size(); dof++){
            for(int dof2=0; dof2<rc2.DOFs.size(); dof2++){
                
                if(DOFs.get(dof)==rc2.DOFs.get(dof2)){//same DOF
                    
                    if( Math.abs( DOFmin.get(dof) - rc2.DOFmin.get(dof2) ) > tol )
                        return true;
                    if( Math.abs( DOFmax.get(dof) - rc2.DOFmax.get(dof2) ) > tol )
                        return true;
                }
            }
        }
        
        //no incompatibility found!
        return false;
    }
    
    
}
