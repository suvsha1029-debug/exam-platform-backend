using System.Runtime.InteropServices;

namespace QSStudio.Desktop;

internal static class NativeGuard
{
    private const string DllName = "qsstudio_native_guard";

    internal static GuardSnapshot Probe()
    {
        try
        {
            return new GuardSnapshot(
                Loaded: true,
                Version: GetVersion(),
                RemoteSession: IsRemoteSession(),
                DebuggerAttached: IsDebuggerAttached(),
                WorkstationLocked: IsWorkstationLocked()
            );
        }
        catch (DllNotFoundException)
        {
            return GuardSnapshot.NotLoaded("native DLL missing");
        }
        catch (EntryPointNotFoundException)
        {
            return GuardSnapshot.NotLoaded("native DLL entry mismatch");
        }
        catch (Exception ex)
        {
            return GuardSnapshot.NotLoaded(ex.Message);
        }
    }

    [DllImport(DllName, EntryPoint = "QsGuard_GetVersion", CallingConvention = CallingConvention.Cdecl)]
    private static extern int GetVersion();

    [DllImport(DllName, EntryPoint = "QsGuard_IsRemoteSession", CallingConvention = CallingConvention.Cdecl)]
    [return: MarshalAs(UnmanagedType.I1)]
    private static extern bool IsRemoteSession();

    [DllImport(DllName, EntryPoint = "QsGuard_IsDebuggerAttached", CallingConvention = CallingConvention.Cdecl)]
    [return: MarshalAs(UnmanagedType.I1)]
    private static extern bool IsDebuggerAttached();

    [DllImport(DllName, EntryPoint = "QsGuard_IsWorkstationLocked", CallingConvention = CallingConvention.Cdecl)]
    [return: MarshalAs(UnmanagedType.I1)]
    private static extern bool IsWorkstationLocked();

    [DllImport(DllName, EntryPoint = "QsGuard_StartExamGuard", CallingConvention = CallingConvention.Cdecl)]
    [return: MarshalAs(UnmanagedType.I1)]
    private static extern bool StartGuard();

    [DllImport(DllName, EntryPoint = "QsGuard_StopExamGuard", CallingConvention = CallingConvention.Cdecl)]
    private static extern void StopGuard();

    internal static bool StartExamGuard()
    {
        try
        {
            return StartGuard();
        }
        catch
        {
            return false;
        }
    }

    internal static void StopExamGuard()
    {
        try
        {
            StopGuard();
        }
        catch
        {
            // Keep the desktop shell resilient even if the native DLL cannot stop cleanly.
        }
    }
}

internal readonly record struct GuardSnapshot(
    bool Loaded,
    int Version,
    bool RemoteSession,
    bool DebuggerAttached,
    bool WorkstationLocked,
    string Message = "")
{
    internal static GuardSnapshot NotLoaded(string message) => new(false, 0, false, false, false, message);
}
