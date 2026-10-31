using System;
using System.IO;
using System.Runtime.InteropServices;
class Launcher {
  [StructLayout(LayoutKind.Sequential)] struct BASIC { public long PerProcessUserTimeLimit,PerJobUserTimeLimit;public uint LimitFlags;public UIntPtr MinimumWorkingSetSize,MaximumWorkingSetSize;public uint ActiveProcessLimit;public UIntPtr Affinity;public uint PriorityClass,SchedulingClass; }
  [StructLayout(LayoutKind.Sequential)] struct IO { public ulong ReadOperationCount,WriteOperationCount,OtherOperationCount,ReadTransferCount,WriteTransferCount,OtherTransferCount; }
  [StructLayout(LayoutKind.Sequential)] struct LIMITS { public BASIC BasicLimitInformation;public IO IoInfo;public UIntPtr ProcessMemoryLimit,JobMemoryLimit,PeakProcessMemoryUsed,PeakJobMemoryUsed; }
  [StructLayout(LayoutKind.Sequential,CharSet=CharSet.Unicode)] struct STARTUP { public uint cb;public string lpReserved,lpDesktop,lpTitle;public uint dwX,dwY,dwXSize,dwYSize,dwXCountChars,dwYCountChars,dwFillAttribute,dwFlags;public ushort wShowWindow,cbReserved2;public IntPtr lpReserved2,hStdInput,hStdOutput,hStdError; }
  [StructLayout(LayoutKind.Sequential)] struct PROCESS {public IntPtr hProcess,hThread;public uint dwProcessId,dwThreadId;}
  [DllImport("kernel32.dll",CharSet=CharSet.Unicode)] static extern IntPtr CreateJobObject(IntPtr attributes,string name);
  [DllImport("kernel32.dll",SetLastError=true)] static extern bool SetInformationJobObject(IntPtr job,int info,ref LIMITS limits,uint length);
  [DllImport("kernel32.dll",SetLastError=true)] static extern bool AssignProcessToJobObject(IntPtr job,IntPtr process);
  [DllImport("kernel32.dll",CharSet=CharSet.Unicode,SetLastError=true)] static extern bool CreateProcess(string app,System.Text.StringBuilder command,IntPtr pa,IntPtr ta,bool inherit,uint flags,IntPtr environment,string cwd,ref STARTUP startup,out PROCESS process);
  [DllImport("kernel32.dll")] static extern uint ResumeThread(IntPtr thread);
  [DllImport("kernel32.dll")] static extern uint WaitForSingleObject(IntPtr handle,uint milliseconds);
  [DllImport("kernel32.dll")] static extern bool GetExitCodeProcess(IntPtr process,out uint code);
  [DllImport("kernel32.dll")] static extern bool TerminateProcess(IntPtr process,uint code);
  [DllImport("kernel32.dll")] static extern bool CloseHandle(IntPtr handle);
  static int Main(){IntPtr job=IntPtr.Zero;PROCESS child=new PROCESS();try{
    Console.Title="The Fourth Seat — Local Engine";Console.WriteLine("THE FOURTH SEAT\nStarting your local engine. The first launch can take a few minutes.\nClose this window to stop the game and all engine services.\n");
    string root=AppDomain.CurrentDomain.BaseDirectory,node=Path.Combine(root,"node","node.exe"),entry=Path.Combine(root,"runtime","main.mjs");
    if(!File.Exists(node)||!File.Exists(entry))throw new Exception("Extract the entire ZIP before launching. Keep all folders together.");
    job=CreateJobObject(IntPtr.Zero,null);if(job==IntPtr.Zero)throw new Exception("Cannot create engine process group.");
    LIMITS limits=new LIMITS();limits.BasicLimitInformation.LimitFlags=0x2000;
    if(!SetInformationJobObject(job,9,ref limits,(uint)Marshal.SizeOf(typeof(LIMITS))))throw new Exception("Cannot configure engine shutdown.");
    STARTUP si=new STARTUP();si.cb=(uint)Marshal.SizeOf(typeof(STARTUP));
    if(!CreateProcess(node,new System.Text.StringBuilder("\""+node+"\" --max-old-space-size=384 \""+entry+"\""),IntPtr.Zero,IntPtr.Zero,false,4,IntPtr.Zero,root,ref si,out child))throw new Exception("Could not start bundled Node. Error "+Marshal.GetLastWin32Error());
    if(!AssignProcessToJobObject(job,child.hProcess)){TerminateProcess(child.hProcess,1);throw new Exception("Could not attach engine cleanup. Error "+Marshal.GetLastWin32Error());}
    if(ResumeThread(child.hThread)==0xffffffff){TerminateProcess(child.hProcess,1);throw new Exception("Could not start engine thread.");}
    WaitForSingleObject(child.hProcess,0xffffffff);uint exit;GetExitCodeProcess(child.hProcess,out exit);
    if(exit!=0&&Environment.GetEnvironmentVariable("FOURTH_SEAT_TEST")!="1"){Console.WriteLine("\nStartup failed. Review the message above. Press Enter to close.");Console.ReadLine();}return(int)exit;
  }catch(Exception e){Console.Error.WriteLine(e.Message);if(Environment.GetEnvironmentVariable("FOURTH_SEAT_TEST")!="1")Console.ReadLine();return 1;}
  finally{if(child.hThread!=IntPtr.Zero)CloseHandle(child.hThread);if(child.hProcess!=IntPtr.Zero)CloseHandle(child.hProcess);if(job!=IntPtr.Zero)CloseHandle(job);}}
}
