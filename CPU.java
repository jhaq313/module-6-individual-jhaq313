public class CPU {
    public static final int IP_REGISTER = 11;
    public static final int SP_REGISTER = 13;
    public static int clockCycleCount = 0;
    
    public static int[] registers = new int[16];
    public static boolean signFlag;
    public static boolean zeroFlag;
    public static final boolean[] locks = new boolean[32];
    public static final boolean[] events = new boolean[10];
    
    private final ProcessManager processManager;
    private final boolean debugMode;

    public CPU(ProcessManager pm, boolean debug) {
        this.processManager = pm;
        this.debugMode = debug;
        registers[SP_REGISTER] = 0xFF00;
    }

    public static void releaseLock(int lockId) {
        if (lockId >= 0 && lockId < locks.length) {
            locks[lockId] = false;
        }
    }

    public void run() {
        System.out.println("CPU starting execution cycle");
        
        while (true) {
            PCB process = processManager.getNextProcess();
            if (process == null) {
                System.out.println("No more processes in queue");
                break;
            }

            if (debugMode) {
                System.out.printf("\n[Cycle %d] Switching to process %d (priority %d)\n", 
                    clockCycleCount, process.getProcessId(), process.getPriority());
                processManager.printProcessStates();
            }

            processManager.contextSwitch(process);
            executeProcess(process);
            clockCycleCount++;
        }
        
        System.out.println("\nCPU execution completed");
        System.out.printf("Total clock cycles: %d\n", clockCycleCount);
    }

    private void executeProcess(PCB process) {
        int ip = registers[IP_REGISTER];
        
        while (process.getTimeQuantum() > 0) {
            clockCycleCount++;
            
            try {
                int opcodeVal = processManager.getMemoryManager()
                    .readMemory(process.getProcessId(), ip);
                int arg1 = processManager.getMemoryManager()
                    .readMemory(process.getProcessId(), ip + 4);
                int arg2 = processManager.getMemoryManager()
                    .readMemory(process.getProcessId(), ip + 8);

                InstructionSet opcode = InstructionSet.values()[opcodeVal];
                
                if (debugMode) {
                    System.out.printf("[%04d] Executing %-12s args: %3d, %3d | ", 
                        ip, opcode, arg1, arg2);
                }

                switch (opcode) {
                    case LOAD_VALUE:
                        registers[arg1] = arg2;
                        break;
                    case INCREMENT:
                        registers[arg1]++;
                        break;
                    case ADD_VALUE:
                        registers[arg1] += arg2;
                        break;
                    case SHOW_REG:
                        System.out.printf("[OUTPUT] R%d = %d\n", arg1, registers[arg1]);
                        break;
                    case SLEEP:
                        process.setSleepCounter(arg1);
                        process.saveRegisters(registers);
                        processManager.addSleepingProcess(process);
                        if (debugMode) {
                            System.out.printf("Sleeping for %d cycles\n", process.getSleepCounter());
                        }
                        ip += 12;
                        registers[IP_REGISTER] = ip;
                        process.setTimeQuantum(process.getTimeQuantum() - 1);
                        return;
                    case TERMINATE:
                        process.terminate();
                        process.saveRegisters(registers);
                        if (debugMode) {
                            System.out.println("Process terminated normally");
                            process.printStatistics();
                        }
                        return;
                    case MAP_SHARED_MEM:
                        int sharedAddr = processManager.getMemoryManager()
                            .mapSharedMemory(process.getProcessId(), arg1);
                        registers[arg2] = sharedAddr;
                        break;
                    case ACQUIRE_LOCK:
                        if (arg1 >= 0 && arg1 < locks.length && !locks[arg1]) {
                            locks[arg1] = true;
                            process.addHeldLock(arg1);
                        }
                        break;
                    case RELEASE_LOCK:
                        if (arg1 >= 0 && arg1 < locks.length && 
                            locks[arg1] && process.isHoldingLock(arg1)) {
                            locks[arg1] = false;
                            process.removeHeldLock(arg1);
                        }
                        break;
                    case SIGNAL_EVENT:
                        if (arg1 >= 0 && arg1 < events.length) {
                            events[arg1] = true;
                            processManager.notifyEvent(arg1);
                        }
                        break;
                    case WAIT_EVENT:
                        if (arg1 >= 0 && arg1 < events.length && !events[arg1]) {
                            process.setState(PCB.ProcessState.WAITING_EVENT);
                            process.setWaitingEventId(arg1);
                            process.saveRegisters(registers);
                            processManager.addWaitingProcess(process);
                            ip += 12;
                            registers[IP_REGISTER] = ip;
                            process.setTimeQuantum(process.getTimeQuantum() - 1);
                            return;
                        }
                        break;
                    case ALLOC:
                        int size = registers[arg1];
                        int address = processManager.getMemoryManager().allocateHeap(process, size);
                        registers[arg2] = (address != 0) ? address : 0;
                        break;
                    case FREE_MEMORY:
                        int addrToFree = registers[arg1];
                        processManager.getMemoryManager().freeHeap(process, addrToFree);
                        break;
                    case MEMORY_STATS:
                        processManager.getMemoryManager().printMemoryStats();
                        break;
                    case WRITE_MEM:
                        try {
                            int writeAddress = registers[arg1];
                            int value = registers[arg2];
                            processManager.getMemoryManager().writeMemory(process.getProcessId(), writeAddress, value);
                            if (debugMode) {
                                System.out.printf(" | Wrote %d to address %d", value, writeAddress);
                            }
                        } catch (RuntimeException e) {
                            System.out.println("\nMemory write failed: " + e.getMessage());
                            process.setState(PCB.ProcessState.TERMINATED);
                        }
                        break;
                    default:
                        System.out.println("Unknown opcode: " + opcode);
                        process.setState(PCB.ProcessState.TERMINATED);
                        return;
                }

                zeroFlag = (registers[arg1] == 0);
                signFlag = (registers[arg1] < 0);

                if (debugMode) {
                    System.out.printf(" | R%d=%d | Flags: [Z:%b, S:%b]\n", 
                        arg1, registers[arg1], zeroFlag, signFlag);
                }

                ip += 12;
                registers[IP_REGISTER] = ip;
                process.setTimeQuantum(process.getTimeQuantum() - 1);

            } catch (Exception e) {
                System.out.println("\nCPU Exception: " + e.getMessage());
                process.setState(PCB.ProcessState.TERMINATED);
                break;
            }
        }
        
        process.saveRegisters(registers);
        if (debugMode && process.getState() != PCB.ProcessState.TERMINATED) {
            System.out.printf("\nProcess %d quantum expired\n", process.getProcessId());
            process.printStatistics();
        }
    }
}