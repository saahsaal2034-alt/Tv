# Xiaomi Google TV optimizer

Scripts that speed up a Xiaomi (Google TV) over your home Wi-Fi using ADB.
Everything they change can be undone with `restore.sh`.

## What it does
- Cuts UI animation times in half, so menus feel snappier
- Clears app caches and stops apps running in the background
- Optionally disables apps you don't use (listed in `disable.txt`). Disabled apps are not deleted.

## One-time setup on the TV
1. **Settings → System → About**, select **Android TV OS build** 7 times until it says you're a developer.
2. **Settings → System → Developer options**: turn on **USB debugging** (and **Network debugging** if your TV has it).
3. Write down the TV's IP address from **Settings → Network & Internet**. It's `192.168.0.169` by default.

## On a computer connected to the same Wi-Fi
1. Install Android platform-tools (`adb`): `brew install android-platform-tools` (Mac),
   `sudo apt install adb` (Linux), or download it from developer.android.com (Windows).
2. Run:
   ```bash
   ./optimize.sh 192.168.0.169
   ```
   When the TV asks you to **Allow USB debugging**, tick "Always allow" and press OK.
3. Restart the TV.

Optional: run `./list-apps.sh` to see your installed apps, add the ones you never use to `disable.txt`, then run `./optimize.sh` again.

Undo everything with `./restore.sh 192.168.0.169`.

## Picture settings (do these with the remote)
**Settings → Display & Sound → Picture**:
- Picture mode: **Movie**. Vivid makes colours look too strong.
- Turn **MEMC** off. It causes the "soap opera" look.
- For gaming, turn **Game mode** / **ALLM** on to cut input lag.
- **Settings → Inputs → HDMI EDID version**: set it to **2.0** (or Auto) for the HDMI port your 4K/HDR devices use.
