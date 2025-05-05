import java.util.*;

public class ProcessManager {
    private static ProcessManager instance;
    private final PriorityQueue<PCB> readyQueue;
    private final List<PCB> sleepingProcesses;
    private final Map<Integer, List<PCB>> eventWaitingProcesses;
    private PCB currentProcess;
    private final MemoryManager memoryManager;
    private int nextPid = 1;
    private final List<PCB> allProcesses = new ArrayList<>();

    public static ProcessManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ProcessManager not initialized");
        }
        return instance;
    }

    public ProcessManager(MemoryManager mm) {
        instance = this;
        this.memoryManager = mm;
        this.readyQueue = new PriorityQueue<>(
            Comparator.comparingInt(PCB::getPriority).reversed()
        );
        this.sleepingProcesses = new ArrayList<>();
        this.eventWaitingProcesses = new HashMap<>();
    }

    public MemoryManager getMemoryManager() { return memoryManager; }
    public List<PCB> getAllProcesses() { return new ArrayList<>(allProcesses); }

    public void createProcess(List<Instruction> program, int priority) {
        PCB pcb = new PCB(nextPid++, priority);
        memoryManager.loadProgram(pcb, program);
        readyQueue.add(pcb);
        allProcesses.add(pcb);
        System.out.printf("Created process %d with priority %d\n", pcb.getProcessId(), priority);
    }

    public void addSleepingProcess(PCB process) {
        if (process.getSleepCounter() > 0) {
            sleepingProcesses.add(process);
            System.out.printf("Process %d sleeping for %d cycles\n",
                process.getProcessId(), process.getSleepCounter());
        } else {
            readyQueue.add(process);
        }
    }

    public void addWaitingProcess(PCB process) {
        int eventId = process.getWaitingEventId();
        eventWaitingProcesses.computeIfAbsent(eventId, k -> new ArrayList<>()).add(process);
        System.out.printf("Process %d waiting for event %d\n",
            process.getProcessId(), eventId);
    }

    public void notifyEvent(int eventId) {
        List<PCB> waiting = eventWaitingProcesses.remove(eventId);
        if (waiting != null) {
            for (PCB p : waiting) {
                p.setState(PCB.ProcessState.READY);
                readyQueue.add(p);
                System.out.printf("Process %d woke up from event %d\n",
                    p.getProcessId(), eventId);
            }
        }
    }

    public PCB getNextProcess() {
        updateSleepingProcesses();
        return readyQueue.poll();
    }

    private void updateSleepingProcesses() {
        Iterator<PCB> it = sleepingProcesses.iterator();
        while (it.hasNext()) {
            PCB p = it.next();
            p.decrementSleep();
            if (p.getState() == PCB.ProcessState.READY) {
                readyQueue.add(p);
                it.remove();
                System.out.printf("Process %d woke up from sleep\n", p.getProcessId());
            }
        }
    }

    public void contextSwitch(PCB newProcess) {
        if (currentProcess != null) {
            currentProcess.saveRegisters(CPU.registers);
            currentProcess.setSignFlag(CPU.signFlag);
            currentProcess.setZeroFlag(CPU.zeroFlag);
            
            if (currentProcess.getState() == PCB.ProcessState.RUNNING && 
                currentProcess.getState() != PCB.ProcessState.TERMINATED) {
                currentProcess.setState(PCB.ProcessState.READY);
                readyQueue.add(currentProcess);
            }
        }

        if (newProcess != null) {
            System.arraycopy(newProcess.getRegisters(), 0, CPU.registers, 0, 16);
            CPU.signFlag = newProcess.getSignFlag();
            CPU.zeroFlag = newProcess.getZeroFlag();
            newProcess.setState(PCB.ProcessState.RUNNING);
            System.out.printf("Switched to process %d (priority %d)\n", 
                newProcess.getProcessId(), newProcess.getPriority());
        }

        currentProcess = newProcess;
    }

    public void printProcessStates() {
        System.out.println("\nCurrent Process States:");
        System.out.println("----------------------");
        
        if (currentProcess != null) {
            System.out.printf("Running: %d (Priority %d)\n", 
                currentProcess.getProcessId(), currentProcess.getPriority());
        }
        
        System.out.println("Ready Queue:");
        for (PCB p : readyQueue) {
            System.out.printf("- Process %d (Priority %d)\n", 
                p.getProcessId(), p.getPriority());
        }
        
        System.out.println("Sleeping Processes:");
        for (PCB p : sleepingProcesses) {
            System.out.printf("- Process %d (Sleep counter: %d)\n", 
                p.getProcessId(), p.getSleepCounter());
        }
        
        System.out.println("Event Waiting Processes:");
        for (Map.Entry<Integer, List<PCB>> entry : eventWaitingProcesses.entrySet()) {
            for (PCB p : entry.getValue()) {
                System.out.printf("- Process %d (Waiting for event %d)\n",
                    p.getProcessId(), entry.getKey());
            }
        }
    }
}