/*
 ** This file is part of OSPREY 3.0
 **
 ** OSPREY Protein Redesign Software Version 3.0
 ** Copyright (C) 2001-2018 Bruce Donald Lab, Duke University
 **
 ** OSPREY is free software: you can redistribute it and/or modify
 ** it under the terms of the GNU General Public License version 2
 ** as published by the Free Software Foundation.
 **
 ** You should have received a copy of the GNU General Public License
 ** along with OSPREY.  If not, see <http://www.gnu.org/licenses/>.
 **
 ** OSPREY relies on grants for its development, and since visibility
 ** in the scientific literature is essential for our success, we
 ** ask that users of OSPREY cite our papers. See the CITING_OSPREY
 ** document in this distribution for more information.
 **
 ** Contact Info:
 **    Bruce Donald
 **    Duke University
 **    Department of Computer Science
 **    Levine Science Research Center (LSRC)
 **    Durham
 **    NC 27708-0129
 **    USA
 **    e-mail: www.cs.duke.edu/brd/
 **
 ** <signature of Bruce Donald>, Mar 1, 2018
 ** Bruce Donald, Professor of Computer Science
 */

package edu.duke.cs.osprey.tupexp;

import edu.duke.cs.osprey.control.ParamSet;
import java.io.Serializable;

/**
 *
 * Settings for LUTE
 *
 * @author mhall44
 */
@SuppressWarnings("serial")
public class LUTESettings implements Serializable {

    boolean useLUTE=false;
    public double goalResid=0.01;
    boolean useRelWt=false;
    boolean useThreshWt=false;

    public LUTESettings(){
        //by default, no LUTE
        useLUTE = false;
    }

    public LUTESettings(ParamSet params){
        //initialize from input parameter set
        useLUTE = params.getBool("USETUPEXP");
        goalResid = params.getDouble("LUTEGOALRESID");
        useRelWt = params.getBool("LUTERELWT");
        useRelWt = params.getBool("LUTETHRESHWT");
    }


    public static LUTESettings defaultLUTE(){
        LUTESettings ans = new LUTESettings();
        ans.useLUTE = true;
        return ans;
    }

    public boolean shouldWeUseLUTE(){
        return useLUTE;
    }
}
