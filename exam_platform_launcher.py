import os
import socket
import subprocess
import threading
import time
import urllib.request
import webbrowser
from pathlib import Path
import tkinter as tk
from tkinter import ttk, messagebox
from tkinter import scrolledtext


ROOT = Path(__file__).resolve().parent
APP_JAR = ROOT / "build" / "libs" / "exam-platform-backend-0.0.1-SNAPSHOT.jar"
GRADLEW = ROOT / "gradlew.bat"
START_STABLE_BAT = ROOT / "start-exam-platform.bat"
START_DEV_BAT = ROOT / "start-exam-platform-dev.bat"
STOP_BAT = ROOT / "stop-exam-platform.bat"

PID_FILE = ROOT / "exam-platform.pid"
DEV_PID_FILE = ROOT / "exam-platform-dev.pid"
SERVER_LOG = ROOT / "server.log"
SERVER_ERR = ROOT / "server.err.log"
DEV_LOG = ROOT / "dev-server.log"
DEV_ERR = ROOT / "dev-server.err.log"

SERVER_PORT = 8081
DEFAULT_MASTER_ADMIN_EMAIL = "shashwatdogra1@gmail.com"
ADMIN_CONSOLE_PATH = "/console/8f3a91d6c42e7b5a0d19/admin"
MASTER_ADMIN_CONSOLE_PATH = "/console/c7e42a9d51f8603b7a24/master"
MASTER_ADMIN_SETUP_PATH = "/console/setup/51b7d893e4a26c0f/master"


def detect_lan_ip():
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.connect(("8.8.8.8", 80))
        ip = sock.getsockname()[0]
        sock.close()
        if ip and not ip.startswith("127."):
            return ip
    except Exception:
        pass
    return "192.168.31.154"


LAN_IP = detect_lan_ip()


def build_url(path: str, host: str = "localhost"):
    path = path if path.startswith("/") else "/" + path
    return f"http://{host}:{SERVER_PORT}{path}"


PUBLIC_URL = build_url("/", "localhost")
ADMIN_URL = build_url(ADMIN_CONSOLE_PATH, "localhost")
REGISTER_URL = build_url("/candidate-entry/registration", "localhost")
DASHBOARD_URL = build_url("/candidate-entry/dashboard", "localhost")
EXAM_URL = build_url("/candidate-entry/exam", "localhost")

LAN_PUBLIC_URL = build_url("/", LAN_IP)
LAN_ADMIN_URL = build_url(ADMIN_CONSOLE_PATH, LAN_IP)
LAN_REGISTER_URL = build_url("/candidate-entry/registration", LAN_IP)
LAN_DASHBOARD_URL = build_url("/candidate-entry/dashboard", LAN_IP)
LAN_EXAM_URL = build_url("/candidate-entry/exam", LAN_IP)


def read_pid(path: Path):
    try:
        text = path.read_text(encoding="utf-8").strip()
        return int(text) if text else None
    except Exception:
        return None


def write_pid(path: Path, pid: int):
    path.write_text(str(pid), encoding="utf-8")


def remove_pid(path: Path):
    try:
        path.unlink(missing_ok=True)
    except Exception:
        pass


def process_alive(pid: int | None):
    if not pid:
        return False
    result = subprocess.run(
        ["powershell", "-NoProfile", "-Command", f"Get-Process -Id {pid} -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Id"],
        capture_output=True,
        text=True,
        creationflags=subprocess.CREATE_NO_WINDOW
    )
    return result.returncode == 0 and str(pid) in (result.stdout or "")


def stop_pid(pid: int | None):
    if not pid:
        return
    subprocess.run(
        ["powershell", "-NoProfile", "-Command", f"Stop-Process -Id {pid} -Force -ErrorAction SilentlyContinue"],
        creationflags=subprocess.CREATE_NO_WINDOW
    )


def port_ready(host="127.0.0.1"):
    try:
        with socket.create_connection((host, SERVER_PORT), timeout=2):
            return True
    except Exception:
        return False


def wait_for_server(timeout_seconds=90):
    deadline = time.time() + timeout_seconds
    while time.time() < deadline:
        if port_ready():
            return True
        time.sleep(1)
    return port_ready()


def resolved_db_env():
    env = os.environ.copy()
    env.setdefault("DB_USERNAME", os.environ.get("DB_USERNAME", "postgres"))
    db_password = (
        os.environ.get("DB_PASSWORD")
        or os.environ.get("POSTGRES_PASSWORD")
        or os.environ.get("PGPASSWORD")
        or "Dogra@1029"
    )
    env["DB_PASSWORD"] = db_password
    env.setdefault("MASTER_ADMIN_EMAIL", DEFAULT_MASTER_ADMIN_EMAIL)
    mail_username = os.environ.get("MAIL_USERNAME", "")
    if mail_username:
        env["MAIL_USERNAME"] = mail_username
    mail = os.environ.get("MAIL_PASSWORD", "")
    if mail:
        env["MAIL_PASSWORD"] = mail
    return env


class LauncherApp:
    def __init__(self, root: tk.Tk):
        self.root = root
        self.root.title("Exam Platform Launcher")
        self.root.geometry("920x650")
        self.root.minsize(860, 620)
        self.root.configure(bg="#dbeafe")

        self.mode_var = tk.StringVar(value="Not running")
        self.server_var = tk.StringVar(value="Server status: checking...")
        self.pid_var = tk.StringVar(value="Stable PID: - | Dev PID: -")
        self.last_action_var = tk.StringVar(value="Ready.")
        self.lan_var = tk.StringVar(value=f"LAN access: http://{LAN_IP}:{SERVER_PORT}/")
        self.log_choice_var = tk.StringVar(value="Stable Log")
        self.log_status_var = tk.StringVar(value="Log viewer ready.")
        self.starting_mode = None
        self.auto_scroll_logs = True

        self._build_ui()
        self.refresh_status()
        self._start_poll()

    def _build_ui(self):
        style = ttk.Style()
        try:
            style.theme_use("clam")
        except Exception:
            pass

        top = tk.Frame(self.root, bg="#0d5ca6", padx=20, pady=18)
        top.pack(fill="x")

        tk.Label(top, text="Exam Platform Control Panel", font=("Arial", 22, "bold"), fg="white", bg="#0d5ca6").pack(anchor="w")
        tk.Label(top, text="One-click stable start, dev start, stop, logs, and portal open.", font=("Arial", 11), fg="#d8ecff", bg="#0d5ca6").pack(anchor="w", pady=(4, 0))

        viewport = tk.Frame(self.root, bg="#dbeafe")
        viewport.pack(fill="both", expand=True)

        canvas = tk.Canvas(viewport, bg="#dbeafe", highlightthickness=0, borderwidth=0)
        canvas.pack(side="left", fill="both", expand=True)

        body_scroll = ttk.Scrollbar(viewport, orient="vertical", command=canvas.yview)
        body_scroll.pack(side="right", fill="y")
        canvas.configure(yscrollcommand=body_scroll.set)

        body = tk.Frame(canvas, bg="#dbeafe", padx=18, pady=16)
        body_window = canvas.create_window((0, 0), window=body, anchor="nw")

        def sync_body_scroll(_event=None):
            canvas.configure(scrollregion=canvas.bbox("all"))

        def sync_body_width(event):
            canvas.itemconfigure(body_window, width=event.width)

        body.bind("<Configure>", sync_body_scroll)
        canvas.bind("<Configure>", sync_body_width)

        def on_outer_mousewheel(event):
            delta = getattr(event, "delta", 0)
            if delta:
                canvas.yview_scroll(int(-delta / 120), "units")
                return "break"
            if getattr(event, "num", None) == 4:
                canvas.yview_scroll(-1, "units")
                return "break"
            if getattr(event, "num", None) == 5:
                canvas.yview_scroll(1, "units")
                return "break"
            return None

        canvas.bind_all("<MouseWheel>", on_outer_mousewheel)
        canvas.bind_all("<Button-4>", on_outer_mousewheel)
        canvas.bind_all("<Button-5>", on_outer_mousewheel)

        status = tk.LabelFrame(body, text="Status", bg="white", fg="#073a70", font=("Arial", 11, "bold"), padx=14, pady=12)
        status.pack(fill="x", pady=(0, 14))
        tk.Label(status, textvariable=self.mode_var, font=("Arial", 12, "bold"), fg="#0d5ca6", bg="white").pack(anchor="w")
        tk.Label(status, textvariable=self.server_var, font=("Arial", 11), fg="#10243f", bg="white").pack(anchor="w", pady=(6, 0))
        tk.Label(status, textvariable=self.pid_var, font=("Arial", 10), fg="#607089", bg="white").pack(anchor="w", pady=(4, 0))
        tk.Label(status, textvariable=self.lan_var, font=("Arial", 10, "bold"), fg="#0d5ca6", bg="white").pack(anchor="w", pady=(4, 0))
        tk.Label(status, textvariable=self.last_action_var, font=("Arial", 10), fg="#607089", bg="white").pack(anchor="w", pady=(4, 0))

        actions = tk.Frame(body, bg="#dbeafe")
        actions.pack(fill="x", pady=(0, 14))

        left = tk.LabelFrame(actions, text="Server Actions", bg="white", fg="#073a70", font=("Arial", 11, "bold"), padx=14, pady=12)
        left.pack(side="left", fill="both", expand=True, padx=(0, 8))

        right = tk.LabelFrame(actions, text="Open Pages / Logs", bg="white", fg="#073a70", font=("Arial", 11, "bold"), padx=14, pady=12)
        right.pack(side="left", fill="both", expand=True, padx=(8, 0))

        self._button(left, "Start Stable Server", self.start_stable, "#0d5ca6")
        self._button(left, "Start Dev Server", self.start_dev, "#1b6cb6")
        self._button(left, "Stop Server", self.stop_all, "#b00020")
        self._button(left, "Refresh Status", self.refresh_status, "#607089")

        self._button(right, "Open Public Portal", lambda: self.open_url(PUBLIC_URL), "#0d5ca6")
        self._button(right, "Open Admin Portal", lambda: self.open_url(ADMIN_URL), "#0d5ca6")
        self._button(right, "Open Registration Page", lambda: self.open_url(REGISTER_URL), "#0d5ca6")
        self._button(right, "Open Candidate Dashboard", lambda: self.open_url(DASHBOARD_URL), "#0d5ca6")
        self._button(right, "Open Candidate Exam", lambda: self.open_url(EXAM_URL), "#0d5ca6")
        self._button(right, "Open LAN Public Portal", lambda: self.open_url(LAN_PUBLIC_URL), "#1b6cb6")
        self._button(right, "Open LAN Admin Portal", lambda: self.open_url(LAN_ADMIN_URL), "#1b6cb6")
        self._button(right, "Open LAN Candidate Exam", lambda: self.open_url(LAN_EXAM_URL), "#1b6cb6")
        self._button(right, "Open Stable Log", lambda: self.open_file(SERVER_LOG), "#607089")
        self._button(right, "Open Stable Error Log", lambda: self.open_file(SERVER_ERR), "#607089")
        self._button(right, "Open Dev Log", lambda: self.open_file(DEV_LOG), "#607089")
        self._button(right, "Open Dev Error Log", lambda: self.open_file(DEV_ERR), "#607089")

        lower = tk.Frame(body, bg="#dbeafe")
        lower.pack(fill="both", expand=True)

        info = tk.LabelFrame(lower, text="Notes", bg="white", fg="#073a70", font=("Arial", 11, "bold"), padx=14, pady=12)
        info.pack(side="left", fill="both", expand=False, padx=(0, 8))

        notes = (
            "Stable mode: runs the packaged jar.\n"
            "Dev mode: runs Gradle bootRun for faster local editing.\n"
            "Open Exam Platform launcher now targets secure /candidate-entry routes.\n"
            f"LAN server address for same-Wi-Fi use: http://{LAN_IP}:{SERVER_PORT}/\n"
            "If webpages fail to open, use Refresh Status first.\n"
            "If Java path changes later, update JAVA_EXE in this launcher file.\n"
            "You can later convert this file into an EXE using PyInstaller."
        )
        tk.Label(info, text=notes, justify="left", anchor="nw", font=("Arial", 11), fg="#10243f", bg="white").pack(fill="both", expand=True)

        log_box = tk.LabelFrame(lower, text="Live Log Viewer", bg="white", fg="#073a70", font=("Arial", 11, "bold"), padx=14, pady=12)
        log_box.pack(side="left", fill="both", expand=True, padx=(8, 0))

        log_controls = tk.Frame(log_box, bg="white")
        log_controls.pack(fill="x", pady=(0, 8))

        tk.Label(log_controls, text="View", font=("Arial", 10, "bold"), fg="#073a70", bg="white").pack(side="left")
        selector = ttk.Combobox(
            log_controls,
            textvariable=self.log_choice_var,
            values=["Stable Log", "Stable Error Log", "Dev Log", "Dev Error Log"],
            state="readonly",
            width=20
        )
        selector.pack(side="left", padx=(8, 8))
        selector.bind("<<ComboboxSelected>>", lambda _event: self.refresh_log_view())

        tk.Button(
            log_controls,
            text="Refresh",
            command=self.refresh_log_view,
            font=("Arial", 10, "bold"),
            bg="#607089",
            fg="white",
            activebackground="#607089",
            activeforeground="white",
            relief="flat",
            padx=10,
            pady=6,
            cursor="hand2"
        ).pack(side="left", padx=(0, 8))

        tk.Button(
            log_controls,
            text="Auto-Scroll: ON",
            command=self.toggle_auto_scroll,
            font=("Arial", 10, "bold"),
            bg="#0d5ca6",
            fg="white",
            activebackground="#0d5ca6",
            activeforeground="white",
            relief="flat",
            padx=10,
            pady=6,
            cursor="hand2"
        ).pack(side="left")

        self.auto_scroll_button = log_controls.winfo_children()[-1]

        self.log_view = scrolledtext.ScrolledText(
            log_box,
            wrap="none",
            height=18,
            font=("Consolas", 10),
            bg="#0f1720",
            fg="#d8ecff",
            insertbackground="#ffffff",
            relief="solid",
            borderwidth=1
        )
        self.log_view.pack(fill="both", expand=True)
        self.log_view.configure(state="disabled")
        self.log_view.bind("<MouseWheel>", self._on_log_mousewheel)
        self.log_view.bind("<Button-4>", self._on_log_mousewheel)
        self.log_view.bind("<Button-5>", self._on_log_mousewheel)

        tk.Label(log_box, textvariable=self.log_status_var, font=("Arial", 10), fg="#607089", bg="white").pack(anchor="w", pady=(8, 0))
        self.refresh_log_view()

    def _button(self, parent, text, command, color):
        button = tk.Button(
            parent,
            text=text,
            command=lambda: threading.Thread(target=command, daemon=True).start(),
            font=("Arial", 11, "bold"),
            bg=color,
            fg="white",
            activebackground=color,
            activeforeground="white",
            relief="flat",
            padx=12,
            pady=10,
            cursor="hand2"
        )
        button.pack(fill="x", pady=5)
        return button

    def set_action(self, text):
        self.root.after(0, lambda: self.last_action_var.set(text))

    def log_path(self):
        return {
            "Stable Log": SERVER_LOG,
            "Stable Error Log": SERVER_ERR,
            "Dev Log": DEV_LOG,
            "Dev Error Log": DEV_ERR,
        }.get(self.log_choice_var.get(), SERVER_LOG)

    def read_log_tail(self, path: Path, max_lines: int = 500):
        if not path.exists():
            return ""
        try:
            lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
            return "\n".join(lines[-max_lines:])
        except Exception as error:
            return f"Could not read log:\n{error}"

    def refresh_log_view(self):
        path = self.log_path()
        content = self.read_log_tail(path)
        if not content:
            content = f"No log content yet for:\n{path}"
        self.log_view.configure(state="normal")
        self.log_view.delete("1.0", tk.END)
        self.log_view.insert("1.0", content)
        self.log_view.configure(state="disabled")
        if self.auto_scroll_logs:
            self.log_view.see(tk.END)
        self.log_status_var.set(f"Viewing: {path.name}")

    def toggle_auto_scroll(self):
        self.auto_scroll_logs = not self.auto_scroll_logs
        self.auto_scroll_button.configure(text=f"Auto-Scroll: {'ON' if self.auto_scroll_logs else 'OFF'}")
        if self.auto_scroll_logs:
            self.log_view.see(tk.END)

    def _on_log_mousewheel(self, event):
        if getattr(event, "num", None) == 4:
            self.log_view.yview_scroll(-1, "units")
            return "break"
        if getattr(event, "num", None) == 5:
            self.log_view.yview_scroll(1, "units")
            return "break"
        delta = getattr(event, "delta", 0)
        if delta:
            self.log_view.yview_scroll(int(-delta / 120), "units")
            return "break"
        return None

    def refresh_status(self):
        stable_pid = read_pid(PID_FILE)
        dev_pid = read_pid(DEV_PID_FILE)
        stable_alive = process_alive(stable_pid)
        dev_alive = process_alive(dev_pid)
        ready = port_ready()

        if ready and self.starting_mode:
            self.starting_mode = None

        if self.starting_mode and not ready:
            mode = f"Starting in {self.starting_mode.upper()} mode"
            status = "Server status: building / starting backend..."
        elif dev_alive:
            mode = "Running in DEV mode"
            status = "Server status: online on port 8081" if ready else "Server status: dev process running, waiting for port 8081"
        elif stable_alive:
            mode = "Running in STABLE mode"
            status = "Server status: online on port 8081" if ready else "Server status: stable process running, waiting for port 8081"
        else:
            mode = "Not running"
            status = "Server status: offline / still starting"

        self.root.after(0, lambda: self.mode_var.set(mode))
        self.root.after(0, lambda: self.server_var.set(status))
        self.root.after(0, lambda: self.pid_var.set(f"Stable PID: {stable_pid or '-'} | Dev PID: {dev_pid or '-'}"))
        self.root.after(0, lambda: self.lan_var.set(f"LAN access: http://{LAN_IP}:{SERVER_PORT}/"))

        if stable_pid and not stable_alive:
            remove_pid(PID_FILE)
        if dev_pid and not dev_alive:
            remove_pid(DEV_PID_FILE)

    def stop_all(self):
        self.set_action("Stopping running backend processes...")
        self.starting_mode = None
        if STOP_BAT.exists():
            env = resolved_db_env()
            env["EXAM_PLATFORM_PAUSE"] = "0"
            subprocess.run(
                ["cmd", "/c", str(STOP_BAT)],
                cwd=ROOT,
                env=env,
                creationflags=subprocess.CREATE_NO_WINDOW,
                input=b"\n"
            )
        else:
            stable_pid = read_pid(PID_FILE)
            dev_pid = read_pid(DEV_PID_FILE)
            stop_pid(stable_pid)
            stop_pid(dev_pid)
            remove_pid(PID_FILE)
            remove_pid(DEV_PID_FILE)
        time.sleep(2)
        self.refresh_status()
        self.set_action("Stop command completed.")

    def start_stable(self):
        self.stop_all()
        if not START_STABLE_BAT.exists():
            self.root.after(0, lambda: messagebox.showerror("Launcher Missing", f"Missing file:\n{START_STABLE_BAT}"))
            self.set_action("Stable start failed: launcher BAT missing.")
            return
        self.starting_mode = "stable"
        self.set_action("Starting stable server using BAT flow...")
        env = resolved_db_env()
        env["EXAM_PLATFORM_PAUSE"] = "0"
        subprocess.Popen(
            ["cmd", "/c", str(START_STABLE_BAT)],
            cwd=ROOT,
            env=env,
            creationflags=subprocess.CREATE_NEW_CONSOLE
        )
        if wait_for_server(120):
            self.set_action("Stable server started and detected on port 8081.")
        else:
            self.set_action("Stable start sent, but backend is still not reachable. Check logs.")
        self.refresh_status()

    def start_dev(self):
        self.stop_all()
        if not START_DEV_BAT.exists():
            self.root.after(0, lambda: messagebox.showerror("Launcher Missing", f"Missing file:\n{START_DEV_BAT}"))
            self.set_action("Dev start failed: launcher BAT missing.")
            return
        self.starting_mode = "dev"
        self.set_action("Starting dev server using BAT flow...")
        env = resolved_db_env()
        env["EXAM_PLATFORM_PAUSE"] = "0"
        subprocess.Popen(
            ["cmd", "/c", str(START_DEV_BAT)],
            cwd=ROOT,
            env=env,
            creationflags=subprocess.CREATE_NEW_CONSOLE
        )
        if wait_for_server(120):
            self.set_action("Dev server started and detected on port 8081.")
        else:
            self.set_action("Dev start sent, but backend is still not reachable. Check logs.")
        self.refresh_status()

    def open_file(self, path: Path):
        if not path.exists():
            self.root.after(0, lambda: messagebox.showinfo("File Missing", f"File not found:\n{path}"))
            return
        os.startfile(path)  # noqa

    def open_url(self, url: str):
        self.set_action(f"Opening {url} ...")
        if not port_ready():
            self.refresh_status()
            stable_pid = read_pid(PID_FILE)
            dev_pid = read_pid(DEV_PID_FILE)
            if process_alive(stable_pid) or process_alive(dev_pid):
                self.set_action("Server is still starting. Waiting before opening page...")
                if not wait_for_server(120):
                    self.root.after(0, lambda: messagebox.showerror("Server Not Ready", "Backend is still not reachable on port 8081. Check the logs once."))
                    self.set_action("Open page failed: backend not reachable.")
                    return
            else:
                self.set_action("Backend is offline. Starting stable server first...")
                self.start_stable()
                if not port_ready():
                    self.root.after(0, lambda: messagebox.showerror("Server Offline", "Backend could not be started automatically. Check the logs once."))
                    self.set_action("Open page failed: backend is offline after auto-start attempt.")
                    return
        try:
            opened = webbrowser.open_new_tab(url)
            if not opened:
                os.startfile(url)  # noqa
            self.set_action(f"Opened {url}")
        except OSError as error:
            self.root.after(0, lambda: messagebox.showerror("Open Failed", f"Could not open page:\n{url}\n\n{error}"))
            self.set_action(f"Open page failed: {error}")

    def _start_poll(self):
        def poll():
            self.refresh_status()
            self.refresh_log_view()
            self.root.after(3000, poll)
        self.root.after(3000, poll)


if __name__ == "__main__":
    app = tk.Tk()
    LauncherApp(app)
    app.mainloop()
