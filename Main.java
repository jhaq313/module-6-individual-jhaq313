import java.util.*;

public class Main {
    public static void main(String[] args) {
        // start the core OS components
        MemoryManager memory = new MemoryManager(256);
        ProcessManager processManager = new ProcessManager(memory);
        CPU cpu = new CPU(processManager, true); // true = enable debug output

        // These are the extrnal programs that will be run by OS
        String[] programFiles = {
            "shared_memory_lock.txt",
            "event_waiter.txt",
            "event_signaler.txt",
            "heap_allocate_write.txt",
            "countdown.txt"
        };

        // We will assign prioritie's starting from 10 and going down
        int priority = 10;

        for (String filename : programFiles) {
            List<Instruction> instructions = ProgramLoader.loadProgramFromFile(filename);

            if (instructions.size() > 0) {
                processManager.createProcess(instructions, priority);
                priority--; // lower priority for next process
            } else {
                System.out.println("Could not load program: " + filename);
            }
        }

        System.out.println("\n--- Starting OS Execution ---\n");
        cpu.run(); // Begin running all loaded processe's
    }
}
