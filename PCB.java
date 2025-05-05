import java.util.*;

public class PCB {
    public enum ProcessState { NEW, READY, RUNNING, WAITING_SLEEP, WAITING_EVENT, TERMINATED }
    
    private final int processId;
    private final int[] registers = new int[16];
    private ProcessState state;
    private final int priority;
    private int timeQuantum;
    private final List<Integer> workingSetPages = new ArrayList<>();
    private int sleepCounter;
    private int contextSwitchCount;
    private boolean signFlag;
    private boolean zeroFlag;
    private final Set<Integer> heldLocks = new HashSet<>();
    private int waitingEventId = -1;
    private final Map<Integer, HeapBlock> heapAllocations = new HashMap<>();
    private int heapNextAddress = 0x1000;

    public PCB(int pid, int priority) {
        this.processId = pid;
        this.priority = priority;
        this.state = ProcessState.NEW;
        this.timeQuantum = 10;
    }

    public int getProcessId() { return processId; }
    public int getPriority() { return priority; }
    public int getTimeQuantum() { return timeQuantum; }
    public ProcessState getState() { return state; }
    public int[] getRegisters() { return registers.clone(); }
    public List<Integer> getWorkingSetPages() { return new ArrayList<>(workingSetPages); }
    public int getSleepCounter() { return sleepCounter; }
    public boolean getSignFlag() { return signFlag; }
    public boolean getZeroFlag() { return zeroFlag; }
    public int getContextSwitchCount() { return contextSwitchCount; }
    public Set<Integer> getHeldLocks() { return new HashSet<>(heldLocks); }
    public int getWaitingEventId() { return waitingEventId; }
    public Map<Integer, HeapBlock> getHeapAllocations() { return new HashMap<>(heapAllocations); }
    public int getHeapNextAddress() { return heapNextAddress; }

    public void setTimeQuantum(int quantum) { this.timeQuantum = quantum; }
    public void setState(ProcessState state) { this.state = state; }
    public void setSleepCounter(int count) { 
        this.sleepCounter = Math.max(count, 1);
        this.state = ProcessState.WAITING_SLEEP;
    }
    public void setSignFlag(boolean flag) { this.signFlag = flag; }
    public void setZeroFlag(boolean flag) { this.zeroFlag = flag; }
    public void setWaitingEventId(int eventId) { this.waitingEventId = eventId; }
    public void setHeapNextAddress(int addr) { this.heapNextAddress = addr; }

    public void incrementContextSwitches() { contextSwitchCount++; }
    public void addHeldLock(int lockId) { heldLocks.add(lockId); }
    public void removeHeldLock(int lockId) { heldLocks.remove(lockId); }
    public boolean isHoldingLock(int lockId) { return heldLocks.contains(lockId); }
    public void addHeapAllocation(int address, HeapBlock block) { heapAllocations.put(address, block); }
    public void removeHeapAllocation(int address) { heapAllocations.remove(address); }
    public void updateWorkingSet(int page) { 
        if (!workingSetPages.contains(page)) {
            workingSetPages.add(page);
            Collections.sort(workingSetPages);
        }
    }

    public void decrementSleep() {
        if (sleepCounter > 0) {
            sleepCounter--;
            if (sleepCounter == 0) {
                state = ProcessState.READY;
            }
        }
    }

    public void saveRegisters(int[] cpuRegisters) {
        System.arraycopy(cpuRegisters, 0, registers, 0, 16);
    }

    public void terminate() {
        for (int lockId : heldLocks) {
            CPU.releaseLock(lockId);
        }
        heldLocks.clear();
        setState(ProcessState.TERMINATED);
    }

    public void printStatistics() {
        System.out.printf("\nProcess %d Statistics:\n", processId);
        System.out.println("---------------------");
        System.out.printf("Priority: %d\n", priority);
        System.out.printf("State: %s\n", state);
        System.out.printf("Context Switches: %d\n", contextSwitchCount);
        System.out.printf("Time Quantum: %d\n", timeQuantum);
        System.out.printf("Sleep Counter: %d\n", sleepCounter);
        System.out.printf("Waiting Event: %d\n", waitingEventId);
        System.out.printf("Held Locks: %s\n", heldLocks);
        System.out.printf("Heap Allocations: %s\n", heapAllocations);
        System.out.printf("Working Set Pages: %s\n", workingSetPages);
        System.out.printf("Flags: [SIGN: %b, ZERO: %b]\n", signFlag, zeroFlag);
        System.out.println("Register Dump:");
        for (int i = 0; i < registers.length; i++) {
            System.out.printf("  R%-2d: %d\n", i, registers[i]);
        }
    }
}

class HeapBlock {
    int size;
    boolean isFree;

    public HeapBlock(int size, boolean isFree) {
        this.size = size;
        this.isFree = isFree;
    }

    @Override
    public String toString() {
        return String.format("[Size: %d, Free: %b]", size, isFree);
    }
}