package edu.duke.cs.osprey.energy.forcefield;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import edu.duke.cs.osprey.dof.DegreeOfFreedom;
import edu.duke.cs.osprey.energy.EnergyFunction;
import edu.duke.cs.osprey.energy.ResidueInteractions;
import edu.duke.cs.osprey.energy.forcefield.EEF1.SolvPairParams;
import edu.duke.cs.osprey.energy.forcefield.ForcefieldParams.SolvationForcefield;
import edu.duke.cs.osprey.energy.forcefield.ForcefieldParams.VdwParams;
import edu.duke.cs.osprey.structure.Atom;
import edu.duke.cs.osprey.structure.AtomConnectivity;
import edu.duke.cs.osprey.structure.AtomConnectivity.AtomPairs;
import edu.duke.cs.osprey.structure.AtomNeighbors;
import edu.duke.cs.osprey.structure.Molecule;
import edu.duke.cs.osprey.structure.Residue;

public class ResidueForcefieldEnergy implements EnergyFunction.DecomposableByDof {
	
	private static final long serialVersionUID = -4768384219061898745L;
	
	public static class ResPair {
		
		public final Residue res1;
		public final Residue res2;
		
		public double weight = 0;
		public double offset = 0;
		public int numAtomPairs = 0;
		
		// layout per atom pair: flags (bit is14Bonded, bit isHeavyPair, 6 bits space, 3 bytes space, short atom1Offset, short atom2Offset)
		// yeah, there's some extra space here, but keeping 8-byte alignments is fast on 64 bit machines
		public long[] flags = null;
		
		// layout per atom pair: charge, Aij, Bij, radius1, lambda1, alpha1, radius2, lambda2, alpha2
		public double[] precomputed = null;
		
		public ResPair(Collection<Residue> residues, ResidueInteractions.Pair pair) {
			
			this.res1 = findRes(residues, pair.resNum1);
			this.res2 = findRes(residues, pair.resNum2);
			
			this.weight = pair.weight;
			this.offset = pair.offset;
		}
	}
	
	private static Residue findRes(Collection<Residue> residues, String resNum) {
		for (Residue res : residues) {
			if (res.getPDBResNumber().equals(resNum)) {
				return res;
			}
		}
		throw new NoSuchElementException("no residue " + resNum + " found in " + residues);
	}
	
	public final ForcefieldParams params;
	public final ResidueInteractions inters;
	public final AtomConnectivity connectivity;
	
	private ResPair[] resPairs;
	
	private boolean isBroken;
	
	private double coulombFactor;
	private double scaledCoulombFactor;
	
	public ResidueForcefieldEnergy(ForcefieldParams params, ResidueInteractions inters, Molecule mol, AtomConnectivity connectivity) {
		this(params, inters, mol.residues, connectivity);
	}
	
	public ResidueForcefieldEnergy(ForcefieldParams params, ResidueInteractions inters, Collection<Residue> residues, AtomConnectivity connectivity) {
		
		this.params = params;
		this.inters = inters;
		this.connectivity = connectivity;
		
		// map the residue numbers to residues
		resPairs = new ResPair[inters.size()];
		int index = 0;
		for (ResidueInteractions.Pair pair : inters) {
			resPairs[index++] = new ResPair(residues, pair);
		}
		
		// is this a broken conformation?
		isBroken = false;
		for (ResPair pair : resPairs) {
			if (pair.res1.confProblems.size() + pair.res2.confProblems.size() > 0) {
				isBroken = true;
				
				// we're done here, no need to analyze broken conformations
				return;
			}
		}
		
		// pre-compute some constants needed by getEnergy()
		coulombFactor = ForcefieldParams.coulombConstant/params.dielectric;
		scaledCoulombFactor = coulombFactor*params.forcefld.coulombScaling;
		
		VdwParams vdwparams = new VdwParams();
		SolvPairParams solvparams = new SolvPairParams();
		
		// build the atom pairs
		for (ResPair pair : resPairs) {
			
			AtomPairs atomPairs = connectivity.getAtomPairs(pair.res1, pair.res2);
			
			// count the number of atom pairs and allocate space 
			pair.numAtomPairs = atomPairs.getNumPairs(AtomNeighbors.Type.BONDED14) + atomPairs.getNumPairs(AtomNeighbors.Type.NONBONDED);
			pair.flags = new long[pair.numAtomPairs];
			pair.precomputed = new double[pair.numAtomPairs*9];
			
			int flagsIndex = 0;
			int precomputedIndex = 0;
			
			for (AtomNeighbors.Type type : Arrays.asList(AtomNeighbors.Type.BONDED14, AtomNeighbors.Type.NONBONDED)) {
				for (int[] atomPair : atomPairs.getPairs(type)) {
			
					Atom atom1 = pair.res1.atoms.get(atomPair[0]);
					Atom atom2 = pair.res2.atoms.get(atomPair[1]);
					
					// pack the flags
					boolean isHeavyPair = !atom1.isHydrogen() && !atom2.isHydrogen();
					boolean is14Bonded = type == AtomNeighbors.Type.BONDED14;
					int atomOffset1 = atomPair[0]*3;
					int atomOffset2 = atomPair[1]*3;
					long flags = is14Bonded ? 1 : 0;
					flags <<= 1;
					flags |= isHeavyPair ? 1 : 0;
					flags <<= 46;
					flags |= (atomOffset1) & 0xffffL;
					flags <<= 16;
					flags |= (atomOffset2) & 0xffffL;
					pair.flags[flagsIndex++] = flags;
					
					// calc electrostatics params
					pair.precomputed[precomputedIndex++] = atom1.charge*atom2.charge;
					
					// calc vdw params
					params.getVdwParams(atom1, atom2, type, vdwparams);
					pair.precomputed[precomputedIndex++] = vdwparams.Aij;
					pair.precomputed[precomputedIndex++] = vdwparams.Bij;
					
					// compute solvation params if needed
					if (isHeavyPair) {
						switch (params.solvationForcefield) {
							
							case EEF1:
								params.eef1parms.getSolvationPairParams(atom1, atom2, params.solvScale, solvparams);
								pair.precomputed[precomputedIndex++] = solvparams.radius1;
								pair.precomputed[precomputedIndex++] = solvparams.lambda1;
								pair.precomputed[precomputedIndex++] = solvparams.alpha1;
								pair.precomputed[precomputedIndex++] = solvparams.radius2;
								pair.precomputed[precomputedIndex++] = solvparams.lambda2;
								pair.precomputed[precomputedIndex++] = solvparams.alpha2;
							break;
							
							default:
								precomputedIndex += 6;
						}
							
					} else {
						
						precomputedIndex += 6;
					}
				}
			}
			
			// update the pair offset with internal solvation energy if needed
			if (pair.res1 == pair.res2) {
				switch (params.solvationForcefield) {
					
					case EEF1:
						pair.offset += params.eef1parms.getInternalEnergy(pair.res1)*params.solvScale*pair.weight;
					break;
					
					default:
						// do nothing
				}
			}
		}
	}
	
	@Override
	public double getEnergy() {
		return getEnergy(resPairs);
	}
	
	private double getEnergy(ResPair[] resPairs) {
		
		// check broken-ness first. easy peasy
		if (isBroken) {
			return Double.POSITIVE_INFINITY;
		}
		
		// copy stuff to the stack/registers, to improve CPU cache performance
		boolean useHEs = params.hElect;
		boolean useHvdW = params.hVDW;
		double coulombFactor = this.coulombFactor;
		double scaledCoulombFactor = this.scaledCoulombFactor;
		boolean distDepDielect = params.distDepDielect;
		boolean useEEF1 = params.solvationForcefield == SolvationForcefield.EEF1;
		
		double energy = 0;
		
		for (int i=0; i<resPairs.length; i++) {
			ResPair pair = resPairs[i];
			
			// copy pair values/references to the stack/registers
			// so we don't have to touch the pair object while inside the atom pair loop
			double[] coords1 = pair.res1.coords;
			double[] coords2 = pair.res2.coords;
			int numAtomPairs = pair.numAtomPairs;
			long[] flags = pair.flags;
			double[] precomputed = pair.precomputed;
			
			double resPairEnergy = 0;
			
			// for each atom pair...
			int j9 = 0;
			for (int j=0; j<numAtomPairs; j++) {
				
				// read the flags
				// NOTE: this is efficient, but destructive to the val
				long atomPairFlags = flags[j];
				int atomOffset2 = (int)(atomPairFlags & 0xffff);
				atomPairFlags >>= 16;
				int atomOffset1 = (int)(atomPairFlags & 0xffff);
				atomPairFlags >>= 46;
				boolean isHeavyPair = (atomPairFlags & 0x1) == 0x1;
				atomPairFlags >>= 1;
				boolean is14Bonded = (atomPairFlags & 0x1) == 0x1;
				
				// get the radius
				double r2;
				double r;
				{
					// read atom coords
					double x1 = coords1[atomOffset1];
					double y1 = coords1[atomOffset1 + 1];
					double z1 = coords1[atomOffset1 + 2];
					double x2 = coords2[atomOffset2];
					double y2 = coords2[atomOffset2 + 1];
					double z2 = coords2[atomOffset2 + 2];
					
					double d;
					
					d = x1 - x2;
					r2 = d*d;
					d = y1 - y2;
					r2 += d*d;
					d = z1 - z2;
					r2 += d*d;
					r = Math.sqrt(r2);
				}
				
				// read the bit flags
				
				// electrostatics
				if (isHeavyPair || useHEs) {
					double charge = precomputed[j9++];
					if (is14Bonded) {
						if (distDepDielect) {
							resPairEnergy += scaledCoulombFactor*charge/r2;
						} else {
							resPairEnergy += scaledCoulombFactor*charge/r;
						}
					} else {
						if (distDepDielect) {
							resPairEnergy += coulombFactor*charge/r2;
						} else {
							resPairEnergy += coulombFactor*charge/r;
						}
					}
				} else {
					j9++;
				}
				
				// van der Waals
				if (isHeavyPair || useHvdW) {
					
					double Aij = precomputed[j9++];
					double Bij = precomputed[j9++];
					
					// compute vdw
					double r6 = r2*r2*r2;
					double r12 = r6*r6;
					resPairEnergy += Aij/r12 - Bij/r6;
					
				} else {
					j9 += 2;
				}
				
				// solvation
				if (useEEF1 && isHeavyPair && r2 < ForcefieldParams.solvCutoff2) {
							
					double radius1 = precomputed[j9++];
					double lambda1 = precomputed[j9++];
					double alpha1 = precomputed[j9++];
					double radius2 = precomputed[j9++];
					double lambda2 = precomputed[j9++];
					double alpha2 = precomputed[j9++];
					
					// compute solvation energy
					double Xij = (r - radius1)/lambda1;
					double Xji = (r - radius2)/lambda2;
					resPairEnergy -= (alpha1*Math.exp(-Xij*Xij) + alpha2*Math.exp(-Xji*Xji))/r2;
					
				} else {
					j9 += 6;
				}
			}
			
			// apply weights and offsets
			energy += resPairEnergy*pair.weight;
			energy += pair.offset;
		}
		
		return energy;
	}
	
	private ResPair[] makeResPairsSubset(Residue res) {
	
		// pass 1: count
		int num = 0;
		for (ResPair resPair : resPairs) {
			if (resPair.res1 == res || resPair.res2 == res) {
				num++;
			}
		}
		
		// pass 2: collect
		ResPair[] pairs = new ResPair[num];
		num = 0;
		for (ResPair resPair : resPairs) {
			if (resPair.res1 == res || resPair.res2 == res) {
				pairs[num++] = resPair;
			}
		}
		return pairs;
	}
	
	@Override
	public List<EnergyFunction> decomposeByDof(Molecule mol, List<DegreeOfFreedom> dofs) {
		
		class Subset implements EnergyFunction {
			
			private static final long serialVersionUID = 4664215035458391734L;
			
			private ResPair[] resPairs;
			
			@Override
			public double getEnergy() {
				return ResidueForcefieldEnergy.this.getEnergy(resPairs);
			}
		}
		
		Map<Residue,Subset> cache = new HashMap<>();
		
		List<EnergyFunction> efuncs = new ArrayList<>();
		for (DegreeOfFreedom dof : dofs) {
			Residue res = dof.getResidue();
			
			if (res == null) {
				
				// no res, just use the whole efunc
				efuncs.add(this);
				
			} else {
				
				// make a subset energy function
				Subset subset = cache.get(res);
				if (subset == null) {
					subset = new Subset();
					subset.resPairs = makeResPairsSubset(res);
					cache.put(res, subset);
				}
				efuncs.add(subset);
			}
		}
		
		return efuncs;
	}
}
