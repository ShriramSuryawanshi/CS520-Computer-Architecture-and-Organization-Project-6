/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package implementation;

import baseclasses.FunctionalUnitBase;
import baseclasses.InstructionBase;
import baseclasses.Latch;
import baseclasses.ModuleBase;
import baseclasses.PipelineStageBase;
import baseclasses.PropertiesContainer;
import implementation.GlobalData;
import implementation.GlobalData;
import java.util.Map;
import tools.MultiStageDelayUnit;
import tools.MyALU;
import utilitytypes.EnumOpcode;
import utilitytypes.ICpuCore;
import utilitytypes.IFunctionalUnit;
import utilitytypes.IGlobals;
import utilitytypes.IModule;
import utilitytypes.IPipeReg;
import utilitytypes.IPipeStage;
import utilitytypes.IProperties;
import voidtypes.VoidProperties;

/**
 *
 * @author millerti
 */
public class IntDiv extends PipelineStageBase {

    public IntDiv(ICpuCore core) {
        super(core, "IntDiv");
    }

    @Override
    public void compute(Latch input, Latch output) {
        if (input.isNull()) {
            return;
        }

        IGlobals globals = (GlobalData) getCore().getGlobals();

        doPostedForwarding(input);
        InstructionBase ins = input.getInstruction();

        int source1 = ins.getSrc1().getValue();
        int source2 = ins.getSrc2().getValue();
        int result = 0;

        if (ins.getOpcode() == EnumOpcode.DIV) {
            result = source1 / source2;

        } else if (ins.getOpcode() == EnumOpcode.MOD) {
            result = source1 % source2;

        }

        if (globals.getPropertyInteger(IProperties.CPU_RUN_STATE) == IProperties.RUN_STATE_FLUSH) {
            GlobalData.MSFD_cnt = 15;
        }

        if (GlobalData.MSID_cnt < 15) {
            GlobalData.MSID_cnt++;
            setResourceWait("Loop" + GlobalData.MSID_cnt);
        } else {
            GlobalData.MSID_cnt = 0;
        }

        output.setResultValue(result);
        output.setInstruction(ins);
    }
}
