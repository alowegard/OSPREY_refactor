package edu.duke.cs.osprey.confspace;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import com.google.common.collect.Lists;
import edu.duke.cs.osprey.astar.conf.ConfAStarTree;
import edu.duke.cs.osprey.ematrix.EnergyMatrix;
import edu.duke.cs.osprey.ematrix.SimplerEnergyMatrixCalculator;
import edu.duke.cs.osprey.energy.ConfEnergyCalculator;
import edu.duke.cs.osprey.energy.EnergyCalculator;
import edu.duke.cs.osprey.energy.forcefield.ForcefieldParams;
import edu.duke.cs.osprey.parallelism.Parallelism;
import edu.duke.cs.osprey.structure.PDBIO;
import edu.duke.cs.osprey.tools.TimeTools;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.Iterator;
import java.util.function.Consumer;

public class TestConfDB {

	private static SimpleConfSpace confSpace;

	private static File file = new File("conf.db");

	private static SimpleConfSpace.Position lys5;
	private static SimpleConfSpace.Position tyr7;
	private static SimpleConfSpace.Position phe9;

	@BeforeClass
	public static void beforeClass() {

		Strand strand = new Strand.Builder(PDBIO.readResource("/1CC8.ss.pdb")).build();
		strand.flexibility.get("A5").setLibraryRotamers(Strand.WildType, "ALA").addWildTypeRotamers();
		strand.flexibility.get("A7").setLibraryRotamers(Strand.WildType, "ALA").addWildTypeRotamers();
		strand.flexibility.get("A9").setLibraryRotamers(Strand.WildType, "ALA").addWildTypeRotamers();

		confSpace = new SimpleConfSpace.Builder()
			.addStrand(strand)
			.build();

		lys5 = confSpace.positionsByResNum.get("A5");
		tyr7 = confSpace.positionsByResNum.get("A7");
		phe9 = confSpace.positionsByResNum.get("A9");

		assertThat(lys5.resFlex.wildType, is("LYS"));
		assertThat(tyr7.resFlex.wildType, is("TYR"));
		assertThat(phe9.resFlex.wildType, is("PHE"));
	}

	private ConfDB openDB() {
		return new ConfDB(confSpace, file);
	}

	private void cleanDB() {
		if (file.exists()) {
			file.delete();
		}
		assertThat(file.exists(), is(false));
	}

	private void withDB(Consumer<ConfDB> block) {
		cleanDB();
		ConfDB db = openDB();
		try {
			block.accept(db);
		} finally {
			try {
				db.close();
			} catch (Throwable t2) {}
			cleanDB();
		}
	}

	private void withDBTwice(Consumer<ConfDB> block1, Consumer<ConfDB> block2) {
		cleanDB();
		ConfDB db = openDB();
		try {
			block1.accept(db);
			db.close();
		} catch (Throwable t) {
			try {
				db.close();
			} catch (Throwable t2) {}
			cleanDB();
			return;
		}
		db = openDB();
		try {
			block2.accept(db);
		} finally {
			try {
				db.close();
			} catch (Throwable t2) {}
			cleanDB();
		}
	}

	@Test
	public void create() {

		cleanDB();

		new ConfDB(confSpace, file).close();

		assertThat(file.exists(), is(true));
		file.delete();
	}

	@Test
	public void createSequenceDB() {
		withDB((db) -> {
			Sequence sequence = confSpace.makeWildTypeSequence();
			ConfDB.SequenceDB sdb = db.getSequence(sequence);
			assertThat(sdb.sequence, sameInstance(sequence));
		});
	}

	@Test
	public void writeReadConfLowerBound() {
		withDB((db) -> {
			Sequence sequence = confSpace.makeWildTypeSequence();
			ConfDB.SequenceDB sdb = db.getSequence(sequence);

			int[] assignments = { 0, 0, 0 };
			double energy = 4.2;
			long timestampNs = TimeTools.getTimestampNs();

			assertThat(assignments.length, is(confSpace.positions.size()));

			sdb.setLowerBound(assignments, energy, timestampNs);

			ConfDB.Conf conf = sdb.get(assignments);

			assertThat(conf.assignments, is(assignments));
			assertThat(conf.lower.energy, is(energy));
			assertThat(conf.lower.timestampNs, is(timestampNs));
			assertThat(conf.upper, is(nullValue()));
		});
	}

	@Test
	public void writeReadConfUpperBound() {
		withDB((db) -> {
			Sequence sequence = confSpace.makeWildTypeSequence();
			ConfDB.SequenceDB sdb = db.getSequence(sequence);

			int[] assignments = { 7, 4, 5 };
			double energy = 7.2;
			long timestampNs = TimeTools.getTimestampNs();

			assertThat(assignments.length, is(confSpace.positions.size()));

			sdb.setUpperBound(assignments, energy, timestampNs);

			ConfDB.Conf conf = sdb.get(assignments);

			assertThat(conf.assignments, is(assignments));
			assertThat(conf.lower, is(nullValue()));
			assertThat(conf.upper.energy, is(energy));
			assertThat(conf.upper.timestampNs, is(timestampNs));
		});
	}

	@Test
	public void writeReadConfBounds() {
		withDB((db) -> {
			Sequence sequence = confSpace.makeWildTypeSequence();
			ConfDB.SequenceDB sdb = db.getSequence(sequence);

			int[] assignments = { 5, 5, 5 };
			double lowerEnergy = 7.2;
			double upperEnergy = 9.9;
			long timestampNs = TimeTools.getTimestampNs();

			assertThat(assignments.length, is(confSpace.positions.size()));

			sdb.setBounds(assignments, lowerEnergy, upperEnergy, timestampNs);

			ConfDB.Conf conf = sdb.get(assignments);

			assertThat(conf.assignments, is(assignments));
			assertThat(conf.lower.energy, is(lowerEnergy));
			assertThat(conf.lower.timestampNs, is(timestampNs));
			assertThat(conf.upper.energy, is(upperEnergy));
			assertThat(conf.upper.timestampNs, is(timestampNs));
		});
	}

	@Test
	public void writeReadConfLowerThenUpper() {
		withDB((db) -> {
			Sequence sequence = confSpace.makeWildTypeSequence();
			ConfDB.SequenceDB sdb = db.getSequence(sequence);

			int[] assignments = { 5, 5, 5 };
			double lowerEnergy = 7.2;
			long lowerTimestampNs = TimeTools.getTimestampNs();

			assertThat(assignments.length, is(confSpace.positions.size()));

			sdb.setLowerBound(assignments, lowerEnergy, lowerTimestampNs);

			ConfDB.Conf conf = sdb.get(assignments);

			assertThat(conf.assignments, is(assignments));
			assertThat(conf.lower.energy, is(lowerEnergy));
			assertThat(conf.lower.timestampNs, is(lowerTimestampNs));
			assertThat(conf.upper, is(nullValue()));

			double upperEnergy = 9.9;
			long upperTimestampNs = TimeTools.getTimestampNs();

			sdb.setUpperBound(assignments, upperEnergy, upperTimestampNs);

			conf = sdb.get(assignments);

			assertThat(conf.assignments, is(assignments));
			assertThat(conf.lower.energy, is(lowerEnergy));
			assertThat(conf.lower.timestampNs, is(lowerTimestampNs));
			assertThat(conf.upper.energy, is(upperEnergy));
			assertThat(conf.upper.timestampNs, is(upperTimestampNs));
		});
	}

	@Test
	public void writeReadAFewConfs() {
		withDB((db) -> {
			Sequence sequence = confSpace.makeWildTypeSequence();
			ConfDB.SequenceDB sdb = db.getSequence(sequence);

			sdb.setUpperBound(new int[] { 1, 2, 3 }, 7.9, 42L);
			sdb.setUpperBound(new int[] { 7, 9, 8 }, 3.2, 54L);
			sdb.setUpperBound(new int[] { 4, 0, 5 }, 2.3, 69L);

			Iterator<ConfDB.Conf> confs = sdb.iterator();
			ConfDB.Conf conf;

			// confs should come out in lexicographic order of the assignments
			conf = confs.next();
			assertThat(conf.assignments, is(new int[] { 1, 2, 3 }));
			assertThat(conf.upper.energy, is(7.9));
			assertThat(conf.upper.timestampNs, is(42L));

			conf = confs.next();
			assertThat(conf.assignments, is(new int[] { 4, 0, 5 }));
			assertThat(conf.upper.energy, is(2.3));
			assertThat(conf.upper.timestampNs, is(69L));

			conf = confs.next();
			assertThat(conf.assignments, is(new int[] { 7, 9, 8 }));
			assertThat(conf.upper.energy, is(3.2));
			assertThat(conf.upper.timestampNs, is(54L));

			assertThat(confs.hasNext(), is(false));
		});
	}

	@Test
	public void writeCloseReadAFewConfs() {
		Sequence sequence = confSpace.makeWildTypeSequence();
		withDBTwice((db) -> {

			ConfDB.SequenceDB sdb = db.getSequence(sequence);
			sdb.setUpperBound(new int[] { 1, 2, 3 }, 7.9, 42L);
			sdb.setUpperBound(new int[] { 7, 9, 8 }, 3.2, 54L);
			sdb.setUpperBound(new int[] { 4, 0, 5 }, 2.3, 69L);

		}, (db) -> {

			Iterator<ConfDB.Conf> confs = db.getSequence(sequence).iterator();
			ConfDB.Conf conf;

			// confs should come out in lexicographic order of the assignments
			conf = confs.next();
			assertThat(conf.assignments, is(new int[] { 1, 2, 3 }));
			assertThat(conf.upper.energy, is(7.9));
			assertThat(conf.upper.timestampNs, is(42L));

			conf = confs.next();
			assertThat(conf.assignments, is(new int[] { 4, 0, 5 }));
			assertThat(conf.upper.energy, is(2.3));
			assertThat(conf.upper.timestampNs, is(69L));

			conf = confs.next();
			assertThat(conf.assignments, is(new int[] { 7, 9, 8 }));
			assertThat(conf.upper.energy, is(3.2));
			assertThat(conf.upper.timestampNs, is(54L));

			assertThat(confs.hasNext(), is(false));
		});
	}

	@Test
	public void writeReadCloseReadAFewSequences() {

		Sequence sequence1 = confSpace.makeUnassignedSequence()
			.set(lys5, "LYS")
			.set(tyr7, "TYR")
			.set(phe9, "PHE");

		Sequence sequence2 = confSpace.makeUnassignedSequence()
			.set(lys5, "ALA")
			.set(tyr7, "TYR")
			.set(phe9, "PHE");

		Sequence sequence3 = confSpace.makeUnassignedSequence()
			.set(lys5, "LYS")
			.set(tyr7, "ALA")
			.set(phe9, "PHE");

		Sequence sequence4 = confSpace.makeUnassignedSequence()
			.set(lys5, "LYS")
			.set(tyr7, "TYR")
			.set(phe9, "ALA");

		withDBTwice((db) -> {

			ConfDB.SequenceDB sdb1 = db.getSequence(sequence1);
			ConfDB.SequenceDB sdb2 = db.getSequence(sequence2);
			ConfDB.SequenceDB sdb3 = db.getSequence(sequence3);
			ConfDB.SequenceDB sdb4 = db.getSequence(sequence4);

			assertThat(sdb1.sequence, sameInstance(sequence1));
			assertThat(sdb2.sequence, sameInstance(sequence2));
			assertThat(sdb3.sequence, sameInstance(sequence3));
			assertThat(sdb4.sequence, sameInstance(sequence4));

			// sequences get hased in the db, so they can come out in any order
			assertThat(Lists.newArrayList(db.getSequences()), containsInAnyOrder(
				sequence1, sequence2, sequence3, sequence4
			));

		}, (db) -> {

			assertThat(Lists.newArrayList(db.getSequences()), containsInAnyOrder(
				sequence1, sequence2, sequence3, sequence4
			));
		});
	}

	@Test
	public void writeCloseReadSequenceInfo() {

		Sequence sequence = confSpace.makeWildTypeSequence();

		withDBTwice((db) -> {

			ConfDB.SequenceDB sdb = db.getSequence(sequence);
			assertThat(sdb.getLowerEnergyOfUnsampledConfs(), is(Double.NaN));

			sdb.setLowerEnergyOfUnsampledConfs(4.2);

			assertThat(sdb.getLowerEnergyOfUnsampledConfs(), is(4.2));

		}, (db) -> {

			ConfDB.SequenceDB sdb = db.getSequence(sequence);
			assertThat(sdb.getLowerEnergyOfUnsampledConfs(), is(4.2));
		});
	}

	@Test
	public void astarSearch() {
		withDBTwice((db) -> {

			new EnergyCalculator.Builder(confSpace, new ForcefieldParams())
				.setParallelism(Parallelism.makeCpu(4))
				.use((ecalc) -> {

					ConfEnergyCalculator confEcalc = new ConfEnergyCalculator.Builder(confSpace, ecalc).build();

					EnergyMatrix emat = new SimplerEnergyMatrixCalculator.Builder(confEcalc)
						.build()
						.calcEnergyMatrix();

					ConfAStarTree astar = new ConfAStarTree.Builder(emat, confSpace)
						.setTraditional()
						.setShowProgress(true)
						.build();


					long numConfs = astar.getNumConformations().longValueExact();
					assertThat(numConfs, is(1740L));

					// write all the conformations in the A* tree
					ConfSearch.ScoredConf conf;
					while ((conf = astar.nextConf()) != null) {
						ConfDB.SequenceDB sdb = db.getSequence(confSpace.makeSequenceFromConf(conf));
						sdb.setLowerBound(
							conf.getAssignments(),
							conf.getScore(),
							TimeTools.getTimestampNs()
						);
						sdb.updateLowerEnergyOfUnsampledConfs(conf.getScore());

					}
				});

		}, (db) -> {

			assertThat(db.getNumSequences(), is(8L)); // 2^3 sequences

			// count the confs
			long numConfs = 0;
			for (Sequence sequence : db.getSequences()) {
				for (ConfDB.Conf conf : db.getSequence(sequence)) {
					numConfs++;
				}
			}
			assertThat(numConfs, is(1740L));

			// check the lower bounds
			for (Sequence sequence : db.getSequences()) {
				ConfDB.SequenceDB sdb = db.getSequence(sequence);
				double lowerEnergy = Double.NEGATIVE_INFINITY;
				for (ConfDB.Conf conf : sdb) {
					lowerEnergy = Math.max(lowerEnergy, conf.lower.energy);
				}
				assertThat(lowerEnergy, is(sdb.getLowerEnergyOfUnsampledConfs()));
			}
		});
	}

	@Test
	public void energyIndices() {

		int[][] assignments = {
			{ 0, 0, 0 },
			{ 1, 2, 3 },
			{ 3, 2, 1 },
		};

		String tableId = "foo";
		withDBTwice((db) -> {

			ConfDB.ConfTable table = db.new ConfTable(tableId);

			table.setBounds(assignments[0], 7.0, 27.0, 5L);
			table.setBounds(assignments[1], 6.0, 25.0, 6L);
			table.setBounds(assignments[2], 5.0, 26.0, 7L);

			assertThat(table.size(), is(3L));
			assertThat(table.sizeScored(), is(3L));
			assertThat(table.sizeEnergied(), is(3L));

			assertThat(table.energiedConfs(ConfDB.SortOrder.Assignment), contains(
				new ConfSearch.EnergiedConf(assignments[0], 7.0, 27.0),
				new ConfSearch.EnergiedConf(assignments[1], 6.0, 25.0),
				new ConfSearch.EnergiedConf(assignments[2], 5.0, 26.0)
			));

			assertThat(table.energiedConfs(ConfDB.SortOrder.Score), contains(
				new ConfSearch.EnergiedConf(assignments[2], 5.0, 26.0),
				new ConfSearch.EnergiedConf(assignments[1], 6.0, 25.0),
				new ConfSearch.EnergiedConf(assignments[0], 7.0, 27.0)
			));

			assertThat(table.energiedConfs(ConfDB.SortOrder.Energy), contains(
				new ConfSearch.EnergiedConf(assignments[1], 6.0, 25.0),
				new ConfSearch.EnergiedConf(assignments[2], 5.0, 26.0),
				new ConfSearch.EnergiedConf(assignments[0], 7.0, 27.0)
			));

		}, (db) -> {

			ConfDB.ConfTable table = db.new ConfTable(tableId);

			assertThat(table.size(), is(3L));
			assertThat(table.sizeScored(), is(3L));
			assertThat(table.sizeEnergied(), is(3L));

			assertThat(table.energiedConfs(ConfDB.SortOrder.Assignment), contains(
				new ConfSearch.EnergiedConf(assignments[0], 7.0, 27.0),
				new ConfSearch.EnergiedConf(assignments[1], 6.0, 25.0),
				new ConfSearch.EnergiedConf(assignments[2], 5.0, 26.0)
			));

			assertThat(table.energiedConfs(ConfDB.SortOrder.Score), contains(
				new ConfSearch.EnergiedConf(assignments[2], 5.0, 26.0),
				new ConfSearch.EnergiedConf(assignments[1], 6.0, 25.0),
				new ConfSearch.EnergiedConf(assignments[0], 7.0, 27.0)
			));

			assertThat(table.energiedConfs(ConfDB.SortOrder.Energy), contains(
				new ConfSearch.EnergiedConf(assignments[1], 6.0, 25.0),
				new ConfSearch.EnergiedConf(assignments[2], 5.0, 26.0),
				new ConfSearch.EnergiedConf(assignments[0], 7.0, 27.0)
			));
		});
	}

	@Test
	public void energyIndicesChange() {

		int[][] assignments = {
			{ 0, 0, 0 },
			{ 1, 2, 3 },
			{ 3, 2, 1 },
		};

		withDB((db) -> {

			ConfDB.ConfTable table = db.new ConfTable("foo");

			table.setBounds(assignments[0], 7.0, 27.0, 5L);
			table.setBounds(assignments[1], 6.0, 25.0, 6L);
			table.setBounds(assignments[2], 5.0, 26.0, 7L);

			table.setBounds(assignments[1], 10.0, 50.0, 10L);

			assertThat(table.size(), is(3L));
			assertThat(table.sizeScored(), is(3L));
			assertThat(table.sizeEnergied(), is(3L));

			assertThat(table.energiedConfs(ConfDB.SortOrder.Assignment), contains(
				new ConfSearch.EnergiedConf(assignments[0], 7.0, 27.0),
				new ConfSearch.EnergiedConf(assignments[1], 10.0, 50.0),
				new ConfSearch.EnergiedConf(assignments[2], 5.0, 26.0)
			));

			assertThat(table.energiedConfs(ConfDB.SortOrder.Score), contains(
				new ConfSearch.EnergiedConf(assignments[2], 5.0, 26.0),
				new ConfSearch.EnergiedConf(assignments[0], 7.0, 27.0),
				new ConfSearch.EnergiedConf(assignments[1], 10.0, 50.0)
			));

			assertThat(table.energiedConfs(ConfDB.SortOrder.Energy), contains(
				new ConfSearch.EnergiedConf(assignments[2], 5.0, 26.0),
				new ConfSearch.EnergiedConf(assignments[0], 7.0, 27.0),
				new ConfSearch.EnergiedConf(assignments[1], 10.0, 50.0)
			));
		});
	}

	@Test
	public void energyIndexChangeLowerBound() {

		int[][] assignments = {
			{ 0, 0, 0 },
			{ 1, 2, 3 },
			{ 3, 2, 1 },
		};

		withDB((db) -> {

			ConfDB.ConfTable table = db.new ConfTable("foo");

			table.setLowerBound(assignments[0], 7.0, 5L);
			table.setLowerBound(assignments[1], 6.0, 6L);
			table.setLowerBound(assignments[2], 5.0, 7L);

			table.setLowerBound(assignments[1], 10.0, 10L);

			assertThat(table.sizeScored(), is(3L));

			assertThat(table.scoredConfs(ConfDB.SortOrder.Assignment), contains(
				new ConfSearch.ScoredConf(assignments[0], 7.0),
				new ConfSearch.ScoredConf(assignments[1], 10.0),
				new ConfSearch.ScoredConf(assignments[2], 5.0)
			));

			assertThat(table.scoredConfs(ConfDB.SortOrder.Score), contains(
				new ConfSearch.ScoredConf(assignments[2], 5.0),
				new ConfSearch.ScoredConf(assignments[0], 7.0),
				new ConfSearch.ScoredConf(assignments[1], 10.0)
			));
		});
	}

	@Test
	public void energyIndexChangeUpperBound() {

		int[][] assignments = {
			{ 0, 0, 0 },
			{ 1, 2, 3 },
			{ 3, 2, 1 },
		};

		withDB((db) -> {

			ConfDB.ConfTable table = db.new ConfTable("foo");

			table.setUpperBound(assignments[0], 27.0, 5L);
			table.setUpperBound(assignments[1], 25.0, 6L);
			table.setUpperBound(assignments[2], 26.0, 7L);

			table.setUpperBound(assignments[1], 50.0, 10L);

			assertThat(table.sizeEnergied(), is(3L));

			assertThat(table.energiedConfs(ConfDB.SortOrder.Assignment), contains(
				new ConfSearch.EnergiedConf(assignments[0], Double.NaN, 27.0),
				new ConfSearch.EnergiedConf(assignments[1], Double.NaN, 50.0),
				new ConfSearch.EnergiedConf(assignments[2], Double.NaN, 26.0)
			));

			assertThat(table.energiedConfs(ConfDB.SortOrder.Energy), contains(
				new ConfSearch.EnergiedConf(assignments[2], Double.NaN, 26.0),
				new ConfSearch.EnergiedConf(assignments[0], Double.NaN, 27.0),
				new ConfSearch.EnergiedConf(assignments[1], Double.NaN, 50.0)
			));
		});
	}

	@Test
	public void energyIndicesSameEnergies() {

		int[][] assignments = {
			{ 0, 0, 0 },
			{ 1, 2, 3 },
			{ 3, 2, 1 },
		};

		String tableId = "foo";
		withDBTwice((db) -> {

			ConfDB.ConfTable table = db.new ConfTable(tableId);

			table.setBounds(assignments[0], 7.0, 20.0, 5L);
			table.setBounds(assignments[1], 7.0, 20.0, 6L);
			table.setBounds(assignments[2], 7.0, 20.0, 7L);

			assertThat(table.size(), is(3L));
			assertThat(table.sizeScored(), is(3L));
			assertThat(table.sizeEnergied(), is(3L));

			assertThat(table.scoredConfs(ConfDB.SortOrder.Assignment), containsInAnyOrder(
				new ConfSearch.EnergiedConf(assignments[0], 7.0, 20.0),
				new ConfSearch.EnergiedConf(assignments[1], 7.0, 20.0),
				new ConfSearch.EnergiedConf(assignments[2], 7.0, 20.0)
			));

			assertThat(table.scoredConfs(ConfDB.SortOrder.Score), containsInAnyOrder(
				new ConfSearch.EnergiedConf(assignments[0], 7.0, 20.0),
				new ConfSearch.EnergiedConf(assignments[1], 7.0, 20.0),
				new ConfSearch.EnergiedConf(assignments[2], 7.0, 20.0)
			));

			assertThat(table.scoredConfs(ConfDB.SortOrder.Energy), containsInAnyOrder(
				new ConfSearch.EnergiedConf(assignments[0], 7.0, 20.0),
				new ConfSearch.EnergiedConf(assignments[1], 7.0, 20.0),
				new ConfSearch.EnergiedConf(assignments[2], 7.0, 20.0)
			));

		}, (db) -> {

			ConfDB.ConfTable table = db.new ConfTable(tableId);

			assertThat(table.size(), is(3L));
			assertThat(table.sizeScored(), is(3L));
			assertThat(table.sizeEnergied(), is(3L));

			assertThat(table.scoredConfs(ConfDB.SortOrder.Assignment), containsInAnyOrder(
				new ConfSearch.EnergiedConf(assignments[0], 7.0, 20.0),
				new ConfSearch.EnergiedConf(assignments[1], 7.0, 20.0),
				new ConfSearch.EnergiedConf(assignments[2], 7.0, 20.0)
			));

			assertThat(table.scoredConfs(ConfDB.SortOrder.Score), containsInAnyOrder(
				new ConfSearch.EnergiedConf(assignments[0], 7.0, 20.0),
				new ConfSearch.EnergiedConf(assignments[1], 7.0, 20.0),
				new ConfSearch.EnergiedConf(assignments[2], 7.0, 20.0)
			));

			assertThat(table.scoredConfs(ConfDB.SortOrder.Energy), containsInAnyOrder(
				new ConfSearch.EnergiedConf(assignments[0], 7.0, 20.0),
				new ConfSearch.EnergiedConf(assignments[1], 7.0, 20.0),
				new ConfSearch.EnergiedConf(assignments[2], 7.0, 20.0)
			));
		});
	}
}
