package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.io.EOFException;
import java.io.FileDescriptor;
import java.util.Iterator;
import java.util.LinkedList;



/**
 * Encapsulates the state of a user process that is not contained in its
 * user thread (or threads). This includes its address translation state, a
 * file table, and information about the program being executed.
 *
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 *
 * @see	nachos.vm.VMProcess
 * @see	nachos.network.NetProcess
 */
public class UserProcess {

	public static final int MAX_STRING_LENGTH = 256;
	public static final int MAX_OPEN_FILES = 16;
	public static final int ROOT = 1;
	public static final int STDIN = 0; 
	public static final int STDOUT = 1; 
	private int processID;
	private int parentProcessID;
	private UThread thread; 
	private int exitStatus;
	private LinkedList<Integer> children = new LinkedList<Integer>(); 


	private FileDescriptor fileDescriptors[] = new FileDescriptor[MAX_OPEN_FILES];

	/**
	 * Allocate a new process.
	 */
	public UserProcess() {
		for (int i=0; i<MAX_OPEN_FILES; i++) {                                 
			fileDescriptors[i] = new FileDescriptor();                            
		}                                                                   
		/** Comment Out */
	//	fileDescriptors[STDIN].file = UserKernel.console.openForReading();
	//	fileDescriptors[STDIN].position = 0;

	//	fileDescriptors[STDOUT].file = UserKernel.console.openForWriting();
	//	fileDescriptors[STDOUT].position = 0; 
		

		OpenFile file  = UserKernel.fileSystem.open("out", false);      

		int fileHandle = findEmptyFileDescriptor();                      
		fileDescriptors[fileHandle].file = file;                           
		fileDescriptors[fileHandle].position = 0;   

		processID = UserKernel.getNextPid();                       

		/* register this new process in UserKernel's map                           */
		UserKernel.registerProcess(processID, this);   

		int numPhysPages = Machine.processor().getNumPhysPages();
		pageTable = new TranslationEntry[numPhysPages];
		for (int index=0; index<numPhysPages; index++){
			pageTable[index] = new TranslationEntry(index,index, true,false,false,false);
		}
	}

	/**
	 * Allocate and return a new process of the correct class. The class name
	 * is specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 *
	 * @return	a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
		return (UserProcess)Lib.constructObject(Machine.getProcessClassName());
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 *
	 * @param	name	the name of the file containing the executable.
	 * @param	args	the arguments to pass to the executable.
	 * @return	<tt>true</tt> if the program was successfully executed.
	 */
	public boolean execute(String name, String[] args) {
		if (!load(name, args))
			return false;

		new UThread(this).setName(name).fork();

		return true;
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Machine.processor().setPageTable(pageTable);
	}

	/**
	 * Read a null-terminated string from this process's virtual memory. Read
	 * at most <tt>maxLength + 1</tt> bytes from the specified address, search
	 * for the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 *
	 * @param	vaddr	the starting virtual address of the null-terminated
	 *			string.
	 * @param	maxLength	the maximum number of characters in the string,
	 *				not including the null terminator.
	 * @return	the string read, or <tt>null</tt> if no null terminator was
	 *		found.
	 */
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength+1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		for (int length=0; length<bytesRead; length++) {
			if (bytes[length] == 0)
				return new String(bytes, 0, length);
		}

		return null;
	}

	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 *
	 * @param	vaddr	the first byte of virtual memory to read.
	 * @param	data	the array where the data will be stored.
	 * @return	the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no
	 * data could be copied).
	 *
	 * @param	vaddr	the first byte of virtual memory to read.
	 * @param	data	the array where the data will be stored.
	 * @param	offset	the first byte to write in the array.
	 * @param	length	the number of bytes to transfer from virtual memory to
	 *			the array.
	 * @return	the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data, int offset,
			int length) {
		Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		int virtualPageNumber = Machine.processor().pageFromAddress(vaddr);
		int addressOffset = Machine.processor().offsetFromAddress(vaddr);

		TranslationEntry entry = null;
		entry = pageTable[virtualPageNumber];
		entry.used = true;
		int physicalPageNumber = entry.ppn;
		int pageAddress = (physicalPageNumber*pageSize) + addressOffset;
		if(physicalPageNumber < 0 || physicalPageNumber >= Machine.processor().getNumPhysPages()){
			Lib.assertNotReached("\t UserProcess.readVirtualMemory(): bad ppn "+physicalPageNumber);
			return 0;
		}
		int amount = Math.min(length, memory.length-vaddr);
		System.out.println("Reading amount: "+amount);
		System.arraycopy(memory, vaddr, data, offset, amount);

		return amount;
	}

	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory.
	 * Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 *
	 * @param	vaddr	the first byte of virtual memory to write.
	 * @param	data	the array containing the data to transfer.
	 * @return	the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no
	 * data could be copied).
	 *
	 * @param	vaddr	the first byte of virtual memory to write.
	 * @param	data	the array containing the data to transfer.
	 * @param	offset	the first byte to transfer from the array.
	 * @param	length	the number of bytes to transfer from the array to
	 *			virtual memory.
	 * @return	the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset,
			int length) {
		Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		int virtualPageNumber = Machine.processor().pageFromAddress(vaddr);
		int addressOffset = Machine.processor().offsetFromAddress(vaddr);

		TranslationEntry entry = null;
		entry = pageTable[virtualPageNumber];
		entry.used = true;
		entry.dirty = true;
		int physicalPageNumber = entry.ppn;
		int pageAddress = (physicalPageNumber*pageSize) + addressOffset;

		if(entry.readOnly){
			Lib.assertNotReached("\t [UserProcess.writeVirtualMemory]: write read-only page "+physicalPageNumber);
			return 0;
		}

		if(physicalPageNumber < 0 || physicalPageNumber >= Machine.processor().getNumPhysPages()){
			Lib.assertNotReached("\t [UserProcess.writeVirtualMemory]: bad ppn "+physicalPageNumber);
			return 0;
		}

		int amount = Math.min(length, memory.length-vaddr);
		System.out.println("Writing amount: "+amount);
		System.arraycopy(data, offset, memory, vaddr, amount);

		return amount;
	}

	/**
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
	 *
	 * @param	name	the name of the file containing the executable.
	 * @param	args	the arguments to pass to the executable.
	 * @return	<tt>true</tt> if the executable was successfully loaded.
	 */
	private boolean load(String name, String[] args) {
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			return false;
		}

		try {
			coff = new Coff(executable);
		}
		catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s=0; s<coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				return false;
			}
			numPages += section.getLength();
		}

		// make sure the argv array will fit in one page
		byte[][] argv = new byte[args.length][];
		int argsSize = 0;
		for (int i=0; i<args.length; i++) {
			argv[i] = args[i].getBytes();
			// 4 bytes for argv[] pointer; then string plus one for null byte
			argsSize += 4 + argv[i].length + 1;
		}
		if (argsSize > pageSize) {
			coff.close();
			Lib.debug(dbgProcess, "\targuments too long");
			return false;
		}

		// program counter initially points at the program entry point
		initialPC = coff.getEntryPoint();	

		// next comes the stack; stack pointer initially points to top of it
		numPages += stackPages;
		initialSP = numPages*pageSize;

		// and finally reserve 1 page for arguments
		numPages++;

		if (!loadSections())
			return false;

		// store arguments in last page
		int entryOffset = (numPages-1)*pageSize;
		int stringOffset = entryOffset + args.length*4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i=0; i<argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib.assertTrue(writeVirtualMemory(entryOffset,stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) ==
					argv[i].length);
			stringOffset += argv[i].length;
			Lib.assertTrue(writeVirtualMemory(stringOffset,new byte[] { 0 }) == 1);
			stringOffset += 1;
		}

		return true;
	}

	/**
	 * Allocates memory for this process, and loads the COFF sections into
	 * memory. If this returns successfully, the process will definitely be
	 * run (this is the last step in process initialization that can fail).
	 *
	 * @return	<tt>true</tt> if the sections were successfully loaded.
	 */
	protected boolean loadSections() {
		if (numPages > Machine.processor().getNumPhysPages()) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}

		// load sections
		for (int s=0; s<coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int index=0; index<section.getLength(); index++) {
				int virtualPageNumber = section.getFirstVPN()+index;

				TranslationEntry entry = pageTable[virtualPageNumber];
				entry.readOnly = section.isReadOnly();
				int physicalPageNumber = entry.ppn;
				section.loadPage(index, physicalPageNumber);
			}
		}

		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		  for (int index = 0; index < numPages; index++) {                               
	            UserKernel.addFreePage(pageTable[index].ppn);                        
	            pageTable[index].valid = false;                                    
	        } 
	}    

	/**
	 * Initialize the processor's registers in preparation for running the
	 * program loaded into this process. Set the PC register to point at the
	 * start function, set the stack pointer register to point at the top of
	 * the stack, set the A0 and A1 registers to argc and argv, respectively,
	 * and initialize all other registers to 0.
	 */
	public void initRegisters() {
		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i=0; i<processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);

		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);

		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}

	/**
	 * Handle the halt() system call. 
	 */
	private int handleHalt() {

		Machine.halt();

		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}

	private int findEmptyFileDescriptor(){
		for(int index = 0; index < MAX_OPEN_FILES; index++){
			if(fileDescriptors[index].file == null){
				return index;
			}
		}
		return -1;
	}

	private int findFileDescriptorByName(String filename) { 
		for (int index = 0; index < MAX_OPEN_FILES; index++) {                 
			if (fileDescriptors[index].filename == filename) {               
				return index;     
			}
		}                                             

		return -1;                           
	}    

	private int create(int fileAddress){
		String fileName = readVirtualMemoryString(fileAddress, MAX_STRING_LENGTH);
		OpenFile file = UserKernel.fileSystem.open(fileName, true);
		System.out.println("Creating File -- Filename: "+ fileName + " File: " + file);
		if (file == null){
			return -1;
		} else {
			int fileHandle = findEmptyFileDescriptor();
			System.out.println("File Handle: " + fileHandle);
			if (fileHandle < 0){
				return -1;
			} else{
				fileDescriptors[fileHandle].filename = fileName;
				fileDescriptors[fileHandle].file = file;
				fileDescriptors[fileHandle].position = 0;
				return fileHandle;
			}
		}
	}

	private int open(int fileAddress){
		
		String fileName = readVirtualMemoryString(fileAddress, MAX_STRING_LENGTH);
		OpenFile file = UserKernel.fileSystem.open(fileName, false);
		
		if(file == null){			
			return -1;
		} else {
			int fileHandle = findEmptyFileDescriptor();
			if(fileHandle < 0){
				return -1;
			} else{
				fileDescriptors[fileHandle].filename = fileName;
				fileDescriptors[fileHandle].file = file;
				System.out.println("open file " + fileName);
				fileDescriptors[fileHandle].position = 0;
				return fileHandle;
			}
		}

	}

	private int read(int fileHandler, int bufferAddress, int bufferSize){
		
		if(fileHandler < 0  || fileHandler > MAX_OPEN_FILES || fileDescriptors[fileHandler].file == null){
			return -1;
		}

		FileDescriptor fileDescriptor = fileDescriptors[fileHandler];
		System.out.println("read for file " + fileDescriptor.filename + " with handler "+ fileHandler);
		byte[] buffer = new byte[bufferSize];

		int bytesRead = fileDescriptor.file.read(fileDescriptor.position, buffer, 0, bufferSize);
		if (bytesRead < 0){
			return -1;
		} else{
			int positionMoved = writeVirtualMemory(bufferAddress, buffer);
			fileDescriptor.position += positionMoved;
			return bytesRead;
		}
	}

	private int write(int fileHandler, int bufferAddress, int bufferSize) {
		if (fileHandler < 0 || fileHandler > MAX_OPEN_FILES || fileDescriptors[fileHandler].file == null){
			return -1;   
		}


		FileDescriptor fileDescriptor = fileDescriptors[fileHandler];                                  
		System.out.println("write for file " + fileDescriptor.filename  + " with handler "+ fileHandler);
		byte[] buf = new byte[bufferSize];                                   

		int bytesRead = readVirtualMemory(bufferAddress, buf);                  

		// invoke read through stubFilesystem                          
		int bytesWriten = fileDescriptor.file.write(fileDescriptor.position, buf, 0, bytesRead);       

		if (bytesWriten < 0) {                                                 
			return -1;                                                    
		}                                                              
		else {                                                         
			fileDescriptor.position = fileDescriptor.position + bytesWriten;                        
			return bytesWriten;                                             
		}                                                              
	}

	private int close(int fileHandler){
		
		if(fileHandler < 0 || fileHandler >= MAX_OPEN_FILES){
			return -1;
		}

		boolean noErrors = true;

		FileDescriptor fileDescriptor = fileDescriptors[fileHandler];
		System.out.println("closing file "+ fileDescriptors[fileHandler].filename);
		fileDescriptor.position = 0;
		fileDescriptor.file.close();
		if(fileDescriptor.toRemove){
			noErrors = UserKernel.fileSystem.remove(fileDescriptor.filename);
			fileDescriptor.toRemove = false;
		}

		fileDescriptor.filename = "";

		if(!noErrors){
			return -1;
		} else{
			return 0;
		}
	}

	private int unlink(int fileAddress){
		boolean noError = true;
		String fileName = readVirtualMemoryString(fileAddress, MAX_STRING_LENGTH);
		int fileHandle = findFileDescriptorByName(fileName);
		
		System.out.println("Unlinking --- FileName " + fileName+ " File Handle " + fileHandle);
		if(fileHandle < 0){
			return -1;
		}		
		
		if(!fileDescriptors[fileHandle].toRemove){
			noError = UserKernel.fileSystem.remove(fileDescriptors[fileHandle].filename);
		} else {
			fileDescriptors[fileHandle].toRemove = true;
		}
		if(!noError){
			return -1;
		} else {
			return 0;
		}
	}

	public int exec(int file, int argc, int argv){
		
		if (argc < 1){
			return -1;
		}
		String fileName = readVirtualMemoryString(file, MAX_STRING_LENGTH);
		System.out.println("exec fileName: " + fileName);
		if(fileName == null){
			return -1;
		}
		if(argc < 0 || argc > MAX_OPEN_FILES){
			return -1;
		}
		
		String suffix = fileName.substring(fileName.length()-4, fileName.length());
		if(suffix.equals(".coff")){
			return -1;
		}


		String arguments[] = new String[argc];
		byte temp[] = new byte[4];
		for(int index = 0; index < argc; index++){
			int byteCount = readVirtualMemory(argv+index*4, temp);
			if(byteCount != 4){
				return -1;
			}
			int arugmentAddress = Lib.bytesToInt(temp, 0);
			arguments[index] = readVirtualMemoryString(arugmentAddress, MAX_STRING_LENGTH);

		}

		UserProcess childProcess = UserProcess.newUserProcess();
		childProcess.parentProcessID = this.processID;
		this.children.add(childProcess.processID);
		if(childProcess.execute(fileName, arguments)){
			return childProcess.processID;
		} else {
			return -1;
		}
	}

	private int join(int childProccessID, int status){
		System.out.println("join()");
		boolean isChild = false;
		int temp = 0;
		Iterator<Integer> iterator = this.children.iterator();
		while(iterator.hasNext()){
			temp = iterator.next();
			if(temp == childProccessID){
				iterator.remove();
				isChild = true;
				break;
			}
		}
		if(!isChild){
			return -1;
		}

		UserProcess childProccess = UserKernel.getProcessByID(processID);

		if(childProccess == null){
			return -2;
		}

		childProccess.thread.join();
		UserKernel.unregisterProcess(processID);
		return childProccess.exitStatus; 
	}

	private void exit(int status){
		System.out.println("exit()");
		for (int index = 0; index < MAX_OPEN_FILES; index++) {                       
			if (fileDescriptors[index].file != null) {                               
				close(index);    
			}
		}  

		while (children != null && !children.isEmpty())  {               
			int childPid = children.removeFirst();                       
			UserProcess childProcess = UserKernel.getProcessByID(childPid);
			childProcess.parentProcessID = ROOT;                                   
		}    

		this.exitStatus = exitStatus;
		unloadSections();

		if (processID == ROOT) {            
			Kernel.kernel.terminate();           
		} else {            
			Lib.assertTrue(KThread.currentThread() == this.thread);
			KThread.currentThread().finish();                            
		}   
		Lib.assertNotReached("Exit should have already finnished");
	}

	private static final int
	syscallHalt = 0,
	syscallExit = 1,
	syscallExec = 2,
	syscallJoin = 3,
	syscallCreate = 4,
	syscallOpen = 5,
	syscallRead = 6,
	syscallWrite = 7,
	syscallClose = 8,
	syscallUnlink = 9;

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 *
	 * <table>
	 * <tr><td>syscall#</td><td>syscall prototype</td></tr>
	 * <tr><td>0</td><td><tt>void halt();</tt></td></tr>
	 * <tr><td>1</td><td><tt>void exit(int status);</tt></td></tr>
	 * <tr><td>2</td><td><tt>int  exec(char *name, int argc, char **argv);
	 * 								</tt></td></tr>
	 * <tr><td>3</td><td><tt>int  join(int pid, int *status);</tt></td></tr>
	 * <tr><td>4</td><td><tt>int  creat(char *name);</tt></td></tr>
	 * <tr><td>5</td><td><tt>int  open(char *name);</tt></td></tr>
	 * <tr><td>6</td><td><tt>int  read(int fd, char *buffer, int size);
	 *								</tt></td></tr>
	 * <tr><td>7</td><td><tt>int  write(int fd, char *buffer, int size);
	 *								</tt></td></tr>
	 * <tr><td>8</td><td><tt>int  close(int fd);</tt></td></tr>
	 * <tr><td>9</td><td><tt>int  unlink(char *name);</tt></td></tr>
	 * </table>
	 * 
	 * @param	syscall	the syscall number.
	 * @param	a0	the first syscall argument.
	 * @param	a1	the second syscall argument.
	 * @param	a2	the third syscall argument.
	 * @param	a3	the fourth syscall argument.
	 * @return	the value to be returned to the user.
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {
		case syscallHalt:
			return handleHalt();
		case syscallExit:
			//System.out.println("Exit");
			exit(a0);
			return 0;
		case syscallExec:
			//System.out.println("Exec");
			return exec(a0, a1, a2);
		case syscallJoin:
			//System.out.println("Join");
			return join(a0, a1);
		case syscallCreate:
			//System.out.println("Create");
			return create(a0);
		case syscallOpen:
			//System.out.println("Open");
			return open(a0);
		case syscallRead:
			//System.out.println("Read");
			return read(a0, a1, a2);
		case syscallWrite:
			//System.out.println("Write");
			return write(a0, a1, a2);
		case syscallClose:
			//System.out.println("Close");
			return close(a0);
		case syscallUnlink:
			//System.out.println("Unlink");
			return unlink(a0);
		default:
			Lib.debug(dbgProcess, "Unknown syscall " + syscall);
			Lib.assertNotReached("Unknown system call!");
		}
		return 0;
	}

	/**
	 * Handle a user exception. Called by
	 * <tt>UserKernel.exceptionHandler()</tt>. The
	 * <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 *
	 * @param	cause	the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();
		switch (cause) {
		case Processor.exceptionSyscall:
			int result = handleSyscall(processor.readRegister(Processor.regV0),
					processor.readRegister(Processor.regA0),
					processor.readRegister(Processor.regA1),
					processor.readRegister(Processor.regA2),
					processor.readRegister(Processor.regA3)
					);
			processor.writeRegister(Processor.regV0, result);
			processor.advancePC();
			break;		
		case Processor.exceptionAddressError:
			Lib.assertNotReached("Address Error");
		case Processor.exceptionBusError:
			Lib.assertNotReached("Bus Error");
		case Processor.exceptionIllegalInstruction:
			Lib.assertNotReached("Illegal Instruction");
		case Processor.exceptionOverflow:
			Lib.assertNotReached("Overflow Error");
		case Processor.exceptionPageFault:
			Lib.assertNotReached("Page Fault");
		case Processor.exceptionReadOnly:
			Lib.assertNotReached("File is Read Only");
		case Processor.exceptionTLBMiss:
			Lib.assertNotReached("TLB Miss");
		default:
			System.out.println("Unexpected exception: " + Processor.exceptionNames[cause]);
			Lib.debug(dbgProcess, "Unexpected exception: " +
					Processor.exceptionNames[cause]);
			Lib.assertNotReached("Unexpected exception");
		}
	}

	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;
	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;

	private int initialPC, initialSP;
	private int argc, argv;

	private static final int pageSize = Processor.pageSize;
	private static final char dbgProcess = 'a';



	public class FileDescriptor {                            

		public  String   filename = "";   // opened file name    
		public  OpenFile file = null;     // opened file object
		public  int      position = 0;    // IO position  

		public  boolean  toRemove = false;// if need to remove  
		// this file           

	} 
}
