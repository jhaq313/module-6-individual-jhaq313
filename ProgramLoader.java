import java.io.*;
import java.util.*;

public class ProgramLoader {
    public static List<Instruction> loadProgramFromFile(String filename) {
        List<Instruction> program = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith(";")) continue; // skip empty lines or comments
                String[] parts = line.split("\\s+");
                if (parts.length < 3) continue;
                InstructionSet opcode = InstructionSet.valueOf(parts[0].toUpperCase());
                int arg1 = Integer.parseInt(parts[1]);
                int arg2 = Integer.parseInt(parts[2]);
                program.add(new Instruction(opcode, arg1, arg2));
            }
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("Error loading program from file '" + filename + "': " + e.getMessage());
        }
        return program;
    }
}
