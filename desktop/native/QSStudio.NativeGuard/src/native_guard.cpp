#include <windows.h>
#include <atomic>

namespace
{
    HHOOK g_keyboardHook = nullptr;
    HHOOK g_mouseHook = nullptr;
    std::atomic<bool> g_examGuardActive{ false };

    bool isAltDown(const KBDLLHOOKSTRUCT* key)
    {
        return (key->flags & LLKHF_ALTDOWN) != 0 || (GetAsyncKeyState(VK_MENU) & 0x8000) != 0;
    }

    bool isCtrlDown()
    {
        return (GetAsyncKeyState(VK_CONTROL) & 0x8000) != 0;
    }

    bool isWindowsKey(DWORD vkCode)
    {
        return vkCode == VK_LWIN || vkCode == VK_RWIN;
    }

    bool isZoomKey(DWORD vkCode)
    {
        return vkCode == VK_OEM_PLUS
            || vkCode == VK_OEM_MINUS
            || vkCode == VK_ADD
            || vkCode == VK_SUBTRACT
            || vkCode == '0'
            || vkCode == VK_NUMPAD0;
    }

    bool shouldBlockKey(const KBDLLHOOKSTRUCT* key)
    {
        const DWORD vkCode = key->vkCode;
        const bool altDown = isAltDown(key);
        const bool ctrlDown = isCtrlDown();

        if (isWindowsKey(vkCode) || vkCode == VK_SNAPSHOT || vkCode == VK_APPS)
        {
            return true;
        }

        if (vkCode >= VK_F1 && vkCode <= VK_F12)
        {
            return true;
        }

        if (vkCode == VK_ESCAPE || vkCode == VK_LMENU || vkCode == VK_RMENU || vkCode == VK_MENU)
        {
            return true;
        }

        if (altDown && (vkCode == VK_TAB || vkCode == VK_F4 || vkCode == VK_SPACE))
        {
            return true;
        }

        if (ctrlDown && (vkCode == VK_ESCAPE || vkCode == 'P' || vkCode == 'S' || vkCode == 'R' || isZoomKey(vkCode)))
        {
            return true;
        }

        return false;
    }

    bool shouldBlockMouse(WPARAM message, const MSLLHOOKSTRUCT* mouse)
    {
        if (mouse == nullptr)
        {
            return false;
        }

        const bool ctrlDown = isCtrlDown();
        if (!ctrlDown)
        {
            return false;
        }

        if (message == WM_MOUSEWHEEL || message == WM_MOUSEHWHEEL)
        {
            return true;
        }

        return false;
    }

    LRESULT CALLBACK GuardKeyboardProc(int code, WPARAM wParam, LPARAM lParam)
    {
        if (code == HC_ACTION && g_examGuardActive.load(std::memory_order_relaxed))
        {
            const auto* key = reinterpret_cast<KBDLLHOOKSTRUCT*>(lParam);
            if (key != nullptr && (wParam == WM_KEYDOWN || wParam == WM_SYSKEYDOWN || wParam == WM_KEYUP || wParam == WM_SYSKEYUP))
            {
                if (shouldBlockKey(key))
                {
                    return 1;
                }
            }
        }
        return CallNextHookEx(g_keyboardHook, code, wParam, lParam);
    }

    LRESULT CALLBACK GuardMouseProc(int code, WPARAM wParam, LPARAM lParam)
    {
        if (code == HC_ACTION && g_examGuardActive.load(std::memory_order_relaxed))
        {
            const auto* mouse = reinterpret_cast<MSLLHOOKSTRUCT*>(lParam);
            if (shouldBlockMouse(wParam, mouse))
            {
                return 1;
            }
        }
        return CallNextHookEx(g_mouseHook, code, wParam, lParam);
    }

    HMODULE guardModuleHandle()
    {
        HMODULE module = nullptr;
        GetModuleHandleExW(
            GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS | GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,
            reinterpret_cast<LPCWSTR>(&GuardKeyboardProc),
            &module);
        return module;
    }
}

extern "C" __declspec(dllexport) int __cdecl QsGuard_GetVersion()
{
    return 3;
}

extern "C" __declspec(dllexport) bool __cdecl QsGuard_IsRemoteSession()
{
    return GetSystemMetrics(SM_REMOTESESSION) != 0;
}

extern "C" __declspec(dllexport) bool __cdecl QsGuard_IsDebuggerAttached()
{
    return IsDebuggerPresent() != FALSE;
}

extern "C" __declspec(dllexport) bool __cdecl QsGuard_IsWorkstationLocked()
{
    HDESK inputDesktop = OpenInputDesktop(0, FALSE, DESKTOP_SWITCHDESKTOP);
    if (inputDesktop == nullptr)
    {
        return true;
    }

    const BOOL canSwitch = SwitchDesktop(inputDesktop);
    CloseDesktop(inputDesktop);
    return canSwitch == FALSE;
}

extern "C" __declspec(dllexport) bool __cdecl QsGuard_StartExamGuard()
{
    g_examGuardActive.store(true, std::memory_order_relaxed);
    if (g_keyboardHook != nullptr)
    {
        if (g_mouseHook != nullptr)
        {
            return true;
        }
    }

    HMODULE module = guardModuleHandle();
    if (g_keyboardHook == nullptr)
    {
        g_keyboardHook = SetWindowsHookExW(WH_KEYBOARD_LL, GuardKeyboardProc, module, 0);
    }
    if (g_mouseHook == nullptr)
    {
        g_mouseHook = SetWindowsHookExW(WH_MOUSE_LL, GuardMouseProc, module, 0);
    }
    if (g_keyboardHook == nullptr || g_mouseHook == nullptr)
    {
        g_examGuardActive.store(false, std::memory_order_relaxed);
        if (g_keyboardHook != nullptr)
        {
            UnhookWindowsHookEx(g_keyboardHook);
            g_keyboardHook = nullptr;
        }
        if (g_mouseHook != nullptr)
        {
            UnhookWindowsHookEx(g_mouseHook);
            g_mouseHook = nullptr;
        }
        return false;
    }
    return true;
}

extern "C" __declspec(dllexport) void __cdecl QsGuard_StopExamGuard()
{
    g_examGuardActive.store(false, std::memory_order_relaxed);
    if (g_keyboardHook != nullptr)
    {
        UnhookWindowsHookEx(g_keyboardHook);
        g_keyboardHook = nullptr;
    }
    if (g_mouseHook != nullptr)
    {
        UnhookWindowsHookEx(g_mouseHook);
        g_mouseHook = nullptr;
    }
}

extern "C" __declspec(dllexport) bool __cdecl QsGuard_IsExamGuardActive()
{
    return g_keyboardHook != nullptr && g_mouseHook != nullptr && g_examGuardActive.load(std::memory_order_relaxed);
}
