/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package implementation;

import tools.MyALU;
import utilitytypes.EnumOpcode;
import baseclasses.InstructionBase;
import baseclasses.PipelineRegister;
import baseclasses.PipelineStageBase;
import voidtypes.VoidLatch;
import baseclasses.CpuCore;
import baseclasses.Latch;
import cpusimulator.CpuSimulator;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import utilitytypes.ClockedIntArray;
import static utilitytypes.EnumOpcode.*;
import utilitytypes.ICpuCore;
import utilitytypes.IGlobals;
import utilitytypes.IPipeReg;
import utilitytypes.IProperties;
import static utilitytypes.IProperties.*;
import utilitytypes.IRegFile;
import utilitytypes.Logger;
import utilitytypes.Operand;
import voidtypes.VoidLabelTarget;

/**
 * The AllMyStages class merely collects together all of the pipeline stage
 * classes into one place. You are free to split them out into top-level
 * classes.
 *
 * Each inner class here implements the logic for a pipeline stage.
 *
 * It is recommended that the compute methods be idempotent. This means that if
 * compute is called multiple times in a clock cycle, it should compute the same
 * output for the same input.
 *
 * How might we make updating the program counter idempotent?
 *
 * @author
 */
public class AllMyStages {

    /**
     * * Fetch Stage **
     */
    static class Fetch extends PipelineStageBase {

        public Fetch(ICpuCore core) {
            super(core, "Fetch");
        }

        // Does this state have an instruction it wants to send to the next
        // stage?  Note that this is computed only for display and debugging
        // purposes.
        boolean has_work;
        boolean shutting_down = false;

        /**
         * For Fetch, this method only has diagnostic value. However,
         * stageHasWorkToDo is very important for other stages.
         *
         * @return Status of Fetch, indicating that it has fetched an
         * instruction that needs to be sent to Decode.
         */
        @Override
        public boolean stageHasWorkToDo() {
            return has_work;
        }

        @Override
        public String getStatus() {
            IGlobals globals = (GlobalData) getCore().getGlobals();
            if (globals.getPropertyInteger("branch_state_fetch") == GlobalData.BRANCH_STATE_WAITING) {
                addStatusWord("ResolveWait");
            }
            return super.getStatus();
        }

        @Override
        public void compute(Latch input, Latch output) {

            if (shutting_down) {
                addStatusWord("Shutting down");
                setActivity("");
                return;
            }

            IGlobals globals = (GlobalData) getCore().getGlobals();

            if (globals.getPropertyInteger("cpu_run_state") == GlobalData.RUN_STATE_FAULT) {
                setResourceWait("cpu_run_state");
            }

            if (globals.getPropertyInteger("cpu_run_state") == GlobalData.RUN_STATE_RECOVERY) {
                int pc = globals.getPropertyInteger("recovery_pc");
                InstructionBase ins = globals.getInstructionAt(pc);
                globals.setClockedProperty(PROGRAM_COUNTER, pc);
                globals.setClockedProperty(RECOVERY_TAKEN, pc);
                globals.setClockedProperty("cpu_run_state", GlobalData.RUN_STATE_RUNNING);
            }

            // Get the PC and fetch the instruction
            int pc_no_branch = globals.getPropertyInteger(PROGRAM_COUNTER);
            InstructionBase ins = globals.getInstructionAt(pc_no_branch);

            has_work = false;

            if (ins.isNull()) {
                // Fetch is working on no instruction at no address
                setActivity("");
            } else {
                // Since there is no input pipeline register, we have to inform
                // the diagnostic helper code explicitly what instruction Fetch
                // is working on.
                has_work = true;
                output.setInstruction(ins);
                setActivity(ins.toString());
            }

            int branch_state_fetch = globals.getPropertyInteger("branch_state_fetch");
            int reg_branch_target = globals.getPropertyInteger(REG_BRANCH_TARGET);

            if (branch_state_fetch == GlobalData.BRANCH_STATE_TARGET) {
                ins = globals.getInstructionAt(reg_branch_target);
                globals.setProperty(PROGRAM_COUNTER, reg_branch_target);
                globals.setClockedProperty("branch_state_fetch", GlobalData.BRANCH_STATE_NULL);
            }

            ins = ins.duplicate();

            Operand oper0 = ins.getOper0();
            EnumOpcode opcode = ins.getOpcode();

            switch (opcode) {

                case JMP:

                    if (ins.getLabelTarget().isNull()) {
                        output.setInstruction(ins);
                        output.setProperty(LOOKUP_BRANCH_TARGET, 0);
                        globals.setClockedProperty("branch_state_fetch", GlobalData.BRANCH_STATE_WAITING);
                        this.setResourceWait(oper0.getRegisterName());

                    } else {
                        globals.setClockedProperty("branch_state_fetch", GlobalData.BRANCH_STATE_NULL);
                        globals.setProperty(PROGRAM_COUNTER, ins.getLabelTarget().getAddress());
                        ins.setBranchPrediction(InstructionBase.EnumBranch.TAKEN);
                    }

                    break;

                case CALL:

                    if (ins.getLabelTarget().isNull()) {
                        output.setInstruction(ins);
                        output.setProperty(LOOKUP_BRANCH_TARGET, 1);
                        globals.setClockedProperty("branch_state_fetch", GlobalData.BRANCH_STATE_WAITING);
                        this.setResourceWait(oper0.getRegisterName());

                    } else {
                        globals.setClockedProperty("branch_state_fetch", GlobalData.BRANCH_STATE_NULL);
                        globals.setProperty(PROGRAM_COUNTER, ins.getLabelTarget().getAddress());
                        ins.setBranchPrediction(InstructionBase.EnumBranch.TAKEN);
                    }

                    break;

                case BRA:

                    if (ins.getLabelTarget().isNull()) {
                        output.setInstruction(ins);
                        output.setProperty(LOOKUP_BRANCH_TARGET, 1);
                        globals.setClockedProperty("branch_state_fetch", GlobalData.BRANCH_STATE_WAITING);
                        this.setResourceWait(oper0.getRegisterName());

                    } else {

                        if (ins.getLabelTarget().getAddress() < pc_no_branch) {
                            globals.setClockedProperty("branch_state_fetch", GlobalData.BRANCH_STATE_NULL);
                            globals.setProperty(PROGRAM_COUNTER, ins.getLabelTarget().getAddress());
                            ins.setBranchPrediction(InstructionBase.EnumBranch.TAKEN);
                        } else {
                            ins.setBranchPrediction(InstructionBase.EnumBranch.NOT_TAKEN);
                            globals.setClockedProperty(PROGRAM_COUNTER, pc_no_branch + 1);
                        }
                        break;
                    }

                default:

                    if (branch_state_fetch == GlobalData.BRANCH_STATE_NULL) {
                        globals.setClockedProperty(PROGRAM_COUNTER, pc_no_branch + 1);
                    }
                    break;
            }

            // Initialize this status flag to assume a stall or bubble condition
            // by default.
            //    has_work = false;
            // If the instruction is NULL (like we ran off the end of the
            // program), just return.  However, for diagnostic purposes,
            // we make sure something meaningful appears when 
            // CpuSimulator.printStagesEveryCycle is set to true.
            // If the output cannot accept work, then 
            if (!output.canAcceptWork()) {
                return;
            }

//            Logger.out.println("No stall");
            boolean branch_wait = false;
            if (branch_state_fetch == GlobalData.BRANCH_STATE_WAITING) {
                branch_wait = true;
            }
        }
    }

    /**
     * * Decode Stage **
     */
    static class Decode extends PipelineStageBase {

        public Decode(ICpuCore core) {
            super(core, "Decode");
        }

        // When a branch is taken, we have to squash the next instruction
        // sent in by Fetch, because it is the fall-through that we don't
        // want to execute.  This flag is set only for status reporting purposes.
        boolean squashing_instruction = false;
        boolean shutting_down = false;

        @Override
        public String getStatus() {
            IGlobals globals = (GlobalData) getCore().getGlobals();
            String s = super.getStatus();
            if (globals.getPropertyBoolean("decode_squash")) {
                s = "Squashing";
            }
            return s;
        }

//        private static final String[] fwd_regs = {"ExecuteToWriteback", 
//            "MemoryToWriteback"};
        @Override
        public void compute() {
            if (shutting_down) {
                addStatusWord("Shutting down");
                setActivity("");
                return;
            }

            Latch input = readInput(0);
            Latch output;

            IGlobals globals = (GlobalData) getCore().getGlobals();

            input = input.duplicate();
            InstructionBase ins = input.getInstruction();

            if (ins.isNull()) {
                return;
            }

            if (GlobalData.CPU_RUN_STATE.equals("RUN_STATE_HALTING") || GlobalData.CPU_RUN_STATE.equals("RUN_STATE_HALTED") || GlobalData.CPU_RUN_STATE.equals("RUN_STATE_FAULT")) {
                input.consume();
                return;
            }

            setActivity(ins.toString());

            int rob_head = globals.getPropertyInteger(ROB_HEAD);
            int rob_tail = globals.getPropertyInteger(ROB_TAIL);

            ins.setReorderBufferIndex(rob_tail);
            globals.setClockedProperty(ROB_USED, ins);
            globals.setClockedProperty(ROB_TAIL, rob_tail + 1);

            if (((rob_tail + 1) & 255) == rob_head) {
                setResourceWait("ROB Full!");
                return;
            }

            //Logger.out.println("%% ROB head " + rob_head + ": state= " + globals.getPropertyInteger(IProperties.CPU_RUN_STATE) + " ins=" + ins.toString());
            EnumOpcode opcode = ins.getOpcode();
            Operand oper0 = ins.getOper0();
            IRegFile regfile = globals.getRegisterFile();

            // @shree - Register renaming for Source1
            if (ins.getSrc1().isRegister()) {
                ins.getSrc1().rename(GlobalData.rat[ins.getSrc1().getRegisterNumber()]);
            }

            // @shree - Register renaming for Source2
            if (ins.getSrc2().isRegister()) {
                ins.getSrc2().rename(GlobalData.rat[ins.getSrc2().getRegisterNumber()]);
            }

            // @shree - Register renaming if oper0IsSource
//            if (opcode.oper0IsSource() && ins.getOpcode() != EnumOpcode.JMP) {
//                ins.getOper0().rename(GlobalData.rat[ins.getOper0().getRegisterNumber()]);
//            }
//
//            if (ins.getOpcode() == EnumOpcode.JMP) {
//
//                if (ins.getOper0().isRegister()) {
//                    ins.getOper0().rename(GlobalData.rat[ins.getOper0().getRegisterNumber()]);
//                }
//            }
            int available_reg = -1;
            // @shree - getting availble physical register

            for (int i = 0; i <= 256; i++) {

                if (!regfile.isUsed(i)) {
                    available_reg = i;
                    break;
                }
            }

            registerFileLookup(input);
            forwardingSearch(input);

//            if (ins.getOpcode() == EnumOpcode.CALL) {
//
//                if (ins.getOper0().isRegister()) {
//
//                    regfile.markUnmapped(GlobalData.rat[ins.getOper0().getRegisterNumber()], true);
//                    regfile.changeFlags(available_reg, IRegFile.SET_USED | IRegFile.SET_INVALID, IRegFile.CLEAR_FLOAT);
//                    regfile.markUnmapped(available_reg, false);
//
//                    Logger.out.println("Dest R" + oper0.getRegisterNumber() + ": P" + GlobalData.rat[oper0.getRegisterNumber()] + " released, P" + available_reg + " allocated");
//
//                    GlobalData.rat[oper0.getRegisterNumber()] = available_reg;
//                    ins.getOper0().rename(available_reg);
//                }
//            }
            // See what operands can be fetched from the register file
            // See what operands can be fetched by forwarding
            Operand src1 = ins.getSrc1();
            Operand src2 = ins.getSrc2();

            boolean take_branch = false;
            int value0 = 0;
            int value1 = 0;

            int output_num;
            output_num = lookupOutput("DecodeToIQ");
            output = this.newOutput(output_num);

            switch (opcode) {
                
                case BRA:
                case JMP:
                    
                    if(input.hasProperty(LOOKUP_BRANCH_TARGET)) {
                        
                        if (input.getPropertyInteger(LOOKUP_BRANCH_TARGET) == 1) {
                        
                            System.out.println("send values to fetch");
                        }
                    }
                    
                    break;

                case CALL:
                    Operand pc = Operand.newRegister(Operand.PC_REGNUM);
                    pc.setIntValue(ins.getPCAddress());
                    ins.setSrc1(pc);
                    ins.setSrc2(Operand.newLiteralSource(1));
                    break;

                case LOAD:
                case STORE:

                    output_num = lookupOutput("DecodeToLSQ");
                    output = this.newOutput(output_num);
                    break;

                default:

                    output_num = lookupOutput("DecodeToIQ");
                    output = this.newOutput(output_num);
                    break;
            }

            if (ins.getOpcode() == EnumOpcode.HALT) {
                shutting_down = true;
            }

//            if (!opcode.oper0IsSource()) {
//
//                // @shree - renaming the destination
//                if (ins.getOper0().isRegister()) {
//
//                    regfile.markUnmapped(GlobalData.rat[ins.getOper0().getRegisterNumber()], true);
//                    regfile.changeFlags(available_reg, IRegFile.SET_USED | IRegFile.SET_INVALID, IRegFile.CLEAR_FLOAT);
//                    regfile.markUnmapped(available_reg, false);
//
//                    Logger.out.println("Dest R" + oper0.getRegisterNumber() + ": P" + GlobalData.rat[oper0.getRegisterNumber()] + " released, P" + available_reg + " allocated");
//
//                    GlobalData.rat[oper0.getRegisterNumber()] = available_reg;
//                    ins.getOper0().rename(available_reg);
//                }
//            }
            if (opcode.needsWriteback()) {

                // @shree - renaming the destination
                if (ins.getOper0().isRegister()) {

                    //regfile.markUnmapped(GlobalData.rat[ins.getOper0().getRegisterNumber()], true);
                    // regfile.changeFlags(available_reg, IRegFile.SET_USED | IRegFile.SET_INVALID, IRegFile.CLEAR_FLOAT | IRegFile.CLEAR_UNMAPPED);
                    Logger.out.println("Dest R" + oper0.getRegisterNumber() + ": P" + GlobalData.rat[oper0.getRegisterNumber()] + " released, P" + available_reg + " allocated");

                    GlobalData.rat[oper0.getRegisterNumber()] = available_reg;
                    ins.getOper0().rename(available_reg);
                    regfile.markNewlyAllocated(available_reg);
                }
            }

            if (!output.canAcceptWork()) {
                return;
            }

            // Copy the forward# properties
            output.copyAllPropertiesFrom(input);
            // Copy the instruction

            output.setInstruction(ins);
            // Send the latch data to the next stage

            output.write();

            // And don't forget to indicate that the input was consumed!
            input.consume();
        }
    }

    /**
     * * Execute Stage **
     */
    static class Execute extends PipelineStageBase {

        public Execute(ICpuCore core) {
            super(core, "Execute");
        }

        @Override
        public void compute(Latch input, Latch output) {
            if (input.isNull()) {
                return;
            }
            doPostedForwarding(input);
            InstructionBase ins = input.getInstruction();

            int source1 = ins.getSrc1().getValue();
            int source2 = ins.getSrc2().getValue();
            int oper0 = ins.getOper0().getValue();

            int result = MyALU.execute(ins.getOpcode(), source1, source2, oper0);

            boolean isfloat = ins.getSrc1().isFloat() || ins.getSrc2().isFloat();
            output.setResultValue(result, isfloat);
            output.setInstruction(ins);
        }
    }

}
