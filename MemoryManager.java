import java.util.*;
import java.util.stream.*;

public class MemoryManager {
    private final Map<Integer, byte[]> physicalMemory;
    private final Map<Integer, byte[]> diskStorage;
    private final Map<Integer, Map<Integer, Integer>> pageTables;
    private final Map<Integer, byte[]> sharedMemory;
    private final int pageSize;
    private int sharedMemoryBasePage;
    private final Map<Integer, List<HeapBlock>> processHeapMap;
    private final Map<Integer, PageInfo> pageInfoMap;
    private final int maxPhysicalPages = 32;

    class PageInfo {
        boolean isValid;
        boolean isDirty;
        int lastUsed;
        
        PageInfo(boolean isValid) {
            this.isValid = isValid;
            this.isDirty = false;
            this.lastUsed = 0;
        }
    }

    public MemoryManager(int pageSize) {
        this.pageSize = pageSize;
        this.physicalMemory = new HashMap<>();
        this.diskStorage = new HashMap<>();
        this.pageTables = new HashMap<>();
        this.sharedMemory = new HashMap<>();
        this.processHeapMap = new HashMap<>();
        this.pageInfoMap = new HashMap<>();
        this.sharedMemoryBasePage = 0;
        
        this.sharedMemory.put(sharedMemoryBasePage, new byte[pageSize]);
        this.sharedMemory.put(sharedMemoryBasePage + 1, new byte[pageSize]);
    }

    public void loadProgram(PCB pcb, List<Instruction> program) {
        int pagesNeeded = (program.size() * 12 + pageSize - 1) / pageSize;
        int startPage = allocatePages(pagesNeeded);
        
        byte[] currentPage = new byte[pageSize];
        int currentPageIndex = 0;
        int currentPageNumber = startPage;
        int pos = 0;
        
        for (Instruction instr : program) {
            if (pos + 12 > pageSize) {
                physicalMemory.put(currentPageNumber, currentPage);
                currentPageNumber = allocatePages(1);
                currentPage = new byte[pageSize];
                pos = 0;
            }
            
            packInt(currentPage, pos, instr.opcode.ordinal());
            packInt(currentPage, pos + 4, instr.arg1);
            packInt(currentPage, pos + 8, instr.arg2);
            pos += 12;
        }
        
        physicalMemory.put(currentPageNumber, currentPage);
        
        Map<Integer, Integer> pt = new HashMap<>();
        for (int i = 0; i < pagesNeeded; i++) {
            pt.put(i, startPage + i);
            pageInfoMap.put(startPage + i, new PageInfo(true));
        }
        pageTables.put(pcb.getProcessId(), pt);
        
        for (int i = 0; i < pagesNeeded; i++) {
            pcb.getWorkingSetPages().add(startPage + i);
        }
    }

    public int mapSharedMemory(int pid, int sharedRegionId) {
        if (sharedRegionId < 0 || sharedRegionId >= 2) {
            throw new RuntimeException("Invalid shared region ID");
        }
        
        Map<Integer, Integer> pt = pageTables.get(pid);
        int sharedPage = sharedMemoryBasePage + sharedRegionId;
        int virtualPage = pt.size();
        pt.put(virtualPage, sharedPage);
        pageInfoMap.put(sharedPage, new PageInfo(true));
        
        for (PCB p : ProcessManager.getInstance().getAllProcesses()) {
            if (p.getProcessId() == pid) {
                p.getWorkingSetPages().add(sharedPage);
                break;
            }
        }
        
        return virtualPage * pageSize;
    }

    public int readMemory(int pid, int address) {
        int virtualPage = address / pageSize;
        int offset = address % pageSize;
        
        Map<Integer, Integer> pt = pageTables.get(pid);
        if (pt == null || !pt.containsKey(virtualPage)) {
            throw new RuntimeException("Page fault for PID " + pid);
        }
        
        int physicalPage = pt.get(virtualPage);
        PageInfo info = pageInfoMap.get(physicalPage);
        
        if (!info.isValid) {
            handlePageFault(physicalPage);
        }
        
        info.lastUsed = CPU.clockCycleCount;
        byte[] data = physicalMemory.get(physicalPage);
        return unpackInt(data, offset);
    }

    public void writeMemory(int pid, int address, int value) {
        int virtualPage = address / pageSize;
        int offset = address % pageSize;
        
        Map<Integer, Integer> pt = pageTables.get(pid);
        if (pt == null || !pt.containsKey(virtualPage)) {
            throw new RuntimeException("Page fault for PID " + pid);
        }
        
        int physicalPage = pt.get(virtualPage);
        PageInfo info = pageInfoMap.get(physicalPage);
        
        if (!info.isValid) {
            handlePageFault(physicalPage);
        }
        
        info.isDirty = true;
        info.lastUsed = CPU.clockCycleCount;
        byte[] data = physicalMemory.get(physicalPage);
        packInt(data, offset, value);
        
        PCB p = ProcessManager.getInstance().getAllProcesses().stream()
            .filter(proc -> proc.getProcessId() == pid)
            .findFirst()
            .orElse(null);
        if (p != null) {
            p.updateWorkingSet(physicalPage);
        }
    }

    private void handlePageFault(int physicalPage) {
        if (physicalMemory.size() >= maxPhysicalPages) {
            evictPage();
        }
        
        if (diskStorage.containsKey(physicalPage)) {
            physicalMemory.put(physicalPage, diskStorage.get(physicalPage));
        } else {
            physicalMemory.put(physicalPage, new byte[pageSize]);
        }
        pageInfoMap.get(physicalPage).isValid = true;
    }

    private void evictPage() {
        int lruPage = pageInfoMap.entrySet().stream()
            .filter(e -> e.getValue().isValid)
            .min(Comparator.comparingInt(e -> e.getValue().lastUsed))
            .map(Map.Entry::getKey)
            .orElseThrow();
        
        PageInfo info = pageInfoMap.get(lruPage);
        if (info.isDirty) {
            diskStorage.put(lruPage, physicalMemory.get(lruPage));
        }
        physicalMemory.remove(lruPage);
        info.isValid = false;
        info.isDirty = false;
    }

    public int allocateHeap(PCB pcb, int size) {
        int pid = pcb.getProcessId();
        List<HeapBlock> heap = processHeapMap.computeIfAbsent(pid, k -> new ArrayList<>());
        
        for (int i = 0; i < heap.size(); i++) {
            HeapBlock block = heap.get(i);
            if (block.isFree && block.size >= size) {
                if (block.size > size) {
                    heap.add(i + 1, new HeapBlock(block.size - size, true));
                }
                block.size = size;
                block.isFree = false;
                int address = pcb.getHeapNextAddress() + (i * pageSize);
                pcb.addHeapAllocation(address, block);
                
                int page = address / pageSize;
                if (!pageTables.get(pid).containsKey(page)) {
                    int physicalPage = allocatePages(1);
                    pageTables.get(pid).put(page, physicalPage);
                    pageInfoMap.put(physicalPage, new PageInfo(true));
                    pcb.updateWorkingSet(physicalPage);
                }
                return address;
            }
        }
        
        int pagesNeeded = (size + pageSize - 1) / pageSize;
        int startPage = allocatePages(pagesNeeded);
        for (int i = 0; i < pagesNeeded; i++) {
            heap.add(new HeapBlock(pageSize, false));
            int virtualPage = (pcb.getHeapNextAddress() / pageSize) + i;
            pageTables.get(pid).put(virtualPage, startPage + i);
            pageInfoMap.put(startPage + i, new PageInfo(true));
            pcb.updateWorkingSet(startPage + i);
        }
        int address = pcb.getHeapNextAddress() + ((heap.size() - pagesNeeded) * pageSize);
        pcb.addHeapAllocation(address, new HeapBlock(size, false));
        return address;
    }

    public void freeHeap(PCB pcb, int address) {
        List<HeapBlock> heap = processHeapMap.get(pcb.getProcessId());
        if (heap == null) return;

        HeapBlock block = pcb.getHeapAllocations().get(address);
        if (block != null) {
            block.isFree = true;
            pcb.removeHeapAllocation(address);
            mergeAdjacentBlocks(heap);
        }
    }

    private void mergeAdjacentBlocks(List<HeapBlock> heap) {
        for (int i = 0; i < heap.size() - 1; i++) {
            HeapBlock current = heap.get(i);
            HeapBlock next = heap.get(i + 1);
            if (current.isFree && next.isFree) {
                current.size += next.size;
                heap.remove(i + 1);
                i--;
            }
        }
    }

    private int allocatePages(int count) {
        int startPage = physicalMemory.size();
        for (int i = 0; i < count; i++) {
            physicalMemory.put(startPage + i, new byte[pageSize]);
            pageInfoMap.put(startPage + i, new PageInfo(true));
        }
        return startPage;
    }

    private void packInt(byte[] data, int offset, int value) {
        data[offset] = (byte)(value >> 24);
        data[offset+1] = (byte)(value >> 16);
        data[offset+2] = (byte)(value >> 8);
        data[offset+3] = (byte)value;
    }

    private int unpackInt(byte[] data, int offset) {
        return (data[offset] & 0xFF) << 24 |
               (data[offset+1] & 0xFF) << 16 |
               (data[offset+2] & 0xFF) << 8 |
               (data[offset+3] & 0xFF);
    }

    public void printMemoryStats() {
        System.out.println("\nMemory Statistics:");
        System.out.println("------------------");
        System.out.printf("Physical Pages: %d/%d (%.1f%% used)\n",
            physicalMemory.size(), maxPhysicalPages,
            (physicalMemory.size() * 100.0 / maxPhysicalPages));
        System.out.printf("Disk Pages: %d\n", diskStorage.size());
        System.out.println("Page States:");
        pageInfoMap.forEach((page, info) -> {
            System.out.printf("Page %d: %s, %s, LastUsed: %d\n",
                page,
                info.isValid ? "Valid" : "Invalid",
                info.isDirty ? "Dirty" : "Clean",
                info.lastUsed);
        });
    }
}