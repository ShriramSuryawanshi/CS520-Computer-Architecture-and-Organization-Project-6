/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package implementation;

import baseclasses.InstructionBase;
import baseclasses.InstructionBase.EnumBranch;
import baseclasses.Latch;
import baseclasses.PipelineStageBase;
import utilitytypes.EnumComparison;
import utilitytypes.EnumOpcode;
import utilitytypes.IModule;
import utilitytypes.IProperties;
import utilitytypes.Operand;

/**
 *
 * @author millerti
 */
public class BranchResUnit extends PipelineStageBase {

    public BranchResUnit(IModule parent) {
        super(parent, "BranchResUnit");
    }

    static boolean resolveBranch(EnumComparison condition, int value0) {
        return true;
    }

    @Override
    public void compute(Latch input, Latch output) {
        if (input.isNull()) {
            return;
        }
        doPostedForwarding(input);
        InstructionBase ins = input.getInstruction().duplicate();

        EnumOpcode opcode = ins.getOpcode();
        Operand oper0 = ins.getOper0();
        Operand src1 = ins.getSrc1();
        Operand src2 = ins.getSrc2();

        switch (opcode) {

            case JMP:

                output.setInstruction(ins);
                break;

            case CALL:

                int src1v = src1.getValue();
                int src2v = src2.getValue();

                int result = src1v + src2v;

                output.setInstruction(ins);
                output.setResultValue(result);
                output.copyAllPropertiesFrom(input);
                break;

            case BRA:

                int value0 = 0;
                boolean take_branch = false;

                value0 = oper0.getValue();

                switch (ins.getComparison()) {
                    case EQ:
                        take_branch = (value0 == 0);
                        break;
                    case NE:
                        take_branch = (value0 != 0);
                        break;
                    case GT:
                        take_branch = (value0 > 0);
                        break;
                    case GE:
                        take_branch = (value0 >= 0);
                        break;
                    case LT:
                        take_branch = (value0 < 0);
                        break;
                    case LE:
                        take_branch = (value0 <= 0);
                        break;
                }

                if (take_branch) {
                    ins.setBranchResolution(EnumBranch.TAKEN);
                } else {
                    ins.setBranchResolution(EnumBranch.NOT_TAKEN);
                }

                if (ins.getBranchPrediction() != ins.getBranchResolution()) {
                    ins.setFault(InstructionBase.EnumFault.BRANCH);
                }
        }

        output.setInstruction(ins);
    }

}
