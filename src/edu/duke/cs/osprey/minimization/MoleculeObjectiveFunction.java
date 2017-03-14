package edu.duke.cs.osprey.minimization;

import java.util.ArrayList;
import java.util.List;

import cern.colt.matrix.DoubleMatrix1D;
import edu.duke.cs.osprey.confspace.ParametricMolecule;
import edu.duke.cs.osprey.energy.EnergyFunction;

public class MoleculeObjectiveFunction implements ObjectiveFunction {
	
	private static final long serialVersionUID = -5301575611582359731L;
	
	public final ParametricMolecule pmol;
	public final DofBounds bounds;
	public final EnergyFunction efunc;
	public final List<EnergyFunction> efuncsByDof;
	
	public MoleculeObjectiveFunction(ParametricMolecule pmol, DofBounds bounds, EnergyFunction efunc) {
		this.pmol = pmol;
		this.bounds = bounds;
		this.efunc = efunc;
		
		if (efunc instanceof EnergyFunction.DecomposableByDof) {
			efuncsByDof = ((EnergyFunction.DecomposableByDof)efunc).decomposeByDof(pmol.mol, pmol.dofs);
		} else {
			efuncsByDof = null;
		}
	}
	
	/**
	 * transition adapter, only here temporarily
	 */
	@Deprecated
	public MoleculeObjectiveFunction(MoleculeModifierAndScorer mof) {
		pmol = new ParametricMolecule(mof.getMolec(), mof.getDOFs());
		bounds = new DofBounds(mof.getConstraints());
		efunc = mof.getEfunc();
		efuncsByDof = new ArrayList<>();
		for (int d=0; d<pmol.dofs.size(); d++) {
			efuncsByDof.add(mof.getEfunc(d));
		}
	}

	public EnergyFunction getEfunc(int d) {
		if (efuncsByDof != null) {
			return efuncsByDof.get(d);
		}
		return efunc;
	}

	@Override
	public int getNumDOFs() {
		return pmol.dofs.size();
	}

	@Override
	public DoubleMatrix1D[] getConstraints() {
		return bounds.getBounds();
	}

	@Override
	public void setDOF(int d, double val) {
		pmol.dofs.get(d).apply(val);
	}

	@Override
	public double getValForDOF(int d, double val) {
		setDOF(d, val);
		return getEfunc(d).getEnergy();
	}
	
	@Override
	public void setDOFs(DoubleMatrix1D x) {
		for (int d=0; d<x.size(); d++) {
			pmol.dofs.get(d).apply(x.get(d));
		}
	}

	@Override
	public double getValue(DoubleMatrix1D x) {
		setDOFs(x);
		return efunc.getEnergy();
	}

	@Override
	public double getInitStepSize(int d) {
		return MoleculeModifierAndScorer.getInitStepSize(pmol.dofs.get(d));
	}

	@Override
	public boolean isDOFAngle(int d) {
		return MoleculeModifierAndScorer.isDOFAngle(pmol.dofs.get(d));
	}

	@Override
	public ArrayList<Integer> getInitFixableDOFs() {
		throw new UnsupportedOperationException("implement me!");
	}
}