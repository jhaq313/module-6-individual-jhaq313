public class Instruction {
    public InstructionSet opcode;
    public int arg1;
    public int arg2;

    public Instruction(InstructionSet opcode, int arg1, int arg2) {
        this.opcode = opcode;
        this.arg1 = arg1;
        this.arg2 = arg2;
    }
}