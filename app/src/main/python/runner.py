import os
import runpy
import sys
import asyncio
import threading
import traceback
from collections import deque

_lock = threading.RLock()
_local = threading.local()
_bots = {}
_logs = {}


class BotLogStream:
    def __init__(self, original):
        self.original = original

    def write(self, text):
        bot_id = getattr(_local, "bot_id", None)
        if bot_id is None:
            return self.original.write(text)

        if text:
            with _lock:
                _logs.setdefault(
                    bot_id, deque(maxlen=1000)
                ).append(text)

        return len(text)

    def flush(self):
        self.original.flush()

    def isatty(self):
        return self.original.isatty()

    @property
    def encoding(self):
        return self.original.encoding


if not isinstance(sys.stdout, BotLogStream):
    sys.stdout = BotLogStream(sys.stdout)

if not isinstance(sys.stderr, BotLogStream):
    sys.stderr = BotLogStream(sys.stderr)


def _install_discord_bridge():
    try:
        import discord
    except ImportError:
        return False

    if getattr(discord.Client.run, "_bothost_wrapped", False):
        return True

    def managed_run(client, *args, **kwargs):
        bot_id = getattr(_local, "bot_id", None)

        if bot_id is None:
            raise RuntimeError(
                "Discord bot was started outside BotHost"
            )

        for key in (
            "log_handler",
            "log_formatter",
            "log_level",
            "root_logger"
        ):
            kwargs.pop(key, None)

        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)

        with _lock:
            state = _bots.get(bot_id)

            if state is None:
                raise RuntimeError(
                    "BotHost bot state was not found"
                )

            state["client"] = client
            state["loop"] = loop
            stop_requested = state.get("stop_requested", False)
            state["status"] = "stopping" if stop_requested else "running"

        try:
            if stop_requested:
                loop.run_until_complete(client.close())
                return

            loop.run_until_complete(
                client.start(*args, **kwargs)
            )

        finally:
            try:
                if not client.is_closed():
                    loop.run_until_complete(client.close())
            except Exception:
                traceback.print_exc()

            try:
                loop.run_until_complete(
                    loop.shutdown_asyncgens()
                )
            except Exception:
                pass

            loop.close()
            asyncio.set_event_loop(None)

            with _lock:
                state = _bots.get(bot_id)

                if state:
                    state["client"] = None
                    state["loop"] = None

                    if state["status"] != "error":
                        state["status"] = "stopped"

    managed_run._bothost_wrapped = True
    discord.Client.run = managed_run
    return True


def run_script(script_path, bot_id):
    path = os.path.abspath(script_path)

    if not os.path.isfile(path):
        raise FileNotFoundError(path)

    script_dir = os.path.dirname(path)

    with _lock:
        existing = _bots.get(bot_id)

        if existing and existing["thread"].is_alive():
            raise RuntimeError("This bot is already running")

        _logs[bot_id] = deque(maxlen=1000)

        state = {
            "status": "starting",
            "thread": None,
            "client": None,
            "loop": None,
            "error": None,
            "stop_requested": False
        }

    def target():
        _local.bot_id = bot_id
        added_path = False

        try:
            if script_dir not in sys.path:
                sys.path.insert(0, script_dir)
                added_path = True

            _install_discord_bridge()

            runpy.run_path(
                path,
                run_name="__main__"
            )

        except BaseException:
            error = traceback.format_exc()

            with _lock:
                state["error"] = error
                state["status"] = "error"

                _logs.setdefault(
                    bot_id, deque(maxlen=1000)
                ).append(error)

        finally:
            with _lock:
                if state["status"] != "error":
                    state["status"] = "stopped"

                state["client"] = None
                state["loop"] = None

            if added_path:
                try:
                    sys.path.remove(script_dir)
                except ValueError:
                    pass

            try:
                del _local.bot_id
            except AttributeError:
                pass

    thread = threading.Thread(
        target=target,
        name=f"bothost-{bot_id}",
        daemon=True
    )

    state["thread"] = thread

    with _lock:
        _bots[bot_id] = state

    thread.start()
    return "started"


def stop_bot(bot_id):
    with _lock:
        state = _bots.get(bot_id)

        if not state:
            return "stopped"

        thread = state["thread"]
        client = state["client"]
        loop = state["loop"]

        if not thread.is_alive():
            state["status"] = "stopped"
            return "stopped"

        if client is None or loop is None or loop.is_closed():
            state["stop_requested"] = True
            state["status"] = "stopping"
            return "stop requested"

        state["status"] = "stopping"

        asyncio.run_coroutine_threadsafe(
            client.close(),
            loop
        )

        return "stopping"


def get_status(bot_id):
    with _lock:
        state = _bots.get(bot_id)

        if not state:
            return "stopped"

        return state["status"]


def get_logs(bot_id):
    with _lock:
        return "".join(_logs.get(bot_id, []))


def get_error(bot_id):
    with _lock:
        state = _bots.get(bot_id)
        return (state.get("error") or "") if state else ""
