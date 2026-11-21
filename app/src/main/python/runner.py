import os
import runpy
import threading
import traceback

_lock = threading.RLock()
_threads = {}

def run_script(script_path, bot_id):
    """Run one imported Python bot script in a dedicated daemon thread."""
    path = os.path.abspath(script_path)
    if not os.path.isfile(path):
        raise FileNotFoundError(path)

    def target():
        try:
            runpy.run_path(path, run_name="__main__")
        except BaseException:
            traceback.print_exc()
        finally:
            with _lock:
                _threads.pop(bot_id, None)

    with _lock:
        if bot_id in _threads and _threads[bot_id].is_alive():
            raise RuntimeError("This bot is already running")
        thread = threading.Thread(target=target, name=f"bot-{bot_id}", daemon=True)
        _threads[bot_id] = thread
        thread.start()
    return "started"
