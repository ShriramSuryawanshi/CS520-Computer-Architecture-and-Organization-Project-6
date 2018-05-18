/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package implementation;

import baseclasses.InstructionBase;
import baseclasses.Latch;
import baseclasses.PipelineStageBase;
import static baseclasses.PipelineStageBase.operNames;
import cpusimulator.CpuSimulator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import utilitytypes.EnumOpcode;
import utilitytypes.ICpuCore;
import utilitytypes.IGlobals;
import utilitytypes.IModule;
import utilitytypes.IPipeReg;
import utilitytypes.IProperties;
import utilitytypes.Logger;
import utilitytypes.Operand;

/**
 *
 * @author millerti
 */
public class LoadStoreQueue extends PipelineStageBase {

    public LoadStoreQueue(IModule parent) {
        super(parent, "LoadStoreQueue");

        // Force PipelineStageBase to use the zero-operand compute() method.
        this.disableTwoInputCompute();
    }

    // Data structures
    public void compute() {
        // First, mark any retired STORE instructions

        // Check CPU run state
        // If the LSQ is full and a memory instruction wants to come in from
        // Decode, optionally see if you can proactively free up a retired STORE.
        // See if there's room to storea an instruction received from Decode.
        // Read the input latch.  If it's valid, put the instruction into the 
        // LAST slot.
        // LSQ entries are stored in order from oldest to newest.          
        // Loop over the forwarding sources, capturing all values available
        // right now and storing them into LSQ entries needing those
        // register values.
        // For diagnostic purposes, iterate existing entries of the LSQ and add 
        // their current state to the activity list
        // Next, try to do things that require writing to the
        // next pipeline stage.  If we have already written the output, or
        // the output can't accept work, might as well bail out.
//   this     if (wrote_output || !outputCanAcceptWork(0)) {
//       this     setActivity(...);
//          this  return;
//        this }
        // If and only if there is a LOAD in the FIRST entry of the LSQ, it can be
        // issued to the DCache if needed inputs (to compute the address)
        // will be available net cycle.  
        // Set appropriate "forward#" properties on output latch.
        // Only when a LOAD is not prededed by STOREs can be be issued with
        // forwarding in the next cycle.
        // ******
        // When issuing any LOAD, other entries in the LSQ must be shifted
        // to fill the gap.  The LSQ must maintain program order.  This applies
        // to all cases where the LSQ issues a LOAD.
        // ******
        // If we issued a load, bail out.  ** Before bailing out, ALWAYS make sure
        // that setActivity has been called with info about all the 
        // instructons in the queue.  This is for diagnostic purposes. **
        // Look for a load whose address matches that of a store earlier in the list.
        // Since we don't do speculative loads, if a store is encountered with an
        // unknown address, then no subsequent loads can be issued.
        // Outer loop:  Iterate over all LOAD instructions from first to last
        // LSQ entries.
        // Inner loop:  Iterate backwards over STOREs that came before the LOAD.
        // If you find a STORE with a unknown address, skip to the next LOAD
        // in the outer loop.
        // If you find a STORE with a matching address, make sure the STORE
        // has a data value. If it does, this LOAD can be ussued as a BYPASS LOAD:
        // copy the value from the STORE to the LOAD and issue the load, 
        // instructing the DCache to NOT fetch from memory.
        // If the STORE does not have a data value, skip to the next LOAD in 
        // the outer loop.  
        // Data that is forwarded from a STORE to a LOAD must some from the
        // matching STORE that is NEAREST to the LOAD in the list.
        // If the inner loop finishes and finds neither a matching STORE addresss
        // nor an unknown store address, this LOAD can be issued as an ACCESS
        // LOAD:  The data is fetched from main memory.
        // If we issued a LOAD, set activity string, bail out
        // If we find no LOADs to process, see if there are any STORES to ISSUE.
        // An issuable store has known address and data.  To the DCache,
        // an ISSUE STORE passes through to Writeback without modifying memory.  (It will
        // modify memory later on retirement.)  Also, stores that are issued
        // are NOT REMOVED from the LSQ.  Simply mark them as completed.
        // If we issued a STORE, set activity string, bail out
        // Finally, see if there is a STORE that can be COMMITTED (retired).
        // Only the FIRST entry of the LSQ can be retired (to maintain 
        // program order).
        // To the DCache, a COMMIT STORE writes its data value to memory
        // but is NOT passed on to Writeback.
        // Set activity string, return        
        // NOTE:
        // ***
        // Whenever you issue any instruction, be sure to call core.incIssued();
        // This is also the case for the IssueQueue.
        // ***
        Latch input = this.readInput(0).duplicate();

        IGlobals globals = (GlobalData) getCore().getGlobals();
        if (globals.getPropertyInteger(IProperties.CPU_RUN_STATE) == IProperties.RUN_STATE_FLUSH || globals.getPropertyInteger(IProperties.CPU_RUN_STATE) == IProperties.RUN_STATE_FAULT) {
            input.consume();
            input.setInvalid();

            if (globals.getPropertyInteger(IProperties.CPU_RUN_STATE) == IProperties.RUN_STATE_FLUSH) {

                for (int i = 0; i < 32; i++) {
                    GlobalData.LSQ[i] = "NULL";

                }
            }
        }

        InstructionBase ins = input.getInstruction();
        String instructions = "";

        int issued = -1;
        int total_ins = 0;
        int sent_ins = 0;
        int stall_ins = 0;

        for (int i = 0; i < 32; i++) {

            if (i == 31 && !GlobalData.LSQ[i].equals("NULL")) {
                addStatusWord("LSQ Full");
                System.exit(0);
            }

            if (GlobalData.LSQ[i].equals("NULL") && !ins.isNull()) {
                GlobalData.LSQ[i] = input.getInstruction().toString();
                GlobalData.latchesLSQ[i] = input;
                getCore().incIssued();
                issued = i;
                input.consume();
                break;
            }
        }

        for (int i = 0; i < 32; i++) {

            if (!GlobalData.LSQ[i].equals("NULL")) {

                ins = GlobalData.latchesLSQ[i].getInstruction();
                total_ins++;

                Latch output;
                EnumOpcode opcode = ins.getOpcode();
                boolean oper0src = opcode.oper0IsSource();
                Operand oper0 = ins.getOper0();
                Operand src1 = ins.getSrc1();
                Operand src2 = ins.getSrc2();

                String destination = "";

                int output_num;

                output_num = lookupOutput("LSQToDCache1");
                output = this.newOutput(output_num);

                //   forwardingSearch(GlobalData.latches[i]);
                ICpuCore core = getCore();

                GlobalData.latchesLSQ[i].deleteProperty("forward0");
                GlobalData.latchesLSQ[i].deleteProperty("forward1");
                GlobalData.latchesLSQ[i].deleteProperty("forward2");

                Set<String> fwdSources = core.getForwardingSources();
                // Put operands into array because we will loop over them,
                // searching the pipeline for forwarding opportunities.
                Operand[] operArray = {oper0, src1, src2};

                // For operands that are not registers, getRegisterNumber() will
                // return -1.  We will use that to determine whether or not to
                // look for a given register in the pipeline.
                int[] srcRegs = new int[3];
                // Only want to forward to oper0 if it's a source.
                srcRegs[0] = oper0src ? oper0.getRegisterNumber() : -1;
                srcRegs[1] = src1.getRegisterNumber();
                srcRegs[2] = src2.getRegisterNumber();

                for (int sn = 0; sn < 3; sn++) {
                    int srcRegNum = srcRegs[sn];
                    // Skip any operands that are not register sources
                    if (srcRegNum < 0) {
                        continue;
                    }
                    // Skip any operands that already have values
                    if (operArray[sn].hasValue()) {
                        continue;
                    }
                    Operand oper = operArray[sn];
                    String srcRegName = oper.getRegisterName();
                    String operName = operNames[sn];

                    String srcFoundIn = null;
                    boolean next_cycle = false;
                    boolean this_cycle = false;

                    prn_loop:

                    for (String fwd_pipe_reg_name : fwdSources) {
                        IPipeReg.EnumForwardingStatus fwd_stat = core.matchForwardingRegister(fwd_pipe_reg_name, srcRegNum);

                        switch (fwd_stat) {
                            case NULL:
                                break;
                            case VALID_NOW:
                                srcFoundIn = fwd_pipe_reg_name;
                                this_cycle = true;
                                break prn_loop;
                            case VALID_NEXT_CYCLE:
                                srcFoundIn = fwd_pipe_reg_name;
                                next_cycle = true;
                                break prn_loop;
                        }
                    }

                    if (srcFoundIn != null) {
                        if (!next_cycle) {
                            // If the register number was found and there is a valid
                            // result, go ahead and get the value.
                            int value = core.getResultValue(srcFoundIn);
                            boolean isfloat = core.isResultFloat(srcFoundIn);
                            operArray[sn].setValue(value, isfloat);

                            if (CpuSimulator.printForwarding) {
                                Logger.out.printf("# Forward from " + srcFoundIn + " this cycle to IQ: op" + sn + " of " + ins.toString() + "\n");

                                if (issued != i) {
                                    GlobalData.LSQ[i] = GlobalData.latchesLSQ[i].getInstruction().toString();
                                }
                            }

                        } else {

                            String propname = "forward" + sn;
                            GlobalData.latchesLSQ[i].setProperty(propname, srcFoundIn);

                        }
                    }
                }

                boolean flag = false;

                for (int sn = 0; sn < 3; sn++) {
                    int srcRegNum = srcRegs[sn];
                    // Skip any operands that are not register sources
                    if (srcRegNum < 0) {
                        continue;
                    }
                    // Skip any that already have values
                    if (operArray[sn].hasValue()) {
                        continue;
                    }

                    String propname = "forward" + sn;
                    if (!GlobalData.latchesLSQ[i].hasProperty(propname)) {
                        // If any source operand is not available
                        // now or on the next cycle, then stall.

                        flag = true;
                        break;
                    }
                }

                if (flag) {

                    if (issued == i) {
                        instructions = instructions + GlobalData.LSQ[i] + " [new] \n";
                    } else if (issued != i) {
                        instructions = instructions + GlobalData.LSQ[i] + " \n";
                    }

                    stall_ins++;

                    continue;
                }

                if (!output.canAcceptWork()) {
                    continue;
                }

                //  if (ins.getOpcode() == EnumOpcode.HALT) shutting_down = true;            
                if (CpuSimulator.printForwarding) {
                    for (int sn = 0; sn < 3; sn++) {
                        String propname = "forward" + sn;
                        if (GlobalData.latchesLSQ[i].hasProperty(propname)) {
                            String operName = PipelineStageBase.operNames[sn];
                            String srcFoundIn = GlobalData.latchesLSQ[i].getPropertyString(propname);
                            String srcRegName = operArray[sn].getRegisterName();
                            Logger.out.printf("# Posting forward from " + srcFoundIn + " next cycle to " + destination + " op" + sn + " of " + ins.toString() + "\n");
                        }
                    }
                }

                output.copyAllPropertiesFrom(GlobalData.latchesLSQ[i]);
                output.setInstruction(ins);
                output.write();
                getCore().incDispatched();

                if (issued == i) {
                    instructions = instructions + GlobalData.LSQ[i] + " [new] [selected] \n";
                } else if (issued != i) {
                    instructions = instructions + GlobalData.LSQ[i] + " [selected] \n";
                }

                if (ins.getOpcode() == EnumOpcode.FDIV) {
                    GlobalData.Fdiv_sent = 0;
                }

                sent_ins++;
                GlobalData.LSQ[i] = "NULL";

            }
        }

        for (int a = 0; a < 32; a++) {

            if (!GlobalData.LSQ[a].equals("NULL")) {
                continue;

            } else {

                for (int b = a; b < 32; b++) {

                    if (GlobalData.LSQ[b].equals("NULL")) {
                        continue;
                    } else {
                        GlobalData.LSQ[a] = GlobalData.LSQ[b];
                        GlobalData.LSQ[b] = "NULL";
                        GlobalData.latchesLSQ[a] = GlobalData.latchesLSQ[b];
                        break;
                    }
                }
            }
        }

        setActivity(instructions);

        if (sent_ins == 0 && total_ins > 0 && total_ins == stall_ins && GlobalData.Fdiv_sent > 0) {
            addStatusWord("OutputStall(IQ2FD)");

            if (GlobalData.Fdiv_sent == 15) {
                GlobalData.Fdiv_sent = -1;
            }
        }
    }
}
