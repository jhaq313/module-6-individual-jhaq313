# Final Project – Operating System Simulation  
**NAME:** Jafar Haq  
**Course:** CSCI-480 – Operating Systems

---

## Module Completion Summary

### ✅ Module 1: Virtual Machine  
Completed  
- Implemented a CPU with 16 registers, a program counter, and flags.  
- Supports core instructions like `LOAD_VALUE`, `SHOW_REG`, `INCREMENT`, `SLEEP`, `TERMINATE`, etc.

### ✅ Module 2: Paging  
Completed  
- Virtual address translation using fixed-size pages (256 bytes).  
- Each process uses its own page table and working set.

### ✅ Module 3: Process Management  
Completed  
<<<<<<< HEAD
- Processe's are created with prioritie's and managed through a ready queue.  
- CPU perform's context switching based on priority.
=======
- Processes are created with priorities and managed through a ready queue.  
- CPU performs context switching based on priority.
>>>>>>> 04966a345680899e6fb2c3fe8143b0b662582e73

### ✅ Module 4: Shared Memory, Locks, and Events  
Completed  
- Supports mapping shared memory and locking resources.  
- Includes event signaling and waiting across processes.

### ✅ Module 5: Heap Allocation  
Completed  
- Processes can dynamically allocate heap memory and write to it.  
- Memory stats show allocations per process.

### ✅ Module 6: Virtual Memory  
Completed  
- Virtual memory manager tracks `Valid`, `Dirty`, and `LastUsed` page attributes.  
- Working set pages and LRU logic integrated.

---

## Test Programs Used

Each of these `.txt` programs was loaded by the OS and executed in order of priority:

- `shared_memory_lock.txt` – Shared memory and locking
- `event_waiter.txt` – Waits on an event signal
- `event_signaler.txt` – Sends signal to resume another process
- `heap_allocate_write.txt` – Allocates heap, writes data, prints memory stats
- `countdown.txt` – Counts down from 3 to 1 using sleep and register output

---

<<<<<<< HEAD
## 📌 Notes

- All programs are loaded from external `.txt` files, not hardcoded.  
- The simulation runs each process independently, with full memory and process management.  
- Output includes process statistics, memory state, register dumps, and more.

---

## 🎥 Demo Video

*(Insert link)*

---
=======
## 🎥 Demo Video

*(https://youtu.be/9tPFvy0-m2g)*

---

Thank you!
>>>>>>> 04966a345680899e6fb2c3fe8143b0b662582e73
